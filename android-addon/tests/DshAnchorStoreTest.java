package com.turboio.addon;

import java.util.*;

/**
 * Stage-two keyed session anchors: per target local tracking, account and credential isolation, stale
 * identity refusal, per-key unknown-delivery locking and the durable round trip. Plain main() style,
 * matching the existing android-addon/tests harness.
 */
public final class DshAnchorStoreTest {
    static int checks;
    static void ok(boolean value) {
        checks++;
        if (!value) throw new AssertionError("AnchorStore check " + checks);
    }
    static void reject(String code, Runnable run) {
        boolean failed = false;
        try { run.run(); } catch (IllegalArgumentException e) { failed = code.equals(e.getMessage()); }
        checks++;
        if (!failed) throw new AssertionError("expected " + code + " at check " + checks);
    }

    static final String URL = "https://example.com";
    static final String USER = "user-1";
    static final String MODEL = "dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
    static final String PERM = "dsh:permission:cmVhZC1vbmx5";
    static final String KEY_A = "https://example.com|con_a|proj_a|rti_a";
    static final String KEY_B = "https://example.com|con_b|proj_b|rti_b";

    static DshAnchorStore store() { return new DshAnchorStore(USER, URL); }

    /** One binding under one credential; the fingerprint is what gates anchor reuse. */
    static DshClient.Config config(String token, String connector, String project, String runtime) {
        return new DshClient.Config(true, URL, token, connector, project, runtime, MODEL, PERM);
    }

    static void keyDerivation() {
        ok(KEY_A.equals(DshAnchorStore.targetKey("HTTPS://Example.com/", "con_a", "proj_a", "rti_a")));
        ok("".equals(DshAnchorStore.targetKey("http://example.com", "con_a", "proj_a", "rti_a"))); // https only
        ok("".equals(DshAnchorStore.targetKey(URL, "con a", "proj_a", "rti_a"))); // whitespace inside an id
        ok("".equals(DshAnchorStore.targetKey(URL, "con_a", "", "rti_a")));
        String fingerprint = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        ok(KEY_A.equals(DshAnchorStore.keyOfFingerprint(fingerprint)));
        ok("".equals(DshAnchorStore.keyOfFingerprint("garbage")));
        ok("".equals(DshAnchorStore.keyOfFingerprint(null)));
        ok(!fingerprint.equals(config("token", "con_a", "proj_a", "rti_b").targetFingerprint()));
        ok(!fingerprint.equals(config("other-token", "con_a", "proj_a", "rti_a").targetFingerprint()));
    }

    static void saveAndKeyIsolation() {
        DshAnchorStore s = store();
        String fpA = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        String fpB = config("token", "con_b", "proj_b", "rti_b").targetFingerprint();
        s.saveSnapshot(KEY_A, fpA, "Office PC", "aa_session_a", "msg_a", false);
        s.saveSnapshot(KEY_B, fpB, "Home PC", "aa_session_b", "msg_b", false);
        ok(s.sizeSnapshot() == 2);
        ok("aa_session_a".equals(s.getSnapshot(KEY_A).sessionId));
        // A display label is advisory: an alias rename never moves a row to another identity key.
        ok("office pc".equals(s.getSnapshot(KEY_A).alias));
        ok("aa_session_b".equals(s.getSnapshot(KEY_B).sessionId));
        s.saveSnapshot(KEY_B, fpB, "Home PC", "aa_session_b2", "msg_b2", false);
        ok("aa_session_a".equals(s.getSnapshot(KEY_A).sessionId));
        ok("aa_session_b2".equals(s.getSnapshot(KEY_B).sessionId));
        ok(!s.anyUnknownDelivery());
        // Writing an all-empty state removes the row instead of storing a hollow one.
        s.saveSnapshot(KEY_B, fpB, "Home PC", "", "", false);
        ok(s.getSnapshot(KEY_B) == null);
        ok(s.sizeSnapshot() == 1);
        ok(s.dropSnapshot(KEY_A));
        ok(!s.dropSnapshot(KEY_A));
        ok(!s.dropSnapshot("not-a-key"));
        ok(s.sizeSnapshot() == 0);
    }

