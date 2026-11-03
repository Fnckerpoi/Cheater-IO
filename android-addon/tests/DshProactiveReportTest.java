package com.turboio.addon;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single-round DSH proactive report watcher, tested on the plain JVM with a fake clock, scheduler,
 * status query and display sink. Assertions stay on observable behaviour: which identity each poll
 * carried, how many requests were in flight, which callbacks reached the display, whether a ticket
 * was still displayable at display time, and that success needs real completion evidence.
 */
public final class DshProactiveReportTest {
    static int checks;
    static void ok(boolean value) {
        checks++;
        if (!value) throw new AssertionError("ProactiveReport check " + checks);
    }
    static void eq(Object actual, Object expected) {
        checks++;
        if (!Objects.equals(actual, expected))
            throw new AssertionError("ProactiveReport check " + checks + ": expected " + expected + " got " + actual);
    }

    // Literal budget expectations: 5s poll, 30 min cap. Reading them from the class under test
    // would make the timeout assertions tautological.
    static final long POLL = 5000L;
    static final long DEADLINE = 30L * 60L * 1000L;
    static final String ACCOUNT = "user-1";

    static DshProactiveReport.RoundIdentity round() { return round(1L, 1L, "fpA", "s-1", "m-1"); }
    static DshProactiveReport.RoundIdentity round(long auth, long selection, String fingerprint,
                                                  String sessionId, String messageId) {
        return new DshProactiveReport.RoundIdentity(ACCOUNT, auth, selection, fingerprint, sessionId, messageId);
    }

    static Map<String, Object> remote(String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        return map;
    }
    static Map<String, Object> running(String answer) {
        Map<String, Object> map = remote("running");
        map.put("runtimeState", "running");
        if (answer != null) map.put("answer", answer);
        return map;
    }
    static Map<String, Object> completed(String answer, String evidence) {
        Map<String, Object> map = remote("completed");
        map.put("runtimeState", "idle");
        map.put("completionEvidence", evidence);
        if (answer != null) map.put("answer", answer);
        return map;
    }

    // ---------------------------------------------------------------- fakes

    static final class Clock implements DshProactiveReport.Clock {
        long now;
        @Override public long millis() { return now; }
    }

    /** Deterministic stand-in for the host scheduler: every task is run explicitly by step(). */
    static final class Scheduler implements DshProactiveReport.Scheduler {
        private final Clock clock;
        private final List<Task> tasks = new ArrayList<>();
        /** Slots handed to the test so it can re-deliver an already-scheduled tick. */
        final List<Runnable[]> captured = new ArrayList<>();
        Scheduler(Clock clock) { this.clock = clock; }
        @Override public DshProactiveReport.Handle schedule(Runnable task, long delayMillis) {
            Task entry = new Task(task, clock.now + delayMillis);
            tasks.add(entry);
            for (int i = 0; i < captured.size(); i++) {
                Runnable[] slot = captured.get(i);
                if (slot[0] == null) { slot[0] = task; captured.remove(i); break; }
            }
            return entry;
        }
        /** Runs the earliest live task and moves the fake clock to its due time. */
        boolean step() {
            Task next = null;
            for (Task task : tasks)
                if (!task.cancelled && (next == null || task.due < next.due)) next = task;
            if (next == null) return false;
            tasks.remove(next);
            if (next.due > clock.now) clock.now = next.due;
            next.runnable.run();
            return true;
        }
        int runAll(int limit) {
            int done = 0;
            while (step()) { if (++done > limit) throw new AssertionError("timer runaway"); }
            return done;
        }
        int pending() {
            int live = 0;
            for (Task task : tasks) if (!task.cancelled) live++;
            return live;
        }
        private final class Task implements DshProactiveReport.Handle {
            final Runnable runnable;
            final long due;
            boolean cancelled;
            Task(Runnable runnable, long due) { this.runnable = runnable; this.due = due; }
            @Override public void cancel() { cancelled = true; }
        }
    }

    /** Stands in for the host lock-internal identity check (ToolClient.isCurrentRound). */
    static final class Guard implements DshProactiveReport.IdentityGuard {
        DshProactiveReport.RoundIdentity current;
        int checks;
        final List<String> log;
        Guard(DshProactiveReport.RoundIdentity current, List<String> log) { this.current = current; this.log = log; }
        @Override public boolean isCurrent(DshProactiveReport.RoundIdentity round) {
            checks++;
            log.add("G");
            return round != null && round.equals(current);
        }
    }

