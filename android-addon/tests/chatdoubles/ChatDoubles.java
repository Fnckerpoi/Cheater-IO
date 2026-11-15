package com.turboio.addon;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

class ToolClient {
    static int calls;
    static boolean webSearch;
    static boolean dsh = true;
    static final List<String> calledTools = new ArrayList<String>();

    ToolClient(Object a) {}

    JSONArray specs() {
        JSONArray arr = new JSONArray();
        if (webSearch) arr.put(new JSONObject().put("type", "function").put("function", new JSONObject().put("name", "web_search")));
        if (dsh) arr.put(new JSONObject().put("type", "function").put("function", new JSONObject().put("name", "dsh_dispatch")));
        McpConnectionStore.Snapshot mcp = McpConnectionStore.snapshot(null);
        if (mcp != null && mcp.enabled && mcp.endpoint != null && !mcp.endpoint.trim().isEmpty()) {
            JSONObject kqProps = new JSONObject()
                    .put("query", new JSONObject().put("type", "string").put("description", "query string"))
                    .put("namespaces", new JSONObject().put("type", "array").put("items", new JSONObject().put("type", "string")));
            arr.put(new JSONObject().put("type", "function").put("function", new JSONObject().put("name", "knowledge_query")
                    .put("description", "query knowledge base").put("parameters", new JSONObject().put("type", "object").put("properties", kqProps))));
        }
        return arr;
    }

    JSONObject call(String n, JSONObject a, String s) {
        calls++;
        calledTools.add(n);
        if ("knowledge_query".equals(n)) {
            McpConnectionStore.Snapshot config = McpConnectionStore.snapshot(null);
            KnowledgeRetrieval.Result res = KnowledgeRetrieval.retrieve(a.optString("query"), config, 30000L);
            JSONObject obj = new JSONObject();
            String status = res.status != null ? res.status.name() : "ERROR";
            if ("NO_MATCH".equals(status)) status = "EMPTY";
            else if ("AUTH_ERROR".equals(status)) status = "AUTH_FAILED";
            else if ("TRANSPORT_ERROR".equals(status) || "DISABLED".equals(status) || "UNCONFIGURED".equals(status)) status = "OFFLINE";
            else if ("TOOL_ERROR".equals(status) || "PROTOCOL_ERROR".equals(status)) status = "ERROR";
            obj.put("status", status);
            obj.put("query", a.optString("query"));
            JSONArray evArr = new JSONArray();
            if (res.evidences != null) {
                for (KnowledgeRetrieval.Evidence ev : res.evidences) {
                    JSONObject evObj = new JSONObject();
                    evObj.put("namespace", ev.namespace);
                    evObj.put("doc_id", ev.docId);
                    evObj.put("source_path", ev.sourcePath);
                    evObj.put("metadata", ev.metadata);
                    evObj.put("kind", ev.kind);
                    evObj.put("text", ev.text);
                    evObj.put("source_time", ev.sourceTimes != null ? ev.sourceTimes : "unknown");
                    evArr.put(evObj);
                }
            }
            obj.put("evidences", evArr);
            obj.put("count", evArr.length());
            obj.put("coverage_ratio", res.coverageRatio);
            JSONArray failedNs = new JSONArray();
            if (res.namespacesFailed != null) {
                for (String ns : res.namespacesFailed) {
                    if (ns != null && !ns.isEmpty()) failedNs.put(ns);
                }
            }
            obj.put("namespaces_failed", failedNs);
            obj.put("elapsed_ms", res.elapsedMs > 0 ? res.elapsedMs : 12L);
            obj.put("truncated", res.truncated);
            return obj;
        }
        return new JSONObject().put("status", "submitted");
    }
}

class McpConnectionStore {
    static volatile long revision = 1;
    static String endpoint = "", token = "";
    static boolean enabled = true;

    static class Snapshot {
        long revision;
        String endpoint, token;
        boolean enabled;

        Snapshot(long r, String e, String t, boolean x) {
            revision = r;
            endpoint = e;
            token = t;
            enabled = x;
        }
    }

    static Snapshot snapshot(Object a) {
        return new Snapshot(revision, endpoint, token, enabled);
    }
}
