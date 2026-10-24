package com.turboio.addon;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Agents-Anywhere /api/v2 protocol client and pure state evaluator for DSH dispatch. */
public final class DshClient {
    public static final Object LOCK = new Object();
    public static final int MAX_TASK_LENGTH = 500;
    public static final int MAX_ANSWER_LENGTH = 2000;
    private static final int MAX_ID_LENGTH = 200;
    private static final int MAX_MODEL_LENGTH = 160;
    private static final int MAX_PERMISSION_LENGTH = 64;
    private static final int MAX_RESPONSE_BYTES = 1048576;

    public interface Transport {
        Map<String, Object> send(String method, String url, String bearerToken, Map<String, Object> body) throws Exception;
    }

    public static final class HttpStatusException extends IOException {
        public final int status;
        public HttpStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    public static final class Config {
        public final boolean enabled;
        public final String baseUrl;
        public final String token;
        public final String connectorId;
        public final String projectId;
        public final String runtimeId;
        public final String model;
        public final String permission;

        public Config(boolean enabled, String baseUrl, String token, String connectorId,
                      String projectId, String runtimeId, String model, String permission) {
            this.enabled = enabled;
            this.baseUrl = normalizeBaseUrl(baseUrl);
            this.token = token == null ? "" : token.trim();
            this.connectorId = cleanToken(connectorId, MAX_ID_LENGTH);
            this.projectId = cleanToken(projectId, MAX_ID_LENGTH);
            this.runtimeId = cleanToken(runtimeId, MAX_ID_LENGTH);
            this.model = cleanValue(model, MAX_MODEL_LENGTH);
            this.permission = cleanToken(permission, MAX_PERMISSION_LENGTH);
        }

        public boolean ready() {
            return enabled && validBinding(baseUrl, connectorId, projectId, runtimeId, model, permission)
                && !token.isEmpty() && !containsControl(token);
        }

        public String targetFingerprint() {
            if (baseUrl == null || connectorId.isEmpty() || projectId.isEmpty() || runtimeId.isEmpty()) return "";
            try {
                byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
                return baseUrl + "|" + connectorId + "|" + projectId + "|" + runtimeId + "|" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            } catch(Exception e) { throw new IllegalStateException("fingerprint_failed"); }
        }
    }

    public interface StateStore {
        String sessionId();
        boolean unknownDelivery();
        String clientMessageId();
        String targetFingerprint();
        void savePending(String targetFingerprint, String clientMessageId);
        void clearPending();
        void saveSession(String targetFingerprint, String sessionId, String clientMessageId);
        void clearSession();
    }

    public static final class MapStateStore implements StateStore {
        private String sessionId = "";
        private boolean unknownDelivery;
        private String clientMessageId = "";
        private String targetFingerprint = "";

        @Override public String sessionId() { return sessionId; }
        @Override public boolean unknownDelivery() { return unknownDelivery; }
        @Override public String clientMessageId() { return clientMessageId; }
        @Override public String targetFingerprint() { return targetFingerprint; }

        @Override
        public void savePending(String fingerprint, String messageId) {
            sessionId = "";
            unknownDelivery = true;
            clientMessageId = messageId == null ? "" : messageId;
            targetFingerprint = fingerprint == null ? "" : fingerprint;
        }

        @Override
        public void clearPending() {
            unknownDelivery = false;
            clientMessageId = "";
        }

        @Override
        public void saveSession(String fingerprint, String id, String messageId) {
            sessionId = id == null ? "" : id;
            unknownDelivery = false;
            clientMessageId = messageId == null ? "" : messageId;
            targetFingerprint = fingerprint == null ? "" : fingerprint;
        }

        @Override
        public void clearSession() {
            sessionId = "";
            unknownDelivery = false;
            clientMessageId = "";
            targetFingerprint = "";
        }
    }

    private final Config config;
    private final StateStore store;
    private final Transport transport;

    public DshClient(Config config, StateStore store, Transport transport) {
        this.config = config;
        this.store = store;
        this.transport = transport;
    }

