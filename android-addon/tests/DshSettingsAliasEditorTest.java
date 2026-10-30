package com.turboio.addon;

import java.util.*;

/**
 * The per-alias voice target editor: each alias carries its OWN connector/project/runtime/model/permission,
 * and the save gate is fail-closed against the live account-scoped directory. Plain main() style, matching
 * the existing android-addon/tests harness (JVM only: DshSettings.AliasTarget and DshAliasRegistry have no Android types).
 */
public final class DshSettingsAliasEditorTest {
    static int checks;
    static void ok(boolean value) {
        checks++;
        if (!value) throw new AssertionError("AliasEditor check " + checks);
    }

    static final String URL = "https://example.com";
    static final long EPOCH = 7L, TICKET = 3L;
    static final String MODEL_A = "dsh:model:WyJtYWdwaWUiLCJncm91cC9zbWFydC1zcGxpdCIsbnVsbF0";
    static final String MODEL_B = "dsh:model:WyJwYyIsImFscGhhIixudWxsXQ";
    static final String PERM_A = "dsh:permission:cmVhZC1vbmx5";
    static final String PERM_B = "dsh:permission:d29ya3NwYWNlLXdyaXRl";

    static DshDirectory.Option option(String id) { return new DshDirectory.Option(id, id, "live"); }

    /** A draft whose five levels are all present in the live rows of this account and generation. */
    static DshSettings.AliasTarget verifiedDraft() {
        DshSettings.AliasTarget draft = new DshSettings.AliasTarget();
        draft.connectors = Arrays.asList(option("c1"), option("c2"));
        draft.projects = Arrays.asList(option("p1"), option("p2"));
        draft.runtimes = Arrays.asList(option("r1"), option("r2"), option("r3"));
        // r3 is a visible card that is NOT a usable dsh instance: it must never be savable.
        draft.usable = Arrays.asList(option("r1"), option("r2"));
        draft.models = Arrays.asList(option(MODEL_A), option(MODEL_B));
        draft.permissions = Arrays.asList(option(PERM_A), option(PERM_B));
        draft.connector = "c1"; draft.project = "p1"; draft.runtime = "r1";
        draft.model = MODEL_A; draft.permission = PERM_A;
        draft.loadedAccount = EPOCH; draft.loadedTicket = TICKET;
        draft.catalogAccount = EPOCH; draft.catalogTicket = TICKET; draft.catalogRuntime = "r1";
        return draft;
    }

