package com.turboio.addon;

final class ToolRoundTrace {
    private static final String[] TOOLS={"web_search","dsh_dispatch","dsh_status","dsh_end_session","knowledge_query"};
    private long generation;
    private boolean finished, intent, escalated, dshCalled, dispatched;
    private String registered="pending", calls="none", dsh="none", error="none";
    private int count, http;

    // Knowledge query tracing
    private boolean kbInvoked;
    private String kbStatus="none";
    private int kbCount;
    private long kbElapsedMs;
    private double kbCoverage;
    private int kbNsFailed;
    private boolean kbTruncated;

    // Durable completed round trace
    private boolean hasCompleted;
    private long lastGen;
    private boolean lastOk;
    private String lastRegistered = "none";
    private String lastCalls = "none";
    private int lastCount;
    private int lastHttp;
    private String lastError = "none";
    private boolean lastKbInvoked;
    private String lastKbStatus = "none";
    private int lastKbCount;
    private long lastKbElapsedMs;
    private double lastKbCoverage;
    private int lastKbNsFailed;
    private boolean lastKbTruncated;

    synchronized void reset(long token) {
        generation=token; finished=false; intent=false; escalated=false; dshCalled=false; dispatched=false;
        registered="pending"; calls="none"; dsh="none"; error="none"; count=0; http=0;
        kbInvoked=false; kbStatus="none"; kbCount=0; kbElapsedMs=0L; kbCoverage=0.0; kbNsFailed=0; kbTruncated=false;
    }

    static final String NOT_DISPATCHED="本轮未调用 DSH 工具，未通过本轮工具派发任务；远端其他任务状态未知。";
    static final String REMINDER="请遵循用户完整含义，包括否定、引用与概念讨论；本提醒不是派发授权，不得把动作词匹配视为授权。请核对用户本轮意图：若用户明确要 DSH 执行已授权任务或传话，应调用已注册的 dsh_dispatch；查询结果用 dsh_status。若只是询问概念可直接回答。仅提及 DSH 不构成授权，不得扩大权限、构造授权或重复派发；未调用工具不能声称已提交，submitted/running/unknown_delivery 不代表完成。";
    private static boolean isDsh(String name) { return "dsh_dispatch".equals(name)||"dsh_status".equals(name)||"dsh_end_session".equals(name); }

    static boolean actionIntent(String input) {
        if(input==null||input.length()>2000)return false;
        String text=input.toLowerCase(java.util.Locale.ROOT);
        if(!text.contains("dsh")&&!text.contains("电脑端"))return false;
        for(String deny:new String[]{"不要","别让","不用","不需要","是什么","什么意思","如何","怎么","能否","能不能","是否","支持吗"})if(text.contains(deny))return false;
        for(String verb:new String[]{"执行","检查","构建","跑","查","传话","派发"})if(text.contains(verb))return true;
        return false;
    }

    synchronized void intent(long token,String input) { if(active(token))intent=actionIntent(input); }
    synchronized boolean holdAnswer(long token) { return token==generation&&intent&&!dshCalled; }
    synchronized boolean escalate(long token) { if(!active(token)||!intent||dshCalled||escalated||!registered.contains("dsh_dispatch"))return false;escalated=true;return true; }
    synchronized String noDispatch(long token) { return token==generation&&intent&&!dshCalled?(registered.contains("dsh_dispatch")?NOT_DISPATCHED:"本轮未调用 DSH 工具：dsh_dispatch 未注册，请检查启用状态与配置；远端其他任务状态未知。"):""; }
    private boolean active(long token) { return token==generation&&!finished; }
    private static String allow(String value,String fallback,String... allowed) { for(String item:allowed) if(item.equals(value))return item;return fallback; }

    synchronized void register(long token,String[] names) {
        if(!active(token))return;
        StringBuilder out=new StringBuilder();
        for(String name:TOOLS) for(String candidate:names) if(name.equals(candidate)) { if(out.length()>0)out.append(',');out.append(name);break; }
        registered=out.length()==0?"none":out.toString();
    }

    synchronized void call(long token,String name) {
        if(!active(token))return;
        if(isDsh(name))dshCalled=true;
        if("dsh_dispatch".equals(name))dispatched=true;
        if(count<3) { calls=(count==0?"":calls+",")+allow(name,"other",TOOLS);count++; }
    }

    synchronized void http(long token,int status) { if(active(token))http=status>=100&&status<=599?status:0; }

    static String normalizeKbStatus(String raw) {
        if(raw==null||raw.isEmpty())return "ERROR";
        String upper=raw.trim().toUpperCase(java.util.Locale.ROOT);
        switch(upper) {
            case "HIT": return "HIT";
            case "EMPTY": return "EMPTY";
            case "TIMEOUT": return "TIMEOUT";
            case "AUTH_FAILED":
            case "AUTH_ERROR": return "AUTH_FAILED";
            case "OFFLINE":
            case "DISABLED":
            case "TRANSPORT_ERROR":
            case "UNCONFIGURED": return "OFFLINE";
            case "PARTIAL": return "PARTIAL";
            case "ERROR":
            case "TOOL_ERROR":
            case "PROTOCOL_ERROR":
            default:
                return "ERROR";
        }
    }

