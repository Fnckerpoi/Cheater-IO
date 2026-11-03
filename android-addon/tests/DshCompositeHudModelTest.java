package com.turboio.addon;

import java.util.HashMap;
import java.util.Map;

/**
 * C1-C7 for the pure composite HUD: ASR subtitle lifecycle plus the AA board in one bounded lens
 * transcript. No Android, no timers and no test framework, so it runs on a plain JVM.
 */
public final class DshCompositeHudModelTest {
    private static int checks, failures;
    private static final DshProactiveReport.RoundIdentity ROUND = new DshProactiveReport.RoundIdentity(
        "account-private", 1, 1, "target-private", "session-private", "message-private");

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) { failures++; System.err.println("FAIL: " + reason); }
    }

    private static Map<String,Object> frame(String status, String answer) {
        Map<String,Object> value = new HashMap<>();
        value.put("status", status);
        if (answer != null) {
            value.put("answer", answer);
            value.put("completionEvidence", DshProactiveReport.COMPLETION_EVIDENCE);
        }
        return value;
    }

    /** Real board snapshots; the composite model may never invent presentation fields. */
    private static DshDashboardModel.View view(String status, String answer, long at) {
        DshDashboardModel model = new DshDashboardModel();
        long token = model.start(ROUND, "AA 终端", at);
        model.accept(token, ROUND, frame(status, answer), at + 1);
        return model.snapshot(at + 1);
    }

    private static String repeat(char value, int times) {
        StringBuilder out = new StringBuilder(times);
        for (int i = 0; i < times; i++) out.append(value);
        return out.toString();
    }

    private static String[] lines(String text) { return text.split("\n", -1); }

    /** Index of the separator line, or -1 when the composite has no ASR section. */
    private static int separatorIndex(String text) {
        String[] parts = lines(text);
        for (int i = 0; i < parts.length; i++) if (parts[i].trim().equals("---")) return i;
        return -1;
    }

    private static boolean bounded(String text, int lineCount) {
        return text.length() <= DshCompositeHudModel.MAX_CHARS && lineCount >= 1
            && lineCount <= DshCompositeHudModel.MAX_LINES;
    }

    /** Output must be well-formed UTF-16: supplementary characters survive whole or not at all. */
    private static boolean surrogateSafe(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    private static void emptyInputs() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        DshCompositeHudModel.Frame empty = hud.compose(null, 0);
        check(empty != null && !empty.text.isEmpty(), "C1 a null board still renders a placeholder");
        check(empty.lineCount == lines(empty.text).length, "C1 frame line count matches the transcript");
        check(bounded(empty.text, empty.lineCount), "C1 placeholder fits 5 lines and 300 code units");
        check(empty.asrPhase == DshCompositeHudModel.Phase.IDLE && empty.asrSegment.isEmpty(),
            "C1 a fresh model has no ASR segment");
        check(!empty.dashboardSegment.isEmpty(), "C1 an empty board still has a dashboard segment");

        DshCompositeHudModel.Frame idle = hud.compose(view("running", null, 10), 11);
        check(bounded(idle.text, idle.lineCount), "C1 an interim board without an answer stays bounded");
        check(idle.text.contains("AA 终端") && idle.text.contains("运行中"), "C1 idle board keeps target and status");
        check(idle.text.contains("完整结果请到 AA 查看。"), "C1 idle board keeps the AA pointer");
    }

    private static void streamingSubtitle() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        hud.startSession("asr-1");
        check(hud.acceptAsr("旧的半句", false, "asr-1", 100), "C2 first partial accepted");
        check(hud.acceptAsr("新的半句", false, "asr-1", 101), "C2 newer partial accepted");
        DshCompositeHudModel.Frame one = hud.compose(view("running", null, 200), 201);
        check(one.asrPhase == DshCompositeHudModel.Phase.STREAMING, "C2 partials are STREAMING");
        check(one.asrSegment.contains("新的半句") && !one.asrSegment.contains("旧的半句"),
            "C2 a partial replaces the previous partial instead of appending");
        int sep = separatorIndex(one.text);
        check(sep >= 1 && sep <= 2, "C2 ASR occupies the upper 1-2 lines");
        check(one.lineCount - sep - 1 >= 1, "C2 the board keeps at least one line below the separator");
        check(one.text.indexOf("AA 终端") > one.text.indexOf("---"), "C2 the board sits under the separator");
        check(bounded(one.text, one.lineCount), "C2 the composite stays inside both budgets");

        hud.startSession("asr-2");
        hud.acceptAsr(repeat('长', 160), false, "asr-2", 300);
        DshCompositeHudModel.Frame two = hud.compose(view("running", null, 400), 401);
        check(separatorIndex(two.text) == 2, "C2 a long partial uses exactly two upper lines");
        check(two.lineCount <= 5 && two.text.length() <= DshCompositeHudModel.MAX_CHARS,
            "C2 a long partial cannot push the board out of budget");
        check(two.text.contains("AA 终端"), "C2 the board survives a long partial");
    }

    private static void finalSettling() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        hud.startSession("asr-3");
        hud.acceptAsr("半句", false, "asr-3", 1000);
        check(hud.acceptAsr("完整一句话", true, "asr-3", 1000), "C3 final event accepted");
        DshDashboardModel.View board = view("completed", "AA 结果", 1000);
        DshCompositeHudModel.Frame still = hud.compose(board, 5999);
        check(still.asrPhase == DshCompositeHudModel.Phase.FINALIZED, "C3 final enters FINALIZED");
        check(still.text.contains("完整一句话") && still.asrSegment.contains("[识别]"),
            "C3 the finalized subtitle is still visible one millisecond before settling");
        check(still.text.contains("AA 结果"), "C3 the board stays visible while the subtitle settles");
        check(separatorIndex(still.text) >= 1, "C3 the finalized subtitle keeps the two sections apart");
        check(bounded(still.text, still.lineCount), "C3 the settling composite stays bounded");
    }

    private static void settlingClearsOnlyAsr() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        hud.startSession("asr-4");
        hud.acceptAsr("完整一句话", true, "asr-4", 1000);
        DshDashboardModel.View board = view("completed", "AA 结果", 1000);
        DshCompositeHudModel.Frame idle = hud.compose(board, 6000);
        check(idle.asrPhase == DshCompositeHudModel.Phase.IDLE, "C4 the final subtitle clears at 5000ms");
        check(idle.asrSegment.isEmpty(), "C4 the cleared frame carries no subtitle text");
        check(!idle.text.contains("完整一句话"), "C4 settled ASR leaves the lens transcript");
        check(idle.text.contains("AA 结果") && idle.text.contains("完整结果请到 AA 查看。"),
            "C4 the AA board remains after the subtitle settles");
        check(separatorIndex(idle.text) < 0, "C4 an idle composite has no separator");
        check(bounded(idle.text, idle.lineCount), "C4 the board-only composite stays bounded");
        check(hud.compose(board, 6001).asrPhase == DshCompositeHudModel.Phase.IDLE, "C4 settling is sticky");

        DshCompositeHudModel shortHud = new DshCompositeHudModel(1200L);
        shortHud.startSession("asr-5");
        shortHud.acceptAsr("短句", true, "asr-5", 0);
        check(shortHud.compose(board, 1199).asrPhase == DshCompositeHudModel.Phase.FINALIZED,
            "C4 a custom settling window keeps the subtitle at 1199ms");
        check(shortHud.compose(board, 1200).asrPhase == DshCompositeHudModel.Phase.IDLE,
            "C4 a custom settling window clears at its own boundary");

        // A partial the vendor abandoned must not hold the upper lines forever either.
        DshCompositeHudModel abandoned = new DshCompositeHudModel();
        abandoned.startSession("asr-6");
        abandoned.acceptAsr("半途再也没下文", false, "asr-6", 1000);
        check(abandoned.compose(board, 5999).asrPhase == DshCompositeHudModel.Phase.STREAMING,
            "C4 a recent partial stays inside the settling window");
        DshCompositeHudModel.Frame gone = abandoned.compose(board, 6000);
        check(gone.asrPhase == DshCompositeHudModel.Phase.IDLE && !gone.text.contains("半途再也没下文"),
            "C4 an abandoned partial settles and leaves only the board");
        check(bounded(gone.text, gone.lineCount), "C4 the settled composite stays bounded");
    }

    private static void longInputs() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        hud.startSession("asr-6");
        hud.acceptAsr(repeat('语', 2000), false, "asr-6", 100);
        DshDashboardModel.View board = view("completed", repeat('x', 198) + "结尾", 100);
        DshCompositeHudModel.Frame first = hud.compose(board, 101);
        DshCompositeHudModel.Frame second = hud.compose(board, 102);
        check(first.text.equals(second.text), "C5 the same input composes to the same bounded transcript");
        check(bounded(first.text, first.lineCount), "C5 long input stays within 5 lines and 300 code units");
        for (String line : lines(first.text))
            check(line.length() <= DshCompositeHudModel.MAX_LINE_CHARS, "C5 every rendered line honours the line budget");
        check(first.text.contains("AA 终端") && first.text.contains("已完成"),
            "C5 essential target and status survive the longest subtitle");
        check(first.dashboardSegment.contains("结尾"),
            "C5 the essential result tail is kept ahead of decorative copy");

        hud.startSession("asr-7");
        hud.acceptAsr("一\n二\n三\n四\n五\n六\n七", false, "asr-7", 200);
        DshCompositeHudModel.Frame injected = hud.compose(board, 201);
        check(injected.lineCount <= DshCompositeHudModel.MAX_LINES && bounded(injected.text, injected.lineCount),
            "C5 embedded newlines cannot create extra lens lines");

        DshCompositeHudModel idleHud = new DshCompositeHudModel();
        DshCompositeHudModel.Frame idle = idleHud.compose(view("completed", repeat('x', 198) + "结尾", 300), 301);
        check(bounded(idle.text, idle.lineCount) && idle.text.contains("结尾"),
            "C5 the board-only transcript keeps the answer tail and stays bounded");
    }

    private static void emojiSurvivesTruncation() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        hud.startSession("asr-8");
        // The emoji pair straddles the subtitle cap, so it must be dropped or kept whole.
        hud.acceptAsr(repeat('字', DshCompositeHudModel.MAX_ASR_CHARS) + "\uD83D\uDE00tail", false, "asr-8", 300);
        DshDashboardModel.View board = view("completed", repeat('y', 150) + "\uD83D\uDE00", 300);
        DshCompositeHudModel.Frame active = hud.compose(board, 301);
        check(surrogateSafe(active.text), "C6 truncation never splits a surrogate pair");
        check(active.text.length() <= DshCompositeHudModel.MAX_CHARS, "C6 the composite is under 300 code units");
        check(active.lineCount <= DshCompositeHudModel.MAX_LINES, "C6 the composite is under 5 lines");

        hud.startSession("asr-9");
        hud.acceptAsr("表情在最前\uD83D\uDE00" + repeat('字', 400), false, "asr-9", 400);
        DshCompositeHudModel.Frame kept = hud.compose(board, 401);
        check(surrogateSafe(kept.text) && kept.text.indexOf("\uD83D\uDE00") >= 0,
            "C6 an emoji inside the kept window survives intact");

        DshCompositeHudModel idleHud = new DshCompositeHudModel();
        DshCompositeHudModel.Frame idle = idleHud.compose(view("completed", repeat('z', 199) + "\uD83D\uDE00", 500), 501);
        check(surrogateSafe(idle.text), "C6 the board-only transcript never splits an emoji");
    }

    private static void sessionGuard() {
        DshCompositeHudModel hud = new DshCompositeHudModel();
        DshDashboardModel.View board = view("completed", "AA 结果", 400);
        hud.startSession("asr-live");
        check(hud.acceptAsr("第一句在说", false, "asr-live", 400), "C7 partial for the live session accepted");
        check(!hud.acceptAsr("过期的别家", false, "asr-old", 401), "C7 a foreign session is rejected mid-stream");
        check(!hud.acceptAsr("", true, null, 402), "C7 a session-less callback is rejected");
        DshCompositeHudModel.Frame live = hud.compose(board, 403);
        check(live.asrSegment.contains("第一句在说") && !live.asrSegment.contains("过期的别家"),
            "C7 a stale session cannot overwrite the live subtitle");
        check(hud.acceptAsr("第一句说完了", true, "asr-live", 404), "C7 final for the live session accepted");
        DshCompositeHudModel.Frame done = hud.compose(board, 405);
        check(done.asrPhase == DshCompositeHudModel.Phase.FINALIZED && done.text.contains("第一句说完了"),
            "C7 the final text replaces the partial it belongs to");
        check(hud.acceptAsr("第二句开始", false, "asr-live", 406), "C7 a fresh partial after a final is accepted");
        DshCompositeHudModel.Frame fresh = hud.compose(board, 407);
        check(fresh.asrPhase == DshCompositeHudModel.Phase.STREAMING
            && fresh.asrSegment.contains("第二句开始") && !fresh.asrSegment.contains("第一句说完了"),
            "C7 a fresh partial replaces the prior finalized segment");
        check(fresh.text.contains("AA 结果"), "C7 the board survives a restarting subtitle");

        hud.startSession("asr-next");
        DshCompositeHudModel.Frame reset = hud.compose(board, 408);
        check(reset.asrPhase == DshCompositeHudModel.Phase.IDLE && reset.asrSegment.isEmpty(),
            "C7 a new session drops the previous session's subtitle");
    }

    public static void main(String[] args) {
        emptyInputs();
        streamingSubtitle();
        finalSettling();
        settlingClearsOnlyAsr();
        longInputs();
        emojiSurvivesTruncation();
        sessionGuard();
        if (failures != 0) throw new AssertionError("DshCompositeHudModelTest: " + failures + " failed / " + checks + " checks");
        System.out.println("DshCompositeHudModelTest: " + checks + " passed");
    }
}
