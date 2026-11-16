package com.turboio.addon;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ProactiveHintAugmentorTest {
    public static void main(String[] args) throws Exception {
        testExtractQuestionAndAnswerJson();
        testExtractQuestionPlainText();
        testInterceptHoldsThenDeliversRegeneratedAnswer();
        testInterceptCachesSameQuestionUnderSameRevision();
        testInterceptFallbacksWhenRetrievalFails();
        testFormatContextIncludesCoverageInfo();
        testSynthesizeIncludesCoverageNote();
        HintCoordinatorTest.main(args);
        System.out.println("ProactiveHintAugmentorTest: ALL PASS");
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

    private static McpConnectionStore.Snapshot config(long revision) throws Exception {
        Constructor<McpConnectionStore.Snapshot> c = McpConnectionStore.Snapshot.class
            .getDeclaredConstructor(boolean.class, String.class, String.class, long.class);
        c.setAccessible(true);
        return c.newInstance(true, "http://127.0.0.1/mcp", "", revision);
    }

    private static KnowledgeRetrieval.Result hit(long revision) {
        return new KnowledgeRetrieval.Result(KnowledgeRetrieval.Status.HIT,
            java.util.Collections.singletonList(new KnowledgeRetrieval.Evidence("kb", "doc-a", "faq.md", "{}", 0.9d, "evidence", "命中证据")),
            revision);
    }

    private static String textOf(HintCoordinatorTest.Hint hint) throws Exception {
        return (String) ProactiveHintAugmentor.textField(hint).get(hint);
    }

    private static final class Fixture implements ProactiveHintAugmentor.State, ProactiveHintAugmentor.MainScheduler {
        final ArrayDeque<Runnable> workerTasks = new ArrayDeque<>();
        final ArrayDeque<Runnable> mainTasks = new ArrayDeque<>();
        final List<Runnable> timers = new ArrayList<>();
        long now;
        long generation = 1, sequence = 1, revision = 3;
        int retrieves, models, deliveries;
        String diagnostic;
        ProactiveHintAugmentor.Retrieval retrieval = (question, snapshot, budgetMs) -> { retrieves++; return hit(snapshot.revision); };
        ProactiveHintAugmentor.Regenerator model = (question, cloud, result, budgetMs) -> { models++; return "重生成回答"; };
        ProactiveHintAugmentor augmentor() { return new ProactiveHintAugmentor(workerTasks::add, this, () -> now, this, retrieval); }
        void deliver(Object hint, String value) { deliveries++; diagnostic = value; }
        public long generation() { return generation; }
        public long sequence() { return sequence; }
        public long revision() { return revision; }
        public void execute(Runnable task) { mainTasks.add(task); }
        public ProactiveHintAugmentor.Cancellation schedule(Runnable task, long delayMs) {
            timers.add(task);
            return () -> timers.remove(task);
        }
        void runWorker() { while (!workerTasks.isEmpty()) workerTasks.remove().run(); }
        void runMain() { while (!mainTasks.isEmpty()) mainTasks.remove().run(); }
    }

    private static void testInterceptHoldsThenDeliversRegeneratedAnswer() throws Exception {
        Fixture f = new Fixture();
        String original = "{\"question\":\"产品保修期多久？\",\"answer\":\"整机保修一年。\"}";
        HintCoordinatorTest.Hint hint = new HintCoordinatorTest.Hint(original);
        f.augmentor().interceptAndRegenerateAsync(hint, config(f.revision), f.generation, f.sequence, f.model, f::deliver);
        if (!textOf(hint).equals(original)) throw new AssertionError("must hold hint before retrieval");
        if (f.deliveries != 0 || f.retrieves != 0) throw new AssertionError("work must be deferred");
        f.runWorker();
        if (f.deliveries != 0) throw new AssertionError("worker must not deliver");
        if (!textOf(hint).equals(original)) throw new AssertionError("worker must not mutate hint");
        f.runMain();
        if (!new org.json.JSONObject(textOf(hint)).getString("answer").equals("重生成回答")) throw new AssertionError("answer not rewritten: " + textOf(hint));
        if (f.deliveries != 1) throw new AssertionError("exactly one delivery");
        if (!"提醒知识库回答已准备".equals(f.diagnostic)) throw new AssertionError("diagnostic mismatch: " + f.diagnostic);
    }

    private static void testInterceptCachesSameQuestionUnderSameRevision() throws Exception {
        Fixture f = new Fixture();
        ProactiveHintAugmentor augmentor = f.augmentor();
        HintCoordinatorTest.Hint first = new HintCoordinatorTest.Hint("{\"question\":\"缓存问题\",\"answer\":\"云端一\"}");
        augmentor.interceptAndRegenerateAsync(first, config(f.revision), f.generation, f.sequence, f.model, f::deliver);
        f.runWorker(); f.runMain();
        f.sequence++;
        HintCoordinatorTest.Hint second = new HintCoordinatorTest.Hint("{\"question\":\"缓存问题\",\"answer\":\"云端二\"}");
        augmentor.interceptAndRegenerateAsync(second, config(f.revision), f.generation, f.sequence, f.model, f::deliver);
        if (f.retrieves != 1 || f.models != 1) throw new AssertionError("same question and revision must reuse cache");
        if (!new org.json.JSONObject(textOf(second)).getString("answer").equals("重生成回答")) throw new AssertionError("cache must replay answer");
        if (!"提醒已复用重生成回答".equals(f.diagnostic)) throw new AssertionError("cache diagnostic mismatch: " + f.diagnostic);
        f.revision++; f.sequence++;
        HintCoordinatorTest.Hint third = new HintCoordinatorTest.Hint("{\"question\":\"缓存问题\",\"answer\":\"云端三\"}");
        augmentor.interceptAndRegenerateAsync(third, config(f.revision), f.generation, f.sequence, f.model, f::deliver);
        f.runWorker(); f.runMain();
        if (f.retrieves != 2) throw new AssertionError("revision change must re-retrieve");
    }

    private static void testInterceptFallbacksWhenRetrievalFails() throws Exception {
        Fixture f = new Fixture();
        f.retrieval = (question, snapshot, budgetMs) -> { f.retrieves++; return KnowledgeRetrieval.result(KnowledgeRetrieval.Status.TIMEOUT, snapshot.revision); };
        String original = "{\"question\":\"回退问题\",\"answer\":\"云端原回答\"}";
        HintCoordinatorTest.Hint hint = new HintCoordinatorTest.Hint(original);
        f.augmentor().interceptAndRegenerateAsync(hint, config(f.revision), f.generation, f.sequence, f.model, f::deliver);
        f.runWorker(); f.runMain();
        if (!textOf(hint).equals(original)) throw new AssertionError("fallback must keep cloud answer");
        if (f.models != 0 || f.deliveries != 1) throw new AssertionError("fallback must skip regeneration and deliver once");
        if (!f.diagnostic.startsWith("提醒知识库") || !f.diagnostic.contains("timeout") || !f.diagnostic.contains("已放行云端原回答")) throw new AssertionError("fallback diagnostic mismatch: " + f.diagnostic);
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
