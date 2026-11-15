package com.turboio.addon;
public final class ToolRoundTraceTest {
    private static int checks;
    private static void check(boolean value) { if(!value)throw new AssertionError("check "+checks);checks++; }
    public static void main(String[] args) throws Exception {
        ToolRoundTrace trace=new ToolRoundTrace();trace.reset(1);
        trace.register(1,new String[0]);check(trace.snapshot().contains("registered=none"));
        trace.register(1,new String[]{"dsh_dispatch","dsh_status","SECRET_BODY_URL","dsh_dispatch"});
        check(trace.snapshot().contains("registered=dsh_dispatch,dsh_status"));check(trace.snapshot().contains("tool_calls=0:none"));
        check(!trace.snapshot().contains("SECRET"));
        trace.call(1,"dsh_dispatch");trace.error(1,ToolRoundTrace.exceptionCode(new IllegalArgumentException("secret_leak")));
        trace.result(1,"dsh_dispatch","failed","");check(trace.snapshot().contains("tool_error=invalid_arguments"));
        trace.result(1,"dsh_dispatch","unknown_delivery","");check(trace.snapshot().contains("dsh_dispatch:unknown_delivery"));
        trace.reset(2);trace.register(2,new String[]{"dsh_dispatch"});trace.call(2,"dsh_dispatch");trace.result(2,"dsh_dispatch","submitted","");
        check(trace.snapshot().contains("dsh_dispatch:submitted"));check(!trace.snapshot().contains("unknown_delivery"));
        String before=trace.snapshot();
        Thread old=new Thread(()->{trace.register(1,new String[0]);trace.call(1,"dsh_status");trace.result(1,"dsh_status","completed","");trace.error(1,"invalid_arguments");trace.http(1,500);trace.finish(1);});old.start();old.join();
        check(before.equals(trace.snapshot()));trace.http(2,200);check(trace.snapshot().contains("http=200"));
        trace.finish(2);before=trace.snapshot();trace.result(2,"dsh_dispatch","completed","");trace.error(2,"other");trace.http(2,500);check(before.equals(trace.snapshot()));
        trace.reset(3);trace.call(3,"SECRET_BODY_URL");trace.result(3,"dsh_dispatch","SECRET_BODY_URL","SECRET_BODY_URL");
        check(!trace.snapshot().contains("SECRET"));check(trace.snapshot().contains("tool_error=other"));
        for(int i=0;i<100;i++)trace.call(3,"web_search");check(trace.snapshot().contains("tool_calls=3:"));check(trace.snapshot().length()<600);
        check(ToolRoundTrace.exceptionCode(new java.io.IOException("https://secret.invalid/body")).equals("request_failed"));
        check(ToolRoundTrace.exceptionCode(new IllegalStateException("dsh_storage_failed")).equals("dsh_storage_failed"));
        for(String input:new String[]{"让DSH执行任务","请电脑端检查结果","DSH构建项目","让dsh跑测试","DSH查一下进度","向DSH传话","请DSH派发任务"})check(ToolRoundTrace.actionIntent(input));
        for(String input:new String[]{"DSH是什么","DSH如何执行","不要让DSH执行","DSH能不能跑测试","检查天气","介绍DSH","电脑端支持吗",new String(new char[2001])})check(!ToolRoundTrace.actionIntent(input));
        check(!ToolRoundTrace.actionIntent(null));
        trace.reset(4);trace.register(4,new String[]{"dsh_dispatch"});trace.intent(4,"让DSH执行任务");check(trace.holdAnswer(4));check(trace.escalate(4));check(!trace.escalate(4));
        check(trace.snapshot().contains("intent=yes | escalation=once"));check(trace.noDispatch(4).equals(ToolRoundTrace.NOT_DISPATCHED));
        check(trace.noDispatch(4).contains("本轮未调用 DSH 工具"));check(!trace.snapshot().contains("让DSH"));
        trace.call(4,"dsh_dispatch");check(trace.noDispatch(4).isEmpty());check(trace.snapshot().contains("dispatch_selected=yes"));check(!trace.escalate(4));
        for(String tool:new String[]{"dsh_status","dsh_end_session","dsh_dispatch"}) { trace.reset(5);trace.intent(5,"让DSH执行任务");trace.call(5,tool);check(!trace.escalate(5));check(trace.noDispatch(5).isEmpty()); }
        trace.reset(6);before=trace.snapshot();trace.intent(5,"让DSH执行");trace.call(5,"dsh_dispatch");check(!trace.escalate(5));check(trace.noDispatch(5).isEmpty());check(before.equals(trace.snapshot()));
        check(trace.noDispatch(6).isEmpty());check(trace.snapshot().contains("intent=no | escalation=off | dispatch_selected=no"));
        trace.intent(6,"DSH是什么意思");check(!trace.escalate(6));check(!trace.holdAnswer(6));
        trace.reset(7);trace.register(7,new String[]{"dsh_dispatch"});trace.intent(7,"让DSH执行任务");trace.call(7,"web_search");check(trace.escalate(7));trace.finish(7);check(!trace.escalate(7));
        trace.reset(8);trace.intent(8,"让DSH执行任务");trace.register(8,new String[0]);check(!trace.escalate(8));check(trace.noDispatch(8).contains("未注册"));
        trace.reset(9);trace.intent(9,"让DSH执行任务");trace.call(9,"dsh_status");check(!trace.holdAnswer(9));check(trace.noDispatch(9).isEmpty());

        // --- Task 4 Knowledge Query Tracing Tests ---
        // 1. knowledge_query is registered and tracked in registered and tool_calls
        trace.reset(10);trace.register(10,new String[]{"knowledge_query","SECRET_BODY_URL"});
        check(trace.snapshot().contains("registered=knowledge_query"));check(!trace.snapshot().contains("SECRET"));
        trace.call(10,"knowledge_query");trace.call(10,"knowledge_query");trace.call(10,"dsh_dispatch");
        check(trace.snapshot().contains("tool_calls=3:knowledge_query,knowledge_query,dsh_dispatch"));
        check(trace.snapshot().contains("knowledge_query: not_invoked (direct answer)"));
        String noKbDiagnostic=trace.knowledgeDiagnostic();
        check(noKbDiagnostic.equals("knowledge_query: not_invoked (direct answer)"));

        // 2. Direct turn (no knowledge_query called) emits not_invoked in both snapshot and helper
        trace.reset(11);trace.register(11,new String[]{"knowledge_query","dsh_dispatch"});trace.call(11,"dsh_dispatch");
        check(trace.snapshot().contains("knowledge_query: not_invoked (direct answer)"));
        check(trace.knowledgeDiagnostic().equals("knowledge_query: not_invoked (direct answer)"));

        // 3. Invoked with HIT status, count, elapsed, coverage, nsfailed, truncated
        trace.reset(12);trace.register(12,new String[]{"knowledge_query"});trace.call(12,"knowledge_query");
        trace.knowledge(12,"SCADA architecture","HIT",3,412L,1.0,0,false);
        String hit=trace.snapshot();
        check(hit.contains("knowledge_query=status=HIT count=3 coverage=1.0 elapsed=412ms nsfailed=0 truncated=false"));
        check(!hit.contains("REAL_EVIDENCE"));check(!hit.contains("doc-101"));
        String hitDiagnostic=trace.knowledgeDiagnostic();
        check(hitDiagnostic.equals("[Tool] knowledge_query: status=HIT count=3 coverage=1.0 elapsed=412ms"));

        // 4. Invoked with EMPTY
        trace.reset(13);trace.register(13,new String[]{"knowledge_query"});trace.call(13,"knowledge_query");
        trace.knowledge(13,"project record","EMPTY",0,0L,0.0,0,false);
        check(trace.snapshot().contains("knowledge_query=status=EMPTY count=0 coverage=0.0 elapsed=0ms nsfailed=0 truncated=false"));
        check(trace.knowledgeDiagnostic().equals("[Tool] knowledge_query: status=EMPTY count=0 coverage=0.0 elapsed=0ms"));

        // 5. Invoked with TIMEOUT
        trace.reset(14);trace.register(14,new String[]{"knowledge_query"});trace.call(14,"knowledge_query");
        trace.knowledge(14,"project record","TIMEOUT",0,30000L,0.0,2,false);
        check(trace.snapshot().contains("knowledge_query=status=TIMEOUT count=0 coverage=0.0 elapsed=30000ms nsfailed=2 truncated=false"));
        check(trace.knowledgeDiagnostic().equals("[Tool] knowledge_query: status=TIMEOUT count=0 coverage=0.0 elapsed=30000ms"));

        // 6. Invoked with ERROR and truncated flag
        trace.reset(15);trace.register(15,new String[]{"knowledge_query"});trace.call(15,"knowledge_query");
        trace.knowledge(15,"project record","ERROR",0,88L,0.0,0,true);
        check(trace.snapshot().contains("knowledge_query=status=ERROR count=0 coverage=0.0 elapsed=88ms nsfailed=0 truncated=true"));
        check(trace.knowledgeDiagnostic().equals("[Tool] knowledge_query: status=ERROR count=0 coverage=0.0 elapsed=88ms"));

        // 7. Invoked with PARTIAL
        trace.reset(16);trace.register(16,new String[]{"knowledge_query"});trace.call(16,"knowledge_query");
        trace.knowledge(16,"project record","PARTIAL",2,150L,0.86,1,false);
        check(trace.snapshot().contains("knowledge_query=status=PARTIAL count=2 coverage=0.9 elapsed=150ms nsfailed=1 truncated=false"));

        // 8. normalizeKbStatus sanitization
        check(ToolRoundTrace.normalizeKbStatus(null).equals("ERROR"));
        check(ToolRoundTrace.normalizeKbStatus("").equals("ERROR"));
        check(ToolRoundTrace.normalizeKbStatus("banner-value").equals("ERROR"));
        check(ToolRoundTrace.normalizeKbStatus("PROTOCOL_ERROR").equals("ERROR"));
        check(ToolRoundTrace.normalizeKbStatus("AUTH_ERROR").equals("AUTH_FAILED"));
        check(ToolRoundTrace.normalizeKbStatus("DISABLED").equals("OFFLINE"));
        check(ToolRoundTrace.normalizeKbStatus("HIT").equals("HIT"));
        check(ToolRoundTrace.normalizeKbStatus("hit").equals("HIT"));
        check(ToolRoundTrace.normalizeKbStatus("EMPTY").equals("EMPTY"));
        check(ToolRoundTrace.normalizeKbStatus("TIMEOUT").equals("TIMEOUT"));
        check(ToolRoundTrace.normalizeKbStatus("AUTH_FAILED").equals("AUTH_FAILED"));
        check(ToolRoundTrace.normalizeKbStatus("OFFLINE").equals("OFFLINE"));
        check(ToolRoundTrace.normalizeKbStatus("ERROR").equals("ERROR"));
        check(ToolRoundTrace.normalizeKbStatus("PARTIAL").equals("PARTIAL"));

        // 9. Knowledge query contents must not appear in diagnostics
        String secretQuery="SECRET_PREFIX_12345 additional query content";
        trace.reset(17);trace.register(17,new String[]{"knowledge_query"});trace.call(17,"knowledge_query");
        trace.knowledge(17,secretQuery,"HIT",1,11L,1.0,0,false);
        check(!trace.snapshot().contains("SECRET_PREFIX_12345"));
        check(!trace.snapshot().contains("additional query content"));
        check(!trace.knowledgeDiagnostic().contains("SECRET_PREFIX_12345"));

        // 10. Concurrency & generation token isolation
        trace.reset(19);trace.register(19,new String[]{"knowledge_query"});trace.call(19,"knowledge_query");
        trace.knowledge(19,"冻结前","HIT",0,10L,1.0,0,false);trace.finish(19);
        String frozenKb=trace.snapshot();trace.knowledge(19,"冻结后","TIMEOUT",0,9999L,0.0,0,false);check(frozenKb.equals(trace.snapshot()));
        check(!trace.snapshot().contains("冻结后"));

        // 11. Durable completed round trace survives reset(token)
        ToolRoundTrace durable=new ToolRoundTrace();
        durable.reset(100);
        check(durable.snapshot().contains("last_round=none"));
        durable.register(100,new String[]{"knowledge_query"});
        durable.call(100,"knowledge_query");
        durable.http(100,200);
        durable.knowledge(100,"register spec","HIT",4,235L,1.0,0,false);
        durable.finish(100,true);
        check(durable.snapshot().contains("last_round=gen100(ok,http=200,tools=knowledge_query,kq=HIT:4@235ms)"));

        // Subsequent cancel/reset clears current round but preserves last_round
        durable.reset(101);
        check(durable.snapshot().contains("tool_gen=101"));
        check(durable.snapshot().contains("registered=pending"));
        check(durable.snapshot().contains("tool_calls=0:none"));
        check(durable.snapshot().contains("last_round=gen100(ok,http=200,tools=knowledge_query,kq=HIT:4@235ms)"));
        check(durable.knowledgeDiagnostic().equals("knowledge_query: not_invoked (direct answer) | last_gen100: HIT count=4"));

        // Failed round finish(token, false) records err status
        durable.register(101,new String[]{"knowledge_query"});
        durable.http(101,502);
        durable.finish(101,false);
        check(durable.snapshot().contains("last_round=gen101(err,http=502,tools=none,not_invoked)"));

        System.out.println("PASS "+checks+" tool trace checks");
    }
}
