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
        System.out.println("PASS "+checks+" tool trace checks");
    }
}
