package com.turboio.addon;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AaCardController {
    static final String CARD_ID = "turbo_ui_card_aa_workbench_01";
    private static final String TITLE = "AA 工作台";
    private static final List<String> TARGET_KEYS = Arrays.asList(
        "dsh_user_id", "dsh_url", "dsh_connector", "dsh_project", "dsh_runtime",
        "dsh_model", "dsh_permission", "dsh");
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final Runnable idleWake = () -> NativeTransfer.MAIN.post(AaCardController::send);
    private static final Runnable bindingChanged = AaCardController::changed;
    private static final SharedPreferences.OnSharedPreferenceChangeListener bindingListener = (prefs, key) -> {
        if (key == null || key.equals("dsh") || key.startsWith("dsh_") || key.equals(ToolClient.ALIAS_DOC)) {
            NativeTransfer.MAIN.removeCallbacks(bindingChanged);
            NativeTransfer.MAIN.post(bindingChanged);
        }
    };
    private static volatile Context app;
    private static SharedPreferences options;
    private static Binding resultBinding;
    private static String taskStatus = "", answer = "";
    private static String note = "尚未同步到眼镜";
    private static Map<String, Object> confirmedDocument;
    private static String confirmedPeer = "";
    private static String requestPeer = "";
    private static Map<String, Object> requestedDocument;
    private static boolean removeRequested, explicit, pinTop, inFlight, paused, refreshing;

    private AaCardController() {}

    static final class Binding {
        final Map<String, ?> values;
        final long auth;

        Binding(Map<String, ?> values, long auth) {
            this.values = new HashMap<>(values);
            this.auth = auth;
        }

        String text(String key) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : "";
        }

        boolean enabled() {
            return Boolean.TRUE.equals(values.get("dsh")) && !text("dsh_user_id").isEmpty()
                && !text("dsh_connector").isEmpty() && !text("dsh_runtime").isEmpty();
        }

        boolean sameTarget(Binding other) {
            if (other == null || auth != other.auth) return false;
            for (String key : TARGET_KEYS) {
                if (!Objects.equals(values.get(key), other.values.get(key))) return false;
            }
            return true;
        }

        boolean sameTask(Binding other) {
            return sameTarget(other) && session().equals(other.session()) && message().equals(other.message())
                && Objects.equals(values.get("dsh_unknown_delivery"), other.values.get("dsh_unknown_delivery"));
        }

        String session() { return text("dsh_session_id"); }
        String message() { return text("dsh_client_message_id"); }
    }

    static void init(Context context) {
        if (app != null) return;
        app = context.getApplicationContext();
        options = app.getSharedPreferences("turboio_aa_card", 0);
        app.getSharedPreferences("turboio_settings", 0).registerOnSharedPreferenceChangeListener(bindingListener);
        app.getSharedPreferences(ToolClient.ALIAS_FILE, 0).registerOnSharedPreferenceChangeListener(bindingListener);
    }

    static Binding binding(Context context) {
        synchronized (DshClient.LOCK) {
            return new Binding(context.getSharedPreferences("turboio_settings", 0).getAll(), DshSettings.authGeneration());
        }
    }

    static void record(Context context, Binding request, Map<String, Object> outcome, boolean dispatch) {
        if (app == null) return;
        String state = DshDirectory.text(outcome, "status");
        String session = DshDirectory.text(outcome, "sessionId");
        String message = DshDirectory.text(outcome, "clientMessageId");
        String reply = DshDirectory.text(outcome, "answer");
        NativeTransfer.MAIN.post(() -> {
            Binding live = binding(context);
            if (!live.enabled()) return;
            if (dispatch) {
                if (!live.sameTarget(request) || !live.session().equals(session)
                        || !live.message().equals(message) || message.isEmpty() || !"submitted".equals(state)) return;
                if (live.sameTask(resultBinding) && !"submitted".equals(taskStatus)) return;
            } else if (!live.sameTask(request) || (!session.isEmpty() && !session.equals(live.session()))) {
                return;
            }
            resultBinding = live;
            taskStatus = "failed".equals(state) && session.isEmpty() ? "unknown" : state;
            answer = "completed".equals(state) ? reply : "";
            changed();
        });
    }

    static void refresh() {
        if (refreshing) return;
        Binding request = binding(app);
        if (!request.enabled() || request.session().isEmpty()) {
            changed();
            return;
        }
        refreshing = true;
        notifyListeners();
        Context context = app;
        DshSettings.IO.execute(() -> {
            try {
                new ToolClient(context).readBoundStatus(request, null);
            } catch (Exception failure) {
                // The status reader already recorded the failure against this request.
            } finally {
                NativeTransfer.MAIN.post(() -> {
                    refreshing = false;
                    notifyListeners();
                });
            }
        });
    }

    static boolean refreshing() { return refreshing; }
    static String status() { return note; }
    static boolean showSummary() { return options.getBoolean("show_summary", false); }
    static void listen(Runnable r) { listeners.add(r); }
    static void unlisten(Runnable r) { listeners.remove(r); }

    static void setShowSummary(boolean show) {
        options.edit().putBoolean("show_summary", show).apply();
        changed();
    }

    static Map<String, Object> preview() {
        Binding live = binding(app);
        boolean matches = live.sameTask(resultBinding);
        String status = localStatus(live, matches ? taskStatus : "");
        String label = "未绑定电脑";
        if (live.enabled()) {
            label = live.text("dsh_connector");
            String aliases = app.getSharedPreferences(ToolClient.ALIAS_FILE, 0).getString(ToolClient.ALIAS_DOC, "");
            DshAliasRegistry table = DshSettings.Screen.voiceAliasTableFor(
                live.text("dsh_user_id"), live.text("dsh_url"), aliases);
            if (table != null) {
                for (DshAliasRegistry.AliasEntry entry : table.listSnapshot()) {
                    if (entry.connectorId.equals(label) && entry.projectId.equals(live.text("dsh_project"))
                            && entry.runtimeId.equals(live.text("dsh_runtime"))) {
                        label = entry.alias;
                        break;
                    }
                }
            }
        }
        return document(label, status, matches && showSummary() ? answer : "");
    }

    private static String localStatus(Binding binding, String remote) {
        if (Boolean.TRUE.equals(binding.values.get("dsh_unknown_delivery"))) return "unknown_delivery";
        if (!binding.enabled()) return "unconfigured";
        if (binding.session().isEmpty()) return "ready";
        if (!remote.isEmpty()) return remote;
        return binding.message().isEmpty() ? "session_selected" : "unknown";
    }

    static Map<String, Object> document(String target, String status, String answer) {
        String state;
        String detail = "请在手机查看详情";
        switch (status) {
            case "ready": state = "待命"; detail = "等待派发任务"; break;
            case "session_selected": state = "已选会话"; detail = "等待派发任务"; break;
            case "submitted": state = "已提交"; break;
            case "running": state = "执行中"; break;
            case "waiting_approval": state = "等待审批"; detail = "请在电脑上处理审批"; break;
            case "completed": state = "已完成"; break;
            case "failed": state = "执行失败"; break;
            case "idle_no_reply": state = "空闲 · 无回复"; break;
            case "unknown_delivery": state = "提交待核对"; break;
            case "unconfigured": state = "未配置"; detail = "请在手机绑定电脑"; break;
            default: state = "状态待查询";
        }
        String excerpt = cardLine(answer, 14);
        if ("completed".equals(status) && !excerpt.isEmpty()) detail = excerpt;
        String label = cardLine(target, 13);
        return CardCodec.object("schema", 1, "id", CARD_ID, "name", TITLE, "components", Arrays.asList(
            CardCodec.txt("title", TITLE, 12, 14, 232, 18),
            CardCodec.component("rule", "divider", 12, 46, 232, 2),
            CardCodec.txt("target", label.isEmpty() ? "未绑定电脑" : label, 12, 62, 232, 16),
            CardCodec.txt("status", state, 12, 96, 232, 24),
            CardCodec.txt("detail", detail, 12, 144, 232, 16)));
    }

    private static String cardLine(String text, int limit) {
        String line = text.replaceAll("[\\x00-\\x1F\\x7F-\\x9F\\x{2028}\\x{2029}\\s]+", " ").trim();
        int count = line.codePointCount(0, line.length());
        return count <= limit ? line : line.substring(0, line.offsetByCodePoints(0, limit - 1)) + "…";
    }

    static void publish(String peer, boolean top, Map<String, Object> preview) {
        if (!preview.equals(preview())) {
            note = "预览已变化，请重新确认";
            notifyListeners();
            return;
        }
        requestPeer = peer;
        requestedDocument = preview;
        removeRequested = false;
        pinTop = top;
        explicit = true;
        send();
    }

    static void remove(String peer) {
        if (peer.equals(options.getString("peer", ""))) options.edit().remove("peer").apply();
        requestPeer = peer;
        requestedDocument = null;
        removeRequested = true;
        explicit = true;
        send();
    }

    private static void changed() {
        if (!binding(app).sameTask(resultBinding)) {
            resultBinding = null;
            taskStatus = answer = "";
        }
        if (!explicit && !paused) send();
        notifyListeners();
    }

    private static void send() {
        if (inFlight || (!explicit && paused)) return;
        String peer = NativeTransfer.connected();
        boolean manual = explicit;
        boolean remove = manual && removeRequested;
        boolean top = manual && pinTop;
        if (peer.isEmpty() || !peer.equals(manual ? requestPeer : options.getString("peer", ""))) {
            if (manual || !options.getString("peer", "").isEmpty()) {
                note = "眼镜未连接或已切换，请连接原眼镜后同步";
                explicit = false;
                notifyListeners();
            }
            return;
        }
        Map<String, Object> doc = remove ? null : preview();
        if (manual && !remove && !doc.equals(requestedDocument)) {
            explicit = false;
            note = "预览已变化，请重新确认";
            notifyListeners();
            return;
        }
        if (!manual && peer.equals(confirmedPeer) && doc.equals(confirmedDocument)) return;
        if (CardTransport.busy()) {
            note = "等待当前卡片传输完成";
            CardTransport.whenIdle(idleWake);
            notifyListeners();
            return;
        }
        explicit = false;
        requestedDocument = null;
        inFlight = true;
        note = remove ? "正在移除卡片" : "正在同步眼镜";
        CardTransport.Completion done = result -> {
            inFlight = false;
            paused = result != CardTransport.Result.SUCCESS;
            if (!paused) {
                confirmedPeer = peer;
                confirmedDocument = doc;
                if (manual && !remove && !removeRequested) options.edit().putString("peer", peer).apply();
            }
            note = paused ? "未同步 · " + CardTransport.status()
                : remove ? "卡片已移除并回读确认" : "卡片已回读确认";
            notifyListeners();
            NativeTransfer.MAIN.post(AaCardController::send);
        };
        if (remove) CardTransport.remove(CARD_ID, done);
        else CardTransport.publish(doc, top, !manual, done);
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
}