    synchronized void knowledge(long token,String query,String status,int count,long elapsedMs,double coverage,int nsFailed,boolean truncated) {
        if(!active(token))return;
        kbInvoked=true;
        kbStatus=normalizeKbStatus(status);
        kbCount=Math.max(0,count);
        kbElapsedMs=Math.max(0L,elapsedMs);
        kbCoverage=Math.max(0.0,Math.min(1.0,coverage));
        kbNsFailed=Math.max(0,nsFailed);
        kbTruncated=truncated;
    }

    synchronized void result(long token,String name,String status,String code) {
        if(!active(token))return;
        if("knowledge_query".equals(name)) {
            kbInvoked=true;
            if("none".equals(kbStatus)) {
                kbStatus=normalizeKbStatus(status);
            }
            if(code!=null&&!code.isEmpty())error(token,code);
            return;
        }
        if("dsh_dispatch".equals(name)||"dsh_status".equals(name)||"dsh_end_session".equals(name)) {
            String value=allow(status,"other","submitted","running","unknown_delivery","waiting_approval","idle_no_reply","completed","failed","ended","locked","no_session","succeeded");
            if(dsh.length()<300)dsh=("none".equals(dsh)?"":dsh+",")+allow(name,"other",TOOLS)+":"+value;
            if(code!=null&&!code.isEmpty())error(token,code);
        }
    }

    synchronized void error(long token,String code) { if(active(token))error=allow(code,"other","invalid_arguments","tool_disabled","dsh_storage_failed","dsh_config_changed","dispatch_budget","session_archived","session_unavailable","session_deleted","session_missing","request_failed","response_invalid","tool_budget","tool_limit","too_many_tools","invalid_tool_id","incomplete_stream","empty_answer","tool_round_limit","stream_limit","answer_limit","provider_error"); }

    static String exceptionCode(Exception failure) {
        if(failure instanceof IllegalArgumentException)return "tool_disabled".equals(failure.getMessage())?"tool_disabled":"invalid_arguments";
        if(failure instanceof org.json.JSONException)return "invalid_arguments";
        if(failure instanceof IllegalStateException)return allow(failure.getMessage(),"request_failed","dsh_storage_failed","dsh_config_changed");
        if(failure instanceof java.io.IOException)return allow(failure.getMessage(),"request_failed","tool_budget","tool_limit","too_many_tools","invalid_tool_id","incomplete_stream","empty_answer","tool_round_limit","stream_limit","answer_limit","provider_error");
        return "request_failed";
    }

    public synchronized void finish(long token) { finish(token, true); }

    public synchronized void finish(long token, boolean ok) {
        if (token == generation) {
            finished = true;
            hasCompleted = true;
            lastGen = generation;
            lastOk = ok;
            lastRegistered = registered;
            lastCalls = calls;
            lastCount = count;
            lastHttp = http;
            lastError = error;
            lastKbInvoked = kbInvoked;
            lastKbStatus = kbStatus;
            lastKbCount = kbCount;
            lastKbElapsedMs = kbElapsedMs;
            lastKbCoverage = kbCoverage;
            lastKbNsFailed = kbNsFailed;
            lastKbTruncated = kbTruncated;
        }
    }

    synchronized String knowledgeDiagnostic() {
        if (kbInvoked) {
            String covStr = String.format(java.util.Locale.ROOT, "%.1f", kbCoverage);
            return "[Tool] knowledge_query: status=" + kbStatus + " count=" + kbCount + " coverage=" + covStr + " elapsed=" + kbElapsedMs + "ms";
        } else if (hasCompleted && lastKbInvoked) {
            return "knowledge_query: not_invoked (direct answer) | last_gen" + lastGen + ": " + lastKbStatus + " count=" + lastKbCount;
        } else {
            return "knowledge_query: not_invoked (direct answer)";
        }
    }

    synchronized String snapshot() {
        String covStr = String.format(java.util.Locale.ROOT, "%.1f", kbCoverage);
        String kqPart = !kbInvoked
            ? "knowledge_query: not_invoked (direct answer)"
            : "knowledge_query=status=" + kbStatus + " count=" + kbCount + " coverage=" + covStr + " elapsed=" + kbElapsedMs + "ms nsfailed=" + kbNsFailed + " truncated=" + kbTruncated;

        String currentPart = "tool_gen=" + generation + " | intent=" + (intent ? "yes" : "no") + " | escalation=" + (escalated ? "once" : "off") + " | dispatch_selected=" + (dispatched ? "yes" : "no") + " | registered=" + registered + " | tool_calls=" + count + ":" + calls + " | dsh=" + dsh + " | " + kqPart + " | tool_error=" + error + " | http=" + http;

        if (!hasCompleted) {
            return currentPart + " | last_round=none";
        }
        String lastCovStr = String.format(java.util.Locale.ROOT, "%.1f", lastKbCoverage);
        String lastKqPart = !lastKbInvoked ? "not_invoked" : "kq=" + lastKbStatus + ":" + lastKbCount + "@" + lastKbElapsedMs + "ms";
        String lastRoundSummary = "last_round=gen" + lastGen + "(" + (lastOk ? "ok" : "err") + ",http=" + lastHttp + ",tools=" + lastCalls + "," + lastKqPart + ")";
        return currentPart + " | " + lastRoundSummary;
    }
}
