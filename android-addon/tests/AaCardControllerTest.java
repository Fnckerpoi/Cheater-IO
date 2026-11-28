package com.turboio.addon;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.turboio.addon.BoundedJson.map;
import static com.turboio.addon.CardCodec.object;

public final class AaCardControllerTest {
    private static final String ID = AaCardController.CARD_ID;
    private static final String PEER = NativeTransfer.peer;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static CardEnvelope.Reply packet() {
        List<byte[]> sent = NativeTransfer.packets;
        byte[] bytes = sent.get(sent.size() - 1).clone();
        bytes[3] = 19;
        bytes[bytes.length - 1] = 2;
        return CardEnvelope.reply(bytes);
    }

    private static void reply(Map<String, Object> payload) {
        byte[] bytes = CardEnvelope.request(packet().sequence, payload);
        bytes[3] = 19;
        bytes[bytes.length - 1] = 2;
        NativeTransfer.receiver.message(NativeTransfer.peer, bytes);
        NativeTransfer.MAIN.drain();
    }

    private static void config(List<Object> rows) {
        check("dashboard_config".equals(packet().json.get("cmd")), "configuration read");
        reply(object("cmd", "dashboard_config", "payload", object("data", object("widgets_v2", rows))));
    }

    private static Map<String, Object> writtenCard() {
        check("widget_install".equals(packet().json.get("cmd")), "targeted install");
        Map<String, Object> row = map(map(packet().json.get("payload")).get("data"));
        check(ID.equals(row.get("id")), "stable AA card ID");
        return row;
    }

    private static String text(String id) {
        for (Object row : BoundedJson.list(AaCardController.preview().get("components"))) {
            if (id.equals(map(row).get("id"))) return (String) map(row).get("text");
        }
        throw new AssertionError(id);
    }

    private static void outcome(ToolClientKnowledgeTest.FakeContext context, AaCardController.Binding binding,
                                String status, String answer) {
        AaCardController.record(context, binding, object("status", status, "sessionId", "session",
            "answer", answer), false);
        NativeTransfer.MAIN.drain();
    }

    public static void main(String[] args) {
        ToolClientKnowledgeTest.FakeContext context = new ToolClientKnowledgeTest.FakeContext();
        SharedPreferences prefs = context.getSharedPreferences("turboio_settings", 0);
        prefs.edit().putBoolean("dsh", true).putString("dsh_user_id", "account")
            .putString("dsh_connector", "办公室电脑").putString("dsh_runtime", "runtime")
            .putString("dsh_session_id", "session").putString("dsh_client_message_id", "old").commit();
        CardTransport.init();
        AaCardController.init(context);

        AaCardController.Binding old = AaCardController.binding(context);
        outcome(context, old, "running", "partial");
        check(NativeTransfer.packets.isEmpty(), "no writes before explicit install");
        check("执行中".equals(text("status")), "remote running state");
        Map<String, Object> weather = object("id", "weather", "type", "native");
        List<Object> dashboard = new ArrayList<>(Arrays.asList(weather));
        AaCardController.publish(PEER, false, AaCardController.preview());
        config(dashboard);
        Map<String, Object> running = writtenCard();
        reply(object("code", 0));
        dashboard.add(running);
        config(dashboard);
        int afterInstall = NativeTransfer.packets.size();
        outcome(context, old, "running", "partial");
        check(NativeTransfer.packets.size() == afterInstall, "same visible content skips delivery");

        prefs.edit().putString("dsh_client_message_id", "new").commit();
        outcome(context, old, "completed", "old task");
        check("状态待查询".equals(text("status")), "prior message in the same session is discarded");
        AaCardController.Binding current = AaCardController.binding(context);
        outcome(context, current, "completed", "完成内容\u0085😀中文中文中文中文中文中文中文中文");
        config(dashboard);
        Map<String, Object> completed = writtenCard();
        reply(object("code", 0));
        dashboard.set(1, completed);
        config(dashboard);
        check("已完成".equals(text("status")), "confirmed completion");
        check(!text("detail").contains("完成内容"), "reply hidden by default");

        NativeTransfer.peer = "other-glasses";
        int beforeSwitch = NativeTransfer.packets.size();
        AaCardController.setShowSummary(true);
        check(NativeTransfer.packets.size() == beforeSwitch, "opt-in cannot send to different glasses");
        check(text("detail").startsWith("完成内容 😀") && text("detail").endsWith("…"), "bounded Unicode answer");
        CardCodec.encode(AaCardController.preview(), CardIcons::pixels);
        NativeTransfer.peer = PEER;

        AaCardController.remove(PEER);
        outcome(context, current, "completed", "new content");
        config(dashboard);
        check("widget_uninstall".equals(packet().json.get("cmd")), "removal wins over incoming state");
        reply(object("code", 0));
        dashboard.remove(1);
        config(dashboard);
        int afterRemove = NativeTransfer.packets.size();
        outcome(context, current, "running", "");
        check(NativeTransfer.packets.size() == afterRemove, "removed card does not reinstall");

        System.out.println("AaCardControllerTest: install, update, stale result, opt-in summary, peer and removal passed");
    }
}
