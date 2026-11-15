package com.turboio.addon;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Holds a vendor hint until retrieval and regeneration finish, then delivers on main. */
public final class ProactiveHintAugmentor {
    public interface Clock { long nowMs(); }
    public interface Cancellation { void cancel(); }
    public interface MainScheduler extends Executor { Cancellation schedule(Runnable task, long delayMs); }
    public interface State { long generation(); long sequence(); long revision(); }
    public interface Retrieval { KnowledgeRetrieval.Result retrieve(String question, McpConnectionStore.Snapshot config, long budgetMs); }
    public interface Regenerator { String regenerate(String question, String cloudAnswer, KnowledgeRetrieval.Result result, long budgetMs) throws Exception; }
    public interface Delivery { void deliver(Object hint, String diagnostic); }

    private final Executor worker;
    private final MainScheduler main;
    private final Clock clock;
    private final State state;
    private final Retrieval retrieval;
    // Accessed only by the serialized main scheduler.
    private String cachedQuestion, cachedAnswer;
    private long cachedRevision = -1;

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
        HintPayload payload=parseHintText(pending.original);
        if (config==null || !config.enabled || config.endpoint.trim().isEmpty() || payload.question.trim().isEmpty()) {
            pending.accept(null,"提醒知识库未启用或问题为空，已放行云端原回答"); return;
        }
        if (config.revision==cachedRevision && payload.question.equals(cachedQuestion)) {
            pending.accept(cachedAnswer,"提醒已复用重生成回答"); return;
        }
        pending.watchdog=main.schedule(() -> pending.accept(null,"提醒流水线超时，已放行云端原回答"),4000);
        try { worker.execute(() -> {
            String answer=null, diagnostic="提醒检索失败，已放行云端原回答";
            try {
                if (!pending.active()) return;
                long retrievalStart=clock.nowMs();
                long budget=Math.min(1800,pending.deadline-retrievalStart);
                if (budget<=0) throw new java.net.SocketTimeoutException();
                KnowledgeRetrieval.Result result=retrieval.retrieve(payload.question,config,budget);
                if (!pending.active()) return;
                if (clock.nowMs()-retrievalStart>budget) {
                    diagnostic="提醒检索超时，已放行云端原回答";
                } else if (result!=null && result.revision==config.revision && result.isHit()) {
                    long modelStart=clock.nowMs();
                    long modelBudget=Math.min(2200,pending.deadline-modelStart);
                    diagnostic="提醒检索命中但重生成回退";
                    if (modelBudget>0) {
                        String candidate=regenerator.regenerate(payload.question,payload.answer==null?pending.original:payload.answer,result,modelBudget);
                        if (clock.nowMs()-modelStart<=modelBudget && clock.nowMs()<pending.deadline && candidate!=null && !candidate.trim().isEmpty()) {
                            answer=candidate; diagnostic="提醒知识库回答已准备";
                        }
                    }
                } else {
                    diagnostic="提醒知识库"+(result==null?"检索失败":result.status.name().toLowerCase(java.util.Locale.ROOT))+"，已放行云端原回答";
                }
            } catch (Exception failed) { diagnostic="提醒检索或重生成失败，已放行云端原回答"; }
            final String completed=answer, status=diagnostic;
            main.execute(() -> pending.accept(completed,status));
        }); } catch (RuntimeException rejected) { pending.accept(null,"提醒任务不可用，已放行云端原回答"); }
    }
    private final class Pending {
        final Object hint; final Field field; final String original;
        final McpConnectionStore.Snapshot config; final long generation,sequence,deadline;
        final Delivery delivery; final AtomicBoolean delivered=new AtomicBoolean();
        Cancellation watchdog;
        Pending(Object hint,Field field,String original,McpConnectionStore.Snapshot config,long generation,long sequence,Delivery delivery) {
            this.hint=hint;this.field=field;this.original=original;this.config=config;this.generation=generation;this.sequence=sequence;this.delivery=delivery;deadline=clock.nowMs()+4000;
        }
        boolean valid() { return state.generation()==generation && state.sequence()==sequence && (config==null || state.revision()==config.revision); }
        boolean active() { return !delivered.get() && valid() && clock.nowMs()<deadline; }
        void accept(String answer,String diagnostic) {
            if (delivered.get()) return;
            if (!valid()) { retire(); return; }
            try { if (!original.equals(field.get(hint))) { retire();return; } }
            catch (Exception inaccessible) { retire();return; }
            if (!delivered.compareAndSet(false,true)) return;
            if (watchdog!=null) watchdog.cancel();
            if (clock.nowMs()>=deadline) { answer=null;diagnostic="提醒流水线超时，已放行云端原回答"; }
            if (answer!=null) {
                try { field.set(hint,replaceAnswer(original,answer)); }
                catch (Exception immutable) { answer=null; diagnostic="提醒文本更新失败，已放行云端原回答"; }
                if (answer!=null) { cachedQuestion=parseHintText(original).question;cachedAnswer=answer;cachedRevision=config.revision; }
            }
            delivery.deliver(hint,diagnostic);
        }
        void retire() { delivered.set(true);if(watchdog!=null)watchdog.cancel(); }
    }
    static Field textField(Object hint) throws NoSuchFieldException {
        Class<?> type=hint.getClass();
        while(type!=null && type!=Object.class) {
            try { Field field=type.getDeclaredField("text");field.setAccessible(true);return field; }
            catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException("text");
    }
    public static String augment(String original,String evidence) {
        HintPayload payload=parseHintText(original);
        if(evidence==null || evidence.trim().isEmpty() || payload.question.isEmpty()) return original;
        String clipped=evidence.trim();if(clipped.length()>80)clipped=clipped.substring(0,80)+"...";
        return replaceAnswer(original,"【知识库】"+clipped+(payload.answer==null?"":"\n【推荐】"+payload.answer.trim()));
    }
    public static String process(String original,CortrixClient client) {
        if(client==null || parseHintText(original).question.isEmpty())return original;
        return augment(original,client.queryBestEvidenceSync(parseHintText(original).question));
    }
    interface EvidenceLookup { String query(String question); }
    interface Completion { void accept(String result); }
    static void augmentAsync(String original,EvidenceLookup lookup,Executor worker,Executor main,Completion completion) {
        worker.execute(() -> {String value=original;try {value=augment(original,lookup.query(parseHintText(original).question));}catch(Exception ignored){}final String result=value;main.execute(()->completion.accept(result));});
    }
    public static void augmentHintObject(Object hint,CortrixClient client) {
        try {Field field=textField(hint);Object value=field.get(hint);if(value instanceof String)field.set(hint,process((String)value,client));}catch(Exception ignored){}
    }
}