    /** Every unverified level must refuse on its own, so a save can never borrow the global binding. */
    static void failClosed() {
        ok(DshSettings.AliasTarget.OK.equals(verifiedDraft().refusal(EPOCH, TICKET, URL)));
        ok("FOREIGN_TARGET".equals(verifiedDraft().refusal(EPOCH, TICKET, "")));
        ok("FOREIGN_TARGET".equals(verifiedDraft().refusal(EPOCH, TICKET, "http://example.com")));
        DshSettings.AliasTarget stale = verifiedDraft();
        stale.loadedAccount = EPOCH - 1;
        ok("STALE_AUTH".equals(stale.refusal(EPOCH, TICKET, URL)));
        ok("STALE_DIRECTORY".equals(verifiedDraft().refusal(EPOCH, TICKET + 1, URL)));
        ok("STALE_AUTH".equals(verifiedDraft().refusal(EPOCH + 1, TICKET, URL)));
        // A draft that never loaded rows is unverified at every level, in parent-first order.
        ok("STALE_AUTH".equals(new DshSettings.AliasTarget().refusal(EPOCH, TICKET, URL)));
        DshSettings.AliasTarget fresh = verifiedDraft();
        fresh.loadedAccount = EPOCH; fresh.connector = "";
        ok("CONNECTOR_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.connector = "c-missing";
        ok("CONNECTOR_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.project = "p-missing";
        ok("PROJECT_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.project = "";
        ok("PROJECT_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.runtime = "r-missing";
        ok("RUNTIME_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.runtime = "r3"; fresh.catalogRuntime = "r3";
        ok("RUNTIME_UNAVAILABLE".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.model = "";
        ok("MODEL_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.model = "dsh:model:bm90LWxpdmU";
        ok("MODEL_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.permission = "dsh:permission:bm90LWxpdmU";
        ok("PERMISSION_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        // Catalog rows of another runtime instance, or of another directory generation, never count.
        fresh = verifiedDraft(); fresh.catalogRuntime = "r2";
        ok("STALE_CATALOG".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.catalogTicket = TICKET + 1;
        ok("STALE_CATALOG".equals(fresh.refusal(EPOCH, TICKET, URL)));
        fresh = verifiedDraft(); fresh.catalogAccount = -1;
        ok("STALE_CATALOG".equals(fresh.refusal(EPOCH, TICKET, URL)));
        // A parent change must wipe descendants, otherwise a stale child id would stay "verified".
        fresh = verifiedDraft(); fresh.dropProjectAndRuntimes();
        ok("PROJECT_UNVERIFIED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        ok(fresh.projects.isEmpty() && fresh.runtimes.isEmpty() && fresh.usable.isEmpty() && fresh.models.isEmpty());
        fresh = verifiedDraft(); fresh.dropCatalog();
        ok("STALE_CATALOG".equals(fresh.refusal(EPOCH, TICKET, URL)));
        // Garbage ids/selections that pass directory membership by accident still hit the identity gate.
        fresh = verifiedDraft();
        fresh.projects = Arrays.asList(new DshDirectory.Option("bad id", "bad", "")); fresh.project = "bad id";
        ok("IDENTITY_REQUIRED".equals(fresh.refusal(EPOCH, TICKET, URL)));
        // The refusal strings reach the user; none may be blank or silently mean "save anyway".
        ok(!DshSettings.AliasTarget.message("RUNTIME_UNAVAILABLE").isEmpty());
        ok(!DshSettings.AliasTarget.message("STALE_CATALOG").isEmpty());
        ok(!DshSettings.AliasTarget.message("UNEXPECTED").isEmpty());
        // ids() order is the contract the durable write depends on.
        ok(verifiedDraft().ids().equals(Arrays.asList("c1", "p1", "r1", MODEL_A, PERM_A)));
    }

    /** Two aliases on two runtime instances keep two independent targets, one alias per instance. */
    static void independentTargets() {
        DshAliasRegistry table = new DshAliasRegistry("user-1", URL);
        table.upsertSnapshot("办公室电脑", URL, "c1", "r1", "p1", MODEL_A, PERM_A);
        table.upsertSnapshot("家里电脑", URL, "c2", "r2", "p2", MODEL_B, PERM_B);
        List<DshAliasRegistry.AliasEntry> rows = table.listSnapshot();
        ok(rows.size() == 2);
        DshAliasRegistry.AliasEntry office = DshSettings.AliasTarget.snapshotOf(table, "r1");
        DshAliasRegistry.AliasEntry home = DshSettings.AliasTarget.snapshotOf(table, "r2");
        ok(office != null && "p1".equals(office.projectId) && MODEL_A.equals(office.model) && PERM_A.equals(office.permission));
        ok(home != null && "p2".equals(home.projectId) && MODEL_B.equals(home.model) && PERM_B.equals(home.permission));
        // One alias per runtime instance, and the identity of the other entry survives the round trip.
        ok(DshSettings.AliasTarget.snapshotOf(table, "r3") == null);
        ok(DshSettings.AliasTarget.snapshotOf(table, "r1").aliasKey.equals(DshAliasRegistry.normalizeAlias("办公室电脑")));
        DshAliasRegistry reread = DshAliasRegistry.deserializeSnapshot(
            DshAliasRegistry.serializeSnapshot(table), "user-1", URL);
        ok("p2".equals(DshSettings.AliasTarget.snapshotOf(reread, "r2").projectId)
            && MODEL_B.equals(DshSettings.AliasTarget.snapshotOf(reread, "r2").model)
            && PERM_B.equals(DshSettings.AliasTarget.snapshotOf(reread, "r2").permission));
        // Editing only renames the entry it owns: the other alias keeps its own target.
        reread.upsertSnapshot("家里电脑改名", URL, "c2", "r2", "p2", MODEL_B, PERM_B);
        ok("p1".equals(DshSettings.AliasTarget.snapshotOf(reread, "r1").projectId)
            && DshSettings.AliasTarget.snapshotOf(reread, "r2") != null && reread.sizeSnapshot() == 2);
        // Another account or server address can never resolve these aliases.
        ok(DshSettings.AliasTarget.snapshotOf(new DshAliasRegistry("user-2", URL), "r1") == null);
        ok(DshSettings.AliasTarget.snapshotOf(new DshAliasRegistry("user-1", "https://other.example.com"), "r1") == null);
    }

    /** Pointing a NEW alias at an instance another alias already owns is refused, never silently replaced. */
    static void oneAliasPerRuntime() {
        DshAliasRegistry table = new DshAliasRegistry("user-1", URL);
        table.upsertSnapshot("办公室电脑", URL, "c1", "r1", "p1", MODEL_A, PERM_A);
        table.upsertSnapshot("家里电脑", URL, "c2", "r2", "p2", MODEL_B, PERM_B);
        // A fresh alias aimed at r1 collides with the entry that already owns r1.
        ok("办公室电脑".equals(DshSettings.AliasTarget.ownerConflict(table, "办公室", "r1", "")));
        // Same alias key renames in place; the entry being edited may keep its own instance.
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "办公室电脑", "r1", "")));
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "办公室电脑", "r1", DshSettings.AliasTarget.snapshotOf(table, "r1").aliasKey)));
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "新名字", "r2", DshSettings.AliasTarget.snapshotOf(table, "r2").aliasKey)));
        // Blank/garbage alias text defers to upsertSnapshot, which reports the alias itself.
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "  ", "r1", "")));
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "", "r1", "")));
        // A runtime instance nobody owns is free, and a null table cannot claim an owner.
        ok("".equals(DshSettings.AliasTarget.ownerConflict(table, "第三台", "r9", "")));
        ok("".equals(DshSettings.AliasTarget.ownerConflict(null, "办公室", "r1", "")));
        ok(DshSettings.AliasTarget.snapshotOf(null, "r1") == null);
        // The registry itself still refuses to move a live alias key onto another instance.
        DshAliasRegistry next = new DshAliasRegistry("user-1", URL);
        next.upsertSnapshot("办公室电脑", URL, "c1", "r1", "p1", MODEL_A, PERM_A);
        try {
            next.upsertSnapshot("办公室电脑", URL, "c1", "r2", "p1", MODEL_A, PERM_A);
            ok(false);
        } catch (IllegalArgumentException refused) { ok("ALIAS_TAKEN".equals(refused.getMessage())); }
        ok(next.sizeSnapshot() == 1 && "r1".equals(next.listSnapshot().get(0).runtimeId));
    }

    public static void main(String[] args) {
        failClosed();
        independentTargets();
        oneAliasPerRuntime();
        System.out.println("DshSettingsAliasEditorTest: " + checks + " passed");
    }
}
