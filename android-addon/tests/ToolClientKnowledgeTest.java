package com.turboio.addon;

import android.content.*;
import com.sun.net.httpserver.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class ToolClientKnowledgeTest {
    static class FakeContext extends ContextWrapper {
        final Map<String, SharedPreferences> prefs = new HashMap<>();
        FakeContext() { super(null); }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return prefs.computeIfAbsent(name, k -> new FakePrefs());
        }
    }

    static class FakePrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();
        @Override public String getString(String k, String d) { return map.containsKey(k) ? (String) map.get(k) : d; }
        @Override public boolean getBoolean(String k, boolean d) { return map.containsKey(k) ? (Boolean) map.get(k) : d; }
        @Override public long getLong(String k, long d) { return map.containsKey(k) ? (Long) map.get(k) : d; }
        @Override public int getInt(String k, int d) { return map.containsKey(k) ? (Integer) map.get(k) : d; }
        @Override public float getFloat(String k, float d) { return map.containsKey(k) ? (Float) map.get(k) : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return map.containsKey(k) ? (Set<String>) map.get(k) : d; }
        @Override public Map<String, ?> getAll() { return map; }
        @Override public boolean contains(String k) { return map.containsKey(k); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> changes = new HashMap<>();
                boolean clearAll;
                @Override public Editor putString(String k, String v) { changes.put(k, v); return this; }
                @Override public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
                @Override public Editor putLong(String k, long v) { changes.put(k, v); return this; }
                @Override public Editor putInt(String k, int v) { changes.put(k, v); return this; }
                @Override public Editor putFloat(String k, float v) { changes.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { changes.put(k, v); return this; }
                @Override public Editor remove(String k) { changes.put(k, null); return this; }
                @Override public Editor clear() { clearAll = true; changes.clear(); return this; }
                @Override public void apply() { commit(); }
                @Override public boolean commit() {
                    if (clearAll) map.clear();
                    for (String k : changes.keySet()) {
                        if (changes.get(k) == null) map.remove(k);
                        else map.put(k, changes.get(k));
                    }
                    return true;
                }
            };
        }
    }

    static class MockMcpServer implements AutoCloseable {
        final HttpServer server;
        final ExecutorService pool = Executors.newCachedThreadPool();
        final String mode;
        final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        JSONObject lastToolArgs;

        MockMcpServer(String mode) throws Exception {
            this.mode = mode;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.setExecutor(pool);
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }

        void handle(HttpExchange e) throws IOException {
            try {
                byte[] reqBytes;
                try (InputStream in = e.getRequestBody()) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int n;
                    while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                    reqBytes = bos.toByteArray();
                }
                JSONObject req;
                try {
                    req = new JSONObject(new String(reqBytes, StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    e.sendResponseHeaders(400, -1);
                    e.close();
                    return;
                }
            String method = req.optString("method", "");
            methods.add(method);

            if ("initialize".equals(method)) {
                JSONObject res = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", req.getLong("id"))
                    .put("result", new JSONObject()
                        .put("protocolVersion", "2025-03-26")
                        .put("capabilities", new JSONObject())
                        .put("serverInfo", new JSONObject().put("name", "mock-cortrix").put("version", "1.0.0")));
                byte[] b = res.toString().getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, b.length);
                e.getResponseBody().write(b);
                e.close();
                return;
            }

            if ("notifications/initialized".equals(method)) {
                e.sendResponseHeaders(204, -1);
                e.close();
                return;
            }

            if ("tools/list".equals(method)) {
                JSONObject tool = new JSONObject()
                    .put("name", "cortrix_query")
                    .put("description", "Mock search")
                    .put("inputSchema", new JSONObject().put("type", "object"));
                JSONObject res = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", req.getLong("id"))
                    .put("result", new JSONObject().put("tools", new JSONArray().put(tool)));
                byte[] b = res.toString().getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, b.length);
                e.getResponseBody().write(b);
                e.close();
                return;
            }

            if ("tools/call".equals(method)) {
                JSONObject params = req.optJSONObject("params");
                if (params != null) lastToolArgs = params.optJSONObject("arguments");

                if ("401".equals(mode)) {
                    e.sendResponseHeaders(401, -1);
                    e.close();
                    return;
                }
                if ("timeout".equals(mode)) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    e.sendResponseHeaders(200, -1);
                    e.close();
                    return;
                }
                if ("500".equals(mode)) {
                    e.sendResponseHeaders(500, -1);
                    e.close();
                    return;
                }

                JSONObject resultObj = new JSONObject();
                if ("hit".equals(mode)) {
                    JSONArray results = new JSONArray();
                    JSONObject doc = new JSONObject()
                        .put("namespace", "obsidian-knowledge")
                        .put("doc_id", "doc-101")
                        .put("source_path", "projects/turboio.md")
                        .put("score", 0.95)
                        .put("metadata", new JSONObject().put("updated", "2026-11-06T10:00:00Z"))
                        .put("evidence", new JSONArray().put(new JSONObject().put("kind", "chunk").put("text", "TurboIO Cortrix MCP spec")));
                    results.put(doc);

                    JSONObject structuredData = new JSONObject()
                        .put("coverage_ratio", 0.9)
                        .put("namespaces_failed", new JSONArray().put("legacy_ns"));
                    JSONObject envelope = new JSONObject()
                        .put("data", new JSONObject().put("results", results))
                        .put("meta", new JSONObject().put("structured_data", structuredData));
                    resultObj.put("structuredContent", envelope);
                } else if ("empty".equals(mode)) {
                    JSONObject envelope = new JSONObject()
                        .put("data", new JSONObject().put("results", new JSONArray()))
                        .put("meta", new JSONObject().put("structured_data", new JSONObject().put("coverage_ratio", 1.0).put("namespaces_failed", new JSONArray())));
                    resultObj.put("structuredContent", envelope);
                } else if ("tool_error".equals(mode)) {
                    resultObj.put("isError", true);
                    resultObj.put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", "tool execution failed")));
                }

                JSONObject res = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", req.getLong("id"))
                    .put("result", resultObj);
                byte[] b = res.toString().getBytes(StandardCharsets.UTF_8);
                e.getResponseHeaders().set("Content-Type", "application/json");
                e.sendResponseHeaders(200, b.length);
                e.getResponseBody().write(b);
                e.close();
                return;
            }

            e.sendResponseHeaders(404, -1);
            e.close();
        } catch (Exception ex) {
            try { e.sendResponseHeaders(500, -1); e.close(); } catch (Exception ignored) {}
        }
    }

        @Override public void close() {
            server.stop(0);
            pool.shutdownNow();
        }
    }

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("CHECK FAILED: " + msg);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running ToolClientKnowledgeTest...");
        testRegistrationGating();
        testInvalidArgumentsHandledDefensively();
        testExecutionHit();
        testExecutionEmpty();
        testExecutionTimeoutAndErrors();
        System.out.println("ToolClientKnowledgeTest: ALL PASS");
    }

    static void testRegistrationGating() throws Exception {
        FakeContext ctx = new FakeContext();
        ToolClient tc = new ToolClient(ctx);
        check(findTool(tc.specs(), "knowledge_query") == null, "knowledge_query should not be registered when MCP disabled");

        SharedPreferences settings = ctx.getSharedPreferences("turboio_settings", 0);
        settings.edit().putBoolean("cortrix_enabled", true).putString("cortrix_endpoint", "http://127.0.0.1:8080/mcp").commit();
        ToolClient tcEnabled = new ToolClient(ctx);
        JSONObject spec = findTool(tcEnabled.specs(), "knowledge_query");
        check(spec != null, "knowledge_query must be registered when MCP is enabled with endpoint");

        JSONObject fn = spec.getJSONObject("function");
        check("knowledge_query".equals(fn.getString("name")), "tool name");
        check(fn.getString("description").contains("知识库"), "tool description");
        JSONObject params = fn.getJSONObject("parameters");
        check("object".equals(params.getString("type")), "param type");
        JSONObject props = params.getJSONObject("properties");
        check(props.has("query"), "has query property");
        check(props.has("namespaces"), "has namespaces property");
        check("string".equals(props.getJSONObject("query").getString("type")), "query is string");
        check("array".equals(props.getJSONObject("namespaces").getString("type")), "namespaces is array");
        check(!props.getJSONObject("namespaces").getString("description").contains("obsidian-knowledge"), "namespaces description must not induce obsidian-knowledge");
        check("no_app".equals(ToolClient.knowledgeReadyState(null)), "knowledgeReadyState null");
        check("ready".equals(ToolClient.knowledgeReadyState(ctx)), "knowledgeReadyState ready");
        JSONArray req = params.getJSONArray("required");
        check(req.length() == 1 && "query".equals(req.getString(0)), "query is only required field");
    }

    static void testInvalidArgumentsHandledDefensively() throws Exception {
        FakeContext ctx = new FakeContext();
        ctx.getSharedPreferences("turboio_settings", 0).edit()
            .putBoolean("cortrix_enabled", true)
            .putString("cortrix_endpoint", "http://127.0.0.1:8080/mcp")
            .commit();
        ToolClient tc = new ToolClient(ctx);

        // 1. Missing query
        JSONObject res1 = tc.call("knowledge_query", new JSONObject(), "secret");
        check("ERROR".equals(res1.getString("status")), "missing query should return ERROR status");
        check(res1.getInt("count") == 0, "count must be 0");
        check(res1.getJSONArray("evidences").length() == 0, "evidences must be empty");
        check(res1.has("message"), "error must have message");

        // 2. Query too short (<2 chars)
        JSONObject res2 = tc.call("knowledge_query", new JSONObject().put("query", "a"), "secret");
        check("ERROR".equals(res2.getString("status")), "short query should return ERROR");
        check(res2.has("message"), "error message on short query");

        // 3. Query containing model secret
        JSONObject res3 = tc.call("knowledge_query", new JSONObject().put("query", "test secret123 keyword"), "secret123");
        check("ERROR".equals(res3.getString("status")), "query with secret should return ERROR");

        // 4. Invalid namespaces type (not array)
        JSONObject res4 = tc.call("knowledge_query", new JSONObject().put("query", "valid query").put("namespaces", "not-an-array"), "secret");
        check("ERROR".equals(res4.getString("status")), "non-array namespaces should return ERROR");
    }

    static void testExecutionHit() throws Exception {
        try (MockMcpServer s = new MockMcpServer("hit")) {
            FakeContext ctx = new FakeContext();
            ctx.getSharedPreferences("turboio_settings", 0).edit()
                .putBoolean("cortrix_enabled", true)
                .putString("cortrix_endpoint", s.url())
                .commit();
            ToolClient tc = new ToolClient(ctx);

            JSONObject args = new JSONObject()
                .put("query", "TurboIO design")
                .put("namespaces", new JSONArray().put("obsidian-knowledge"));
            JSONObject res = tc.call("knowledge_query", args, "model_secret");

            check("HIT".equals(res.getString("status")), "status must be HIT");
            check(res.getInt("count") == 1, "count must be 1");
            check(Math.abs(res.getDouble("coverage_ratio") - 0.9) < 0.001, "coverage_ratio must match 0.9");
            check(res.getJSONArray("namespaces_failed").length() == 1, "namespaces_failed count");
            check("legacy_ns".equals(res.getJSONArray("namespaces_failed").getString(0)), "failed namespace name");
            check(res.getLong("elapsed_ms") >= 0, "elapsed_ms >= 0");
            check(!res.getBoolean("truncated"), "truncated is false");

            JSONArray evs = res.getJSONArray("evidences");
            check(evs.length() == 1, "evidence array length 1");
            JSONObject ev = evs.getJSONObject(0);
            check("projects/turboio.md".equals(ev.getString("source")), "source path extracted");
            check(Math.abs(ev.getDouble("score") - 0.95) < 0.001, "score");
            check(ev.getString("text").contains("TurboIO Cortrix MCP spec"), "text");
            check("2026-11-06T10:00:00Z".equals(ev.getString("source_time")), "source_time extracted");

            check(s.lastToolArgs != null, "server received args");
            check("TurboIO design".equals(s.lastToolArgs.getString("query")), "forwarded query");
            check("obsidian-knowledge".equals(s.lastToolArgs.getJSONArray("namespaces").getString(0)), "forwarded namespaces");
        }
    }

    static void testExecutionEmpty() throws Exception {
        try (MockMcpServer s = new MockMcpServer("empty")) {
            FakeContext ctx = new FakeContext();
            ctx.getSharedPreferences("turboio_settings", 0).edit()
                .putBoolean("cortrix_enabled", true)
                .putString("cortrix_endpoint", s.url())
                .commit();
            ToolClient tc = new ToolClient(ctx);

            JSONObject res = tc.call("knowledge_query", new JSONObject().put("query", "not found question"), "secret");
            check("EMPTY".equals(res.getString("status")), "status must be EMPTY");
            check(res.getInt("count") == 0, "count must be 0");
            check(res.getJSONArray("evidences").length() == 0, "evidences must be empty");
            check(res.has("message"), "empty response should carry message");
        }
    }

    static void testExecutionTimeoutAndErrors() throws Exception {
        // 1. Auth error (401)
        try (MockMcpServer s = new MockMcpServer("401")) {
            FakeContext ctx = new FakeContext();
            ctx.getSharedPreferences("turboio_settings", 0).edit()
                .putBoolean("cortrix_enabled", true)
                .putString("cortrix_endpoint", s.url())
                .commit();
            ToolClient tc = new ToolClient(ctx);

            JSONObject res = tc.call("knowledge_query", new JSONObject().put("query", "some query"), "secret");
            check("AUTH_FAILED".equals(res.getString("status")), "status must be AUTH_FAILED on 401");
            check(res.getInt("count") == 0, "count 0");
            check(res.has("message"), "has error message");
        }

        // 2. Transport error (500)
        try (MockMcpServer s = new MockMcpServer("500")) {
            FakeContext ctx = new FakeContext();
            ctx.getSharedPreferences("turboio_settings", 0).edit()
                .putBoolean("cortrix_enabled", true)
                .putString("cortrix_endpoint", s.url())
                .commit();
            ToolClient tc = new ToolClient(ctx);

            JSONObject res = tc.call("knowledge_query", new JSONObject().put("query", "some query"), "secret");
            check("OFFLINE".equals(res.getString("status")), "status must be OFFLINE on 500");
            check(res.getInt("count") == 0, "count 0");
        }

        // 3. Tool error (isError from MCP)
        try (MockMcpServer s = new MockMcpServer("tool_error")) {
            FakeContext ctx = new FakeContext();
            ctx.getSharedPreferences("turboio_settings", 0).edit()
                .putBoolean("cortrix_enabled", true)
                .putString("cortrix_endpoint", s.url())
                .commit();
            ToolClient tc = new ToolClient(ctx);

            JSONObject res = tc.call("knowledge_query", new JSONObject().put("query", "some query"), "secret");
            check("ERROR".equals(res.getString("status")), "status must be ERROR on tool error");
            check(res.getInt("count") == 0, "count 0");
        }
    }

    private static JSONObject findTool(JSONArray tools, String name) throws Exception {
        if (tools == null) return null;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject t = tools.getJSONObject(i);
            if (t.has("function") && name.equals(t.getJSONObject("function").optString("name"))) {
                return t;
            }
        }
        return null;
    }
}
