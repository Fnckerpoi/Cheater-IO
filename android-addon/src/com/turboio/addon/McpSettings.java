package com.turboio.addon;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** User-owned MCP connection and write-only credential editor. */
public final class McpSettings {
    private McpSettings() {}
    private static final class NamespacePicker {
        final Button button;
        final Set<String> selected;
        List<String> available=java.util.Collections.emptyList();
        NamespacePicker(Activity host,List<String> saved) {
            selected=new LinkedHashSet<>(saved);
            button=new Button(host);
            updateLabel();
            button.setOnClickListener(v -> {
                Set<String> names=new LinkedHashSet<>(available);
                names.addAll(selected);
                List<String> choices=new ArrayList<>();
                choices.add("使用网关默认库");choices.addAll(names);
                ListPopupWindow popup=new ListPopupWindow(host);
                popup.setAnchorView(button);popup.setWidth(button.getWidth());popup.setModal(true);
                popup.setAdapter(new ArrayAdapter<>(host,android.R.layout.simple_list_item_multiple_choice,choices));
                popup.setOnItemClickListener((parent,row,index,id) -> {
                    if(index==0) selected.clear();
                    else if(!selected.remove(choices.get(index))) selected.add(choices.get(index));
                    setChecks(popup.getListView(),choices);
                    updateLabel();
                });
                popup.show();
                ListView list=popup.getListView();
                if(list!=null){list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);setChecks(list,choices);}
            });
        }
        void setChecks(ListView list,List<String> choices) {
            if(list==null)return;
            list.setItemChecked(0,selected.isEmpty());
            for(int i=1;i<choices.size();i++)list.setItemChecked(i,selected.contains(choices.get(i)));
        }
        void updateLabel() {
            button.setText((selected.isEmpty()?"使用网关默认库":String.join("、",selected))+" ▾");
        }
    }
    private static String catalogFailure(KnowledgeRetrieval.Status status) {
        return status==KnowledgeRetrieval.Status.TOOL_ERROR?"请确认服务提供 cortrix_list_namespaces 工具。":probeStatus(status);
    }
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
        TextView nsLabel = new TextView(host); nsLabel.setText("有问必答知识库"); box.addView(nsLabel);
        NamespacePicker picker = new NamespacePicker(host, snapshot.namespaces);
        box.addView(picker.button);
        Button refresh = new Button(host); refresh.setText("获取知识库列表"); box.addView(refresh);
        TextView catalogStatus = new TextView(host); box.addView(catalogStatus);
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
        refresh.setOnClickListener(v -> {
            String address=endpoint.getText().toString().trim();
            try { McpConnectionStore.validate(address,true); }
            catch (IllegalArgumentException invalid) {catalogStatus.setText("请先填写有效的 MCP 地址");return;}
            String typedToken=token.getText().toString().trim();
            if(!address.equals(snapshot.endpoint) && typedToken.isEmpty() && !snapshot.token.isEmpty() && !clear.isChecked()) {
                catalogStatus.setText("MCP 地址已更改，请填写新连接的 Token，或选择清除 Token");return;
            }
            boolean withoutToken=clear.isChecked();
            String credential=withoutToken?"":typedToken.isEmpty()?snapshot.token:typedToken;
            refresh.setEnabled(false);catalogStatus.setText("正在获取知识库列表…");
            new Thread(() -> {
                CortrixClient.NamespaceResult result=new CortrixClient(address,credential,0,0).listNamespaces(30000);
                host.runOnUiThread(() -> {
                    if(!dialog.isShowing()||host.isFinishing())return;
                    refresh.setEnabled(true);
                    if(!address.equals(endpoint.getText().toString().trim()) || withoutToken!=clear.isChecked()
                            || !typedToken.equals(token.getText().toString().trim())) {
                        catalogStatus.setText("连接已更改，请重新获取列表");return;
                    }
                    if(result.status==KnowledgeRetrieval.Status.HIT) {
                        picker.available=result.names;
                        catalogStatus.setText(result.names.isEmpty()?"服务未返回可用知识库":"已获取 "+result.names.size()+" 个知识库");
                    } else catalogStatus.setText("无法获取知识库列表。"+catalogFailure(result.status));
                });
            },"mcp-namespace-list").start();
        });
        android.text.TextWatcher connectionChanged=new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            public void onTextChanged(CharSequence text,int start,int before,int count) {picker.available=java.util.Collections.emptyList();catalogStatus.setText("");}
            public void afterTextChanged(android.text.Editable text) {}
        };
        endpoint.addTextChangedListener(connectionChanged);token.addTextChangedListener(connectionChanged);
        clear.setOnCheckedChangeListener((button,checked)->{picker.available=java.util.Collections.emptyList();catalogStatus.setText("");});
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                McpConnectionStore.save(host.getApplicationContext(), enabled.isChecked(), endpoint.getText().toString(), token.getText().toString(), clear.isChecked(), String.join(",",picker.selected));
                token.setText(""); dialog.dismiss();
                Toast.makeText(host, "MCP 连接已保存", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) { Toast.makeText(host, "请输入有效 HTTPS 地址（本机回环允许 HTTP）", Toast.LENGTH_LONG).show(); }
            catch (IllegalStateException e) { Toast.makeText(host, "保存失败，请检查本机密钥与设置存储", Toast.LENGTH_LONG).show(); }
        }));
        dialog.setOnDismissListener(d -> token.setText(""));
        dialog.show();
    }
}
