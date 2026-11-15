package com.turboio.addon;

public final class ProactiveHintAugmentorTest {
    public static void main(String[] args) throws Exception {
        testPreserveExtraFields();
        testExtractQuestionAndAnswerJson();
        testExtractQuestionPlainText();
        testAssembleAugmentedJson();
        testTruncationBudget();
        testFallbackWhenEmptyEvidence();
        testAsyncWorkerUsesClientAndReturnsOriginalOnFailure();
        HintCoordinatorTest.main(args);
        System.out.println("ProactiveHintAugmentorTest: ALL PASS");
    }

    private static void testPreserveExtraFields() {
        String original = "{\"question\":\"q\",\"answer\":\"a\",\"meta\":{\"id\":7},\"isFinish\":true}";
        org.json.JSONObject result = new org.json.JSONObject(ProactiveHintAugmentor.augment(original, "evidence"));
        if (!result.has("meta") || result.getJSONObject("meta").getInt("id") != 7 || !result.getBoolean("isFinish"))
            throw new AssertionError("augmentation must preserve JSON extra fields");
    }

    private static void testExtractQuestionAndAnswerJson() {
        String json = "{\"question\":\"今天天气怎么样？\",\"answer\":\"今天晴转多云。\"}";
        ProactiveHintAugmentor.HintPayload parsed = ProactiveHintAugmentor.parseHintText(json);
        if (!"今天天气怎么样？".equals(parsed.question)) throw new AssertionError("question mismatch: " + parsed.question);
        if (!"今天晴转多云。".equals(parsed.answer)) throw new AssertionError("answer mismatch: " + parsed.answer);
    }

    private static void testExtractQuestionPlainText() {
        String plain = "会议几点开始？";
        ProactiveHintAugmentor.HintPayload parsed = ProactiveHintAugmentor.parseHintText(plain);
        if (!"会议几点开始？".equals(parsed.question)) throw new AssertionError("question mismatch: " + parsed.question);
        if (parsed.answer != null) throw new AssertionError("answer should be null for plain text");
    }

    private static void testAssembleAugmentedJson() {
        String originalJson = "{\"question\":\"产品保修期多久？\",\"answer\":\"整机保修一年。\"}";
        String evidence = "雷鸟X2享有专属延长质保2年服务。";
        String augmented = ProactiveHintAugmentor.augment(originalJson, evidence);
        if (!augmented.contains("\"question\":\"产品保修期多久？\"")) throw new AssertionError("question missing");
        if (!augmented.contains("【知识库】雷鸟X2享有专属延长质保2年服务。")) throw new AssertionError("kb prefix missing");
        if (!augmented.contains("【推荐】整机保修一年。")) throw new AssertionError("rec prefix missing");
    }

    private static void testTruncationBudget() {
        String originalJson = "{\"question\":\"问答\",\"answer\":\"答案\"}";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("超长内容");
        String evidence = sb.toString();
        String augmented = ProactiveHintAugmentor.augment(originalJson, evidence);
        if (augmented.length() > 300) throw new AssertionError("augmented payload too long: " + augmented.length());
        if (!augmented.contains("...")) throw new AssertionError("expected truncation marker ...");
    }

    private static void testAsyncWorkerUsesClientAndReturnsOriginalOnFailure() {
        String original = "{\"question\":\"异步问题\",\"answer\":\"官方答案\"}";
        final java.util.ArrayDeque<Runnable> work = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<Runnable> main = new java.util.ArrayDeque<>();
        final String[] result = new String[1];
        final boolean[] queried = new boolean[1];
        ProactiveHintAugmentor.augmentAsync(original, question -> {
            queried[0] = true;
            if (!"异步问题".equals(question)) throw new AssertionError("wrong query");
            throw new IllegalStateException("secret-token-must-not-escape");
        }, work::add, main::add, value -> result[0] = value);
        if (queried[0]) throw new AssertionError("lookup ran inline");
        if (result[0] != null) throw new AssertionError("completion ran inline");
        work.remove().run();
        if (!queried[0]) throw new AssertionError("lookup did not run on worker");
        if (result[0] != null) throw new AssertionError("result callback must be dispatched separately");
        main.remove().run();
        if (!original.equals(result[0])) throw new AssertionError("failure must preserve original hint");
    }

    private static void testFallbackWhenEmptyEvidence() {
        String originalJson = "{\"question\":\"未命中问题\",\"answer\":\"官方答案\"}";
        String augmented = ProactiveHintAugmentor.augment(originalJson, null);
        if (!augmented.equals(originalJson)) throw new AssertionError("should fallback to original when evidence is null");

        String augmentedEmpty = ProactiveHintAugmentor.augment(originalJson, "   ");
        if (!augmentedEmpty.equals(originalJson)) throw new AssertionError("should fallback to original when evidence is blank");
    }
}
