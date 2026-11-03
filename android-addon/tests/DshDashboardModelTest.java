package com.turboio.addon;

import java.util.HashMap;
import java.util.Map;

/** Standalone behavioral regression suite; no Android, network or test framework required. */
public final class DshDashboardModelTest {
    private static int checks, failures;
    private static final DshProactiveReport.RoundIdentity ROUND = round("account-private", 1, 1, "target-private", "session-private", "message-private");
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) { failures++; System.err.println("FAIL: " + reason); }
    }
    private static DshProactiveReport.RoundIdentity round(String account, long auth, long selection, String target, String session, String message) {
        return new DshProactiveReport.RoundIdentity(account, auth, selection, target, session, message);
    }
    private static Map<String,Object> frame(String status) {
        Map<String,Object> value = new HashMap<>(); value.put("status", status); return value;
    }
    private static Map<String,Object> completed(String answer) {
        Map<String,Object> value = frame("completed"); value.put("answer", answer);
        value.put("completionEvidence", DshProactiveReport.COMPLETION_EVIDENCE); return value;
    }
    private static void freshnessAndRecovery() {
        DshDashboardModel model = new DshDashboardModel(); long token = model.start(ROUND, "办公电脑", 100);
        check(model.snapshot(100).lastObservedMillis == -1, "start has no remote observation");
        check(!model.snapshot(15100).stale, "start baseline boundary");
        check(model.snapshot(15101).stale, "start expires without callbacks");
        check(model.accept(token, ROUND, frame("submitted"), 15102), "identical submitted recovers stale");
        check(model.accept(token, ROUND, completed("结果"), 15200), "completed evidence changes state");
        check(!model.accept(token, ROUND, completed("结果"), 15300), "identical live frame suppressed");
        check(model.snapshot(15300).lastObservedMillis == 15300, "duplicate observation refreshes timestamp");
        check(!model.snapshot(30300).stale, "freshness inclusive boundary");
        check(model.snapshot(30301).stale, "terminal expires independently");
        check(model.snapshot(15200).stale, "backward query cannot revive terminal");
        check(model.accept(token, ROUND, completed("结果"), 30302), "identical terminal recovers stale with true change");
        check(!model.snapshot(30302).stale, "genuine new evidence restores freshness");
    }
    private static void localEventsKeepEvidence() {
        for (String status : new String[]{"timeout", "stopped", "network"}) {
            DshDashboardModel model = new DshDashboardModel(); long token = model.start(ROUND, "电脑", 10);
            model.accept(token, ROUND, completed("结果"), 20); model.snapshot(15021);
            if (status.equals("network")) model.connectionUnknown(token, ROUND, 15022);
            else model.accept(token, ROUND, frame(status), 15022);
            check(model.snapshot(15022).lastObservedMillis == 20, status + " preserves last remote evidence");
            check(model.snapshot(15022).stale, status + " cannot restore freshness");
        }
        DshDashboardModel model = new DshDashboardModel(); long token = model.start(ROUND, "电脑", 10);
        Map<String,Object> net = frame("unknown"); net.put("stopReason", "network_errors");
        model.accept(token, ROUND, net, 11);
        check(model.snapshot(11).lastObservedMillis == -1, "local failure before first response preserves sentinel");
        check(model.snapshot(11).status == DshDashboardModel.Status.QUERY_UNKNOWN, "network evidence classifies query unknown");
        model.accept(token, ROUND, frame("unknown"), 12);
        check(model.snapshot(12).status == DshDashboardModel.Status.UNKNOWN, "unknown alone does not mean disconnected");
        check(model.snapshot(12).lastObservedMillis == 12, "remote unknown is observed evidence");
        model.accept(token, ROUND, frame("failed"), 13);
        check(model.snapshot(13).status == DshDashboardModel.Status.FAILED, "remote failed remains separate");
        check(model.snapshot(13).lastObservedMillis == 13, "actual remote failure is observed evidence");
    }
    private static void identityAndGenerations() {
        DshDashboardModel model = new DshDashboardModel(); long first = model.start(ROUND, "一", 100);
        model.accept(first, ROUND, completed("旧结果"), 101); DshDashboardModel.View old = model.snapshot(101);
        DshProactiveReport.RoundIdentity[] changed = {
            round("other-account",1,1,"target-private","session-private","message-private"),
            round("account-private",2,1,"target-private","session-private","message-private"),
            round("account-private",1,2,"target-private","session-private","message-private"),
            round("account-private",1,1,"other-target","session-private","message-private"),
            round("account-private",1,1,"target-private","other-session","message-private"),
            round("account-private",1,1,"target-private","session-private","other-message")
        };
        for (DshProactiveReport.RoundIdentity identity : changed) {
            check(!model.accept(first, identity, completed("错误结果"), 102), "identity tuple mismatch rejected");
            check(!model.connectionUnknown(first, identity, 102), "wrong identity local failure rejected");
        }
        long second = model.start(changed[3], "二", 103); long third = model.start(ROUND, "三", 104);
        check(!model.accept(first, ROUND, completed("旧结果"), 105), "ABA old generation rejected");
        check(!model.accept(second, changed[3], completed("旧结果"), 105), "middle round callback rejected");
        check(!model.connectionUnknown(first, ROUND, 105), "late local failure old generation rejected");
        check(model.snapshot(105).status == DshDashboardModel.Status.SUBMITTED, "late events leave new submitted state");
        check(model.snapshot(105).resultLine.isEmpty(), "start clears result immediately");
        check(old.resultLine.equals("旧结果") && old.lastObservedMillis == 101, "old view immutable across starts");
        Map<String,Object> bad = frame("failed"); bad.put("sessionId", "wrong");
        check(!model.accept(third, ROUND, bad, 106), "supplied session mismatch rejected");
        bad.remove("sessionId"); bad.put("clientMessageId", "wrong");
        check(!model.accept(third, ROUND, bad, 106), "supplied message mismatch rejected");
        bad.put("clientMessageId", null); check(!model.accept(third, ROUND, bad, 106), "present null message rejected");
        check(!model.connectionUnknown(third, ROUND, 104), "backward local failure rejected");
        boolean rejected = false; try { model.start(ROUND, "倒退", 104); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "backward start rejected across generations");
        check(model.snapshot(106).targetLine.equals("三"), "invalid start does not mutate view");
    }
    private static void statesAndCompletion() {
        DshDashboardModel model = new DshDashboardModel(); long token = model.start(ROUND, "电脑", 0);
        String[] names = {"submitted","running","waiting_approval","idle_no_reply","failed","unknown","unknown_delivery","timeout","stopped","garbage"};
        DshDashboardModel.Status[] states = {DshDashboardModel.Status.SUBMITTED,DshDashboardModel.Status.RUNNING,DshDashboardModel.Status.WAITING_APPROVAL,DshDashboardModel.Status.IDLE_NO_REPLY,DshDashboardModel.Status.FAILED,DshDashboardModel.Status.UNKNOWN,DshDashboardModel.Status.UNKNOWN_DELIVERY,DshDashboardModel.Status.TIMEOUT,DshDashboardModel.Status.STOPPED,DshDashboardModel.Status.UNKNOWN};
        for (int i=0;i<names.length;i++) {
            Map<String,Object> event=frame(names[i]); event.put("answer", "部分答案"); model.accept(token,ROUND,event,i+1);
            DshDashboardModel.View v=model.snapshot(i+1);
            check(v.status == states[i], names[i] + " maps correctly");
            check(v.resultLine.isEmpty(), names[i] + " does not show partial as final");
            check(v.statusLine.matches(".*[\u4e00-\u9fff].*"), names[i] + " Chinese product label");
        }
        Map<String,Object> unproven=frame("completed"); unproven.put("answer","未证实");model.accept(token,ROUND,unproven,20);
        check(model.snapshot(20).status==DshDashboardModel.Status.UNKNOWN && model.snapshot(20).resultLine.isEmpty(), "completed needs explicit proof");
        model.accept(token,ROUND,completed(""),21); check(model.snapshot(21).resultLine.isEmpty(), "no fabricated empty answer");
    }
    private static boolean validLine(String text, int max) {
        if (text.length()>max) return false;
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c)==Character.FORMAT || c=='\u2028' || c=='\u2029') return false;
            if (Character.isHighSurrogate(c)) {if (++i>=text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;}
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    private static void boundsAndPrivacy() {
        DshDashboardModel model=new DshDashboardModel();
        String prefix=new String(new char[39]).replace('\0','x'); long token=model.start(ROUND,prefix+"😀extra",10);
        model.accept(token,ROUND,completed(new String(new char[199]).replace('\0','x')+"😀extra"),11);
        DshDashboardModel.View v=model.snapshot(11);
        check(validLine(v.targetLine,DshDashboardModel.MAX_TARGET), "target truncation preserves Unicode");
        check(validLine(v.resultLine,DshDashboardModel.MAX_RESULT), "result truncation preserves Unicode");
        token=model.start(ROUND,"电脑\n\u0085\u2028\u202e",12);
        model.accept(token,ROUND,completed("答复\t\u0085\u2029\u202e"),13);v=model.snapshot(13);
        check(validLine(v.targetLine,DshDashboardModel.MAX_TARGET), "target removes controls");
        check(validLine(v.resultLine,DshDashboardModel.MAX_RESULT), "result removes controls");
        token=model.start(ROUND,ROUND.account+" "+ROUND.targetFingerprint,14);
        model.accept(token,ROUND,completed(ROUND.account+" "+ROUND.targetFingerprint+" "+ROUND.sessionId+" "+ROUND.clientMessageId),15);v=model.snapshot(15);
        for(String text:new String[]{v.targetLine,v.statusLine,v.resultLine})
            for(String identity:new String[]{ROUND.account,ROUND.targetFingerprint,ROUND.sessionId,ROUND.clientMessageId})
                check(!text.contains(identity), "actual presentation fields redact exact identities");
    }
    public static void main(String[] args) {
        freshnessAndRecovery(); localEventsKeepEvidence(); identityAndGenerations(); statesAndCompletion(); boundsAndPrivacy();
        if(failures!=0) throw new AssertionError("DshDashboardModelTest: " + failures + " failed / " + checks + " checks");
        System.out.println("DshDashboardModelTest: " + checks + " passed");
    }
}
