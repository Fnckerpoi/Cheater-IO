package com.turboio.addon;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage-one voice alias routing tests: normalization, ownership scoping, alias/runtime uniqueness,
 * table limits, durable round-trip and the deterministic routing guard. Plain main() style, matching
 * the existing android-addon/tests harness.
 */
public final class DshAliasRegistryTest {
    static int checks;
    static void ok(boolean value) {
        checks++;
        if (!value) throw new AssertionError("AliasRegistry check " + checks);
    }
    static void reject(String code, Runnable run) {
        boolean failed = false;
        try { run.run(); } catch (IllegalArgumentException e) { failed = code.equals(e.getMessage()); }
        checks++;
        if (!failed) throw new AssertionError("expected " + code + " at check " + checks);
    }

    static final String URL = "https://example.com";
    static final String MODEL = "dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
    static final String PERM = "dsh:permission:cmVhZC1vbmx5";
    static final String LONE_SURROGATE = String.valueOf((char) 0xD800);
    static final String MATH_BOLD_A = "\uD835\uDC00"; // U+1D400, NFKC -> "A"

    static DshAliasRegistry table() { return new DshAliasRegistry("user-1", URL); }
    static void add(DshAliasRegistry t, String alias, String runtime) {
        t.upsertSnapshot(alias, URL, "connector-1", runtime, "project-1", MODEL, PERM);
    }
    static boolean same(DshAliasRegistry.AliasEntry a, DshAliasRegistry.AliasEntry b) {
        return a.alias.equals(b.alias) && a.aliasKey.equals(b.aliasKey) && a.baseUrl.equals(b.baseUrl)
            && a.connectorId.equals(b.connectorId) && a.runtimeId.equals(b.runtimeId)
            && a.projectId.equals(b.projectId) && a.model.equals(b.model) && a.permission.equals(b.permission);
    }

    static void normalization() {
        ok("".equals(DshAliasRegistry.normalizeAlias(null)));
        ok("office pc".equals(DshAliasRegistry.normalizeAlias("  Office  PC  ")));
        ok("office".equals(DshAliasRegistry.normalizeAlias("\uFF2F\uFF26\uFF26\uFF29\uFF23\uFF25"))); // fullwidth NFKC
        ok("a".equals(DshAliasRegistry.normalizeAlias(MATH_BOLD_A))); // NFKC fold + lowercase
        ok("a b c".equals(DshAliasRegistry.normalizeAlias("a\u2028b\u2029c"))); // line/para separators fold
        ok("".equals(DshAliasRegistry.normalizeAlias("a\t\n b"))); // \t\n are Cc controls: rejected whole, same as AA
        ok("office pc".equals(DshAliasRegistry.normalizeAlias("office\u00a0pc"))); // NBSP NFKC-folds to space
        ok("ss".equals(DshAliasRegistry.normalizeAlias("\u1e9e"))); // casefold: capital sharp s -> ss
        ok("strasse".equals(DshAliasRegistry.normalizeAlias("Stra\u00dfe"))); // sharp s expands
        ok(DshAliasRegistry.normalizeAlias("STRASSE").equals(DshAliasRegistry.normalizeAlias("Stra\u00dfe")));
        ok("\u03c3".equals(DshAliasRegistry.normalizeAlias("\u03c2"))); // final sigma folds onto sigma
        ok("\u03c3".equals(DshAliasRegistry.normalizeAlias("\u03f2"))); // lunate sigma folds too
        ok("\u03bf\u03c3".equals(DshAliasRegistry.normalizeAlias("\u039f\u03a3"))); // contextual final-sigma lowering
        reject("ALIAS_CONTROL", new Runnable() { public void run() { add(table(), "office\n", "r1"); } }); // pre-trim control
        reject("ALIAS_CONTROL", new Runnable() { public void run() { add(table(), "\toffice", "r1"); } });
        reject("ALIAS_CONTROL", new Runnable() { public void run() { add(table(), "office\t", "r1"); } });
        ok("\uD83D\uDE80a".equals(DshAliasRegistry.normalizeAlias("\uD83D\uDE80a"))); // non-BMP stays legal
        ok("".equals(DshAliasRegistry.normalizeAlias("\u0000")));
        ok("".equals(DshAliasRegistry.normalizeAlias("\u0007")));
        ok("".equals(DshAliasRegistry.normalizeAlias("\u007f")));
        ok("".equals(DshAliasRegistry.normalizeAlias("\u200d"))); // format/invisible
        ok("".equals(DshAliasRegistry.normalizeAlias("\uFEFF")));
        ok("".equals(DshAliasRegistry.normalizeAlias("\uE000"))); // private use
        ok("".equals(DshAliasRegistry.normalizeAlias("\u0378"))); // unassigned
        ok("".equals(DshAliasRegistry.normalizeAlias(LONE_SURROGATE))); // fail closed, never throws
    }