    static final class Query implements DshProactiveReport.Query {
        final List<String> log;
        Query(List<String> log) { this.log = log; }
        final List<Object> script = new ArrayList<>();
        final List<DshProactiveReport.RoundIdentity> rounds = new ArrayList<>();
        int calls, inFlight, maxInFlight;
        /** Hook that runs while a request is in flight, to model concurrent host actions. */
        Runnable during;
        @Override public Map<String, Object> run(DshProactiveReport.RoundIdentity round) throws Exception {
            calls++;
            rounds.add(round);
            log.add("Q");
            inFlight++;
            if (inFlight > maxInFlight) maxInFlight = inFlight;
            Object next;
            try {
                if (during != null) during.run();
                next = script.isEmpty() ? running(null) : script.remove(0);
            } finally { inFlight--; }
            if (next instanceof Exception) throw (Exception) next;
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) next;
            return map;
        }
    }

    /**
     * The real sink only accepts the report and posts the actual drawing to the main thread, so the
     * display decision happens later and must re-check the ticket. That hop is modelled here.
     */
    static final class Sink implements DshProactiveReport.Sink {
        static final class Event {
            final String kind;
            final DshProactiveReport.Ticket ticket;
            final Map<String, Object> payload;
            Event(String kind, DshProactiveReport.Ticket ticket, Map<String, Object> payload) {
                this.kind = kind; this.ticket = ticket; this.payload = payload;
            }
            String status() {
                Object value = payload.get("status");
                return value == null ? "" : String.valueOf(value);
            }
            Object get(String key) { return payload.get(key); }
        }
        final List<Event> events = new ArrayList<>();
        final List<String> log;
        Sink(List<String> log) { this.log = log; }
        private final List<Runnable> queue = new ArrayList<>();
        int displayed;
        /** Hook for host actions taken while a callback is being delivered. */
        Runnable during;
        Runnable duringObservation;
        final List<Long> observations = new ArrayList<>();
        final List<Map<String, Object>> observedReports = new ArrayList<>();
        @Override public void onObservation(DshProactiveReport.Ticket ticket, Map<String, Object> report, long observedAtMillis) {
            log.add("O");
            observations.add(Long.valueOf(observedAtMillis));
            observedReports.add(new LinkedHashMap<>(report));
            if (duringObservation != null) duringObservation.run();
        }
        @Override public void onUpdate(DshProactiveReport.Ticket ticket, Map<String, Object> payload) {
            deliver("update", ticket, payload);
        }
        @Override public void onTerminal(DshProactiveReport.Ticket ticket, Map<String, Object> payload) {
            deliver("terminal", ticket, payload);
        }
        private void deliver(String kind, DshProactiveReport.Ticket ticket, Map<String, Object> payload) {
            log.add("S");
            if (during != null) during.run();
            events.add(new Event(kind, ticket, payload));
            queue.add(new Runnable() {
                @Override public void run() { if (ticket.isValid()) displayed++; }
            });
        }
        void drain() {
            List<Runnable> copy = new ArrayList<>(queue);
            queue.clear();
            for (Runnable display : copy) display.run();
        }
        int count(String kind) {
            int n = 0;
            for (Event event : events) if (kind.equals(event.kind)) n++;
            return n;
        }
        Event nth(String kind, int index) {
            int n = 0;
            for (Event event : events) if (kind.equals(event.kind)) { if (n++ == index) return event; }
            throw new AssertionError("no " + kind + " event " + index);
        }
        List<String> statuses(String kind) {
            List<String> out = new ArrayList<>();
            for (Event event : events) if (kind.equals(event.kind)) out.add(event.status());
            return out;
        }
    }

    /** watching() is the host diagnostic: it must go null as soon as local tracking ends. */
    static void watchingOnlyWhileLocallyTracked() {
        Harness h = new Harness();
        ok(h.report.watching() == null);
        ok(h.start());
        ok(h.report.watching().equals(round()));
        h.query.script.add(running("partial"));
        ok(h.pump());
        ok(h.report.watching().equals(round()));
        h.report.stop();
        ok(h.report.watching() == null);
        ok(h.start());
        h.query.script.add(completed("done", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        ok(h.report.watching() == null);
        h.guard.current = null;
        ok(h.start());
        ok(h.report.watching().equals(round()));
        h.guard.current = round(9, 1, "fpA", "s-1", "m-1");
        ok(h.pump());
        ok(h.report.watching() == null);
    }

    static final class Harness {
        final List<String> log = new ArrayList<>();
        final Clock clock = new Clock();
        final Scheduler scheduler = new Scheduler(clock);
        final Guard guard = new Guard(round(), log);
        final Query query = new Query(log);
        final Sink sink = new Sink(log);
        final DshProactiveReport report = new DshProactiveReport(guard, query, sink, scheduler, clock);
        boolean start() { return report.start(round()); }
        /** One timer step; the display hop is drained separately. */
        boolean pump() { return scheduler.step(); }
    }

    /** Every display callback must follow a real identity check for that same request. */
    static boolean everyCallbackIsGuarded(List<String> log) {
        boolean guarded = false;
        for (String entry : log) {
            if ("Q".equals(entry)) guarded = false;
            else if ("G".equals(entry)) guarded = true;
            else if (!guarded) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- tests

    /** running -> waiting approval -> running -> completed: one display per distinct state, once. */
    static void progressFlowDedupsAndCompletesOnlyWithEvidence() {
        Harness h = new Harness();
        ok(h.start());
        ok(h.report.isRunning());
        eq(h.query.calls, 0);
        eq(h.scheduler.pending(), 1);
        h.query.script.add(running("partial"));
        ok(h.pump());
        h.query.script.add(running("partial"));
        ok(h.pump());
        h.query.script.add(remote("waiting_approval"));
        ok(h.pump());
        h.query.script.add(remote("waiting_approval"));
        ok(h.pump());
        h.query.script.add(running("longer partial"));
        ok(h.pump());
        h.query.script.add(completed("final answer", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        eq(h.query.calls, 6);
        eq(h.query.maxInFlight, 1);
        eq(Long.valueOf(h.clock.now), Long.valueOf(25000L));
        eq(h.sink.statuses("update"), new ArrayList<>(java.util.Arrays.asList("running", "waiting_approval", "running")));
        eq(h.sink.count("update"), 3);
        eq(h.sink.count("terminal"), 1);
        Sink.Event done = h.sink.nth("terminal", 0);
        eq(done.status(), "completed");
        eq(done.get("outcome"), "success");
        eq(done.get("answer"), "final answer");
        eq(done.get("completionEvidence"), "assistant_done_and_idle");
        eq(done.get("stopReason"), "remote_state");
        eq(done.get("keepLock"), Boolean.FALSE);
        eq(done.get("glassesEligible"), Boolean.TRUE);
        eq(done.get("sessionId"), "s-1");
        eq(done.get("untrusted_external_data"), Boolean.TRUE);
        ok(done.ticket.isValid());
        ok(done.ticket.round().equals(round()));
        ok(everyCallbackIsGuarded(h.log));
        // Every poll guards before the request and again after the network answer, and each display
        // callback carries one more guard check. Observations guard before and after delivery.
        eq(Integer.valueOf(h.guard.checks), Integer.valueOf(2 * h.query.calls + h.sink.events.size()
            + 2 * h.sink.observations.size()));
        h.sink.drain();
        eq(h.sink.displayed, 4);
        // Terminal is final: no further request, no second display, timer gone.
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
        ok(!h.pump());
        eq(h.query.calls, 6);
        eq(h.sink.count("terminal"), 1);
    }

    /** submitted / approval wait / idle-without-reply all keep polling and never claim completion. */
    static void interimStatesKeepPollingWithoutTerminal() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(remote("submitted"));
        h.query.script.add(remote("session_selected"));
        h.query.script.add(remote("idle_no_reply"));
        h.query.script.add(remote("idle_no_reply"));
        h.query.script.add(running("partial"));
        for (int i = 0; i < 5; i++) ok(h.pump());
        eq(h.query.calls, 5);
        eq(h.sink.count("terminal"), 0);
        eq(h.sink.statuses("update"),
            new ArrayList<>(java.util.Arrays.asList("submitted", "session_selected", "idle_no_reply", "running")));
        for (Sink.Event event : h.sink.events) ok(!"success".equals(event.get("outcome")));
        eq(h.scheduler.pending(), 1);
        ok(h.report.isRunning());
    }

    /** Nothing but a real completed+evidence result may look like success. */
    static void otherStatusesNeverReportSuccess() {
        String[] statuses = {"failed", "unknown", "unknown_delivery", "ended", "locked", "released",
            "no_session", "succeeded", "", "weird_status"};
        for (String status : statuses) {
            Harness h = new Harness();
            ok(h.start());
            Map<String, Object> answer = remote(status);
            StringBuilder longMessage = new StringBuilder();
            for (int i = 0; i < 4000; i++) longMessage.append('x');
            answer.put("message", longMessage.toString());
            answer.put("answer", longMessage.toString());
            h.query.script.add(answer);
            ok(h.pump());
            eq(h.sink.count("terminal"), 1);
            Sink.Event terminal = h.sink.nth("terminal", 0);
            ok(!"success".equals(terminal.get("outcome")));
            ok(!"completed".equals(terminal.status()));
            eq(terminal.get("glassesEligible"), Boolean.FALSE);
            eq(terminal.get("stopReason"), "remote_state");
            String remoteMessage = String.valueOf(terminal.get("remoteMessage"));
            ok(remoteMessage.length() <= DshProactiveReport.MAX_REMOTE_TEXT_LENGTH + 1);
            String copiedAnswer = terminal.get("answer") == null ? "" : String.valueOf(terminal.get("answer"));
            ok(copiedAnswer.length() <= DshProactiveReport.MAX_ANSWER_COPY_LENGTH);
            eq("unknown_delivery".equals(status) ? Boolean.TRUE : Boolean.FALSE, terminal.get("keepLock"));
            eq(h.scheduler.pending(), 0);
            ok(!h.report.isRunning());
            int calls = h.query.calls;
            ok(!h.pump());
            eq(h.query.calls, calls);
        }
        // Failure wording must stay a failure, approval/unknown stay attributable as non-success.
        Harness failed = new Harness();
        ok(failed.start());
        failed.query.script.add(remote("failed"));
        ok(failed.pump());
        eq(failed.sink.nth("terminal", 0).get("outcome"), "failure");
        eq(failed.sink.nth("terminal", 0).status(), "failed");
    }

    static void completedWithoutRealEvidenceIsNotSuccess() {
        Object[] evidence = {"", "partial", "assistant_done_only", null};
        for (Object value : evidence) {
            Harness h = new Harness();
            ok(h.start());
            Map<String, Object> answer = completed("looks finished", null);
            answer.put("completionEvidence", value);
            h.query.script.add(answer);
            ok(h.pump());
            eq(h.sink.count("terminal"), 1);
            Sink.Event terminal = h.sink.nth("terminal", 0);
            eq(terminal.status(), "unknown");
            ok(!"success".equals(terminal.get("outcome")));
            eq(terminal.get("glassesEligible"), Boolean.FALSE);
            ok(!terminal.payload.containsKey("completionEvidence"));
            eq(h.scheduler.pending(), 0);
            ok(!h.pump());
        }
    }

    /** An empty answer may not be turned into invented text for the phone or the glasses. */
    static void completedWithoutAnswerTextNeverFabricatesAnswer() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(completed(null, DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        Sink.Event terminal = h.sink.nth("terminal", 0);
        eq(terminal.status(), "completed");
        eq(terminal.get("outcome"), "success");
        ok(!terminal.payload.containsKey("answer"));
        eq(terminal.get("glassesEligible"), Boolean.FALSE);
    }

    /** Three consecutive transport failures stop; an interleaved good answer resets the streak. */
    static void threeConsecutiveNetworkErrorsStopAsUnknown() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(new IOException("net-1"));
        h.query.script.add(new IOException("net-2"));
        h.query.script.add(running("partial"));
        h.query.script.add(new IOException("net-3"));
        h.query.script.add(new IOException("net-4"));
        for (int i = 0; i < 5; i++) { ok(h.pump()); ok(h.report.isRunning()); }
        eq(h.query.calls, 5);
        eq(h.sink.count("terminal"), 0);
        eq(h.sink.count("update"), 1);
        h.query.script.add(new IOException("net-5"));
        ok(h.pump());
        eq(h.sink.count("terminal"), 1);
        Sink.Event terminal = h.sink.nth("terminal", 0);
        eq(terminal.status(), "unknown");
        eq(terminal.get("stopReason"), "network_errors");
        eq(terminal.get("remoteStatus"), "network_error");
        eq(terminal.get("outcome"), "unknown");
        eq(Integer.valueOf(DshProactiveReport.MAX_CONSECUTIVE_NETWORK_ERRORS), Integer.valueOf(3));
        eq(h.query.calls, 6);
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
        ok(!h.pump());
        eq(h.query.calls, 6);
    }

    static void deadlineStopsAndReportsUnknownState() {
        Harness h = new Harness();
        ok(h.start());
        int steps = h.scheduler.runAll(5000);
        eq(h.sink.count("terminal"), 1);
        Sink.Event terminal = h.sink.nth("terminal", 0);
        eq(terminal.status(), "timeout");
        eq(terminal.get("stopReason"), "deadline");
        ok(!"success".equals(terminal.get("outcome")));
        eq(terminal.get("glassesEligible"), Boolean.FALSE);
        eq(Long.valueOf(h.clock.now), Long.valueOf(1800000L));
        eq(Integer.valueOf(h.query.calls), Integer.valueOf(360));
        eq(Integer.valueOf(steps), Integer.valueOf(361));
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
    }

    /** Account, auth generation, selection generation, target, session and message changes all drop. */
    static void identityChangesDropReturnedResults() {
        // Two scripted answers: a terminal one and an interim one, both after the identity moved.
        for (int variant = 0; variant < 2; variant++) identityDropFor(variant == 0
            ? completed("answer", DshProactiveReport.COMPLETION_EVIDENCE) : running("partial answer"));
    }
    static void identityDropFor(Map<String, Object> answer) {
        DshProactiveReport.RoundIdentity[] changed = {
            new DshProactiveReport.RoundIdentity("user-2", 1, 1, "fpA", "s-1", "m-1"),
            round(2, 1, "fpA", "s-1", "m-1"),
            round(1, 2, "fpA", "s-1", "m-1"),
            round(1, 1, "fpB", "s-1", "m-1"),
            round(1, 1, "fpA", "s-2", "m-1"),
            round(1, 1, "fpA", "s-1", "m-2"),
        };
        for (DshProactiveReport.RoundIdentity other : changed) {
            Harness h = new Harness();
            ok(h.start());
            h.query.script.add(answer);
            h.query.during = new Runnable() {
                @Override public void run() { h.guard.current = other; }
            };
            ok(h.pump());
            eq(h.query.calls, 1);
            eq(h.sink.events.size(), 0);
            h.sink.drain();
            eq(h.sink.displayed, 0);
            eq(h.scheduler.pending(), 0);
            ok(!h.report.isRunning());
            ok(!h.pump());
            eq(h.query.calls, 1);
        }
    }

    /** A guard that is already stale before the timer fires must not touch the network at all. */
    static void staleIdentityNeverPolls() {
        Harness h = new Harness();
        ok(h.start());
        h.guard.current = round(1, 2, "fpB", "s-1", "m-1");
        h.guard.current = round(1, 3, "fpA", "s-1", "m-1"); // ABA: same field values, newer generation
        ok(h.pump());
        eq(h.query.calls, 0);
        eq(h.sink.events.size(), 0);
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
        ok(!h.pump());
    }

    /** Switching target away and back must not resurrect the old round. */
    static void targetSwitchBackStillInvalidatesOldRound() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(completed("old answer", DshProactiveReport.COMPLETION_EVIDENCE));
        h.query.during = new Runnable() {
            @Override public void run() {
                h.guard.current = round(1, 2, "fpB", "s-1", "m-1");
                h.guard.current = round(1, 3, "fpA", "s-1", "m-1");
            }
        };
        ok(h.pump());
        eq(h.query.calls, 1);
        eq(h.sink.events.size(), 0);
        eq(h.scheduler.pending(), 0);
        ok(!h.pump());
        eq(h.query.calls, 1);
    }

    /** stop() invalidates the ticket, so a display already queued on the main thread must not draw. */
    static void stopInvalidatesQueuedDisplayAndTimers() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(completed("final", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        eq(h.sink.count("terminal"), 1);
        DshProactiveReport.Ticket ticket = h.sink.nth("terminal", 0).ticket;
        ok(ticket.isValid());
        h.report.stop();
        ok(!ticket.isValid());
        h.sink.drain();
        eq(h.sink.displayed, 0);
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
        eq(h.query.calls, 1);
        ok(!h.pump());
        eq(h.query.calls, 1);
    }

    /** A stop while a poll is queued kills the timer and the earlier update ticket. */
    static void stopCancelsPendingPollAndOldUpdate() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("partial"));
        ok(h.pump());
        DshProactiveReport.Ticket stale = h.sink.nth("update", 0).ticket;
        eq(h.scheduler.pending(), 1);
        h.report.stop();
        eq(h.scheduler.pending(), 0);
        ok(!stale.isValid());
        h.sink.drain();
        eq(h.sink.displayed, 0);
        ok(!h.pump());
        eq(h.query.calls, 1);
    }

    /** A new submission replaces the watcher: old tickets die, only the new round is displayed. */
    static void newStartReplacesOldWatcher() {
        Harness h = new Harness();
        DshProactiveReport.RoundIdentity second = round(1, 2, "fpB", "s-2", "m-2");
        ok(h.start());
        h.query.script.add(running("old partial"));
        ok(h.pump());
        DshProactiveReport.Ticket stale = h.sink.nth("update", 0).ticket;
        h.guard.current = second;
        ok(h.report.start(second));
        eq(h.scheduler.pending(), 1);
        ok(!stale.isValid());
        h.query.script.add(completed("new answer", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        eq(h.query.calls, 2);
        eq(h.query.rounds.get(1), second);
        eq(h.sink.count("terminal"), 1);
        eq(h.sink.nth("terminal", 0).get("answer"), "new answer");
        h.sink.drain();
        eq(h.sink.displayed, 1);
    }

    /** A submission that lands mid-request retires the in-flight result instead of merging it. */
    static void startDuringInFlightQueryReplacesWatcher() {
        Harness h = new Harness();
        final DshProactiveReport.RoundIdentity second = round(1, 2, "fpB", "s-2", "m-2");
        ok(h.start());
        h.query.script.add(completed("stale answer", DshProactiveReport.COMPLETION_EVIDENCE));
        h.query.during = new Runnable() {
            @Override public void run() {
                h.guard.current = second;
                h.report.start(second);
            }
        };
        ok(h.pump());
        eq(h.query.calls, 1);
        eq(h.sink.events.size(), 0);
        eq(h.scheduler.pending(), 1);
        h.query.during = null; // the host does not keep re-submitting on every later request
        h.query.script.add(running("new partial"));
        ok(h.pump());
        eq(h.query.calls, 2);
        eq(h.query.rounds.get(1), second);
        eq(h.sink.statuses("update"), new ArrayList<>(java.util.Arrays.asList("running")));
    }

    /** One request at a time: a timer tick during a request finds nothing armed and adds no call. */
    static void noSecondRequestWhileOneIsInFlight() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("partial"));
        final int[] nested = new int[1];
        h.query.during = new Runnable() {
            @Override public void run() { while (h.scheduler.step()) nested[0]++; }
        };
        ok(h.pump());
        eq(nested[0], 0);
        eq(h.query.calls, 1);
        eq(h.query.maxInFlight, 1);
        eq(h.scheduler.pending(), 1);
        for (int i = 0; i < 10; i++) {
            h.query.script.add(running("partial " + i));
            ok(h.pump());
        }
        eq(h.query.calls, 11);
        eq(Long.valueOf(h.clock.now), Long.valueOf(50000L));
        eq(h.query.maxInFlight, 1);
        ok(h.report.start(round()));
        eq(h.scheduler.pending(), 1);
    }

    /**
     * A host timer may deliver a late duplicate tick (a queued callback that cancel() missed). That
     * must not open a second concurrent request for the same round.
     */
    static void duplicateTickWhileInFlightDoesNotAddARequest() {
        Harness h = new Harness();
        // Arm the capture first so the tick that start() scheduled can be re-delivered by hand.
        final Runnable[] tick = new Runnable[1];
        final boolean[] used = new boolean[1];
        h.scheduler.captured.add(tick);
        ok(h.start());
        ok(tick[0] != null);
        h.query.script.add(running("partial"));
        h.query.during = new Runnable() {
            @Override public void run() {
                if (tick[0] != null && !used[0]) {
                    used[0] = true;
                    tick[0].run(); // a queued callback that the cancel() missed
                    tick[0].run();
                }
            }
        };
        ok(h.pump());
        eq(Integer.valueOf(h.query.calls), Integer.valueOf(1));
        eq(Integer.valueOf(h.query.maxInFlight), Integer.valueOf(1));
        h.query.during = null;
        ok(h.pump());
        eq(Integer.valueOf(h.query.calls), Integer.valueOf(2));
        h.query.script.add(running("partial 2"));
        ok(h.pump());
        eq(Integer.valueOf(h.query.calls), Integer.valueOf(3));
    }

    /** Queries and callbacks must not hold the watcher lock, so a foreign stop() cannot stall. */
    static void queryAndSinkRunWithoutHoldingTheWatcherLock() throws Exception {
        Harness h = new Harness();
        ok(h.start());
        final boolean[] returnedDuringQuery = new boolean[1];
        h.query.during = new Runnable() {
            @Override public void run() { returnedDuringQuery[0] = stopFromOtherThread(h.report); }
        };
        h.query.script.add(completed("answer", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        ok(returnedDuringQuery[0]);
        eq(h.sink.events.size(), 0);
        eq(h.scheduler.pending(), 0);
        ok(!h.pump());

        Harness again = new Harness();
        ok(again.start());
        final boolean[] returnedDuringSink = new boolean[1];
        again.sink.during = new Runnable() {
            @Override public void run() { returnedDuringSink[0] = stopFromOtherThread(again.report); }
        };
        again.query.script.add(completed("answer", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(again.pump());
        ok(returnedDuringSink[0]);
        eq(again.sink.count("terminal"), 1);
        eq(again.scheduler.pending(), 0);
        ok(!again.pump());
    }

    static boolean stopFromOtherThread(final DshProactiveReport report) {
        Thread worker = new Thread(new Runnable() {
            @Override public void run() { report.stop(); }
        });
        worker.start();
        try { worker.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return !worker.isAlive();
    }

    /** An incomplete round (no account, target, session or message) starts no request. */
    static void incompleteIdentityNeverStarts() {
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 400; i++) oversized.append('f');
        DshProactiveReport.RoundIdentity[] rejected = {
            null,
            new DshProactiveReport.RoundIdentity("", 1, 1, "fpA", "s-1", "m-1"),
            round(1, 1, "", "s-1", "m-1"),
            round(1, 1, "fpA", "", "m-1"),
            round(1, 1, "fpA", "s-1", ""),
            round(1, 1, oversized.toString(), "s-1", "m-1"),
        };
        for (DshProactiveReport.RoundIdentity identity : rejected) {
            Harness h = new Harness();
            ok(!h.report.start(identity));
            ok(!h.report.isRunning());
            eq(h.scheduler.pending(), 0);
            ok(!h.pump());
            eq(h.query.calls, 0);
            eq(h.sink.events.size(), 0);
        }
    }

    /** The identity object is the only thing the display layer ever sees: it never carries a secret. */
    static void roundIdentityCarriesNoCredential() {
        for (Field field : DshProactiveReport.RoundIdentity.class.getDeclaredFields())
            ok(!field.getName().toLowerCase().contains("token"));
        ok(!round().toString().contains("sk-"));
        ok(round().equals(round()));
        ok(!round().equals(round(1, 2, "fpA", "s-1", "m-1")));
        eq(round().hashCode(), round().hashCode());
        eq(new DshProactiveReport.RoundIdentity(" user-1 ", 1, 1, " fpA ", " s-1 ", " m-1 "), round());
    }

    /** An unconfirmed submission is not attributable, so it can neither start nor survive a flip. */
    static void unknownDeliveryRoundCannotBeWatched() {
        Harness h = new Harness();
        DshProactiveReport.RoundIdentity locked =
            new DshProactiveReport.RoundIdentity(ACCOUNT, 1, 1, "fpA", "s-1", "m-1", true);
        ok(!h.report.start(locked));
        ok(!h.report.isRunning());
        eq(h.query.calls, 0);
        eq(h.sink.events.size(), 0);
        // A round that was clean at start but flips to unknown delivery during a poll is dropped.
        Harness live = new Harness();
        ok(live.start());
        live.query.script.add(completed("answer", DshProactiveReport.COMPLETION_EVIDENCE));
        live.query.during = new Runnable() {
            @Override public void run() {
                live.guard.current =
                    new DshProactiveReport.RoundIdentity(ACCOUNT, 1, 1, "fpA", "s-1", "m-1", true);
            }
        };
        ok(live.pump());
        eq(live.query.calls, 1);
        eq(live.sink.events.size(), 0);
        ok(!live.pump());
        eq(live.query.calls, 1);
    }

    /** stop then start again keeps the watcher usable and the old display dead. */
    static void stopThenStartAgainWorksAndIsIsolated() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("first round partial"));
        ok(h.pump());
        h.report.stop();
        h.sink.drain();
        eq(h.sink.displayed, 0);
        ok(h.start());
        h.query.script.add(completed("second round", DshProactiveReport.COMPLETION_EVIDENCE));
        ok(h.pump());
        eq(h.query.calls, 2);
        eq(h.sink.count("terminal"), 1);
        eq(h.sink.nth("terminal", 0).get("answer"), "second round");
        h.sink.drain();
        eq(h.sink.displayed, 1);
        ok(!h.pump());
    }

    static void hashCollisionStillDisplaysChangedAnswer() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("Aa"));
        h.query.script.add(running("BB"));
        ok(h.pump());
        ok(h.pump());
        eq(h.sink.count("update"), 2);
        eq(h.sink.nth("update", 1).get("answer"), "BB");
    }

    static void lateCompletedResponseTimesOut() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(completed("late answer", DshProactiveReport.COMPLETION_EVIDENCE));
        h.query.during = new Runnable() {
            @Override public void run() { h.clock.now = DEADLINE; }
        };
        ok(h.pump());
        eq(h.sink.count("terminal"), 1);
        Sink.Event terminal = h.sink.nth("terminal", 0);
        eq(terminal.status(), "timeout");
        eq(terminal.get("stopReason"), "deadline");
        eq(terminal.get("remoteStatus"), "not_evaluated");
        ok(!"success".equals(terminal.get("outcome")));
        eq(terminal.get("glassesEligible"), Boolean.FALSE);
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
    }

    static void staleIdentityWinsOverLateTimeout() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(completed("late answer", DshProactiveReport.COMPLETION_EVIDENCE));
        h.query.during = new Runnable() {
            @Override public void run() {
                h.clock.now = DEADLINE;
                h.guard.current = round(1, 2, "fpB", "s-1", "m-1");
            }
        };
        ok(h.pump());
        eq(h.sink.events.size(), 0);
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());
    }

    static void observationsReflectValidQueryCompletion() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("same"));
        h.query.script.add(running("same"));
        h.query.during = new Runnable() { public void run() { h.clock.now += 17; } };
        ok(h.pump());
        ok(h.pump());
        eq(h.sink.observations, java.util.Arrays.asList(17L, 5034L));
        eq(h.sink.count("update"), 1);
        eq(h.sink.observedReports.get(1).get("status"), "running");
        h.query.script.add(remote("waiting_approval"));
        ok(h.pump());
        eq(h.sink.observedReports.get(2).get("status"), "waiting_approval");
        h.query.script.add(completed("final", "missing_proof"));
        ok(h.pump());
        eq(h.sink.observedReports.get(3).get("status"), "unknown");
        eq(h.sink.observedReports.get(3).get("outcome"), "unknown");
        eq(h.sink.observedReports.get(3).get("glassesEligible"), Boolean.FALSE);

        Harness failed = new Harness();
        ok(failed.start());
        failed.query.script.add(new IOException());
        failed.query.script.add(new IOException());
        failed.query.script.add(new IOException());
        ok(failed.pump());
        ok(failed.pump());
        ok(failed.pump());
        eq(failed.sink.nth("terminal", 0).get("stopReason"), "network_errors");
        Harness absent = new Harness();
        ok(absent.start());
        absent.query.script.add(null);
        ok(absent.pump());
        eq(absent.sink.observations.size(), 0);
        eq(failed.sink.observations.size(), 0);

        Harness late = new Harness();
        ok(late.start());
        late.query.during = new Runnable() { public void run() { late.clock.now = DEADLINE; } };
        ok(late.pump());
        eq(late.sink.observations.size(), 0);

        Harness normalizing = new Harness();
        ok(normalizing.start());
        Map<String, Object> slowFrame = new LinkedHashMap<String, Object>(running("old")) {
            @Override public Object get(Object key) {
                normalizing.clock.now = DEADLINE;
                return super.get(key);
            }
        };
        normalizing.query.script.add(slowFrame);
        ok(normalizing.pump());
        eq(normalizing.sink.observations.size(), 0);
        eq(normalizing.sink.nth("terminal", 0).status(), "timeout");

        Harness stale = new Harness();
        ok(stale.start());
        stale.query.during = new Runnable() { public void run() { stale.guard.current = null; } };
        ok(stale.pump());
        eq(stale.sink.observations.size(), 0);
    }

    static void observationCanStopOrReplaceWithoutEvaluating() {
        Harness stopped = new Harness();
        ok(stopped.start());
        stopped.query.script.add(completed("old", DshProactiveReport.COMPLETION_EVIDENCE));
        stopped.sink.duringObservation = new Runnable() { public void run() { ok(stopFromOtherThread(stopped.report)); } };
        ok(stopped.pump());
        eq(stopped.sink.observations.size(), 1);
        eq(stopped.sink.events.size(), 0);
        eq(stopped.scheduler.pending(), 0);

        Harness replaced = new Harness();
        ok(replaced.start());
        replaced.query.script.add(completed("old", DshProactiveReport.COMPLETION_EVIDENCE));
        replaced.sink.duringObservation = new Runnable() { public void run() { replaced.report.start(round()); } };
        ok(replaced.pump());
        eq(replaced.sink.observations.size(), 1);
        eq(replaced.sink.events.size(), 0);
        eq(replaced.scheduler.pending(), 1);

        Harness switched = new Harness();
        ok(switched.start());
        switched.query.script.add(running("old"));
        switched.sink.duringObservation = new Runnable() { public void run() { switched.guard.current = null; } };
        ok(switched.pump());
        eq(switched.sink.events.size(), 0);
        eq(switched.scheduler.pending(), 0);
    }

    /** Running-update path: a stop or replacement delivered during onObservation suppresses onUpdate. */
    static void runningUpdateObservationSuppressesAfterStopOrReplacement() {
        Harness stopped = new Harness();
        ok(stopped.start());
        stopped.query.script.add(running("partial"));
        stopped.sink.duringObservation = new Runnable() {
            @Override public void run() { ok(stopFromOtherThread(stopped.report)); }
        };
        ok(stopped.pump());
        eq(stopped.sink.observations.size(), 1);
        eq(stopped.sink.observedReports.get(0).get("status"), "running");
        eq(stopped.sink.count("update"), 0);
        eq(stopped.sink.count("terminal"), 0);
        eq(stopped.scheduler.pending(), 0);
        ok(!stopped.report.isRunning());

        Harness replaced = new Harness();
        ok(replaced.start());
        replaced.query.script.add(running("old partial"));
        replaced.sink.duringObservation = new Runnable() {
            @Override public void run() { replaced.report.start(round()); }
        };
        ok(replaced.pump());
        eq(replaced.sink.observations.size(), 1);
        eq(replaced.sink.observedReports.get(0).get("answer"), "old partial");
        eq(replaced.sink.count("update"), 0);
        eq(replaced.sink.count("terminal"), 0);
        eq(replaced.scheduler.pending(), 1);
        replaced.sink.duringObservation = null;
        replaced.query.script.add(running("new partial"));
        ok(replaced.pump());
        eq(replaced.query.calls, 2);
        eq(replaced.sink.observations.size(), 2);
        eq(replaced.sink.count("update"), 1);
        eq(replaced.sink.nth("update", 0).get("answer"), "new partial");
    }

    /** Deadline reached while onObservation runs: the reply is never evaluated, timeout wins. */
    static void observationDeadlineCrossingSuppressesEvaluation() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("partial"));
        h.sink.duringObservation = new Runnable() {
            @Override public void run() { h.clock.now = DEADLINE; }
        };
        ok(h.pump());
        eq(h.sink.observations.size(), 1);
        eq(h.sink.observedReports.get(0).get("status"), "running");
        eq(h.sink.count("update"), 0);
        eq(h.sink.count("terminal"), 1);
        Sink.Event timedOut = h.sink.nth("terminal", 0);
        eq(timedOut.status(), "timeout");
        eq(timedOut.get("stopReason"), "deadline");
        eq(timedOut.get("remoteStatus"), "not_evaluated");
        eq(h.scheduler.pending(), 0);
        ok(!h.report.isRunning());

        Harness done = new Harness();
        ok(done.start());
        done.query.script.add(completed("late answer", DshProactiveReport.COMPLETION_EVIDENCE));
        done.sink.duringObservation = new Runnable() {
            @Override public void run() { done.clock.now = DEADLINE; }
        };
        ok(done.pump());
        eq(done.sink.observations.size(), 1);
        eq(done.sink.observedReports.get(0).get("status"), "completed");
        eq(done.sink.count("terminal"), 1);
        Sink.Event late = done.sink.nth("terminal", 0);
        eq(late.status(), "timeout");
        ok(!"success".equals(late.get("outcome")));
        eq(late.get("stopReason"), "deadline");
        eq(late.get("remoteStatus"), "not_evaluated");
        eq(done.scheduler.pending(), 0);
    }

    /**
     * onObservation is an integration boundary: implementations must not throw. If one does, the
     * watcher is retired cleanly (no evaluation of that reply, no timers left) and the original
     * failure propagates unchanged — never swallowed and never mapped into a report.
     */
    static void observationFailureRetiresCleanlyAndPropagates() {
        Harness h = new Harness();
        ok(h.start());
        h.query.script.add(running("partial"));
        final RuntimeException broken = new RuntimeException("observation_tap_broken");
        h.sink.duringObservation = new Runnable() {
            @Override public void run() { throw broken; }
        };
        Throwable surfaced = null;
        try { h.pump(); } catch (Throwable failure) { surfaced = failure; }
        ok(surfaced == broken);
        eq(h.sink.count("update"), 0);
        eq(h.sink.count("terminal"), 0);
        ok(!h.report.isRunning());
        ok(h.report.watching() == null);
        eq(h.scheduler.pending(), 0);
        ok(!h.pump());
        eq(h.query.calls, 1);

        Harness done = new Harness();
        ok(done.start());
        done.query.script.add(completed("answer", DshProactiveReport.COMPLETION_EVIDENCE));
        final RuntimeException terminalTapBroken = new RuntimeException("observation_tap_broken_terminal");
        done.sink.duringObservation = new Runnable() {
            @Override public void run() { throw terminalTapBroken; }
        };
        surfaced = null;
        try { done.pump(); } catch (Throwable failure) { surfaced = failure; }
        ok(surfaced == terminalTapBroken);
        eq(done.sink.count("update"), 0);
        eq(done.sink.count("terminal"), 0);
        ok(!done.report.isRunning());
        eq(done.scheduler.pending(), 0);
        ok(!done.pump());
        eq(done.query.calls, 1);
    }

    public static void main(String[] args) throws Exception {
        observationsReflectValidQueryCompletion();
        observationCanStopOrReplaceWithoutEvaluating();
        runningUpdateObservationSuppressesAfterStopOrReplacement();
        observationDeadlineCrossingSuppressesEvaluation();
        observationFailureRetiresCleanlyAndPropagates();
        staleIdentityWinsOverLateTimeout();
        lateCompletedResponseTimesOut();
        hashCollisionStillDisplaysChangedAnswer();
        progressFlowDedupsAndCompletesOnlyWithEvidence();
        interimStatesKeepPollingWithoutTerminal();
        otherStatusesNeverReportSuccess();
        completedWithoutRealEvidenceIsNotSuccess();
        completedWithoutAnswerTextNeverFabricatesAnswer();
        threeConsecutiveNetworkErrorsStopAsUnknown();
        deadlineStopsAndReportsUnknownState();
        identityChangesDropReturnedResults();
        staleIdentityNeverPolls();
        targetSwitchBackStillInvalidatesOldRound();
        stopInvalidatesQueuedDisplayAndTimers();
        stopCancelsPendingPollAndOldUpdate();
        newStartReplacesOldWatcher();
        startDuringInFlightQueryReplacesWatcher();
        noSecondRequestWhileOneIsInFlight();
        duplicateTickWhileInFlightDoesNotAddARequest();
        queryAndSinkRunWithoutHoldingTheWatcherLock();
        incompleteIdentityNeverStarts();
        unknownDeliveryRoundCannotBeWatched();
        roundIdentityCarriesNoCredential();
        stopThenStartAgainWorksAndIsIsolated();
        watchingOnlyWhileLocallyTracked();
        System.out.println("DshProactiveReportTest: " + checks + " passed");
    }
}
