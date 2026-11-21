package com.turboio.addon;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** One immutable connection generation, including its Keystore-backed credential. */
public final class McpConnectionStore {
    private McpConnectionStore() {}
    public static final class Snapshot {
        public final boolean enabled;
        public final String endpoint, token;
        public final long revision;
        public final List<String> namespaces;
        private Snapshot(boolean enabled, String endpoint, String token, long revision) {
            this(enabled,endpoint,token,revision,"");
        }
        private Snapshot(boolean enabled, String endpoint, String token, long revision,String selected) {
            namespaces=parseNamespaces(selected);
            this.enabled = enabled; this.endpoint = endpoint; this.token = token; this.revision = revision;
        }
    }

    private static List<String> parseNamespaces(String selected) {
        LinkedHashSet<String> names=new LinkedHashSet<>();
        if (selected!=null) for (String item:selected.split("[,，]")) {
            String name=item.trim();
            if (!name.isEmpty()) names.add(name);
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }
    private static final String NAMESPACES="cortrix_hint_namespaces";
    private static final String JOURNAL = "turboio_mcp_recovery";
    private static final String ACTIVE = "active";
    private static final class Recovery {
        final SharedPreferences settings, secrets, journal;
        final boolean enabled;
        final String endpoint, legacy, encrypted, namespaces;
        final long revision;
        Recovery(SharedPreferences settings, SharedPreferences secrets, SharedPreferences journal) {
            this.settings = settings; this.secrets = secrets; this.journal = journal;
            enabled = settings.getBoolean("cortrix_enabled", false);
            namespaces = settings.getString(NAMESPACES,null);
            endpoint = settings.getString("cortrix_endpoint", "");
            revision = settings.getLong("cortrix_revision", 0L);
            legacy = settings.getString("cortrix_token", null);
            encrypted = secrets.getString("cortrix_token", null);
        }
        Recovery(SharedPreferences settings, SharedPreferences secrets, SharedPreferences journal, boolean persisted) {
            this.settings = settings; this.secrets = secrets; this.journal = journal;
            enabled = journal.getBoolean("enabled", false);
            namespaces = journal.getString(NAMESPACES,null);
            endpoint = journal.getString("endpoint", "");
            revision = journal.getLong("revision", 0L);
            legacy = journal.getString("legacy", null);
            encrypted = journal.getString("encrypted", null);
        }
        void prepare() {
            SharedPreferences.Editor editor = journal.edit().putBoolean(ACTIVE, true).putBoolean("enabled", enabled)
                .putString("endpoint", endpoint).putLong("revision", revision);
            editor = legacy == null ? editor.remove("legacy") : editor.putString("legacy", legacy);
            editor.putString(NAMESPACES,namespaces);
            editor = encrypted == null ? editor.remove("encrypted") : editor.putString("encrypted", encrypted);
            if (!editor.commit()) throw new IllegalStateException("mcp_storage_failed");
        }
        void restore() {
            if (!secrets.edit().putString("cortrix_token", encrypted == null ? "" : encrypted).commit())
                throw new IllegalStateException("mcp_recovery_failed");
            SharedPreferences.Editor editor = settings.edit().putBoolean("cortrix_enabled", enabled)
                .putString("cortrix_endpoint", endpoint).putLong("cortrix_revision", revision);
            editor = legacy == null ? editor.remove("cortrix_token") : editor.putString("cortrix_token", legacy);
            if (namespaces==null) editor.remove(NAMESPACES); else editor.putString(NAMESPACES,namespaces);
            if (!editor.commit()) throw new IllegalStateException("mcp_recovery_failed");
            if (!journal.edit().clear().commit()) throw new IllegalStateException("mcp_recovery_failed");
        }
    }
    private static SharedPreferences journal(Context context) { return context.getSharedPreferences(JOURNAL, 0); }
    private static void recover(Context context, SharedPreferences prefs, SharedPreferences secrets) {
        SharedPreferences journal = journal(context);
        if (journal.getBoolean(ACTIVE, false)) new Recovery(prefs, secrets, journal, true).restore();
    }
    public static synchronized Snapshot snapshot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("turboio_settings", 0);
        SharedPreferences secrets = context.getSharedPreferences("turboio_private", 0);
        recover(context, prefs, secrets);
        try {
            String token = SecretStore.get(context, "cortrix_token");
            long revision = prefs.getLong("cortrix_revision", 0L);
            if (prefs.contains("cortrix_token")) {
                Recovery before = new Recovery(prefs, secrets, journal(context));
                try {
                    before.prepare();
                    String legacy = prefs.getString("cortrix_token", "");
                    if (token.isEmpty() && !legacy.isEmpty()) { SecretStore.put(context, "cortrix_token", legacy); token = legacy; }
                    if (!prefs.edit().remove("cortrix_token").putLong("cortrix_revision", revision + 1).commit())
                        throw new IllegalStateException("mcp_storage_failed");
                    if (!journal(context).edit().clear().commit()) throw new IllegalStateException("mcp_storage_failed");
                    revision++;
                } catch (Exception e) {
                    try { before.restore(); } catch (Exception recoveryFailure) { e.addSuppressed(recoveryFailure); }
                    throw e;
                }
            }
            return new Snapshot(prefs.getBoolean("cortrix_enabled", false), prefs.getString("cortrix_endpoint", ""), token, revision,prefs.getString(NAMESPACES,""));
        } catch (Exception e) { throw new IllegalStateException("mcp_credentials_unavailable", e); }
    }
    public static synchronized void save(Context context, boolean enabled, String endpoint, String newToken, boolean clearToken) {
        save(context,enabled,endpoint,newToken,clearToken,null);
    }
    /** A null selection preserves the existing setting for callers editing credentials only. */
    public static synchronized void save(Context context, boolean enabled, String endpoint, String newToken, boolean clearToken, String selected) {
        String url = endpoint == null ? "" : endpoint.trim();
        validate(url, enabled);
        Snapshot before = snapshot(context);
        String input = newToken == null ? "" : newToken.trim();
        String token = clearToken ? "" : input.isEmpty() ? before.token : input;
        List<String> names=selected==null?before.namespaces:parseNamespaces(selected);
        if (names.equals(before.namespaces) && before.enabled == enabled && before.endpoint.equals(url) && before.token.equals(token)) return;
        SharedPreferences prefs = context.getSharedPreferences("turboio_settings", 0);
        SharedPreferences secrets = context.getSharedPreferences("turboio_private", 0);
        SharedPreferences journal = journal(context);
        Recovery recovery = new Recovery(prefs, secrets, journal);
        try {
            recovery.prepare();
            if (!before.token.equals(token)) SecretStore.put(context, "cortrix_token", token);
            if (!prefs.edit().putBoolean("cortrix_enabled", enabled).putString("cortrix_endpoint", url)
                    .putString(NAMESPACES,String.join(",",names)).putLong("cortrix_revision", before.revision + 1).commit()) throw new IllegalStateException("mcp_storage_failed");
            if (!journal.edit().clear().commit()) throw new IllegalStateException("mcp_storage_failed");
        } catch (Exception e) {
            try { recovery.restore(); } catch (Exception recoveryFailure) { e.addSuppressed(recoveryFailure); }
            throw new IllegalStateException("mcp_save_failed", e);
        }
    }
    static void validate(String endpoint, boolean enabled) {
        if (endpoint.isEmpty() && !enabled) return;
        try {
            URI uri = new URI(endpoint); String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null ||
                    !("https".equalsIgnoreCase(uri.getScheme()) || ("http".equalsIgnoreCase(uri.getScheme()) && loopback)))
                throw new IllegalArgumentException("mcp_endpoint_invalid");
        } catch (Exception e) { throw new IllegalArgumentException("mcp_endpoint_invalid"); }
    }
}