    static void lengthLimits() {
        final String a32 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        final String a33 = a32 + "a";
        add(table(), a32, "r1");
        reject("ALIAS_LENGTH", new Runnable() { public void run() { add(table(), a33, "r2"); } });
        StringBuilder nonBmp = new StringBuilder();
        for (int i = 0; i < 16; i++) nonBmp.append("\uD83D\uDE80"); // 32 UTF-16 chars, 16 code points
        add(table(), nonBmp.toString(), "r3");
        checks++;
    }

    static void ownershipScope() {
        final DshAliasRegistry t = table();
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            t.upsertSnapshot("Office PC", "https://other.example.com", "c1", "r1", "p1", MODEL, PERM); } });
        t.upsertSnapshot("Office PC", "HTTPS://EXAMPLE.com/", "c1", "r1", "p1", MODEL, PERM); // normalized URL match
        ok(t.sizeSnapshot() == 1);
        final DshAliasRegistry same = t;
        reject("IDENTITY_REQUIRED", new Runnable() { public void run() {
            same.upsertSnapshot("Home", URL, "c 1", "r2", "p1", MODEL, PERM); } });
        reject("SELECTION_REQUIRED", new Runnable() { public void run() {
            same.upsertSnapshot("Home", URL, "c2", "r2", "p1", "garbage", PERM); } });
        final DshAliasRegistry foreign = new DshAliasRegistry("", URL);
        ok(!foreign.usable());
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            foreign.upsertSnapshot("Home", URL, "c2", "r2", "p1", MODEL, PERM); } });
    }

    static void uniquenessAndRename() {
        final DshAliasRegistry t = table();
        add(t, "Office PC", "r1");
        add(t, "Home PC", "r2");
        reject("ALIAS_TAKEN", new Runnable() { public void run() {
            t.upsertSnapshot("Office PC", URL, "connector-1", "r2", "project-1", MODEL, PERM); } });
        add(t, "Third PC", "r1"); // same runtime re-upsert = rename
        ok(t.sizeSnapshot() == 2);
        ok("third pc".equals(t.listSnapshot().get(0).aliasKey)); // insertion order preserved
        ok("home pc".equals(t.listSnapshot().get(1).aliasKey));
        ok("r1".equals(t.resolveSnapshot(" Third PC ").runtimeId));
        ok(t.resolveSnapshot("Office PC") == null); // old display is gone after rename
        t.deleteSnapshot("Third PC");
        ok(t.sizeSnapshot() == 1);
        t.deleteSnapshot("home pc");
        ok(t.sizeSnapshot() == 0);
        reject("ALIAS_NOT_FOUND", new Runnable() { public void run() { t.deleteSnapshot("Third PC"); } });
    }

    static void updateGuards() {
        final DshAliasRegistry t = table();
        List<DshAliasRegistry.AliasEntry> dupRuntime = new ArrayList<DshAliasRegistry.AliasEntry>();
        dupRuntime.add(new DshAliasRegistry.AliasEntry("A", "a", URL, "c1", "r1", "p1", MODEL, PERM));
        dupRuntime.add(new DshAliasRegistry.AliasEntry("B", "b", URL, "c1", "r1", "p1", MODEL, PERM));
        reject("RUNTIME_TAKEN", new Runnable() { public void run() { t.updateSnapshot(dupRuntime); } });
        List<DshAliasRegistry.AliasEntry> keyMismatch = new ArrayList<DshAliasRegistry.AliasEntry>();
        keyMismatch.add(new DshAliasRegistry.AliasEntry("Office PC", "wrong-key", URL, "c1", "r1", "p1", MODEL, PERM));
        reject("ALIAS_KEY_MISMATCH", new Runnable() { public void run() { t.updateSnapshot(keyMismatch); } });
        List<DshAliasRegistry.AliasEntry> nine = new ArrayList<DshAliasRegistry.AliasEntry>();
        for (int i = 1; i <= 9; i++) nine.add(new DshAliasRegistry.AliasEntry("a" + i, "a" + i, URL,
            "c1", "r" + i, "p1", MODEL, PERM));
        reject("ALIAS_LIMIT", new Runnable() { public void run() { t.updateSnapshot(nine); } });
        ok(t.sizeSnapshot() == 0); // a rejected update never applies partially
        t.updateSnapshot(nine.subList(0, 8));
        ok(t.sizeSnapshot() == 8);
        t.updateSnapshot(null); // atomic clear
        ok(t.sizeSnapshot() == 0);
    }

    static void durableRoundTrip() {
        DshAliasRegistry t = table();
        add(t, "Office PC", "r1");
        add(t, "\uD83D\uDE80", "r2"); // non-BMP display must survive the JSON round trip
        String doc = DshAliasRegistry.serializeSnapshot(t);
        ok(doc.contains("\"schemaVersion\":1"));
        ok(doc.contains("ownerUserId"));
        ok(!doc.toLowerCase(java.util.Locale.ROOT).contains("token")); // durable form holds no credentials
        DshAliasRegistry back = DshAliasRegistry.deserializeSnapshot(doc, "user-1", URL);
        ok(back.sizeSnapshot() == 2);
        ok(same(t.listSnapshot().get(0), back.listSnapshot().get(0)));
        ok(same(t.listSnapshot().get(1), back.listSnapshot().get(1)));
        ok("\uD83D\uDE80".equals(back.listSnapshot().get(1).alias));
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot(doc, "user-2", URL); } });
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot(doc, "user-1", "https://other.example.com"); } });
        reject("ALIAS_SCHEMA", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot(doc.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "user-1", URL); } });
        reject("ALIAS_SCHEMA", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot(doc.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""), "user-1", URL); } });
        reject("ALIAS_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot("{\"schemaVersion\":1,", "user-1", URL); } });
        reject("ALIAS_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"aliases\":{}}", "user-1", URL); } });
        reject("ALIAS_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"aliases\":[3]}", "user-1", URL); } });
        reject("ALIAS_OWNER", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot("{\"schemaVersion\":1,\"aliases\":[]}", "user-1", URL); } });
        reject("ALIAS_DOCUMENT_EMPTY", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot("", "user-1", URL); } });
        StringBuilder big = new StringBuilder("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"aliases\":[],\"pad\":\"");
        for (int i = 0; i < 17000; i++) big.append('x');
        big.append("\"}");
        reject("ALIAS_DOCUMENT_SIZE", new Runnable() { public void run() {
            DshAliasRegistry.deserializeSnapshot(big.toString(), "user-1", URL); } });
    }

    static void routingDecisions() {
        final DshAliasRegistry.AliasEntry e1 =
            new DshAliasRegistry.AliasEntry("Office PC", "office pc", URL, "c1", "r1", "p1", MODEL, PERM);
        final DshAliasRegistry.AliasEntry e2 =
            new DshAliasRegistry.AliasEntry("Home PC", "home pc", URL, "c1", "r2", "p1", MODEL, PERM);
        List<DshAliasRegistry.AliasEntry> empty = new ArrayList<DshAliasRegistry.AliasEntry>();
        List<DshAliasRegistry.AliasEntry> one = new ArrayList<DshAliasRegistry.AliasEntry>(); one.add(e1);
        List<DshAliasRegistry.AliasEntry> two = new ArrayList<DshAliasRegistry.AliasEntry>(); two.add(e1); two.add(e2);
        // empty table: no target needed, nothing to bind
        ok(DshAliasRegistry.resolveForAction(empty, null).code.equals(DshAliasRegistry.RESOLVED));
        ok(DshAliasRegistry.resolveForAction(empty, null).entry == null);
        ok(DshAliasRegistry.resolveForAction(empty, "x").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND));
        // single alias: no target needed, explicit target still resolves
        ok(DshAliasRegistry.resolveForAction(one, null).code.equals(DshAliasRegistry.RESOLVED));
        ok(DshAliasRegistry.resolveForAction(one, null).entry == null); // keep the current binding
        ok(DshAliasRegistry.resolveForAction(one, "Office PC").entry == e1);
        // two or more aliases: a target is mandatory before any network request
        ok(DshAliasRegistry.resolveForAction(two, null).code.equals(DshAliasRegistry.TARGET_REQUIRED));
        ok(DshAliasRegistry.resolveForAction(two, "   ").code.equals(DshAliasRegistry.TARGET_REQUIRED));
        ok(DshAliasRegistry.resolveForAction(two, null).entry == null);
        // exact match after normalization only
        ok(DshAliasRegistry.resolveForAction(two, "  OFFICE  PC ").entry == e1);
        ok(DshAliasRegistry.resolveForAction(two, "office\u00a0pc").entry == e1);
        ok(DshAliasRegistry.resolveForAction(two, "office").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND));
        ok(DshAliasRegistry.resolveForAction(two, "office pc x").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND));
        ok(DshAliasRegistry.resolveForAction(two, "office pc\n").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND)); // no trim bypass
        ok(DshAliasRegistry.resolveForAction(two, "\tOffice PC").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND));
        ok(DshAliasRegistry.resolveForAction(two, " \t ").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND)); // control, not TARGET_REQUIRED
        ok(DshAliasRegistry.resolveForAction(two, "\u200d").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND)); // no throw
        ok(DshAliasRegistry.resolveForAction(two, LONE_SURROGATE).code.equals(DshAliasRegistry.ALIAS_NOT_FOUND)); // no throw
        ok(DshAliasRegistry.resolveForAction(null, "Office PC").code.equals(DshAliasRegistry.ALIAS_NOT_FOUND)); // null table resolves nothing
    }

    static void lookupAndConfigGuard() {
        DshAliasRegistry t = table();
        add(t, "Office PC", "r1");
        ok(t.getSnapshot("office pc") != null); // normalized key lookup
        ok(t.getSnapshot("Office PC") == null); // raw display is not a key
        ok(t.resolveSnapshot("Office PC") != null); // resolve normalizes the target
        ok(t.resolveSnapshot("office") == null); // no fuzzy match
        ok(t.resolveSnapshot(null) == null);
        // incomplete snapshots must never qualify for a binding switch (NEEDS_CONFIGURATION upstream)
        ok(DshClient.validBinding(URL, "c1", "p1", "r1", MODEL, PERM));
        ok(!DshClient.validBinding(URL, "c1", "p1", "r1", "", PERM));
        ok(!DshClient.validBinding(URL, "c1", "p1", "r1", MODEL, ""));
        ok(!DshClient.validBinding(URL, "c1", "p1", "", MODEL, PERM));
    }

    static java.util.Map<String, Object> runtimeRow(String connector, String runtime, String type, String status, boolean usable) {
        return DshClient.mapOf("connectorId", connector, "runtimeId", runtime, "runtimeType", type,
            "status", status, "available", Boolean.valueOf(usable), "configured", Boolean.valueOf(usable),
            "active", Boolean.valueOf(usable), "displayName", "RT " + runtime);
    }
    static java.util.Map<String, Object> directory(String topConnector, java.util.Map<String, Object>... rows) {
        return DshClient.mapOf("connectorId", topConnector, "runtimes", new ArrayList<java.util.Map<String, Object>>(java.util.Arrays.asList(rows)));
    }
    static void directoryCheck() {
        // P1: dispatch-time live directory identity/availability verdict (pure, no Android, no HTTP).
        java.util.Map<String, Object> good = directory("c1", runtimeRow("c1", "r1", "dsh", "running", true));
        ok(ToolClient.DirectoryCheck.OK.equals(ToolClient.DirectoryCheck.verdict(good, "c1", "r1")));
        ok("ALIAS_TARGET_RETIRED".equals(ToolClient.DirectoryCheck.verdict(directory("c1"), "c1", "r1"))); // vanished
        ok("ALIAS_TARGET_RETIRED".equals(ToolClient.DirectoryCheck.verdict(directory("c2", runtimeRow("c1", "r1", "dsh", "running", true)), "c1", "r1"))); // top connector mismatch
        ok("ALIAS_TARGET_RETIRED".equals(ToolClient.DirectoryCheck.verdict(directory("c1", runtimeRow("c2", "r1", "dsh", "running", true)), "c1", "r1"))); // row connector mismatch
        ok("ALIAS_TARGET_RETIRED".equals(ToolClient.DirectoryCheck.verdict(directory("c1", runtimeRow("c1", "r1", "other", "running", true)), "c1", "r1"))); // wrong runtime type
        ok("TARGET_UNAVAILABLE".equals(ToolClient.DirectoryCheck.verdict(directory("c1", runtimeRow("c1", "r1", "dsh", "starting", true)), "c1", "r1"))); // starting: keep binding
        ok("TARGET_UNAVAILABLE".equals(ToolClient.DirectoryCheck.verdict(directory("c1", runtimeRow("c1", "r1", "dsh", "running", false)), "c1", "r1"))); // unavailable: keep binding
        ok("ALIAS_TARGET_RETIRED".equals(ToolClient.DirectoryCheck.verdict(directory("c1", runtimeRow("c1", "r2", "dsh", "running", true)), "c1", "r1"))); // only another runtime
        reject("invalid_directory_response", new Runnable() { public void run() {
            ToolClient.DirectoryCheck.verdict(DshClient.mapOf("connectorId", "c1"), "c1", "r1"); } }); // malformed listing
    }

    static void fullCasefold() {
        ok("\u03b9\u0308\u0301".equals(DshAliasRegistry.normalizeAlias("\u0390")));
        ok("\u13a0".equals(DshAliasRegistry.normalizeAlias("\uab70")));
        ok("\u13a0".equals(DshAliasRegistry.normalizeAlias("\u13a0")));
        ok("j\u030c".equals(DshAliasRegistry.normalizeAlias("\u01f0")));
        String key=DshAliasRegistry.normalizeAlias("\u0390");
        ok(key.equals(DshAliasRegistry.normalizeAlias(key)));
    }
    public static void main(String[] args) {
        fullCasefold();
        normalization();
        lengthLimits();
        ownershipScope();
        uniquenessAndRename();
        updateGuards();
        durableRoundTrip();
        routingDecisions();
        lookupAndConfigGuard();
        directoryCheck();
        System.out.println("DshAliasRegistryTest: " + checks + " checks passed");
    }
}
