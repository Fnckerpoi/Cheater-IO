package com.turboio.addon;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;

/** User-owned MCP connection and write-only credential editor. */
public final class McpSettings {
    private McpSettings() {}
    private static String probeStatus(KnowledgeRetrieval.Status status) {
        switch (status) {
            case NO_MATCH: case HIT: return "MCP 协议自检通过，已发现 cortrix_query（未执行检索）";
            case DISABLED: return "知识库已停用";
            case UNCONFIGURED: return "请先保存 MCP 地址";
            case AUTH_ERROR: return "认证失败，请检查 Token";
            case TIMEOUT: return "MCP 自检超时";
            case TRANSPORT_ERROR: return "无法连接 MCP 服务";
            case TOOL_ERROR: return "MCP 服务返回工具错误";
            default: return "MCP 协议或工具发现失败";
        }
    }
    public static void show(Activity host) {
        final McpConnectionStore.Snapshot snapshot;
        try { snapshot = McpConnectionStore.snapshot(host.getApplicationContext()); }
        catch (IllegalStateException e) {
            Toast.makeText(host, "无法读取 MCP 凭据，请检查本机密钥存储", Toast.LENGTH_LONG).show(); return;
        }
        LinearLayout box = new LinearLayout(host); box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * host.getResources().getDisplayMetrics().density + .5f);
        box.setPadding(pad, pad, pad, pad);
        Switch enabled = new Switch(host); enabled.setText("启用 Cortrix MCP 知识库"); enabled.setChecked(snapshot.enabled); box.addView(enabled);
        TextView addressLabel = new TextView(host); addressLabel.setText("MCP 地址（远程服务须使用 HTTPS）"); box.addView(addressLabel);
        EditText endpoint = new EditText(host); endpoint.setSingleLine(true); endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); endpoint.setText(snapshot.endpoint); box.addView(endpoint);
        TextView tokenLabel = new TextView(host); tokenLabel.setText("Bearer Token（留空保留现有值）"); box.addView(tokenLabel);
        EditText token = new EditText(host); token.setSingleLine(true); token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); token.setSaveEnabled(false); box.addView(token);
        CheckBox clear = new CheckBox(host); clear.setText("清除已保存 Token"); box.addView(clear);
        TextView note = new TextView(host); note.setText("Token 使用 Android Keystore 加密保存，不回显。启用后，相关问题将发送到配置的知识库服务。"); box.addView(note);
        Button test = new Button(host); test.setText("自检已保存连接"); box.addView(test);
        TextView status = new TextView(host); status.setText("保存后可检查 MCP 初始化与工具发现"); box.addView(status);
        test.setOnClickListener(v -> {
            test.setEnabled(false); status.setText("正在检查已保存的 MCP 连接…");
            new Thread(() -> {
                String message;
                try {
                    McpConnectionStore.Snapshot current = McpConnectionStore.snapshot(host.getApplicationContext());
                    if (!current.enabled) message = "知识库已停用";
                    else {
                        KnowledgeRetrieval.Result result = KnowledgeRetrieval.probe(current.endpoint, current.token, 30000);
                        message = probeStatus(result.status);
                    }
                } catch (IllegalStateException e) { message = "凭据读取失败，请检查本机密钥存储"; }
                final String display = message;
                host.runOnUiThread(() -> { if (!host.isFinishing()) { status.setText(display); test.setEnabled(true); } });
            }, "mcp-settings-test").start();
        });
        ScrollView scroll = new ScrollView(host); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle("Cortrix MCP 连接").setView(scroll)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                McpConnectionStore.save(host.getApplicationContext(), enabled.isChecked(), endpoint.getText().toString(), token.getText().toString(), clear.isChecked());
                token.setText(""); dialog.dismiss();
                Toast.makeText(host, "MCP 连接已保存", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) { Toast.makeText(host, "请输入有效 HTTPS 地址（本机回环允许 HTTP）", Toast.LENGTH_LONG).show(); }
            catch (IllegalStateException e) { Toast.makeText(host, "保存失败，请检查本机密钥与设置存储", Toast.LENGTH_LONG).show(); }
        }));
        dialog.setOnDismissListener(d -> token.setText(""));
        dialog.show();
    }
}
