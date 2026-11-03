package com.turboio.addon;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.HttpURLConnection;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Stream B (B1-B13): desktop-dispatched rounds are discovered from the bound session timeline and
 * adopted into the phone's single-slot monitor. Production classes stay real; only platform,
 * durable-secret and HTTP boundaries are faked.
 */
public final class DshSessionAdopterTest {
    static int checks;
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }

    static final String MODEL = "dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
    static final String PERM = "dsh:permission:cmVhZC1vbmx5";
    // Evaluator verdicts this stream must reproduce verbatim.
    static final String LATER_USER_MESSAGE = "出现后续用户消息，不能归因到本次任务。";
    static final String OWNERSHIP_CHANGED = "查询期间任务归属变化，结果不能归因。";
    // Cadence ports, named once so failure-retry assertions read as policy instead of magic numbers.
    static final long INITIAL_DELAY = DshSessionAdopter.INITIAL_DELAY_MS;
    static final long NOCHANGE_DELAY = DshSessionAdopter.NOCHANGE_DELAY_MS;
    static final long BACKOFF_CAP = DshSessionAdopter.BACKOFF_MAX_MS;

    static DshClient.Config cfg(String token) {
        return new DshClient.Config(true, "https://example.com", token, "connector", "project", "runtime", MODEL, PERM);
    }
    static Map<String, Object> userMessage(String id) {
        return DshClient.mapOf("role", "user", "type", "message", "source", DshClient.mapOf("clientMessageId", id));
    }
    static Map<String, Object> assistantMessage(String status, String text, String turnId) {
        return DshClient.mapOf("role", "assistant", "type", "message", "status", status, "content", text, "turnId", turnId);
    }
    static Map<String, Object> runtimeStatePayload(String status) {
        return DshClient.mapOf("state", DshClient.mapOf("sessionId", "session", "runtimeId", "runtime", "status", status));
    }
    static Map<String, Object> timelinePayload(boolean hasMore, Object... items) {
        return DshClient.mapOf("sessionId", "session", "hasMore", Boolean.valueOf(hasMore), "items", Arrays.asList(items));
    }
    /** The durable slot this device recorded itself: an older local turn. */
    static DshClient.MapStateStore boundStore(String token) {
        DshClient.MapStateStore store = new DshClient.MapStateStore();
        store.saveSession(cfg(token).targetFingerprint(), "session", "msg_local");
        return store;
    }

    // ===== fake timers: recorded delays, zero real wall clock =====
    static final class Timer implements DshProactiveReport.Scheduler {
        static final class Job implements DshProactiveReport.Handle {
            final Runnable task; boolean canceled;
            Job(Runnable task) { this.task = task; }
            @Override public void cancel() { canceled = true; }
        }
        final List<Job> jobs = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        @Override public DshProactiveReport.Handle schedule(Runnable task, long delayMillis) {
            Job job = new Job(task); jobs.add(job); delays.add(Long.valueOf(delayMillis)); return job;
        }
        int live() { int n = 0; for (Job j : jobs) if (!j.canceled) n++; return n; }
        long lastDelay() { return delays.isEmpty() ? -1 : delays.get(delays.size() - 1).longValue(); }
        void runNext() {
            while (!jobs.isEmpty()) {
                Job job = jobs.remove(0);
                if (!job.canceled) { job.task.run(); return; }
            }
            throw new AssertionError("no scheduled poll left");
        }
    }

    // ===== pure policy host: only the adopter is under test =====
    static final class FakeHost implements DshSessionAdopter.Host {
        ToolClient.Binding binding;
        List<String> discovered = new ArrayList<>();
        String tracked = "";
        int starts, stops, discoveries;
        boolean acceptStart = true;
        RuntimeException discoveryFailure;
        boolean keepFailing;
        /** One-shot port failures, consumed by the next call of that port. */
        RuntimeException bindingFailure, trackedFailure, startFailure, stopFailure;
        /** Runs inside the timeline read, standing in for a control call that lands mid-network. */
        Runnable duringDiscovery;
        /** Runs inside the binding read, standing in for a control call that lands just before a port throws. */
        Runnable duringBinding;
        final List<DshProactiveReport.RoundIdentity> adopted = new ArrayList<>();
        @Override public ToolClient.Binding binding() {
            if (duringBinding != null) duringBinding.run();
            if (bindingFailure != null) { RuntimeException failure = bindingFailure; bindingFailure = null; throw failure; }
            return binding;
        }
        @Override public List<String> discoveredMessages(String sessionId) {
            discoveries++;
            if (duringDiscovery != null) duringDiscovery.run();
            if (discoveryFailure != null) { RuntimeException failure = discoveryFailure; discoveryFailure = null; throw failure; }
            if (keepFailing) throw new IllegalStateException("timeline offline");
            return discovered;
        }
        @Override public String trackedMessageId() {
            if (trackedFailure != null) { RuntimeException failure = trackedFailure; trackedFailure = null; throw failure; }
            return tracked;
        }
        @Override public boolean start(DshProactiveReport.RoundIdentity round) {
            starts++;
            if (startFailure != null) { RuntimeException failure = startFailure; startFailure = null; throw failure; }
            if (acceptStart) adopted.add(round);
            return acceptStart;
        }
        @Override public void stop() {
            stops++;
            if (stopFailure != null) { RuntimeException failure = stopFailure; stopFailure = null; throw failure; }
        }
    }
    static ToolClient.Binding binding(String account) { return binding(account, 1L, 0L); }
    static ToolClient.Binding binding(String account, long auth, long selection) {
        return new ToolClient.Binding(account, auth, selection, cfg("token").targetFingerprint(), "session");
    }

    // ===== real durable host (ToolClient over the fake Context/prefs) =====
    static android.content.Context app;
    static ToolClient host;
    static Map<String, Object> prefs;
    static void fillPrefs(Map<String, Object> v) throws Exception {
        v.clear();
        v.put("dsh", Boolean.TRUE); v.put("dsh_user_id", "user"); v.put("dsh_url", "https://example.com");
        v.put("dsh_connector", "connector"); v.put("dsh_project", "project"); v.put("dsh_runtime", "runtime");
        v.put("dsh_model", MODEL); v.put("dsh_permission", PERM);
        v.put("dsh_session_id", "session"); v.put("dsh_client_message_id", "msg_local");
        v.put("dsh_unknown_delivery", Boolean.FALSE);
        v.put("dsh_target_fingerprint", cfg("token").targetFingerprint());
        SecretStore.token = "token"; DshSettings.auth = 1; DshSettings.locked = false;
        Field failed = ToolClient.class.getDeclaredField("dshStorageFailed");
        failed.setAccessible(true); failed.set(null, Boolean.FALSE);
    }
    static void installDisplay(ToolClient client, Timer timer) throws Exception {
        Field singleton = DshProactiveDisplay.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        singleton.set(null, new DshProactiveDisplay(client, timer, SystemClock::elapsedRealtime));
    }

    // ===== fake HTTPS boundary for the real ToolClient/DshClient stack =====
    static String runtimeStatus = "idle", assistantStatus = "done", answerText = "desktop answer";
    /** Optional newest turn injected into the fake timeline, empty when the timeline stays plain. */
    static String extraTurn = "";
    static int gets, posts, stateReads, timelineReads;
    static final class Connection extends HttpURLConnection {
        Connection(URL u) { super(u); }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() throws IOException {
            check(!Thread.holdsLock(DshClient.LOCK), "adoption reads never run inside the host lock");
            // Only GETs happen on the adoption path; a POST would mean an unexpected dispatch.
            if ("POST".equals(getRequestMethod())) posts++; else gets++;
            return 200;
        }
        @Override public InputStream getInputStream() {
            String path = url.getPath();
            if (path.endsWith("/runtime/state")) { stateReads++; return stream(runtimeStatePayload(runtimeStatus)); }
            timelineReads++;
            if (!extraTurn.isEmpty()) {
                return stream(timelinePayload(false,
                    userMessage("msg_local"), assistantMessage(assistantStatus, "local answer", "turn_local"),
                    userMessage("msg_desktop"), assistantMessage(assistantStatus, answerText, "turn_desktop"),
                    userMessage(extraTurn), assistantMessage(assistantStatus, answerText, "turn_third")));
            }
            return stream(timelinePayload(false,
                userMessage("msg_local"), assistantMessage(assistantStatus, "local answer", "turn_local"),
                userMessage("msg_desktop"), assistantMessage(assistantStatus, answerText, "turn_desktop")));
        }
        static InputStream stream(Map<String, Object> payload) {
            return new ByteArrayInputStream(DshClient.stringifyJson(payload).getBytes(StandardCharsets.UTF_8));
        }
    }

    static final class Phone implements DshProactiveDisplay.Listener {
        int updates, terminals; Map<String, Object> last;
        @Override public void onReport(Map<String, Object> report) {
            check(Thread.holdsLock(DshClient.LOCK) || "stopped".equals(report.get("status")), "phone draw stays host-atomic");
            updates++; last = report;
            if ("completed".equals(report.get("status"))) terminals++;
        }
    }

    /** Real production host, wrapped only to count the two lifecycle calls. */
    static final class CountingHost implements DshSessionAdopter.Host {
        final DshSessionAdopter.Host real;
        int starts, stops;
        /** Runs just before the real timeline read, standing in for durable state that moves mid-read. */
        Runnable duringDiscovery;
        CountingHost(DshSessionAdopter.Host real) { this.real = real; }
        @Override public ToolClient.Binding binding() { return real.binding(); }
        @Override public List<String> discoveredMessages(String sessionId) {
            if (duringDiscovery != null) duringDiscovery.run();
            return real.discoveredMessages(sessionId);
        }
        @Override public String trackedMessageId() { return real.trackedMessageId(); }
        @Override public boolean start(DshProactiveReport.RoundIdentity round) { starts++; return real.start(round); }
        @Override public void stop() { stops++; real.stop(); }
    }

    static final class Fixture {
        final android.content.Context app = new android.content.Context();
        final ToolClient host = new ToolClient(app);
        final Map<String, Object> prefs = app.values.get("turboio_settings");
        final Timer monitor = new Timer(), adoption = new Timer();
        final DshProactiveDisplay display;
        final Phone phone = new Phone();
        final CountingHost adapter;
        final DshSessionAdopter adopter;
        Fixture() throws Exception {
            Handler.drain();
            fillPrefs(prefs);
            runtimeStatus = "idle"; assistantStatus = "done"; answerText = "desktop answer";
            extraTurn = "";
            display = new DshProactiveDisplay(host, monitor, SystemClock::elapsedRealtime);
            Field singleton = DshProactiveDisplay.class.getDeclaredField("instance");
            singleton.setAccessible(true); singleton.set(null, display);
            display.subscribe(phone);
            adapter = new CountingHost(ToolClient.adoptionHost(host));
            adopter = new DshSessionAdopter(adapter, adoption, SystemClock::elapsedRealtime);
            adopter.start();
        }
        String trackedTurn() { return String.valueOf(prefs.get("dsh_client_message_id")); }
        void cleanup() { display.stop(); Handler.drain(); display.confirmGlassesClosed(); Handler.drain(); }
    }

    // ===== B1: discovery reads the newest-first user turn out of the bound timeline =====
    static void b1() throws Exception {
        final List<String> urls = new ArrayList<>();
        DshClient client = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) -> {
            urls.add(url);
            return timelinePayload(false, userMessage("msg_local"), userMessageNoSource(),
                assistantMessage("done", "local answer", "t1"), userMessage("msg_desktop"),
                assistantMessage("done", "desktop answer", "t2"));
        });
        List<String> ids = client.timelineUserMessageIds("session");
        check(ids.size() == 2, "B1 discovery keeps only user turns that carry a client message id, got " + ids);
        check("msg_desktop".equals(ids.get(0)), "B1 discovery returns the newest turn first, got " + ids);
        check("msg_local".equals(ids.get(1)), "B1 discovery keeps older turns behind the newest, got " + ids);
        check(urls.size() == 1 && urls.get(0).contains("/api/v2/sessions/session/timeline?mode=latest&limit=200"),
            "B1 discovery reads the latest timeline window exactly once");
    }
    static Map<String, Object> userMessageNoSource() {
        return DshClient.mapOf("role", "user", "type", "message", "content", "no source block");
    }

    // ===== B3: a timeline for another session is never adoption material =====
    static void b3() throws Exception {
        DshClient mismatched = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) ->
            DshClient.mapOf("sessionId", "session-elsewhere", "hasMore", Boolean.FALSE,
                "items", Arrays.asList(userMessage("msg_desktop"))));
        check(mismatched.timelineUserMessageIds("session").isEmpty(), "B3 a timeline bound to another session discovers nothing");
        DshClient bound = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) ->
            timelinePayload(false, userMessage("msg_desktop")));
        check(bound.timelineUserMessageIds("session-other").isEmpty(), "B3 asking for an unbound session discovers nothing");
        check(bound.timelineUserMessageIds("").isEmpty(), "B3 an empty session id discovers nothing");
    }

    // ===== B5: mode=latest truncation only hides older history, discovery still sees the newest turn =====
    static void b5() throws Exception {
        DshClient client = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) ->
            url.contains("runtime/state") ? runtimeStatePayload("idle")
                : timelinePayload(true, userMessage("msg_local"), assistantMessage("done", "local", "t1"),
                    userMessage("msg_desktop"), assistantMessage("done", "desktop", "t2")));
        List<String> ids = client.timelineUserMessageIds("session");
        check(ids.size() == 2 && "msg_desktop".equals(ids.get(0)), "B5 hasMore truncation never hides the newest turn, got " + ids);
        // A truncated page is not an unpaginated window here: the latest window always holds the newest turn.
        Map<String, Object> out = client.statusForMessage("msg_desktop");
        check("completed".equals(out.get("status")), "B5 the latest window can still finish the newest turn, got " + out.get("message"));
        // The same payload through the unbounded-page evaluator must refuse, because that page IS unknown.
        Map<String, Object> blocked = DshClient.evaluateSessionStatus("session", "msg_desktop", runtimeStatePayload("idle"),
            timelinePayload(true, userMessage("msg_local"), assistantMessage("done", "local", "t1"),
                userMessage("msg_desktop"), assistantMessage("done", "desktop", "t2")));
        check("unknown".equals(blocked.get("status")) && String.valueOf(blocked.get("message")).contains("分页"),
            "B5 an incomplete page still blocks a conclusion, got " + blocked.get("message"));
    }

    // ===== B10: statusForMessage locates the turn by the discovered id =====
    static void b10() throws Exception {
        DshClient client = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) ->
            url.contains("runtime/state") ? runtimeStatePayload("idle")
                : timelinePayload(false, userMessage("msg_local"), assistantMessage("done", "local answer", "t1"),
                    userMessage("msg_desktop"), assistantMessage("done", "desktop answer", "t2")));
        Map<String, Object> out = client.statusForMessage("msg_desktop");
        check("completed".equals(out.get("status")), "B10 the discovered desktop turn evaluates to completed, got " + out);
        check("assistant_done_and_idle".equals(out.get("completionEvidence")), "B10 completion keeps its evidence");
        check("desktop answer".equals(out.get("answer")), "B10 only the discovered turn's answer is reported, got " + out.get("answer"));
        check("session".equals(out.get("sessionId")), "B10 the located turn stays inside the bound session");
    }

    // ===== B11: a later user message still blocks attribution of an older turn =====
    static void b11() throws Exception {
        DshClient client = new DshClient(cfg("a"), boundStore("a"), (method, url, token, body) ->
            url.contains("runtime/state") ? runtimeStatePayload("idle")
                : timelinePayload(false, userMessage("msg_local"), assistantMessage("done", "local answer", "t1"),
                    userMessage("msg_desktop"), assistantMessage("done", "desktop answer", "t2")));
        Map<String, Object> out = client.statusForMessage("msg_local");
        check("unknown".equals(out.get("status")), "B11 an older turn is never reported once a newer user message exists");
        check(LATER_USER_MESSAGE.equals(out.get("message")), "B11 single-slot adoption rule keeps the later-user refusal verbatim, got " + out.get("message"));
    }

    // ===== B13: query-time ownership guard =====
    static void b13() throws Exception {
        final DshClient.MapStateStore store = boundStore("a");
        final DshClient.Config config = cfg("a");
        DshClient moved = new DshClient(config, store, (method, url, token, body) -> {
            if (url.contains("runtime/state")) return runtimeStatePayload("idle");
            synchronized (DshClient.LOCK) { store.saveSession(config.targetFingerprint(), "session-moved", "msg_local"); }
            return timelinePayload(false, userMessage("msg_local"), assistantMessage("done", "local", "t1"),
                userMessage("msg_desktop"), assistantMessage("done", "desktop", "t2"));
        });
        Map<String, Object> sessionMove = moved.statusForMessage("msg_desktop");
        check("unknown".equals(sessionMove.get("status")) && OWNERSHIP_CHANGED.equals(sessionMove.get("message")),
            "B13 a session change during the query blocks attribution, got " + sessionMove.get("message"));

        // The adoption read is about the turn it discovered, not the locally stored slot: session and
        // target are the ownership proof, so a locally advanced slot must not invalidate that answer.
        final DshClient.MapStateStore advanced = boundStore("a");
        DshClient slotMoved = new DshClient(config, advanced, (method, url, token, body) -> {
            if (url.contains("runtime/state")) return runtimeStatePayload("idle");
            synchronized (DshClient.LOCK) { advanced.saveSession(config.targetFingerprint(), "session", "msg_next"); }
            return timelinePayload(false, userMessage("msg_local"), assistantMessage("done", "local", "t1"),
                userMessage("msg_desktop"), assistantMessage("done", "desktop", "t2"));
        });
        Map<String, Object> adopted = slotMoved.statusForMessage("msg_desktop");
        check("completed".equals(adopted.get("status")),
            "B13 the discovered turn stays attributable under the same session and target, got " + adopted.get("message"));

        // status() keeps its own slot guard: once a different read advanced the durable record, the
        // stored message id no longer matches and the result must not be attributed.
        final DshClient.MapStateStore own = boundStore("a");
        DshClient ownSlot = new DshClient(config, own, (method, url, token, body) -> {
            if (url.contains("runtime/state")) return runtimeStatePayload("idle");
            synchronized (DshClient.LOCK) { own.saveSession(config.targetFingerprint(), "session", "msg_intruder"); }
            return timelinePayload(false, userMessage("msg_local"), assistantMessage("done", "local", "t1"));
        });
        Map<String, Object> ordinary = ownSlot.status(java.util.Collections.emptyMap());
        check(OWNERSHIP_CHANGED.equals(ordinary.get("message")),
            "B13 the ordinary status guard still refuses once its own slot moved on, got " + ordinary.get("message"));
    }

    // ===== B2: target/config churn blocks adoption =====
    static void b2() throws Exception {
        app = new android.content.Context(); host = new ToolClient(app); prefs = app.values.get("turboio_settings");
        fillPrefs(prefs); installDisplay(host, new Timer());
        ToolClient.Binding ok = host.adoptionBinding();
        check(ok != null && "user".equals(ok.account) && "session".equals(ok.sessionId), "B2 a bound session yields an adoption binding");
        check("msg_local".equals(host.trackedMessageId()), "B2 the tracked turn reads the durable slot");
        check(host.discoveredMessages("session-other").isEmpty(), "B2 an unbound session discovers nothing locally");
        check(host.discoveredMessages("session").contains("msg_desktop"), "B2 the bound session yields its newest turn");
        fillPrefs(prefs); prefs.put("dsh_runtime", "other");
        check(host.adoptionBinding() == null, "B2 a changed target config blocks adoption");
        fillPrefs(prefs); prefs.put("dsh_target_fingerprint", "stale");
        check(host.adoptionBinding() == null, "B2 a stored target that no longer matches blocks adoption");
        fillPrefs(prefs); prefs.put("dsh_unknown_delivery", Boolean.TRUE);
        check(host.adoptionBinding() == null, "B2 an unconfirmed delivery blocks adoption");
        fillPrefs(prefs); prefs.put("dsh", Boolean.FALSE);
        check(host.adoptionBinding() == null, "B2 a disabled tool blocks adoption");
        fillPrefs(prefs); DshSettings.locked = true;
        check(host.adoptionBinding() == null, "B2 a storage lock blocks adoption");
        fillPrefs(prefs); DshSettings.auth++;
        ToolClient.Binding current = host.adoptionBinding();
        check(current != null && current.authGeneration == 2L, "B2 the binding carries the live auth generation");
        check(!(new DshProactiveReport.RoundIdentity("user", 1L, current.selectionGeneration, current.targetFingerprint,
            current.sessionId, "msg_desktop", false)).equals(host.snapshotRound()), "B2 an auth generation change retires the old round");
    }

    // ===== B4: account identity is part of the adoption identity, and never published for a foreign owner =====
    static void b4() throws Exception {
        fillPrefs(prefs); installDisplay(host, new Timer());
        // A real sign-out clears the account and disables the tool, so no owner is left to adopt with.
        prefs.put("dsh_user_id", ""); prefs.put("dsh", Boolean.FALSE);
        check(host.adoptionBinding() == null, "B4 a signed-out device has no adoption owner");
        fillPrefs(prefs); prefs.put("dsh_user_id", "");
        ToolClient.Binding nameless = host.adoptionBinding();
        check(!new DshProactiveReport.RoundIdentity(nameless.account, nameless.authGeneration, nameless.selectionGeneration,
            nameless.targetFingerprint, nameless.sessionId, "msg_desktop", false).valid(),
            "B4 an ownerless binding can never build an attributable round");
        check(!host.adoptRound(new DshProactiveReport.RoundIdentity(nameless.account, nameless.authGeneration,
            nameless.selectionGeneration, nameless.targetFingerprint, nameless.sessionId, "msg_desktop", false)),
            "B4 adoption refuses to publish a turn with no account");
        check("msg_local".equals(prefs.get("dsh_client_message_id")), "B4 the tracked turn survives an ownerless adoption");
        fillPrefs(prefs);
        ToolClient.Binding own = host.adoptionBinding();
        check(!own.sameIdentity(new ToolClient.Binding("other", own.authGeneration, own.selectionGeneration,
            own.targetFingerprint, own.sessionId)), "B4 another account is a different identity");
        check(!own.sameIdentity(new ToolClient.Binding("user", own.authGeneration, own.selectionGeneration,
            "other-target", own.sessionId)), "B4 another target is a different identity");
        check(own.sameIdentity(new ToolClient.Binding("user", 9L, 9L, own.targetFingerprint, own.sessionId)),
            "B4 identity churn is decided by the durable guard, not the generation numbers alone");
        fillPrefs(prefs);
        DshProactiveReport.RoundIdentity foreign = new DshProactiveReport.RoundIdentity("other", 1L,
            ToolClient.selectionGeneration(), own.targetFingerprint, "session", "msg_desktop", false);
        check(!host.adoptRound(foreign), "B4 a turn belonging to another account is refused");
        check("msg_local".equals(prefs.get("dsh_client_message_id")), "B4 a refused adoption never overwrites the tracked turn");
        DshProactiveReport.RoundIdentity otherSession = new DshProactiveReport.RoundIdentity("user", 1L,
            ToolClient.selectionGeneration(), own.targetFingerprint, "session-elsewhere", "msg_desktop", false);
        check(!host.adoptRound(otherSession), "B4 a turn from another session is refused");
        check("msg_local".equals(prefs.get("dsh_client_message_id")), "B4 the tracked turn survives a refused adoption");
    }

    // ===== the durable publish makes the adopted turn the phone's own tracked turn =====
    static void adoptionPublishesTheDiscoveredTurn() throws Exception {
        fillPrefs(prefs); installDisplay(host, new Timer());
        ToolClient.Binding own = host.adoptionBinding();
        DshProactiveReport.RoundIdentity adopted = new DshProactiveReport.RoundIdentity(own.account, own.authGeneration,
            own.selectionGeneration, own.targetFingerprint, own.sessionId, "msg_desktop", false);
        check(!adopted.equals(host.snapshotRound()), "the desktop turn is not yet the tracked turn");
        check(host.adoptRound(adopted), "adoption publishes the desktop turn and starts its monitor");
        check("msg_desktop".equals(host.trackedMessageId()), "the adopted turn becomes the durable tracked turn");
        check(host.isCurrentRound(adopted), "the adopted turn is attributable from durable state");
        check(adopted.equals(host.snapshotRound()), "the phone now tracks exactly the adopted desktop turn");
    }

    // ===== B7: one adoption per turn, no restart on repeat polls =====
    static void b7() {
        FakeHost h = new FakeHost();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer();
        DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime);
        adopter.start();
        check(timer.lastDelay() == 2000L, "B7 discovery arms two seconds after start");
        timer.runNext();
        check(h.starts == 1 && h.adopted.get(0).clientMessageId.equals("msg_desktop"), "B7 the desktop turn is adopted once");
        check(h.adopted.get(0).valid() && !h.adopted.get(0).unknownDelivery, "B7 the adopted round is a valid single-slot round");
        timer.runNext();
        check(h.starts == 1, "B7 a repeat poll never restarts the adopted monitor");
        timer.runNext();
        check(h.starts == 1 && h.stops == 0, "B7 dedupe keeps the single slot stable");
        check(timer.delays.equals(Arrays.asList(Long.valueOf(2000L), Long.valueOf(2000L), Long.valueOf(5000L), Long.valueOf(10000L))),
            "B7 cadence resets after adoption then doubles, got " + timer.delays);
    }

    // ===== B8: identity switch stops the adoption and re-arms under the new owner =====
    static void b8() {
        FakeHost h = new FakeHost();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer();
        DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime);
        adopter.start(); timer.runNext();
        check(h.starts == 1 && h.stops == 0, "B8 adoption runs under the original identity");
        h.binding = binding("other-account");
        timer.runNext();
        check(h.stops == 1, "B8 an account switch stops the adopted monitor exactly once");
        check(h.starts == 1, "B8 the switching poll adopts nothing");
        check(timer.lastDelay() == 2000L, "B8 an identity switch re-arms the initial cadence");
        timer.runNext();
        check(h.starts == 2 && "other-account".equals(h.adopted.get(1).account), "B8 adoption re-arms under the new identity");
        h.binding = binding("user");
        h.discovered = Arrays.asList("msg_third", "msg_desktop", "msg_local");
        timer.runNext();
        check(h.stops == 2 && h.starts == 2, "B8 a target switch stops the previous adoption before adopting");
        timer.runNext();
        check(h.starts == 3 && "msg_third".equals(h.adopted.get(2).clientMessageId), "B8 the newer turn is adopted under the returned identity");
    }

    // ===== B12: the stop button pauses adoption, a fresh look resumes it =====
    static void b12() {
        FakeHost h = new FakeHost();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer();
        DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime);
        adopter.start(); timer.runNext();
        check(h.starts == 1 && !adopter.isPaused(), "B12 adoption is live before the stop button");
        adopter.pause();
        check(adopter.isPaused() && !adopter.isStopped(), "B12 the stop button pauses without destroying the adopter");
        h.discovered = Arrays.asList("msg_third", "msg_desktop", "msg_local");
        timer.runNext();
        check(h.starts == 1 && h.stops == 0, "B12 a paused adopter discovers nothing new");
        check(timer.lastDelay() == 5000L, "B12 a paused adopter keeps a slow re-check, got " + timer.lastDelay());
        adopter.resume();
        check(!adopter.isPaused(), "B12 resuming clears the pause");
        timer.runNext();
        check(h.starts == 2 && "msg_third".equals(h.adopted.get(1).clientMessageId), "B12 resuming adopts the newest turn");
        adopter.stop();
        check(adopter.isStopped(), "B12 stopping ends the adopter");
        check(timer.live() == 0, "B12 stopping cancels the pending poll");
        check(h.starts == 2 && h.stops == 0, "B12 a stopped adopter never polls again");
    }

    /**
     * The control path must not queue behind the timeline read, and a control call that lands while a read
     * is in flight still wins: that read's discovery belongs to the previous generation and is discarded.
     */
    static void controlDuringRead() {
        final DshSessionAdopter[] adopter = new DshSessionAdopter[1];
        final Timer timer = new Timer();
        FakeHost h = new FakeHost();
        // Stands in for the settings thread pressing stop while this read is still running.
        h.duringDiscovery = () -> adopter[0].pause();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        adopter[0] = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime);
        adopter[0].start();
        timer.runNext();
        check(h.starts == 0, "a pause landing during the timeline read discards that discovery");
        check(timer.lastDelay() == 5000L, "the discarded poll re-arms the paused cadence, got " + timer.lastDelay());
        // A lost binding also stops claiming an owner; the monitor's own durable guard retires the session.
        FakeHost lost = new FakeHost();
        lost.binding = binding("user"); lost.tracked = "msg_local";
        lost.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer lostTimer = new Timer();
        DshSessionAdopter adopter2 = new DshSessionAdopter(lost, lostTimer, SystemClock::elapsedRealtime);
        adopter2.start(); lostTimer.runNext();
        check(lost.starts == 1, "adoption runs while the binding is live");
        lost.binding = null;
        lost.discovered = Arrays.asList("msg_fourth", "msg_desktop", "msg_local");
        lostTimer.runNext();
        check(lost.starts == 1 && lost.stops == 0, "an unbound device adopts nothing and invents no stop card");
        check(lostTimer.lastDelay() == 2000L, "a lost binding re-arms the initial cadence");
    }

    static void transientDiscoveryFailureRetries() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local"); h.discoveryFailure = new IllegalStateException("offline");
        Timer timer = new Timer(); DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime); adopter.start();
        timer.runNext();
        check(timer.live() == 1 && timer.lastDelay() == 5000L, "discovery exceptions re-arm bounded retry");
        timer.runNext();
        check(h.starts == 1 && "msg_desktop".equals(h.adopted.get(0).clientMessageId), "discovery resumes after transient failure");
    }
    static void generationChangeDuringReadDoesNotPoisonDedupe() {
        FakeHost h = new FakeHost(); h.binding = binding("user", 1L, 0L); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer(); DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime); adopter.start();
        h.duringDiscovery = () -> h.binding = binding("user", 2L, 0L);
        timer.runNext();
        check(h.starts == 0, "stale auth generation is discarded");
        h.duringDiscovery = null;
        timer.runNext();
        check(h.starts == 1, "same turn retries under fresh auth generation");
        check(h.adopted.get(0).authGeneration == 2L, "fresh generation is used for adoption");
    }
    static void rejectedStartDoesNotPoisonDedupe() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local"; h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer(); DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime); adopter.start();
        h.acceptStart = false; timer.runNext();
        h.acceptStart = true; timer.runNext();
        check(h.starts == 2, "a rejected start is retried");
    }

    static void resumeRestartsTrackedTurn() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local"; h.discovered = Arrays.asList("msg_local");
        Timer timer = new Timer(); DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime); adopter.start(); timer.runNext();
        adopter.pause(); adopter.resume(); timer.runNext();
        check(h.starts == 1, "resume restarts monitor for already-tracked current turn");
    }
    static void idlePollSwitchDoesNotStopExternalMonitor() {
        FakeHost h = new FakeHost(); h.binding = binding("first"); h.tracked = "local-turn"; h.discovered = Arrays.asList("local-turn");
        Timer timer = new Timer(); DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime); adopter.start(); timer.runNext();
        h.binding = binding("second"); h.discovered = Arrays.asList("second-local-turn"); timer.runNext();
        check(h.stops == 0, "identity switch after no adoption does not stop local-dispatch monitor");
    }

    /**
     * Records the exact poll task as well as the delay, so a test can run a duplicate of it. cancel(false)
     * on the background executor cannot stop a task that already started, which is exactly how two polls of
     * one adopter can be in flight together.
     */
    static final class Cloning implements DshProactiveReport.Scheduler {
        final Timer inner = new Timer();
        Runnable pollTask;
        @Override public DshProactiveReport.Handle schedule(Runnable task, long delayMillis) {
            pollTask = task;
            return inner.schedule(task, delayMillis);
        }
    }

    /**
     * The overlapping poll may not take a second timeline read and may not fall silent either: it re-arms a
     * bounded wait and leaves the live read's slot and state alone, so the turn that read discovered is
     * still adopted by the poll that owns the slot.
     */
    static void overlappingPollYieldsToTheLiveRead() {
        FakeHost h = new FakeHost();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Cloning cloning = new Cloning();
        Timer timer = cloning.inner;
        // The duplicate lands inside the live timeline read, holding the same identity and cadence.
        final DshSessionAdopter[] adopter = new DshSessionAdopter[1];
        h.duringDiscovery = () -> { if (cloning.pollTask != null) cloning.pollTask.run(); };
        adopter[0] = new DshSessionAdopter(h, cloning, SystemClock::elapsedRealtime);
        adopter[0].start();
        timer.runNext();
        check(h.discoveries == 1, "the overlapping poll never takes a second timeline read, got " + h.discoveries);
        check(h.starts == 1 && "msg_desktop".equals(h.adopted.get(0).clientMessageId),
            "the live read still adopts the newest turn, got starts=" + h.starts);
        check(timer.delays.equals(Arrays.asList(Long.valueOf(2000L), NOCHANGE_DELAY, Long.valueOf(2000L))),
            "the yielding poll re-arms a bounded wait, got " + timer.delays);
        check(timer.live() == 1, "the overlap leaves exactly one pending poll, got " + timer.live());
        check(!adopter[0].isStopped() && !adopter[0].isPaused(), "an overlap invents no stopped or paused adopter");
    }

    // ===== B14: a throwing host port must never end the polling loop =====
    /**
     * Every host call crosses a transport/storage/display boundary. If one of them throws, this poll has
     * to still arm the next one: a background task that dies with an exception silently ends adoption
     * forever, which is exactly the bug this suite must catch. The adoption state is left untouched, so
     * the next read retries the same candidate instead of losing it.
     */
    static void hostPortFailuresNeverEndPolling() {
        // The binding read throws before the timeline read is even taken.
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local"); h.bindingFailure = new IllegalStateException("prefs offline");
        Timer timer = new Timer(); new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime).start();
        timer.runNext();
        check(h.starts == 0, "B14 a throwing binding read adopts nothing");
        check(timer.live() == 1, "B14 a throwing binding read still arms the next poll");
        check(timer.lastDelay() >= NOCHANGE_DELAY && timer.lastDelay() <= BACKOFF_CAP,
            "B14 a throwing binding read retries under the bounded wait, got " + timer.lastDelay());
        timer.runNext();
        check(h.starts == 1 && "msg_desktop".equals(h.adopted.get(0).clientMessageId), "B14 the same turn is adopted once the binding read recovers");

        // The tracked-turn read throws after the timeline already answered.
        FakeHost tr = new FakeHost(); tr.binding = binding("user"); tr.tracked = "msg_local";
        tr.discovered = Arrays.asList("msg_desktop", "msg_local"); tr.trackedFailure = new IllegalStateException("prefs offline");
        Timer trTimer = new Timer(); new DshSessionAdopter(tr, trTimer, SystemClock::elapsedRealtime).start();
        trTimer.runNext();
        check(tr.starts == 0, "B14 a throwing tracked read adopts nothing");
        check(trTimer.live() == 1, "B14 a throwing tracked read still arms the next poll");
        trTimer.runNext();
        check(tr.starts == 1 && "msg_desktop".equals(tr.adopted.get(0).clientMessageId), "B14 the same turn is adopted once the tracked read recovers");

        // The publish itself throws: the candidate is retried, never marked as adopted.
        FakeHost st = new FakeHost(); st.binding = binding("user"); st.tracked = "msg_local";
        st.discovered = Arrays.asList("msg_desktop", "msg_local"); st.startFailure = new IllegalStateException("display offline");
        Timer stTimer = new Timer(); new DshSessionAdopter(st, stTimer, SystemClock::elapsedRealtime).start();
        stTimer.runNext();
        check(st.starts == 1, "B14 a throwing publish is attempted once");
        check(st.adopted.isEmpty(), "B14 a throwing publish records no adoption");
        check(stTimer.live() == 1, "B14 a throwing publish still arms the next poll");
        stTimer.runNext();
        check(st.starts == 2 && "msg_desktop".equals(st.adopted.get(0).clientMessageId), "B14 the publish is retried under the same identity");
    }

    // ===== B15: a throwing stop is retried, and the loop keeps running =====
    static void stopFailureIsRetried() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer(); new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime).start();
        timer.runNext();
        check(h.starts == 1 && h.stops == 0, "B15 the adoption runs before the switch");
        h.binding = binding("other-account"); h.stopFailure = new IllegalStateException("display offline");
        timer.runNext();
        check(h.stops == 1, "B15 the switch attempts one stop");
        check(h.starts == 1, "B15 the switching poll adopts nothing");
        check(timer.live() == 1, "B15 a throwing stop still arms the next poll");
        check(timer.lastDelay() <= BACKOFF_CAP, "B15 a throwing stop retries within the cap, got " + timer.lastDelay());
        timer.runNext();
        check(h.stops == 2, "B15 an un-retired monitor is stopped again before the new owner adopts");
        timer.runNext();
        check(h.starts == 2 && "other-account".equals(h.adopted.get(1).account), "B15 adoption re-arms under the new identity");
    }

    // ===== B16: repeated port failures stay bounded and never silence the poller =====
    static void repeatedFailuresStayBounded() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_local"); h.keepFailing = true;
        Timer timer = new Timer(); new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime).start();
        for (int i = 0; i < 8; i++) timer.runNext();
        check(h.starts == 0, "B16 a failing discovery never adopts");
        check(timer.live() == 1, "B16 the poller is still alive after eight failures");
        for (long delay : timer.delays) check(delay <= BACKOFF_CAP, "B16 failure retries stay capped, got " + timer.delays);
        h.keepFailing = false;
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        timer.runNext();
        check(h.starts == 1 && "msg_desktop".equals(h.adopted.get(0).clientMessageId), "B16 the newest turn is adopted as soon as the transport recovers");
        check(timer.lastDelay() == INITIAL_DELAY, "B16 a recovery resets the initial cadence, got " + timer.lastDelay());

        // A stop that lands seconds before the port throws must still win: the retry trap may not re-arm
        // a poll for an adopter the user already stopped, or a stopped adopter would keep polling.
        FakeHost stoppedHost = new FakeHost(); stoppedHost.binding = binding("user"); stoppedHost.tracked = "msg_local";
        stoppedHost.bindingFailure = new IllegalStateException("prefs offline");
        final DshSessionAdopter[] stoppedAdopter = new DshSessionAdopter[1];
        stoppedHost.duringBinding = () -> stoppedAdopter[0].stop();
        Timer stoppedTimer = new Timer();
        stoppedAdopter[0] = new DshSessionAdopter(stoppedHost, stoppedTimer, SystemClock::elapsedRealtime);
        stoppedAdopter[0].start();
        stoppedTimer.runNext();
        check(stoppedAdopter[0].isStopped() && stoppedTimer.live() == 0,
            "B16 a stop landing before a port throws cancels the retry, got " + stoppedTimer.live() + " pending polls");
    }

    // ===== B17: a binding that vanishes and returns must resume adoption on its own =====
    static void bindingFlapResumesAdoption() throws Exception {
        runtimeStatus = "running"; assistantStatus = "running"; answerText = "desktop partial";
        Fixture f = new Fixture();
        f.adoption.runNext();
        check(f.adapter.starts == 1 && "msg_desktop".equals(f.trackedTurn()), "B17 the desktop turn is adopted while the binding is live");
        int reports = f.phone.updates;
        // A settings change removes the bound session: no stop card, but the poller must stay armed.
        f.prefs.put("dsh_session_id", "");
        f.adoption.runNext();
        check(f.adapter.stops == 0, "B17 a lost binding invents no stop card");
        check(f.phone.updates == reports, "B17 a lost binding draws nothing new");
        check(f.adoption.live() == 1, "B17 a lost binding keeps the poller alive");
        // The same owner re-binds to a newer turn: the still-armed poller adopts it without any restart.
        f.prefs.put("dsh_session_id", "session");
        extraTurn = "msg_third";
        f.adoption.runNext();
        check(f.adapter.starts == 2 && "msg_third".equals(f.trackedTurn()), "B17 the restored binding adopts the newest turn");
        check(f.adoption.lastDelay() == DshSessionAdopter.INITIAL_DELAY_MS, "B17 a binding flap re-arms the initial cadence");
        extraTurn = "";
        f.cleanup();
    }

    /** A timeline read that keeps failing must retry inside the same bounded window and then recover. */
    static void discoveryFailuresStayBounded() {
        FakeHost h = new FakeHost(); h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_desktop", "msg_local");
        Timer timer = new Timer();
        DshSessionAdopter adopter = new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime);
        adopter.start();
        List<Long> retries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            h.discoveryFailure = new IllegalStateException("offline");
            timer.runNext();
            retries.add(Long.valueOf(timer.lastDelay()));
            check(timer.live() == 1, "a failing discovery keeps exactly one pending poll, got " + timer.live());
        }
        check(h.starts == 0, "a failing timeline never adopts");
        for (Long delay : retries) {
            check(delay.longValue() >= 5000L && delay.longValue() <= 30000L,
                "discovery retries stay inside the bounded backoff window, got " + retries);
        }
        check(!adopter.isStopped() && !adopter.isPaused(), "a transport problem invents no stopped or paused adopter");
        h.discoveryFailure = null;
        timer.runNext();
        check(h.starts == 1 && "msg_desktop".equals(h.adopted.get(0).clientMessageId),
            "adoption recovers after repeated discovery failures");
    }

    /**
     * Requirement 2 against the real durable host, not a double: the auth generation moves during the
     * timeline read while the account, target and session stay the same. Building the round from the
     * pre-read snapshot makes the host refuse it as stale, and a dedupe id recorded before that refusal
     * hides the same turn from every later poll. The poll must re-snapshot the binding after the read and
     * only ever build a round from the current generations, so the turn is still adoptable next poll.
     */
    static void generationChurnDuringReadStillAdopts() throws Exception {
        Fixture f = new Fixture();
        // The first monitor read must see the adopted turn still in flight.
        runtimeStatus = "running"; assistantStatus = "running"; answerText = "desktop partial";
        // A re-login lands exactly while this device is reading the bound session's timeline.
        f.adapter.duringDiscovery = () -> DshSettings.auth++;
        try {
            f.adoption.runNext();
            check("msg_local".equals(f.trackedTurn()),
                "a round built from stale generations is never published, got " + f.trackedTurn());
            check(f.adoption.live() == 1, "the churn poll keeps the poller armed");
            check(f.monitor.live() == 0, "the refused round arms no monitor");
            f.adapter.duringDiscovery = null;
            long auth = f.host.adoptionBinding().authGeneration;
            f.adoption.runNext();
            check("msg_desktop".equals(f.trackedTurn()),
                "the same turn is adopted under the fresh generation, got " + f.trackedTurn());
            check(f.adapter.starts == 1, "only the fresh-generation attempt publishes, got " + f.adapter.starts);
            DshProactiveReport.RoundIdentity now = f.host.snapshotRound();
            check(now != null && now.authGeneration == auth && "msg_desktop".equals(now.clientMessageId),
                "the adopted round carries the current generation, got " + now);
            check(f.host.isCurrentRound(now), "the adopted round stays attributable from durable state");
            check(f.monitor.live() == 1, "the adopted round arms its own monitor");
            f.monitor.runNext(); Handler.drain();
            check("running".equals(f.phone.last.get("status")), "the fresh-generation round reaches the phone card");
            check("msg_desktop".equals(f.phone.last.get("clientMessageId")), "the phone card shows the adopted turn");
        } finally {
            f.adapter.duringDiscovery = null;
            DshSettings.auth = 1;
            f.cleanup();
        }
    }

    /**
     * Requirement 3: is a stop call needed when the binding vanishes while our adopted monitor is live?
     * No. Every null-binding reason (locked storage, unconfirmed delivery, unready or mismatched target,
     * empty session) also makes {@code snapshotRound()} fail, so the monitor's own durable guard retires
     * it silently. A stop would only fabricate the "stopped local monitoring" card for something the user
     * never asked for, so this pins the no-stop decision and proves the stale monitor really goes away.
     */
    static void vanishedBindingRetiresLiveMonitorWithoutAStopCard() throws Exception {
        runtimeStatus = "running"; assistantStatus = "running"; answerText = "desktop partial";
        Fixture f = new Fixture();
        f.adoption.runNext();
        check(f.adapter.starts == 1 && "msg_desktop".equals(f.trackedTurn()), "a live adoption starts one monitor");
        check(f.monitor.live() == 1, "the adopted turn keeps a live local monitor");
        Handler.drain();
        int reports = f.phone.updates;
        // The bound session disappears while that monitor is still running.
        f.prefs.put("dsh_session_id", "");
        f.adoption.runNext();
        check(f.adapter.stops == 0, "a vanished binding stops adoption without stopping the local monitor");
        check(f.phone.updates == reports, "a vanished binding draws nothing new");
        f.monitor.runNext(); Handler.drain();
        check(f.monitor.live() == 0, "the durable guard retires the live monitor, so it cannot go stale");
        check(f.phone.updates == reports, "that retirement draws nothing, so no stopped card is invented");
        check(!"stopped".equals(f.phone.last.get("status")), "no stopped card for a switch the user never made, got " + f.phone.last);
        check(f.phone.terminals == 0, "a vanished binding never reports a terminal result");
        f.cleanup();
    }

    // ===== cadence stays bounded while nothing new arrives =====
    static void cadenceStaysBounded() {
        FakeHost h = new FakeHost();
        h.binding = binding("user"); h.tracked = "msg_local";
        h.discovered = Arrays.asList("msg_local");
        Timer timer = new Timer();
        new DshSessionAdopter(h, timer, SystemClock::elapsedRealtime).start();
        for (int i = 0; i < 6; i++) timer.runNext();
        check(h.starts == 0, "the tracked turn alone is never adopted");
        check(timer.delays.equals(Arrays.asList(Long.valueOf(2000L), Long.valueOf(5000L), Long.valueOf(10000L),
            Long.valueOf(20000L), Long.valueOf(30000L), Long.valueOf(30000L), Long.valueOf(30000L))),
            "no-change discovery doubles up to the thirty second cap, got " + timer.delays);
        h.discovered = new ArrayList<>();
        Timer empty = new Timer();
        new DshSessionAdopter(h, empty, SystemClock::elapsedRealtime).start();
        for (int i = 0; i < 3; i++) empty.runNext();
        check(empty.lastDelay() <= 30000L && h.starts == 0, "an empty timeline only backs off");
    }

    // ===== B6: the whole desktop round flows to the phone card and the glasses =====
    static void b6() throws Exception {
        Fixture f = new Fixture();
        // The first monitor read must see an in-flight desktop turn, not a finished one.
        runtimeStatus = "running"; assistantStatus = "running"; answerText = "desktop partial";
        f.adoption.runNext();
        check(f.adapter.starts == 1, "B6 discovery starts exactly one monitor, got " + f.adapter.starts);
        check("msg_desktop".equals(f.trackedTurn()), "B6 the desktop turn becomes the phone tracked turn");
        check(f.monitor.live() == 1, "B6 the adopted round arms its own monitor timer");
        f.monitor.runNext(); Handler.drain();
        check("running".equals(f.phone.last.get("status")), "B6 interim desktop progress reaches the phone card, got " + f.phone.last);
        check("msg_desktop".equals(f.phone.last.get("clientMessageId")), "B6 the phone card attributes the desktop turn");
        check(!NavGlasses.active(), "B6 an interim round never takes the glasses");
        runtimeStatus = "idle"; assistantStatus = "done"; answerText = "desktop answer";
        f.monitor.runNext(); Handler.drain();
        check("completed".equals(f.phone.last.get("status")), "B6 the terminal desktop result reaches the phone, got " + f.phone.last);
        check(String.valueOf(f.phone.last.get("answer")).contains("desktop answer"), "B6 the phone shows the desktop result");
        check(NavGlasses.active(), "B6 the adopted terminal result reaches the glasses");
        String note = String.valueOf(f.phone.last.get("displayNote"));
        check(!note.isEmpty() && !note.contains("AA"), "B6 the glasses accepted the adopted summary, got " + note);
        check(f.phone.terminals == 1, "B6 one terminal result per adopted round");
        check(f.host.isCurrentRound(f.host.snapshotRound()), "B6 the adopted round stays attributable from durable state");
        f.cleanup();
    }

    // ===== B9: a round that already finished is backfilled on arrival =====
    static void b9() throws Exception {
        runtimeStatus = "idle"; assistantStatus = "done"; answerText = "desktop answer";
        Fixture f = new Fixture();
        int states = stateReads;
        f.adoption.runNext();
        check(f.trackedTurn().equals("msg_desktop") && f.monitor.live() == 1, "B9 arrival arms one monitor for the finished turn");
        f.monitor.runNext(); Handler.drain();
        check("completed".equals(f.phone.last.get("status")), "B9 the finished turn is backfilled on the first poll");
        check(stateReads == states + 1, "B9 backfill needs exactly one status evaluation, got " + (stateReads - states));
        check(f.phone.terminals == 1, "B9 one terminal result for the backfilled turn");
        f.cleanup();
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory((URLStreamHandlerFactory) protocol ->
            "https".equals(protocol) ? new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL u) { return new Connection(u); }
            } : null);
        b1(); b3(); b5(); b10(); b11(); b13();
        b2(); b4(); adoptionPublishesTheDiscoveredTurn();
        b7(); b8(); b12(); cadenceStaysBounded();
        controlDuringRead();
        transientDiscoveryFailureRetries(); generationChangeDuringReadDoesNotPoisonDedupe(); rejectedStartDoesNotPoisonDedupe();
        resumeRestartsTrackedTurn(); idlePollSwitchDoesNotStopExternalMonitor();
        overlappingPollYieldsToTheLiveRead();
        discoveryFailuresStayBounded();
        generationChurnDuringReadStillAdopts();
        hostPortFailuresNeverEndPolling(); stopFailureIsRetried(); repeatedFailuresStayBounded();
        b6(); b9();
        vanishedBindingRetiresLiveMonitorWithoutAStopCard();
        bindingFlapResumesAdoption();
        System.out.println("DshSessionAdopterTest: " + checks + " checks passed");
    }
}
