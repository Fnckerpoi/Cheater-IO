package com.turboio.addon;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Holds a vendor hint until retrieval and regeneration finish, then delivers on main. */
public final class ProactiveHintAugmentor {
    static final long RETRIEVAL_BUDGET_MS=15000L;
    static final long MODEL_BUDGET_MS=25000L;
    static final long TOTAL_BUDGET_MS=42000L;
    static java.util.concurrent.ThreadPoolExecutor newHintExecutor() {
        return new java.util.concurrent.ThreadPoolExecutor(2,2,0L,java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(1),r -> {
                Thread thread=new Thread(r,"turboio-hint");
                thread.setDaemon(true);
                return thread;
            },new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    }
    public interface Clock { long nowMs(); }
    public interface Cancellation { void cancel(); }
    public interface MainScheduler extends Executor { Cancellation schedule(Runnable task, long delayMs); }
    public interface State { long generation(); long sequence(); long revision(); }
    public interface Retrieval { KnowledgeRetrieval.Result retrieve(String question, McpConnectionStore.Snapshot config, long budgetMs); }
    public interface Regenerator { String regenerate(String question, String cloudAnswer, KnowledgeRetrieval.Result result, long budgetMs) throws Exception; }
    public interface Delivery { void deliver(Object hint, String diagnostic); }
    /** Stage trace sink; receives lengths and statuses only, never hint text. */
    public interface Trace { void log(String event); }
    static volatile Trace trace = event -> {};

    private final Executor worker;
    private final MainScheduler main;
    private final Clock clock;
    private final State state;
    private final Retrieval retrieval;
    // Accessed only by the serialized main scheduler.
    private String cachedQuestion, cachedAnswer;
    private long cachedRevision = -1;
    private Pending latest;

    public ProactiveHintAugmentor(Executor worker, MainScheduler main, Clock clock, State state) {
        this(worker, main, clock, state, KnowledgeRetrieval::retrieve);
    }
    public ProactiveHintAugmentor(Executor worker, MainScheduler main, Clock clock, State state, Retrieval retrieval) {
        this.worker=worker; this.main=main; this.clock=clock; this.state=state; this.retrieval=retrieval;
    }
    public static final class HintPayload {
        public final String question, answer;
        public final boolean isJson;
        public HintPayload(String question, String answer, boolean isJson) {
            this.question=question; this.answer=answer; this.isJson=isJson;
        }
    }
    public static HintPayload parseHintText(String text) {
        if (text==null || text.trim().isEmpty()) return new HintPayload("",null,false);
        String trimmed=text.trim();
        if (trimmed.startsWith("{")) {
            try {
                JSONObject json=new JSONObject(trimmed);
                Object q=json.opt("question"), a=json.opt("answer");
                return new HintPayload(q instanceof String ? (String)q : "", a instanceof String ? (String)a : null,true);
            } catch (Exception invalid) { return new HintPayload("",null,false); }
        }
        return new HintPayload(trimmed,null,false);
    }
    static String replaceAnswer(String original, String answer) {
        try {
            if (parseHintText(original).isJson) return new JSONObject(original).put("answer",answer).toString();
            return answer;
        } catch (Exception invalid) { return original; }
    }
    /** Call from main. All field mutation and Delivery calls remain on that scheduler. */
    public void interceptAndRegenerateAsync(Object hint, McpConnectionStore.Snapshot config,
            long generation, long sequence, Regenerator regenerator, Delivery delivery) {
        final Pending pending;
        try {
            Field field=textField(hint);
            Object value=field.get(hint);
            if (!(value instanceof String)) { delivery.deliver(hint,"提醒文本不可用，已放行云端原回答"); return; }
            pending=new Pending(hint,field,(String)value,config,generation,sequence,delivery);
        } catch (Exception inaccessible) { delivery.deliver(hint,"提醒文本不可用，已放行云端原回答"); return; }
        if (!pending.valid()) return;
        if(latest!=null && !latest.delivered.get()) latest.discard("superseded");
        latest=pending;
        HintPayload payload=parseHintText(pending.original);
        trace.log("hint parse seq="+sequence+" json="+payload.isJson+" question_len="+payload.question.length()+" enabled="+(config!=null&&config.enabled));
        if (config==null || !config.enabled || config.endpoint.trim().isEmpty() || payload.question.trim().isEmpty()) {
            pending.accept(null,"提醒知识库未启用或问题为空，已放行云端原回答"); return;
        }
        if (config.revision==cachedRevision && payload.question.equals(cachedQuestion)) {
            pending.accept(cachedAnswer,"提醒已复用重生成回答"); return;
        }
        pending.watchdog=main.schedule(() -> pending.accept(null,"提醒流水线超时，已放行云端原回答"),Math.max(0,pending.deadline-clock.nowMs()));
        pending.work=new FutureTask<>(() -> {
            String answer=null, diagnostic="提醒检索失败，已放行云端原回答";
            boolean cacheable=false;
            try {
                if (!pending.active()) return;
                long retrievalStart=clock.nowMs();
                long budget=Math.min(RETRIEVAL_BUDGET_MS,pending.deadline-retrievalStart);
                if (budget<=0) throw new java.net.SocketTimeoutException();
                KnowledgeRetrieval.Result result=retrieval.retrieve(payload.question,config,budget);
                trace.log("hint retrieval seq="+sequence+" status="+(result==null?"null":result.status)+" ms="+(clock.nowMs()-retrievalStart));
                if (!pending.active()) return;
                boolean inBudget=clock.nowMs()-retrievalStart<=budget;
                if (!inBudget) {
                    diagnostic="提醒检索超时，已放行云端原回答";
                } else if (result!=null && result.revision==config.revision && result.isHit()) {
                    long modelStart=clock.nowMs();
                    long modelBudget=Math.min(MODEL_BUDGET_MS,pending.deadline-modelStart);
                    if (modelBudget>0) {
                        try {
                            String candidate=regenerator.regenerate(payload.question,payload.answer==null?pending.original:payload.answer,result,modelBudget);
                            if (clock.nowMs()-modelStart<=modelBudget && clock.nowMs()<pending.deadline && candidate!=null && !candidate.trim().isEmpty()) {
                                answer=candidate; diagnostic="提醒知识库回答已准备";
                                cacheable=result.namespacesFailed.isEmpty();
                            }
                        } catch (Exception failed) {
                            // The model logs the failing stage. Preserve the already retrieved evidence.
                        }
                    }
                    if (!pending.active()) return;
                    if (answer==null) {
                        answer=ProactiveHintRegenerator.evidenceFallback(result);
                        diagnostic=answer==null?"提醒证据正文不可用，已放行云端原回答":"提醒已使用知识库摘录";
                    }
                } else if (result!=null && result.revision==config.revision && result.status==KnowledgeRetrieval.Status.NO_MATCH) {
                    answer="未找到相关内容";
                    diagnostic=answer;
                } else {
                    diagnostic="提醒知识库"+(result==null?"检索失败":result.status.name().toLowerCase(java.util.Locale.ROOT))+"，已放行云端原回答";
                }
                if (inBudget && result!=null && result.revision==config.revision && !result.namespacesFailed.isEmpty()) {
                    String failed=String.join("、",result.namespacesFailed);
                    if (result.coverageRatio==0 && result.status==KnowledgeRetrieval.Status.TOOL_ERROR) {
                        diagnostic="知识库检索失败（"+failed+"）";
                        answer=ProactiveHintRegenerator.clip(diagnostic,120);
                    } else {
                        String notice="部分知识库检索失败（"+failed+"）";
                        diagnostic=diagnostic+"；"+notice;
                        if (answer!=null) answer=withCoverageNotice(answer,notice);
                    }
                }
            } catch (Exception failed) { diagnostic="提醒检索或重生成失败，已放行云端原回答"; }
            final String completed=answer, status=diagnostic;
            final boolean store=cacheable && completed!=null;
            main.execute(() -> pending.accept(completed,status,store));
        },null);
        try { worker.execute(pending.work); }
        catch (RuntimeException rejected) { pending.accept(null,"提醒任务不可用，已放行云端原回答"); }
    }
    private static String withCoverageNotice(String answer,String notice) {
        String label=ProactiveHintRegenerator.clip(notice,60);
        return ProactiveHintRegenerator.clip(answer,119-label.length())+" "+label;
    }
    private final class Pending {
        final Object hint; final Field field; final String original;
        final McpConnectionStore.Snapshot config; final long generation,sequence,deadline;
        final Delivery delivery; final AtomicBoolean delivered=new AtomicBoolean();
        Cancellation watchdog;
        FutureTask<Void> work;
        Pending(Object hint,Field field,String original,McpConnectionStore.Snapshot config,long generation,long sequence,Delivery delivery) {
            this.hint=hint;this.field=field;this.original=original;this.config=config;this.generation=generation;this.sequence=sequence;this.delivery=delivery;deadline=clock.nowMs()+TOTAL_BUDGET_MS;
        }
        boolean valid() { return state.generation()==generation && state.sequence()==sequence && (config==null || state.revision()==config.revision); }
        boolean active() { return !delivered.get() && valid() && clock.nowMs()<deadline; }
        void accept(String answer,String diagnostic) { accept(answer,diagnostic,false); }
        void accept(String answer,String diagnostic,boolean cacheable) {
            if (delivered.get()) return;
            if (!valid()) { discard("stale");return; }
            try { if (!original.equals(field.get(hint))) { retire();return; } }
            catch (Exception inaccessible) { retire();return; }
            if (!delivered.compareAndSet(false,true)) return;
            if (watchdog!=null) watchdog.cancel();
            if (clock.nowMs()>=deadline) { cancelWork();answer=null;diagnostic="提醒流水线超时，已放行云端原回答"; }
            if (answer!=null) {
                try { field.set(hint,replaceAnswer(original,answer)); }
                catch (Exception immutable) { answer=null; diagnostic="提醒文本更新失败，已放行云端原回答"; }
                if (answer!=null && cacheable) { cachedQuestion=parseHintText(original).question;cachedAnswer=answer;cachedRevision=config.revision; }
            }
            if(latest==this)latest=null;
            trace.log("hint deliver seq="+sequence+" regenerated="+(answer!=null)+" status="+diagnostic);
            delivery.deliver(hint,diagnostic);
        }
        void discard(String reason) { trace.log("hint discard seq="+sequence+" reason="+reason);retire(); }
        void retire() { delivered.set(true);if(watchdog!=null)watchdog.cancel();cancelWork();if(latest==this)latest=null; }
        void cancelWork() { if(work!=null)work.cancel(true); }
    }
    static Field textField(Object hint) throws NoSuchFieldException {
        Class<?> type=hint.getClass();
        while(type!=null && type!=Object.class) {
            try { Field field=type.getDeclaredField("text");field.setAccessible(true);return field; }
            catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException("text");
    }
}
