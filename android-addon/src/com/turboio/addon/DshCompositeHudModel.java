package com.turboio.addon;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes one NavGlasses transcript from live ASR subtitles and the AA board.
 *
 * <p>The firmware exposes a single text block (type 7 opens it, type 5 replaces the transcript, type 3
 * closes it) with at most five lines and 384 characters. There are no coordinates and no compositor
 * regions, so the only way to show both feeds at once is upper ASR lines, one separator line and lower
 * board lines inside the same transcript. Placement on the lens is therefore logical and still needs an
 * optical check on real hardware.
 *
 * <p>Pure model: zero Android imports, no timers and no side effects. Every time is a caller supplied
 * elapsed-realtime millisecond value, so integration code owns the clock. {@link #compose} is
 * deterministic for the same inputs and never mutates the supplied board snapshot.
 */
public final class DshCompositeHudModel {
    /** How long a finalized subtitle stays before it settles back to the board alone. */
    public static final long DEFAULT_SETTLING_MS = 5000L;
    /** Vendor text block limits: five rendered lines, 384 transport characters. */
    public static final int MAX_LINES = 5;
    /** Application cap, deliberately below the 384 character transport limit. */
    public static final int MAX_CHARS = 300;
    /** One rendered line, matching the {@code content_width} requested when the session opens. */
    public static final int MAX_LINE_CHARS = 100;
    /** The upper subtitle keeps one or two lines; the separator and the board get the rest. */
    public static final int MAX_ASR_LINES = 2;
    /** A partial or final phrase is kept up to this many code units so the board always survives. */
    public static final int MAX_ASR_CHARS = 120;

    static final String SEPARATOR = "---";
    static final String LIVE_PREFIX = "[语音] ";
    static final String FINAL_PREFIX = "[识别] ";
    static final String BOARD_TITLE = "AA 看板";
    static final String BOARD_WAITING = "等待本地监听";
    static final String POINTER = "完整结果请到 AA 查看。";
    static final String STALE_MARKER = "（状态可能过期）";

    public enum Phase { IDLE, STREAMING, FINALIZED }

    /** Immutable render result; segments are exposed so tests can assert the two sections apart. */
    public static final class Frame {
        public final String text;
        public final int lineCount;
        public final Phase asrPhase;
        public final String asrSegment;
        public final String dashboardSegment;
        Frame(String text, int lineCount, Phase asrPhase, String asrSegment, String dashboardSegment) {
            this.text = text;
            this.lineCount = lineCount;
            this.asrPhase = asrPhase;
            this.asrSegment = asrSegment;
            this.dashboardSegment = dashboardSegment;
        }
    }

    private final long settlingMillis;
    private String session = "";
    private Phase phase = Phase.IDLE;
    private String asr = "";
    private long finalizedAtMillis = Long.MIN_VALUE;
    /** Time of the accepted phrase; also the monotonic floor, so a replay cannot revive cleared text. */
    private long lastEventMillis = Long.MIN_VALUE;

    public DshCompositeHudModel() { this(DEFAULT_SETTLING_MS); }

    public DshCompositeHudModel(long settlingMillis) {
        if (settlingMillis < 0) throw new IllegalArgumentException("settlingMillis");
        this.settlingMillis = settlingMillis;
    }

    /** Visible so the display layer can arm its own clearing timer at the same boundary. */
    public long settlingMillis() { return settlingMillis; }

    /** Starts a new ASR session token and drops any phrase that belonged to the previous one. */
    public synchronized void startSession(String value) {
        session = value == null ? "" : value;
        phase = Phase.IDLE;
        asr = "";
        finalizedAtMillis = Long.MIN_VALUE;
    }

    /**
     * Applies one vendor ASR callback. Only the current session token is accepted, a partial replaces
     * the previous partial instead of appending, and a final phrase starts the settling window.
     *
     * @return true when the callback changed the subtitle state
     */
    public synchronized boolean acceptAsr(String text, boolean finished, String value, long nowMillis) {
        if (nowMillis < 0) throw new IllegalArgumentException("nowMillis");
        String token = value == null ? "" : value;
        if (token.isEmpty() || !token.equals(session)) return false;
        if (nowMillis < lastEventMillis) return false;
        String cleaned = clip(clean(text), MAX_ASR_CHARS);
        // An empty partial proves nothing; an empty final is allowed and simply settles away.
        if (cleaned.isEmpty() && !finished) return false;
        lastEventMillis = nowMillis;
        asr = cleaned;
        phase = finished ? Phase.FINALIZED : Phase.STREAMING;
        finalizedAtMillis = finished ? nowMillis : Long.MIN_VALUE;
        return true;
    }

    /**
     * Renders the composite transcript for this instant. A finalized subtitle clears once its settling
     * window has elapsed; the board is then rendered alone.
     */
    public synchronized Frame compose(DshDashboardModel.View dashboard, long nowMillis) {
        if (nowMillis < 0) throw new IllegalArgumentException("nowMillis");
        // Both sections expire: a finalized phrase settles on its own, and an abandoned partial must not
        // hold the upper lines forever once the vendor stopped sending for that session.
        if (phase != Phase.IDLE && nowMillis - lastEventMillis >= settlingMillis) {
            phase = Phase.IDLE;
            asr = "";
            // Keep lastEventMillis as the floor: callbacks older than the phrase we already showed stay
            // rejected, so only a genuinely later phrase can restart the upper section after settling.
            finalizedAtMillis = Long.MIN_VALUE;
        }
        boolean asrActive = phase != Phase.IDLE && !asr.isEmpty();
        Sink sink = new Sink();
        StringBuilder asrSegment = new StringBuilder();
        if (asrActive) {
            for (String line : wrap((phase == Phase.FINALIZED ? FINAL_PREFIX : LIVE_PREFIX) + asr, MAX_ASR_LINES)) {
                if (!sink.add(line)) break;
                if (asrSegment.length() > 0) asrSegment.append('\n');
                asrSegment.append(line);
            }
            sink.add(SEPARATOR);
        }
        StringBuilder board = new StringBuilder();
        appendBoard(sink, board, dashboard, asrActive);
        return new Frame(sink.joined(), sink.lineCount(), phase, asrSegment.toString(), board.toString());
    }

    /**
     * Board layout. Essential copy first: the target and status line, then the answer tail, and only
     * then the optional stale marker and the AA pointer, which are the first things dropped.
     */
    private static void appendBoard(Sink sink, StringBuilder out, DshDashboardModel.View view, boolean compact) {
        String target = view == null ? "" : clean(view.targetLine);
        String status = view == null ? "" : clean(view.statusLine);
        String result = view == null ? "" : clean(view.resultLine);
        boolean stale = view != null && view.stale;
        if (target.isEmpty()) target = BOARD_TITLE;
        if (status.isEmpty()) status = BOARD_WAITING;
        String head = compact ? target + " · " + status + (stale ? STALE_MARKER : "") : target;
        addLine(sink, out, clip(head, MAX_LINE_CHARS));
        if (!compact) addLine(sink, out, clip(status + (stale ? STALE_MARKER : ""), MAX_LINE_CHARS));
        if (!result.isEmpty()) {
            // Answers grow at the end, so the visible tail wins over an older head when space is short.
            int room = Math.min(sink.freeChars(Integer.MAX_VALUE), sink.lineSlots() * MAX_LINE_CHARS);
            String tail = tailClean(result, room);
            for (String line : wrap(tail, sink.lineSlots())) addLine(sink, out, line);
        }
        if (!compact) addLine(sink, out, POINTER);
    }

    private static void addLine(Sink sink, StringBuilder out, String line) {
        if (sink.add(line)) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
    }

    /** Keep the last {@code max} code units, never splitting a surrogate pair. */
    private static String tailClean(String value, int max) {
        if (max <= 0 || value.length() <= max) return value;
        int start = value.length() - max;
        while (start < value.length() && Character.isLowSurrogate(value.charAt(start))) start++;
        return value.substring(start);
    }

    /** Split into at most {@code maxLines} lines of at most {@link #MAX_LINE_CHARS} code units. */
    private static List<String> wrap(String value, int maxLines) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < value.length() && lines.size() < maxLines) {
            int end = step(value, start, MAX_LINE_CHARS);
            lines.add(value.substring(start, end));
            start = end;
        }
        return lines;
    }

    /** Keep the first {@code max} code units, dropping a trailing partial surrogate pair. */
    private static String clip(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, step(value, 0, max));
    }

    /** Index after {@code max} code units from {@code start}, so pairs always move whole. */
    private static int step(String value, int start, int max) {
        int index = start;
        while (index < value.length() && index - start < max) index += Character.charCount(value.codePointAt(index));
        return index;
    }

    /** Replace control, format and line-separator characters with a single collapsed space. */
    private static String clean(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); ) {
            int point = value.codePointAt(i);
            int width = Character.charCount(point);
            i += width;
            // Isolated surrogate code units would export broken UTF-16, so they never reach the lens.
            if (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE) continue;
            if (width == 1) {
                char c = (char) point;
                int type = Character.getType(c);
                if (Character.isISOControl(c) || type == Character.FORMAT || type == Character.SPACE_SEPARATOR
                        || c == '\u2028' || c == '\u2029') {
                    pendingSpace = out.length() > 0;
                    continue;
                }
            }
            if (pendingSpace) { out.append(' '); pendingSpace = false; }
            out.appendCodePoint(point);
        }
        return out.toString().trim();
    }

    /** Accumulates rendered lines while enforcing both the line and the character budget. */
    private static final class Sink {
        private final List<String> lines = new ArrayList<>();
        private int chars;

        boolean add(String line) {
            if (line == null || line.isEmpty()) return false;
            int nextLines = lines.size() + 1;
            if (nextLines > MAX_LINES || chars + line.length() + nextLines - 1 > MAX_CHARS) return false;
            lines.add(line);
            chars += line.length();
            return true;
        }

        int lineCount() { return lines.size(); }
        int lineSlots() { return Math.max(0, MAX_LINES - lines.size()); }

        /** Characters still available if every remaining line slot were used, newlines included. */
        int freeChars(int maxExtraLines) {
            int slots = Math.min(maxExtraLines, lineSlots());
            if (slots <= 0) return 0;
            return Math.max(0, MAX_CHARS - chars - lines.size() - slots + 1);
        }

        String joined() {
            StringBuilder text = new StringBuilder(chars + Math.max(0, lines.size() - 1));
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) text.append('\n');
                text.append(lines.get(i));
            }
            return text.toString();
        }
    }
}
