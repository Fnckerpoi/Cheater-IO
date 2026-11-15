package com.turboio.addon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ChatRetrievalOrderTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static String rpcResponse(long id, Object result) {
        JSONObject obj = new JSONObject();
        obj.put("jsonrpc", "2.0");
        obj.put("id", id);
        obj.put("result", result);
        return obj.toString();
    }

    private static JSONObject createSampleHit() {
        JSONObject evidenceItem = new JSONObject();
        evidenceItem.put("kind", "parent");
        evidenceItem.put("text", "REAL_EVIDENCE_SNIPPET");

        JSONObject meta = new JSONObject();
        meta.put("owner", "ops-team");

        JSONObject doc = new JSONObject();
        doc.put("namespace", "knowledge");
        doc.put("doc_id", "doc-101");
        doc.put("source_path", "docs/architecture.md");
        doc.put("metadata", meta);
        doc.put("score", 0.95);
        doc.put("evidence", new JSONArray().put(evidenceItem));
        return doc;
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger modelRequests = new AtomicInteger();
        AtomicInteger mcpCalls = new AtomicInteger();
        AtomicInteger mcpInitialized = new AtomicInteger();

        String[] testMode = new String[]{"knowledge_hit"};
        String[] verificationError = new String[]{null};

        CountDownLatch[] blockLatch = new CountDownLatch[]{null};
        Runnable[] onToolsCall = new Runnable[]{null};

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/mcp", (HttpExchange exchange) -> {
            try {
                byte[] reqBytes = readAll(exchange.getRequestBody());
                JSONObject req = new JSONObject(new String(reqBytes, StandardCharsets.UTF_8));
                String method = req.getString("method");
                long id = req.optLong("id", 0);

                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (!"Bearer test-token".equals(authHeader)) {
                    verificationError[0] = "Missing or invalid Bearer auth: " + authHeader;
                }

                if ("initialize".equals(method)) {
                    mcpInitialized.incrementAndGet();
                    exchange.getResponseHeaders().add("Mcp-Session-Id", "session-xyz");
                    exchange.getResponseHeaders().add("Content-Type", "application/json");

                    JSONObject initRes = new JSONObject();
                    initRes.put("protocolVersion", "2025-03-26");
                    byte[] payload = rpcResponse(id, initRes).getBytes(StandardCharsets.UTF_8);

                    exchange.sendResponseHeaders(200, payload.length);
                    exchange.getResponseBody().write(payload);
                } else if ("notifications/initialized".equals(method)) {
                    exchange.sendResponseHeaders(202, -1);
                } else if ("tools/list".equals(method)) {
                    String sess = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                    if (!"session-xyz".equals(sess)) {
                        verificationError[0] = "tools/list missing Mcp-Session-Id header";
                    }

                    JSONObject tool = new JSONObject();
                    tool.put("name", "cortrix_query");
                    JSONObject result = new JSONObject();
                    result.put("tools", new JSONArray().put(tool));

                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    byte[] payload = rpcResponse(id, result).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, payload.length);
                    exchange.getResponseBody().write(payload);
                } else if ("tools/call".equals(method)) {
                    String sess = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                    if (!"session-xyz".equals(sess)) {
                        verificationError[0] = "tools/call missing Mcp-Session-Id header";
                    }
                    mcpCalls.incrementAndGet();

                    if (onToolsCall[0] != null) {
                        onToolsCall[0].run();
                    }
                    if (blockLatch[0] != null) {
                        blockLatch[0].await(5, TimeUnit.SECONDS);
                    }

                    Object result;
                    if ("knowledge_error".equals(testMode[0])) {
                        JSONObject err = new JSONObject();
                        err.put("isError", true);
                        result = err;
                    } else if ("knowledge_empty".equals(testMode[0])) {
                        JSONObject emptyData = new JSONObject();
                        emptyData.put("results", new JSONArray());
                        JSONObject struct = new JSONObject();
                        struct.put("data", emptyData);
                        JSONObject ok = new JSONObject();
                        ok.put("structuredContent", struct);
                        result = ok;
                    } else {
                        JSONObject hit = createSampleHit();
                        if ("knowledge_long".equals(testMode[0])) {
                            StringBuilder text = new StringBuilder("LONG_EVIDENCE_SENTINEL");
                            while (text.length() < 20000) text.append('x');
                            hit.getJSONArray("evidence").getJSONObject(0).put("text", text.toString());
                        }
                        JSONObject hitData = new JSONObject();
                        hitData.put("results", new JSONArray().put(hit));
                        JSONObject struct = new JSONObject();
                        struct.put("data", hitData);
                        JSONObject ok = new JSONObject();
                        ok.put("structuredContent", struct);
                        result = ok;
                    }

                    String json = rpcResponse(id, result);
                    String sse = "id: " + id + "\ndata: " + json + "\n\n";
                    byte[] payload = sse.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, payload.length);
                    exchange.getResponseBody().write(payload);
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            } catch (Throwable t) {
                verificationError[0] = t.toString();
            } finally {
                exchange.close();
            }
        });

        server.createContext("/chat/completions", (HttpExchange exchange) -> {
            try {
                byte[] bodyBytes = readAll(exchange.getRequestBody());
                JSONObject body = new JSONObject(new String(bodyBytes, StandardCharsets.UTF_8));
                JSONArray messages = body.getJSONArray("messages");
                String systemPrompt = messages.getJSONObject(0).getString("content");

                int currentPost = modelRequests.incrementAndGet();
                if ("history_topic".equals(testMode[0])) {
                    if (systemPrompt.contains("只根据提供的历史回答")) {
                        verificationError[0] = "System prompt still anchors answers to history: " + systemPrompt;
                    } else if (!systemPrompt.contains("当前问题") || !systemPrompt.contains("无关")) {
                        verificationError[0] = "System prompt must prioritise the current question over unrelated history: " + systemPrompt;
                    }
                    String lastContent = messages.getJSONObject(messages.length() - 1).optString("content");
                    boolean currentLast = false;
                    for (int i = messages.length() - 1; i >= 0; i--) {
                        JSONObject m = messages.getJSONObject(i);
                        if ("user".equals(m.optString("role"))) { currentLast = "How does SCADA work?".equals(m.optString("content")); break; }
                    }
                    if (!currentLast) verificationError[0] = "Current question must be the last user message; last content: " + lastContent;
                }

                if ("routing_all".equals(testMode[0])) {
                    if (!systemPrompt.contains("web_search") || !systemPrompt.contains("天气")) verificationError[0] = "Registered web_search must own realtime public information: " + systemPrompt;
                    else if (!systemPrompt.contains("knowledge_query")) verificationError[0] = "Registered knowledge_query guidance missing: " + systemPrompt;
                    else if (!systemPrompt.contains("dsh_dispatch")) verificationError[0] = "Registered DSH guidance missing: " + systemPrompt;
                    else if (systemPrompt.contains("与知识库无关的问题直接回答")) verificationError[0] = "Knowledge guidance must not suppress other registered tools: " + systemPrompt;
                } else if ("routing_knowledge_only".equals(testMode[0])) {
                    if (!systemPrompt.contains("knowledge_query")) verificationError[0] = "Registered knowledge_query guidance missing: " + systemPrompt;
                    else if (systemPrompt.contains("web_search") || systemPrompt.contains("dsh_dispatch")) verificationError[0] = "Prompt must not route to unregistered tools: " + systemPrompt;
                }

                if (currentPost == 1) {
                    JSONObject tailMsg = messages.getJSONObject(messages.length() - 1);
                    if (!"user".equals(tailMsg.optString("role"))) {
                        verificationError[0] = "Model POST #1 must end with role=user (single leading system prompt topology), got role=" + tailMsg.optString("role");
                    }
                    // First model turn MUST NOT have injected pre-retrieval
                    if (mcpCalls.get() != 0) {
                        verificationError[0] = "Model POST #1 triggered after MCP retrieval (expected 0 pre-retrieval calls, got " + mcpCalls.get() + ")";
                    }
                    if (systemPrompt.contains("REAL_EVIDENCE_SNIPPET") || systemPrompt.contains("doc-101")) {
                        verificationError[0] = "Model POST #1 system prompt contains pre-retrieved evidence";
                    }
                    // Tools array must be present and contain knowledge_query
                    JSONArray tools = body.optJSONArray("tools");
                    if (tools == null) {
                        verificationError[0] = "Model POST #1 missing tools array";
                    } else {
                        boolean hasKq = false;
                        for (int i = 0; i < tools.length(); i++) {
                            JSONObject fn = tools.getJSONObject(i).optJSONObject("function");
                            if (fn != null && "knowledge_query".equals(fn.optString("name"))) {
                                hasKq = true;
                                break;
                            }
                        }
                        if (!hasKq) {
                            verificationError[0] = "Model POST #1 tools array missing knowledge_query specification";
                        }
                    }
                }

                JSONObject delta = new JSONObject();
                String finishReason;

                if ("direct_answer".equals(testMode[0])) {
                    finishReason = "stop";
                    delta.put("content", "Direct answer without tools");
                } else if ("knowledge_hit".equals(testMode[0]) || "knowledge_long".equals(testMode[0]) || "knowledge_empty".equals(testMode[0]) || "knowledge_error".equals(testMode[0])) {
                    if (currentPost == 1) {
                        // Round 1: Model outputs draft tokens AND tool_call
                        finishReason = "tool_calls";
                        delta.put("content", "Draft response before tool call");
                        JSONArray toolCalls = new JSONArray();
                        JSONObject fn = new JSONObject();
                        fn.put("name", "knowledge_query");
                        fn.put("arguments", "{\"query\":\"SCADA architecture\"}");

                        JSONObject call = new JSONObject();
                        call.put("index", 0);
                        call.put("id", "call_kq_1");
                        call.put("function", fn);
                        toolCalls.put(call);
                        delta.put("tool_calls", toolCalls);
                    } else {
                        // Round 2: Verify tool call and tool message were appended
                        finishReason = "stop";
                        delta.put("content", "Final organized answer based on evidence");

                        if (mcpCalls.get() != 1) {
                            verificationError[0] = "Expected exactly 1 MCP call before model POST #2, got: " + mcpCalls.get();
                        }
                        boolean hasToolCallAssistant = false;
                        boolean hasToolResponse = false;
                        for (int i = 0; i < messages.length(); i++) {
                            JSONObject m = messages.getJSONObject(i);
                            if ("assistant".equals(m.optString("role")) && m.has("tool_calls")) {
                                hasToolCallAssistant = true;
                            }
                            if ("tool".equals(m.optString("role")) && "call_kq_1".equals(m.optString("tool_call_id"))) {
                                hasToolResponse = true;
                                String content = m.optString("content");
                                if ("knowledge_hit".equals(testMode[0])) {
                                    if (!content.contains("REAL_EVIDENCE_SNIPPET") || !content.contains("doc-101")) {
                                        verificationError[0] = "Tool response missing evidence snippet: " + content;
                                    }
                                } else if ("knowledge_long".equals(testMode[0])) {
                                    if (!content.contains("LONG_EVIDENCE_SENTINEL") || content.contains("结果超过安全长度")) {
                                        verificationError[0] = "Long tool response lost evidence: " + content;
                                    }
                                } else if ("knowledge_empty".equals(testMode[0])) {
                                    if (!content.contains("EMPTY") && !content.contains("count\":0")) {
                                        verificationError[0] = "Tool response missing EMPTY status: " + content;
                                    }
                                } else if ("knowledge_error".equals(testMode[0])) {
                                    if (!content.contains("ERROR")) {
                                        verificationError[0] = "Tool response missing ERROR status: " + content;
                                    }
                                }
                            }
                        }
                        if (!hasToolCallAssistant) {
                            verificationError[0] = "Model POST #2 missing assistant message with tool_calls";
                        }
                        if (!hasToolResponse) {
                            verificationError[0] = "Model POST #2 missing tool response message";
                        }
                        JSONObject last = messages.getJSONObject(messages.length() - 1);
                        String guide = last.optString("content");
                        if ("knowledge_hit".equals(testMode[0]) || "knowledge_long".equals(testMode[0])) {
                            if (!"system".equals(last.optString("role")) || !guide.contains("正文") || !guide.contains("不要只")) {
                                verificationError[0] = "HIT tool round must end with guidance to answer from evidence text, not counts: " + last;
                            }
                        } else if (!"system".equals(last.optString("role")) || !guide.contains("未返回") || !guide.contains("不得编造")) {
                            verificationError[0] = "EMPTY/ERROR tool round must end with honest no-result guidance: " + last;
                        }
                    }
                } else if ("parallel_3".equals(testMode[0])) {
                    if (currentPost == 1) {
                        finishReason = "tool_calls";
                        JSONArray toolCalls = new JSONArray();

                        JSONObject fn0 = new JSONObject().put("name", "knowledge_query").put("arguments", "{\"query\":\"SCADA architecture\"}");
                        toolCalls.put(new JSONObject().put("index", 0).put("id", "call_p_0").put("function", fn0));

                        JSONObject fn1 = new JSONObject().put("name", "dsh_dispatch").put("arguments", "{}");
                        toolCalls.put(new JSONObject().put("index", 1).put("id", "call_p_1").put("function", fn1));

                        JSONObject fn2 = new JSONObject().put("name", "dsh_dispatch").put("arguments", "{}");
                        toolCalls.put(new JSONObject().put("index", 2).put("id", "call_p_2").put("function", fn2));

                        delta.put("tool_calls", toolCalls);
                    } else {
                        finishReason = "stop";
                        delta.put("content", "Final response after 3 parallel tools");
                    }
                } else if ("multi_round_tools".equals(testMode[0])) {
                    if (currentPost == 1) {
                        finishReason = "tool_calls";
                        JSONArray toolCalls = new JSONArray();
                        JSONObject fn = new JSONObject().put("name", "knowledge_query").put("arguments", "{\"query\":\"SCADA query\"}");
                        toolCalls.put(new JSONObject().put("index", 0).put("id", "call_mr_1").put("function", fn));
                        delta.put("tool_calls", toolCalls);
                    } else if (currentPost == 2) {
                        finishReason = "tool_calls";
                        JSONArray toolCalls = new JSONArray();
                        JSONObject fn = new JSONObject().put("name", "dsh_dispatch").put("arguments", "{}");
                        toolCalls.put(new JSONObject().put("index", 0).put("id", "call_mr_2").put("function", fn));
                        delta.put("tool_calls", toolCalls);
                    } else {
                        finishReason = "stop";
                        delta.put("content", "Final response after 2 tool rounds");
                    }
                } else {
                    finishReason = "stop";
                    delta.put("content", "Default fallback");
                }

                JSONObject choice = new JSONObject();
                choice.put("delta", delta);
                choice.put("finish_reason", finishReason);

                JSONObject root = new JSONObject();
                root.put("choices", new JSONArray().put(choice));

                String sse = "data: " + root.toString() + "\n\n";
                byte[] payload = sse.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            } catch (Throwable t) {
                verificationError[0] = t.toString();
            } finally {
                exchange.close();
            }
        });

        server.start();

        try {
            int port = server.getAddress().getPort();
            String base = "http://127.0.0.1:" + port;

            McpConnectionStore.endpoint = base + "/mcp";
            McpConnectionStore.token = "test-token";
            McpConnectionStore.enabled = true;

            // Scenario 1: Direct answer without tool call (0 MCP overhead)
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "direct_answer";
            runRequest(base);

            check(modelRequests.get() == 1, "Expected 1 model request for direct answer, got: " + modelRequests.get());
            check(mcpCalls.get() == 0, "Expected 0 MCP calls for direct answer, got: " + mcpCalls.get());
            check(ToolClient.calls == 0, "Expected 0 ToolClient invocations, got: " + ToolClient.calls);
            check(ChatRequestHarness.emittedAnswers.size() == 1, "Expected 1 emitted answer");
            check("Direct answer without tools".equals(ChatRequestHarness.emittedAnswers.get(0)),
                    "Unexpected emitted answer: " + ChatRequestHarness.emittedAnswers.get(0));
            check(verificationError[0] == null, "Verification error in direct_answer: " + verificationError[0]);
            check(ChatRequestHarness.TOOL_TRACE.snapshot().contains("knowledge_query: not_invoked (direct answer)"),
                    "Direct answer trace must report knowledge_query not_invoked");
            check(ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic().equals("knowledge_query: not_invoked (direct answer)"),
                    "Direct answer diagnostic helper must report not_invoked");

            // Regression: Tool round draft must not be emitted to UI before tool completion
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_hit";
            runRequest(base);
            check(!ChatRequestHarness.emittedDraft(), "Interim draft emitted during tool round before final answer: " + ChatRequestHarness.emittedFinalFlags);
            check(ChatRequestHarness.emittedAnswers.size() == 1, "Expected exactly 1 emitted final answer");
            check("Final organized answer based on evidence".equals(ChatRequestHarness.emittedAnswers.get(0)),
                    "Expected final answer, got: " + ChatRequestHarness.emittedAnswers.get(0));

            // Scenario 2: Tool-driven knowledge query with HIT evidence
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_hit";
            runRequest(base);

            check(modelRequests.get() == 2, "Expected 2 model requests (1 tool_call + 1 final), got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected 1 MCP retrieval call, got: " + mcpCalls.get());
            check(ToolClient.calls == 1, "Expected 1 ToolClient call, got: " + ToolClient.calls);
            check(ChatRequestHarness.emittedAnswers.size() == 1, "Expected 1 final emitted answer");
            check("Final organized answer based on evidence".equals(ChatRequestHarness.emittedAnswers.get(0)),
                    "Expected final answer, got: " + ChatRequestHarness.emittedAnswers.get(0));
            check(verificationError[0] == null, "Verification error in knowledge_hit: " + verificationError[0]);
            check(ChatRequestHarness.TOOL_TRACE.snapshot().contains("knowledge_query=status=HIT count=1 coverage=1.0"),
                    "HIT trace must contain status=HIT count=1 coverage=1.0: " + ChatRequestHarness.TOOL_TRACE.snapshot());
            check(ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic().contains("[Tool] knowledge_query: status=HIT count=1 coverage=1.0"),
                    "HIT diagnostic must reflect knowledge_query outcome: " + ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic());

            // Scenario 3: Tool-driven knowledge query with LONG evidence (>20000 chars)
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_long";
            runRequest(base);
            check(modelRequests.get() == 2, "Expected 2 model requests for long retrieval, got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected 1 MCP call for long retrieval, got: " + mcpCalls.get());
            check(ToolClient.calls == 1, "Expected 1 ToolClient call, got: " + ToolClient.calls);
            check(verificationError[0] == null, "Verification error in knowledge_long: " + verificationError[0]);
            check(ChatRequestHarness.TOOL_TRACE.snapshot().contains("truncated=true"), "LONG trace must reflect post-truncation truncated=true: " + ChatRequestHarness.TOOL_TRACE.snapshot());
            check(!ChatRequestHarness.chatLogs.isEmpty() && ChatRequestHarness.chatLogs.get(0).contains("ev=tools_ready"), "Expected tools_ready chatLog: " + ChatRequestHarness.chatLogs);
            check(ChatRequestHarness.chatLogs.toString().contains("ev=kb_exec status=HIT count=1"), "Expected kb_exec chatLog: " + ChatRequestHarness.chatLogs);

            // Scenario 4: Tool-driven knowledge query with EMPTY result
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_empty";
            runRequest(base);

            check(modelRequests.get() == 2, "Expected 2 model requests for empty retrieval, got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected 1 MCP call for empty retrieval, got: " + mcpCalls.get());
            check(ToolClient.calls == 1, "Expected 1 ToolClient call, got: " + ToolClient.calls);
            check(verificationError[0] == null, "Verification error in knowledge_empty: " + verificationError[0]);
            check(ChatRequestHarness.TOOL_TRACE.snapshot().contains("knowledge_query=status=EMPTY count=0"),
                    "EMPTY trace must contain status=EMPTY count=0: " + ChatRequestHarness.TOOL_TRACE.snapshot());
            check(ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic().contains("[Tool] knowledge_query: status=EMPTY count=0"),
                    "EMPTY diagnostic must reflect knowledge_query outcome: " + ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic());

            // Scenario 4: Tool-driven knowledge query with tool ERROR
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_error";
            runRequest(base);

            check(modelRequests.get() == 2, "Expected 2 model requests for error retrieval, got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected 1 MCP call for error retrieval, got: " + mcpCalls.get());
            check(ToolClient.calls == 1, "Expected 1 ToolClient call, got: " + ToolClient.calls);
            check(verificationError[0] == null, "Verification error in knowledge_error: " + verificationError[0]);
            check(ChatRequestHarness.TOOL_TRACE.snapshot().contains("knowledge_query=status=ERROR"),
                    "ERROR trace must contain status=ERROR: " + ChatRequestHarness.TOOL_TRACE.snapshot());
            check(ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic().contains("[Tool] knowledge_query: status=ERROR"),
                    "ERROR diagnostic must reflect knowledge_query outcome: " + ChatRequestHarness.TOOL_TRACE.knowledgeDiagnostic());

            // Scenario 5: Mid-retrieval cancellation (generation token bumped during tool execution)
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_hit";
            blockLatch[0] = new CountDownLatch(1);
            onToolsCall[0] = () -> {
                // Cancel generation while tool retrieval is in-flight
                ChatRequestHarness.generation++;
                blockLatch[0].countDown();
            };

            runRequest(base);
            check(modelRequests.get() == 1, "Model POST #2 must be suppressed when generation cancelled mid-retrieval; got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected MCP call in progress, got: " + mcpCalls.get());

            // Scenario 6: Mid-retrieval configuration revision change
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "knowledge_hit";
            ChatRequestHarness.generation = 10;
            blockLatch[0] = new CountDownLatch(1);
            onToolsCall[0] = () -> {
                // Mutate store revision while tool retrieval is in-flight
                McpConnectionStore.revision = 99;
                blockLatch[0].countDown();
            };

            runRequest(base);
            check(modelRequests.get() == 1, "Model POST #2 must be suppressed when store revision changes mid-retrieval; got: " + modelRequests.get());
            check(mcpCalls.get() == 1, "Expected MCP call in progress, got: " + mcpCalls.get());

            // Scenario 7: Parallel tool calls (indices 0, 1, 2)
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            onToolsCall[0] = null;
            blockLatch[0] = null;
            McpConnectionStore.revision = 100;
            ChatRequestHarness.generation = 20;
            testMode[0] = "parallel_3";
            runRequest(base);

            check(modelRequests.get() == 2, "Expected 2 model requests (1 tool round with 3 parallel calls + 1 final stop), got: " + modelRequests.get());
            check(ToolClient.calls == 2, "Expected knowledge_query + first dsh_dispatch to execute and the duplicate dispatch to be refused by the per-turn budget, got: " + ToolClient.calls);
            check(mcpCalls.get() == 1, "Expected exactly 1 MCP retrieval, got: " + mcpCalls.get());
            check(verificationError[0] == null, "Verification error in parallel_3: " + verificationError[0]);

            // Scenario 8: Multi-round tools (Round 1: knowledge_query, Round 2: dsh_dispatch, Round 3: stop)
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "multi_round_tools";
            ChatRequestHarness.generation = 30;
            McpConnectionStore.revision = 200;
            runRequest(base);

            check(modelRequests.get() == 3, "Expected 3 model requests for multi-round tools, got: " + modelRequests.get());
            check(ToolClient.calls == 2, "Expected 2 tool calls across 2 tool rounds, got: " + ToolClient.calls);
            check(mcpCalls.get() == 1, "Expected 1 MCP retrieval in multi-round tools, got: " + mcpCalls.get());
            check(verificationError[0] == null, "Verification error in multi_round_tools: " + verificationError[0]);

            // Scenario 9: Unrelated earlier topic in history must not anchor the current question
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "history_topic";
            ChatRequestHarness.generation = 40;
            ChatRequestHarness.request(ChatRequestHarness.generation, base, "dummy-api-key", "test-model", "How does SCADA work?", "You are an assistant.",
                    java.util.Arrays.asList(new String[]{"user", "Netty 的 PoolChunk 怎么分配内存？"}, new String[]{"assistant", "PoolChunk 使用伙伴算法分配页。"}));
            check(modelRequests.get() == 1, "Expected 1 model request for history scenario, got: " + modelRequests.get());
            check(verificationError[0] == null, "Verification error in history_topic: " + verificationError[0]);

            // Scenario 10: web search, knowledge and DSH coexist; each registered tool keeps its own route
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "routing_all";
            ToolClient.webSearch = true;
            ToolClient.dsh = true;
            ChatRequestHarness.generation = 50;
            ChatRequestHarness.request(ChatRequestHarness.generation, base, "dummy-api-key", "test-model", "让 AI 助手查询中山天气", "You are an assistant.", java.util.Collections.<String[]>emptyList());
            check(modelRequests.get() == 1, "Expected 1 model request for routing_all, got: " + modelRequests.get());
            check(verificationError[0] == null, "Verification error in routing_all: " + verificationError[0]);

            // Scenario 11: guidance only names tools that are actually registered
            resetState(modelRequests, mcpCalls, mcpInitialized, verificationError);
            testMode[0] = "routing_knowledge_only";
            ToolClient.webSearch = false;
            ToolClient.dsh = false;
            ChatRequestHarness.generation = 60;
            ChatRequestHarness.request(ChatRequestHarness.generation, base, "dummy-api-key", "test-model", "让 AI 助手查询中山天气", "You are an assistant.", java.util.Collections.<String[]>emptyList());
            ToolClient.dsh = true;
            check(modelRequests.get() == 1, "Expected 1 model request for routing_knowledge_only, got: " + modelRequests.get());
            check(verificationError[0] == null, "Verification error in routing_knowledge_only: " + verificationError[0]);

            System.out.println("ALL_CHAT_RETRIEVAL_TESTS_PASSED");
        } finally {
            server.stop(0);
        }
    }

    private static void resetState(AtomicInteger modelRequests, AtomicInteger mcpCalls, AtomicInteger mcpInitialized, String[] verificationError) {
        modelRequests.set(0);
        mcpCalls.set(0);
        mcpInitialized.set(0);
        ToolClient.calls = 0;
        ToolClient.calledTools.clear();
        verificationError[0] = null;
        ChatRequestHarness.reset();
    }

    private static void runRequest(String url) {
        ChatRequestHarness.request(
                ChatRequestHarness.generation,
                url,
                "dummy-api-key",
                "test-model",
                "How does SCADA work?",
                "You are an assistant.",
                Collections.<String[]>emptyList()
        );
    }
}
