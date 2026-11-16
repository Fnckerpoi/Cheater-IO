package com.turboio.addon;

public final class ProactiveHintAugmentorTest {
    public static void main(String[] args) throws Exception {
        testPreserveExtraFields();
        testExtractQuestionAndAnswerJson();
        testExtractQuestionPlainText();
        testAssembleAugmentedJson();
        testTruncationBudget();
        testFallbackWhenEmptyEvidence();
        testFormatContextIncludesCoverageInfo();
        testSynthesizeIncludesCoverageNote();
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

    private static void testFormatContextIncludesCoverageInfo() {
        KnowledgeRetrieval.Evidence evidence = new KnowledgeRetrieval.Evidence(
            "team", "doc-1", "plan.md", "{\"kind\":\"plan\"}", 0.91, "evidence", "覆盖信息必须传给模型。");
        KnowledgeRetrieval.Result hit = new KnowledgeRetrieval.Result(
            KnowledgeRetrieval.Status.HIT, java.util.Collections.singletonList(evidence), 7L,
            0.8d, java.util.Collections.singletonList("team-failed"), 123L, true);
        String context = KnowledgeRetrieval.formatContext(hit);
        if (!context.contains("coverage_ratio")) throw new AssertionError("coverage_ratio missing: " + context);
        if (!context.contains("0.8")) throw new AssertionError("coverage ratio value missing: " + context);
        if (!context.contains("namespaces_failed")) throw new AssertionError("namespaces_failed missing: " + context);
        if (!context.contains("team-failed")) throw new AssertionError("failed namespace missing: " + context);
        if (!context.contains("truncated")) throw new AssertionError("truncated missing: " + context);
        if (!context.contains("true")) throw new AssertionError("truncated value missing: " + context);
        if (!context.contains("doc-1")) throw new AssertionError("evidence doc_id missing: " + context);
    }

    private static void testSynthesizeIncludesCoverageNote() {
        KnowledgeRetrieval.Evidence evidence = new KnowledgeRetrieval.Evidence(
            "team", "doc-2", "guide.md", "", 0.5d, "evidence", "合成路径也要带覆盖注记。");
        KnowledgeRetrieval.Result hit = new KnowledgeRetrieval.Result(
            KnowledgeRetrieval.Status.HIT, java.util.Collections.singletonList(evidence), 3L,
            0.5d, java.util.Collections.singletonList("legacy"), 20L, false);
        String synthesized = ProactiveHintRegenerator.synthesize("云端答案", hit);
        if (!synthesized.contains("覆盖")) throw new AssertionError("coverage note missing: " + synthesized);
        if (!synthesized.contains("云端原回答")) throw new AssertionError("cloud answer missing: " + synthesized);
    }
}