    public static String normalizeBaseUrl(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || containsControl(trimmed)) return null;
        try {
            URI uri = new URI(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getHost();
            if (host == null || host.isEmpty() || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            String path = uri.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return null;
            int port = uri.getPort();
            return "https://" + host.toLowerCase(Locale.ROOT) + (port > 0 ? ":" + port : "");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean validBaseUrl(String raw) {
        return normalizeBaseUrl(raw) != null;
    }

    public static boolean validBinding(String baseUrl, String connectorId, String projectId,
                                       String runtimeId, String model, String permission) {
        return normalizeBaseUrl(baseUrl) != null
            && !cleanToken(connectorId, MAX_ID_LENGTH).isEmpty()
            && !cleanToken(projectId, MAX_ID_LENGTH).isEmpty()
            && !cleanToken(runtimeId, MAX_ID_LENGTH).isEmpty()
            && validSelection(model, true) && validSelection(permission, false);
    }

    public static boolean validSelection(String value, boolean model) {
        String prefix=model?"dsh:model:":"dsh:permission:";
        if(value==null||!value.startsWith(prefix))return false;
        String encoded=value.substring(prefix.length());
        if(!encoded.matches("[A-Za-z0-9_-]+"))return false;
        try {
            byte[] bytes=Base64.getUrlDecoder().decode(encoded);
            if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded))return false;
            String decoded=new String(bytes,StandardCharsets.UTF_8);
            if(!Arrays.equals(bytes,decoded.getBytes(StandardCharsets.UTF_8)))return false;
            if(!model)return Arrays.asList("read-only","workspace-write","danger-full-access","auto").contains(decoded);
            JsonReader reader=new JsonReader(decoded);Object valueObj=reader.readValue(0);reader.skipSpace();
            if(!reader.eof()||!(valueObj instanceof List))return false;
            List<?> row=(List<?>)valueObj;
            return row.size()==3 && row.get(0) instanceof String && !((String)row.get(0)).isEmpty()
                && row.get(1) instanceof String && !((String)row.get(1)).isEmpty()
                && (row.get(2)==null || row.get(2) instanceof String);
        }catch(Exception e){return false;}
    }

    public static boolean sameTarget(String oldUrl, String oldConnector, String oldProject, String oldRuntime,
                                     String newUrl, String newConnector, String newProject, String newRuntime) {
        String normalizedOld = normalizeBaseUrl(oldUrl);
        String normalizedNew = normalizeBaseUrl(newUrl);
        return Objects.equals(normalizedOld != null ? normalizedOld : trimOrEmpty(oldUrl),
            normalizedNew != null ? normalizedNew : trimOrEmpty(newUrl))
            && Objects.equals(trimOrEmpty(oldConnector), trimOrEmpty(newConnector))
            && Objects.equals(trimOrEmpty(oldProject), trimOrEmpty(newProject))
            && Objects.equals(trimOrEmpty(oldRuntime), trimOrEmpty(newRuntime));
    }

    public static String validateTask(Map<String, Object> arguments, String... secrets) {
        if (arguments == null || arguments.size() != 1 || !arguments.containsKey("task"))
            throw new IllegalArgumentException("invalid_arguments");
        Object rawObj = arguments.get("task");
        if (!(rawObj instanceof String)) throw new IllegalArgumentException("invalid_task_type");
        String task = ((String) rawObj).trim();
        if (task.length() < 2 || task.length() > MAX_TASK_LENGTH || containsControl(task))
            throw new IllegalArgumentException("invalid_task_length");
        String lower = task.toLowerCase(Locale.ROOT);
        if (task.contains("sk-") || lower.contains("bearer "))
            throw new IllegalArgumentException("secret_pattern");
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && !secret.trim().isEmpty() && task.contains(secret.trim()))
                    throw new IllegalArgumentException("secret_leak");
            }
        }
        return task;
    }

    public static String validateSessionId(String raw) {
        String cleaned = cleanToken(raw, MAX_ID_LENGTH);
        if (cleaned.isEmpty() || cleaned.contains("/") || cleaned.contains("..") || cleaned.contains("?") || cleaned.contains("#"))
            throw new IllegalArgumentException("invalid_session_id");
        return cleaned;
    }

    public static Map<String, Object> buildCreateAndStartBody(Config config, String task, String clientMessageId) {
        if (config == null || !config.ready()) throw new IllegalArgumentException("dsh_not_ready");
        String msgId = cleanToken(clientMessageId, MAX_ID_LENGTH);
        if (!msgId.startsWith("msg_") || msgId.length() <= 4) throw new IllegalArgumentException("invalid_client_message_id");
        Map<String, Object> selections = new LinkedHashMap<>();
        selections.put("model", config.model);
        selections.put("permission", config.permission);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connectorId", config.connectorId);
        body.put("projectId", config.projectId);
        body.put("runtime", "dsh");
        body.put("runtimeId", config.runtimeId);
        body.put("content", task);
        body.put("selections", selections);
        body.put("clientMessageId", msgId);
        return body;
    }

    public Map<String, Object> dispatch(Map<String, Object> arguments, String... extraSecrets) throws Exception {
        if (config == null || !config.ready()) throw new IllegalArgumentException("dsh_disabled");
        String[] allSecrets = appendSecret(extraSecrets, config.token);
        String task = validateTask(arguments, allSecrets);
        String clientMessageId = "msg_" + UUID.randomUUID().toString();
        String fingerprint = config.targetFingerprint();
        Map<String, Object> body = buildCreateAndStartBody(config, task, clientMessageId);
        synchronized (LOCK) {
        if (store.unknownDelivery()) {
            return mapOf(
                "status", "unknown_delivery",
                "message", "上一次 DSH 派发结果尚未确认，为避免重复创建会话，请先调用 dsh_status 查询状态。"
            );
        }

        store.savePending(fingerprint, clientMessageId);
        if(!store.unknownDelivery() || !clientMessageId.equals(store.clientMessageId()) || !fingerprint.equals(store.targetFingerprint())) throw new IllegalStateException("pending_not_saved");
        }
        try {
            Map<String, Object> response = transport.send("POST", config.baseUrl + "/api/v2/sessions/create-and-start", config.token, body);
            String sessionId = validateSessionId(extractSessionId(response));
            synchronized(LOCK) {
                if(!clientMessageId.equals(store.clientMessageId()) || !fingerprint.equals(store.targetFingerprint())) return mapOf("status","unknown_delivery","message","配置或请求记录变化，保留旧提交未知状态。");
                store.saveSession(fingerprint, sessionId, clientMessageId);
            }
            return mapOf(
                "status", "submitted",
                "sessionId", sessionId,
                "clientMessageId", clientMessageId,
                "untrusted_external_data", Boolean.TRUE
            );
        } catch (HttpStatusException httpError) {
            if (httpError.status == 401 || httpError.status == 403 || httpError.status == 422) {
                synchronized(LOCK) {
                    if(clientMessageId.equals(store.clientMessageId()) && fingerprint.equals(store.targetFingerprint())) store.clearPending();
                }
                return mapOf(
                    "status", "failed",
                    "httpStatus", Integer.valueOf(httpError.status),
                    "message", "DSH 派发被服务端拒绝（HTTP " + httpError.status + "），未创建任务，请检查目标绑定或权限。"
                );
            }
            return mapOf(
                "status", "unknown_delivery",
                "clientMessageId", clientMessageId,
                "message", "DSH 派发响应异常（HTTP " + httpError.status + "），远端可能已创建会话，请勿盲目重发，先用 dsh_status 核查。"
            );
        } catch (Exception networkOrParseError) {
            return mapOf(
                "status", "unknown_delivery",
                "clientMessageId", clientMessageId,
                "message", "DSH 派发网络超时或响应中断，远端可能已接收任务，禁止自动重发，请用 dsh_status 查询。"
            );
        }
    }

    public Map<String, Object> status(Map<String, Object> arguments) throws Exception {
        if (config == null || !config.ready()) throw new IllegalArgumentException("dsh_disabled");
        if (arguments != null && !arguments.isEmpty()) throw new IllegalArgumentException("invalid_status_arguments");
        final String currentTarget = config.targetFingerprint();
        final String storedTarget, storedSession, storedMessage;
        synchronized(LOCK) { storedTarget=store.targetFingerprint(); storedSession=store.sessionId(); storedMessage=store.clientMessageId(); }
        if (!storedTarget.isEmpty() && !storedTarget.equals(currentTarget)) {

            return mapOf(
                "status", "failed",
                "message", "DSH 目标配置已变更，旧会话不再跨目标查询。"
            );
        }
        String sessionId = storedSession;
        if (sessionId.isEmpty()) {
            if (!store.unknownDelivery()) {
                return mapOf(
                    "status", "failed",
                    "message", "当前没有已记录的 DSH 会话任务。"
                );
            }
            return mapOf("status", "unknown_delivery", "message", "提交结果未知；请在 Agents-Anywhere 核对，不自动恢复或重新派发。");
        } else {
            sessionId = validateSessionId(sessionId);
        }
        Map<String, Object> statePayload = transport.send("GET", config.baseUrl + "/api/v2/sessions/" + sessionId + "/runtime/state", config.token, null);
        Map<String, Object> timelinePayload = transport.send("GET", config.baseUrl + "/api/v2/sessions/" + sessionId + "/timeline?limit=50", config.token, null);
        Map<String,Object> actual=optMap(unwrapDataObject(statePayload),"state");
        if(actual==null || (actual.containsKey("runtimeId") && !config.runtimeId.equals(optString(actual,"runtimeId"))) || (actual.containsKey("runtime") && !"dsh".equals(optString(actual,"runtime")))) return mapOf("status","unknown","message","运行时绑定不匹配。");
        Map<String,Object> result=evaluateSessionStatus(sessionId, storedMessage, statePayload, timelinePayload);
        synchronized(LOCK) {
            if(!storedTarget.equals(store.targetFingerprint()) || !storedSession.equals(store.sessionId()) || !storedMessage.equals(store.clientMessageId())) return mapOf("status","unknown","message","查询期间任务归属变化，结果不能归因。");
        }
        return result;
    }

    /** Read-only snapshot of the local unconfirmed-dispatch state; never performs network I/O. */
    public static Map<String, Object> describePending(StateStore store) {
        final boolean unknownDelivery;
        final String sessionId, clientMessageId, targetFingerprint;
        synchronized (LOCK) {
            unknownDelivery = store.unknownDelivery();
            sessionId = store.sessionId() == null ? "" : store.sessionId();
            clientMessageId = store.clientMessageId() == null ? "" : store.clientMessageId();
            targetFingerprint = store.targetFingerprint() == null ? "" : store.targetFingerprint();
        }
        boolean hasSession = !sessionId.isEmpty();
        String message;
        if (unknownDelivery) {
            message = "上一次 DSH 派发结果尚未确认，远端可能已创建会话；请先在 Agents-Anywhere 核对，再决定是否解除跟踪。";
        } else if (hasSession) {
            message = "已跟踪上一次 DSH 会话，尚未确认完成；可查询状态或解除本地跟踪。";
        } else {
            message = "当前没有待确认的 DSH 提交。";
        }
        return mapOf(
            "unknownDelivery", Boolean.valueOf(unknownDelivery),
            "sessionId", sessionId,
            "clientMessageId", clientMessageId,
            "targetFingerprint", targetFingerprint,
            "hasSession", Boolean.valueOf(hasSession),
            "message", message
        );
    }

    /** Releases the local unconfirmed lock under LOCK; never implies the remote session did not run. */
    public static Map<String, Object> releasePending(StateStore store, boolean keepAudit) {
        synchronized (LOCK) {
            store.clearPending();
            store.clearSession();
            if (store.unknownDelivery() || !store.sessionId().isEmpty()
                || !store.clientMessageId().isEmpty() || !store.targetFingerprint().isEmpty()) {
                throw new IllegalStateException("pending_not_cleared");
            }
        }
        return mapOf(
            "status", "released",
            "message", "已解除本地未确认锁定并停止跟踪会话；远端会话（如已创建）不受影响，请自行在 Agents-Anywhere 核对。",
            "auditKept", Boolean.valueOf(keepAudit)
        );
    }

    public static String extractSessionId(Map<String, Object> response) {
        if (response == null) return "";
        Map<String, Object> data = unwrapDataObject(response);
        String direct = firstNonEmpty(
            optString(data, "sessionId"),
            optString(data, "id"),
            optString(response, "sessionId"),
            optString(response, "id")
        );
        if (!direct.isEmpty()) return direct;
        Map<String, Object> session = optMap(data, "session");
        if (session == null) session = optMap(response, "session");
        if (session != null) {
            return firstNonEmpty(optString(session, "id"), optString(session, "sessionId"));
        }
        return "";
    }

    public static String recoverSessionId(Map<String, Object> payload, Config config, String clientMessageId) {
        return "";
    }

    public static Map<String, Object> evaluateSessionStatus(String sessionId, String clientMessageId, Map<String, Object> runtimeStatePayload, Map<String, Object> timelinePayload) {
        Map<String,Object> actualState = runtimeStatePayload == null ? null : optMap(unwrapDataObject(runtimeStatePayload),"state");
        if(actualState == null || !sessionId.equals(optString(actualState,"sessionId"))) return mapOf("status","unknown","message","运行状态会话绑定不匹配。");
        String runtimeState = extractRuntimeState(runtimeStatePayload);
        Map<String,Object> data = timelinePayload == null ? Collections.emptyMap() : unwrapDataObject(timelinePayload);
        if(!sessionId.equals(optString(data,"sessionId"))) return mapOf("status","unknown","message","时间线会话绑定不匹配。");
        if (Boolean.TRUE.equals(data.get("hasMore")) || (timelinePayload != null && Boolean.TRUE.equals(timelinePayload.get("hasMore"))))
            return mapOf("status", "unknown", "message", "时间线分页不完整，不能确认完成。");
        List<?> items = extractArray(data, "items", "timeline", "events", "messages", "data");
        List<Object> scoped = new ArrayList<>();
        boolean found = false;
        if (items != null) for (Object raw : items) {
            Map<String,Object> item = asMap(raw);
            if (item == null) continue;
            Map<String,Object> node = optMap(item,"item"); if(node == null) node=item;
            if ("user".equals(optString(node,"role")) && "message".equals(optString(node,"type"))) {
                if(found) return mapOf("status","unknown","message","出现后续用户消息，不能归因到本次任务。");
                Map<String,Object> source = optMap(node,"source");
                found = clientMessageId != null && !clientMessageId.isEmpty() && clientMessageId.equals(optString(source,"clientMessageId"));
            } else if(found) scoped.add(item);
        }
        if(!found) return mapOf("status","unknown","message","未找到本次 clientMessageId 对应用户消息。");
        Set<String> turns=new HashSet<>();
        for(Object raw:scoped) {
            Map<String,Object> row=asMap(raw);Map<String,Object> node=optMap(row,"item");if(node==null)node=row;
            if("assistant".equals(optString(node,"role")) && "message".equals(optString(node,"type")) && !optString(node,"turnId").isEmpty()) turns.add(optString(node,"turnId"));
        }
        for(Object raw:scoped) {
            Map<String,Object> row=asMap(raw);Map<String,Object> node=optMap(row,"item");if(node==null)node=row;
            if("turn.end".equals(optString(node,"type")) && turns.contains(optString(node,"turnId")) && Arrays.asList("failed","interrupted","cancelled","canceled","error").contains(optString(node,"status")))
                return mapOf("status","failed","message","本轮存在明确失败或中断终态，不能按空闲或部分回复报完成。","sessionId",sessionId);
        }
        AssistantMessage latestAssistant = extractLatestAssistantMessage(mapOf("items",scoped));

        if ("waiting_approval".equals(runtimeState)) {
            Map<String, Object> out = mapOf(
                "status", "waiting_approval",
                "sessionId", sessionId,
                "runtimeState", runtimeState,
                "message", "DSH 会话正在等待权限审批，请到电脑端 DSH 或 Agents-Anywhere 处理审批。",
                "untrusted_external_data", Boolean.TRUE
            );
            if (latestAssistant != null && !latestAssistant.text.isEmpty()) {
                out.put("answer", truncate(latestAssistant.text, MAX_ANSWER_LENGTH));
            }
            return out;
        }

        if ("error".equals(runtimeState) || "blocked".equals(runtimeState)) {
            Map<String, Object> out = mapOf(
                "status", "failed",
                "sessionId", sessionId,
                "runtimeState", runtimeState,
                "message", "DSH 会话执行失败或受阻（" + runtimeState + "），请检查电脑端 DSH。",
                "untrusted_external_data", Boolean.TRUE
            );
            if (latestAssistant != null && !latestAssistant.text.isEmpty()) {
                out.put("answer", truncate(latestAssistant.text, MAX_ANSWER_LENGTH));
            }
            return out;
        }

        boolean runtimeActive = "running".equals(runtimeState) || "starting".equals(runtimeState) || "pending".equals(runtimeState);
        if (latestAssistant != null) {
            if ("done".equals(latestAssistant.status) && "idle".equals(runtimeState)) {
                return mapOf(
                    "status", "completed",
                    "completionEvidence", "assistant_done_and_idle",
                    "sessionId", sessionId,
                    "runtimeState", runtimeState,
                    "answer", truncate(latestAssistant.text, MAX_ANSWER_LENGTH),
                    "untrusted_external_data", Boolean.TRUE
                );
            }
            if ("running".equals(latestAssistant.status) || runtimeActive) {
                return mapOf(
                    "status", "running",
                    "sessionId", sessionId,
                    "runtimeState", runtimeState,
                    "answer", truncate(latestAssistant.text, MAX_ANSWER_LENGTH),
                    "message", "DSH 正在执行中，尚未完成。",
                    "untrusted_external_data", Boolean.TRUE
                );
            }
            if ("error".equals(latestAssistant.status) || "failed".equals(latestAssistant.status)) {
                return mapOf(
                    "status", "failed",
                    "sessionId", sessionId,
                    "runtimeState", runtimeState,
                    "answer", truncate(latestAssistant.text, MAX_ANSWER_LENGTH),
                    "message", "DSH 回复状态异常（" + latestAssistant.status + "）。",
                    "untrusted_external_data", Boolean.TRUE
                );
            }
        }

        if ("idle".equals(runtimeState)) {
            return mapOf(
                "status", "idle_no_reply",
                "sessionId", sessionId,
                "runtimeState", runtimeState,
                "message", "会话已空闲但未产出回复，可能本机模型配额不足或执行中断，请检查电脑端 DSH",
                "untrusted_external_data", Boolean.TRUE
            );
        }

        if (runtimeActive) {
            return mapOf(
                "status", "running",
                "sessionId", sessionId,
                "runtimeState", runtimeState,
                "message", "DSH 任务正在启动或运行中，尚无助手文本输出。",
                "untrusted_external_data", Boolean.TRUE
            );
        }

        return mapOf(
            "status", "failed",
            "sessionId", sessionId,
            "runtimeState", runtimeState.isEmpty() ? "unknown" : runtimeState,
            "message", "无法确认 DSH 会话完成状态，请检查电脑端 DSH。",
            "untrusted_external_data", Boolean.TRUE
        );
    }

    public static String extractRuntimeState(Map<String, Object> payload) {
        if (payload == null) return "";
        Map<String, Object> data = unwrapDataObject(payload);
        Map<String,Object> state=optMap(data,"state");
        if(state != null) return optString(state,"status").trim().toLowerCase(Locale.ROOT);
        Map<String, Object> runtime = optMap(data, "runtime");
        if (runtime == null) runtime = optMap(payload, "runtime");
        String raw = firstNonEmpty(
            optString(data, "state"),
            optString(data, "status"),
            optString(data, "runtimeState"),
            runtime != null ? optString(runtime, "state") : "",
            runtime != null ? optString(runtime, "status") : "",
            optString(payload, "state"),
            optString(payload, "status")
        );
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AssistantMessage {
        final String status;
        final String text;
        AssistantMessage(String status, String text) {
            this.status = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
            this.text = text == null ? "" : text;
        }
    }

    private static AssistantMessage extractLatestAssistantMessage(Map<String, Object> timelinePayload) {
        if (timelinePayload == null) return null;
        List<?> items = extractArray(timelinePayload, "items", "timeline", "events", "messages", "data");
        if (items == null) return null;
        AssistantMessage latest = null;
        for (Object rawItem : items) {
            Map<String, Object> item = asMap(rawItem);
            if (item == null) continue;
            Map<String, Object> node = optMap(item, "item");
            if (node == null) node = item;
            String role = firstNonEmpty(optString(node, "role"), optString(item, "role")).trim().toLowerCase(Locale.ROOT);
            String type = firstNonEmpty(optString(node, "type"), optString(item, "type")).trim().toLowerCase(Locale.ROOT);
            if ("assistant".equals(role) && "message".equals(type)) {
                String status = firstNonEmpty(optString(node, "status"), optString(item, "status"));
                String text = extractMessageText(node);
                if (text.isEmpty() && node != item) text = extractMessageText(item);
                latest = new AssistantMessage(status, text);
            }
        }
        return latest;
    }

    private static String extractMessageText(Map<String, Object> message) {
        if (message == null) return "";
        Object content = message.get("content");
        if (content instanceof String) return ((String) content).trim();
        if (content instanceof List) {
            List<?> parts = (List<?>) content;
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof String) {
                    sb.append((String) part);
                } else {
                    Map<String, Object> obj = asMap(part);
                    if (obj != null) {
                        sb.append(firstNonEmpty(optString(obj, "text"), optString(obj, "content")));
                    }
                }
            }
            if (sb.length() > 0) return sb.toString().trim();
        }
        Map<String, Object> contentObj = asMap(content);
        if (contentObj != null) {
            String text = firstNonEmpty(optString(contentObj, "text"), optString(contentObj, "content"));
            if (!text.isEmpty()) return text.trim();
        }
        return firstNonEmpty(optString(message, "text"), optString(message, "answer")).trim();
    }

    public static Map<String, Object> httpTransport(String method, String url, String bearerToken, Map<String, Object> body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] payload = stringifyJson(body).getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }
            int code = connection.getResponseCode();
            if (code != 200 && code != 201 && code != 202) {
                throw new HttpStatusException(code);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] block = new byte[8192];
                int n;
                while ((n = in.read(block)) != -1) {
                    if (out.size() + n > MAX_RESPONSE_BYTES) throw new IOException("limit");
                    out.write(block, 0, n);
                }
            }
            return parseJsonObject(out.toString("UTF-8"));
        } finally {
            connection.disconnect();
        }
    }

    public static String stringifyJson(Object value) {
        StringBuilder sb = new StringBuilder();
        writeJson(value, sb);
        return sb.toString();
    }

    private static void writeJson(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            quoteJson((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) continue;
                if (!first) sb.append(',');
                first = false;
                quoteJson(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeJson(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(',');
                first = false;
                writeJson(item, sb);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON type: " + value.getClass());
        }
    }

    private static void quoteJson(String text, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseJsonObject(String json) {
        if (json == null) throw new IllegalArgumentException("null json");
        JsonReader reader = new JsonReader(json);
        Object parsed = reader.readValue(0);
        reader.skipSpace();
        if (!reader.eof()) throw new IllegalArgumentException("trailing json");
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("expected json object");
        return (Map<String, Object>) parsed;
    }

    private static final class JsonReader {
        private final String s;
        private int at;
        private int nodes;
        JsonReader(String s) { this.s = s; }
        boolean eof() { return at >= s.length(); }
        void skipSpace() {
            while (at < s.length()) {
                char c = s.charAt(at);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') at++;
                else break;
            }
        }
        char next() {
            if (at >= s.length()) throw new IllegalArgumentException("unexpected eof");
            return s.charAt(at++);
        }
        Object readValue(int depth) {
            if (depth > 32 || ++nodes > 32768) throw new IllegalArgumentException("json complexity");
            skipSpace();
            char c = next();
            if (c == '"') return readString();
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                skipSpace();
                if (at < s.length() && s.charAt(at) == '}') { at++; return map; }
                while (true) {
                    skipSpace();
                    if (next() != '"') throw new IllegalArgumentException("expected key");
                    String key = readString();
                    skipSpace();
                    if (next() != ':') throw new IllegalArgumentException("expected colon");
                    map.put(key, readValue(depth + 1));
                    skipSpace();
                    char sep = next();
                    if (sep == '}') return map;
                    if (sep != ',') throw new IllegalArgumentException("expected comma");
                }
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                skipSpace();
                if (at < s.length() && s.charAt(at) == ']') { at++; return list; }
                while (true) {
                    list.add(readValue(depth + 1));
                    skipSpace();
                    char sep = next();
                    if (sep == ']') return list;
                    if (sep != ',') throw new IllegalArgumentException("expected comma");
                }
            }
            if (c == 't' && match("rue")) return Boolean.TRUE;
            if (c == 'f' && match("alse")) return Boolean.FALSE;
            if (c == 'n' && match("ull")) return null;
            if (c == '-' || (c >= '0' && c <= '9')) {
                int start = at - 1;
                while (at < s.length() && "0123456789.eE+-".indexOf(s.charAt(at)) >= 0) at++;
                String num = s.substring(start, at);
                try {
                    if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
                        return Double.valueOf(num);
                    }
                    long lv = Long.parseLong(num);
                    if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) return Integer.valueOf((int) lv);
                    return Long.valueOf(lv);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid number");
                }
            }
            throw new IllegalArgumentException("invalid json token");
        }
        private boolean match(String tail) {
            if (!s.startsWith(tail, at)) return false;
            at += tail.length();
            return true;
        }
        private String readString() {
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"': case '\\': case '/': out.append(esc); break;
                        case 'b': out.append('\b'); break;
                        case 'f': out.append('\f'); break;
                        case 'n': out.append('\n'); break;
                        case 'r': out.append('\r'); break;
                        case 't': out.append('\t'); break;
                        case 'u':
                            int code = 0;
                            for (int i = 0; i < 4; i++) {
                                int d = Character.digit(next(), 16);
                                if (d < 0) throw new IllegalArgumentException("invalid unicode escape");
                                code = (code << 4) | d;
                            }
                            out.append((char) code);
                            break;
                        default:
                            throw new IllegalArgumentException("invalid escape");
                    }
                } else {
                    out.append(c);
                }
            }
        }
    }

    public static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        return obj instanceof Map ? (Map<String, Object>) obj : null;
    }

    private static Map<String, Object> optMap(Map<String, Object> map, String key) {
        return map == null ? null : asMap(map.get(key));
    }

    private static String optString(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object val = map.get(key);
        return val instanceof String ? (String) val : "";
    }

    private static Map<String, Object> unwrapDataObject(Map<String, Object> root) {
        Map<String, Object> data = optMap(root, "data");
        return data != null ? data : root;
    }

    private static List<?> extractArray(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object val = root.get(key);
            if (val instanceof List) return (List<?>) val;
        }
        Map<String, Object> data = optMap(root, "data");
        if (data != null) {
            for (String key : keys) {
                Object val = data.get(key);
                if (val instanceof List) return (List<?>) val;
            }
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String[] appendSecret(String[] existing, String extra) {
        if (existing == null || existing.length == 0) return new String[]{extra};
        String[] result = Arrays.copyOf(existing, existing.length + 1);
        result[existing.length] = extra;
        return result;
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanToken(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength || containsControl(trimmed)) return "";
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch)) return "";
        }
        return trimmed;
    }

    private static String cleanValue(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength || containsControl(trimmed)) return "";
        return trimmed;
    }

    private static boolean containsControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f) return true;
        }
        return false;
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), maxLength));
    }
}
