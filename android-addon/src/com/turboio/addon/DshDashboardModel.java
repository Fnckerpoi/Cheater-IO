package com.turboio.addon;

import java.util.Map;

/** A single-round presentation model. Times are caller-supplied elapsed realtime in milliseconds. */
public final class DshDashboardModel {
    public static final long DEFAULT_FRESHNESS_MS = 15000L;
    public static final int MAX_TARGET = 40, MAX_STATUS = 40, MAX_RESULT = 200;
    public enum Status { SUBMITTED, RUNNING, WAITING_APPROVAL, IDLE_NO_REPLY, COMPLETED,
        FAILED, UNKNOWN, UNKNOWN_DELIVERY, TIMEOUT, STOPPED, QUERY_UNKNOWN }
    /** Immutable presentation only; no round identity is exposed. */
    public static final class View {
        public final Status status;
        public final String targetLine, statusLine, resultLine;
        /** -1 until the first genuine remote observation; local lifecycle events never refresh it. */
        public final long lastObservedMillis;
        public final boolean stale;
        private View(Status status, String target, String result, long observed, boolean stale) {
            this.status = status; this.targetLine = target; this.statusLine = label(status);
            this.resultLine = result; this.lastObservedMillis = observed; this.stale = stale;
        }
    }
    private final long freshnessMillis;
    private DshProactiveReport.RoundIdentity round;
    private long generation, observed = -1L, startedAt, lastTime;
    private String target = "", result = "";
    private Status status = Status.UNKNOWN;
    private boolean stale;
    public DshDashboardModel() { this(DEFAULT_FRESHNESS_MS); }
    public DshDashboardModel(long freshnessMillis) {
        if (freshnessMillis < 0) throw new IllegalArgumentException("freshnessMillis");
        this.freshnessMillis = freshnessMillis;
    }
    /** Starts a new generation, even for the same identity. Alias must be a safe display name. */
    public synchronized long start(DshProactiveReport.RoundIdentity identity, String alias, long now) {
        if (identity == null || !validIdentity(identity) || now < 0 || (round != null && now < lastTime)) throw new IllegalArgumentException("round or time");
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("generation exhausted");
        generation++; round = identity; target = safe(alias, MAX_TARGET); result = "";
        status = identity.unknownDelivery ? Status.UNKNOWN_DELIVERY : Status.SUBMITTED;
        observed = -1L; startedAt = lastTime = now; stale = false; return generation;
    }
    private static boolean validIdentity(DshProactiveReport.RoundIdentity r) {
        return new DshProactiveReport.RoundIdentity(r.account, r.authGeneration, r.selectionGeneration,
            r.targetFingerprint, r.sessionId, r.clientMessageId).valid();
    }
    /**
     * True for changed status/result or recovery from stale. Pass only genuine normalized remote
     * observations here, except local timeout/stopped/network_errors events, which preserve evidence.
     * Identical remote observations refresh lastObservedMillis without changing the visible frame.
     */
    public synchronized boolean accept(long token, DshProactiveReport.RoundIdentity identity,
                                       Map<String, Object> frame, long now) {
        if (!current(token, identity, now) || frame == null || !matches(frame, "sessionId", round.sessionId)
                || !matches(frame, "clientMessageId", round.clientMessageId)) return false;
        if ("unknown".equals(frame.get("status")) && "network_errors".equals(frame.get("stopReason")))
            return connectionUnknown(token, identity, now);
        Status next = parse(frame.get("status"));
        if (next == Status.COMPLETED && !DshProactiveReport.COMPLETION_EVIDENCE.equals(frame.get("completionEvidence")))
            next = Status.UNKNOWN;
        String answer = next == Status.COMPLETED && frame.get("answer") instanceof String
                ? safe((String) frame.get("answer"), MAX_RESULT) : "";
        boolean remote = next != Status.TIMEOUT && next != Status.STOPPED;
        boolean changed = status != next || !result.equals(answer) || (remote && stale);
        status = next; result = answer; lastTime = now;
        if (remote) { observed = now; stale = false; }
        return changed;
    }
    /** Records a local connection/query problem; it never asserts remote failure. */
    public synchronized boolean connectionUnknown(long token, DshProactiveReport.RoundIdentity identity, long now) {
        if (!current(token, identity, now)) return false;
        boolean changed = status != Status.QUERY_UNKNOWN || !result.isEmpty();
        status = Status.QUERY_UNKNOWN; result = ""; lastTime = now;
        return changed;
    }
    /** Expiry latches until a newly accepted observation; reading never refreshes evidence. */
    public synchronized View snapshot(long now) {
        if (now < 0) throw new IllegalArgumentException("time");
        if (round != null && now >= lastTime) {
            lastTime = now;
            if (now - (observed < 0 ? startedAt : observed) > freshnessMillis) stale = true;
        }
        return new View(status, target, result, observed, stale);
    }
    private boolean current(long token, DshProactiveReport.RoundIdentity identity, long now) {
        return round != null && token == generation && round.equals(identity) && now >= lastTime;
    }
    private static boolean matches(Map<String,Object> m, String key, String expected) {
        return !m.containsKey(key) || (m.get(key) instanceof String && expected.equals(m.get(key)));
    }
    private String safe(String value, int max) {
        if (value == null) return "";
        String text = value.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}\\x{80}-\\x{9f}]", " ");
        for (String secret : new String[]{round.account, round.targetFingerprint, round.sessionId, round.clientMessageId})
            if (!secret.isEmpty()) text = text.replace(secret, "[redacted]");
        text = text.trim();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length();) {
            int point = text.codePointAt(i);
            int width = Character.charCount(point);
            i += width;
            // Drop malformed isolated surrogate code units rather than exporting broken UTF-16.
            if (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE) continue;
            if (line.length() + width > max) break;
            line.appendCodePoint(point);
        }
        return line.toString();
    }
    private static Status parse(Object value) {
        if (!(value instanceof String)) return Status.UNKNOWN;
        switch ((String)value) {
            case "submitted": return Status.SUBMITTED;
            case "running": return Status.RUNNING;
            case "waiting_approval": return Status.WAITING_APPROVAL;
            case "idle_no_reply": return Status.IDLE_NO_REPLY;
            case "completed": return Status.COMPLETED;
            case "failed": return Status.FAILED;
            case "unknown_delivery": return Status.UNKNOWN_DELIVERY;
            case "timeout": return Status.TIMEOUT;
            case "stopped": return Status.STOPPED;
            default: return Status.UNKNOWN;
        }
    }
    private static String label(Status s) {
        switch (s) {
            case SUBMITTED: return "已提交"; case RUNNING: return "运行中";
            case WAITING_APPROVAL: return "等待审批"; case IDLE_NO_REPLY: return "空闲，尚无回复";
            case COMPLETED: return "已完成"; case FAILED: return "远端执行失败";
            case UNKNOWN_DELIVERY: return "送达情况未知"; case TIMEOUT: return "本地跟踪超时";
            case STOPPED: return "本地跟踪已停止"; case QUERY_UNKNOWN: return "连接或查询情况未知";
            default: return "远端状态未知";
        }
    }
}