    static void rowValidation() {
        final DshAnchorStore s = store();
        final String fp = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        reject("ANCHOR_KEY_INVALID", new Runnable() { public void run() {
            s.saveSnapshot("https://example.com|con_a", fp, "a", "s", "m", false); } });
        reject("ANCHOR_KEY_REQUIRED", new Runnable() { public void run() {
            s.saveSnapshot("", fp, "a", "s", "m", false); } });
        reject("ANCHOR_FINGERPRINT", new Runnable() { public void run() {
            s.saveSnapshot(KEY_A, "", "a", "s", "m", false); } });
        reject("ANCHOR_FINGERPRINT", new Runnable() { public void run() {
            s.saveSnapshot(KEY_A, "bad fingerprint", "a", "s", "m", false); } });
        reject("ANCHOR_SESSION_INVALID", new Runnable() { public void run() {
            s.saveSnapshot(KEY_A, fp, "a", "bad session", "m", false); } });
        reject("ANCHOR_MESSAGE_INVALID", new Runnable() { public void run() {
            s.saveSnapshot(KEY_A, fp, "a", "s", "bad\u0001id", false); } });
        // A pending flag without a message id cannot be attributed, so it is never parked.
        reject("ANCHOR_MESSAGE_REQUIRED", new Runnable() { public void run() {
            s.saveSnapshot(KEY_A, fp, "a", "s", "", true); } });
        ok(s.sizeSnapshot() == 0);
        final DshAnchorStore foreign = new DshAnchorStore("", URL);
        ok(!foreign.usable());
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            foreign.saveSnapshot(KEY_A, fp, "a", "s", "m", false); } });
        // Table size stays bounded so the durable document cannot outgrow the tool budget.
        final DshAnchorStore big = store();
        for (int i = 1; i <= DshAnchorStore.MAX_ENTRIES; i++)
            big.saveSnapshot("https://example.com|c" + i + "|p" + i + "|r" + i, fp, "a", "s" + i, "m" + i, false);
        reject("ANCHOR_LIMIT", new Runnable() { public void run() {
            big.saveSnapshot("https://example.com|c9|p9|r9", fp, "a", "s9", "m9", false); } });
        ok(big.sizeSnapshot() == DshAnchorStore.MAX_ENTRIES);
        // Re-saving an existing key is fine even at the limit: no growth, so no new slot is needed.
        big.saveSnapshot("https://example.com|c1|p1|r1", fp, "renamed", "s1b", "m1b", false);
        ok(big.sizeSnapshot() == DshAnchorStore.MAX_ENTRIES);
        ok("s1b".equals(big.getSnapshot("https://example.com|c1|p1|r1").sessionId));
    }

    static void plans() {
        String fp = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        DshAnchorStore tracked = store();
        tracked.saveSnapshot(KEY_A, fp, "Office PC", "aa_a", "msg_a", false);
        DshAnchorStore.Anchor row = tracked.getSnapshot(KEY_A);
        ok(DshAnchorStore.QUERY_PARKED.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_STATUS, row, fp).code));
        ok(DshAnchorStore.END_PARKED.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_END_SESSION, row, fp).code));
        ok(DshAnchorStore.RESTORE.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH, row, fp).code));
        ok(row == DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH, row, fp).anchor);
        // A rotated credential or another binding must never reuse this anchor.
        ok(DshAnchorStore.STALE.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_STATUS, row, fp + "x").code));
        ok(DshAnchorStore.STALE.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH, row, "").code));
        ok(DshAnchorStore.STALE.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_END_SESSION, row, null).code));
        // An unconfirmed submission locks its own key, and it outranks a stale fingerprint.
        DshAnchorStore pending = store();
        pending.saveSnapshot(KEY_A, fp, "Office PC", "aa_a", "msg_a", true);
        ok(DshAnchorStore.LOCKED.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH,
            pending.getSnapshot(KEY_A), "other").code));
        ok(pending.anyUnknownDelivery());
        // Per-key isolation: one unknown row does not lock the other keys.
        pending.saveSnapshot(KEY_B, config("token", "con_b", "proj_b", "rti_b").targetFingerprint(), "Home PC", "aa_b", "msg_b", false);
        DshAnchorStore.Anchor healthy = pending.getSnapshot(KEY_B);
        ok(!healthy.unknownDelivery);
        ok(DshAnchorStore.RESTORE.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH, healthy,
            config("token", "con_b", "proj_b", "rti_b").targetFingerprint()).code));
        // No row at all: nothing to continue, and never an implicit new session.
        ok(DshAnchorStore.UNTRACKED.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_STATUS, null, fp).code));
        ok(DshAnchorStore.UNTRACKED.equals(DshAnchorStore.planFor(DshAnchorStore.ACTION_DISPATCH, null, fp).code));
        ok(DshAnchorStore.UNTRACKED.equals(DshAnchorStore.planFor("unknown_action", row, fp).code));
        ok(DshAnchorStore.planFor(DshAnchorStore.ACTION_STATUS, null, fp).anchor == null);
    }

    static void accountAndDocumentIsolation() {
        DshAnchorStore s = store();
        String fp = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        s.saveSnapshot(KEY_A, fp, "Office PC", "aa_session_a", "msg_a", false);
        String doc = DshAnchorStore.serializeSnapshot(s);
        ok(doc.contains("\"schemaVersion\":1"));
        ok(!doc.toLowerCase(Locale.ROOT).contains("token")); // digest only, never the credential
        ok(!doc.contains("Bearer"));
        // Same document, another account: it is rejected outright, so a session id can never cross accounts.
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(doc, "user-2", URL); } });
        // The other account's own empty store resolves nothing.
        ok(new DshAnchorStore("user-2", URL).getSnapshot(KEY_A) == null);
        // Another server address: the identity key differs, so nothing resolves.
        DshAnchorStore elsewhere = new DshAnchorStore(USER, "https://other.example.com");
        elsewhere.saveSnapshot("https://other.example.com|con_a|proj_a|rti_a", fp, "Office PC", "aa_other", "msg_o", false);
        ok(elsewhere.getSnapshot(KEY_A) == null);
        reject("FOREIGN_TARGET", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(doc, USER, "https://other.example.com"); } });
        reject("ANCHOR_SCHEMA", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(doc.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), USER, URL); } });
        reject("ANCHOR_SCHEMA", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(doc.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""), USER, URL); } });
        reject("ANCHOR_OWNER", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot("{\"schemaVersion\":1,\"anchors\":[]}", USER, URL); } });
        reject("ANCHOR_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",", USER, URL); } });
        reject("ANCHOR_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"anchors\":[{}]}", USER, URL); } });
        reject("ANCHOR_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"anchors\":{}}", USER, URL); } });
        reject("ANCHOR_DOCUMENT_EMPTY", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot("", USER, URL); } });
        StringBuilder big = new StringBuilder("{\"schemaVersion\":1,\"ownerUserId\":\"user-1\",\"anchors\":[],\"pad\":\"");
        for (int i = 0; i < 25000; i++) big.append('x');
        big.append("\"}");
        reject("ANCHOR_DOCUMENT_SIZE", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(big.toString(), USER, URL); } });
        // One damaged row rejects the whole document: never a partial table.
        final String damaged = doc.replace("\"sessionId\":\"aa_session_a\"", "\"sessionId\":\"has space\"");
        reject("ANCHOR_DOCUMENT_MALFORMED", new Runnable() { public void run() {
            DshAnchorStore.deserializeSnapshot(damaged, USER, URL); } });
        // Duplicate identity keys are refused instead of silently picking one.
        List<DshAnchorStore.Anchor> duplicates = new ArrayList<DshAnchorStore.Anchor>();
        duplicates.add(new DshAnchorStore.Anchor(KEY_A, fp, "a", "s1", "m1", false));
        duplicates.add(new DshAnchorStore.Anchor(KEY_A, fp, "b", "s2", "m2", false));
        reject("ANCHOR_KEY_TAKEN", new Runnable() { public void run() { store().replaceSnapshot(duplicates); } });
        final DshAnchorStore round = store();
        round.saveSnapshot(KEY_A, fp, "a", "s", "m", false);
        try { round.replaceSnapshot(duplicates); ok(false); } catch (IllegalArgumentException expected) { ok(true); }
        ok(round.sizeSnapshot() == 1 && "s".equals(round.getSnapshot(KEY_A).sessionId));
        // Round trip keeps every field.
        DshAnchorStore back = DshAnchorStore.deserializeSnapshot(doc, USER, URL);
        ok(back.sizeSnapshot() == 1);
        DshAnchorStore.Anchor copy = back.getSnapshot(KEY_A);
        ok(fp.equals(copy.targetFingerprint) && "aa_session_a".equals(copy.sessionId)
            && "msg_a".equals(copy.clientMessageId) && !copy.unknownDelivery && "office pc".equals(copy.alias));
        ok(KEY_A.equals(copy.targetKey));
        // An ownerless store serializes to an empty table.
        String homeless = DshAnchorStore.serializeSnapshot(new DshAnchorStore("", URL));
        ok(homeless.contains("\"anchors\":[]") && homeless.contains("\"ownerUserId\":\"\""));
        // replaceSnapshot(null) clears the table in one step.
        back.replaceSnapshot(null);
        ok(back.sizeSnapshot() == 0);
    }

    /** DshClient keeps its own gate: an anchor is only usable by the binding fingerprint it was stored under. */
    static void parkedRowCannotCrossTargets() throws Exception {
        final int[] posts = {0};
        DshClient.Transport transport = (method, url, token, body) -> {
            posts[0]++;
            if (url.endsWith("/runtime/state"))
                return DshClient.mapOf("state", DshClient.mapOf("sessionId", "aa_a", "runtimeId", "rti_a",
                    "connectorId", "con_a", "projectId", "proj_a", "runtime", "dsh", "status", "idle"));
            return DshClient.mapOf("sessionId", "aa_a", "hasMore", false, "items", Arrays.asList(
                DshClient.mapOf("role", "user", "type", "message", "source", DshClient.mapOf("clientMessageId", "msg_a")),
                DshClient.mapOf("role", "assistant", "type", "message", "status", "done", "content", "result")));
        };
        DshAnchorStore parked = store();
        String fp = config("token", "con_a", "proj_a", "rti_a").targetFingerprint();
        parked.saveSnapshot(KEY_A, fp, "Office PC", "aa_a", "msg_a", false);
        DshAnchorStore.Anchor row = parked.getSnapshot(KEY_A);
        // The row's own binding continues its own session.
        DshClient.MapStateStore own = new DshClient.MapStateStore();
        own.saveSession(row.targetFingerprint, row.sessionId, row.clientMessageId);
        Map<String, Object> answer = new DshClient(config("token", "con_a", "proj_a", "rti_a"), own, transport)
            .status(Collections.<String,Object>emptyMap());
        ok(posts[0] == 2 && "aa_a".equals(answer.get("sessionId")) && "completed".equals(answer.get("status")));
        // A different runtime instance cannot read that anchor: DshClient refuses before any request.
        int before = posts[0];
        DshClient.MapStateStore crossed = new DshClient.MapStateStore();
        crossed.saveSession(row.targetFingerprint, row.sessionId, row.clientMessageId);
        Map<String, Object> rejected = new DshClient(config("token", "con_a", "proj_a", "rti_other"), crossed, transport)
            .status(Collections.<String,Object>emptyMap());
        ok("failed".equals(rejected.get("status")) && posts[0] == before);
        ok(!crossed.unknownDelivery());
        // A rotated credential changes the fingerprint, so the parked row is not reused either.
        int again = posts[0];
        DshClient.MapStateStore rotated = new DshClient.MapStateStore();
        rotated.saveSession(row.targetFingerprint, row.sessionId, row.clientMessageId);
        Map<String, Object> stale = new DshClient(config("other-token", "con_a", "proj_a", "rti_a"), rotated, transport)
            .status(Collections.<String,Object>emptyMap());
        ok("failed".equals(stale.get("status")) && posts[0] == again);
        // A row parked while unconfirmed stays locked: it is never treated as a fresh session.
        DshAnchorStore pending = store();
        pending.saveSnapshot(KEY_A, fp, "Office PC", "", "msg_pending", true);
        DshAnchorStore.Anchor half = pending.getSnapshot(KEY_A);
        DshClient.MapStateStore pendingStore = new DshClient.MapStateStore();
        pendingStore.savePending(half.targetFingerprint, half.sessionId, half.clientMessageId);
        Map<String, Object> locked = new DshClient(config("token", "con_a", "proj_a", "rti_a"), pendingStore, transport)
            .status(Collections.<String,Object>emptyMap());
        ok("unknown_delivery".equals(locked.get("status")) && posts[0] == again);
    }

    public static void main(String[] args) throws Exception {
        keyDerivation();
        saveAndKeyIsolation();
        rowValidation();
        plans();
        accountAndDocumentIsolation();
        parkedRowCannotCrossTargets();
        System.out.println("DshAnchorStoreTest: " + checks + " checks passed");
    }
}
