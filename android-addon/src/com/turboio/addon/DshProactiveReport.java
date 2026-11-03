package com.turboio.addon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Watcher for ONE submitted DSH round: it polls that round's status, shows meaningful changes on the
 * phone, and reports the final result once. Deliberately free of Android types so the whole policy
 * runs on a plain JVM against fake ports.
 *
 * <p>Lifecycle: {@link #start(RoundIdentity)} keeps a single watcher. A new start or {@link #stop()}
 * retires the previous one and invalidates its {@link Ticket}, so already-queued callbacks and older
 * timers can never display. Each round has at most one request in flight, polled every
 * {@link #POLL_INTERVAL_MS} until {@link #DEADLINE_MS} elapses, stopping early after
 * {@link #MAX_CONSECUTIVE_NETWORK_ERRORS} consecutive transport failures.
 *
 * <p>Attribution: before each request and after every response the injected {@link IdentityGuard}
 * must still accept the round. The host answers that from real durable state inside its own lock
 * (account, auth generation, selection generation, target fingerprint, session, client message id),
 * so an account switch, a target switch and back, or a new message in the same session drops the old
 * round silently: no display, no retry.
 *
 * <p>{@link Sink#onTerminal} fires at most once per watcher and only for a real terminal decision.
 * {@code completed} is success only with {@link #COMPLETION_EVIDENCE}; waiting approval,
 * idle-without-reply, failure, unknown, unknown delivery, timeout and network stop are never success.
 *
 * <p>A sink may only enqueue the actual drawing (main thread), so it must re-check
 * {@link Ticket#isValid()} at draw time. Identity guard calls, network queries and sink callbacks all
 * run outside this watcher's own lock, and this watcher never takes the host lock itself. The host
 * therefore must not call {@link #start(RoundIdentity)} or {@link #stop()} while holding the same lock
 * its {@link IdentityGuard} needs, or the guard and a poll could wait on each other.
 */
public final class DshProactiveReport {
    public static final long POLL_INTERVAL_MS = 5000L;
    public static final long DEADLINE_MS = 30L * 60L * 1000L;
    public static final int MAX_CONSECUTIVE_NETWORK_ERRORS = 3;
    /** The only evidence that a round is really finished; mirrors DshClient's completion proof. */
    public static final String COMPLETION_EVIDENCE = "assistant_done_and_idle";
    public static final int MAX_REMOTE_TEXT_LENGTH = 300;
    public static final int MAX_ANSWER_COPY_LENGTH = 500;
    public static final int MAX_ID_LENGTH = 200;

    /** Injectable time source; the host should use elapsed realtime, not wall clock. */
    public interface Clock {
        long millis();
    }

    /** Injectable timer. Must be non-blocking and must never run a cancelled handle. */
    public interface Scheduler {
        Handle schedule(Runnable task, long delayMillis);
    }
    public interface Handle {
        void cancel();
    }

    /** Status read for one exact round. Transport problems must be thrown, never mapped to failed. */
    public interface Query {
        Map<String, Object> run(RoundIdentity round) throws Exception;
    }

    /** Host answer to "is this still the real, durable round?"; read inside the host lock. */
    public interface IdentityGuard {
        boolean isCurrent(RoundIdentity round);
    }

    /**
     * Display port. Implementations may return before anything is visible, so the display layer owns
     * the final decision by re-checking {@link Ticket#isValid()} when it actually draws.
     */
    public interface Sink {
        /**
         * Fresh normalized remote frame, timestamped at successful query completion. Implementations
         * must not throw: this is an integration boundary. A throw retires local tracking cleanly
         * without evaluating that reply and propagates the original failure unchanged.
         */
        default void onObservation(Ticket ticket, Map<String, Object> report, long observedAtMillis) {}
        void onUpdate(Ticket ticket, Map<String, Object> report);
        void onTerminal(Ticket ticket, Map<String, Object> report);
    }

    /**
     * Identity of one round. Carries no credential material: the bearer token and raw config stay in
     * the host, only the already-published target fingerprint travels here.
     */
    public static final class RoundIdentity {
        public final String account;
        public final long authGeneration;
        public final long selectionGeneration;
        public final String targetFingerprint;
        public final String sessionId;
        public final String clientMessageId;
        /** True when this round's submission could not be confirmed; such a round is unwatchable. */
        public final boolean unknownDelivery;

        public RoundIdentity(String account, long authGeneration, long selectionGeneration,
                             String targetFingerprint, String sessionId, String clientMessageId) {
            this(account, authGeneration, selectionGeneration, targetFingerprint, sessionId, clientMessageId, false);
        }

        public RoundIdentity(String account, long authGeneration, long selectionGeneration,
                             String targetFingerprint, String sessionId, String clientMessageId,
                             boolean unknownDelivery) {
            this.account = trim(account);
            this.authGeneration = authGeneration;
            this.selectionGeneration = selectionGeneration;
            this.targetFingerprint = trim(targetFingerprint);
            this.sessionId = trim(sessionId);
            this.clientMessageId = trim(clientMessageId);
            this.unknownDelivery = unknownDelivery;
        }

        /** True when the tuple is complete enough to attribute one display to one real submission. */
        public boolean valid() {
            if (authGeneration < 0 || selectionGeneration < 0) return false;
            if (unknownDelivery) return false;
            if (!bounded(account) || !bounded(targetFingerprint) || !bounded(sessionId) || !bounded(clientMessageId))
                return false;
            return !account.isEmpty() && !targetFingerprint.isEmpty() && !sessionId.isEmpty()
                && !clientMessageId.isEmpty();
        }

        private static boolean bounded(String value) {
            if (value.length() > MAX_ID_LENGTH) return false;
            for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
            return true;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RoundIdentity)) return false;
            RoundIdentity o = (RoundIdentity) other;
            return authGeneration == o.authGeneration && selectionGeneration == o.selectionGeneration
                && unknownDelivery == o.unknownDelivery
                && account.equals(o.account) && targetFingerprint.equals(o.targetFingerprint)
                && sessionId.equals(o.sessionId) && clientMessageId.equals(o.clientMessageId);
        }
        @Override public int hashCode() {
            return Objects.hash(account, Long.valueOf(authGeneration), Long.valueOf(selectionGeneration),
                targetFingerprint, sessionId, clientMessageId, Boolean.valueOf(unknownDelivery));
        }
        /** Log-safe form: no secret exists in this object, so the ids are safe to print. */
        @Override public String toString() {
            return "RoundIdentity{account=" + account + ", auth=" + authGeneration + ", selection="
                + selectionGeneration + ", target=" + targetFingerprint + ", session=" + sessionId
                + ", message=" + clientMessageId + ", unknownDelivery=" + unknownDelivery + "}";
        }
    }

    /** Validity token handed to the display layer for one watcher generation. */
    public static final class Ticket {
        private final DshProactiveReport owner;
        private final RoundIdentity round;
        private final long generation;
        Ticket(DshProactiveReport owner, RoundIdentity round, long generation) {
            this.owner = owner; this.round = round; this.generation = generation;
        }
        /** The round this report belongs to; safe to compare against host state for logs. */
        public RoundIdentity round() { return round; }
        /** Monotonic watcher generation for logs; never a secret. */
        public long generation() { return generation; }
        /** False once stop(), a newer start(), or a stale drop retired this watcher: do not draw. */
        public boolean isValid() { return owner.isTicketLive(this); }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static String clamp(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return max <= 3 ? value.substring(0, max) : value.substring(0, max - 3) + "...";
    }

    private final Object lock = new Object();
    private final IdentityGuard identity;
    private final Query query;
    private final Sink sink;
    private final Scheduler scheduler;
    private final Clock clock;

    /** Generation of the live watcher; start/stop/drop advance it so old tickets expire. */
    private long epoch;
    private Session current;

    private static final class Session {
        final RoundIdentity round;
        final long epoch;
        final long deadlineAt;
        Handle handle;
        boolean finished;
        boolean awaitingResult;
        boolean terminalSent;
        int networkErrors;
        Map<String, Object> lastUpdate;
        Session(RoundIdentity round, long epoch, long deadlineAt) {
            this.round = round; this.epoch = epoch; this.deadlineAt = deadlineAt;
        }
    }

    public DshProactiveReport(IdentityGuard identity, Query query, Sink sink, Scheduler scheduler, Clock clock) {
        if (identity == null || query == null || sink == null || scheduler == null || clock == null)
            throw new IllegalArgumentException("wiring_required");
        this.identity = identity; this.query = query; this.sink = sink; this.scheduler = scheduler; this.clock = clock;
    }

    /**
     * Begins watching one submitted round, replacing any earlier watcher. Only a round the host has
     * already durably recorded as submitted may start here; an incomplete identity starts nothing.
     */
    public boolean start(RoundIdentity round) {
        if (round == null || !round.valid()) return false;
        synchronized (lock) {
            epoch++;
            retireLocked();
            Session session = new Session(round, epoch, clock.millis() + DEADLINE_MS);
            current = session;
            // The first read is immediate; each response re-arms the next poll.
            session.handle = scheduler.schedule(new PollTask(session), 0L);
            return true;
        }
    }

    /** Stops local tracking only. The remote round keeps running and stays under user control. */
    public void stop() {
        synchronized (lock) {
            epoch++;
            retireLocked();
            current = null;
        }
    }

    public boolean isRunning() {
        synchronized (lock) { return current != null && !current.finished; }
    }

    /**
     * Read-only view of the round still under local tracking; null once it ended, was stopped or was
     * dropped as stale. Never performs network I/O, so the host can log it safely.
     */
    public RoundIdentity watching() {
        synchronized (lock) { return current == null || current.finished ? null : current.round; }
    }

    boolean isTicketLive(Ticket ticket) {
        synchronized (lock) { return current != null && current.epoch == epoch && current.epoch == ticket.generation; }
    }

    private void retireLocked() {
        if (current != null && current.handle != null) {
            current.handle.cancel();
            current.handle = null;
        }
    }

    private final class PollTask implements Runnable {
        private final Session session;
        PollTask(Session session) { this.session = session; }
        @Override public void run() { poll(session); }
    }

    private void poll(Session session) {
        // Claim this round's single in-flight slot; a retired or finished watcher does nothing.
        synchronized (lock) {
            if (!isLiveLocked(session) || session.awaitingResult) return;
            session.awaitingResult = true;
            if (session.handle != null) { session.handle.cancel(); session.handle = null; }
        }
        // Guard and clock stay OUTSIDE the lock: the guard answers from the host lock.
        if (!identity.isCurrent(session.round)) { dropStale(session); return; }
        if (clock.millis() >= session.deadlineAt) {
            Map<String, Object> report = base(session, "timeout", "unknown", "deadline");
            report.put("remoteStatus", "not_queried");
            report.put("message", "本地跟踪已超过 30 分钟上限并停止；远端任务可能仍在继续，请手动查询。");
            finish(session, report);
            return;
        }
        Map<String, Object> remote = null;
        boolean transportFailure = false;
        try {
            remote = query.run(session.round);
        } catch (Exception e) {
            transportFailure = true;
        }
        long observedAtMillis = clock.millis();
        // Responses are external data: re-check the real identity before anything is displayed.
        if (!identity.isCurrent(session.round)) { dropStale(session); return; }
        if (clock.millis() >= session.deadlineAt) {
            Map<String, Object> report = base(session, "timeout", "unknown", "deadline");
            report.put("remoteStatus", "not_evaluated");
            report.put("message", "本地跟踪已超过 30 分钟上限并停止；远端任务可能仍在继续，请手动查询。");
            finish(session, report);
            return;
        }
        if (transportFailure) handleNetworkError(session);
        else evaluate(session, remote, observedAtMillis);
    }

    /** Consecutive transport failures only stop local tracking; they never claim a remote failure. */
    private void handleNetworkError(Session session) {
        int streak;
        synchronized (lock) {
            if (!isLiveLocked(session)) return;
            session.awaitingResult = false;
            session.networkErrors++;
            streak = session.networkErrors;
        }
        if (streak >= MAX_CONSECUTIVE_NETWORK_ERRORS) {
            // A transport problem is not a remote failure: report unknown state, never "failed".
            Map<String, Object> report = base(session, "unknown", "unknown", "network_errors");
            report.put("remoteStatus", "network_error");
            report.put("message", "连续 " + streak + " 次状态查询失败，本地已停止跟踪；远端任务仍在继续，请手动查询。");
            finish(session, report);
            return;
        }
        arm(session);
    }

    private void evaluate(Session session, Map<String, Object> remote, long observedAtMillis) {
        String status = remote == null ? "" : text(remote.get("status"));
        String runtimeState = remote == null ? "" : text(remote.get("runtimeState"));
        String answer = remote == null ? "" : text(remote.get("answer"));
        String message = remote == null ? "" : text(remote.get("message"));
        String evidence = remote == null ? "" : text(remote.get("completionEvidence"));
        synchronized (lock) { if (isLiveLocked(session)) session.networkErrors = 0; }

        boolean interim = "submitted".equals(status) || "running".equals(status)
            || "waiting_approval".equals(status) || "idle_no_reply".equals(status)
            || "session_selected".equals(status);
        if (interim) {
            Map<String, Object> report = base(session, status, "interim", null);
            report.put("runtimeState", clamp(runtimeState, MAX_ID_LENGTH));
            if (!answer.isEmpty()) report.put("answer", clamp(answer, MAX_ANSWER_COPY_LENGTH));
            if (!message.isEmpty()) report.put("remoteMessage", clamp(message, MAX_REMOTE_TEXT_LENGTH));
            update(session, report, observedAtMillis);
            return;
        }
        if ("completed".equals(status)) {
            if (!COMPLETION_EVIDENCE.equals(evidence)) {
                // A "completed" label without the assistant-done-and-idle proof proves nothing.
                Map<String, Object> report = base(session, "unknown", "unknown", "remote_state");
                report.put("remoteStatus", status);
                report.put("message", "远端自称完成但缺少完成证据，不能按成功回报，请手动核对。");
                finish(session, report, remote == null ? null : Long.valueOf(observedAtMillis));
                return;
            }
            Map<String, Object> report = base(session, "completed", "success", "remote_state");
            report.put("completionEvidence", COMPLETION_EVIDENCE);
            report.put("runtimeState", clamp(runtimeState, MAX_ID_LENGTH));
            // Empty answer is never replaced by invented text, so it also cannot go to the glasses.
            report.put("glassesEligible", Boolean.valueOf(!answer.isEmpty()));
            if (!answer.isEmpty()) report.put("answer", clamp(answer, MAX_ANSWER_COPY_LENGTH));
            finish(session, report, remote == null ? null : Long.valueOf(observedAtMillis));
            return;
        }
        // failed / unknown / unknown_delivery and anything unrecognised end local tracking as non-success.
        Map<String, Object> report = base(session, status.isEmpty() ? "unknown" : status,
            "failed".equals(status) ? "failure" : "unknown", "remote_state");
        report.put("remoteStatus", status.isEmpty() ? "absent" : clamp(status, MAX_ID_LENGTH));
        if (!message.isEmpty()) report.put("remoteMessage", clamp(message, MAX_REMOTE_TEXT_LENGTH));
        if (!answer.isEmpty()) report.put("answer", clamp(answer, MAX_ANSWER_COPY_LENGTH));
        // Unknown delivery keeps the host lock and the audit record; nothing here re-dispatches.
        report.put("keepLock", Boolean.valueOf("unknown_delivery".equals(status)));
        finish(session, report, remote == null ? null : Long.valueOf(observedAtMillis));
    }

    private Map<String, Object> base(Session session, String status, String outcome, String stopReason) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", status);
        report.put("outcome", outcome);
        if (stopReason != null) report.put("stopReason", stopReason);
        report.put("sessionId", session.round.sessionId);
        report.put("generation", Long.valueOf(session.epoch));
        report.put("keepLock", Boolean.FALSE);
        report.put("glassesEligible", Boolean.FALSE);
        report.put("untrusted_external_data", Boolean.TRUE);
        return report;
    }

    /** Each fresh remote read carries its normalized frame, independent of display deduplication. */
    private boolean observe(Session session, Map<String, Object> report, long observedAtMillis) {
        if (!observationCurrent(session)) return false;
        Ticket ticket;
        synchronized (lock) {
            if (!isLiveLocked(session)) return false;
            ticket = new Ticket(this, session.round, session.epoch);
        }
        try {
            sink.onObservation(ticket, report, observedAtMillis);
        } catch (RuntimeException | Error failure) {
            // Integration boundary: a throwing tap must never leave the watcher stuck or let the
            // reply be evaluated after a failed callback. Retire cleanly, then propagate the
            // original failure unchanged — never swallowed, never mapped into a report.
            dropStale(session);
            throw failure;
        }
        return observationCurrent(session);
    }

    private boolean observationCurrent(Session session) {
        if (!identity.isCurrent(session.round)) { dropStale(session); return false; }
        synchronized (lock) { if (!isLiveLocked(session)) return false; }
        if (clock.millis() >= session.deadlineAt) {
            Map<String, Object> report = base(session, "timeout", "unknown", "deadline");
            report.put("remoteStatus", "not_evaluated");
            report.put("message", "本地跟踪已超过 30 分钟上限并停止；远端任务可能仍在继续，请手动查询。");
            finish(session, report);
            return false;
        }
        return true;
    }

    /** Meaningful-change display: compare the exact displayed fields. */
    private void update(Session session, Map<String, Object> report, long observedAtMillis) {
        if (!observe(session, report, observedAtMillis)) return;
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("status", report.get("status"));
        key.put("runtimeState", report.get("runtimeState"));
        key.put("answer", report.get("answer"));
        key.put("remoteMessage", report.get("remoteMessage"));
        Ticket ticket = null;
        synchronized (lock) {
            if (!isLiveLocked(session)) return;
            session.awaitingResult = false;
            if (!key.equals(session.lastUpdate)) {
                session.lastUpdate = key;
                ticket = new Ticket(this, session.round, session.epoch);
            }
            armLocked(session);
        }
        // A display only happens for a round the host still recognises as the real one.
        if (ticket != null && identity.isCurrent(session.round)) sink.onUpdate(ticket, report);
        else if (ticket != null) dropStale(session);
    }

    /** Terminal display, at most once per watcher, then no more requests and no more timers. */
    private void finish(Session session, Map<String, Object> report) {
        finish(session, report, null);
    }

    private void finish(Session session, Map<String, Object> report, Long observedAtMillis) {
        if (observedAtMillis != null && !observe(session, report, observedAtMillis.longValue())) return;
        Ticket ticket;
        synchronized (lock) {
            if (!isLiveLocked(session) || session.terminalSent) return;
            session.awaitingResult = false;
            session.finished = true;
            session.terminalSent = true;
            ticket = new Ticket(this, session.round, session.epoch);
            retireLocked();
        }
        // Final re-check: never hand a result to the display of a round the host no longer owns.
        if (!identity.isCurrent(session.round)) { dropStale(session); return; }
        sink.onTerminal(ticket, report);
    }

    /** Identity moved on while we were working: drop the round without displaying or retrying. */
    private void dropStale(Session session) {
        synchronized (lock) {
            if (current != session) return;
            session.awaitingResult = false;
            session.finished = true;
            epoch++;
            retireLocked();
            current = null;
        }
    }

    private void arm(Session session) {
        synchronized (lock) { armLocked(session); }
    }
    private void armLocked(Session session) {
        if (!isLiveLocked(session)) return;
        session.handle = scheduler.schedule(new PollTask(session), POLL_INTERVAL_MS);
    }

    private boolean isLiveLocked(Session session) {
        return current == session && session.epoch == epoch && !session.finished;
    }
}
