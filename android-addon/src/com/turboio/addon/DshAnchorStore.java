package com.turboio.addon;

import java.util.*;

/**
 * Per target local session anchors: stage two of
 * docs/AA_VOICE_RUNTIME_ALIAS_ROUTING.md (decision D5 and section 6).
 *
 * <p>Pure Java: no Android types and no I/O. The durable wrapper (ToolClient) writes the whole document in
 * ONE commit, so a key's session anchor and its pending triple can never be observed half applied — the same
 * invariant DshClient.StateStore documents for the single active slot.
 *
 * <p>Key space: a row is keyed by the target identity (baseUrl|connectorId|projectId|runtimeId) and carries the
 * full binding fingerprint (identity plus a digest of the account credential, i.e. DshClient.Config
 * .targetFingerprint()). A rotated credential changes every fingerprint at once, so parked rows turn stale
 * instead of continuing another credential's session. An alias rename keeps the identity key, so the anchor of
 * one runtime instance cannot move to another instance.
 *
 * <p>Identity boundary: every row belongs to {@code ownerUserId + baseUrl}. A logout, account switch or server
 * address change yields an EMPTY store: a foreign owner, unknown schema version or damaged document rejects the
 * whole document instead of applying part of it, so another account can never inherit a session id.
 *
 * <p>An anchor is local tracking metadata only. It is never authorization: it cannot widen a model or a
 * permission, and it is only usable by the exact binding it was parked under.
 */
public final class DshAnchorStore {
    public static final int SCHEMA_VERSION = 1;
    /** One anchor row per configured alias at most; keeps the durable document inside the tool budget. */
    public static final int MAX_ENTRIES = DshAliasRegistry.MAX_ENTRIES;
    private static final char SEPARATOR = '|';
    private static final int MAX_KEY_LENGTH = 700;
    private static final int MAX_FINGERPRINT_LENGTH = 900;
    private static final int MAX_ID_LENGTH = 200;
    private static final int MAX_TEXT_BYTES = 24576;

    /** Actions the host may take with the anchor of a target that is not the active slot. */
    public static final String ACTION_STATUS = "status", ACTION_END_SESSION = "end_session", ACTION_DISPATCH = "dispatch";
    /** Plan codes: what one resolved voice target may do with its parked row. */
    public static final String UNTRACKED = "UNTRACKED", LOCKED = "LOCKED", STALE = "STALE",
        QUERY_PARKED = "QUERY_PARKED", END_PARKED = "END_PARKED", RESTORE = "RESTORE";

    /** One row: the local tracking triple of one target plus the fingerprint it belongs to. */
    public static final class Anchor {
        public final String targetKey, targetFingerprint, alias, sessionId, clientMessageId;
        public final boolean unknownDelivery;

        public Anchor(String targetKey, String targetFingerprint, String alias, String sessionId,
                      String clientMessageId, boolean unknownDelivery) {
            this.targetKey = cleanOpaque(targetKey, MAX_KEY_LENGTH);
            this.targetFingerprint = cleanOpaque(targetFingerprint, MAX_FINGERPRINT_LENGTH);
            this.alias = displayOf(alias);
            this.sessionId = cleanOpaque(sessionId, MAX_ID_LENGTH);
            this.clientMessageId = cleanOpaque(clientMessageId, MAX_ID_LENGTH);
            this.unknownDelivery = unknownDelivery;
        }

        /** True when nothing is tracked, so the row is dropped instead of stored hollow. */
        public boolean empty() {
            return sessionId.isEmpty() && clientMessageId.isEmpty() && !unknownDelivery;
        }
    }

    /** Decision for one action against one parked row. */
    public static final class Plan {
        public final String code;
        public final Anchor anchor;
        Plan(String code, Anchor anchor) { this.code = code; this.anchor = anchor; }
    }

    private final String ownerUserId, baseUrl;
    private final Map<String, Anchor> byKey = new LinkedHashMap<>();

    public DshAnchorStore(String ownerUserId, String baseUrl) {
        this.ownerUserId = cleanOpaque(ownerUserId, MAX_ID_LENGTH);
        String normalized = DshClient.normalizeBaseUrl(baseUrl);
        this.baseUrl = normalized == null ? "" : normalized;
    }

    public String ownerUserId() { return ownerUserId; }
    public String baseUrl() { return baseUrl; }
    /** Without an owner and a valid server address the store can never hand out an anchor. */
    public boolean usable() { return !ownerUserId.isEmpty() && !baseUrl.isEmpty(); }

    public List<Anchor> listSnapshot() { return Collections.unmodifiableList(new ArrayList<>(byKey.values())); }
    public int sizeSnapshot() { return byKey.size(); }

    /** Row of one target identity; null when that target has no local tracking. */
    public Anchor getSnapshot(String targetKey) {
        String key = cleanOpaque(targetKey, MAX_KEY_LENGTH);
        return key.isEmpty() ? null : byKey.get(key);
    }

    /** True when any key still has an unconfirmed submission; the host refuses every action then. */
    public boolean anyUnknownDelivery() {
        for (Anchor anchor : byKey.values()) if (anchor.unknownDelivery) return true;
        return false;
    }

    /** Keys that hold a parked row; used by the AA settings card, never for routing. */
    public List<String> trackingKeysSnapshot() {
        List<String> out = new ArrayList<>();
        for (Anchor anchor : byKey.values()) if (!anchor.empty()) out.add(anchor.targetKey);
        return out;
    }

    /**
     * Writes one row. An all-empty state removes the key, so "no anchor" and "unknown with no anchor yet" stay
     * distinguishable and the table cannot accumulate hollow rows.
     */
    public List<Anchor> saveSnapshot(String targetKey, String targetFingerprint, String alias, String sessionId,
                                     String clientMessageId, boolean unknownDelivery) {
        if (!usable()) throw new IllegalArgumentException("FOREIGN_TARGET");
        String key = requireKey(targetKey);
        requireOrigin(key);
        Anchor next = new Anchor(key, requireFingerprint(targetFingerprint), alias,
            requirePart(sessionId, "SESSION"), requirePart(clientMessageId, "MESSAGE"), unknownDelivery);
        // A pending flag without a message id cannot be attributed, so it is never stored.
        if (next.unknownDelivery && next.clientMessageId.isEmpty()) throw new IllegalArgumentException("ANCHOR_MESSAGE_REQUIRED");
        if (next.empty()) { byKey.remove(key); return listSnapshot(); }
        if (!byKey.containsKey(key) && byKey.size() >= MAX_ENTRIES) throw new IllegalArgumentException("ANCHOR_LIMIT");
        byKey.put(key, next);
        return listSnapshot();
    }

    /** Idempotent drop: false when the key was not tracked. Removing an anchor never touches a remote session. */
    public boolean dropSnapshot(String targetKey) {
        String key = cleanOpaque(targetKey, MAX_KEY_LENGTH);
        if (key.isEmpty() || !byKey.containsKey(key)) return false;
        byKey.remove(key);
        return true;
    }

    /** Replaces the whole table atomically; a rejected row leaves the previous table untouched. */
    public List<Anchor> replaceSnapshot(Iterable<Anchor> anchors) {
        if (!usable()) throw new IllegalArgumentException("FOREIGN_TARGET");
        Map<String, Anchor> next = new LinkedHashMap<>();
        if (anchors != null) {
            for (Anchor anchor : anchors) {
                if (anchor == null) throw new IllegalArgumentException("ANCHOR_REQUIRED");
                String key = requireKey(anchor.targetKey);
                requireOrigin(key);
                Anchor row = new Anchor(key, requireFingerprint(anchor.targetFingerprint), anchor.alias,
                    requirePart(anchor.sessionId, "SESSION"), requirePart(anchor.clientMessageId, "MESSAGE"),
                    anchor.unknownDelivery);
                if (row.unknownDelivery && row.clientMessageId.isEmpty()) throw new IllegalArgumentException("ANCHOR_MESSAGE_REQUIRED");
                if (row.empty()) continue;
                if (next.containsKey(key)) throw new IllegalArgumentException("ANCHOR_KEY_TAKEN");
                if (next.size() >= MAX_ENTRIES) throw new IllegalArgumentException("ANCHOR_LIMIT");
                next.put(key, row);
            }
        }
        byKey.clear();
        byKey.putAll(next);
        return listSnapshot();
    }

    /**
     * Deterministic plan for a resolved voice target whose identity is NOT the active slot. A parked row is only
     * reusable by the exact fingerprint it was stored under; an unattributable pending flag keeps that key
     * locked, and a stale fingerprint is refused instead of silently continuing another credential's session.
     */
    public static Plan planFor(String action, Anchor parked, String currentFingerprint) {
        if (parked == null) return new Plan(UNTRACKED, null);
        if (parked.unknownDelivery) return new Plan(LOCKED, parked);
        String current = cleanOpaque(currentFingerprint, MAX_FINGERPRINT_LENGTH);
        if (parked.targetFingerprint.isEmpty() || current.isEmpty() || !parked.targetFingerprint.equals(current))
            return new Plan(STALE, parked);
        if (ACTION_STATUS.equals(action)) return new Plan(QUERY_PARKED, parked);
        if (ACTION_END_SESSION.equals(action)) return new Plan(END_PARKED, parked);
        if (ACTION_DISPATCH.equals(action)) return new Plan(RESTORE, parked);
        return new Plan(UNTRACKED, null);
    }

    /** Identity key of one binding: normalized https origin plus the three ids, or "" when incomplete. */
    public static String targetKey(String baseUrl, String connectorId, String projectId, String runtimeId) {
        String origin = DshClient.normalizeBaseUrl(baseUrl);
        String connector = cleanOpaque(connectorId, MAX_ID_LENGTH);
        String project = cleanOpaque(projectId, MAX_ID_LENGTH);
        String runtime = cleanOpaque(runtimeId, MAX_ID_LENGTH);
        if (origin == null || origin.isEmpty() || connector.isEmpty() || project.isEmpty() || runtime.isEmpty()) return "";
        return origin + SEPARATOR + connector + SEPARATOR + project + SEPARATOR + runtime;
    }

    /** Identity part of a binding fingerprint: everything before the credential digest. */
    public static String keyOfFingerprint(String fingerprint) {
        String value = cleanOpaque(fingerprint, MAX_FINGERPRINT_LENGTH);
        int mark = value.lastIndexOf(SEPARATOR);
        if (mark <= 0 || mark + 1 >= value.length()) return "";
        String key = value.substring(0, mark);
        return countSeparators(key) == 3 ? key : "";
    }

    private static String requireKey(String targetKey) {
        String key = cleanOpaque(targetKey, MAX_KEY_LENGTH);
        if (key.isEmpty()) throw new IllegalArgumentException("ANCHOR_KEY_REQUIRED");
        if (countSeparators(key) != 3) throw new IllegalArgumentException("ANCHOR_KEY_INVALID");
        return key;
    }

    private static int countSeparators(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == SEPARATOR) total++;
        return total;
    }

    /**
     * A key must be an identity of THIS server address. Without this, a document copied from another host
     * would carry rows that could later be handed to a different deployment, so the whole table is refused.
     */
    private void requireOrigin(String key) {
        if (!key.startsWith(baseUrl + SEPARATOR)) throw new IllegalArgumentException("FOREIGN_TARGET");
    }

    private static String requireFingerprint(String value) {
        String clean = cleanOpaque(value, MAX_FINGERPRINT_LENGTH);
        if (clean.isEmpty()) throw new IllegalArgumentException("ANCHOR_FINGERPRINT");
        return clean;
    }

    /** A non-empty but unusable session or message id is a bug, not an absence: refuse it. */
    private static String requirePart(String value, String what) {
        if (value == null) return "";
        String trimmed = value.trim();
        String clean = cleanOpaque(trimmed, MAX_ID_LENGTH);
        if (clean.length() != trimmed.length()) throw new IllegalArgumentException("ANCHOR_" + what + "_INVALID");
        return clean;
    }

    /** Opaque ids and digests: bounded, no control characters, no whitespace. */
    private static String cleanOpaque(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) return "";
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 0x20 || ch == 0x7f || Character.isWhitespace(ch)) return "";
        }
        return trimmed;
    }

    /** Display label is advisory only: normalized, bounded, never a routing key. */
    private static String displayOf(String alias) {
        String normalized = DshAliasRegistry.normalizeAlias(alias);
        return normalized.length() > DshAliasRegistry.MAX_ALIAS_CODE_POINTS ? "" : normalized;
    }

    /** Durable form: local ids and digests only, never tokens, secrets or response bodies. */
    public static String serializeSnapshot(DshAnchorStore store) {
        List<Object> rows = new ArrayList<>();
        if (store != null) for (Anchor anchor : store.listSnapshot()) rows.add(DshClient.mapOf(
            "targetKey", anchor.targetKey, "targetFingerprint", anchor.targetFingerprint, "alias", anchor.alias,
            "sessionId", anchor.sessionId, "clientMessageId", anchor.clientMessageId,
            "unknownDelivery", Boolean.valueOf(anchor.unknownDelivery)));
        return DshClient.stringifyJson(DshClient.mapOf(
            "schemaVersion", SCHEMA_VERSION,
            "ownerUserId", store == null ? "" : store.ownerUserId(),
            "anchors", rows));
    }

    /** Strict parse: a foreign owner, an unknown schema version or a malformed row rejects the document. */
    @SuppressWarnings("unchecked")
    public static DshAnchorStore deserializeSnapshot(String text, String expectedOwner, String expectedBaseUrl) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("ANCHOR_DOCUMENT_EMPTY");
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES)
            throw new IllegalArgumentException("ANCHOR_DOCUMENT_SIZE");
        Map<String, Object> root;
        try { root = DshClient.parseJsonObject(text); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED"); }
        Object version = root.get("schemaVersion");
        if (!(version instanceof Integer) || ((Integer) version).intValue() != SCHEMA_VERSION)
            throw new IllegalArgumentException("ANCHOR_SCHEMA");
        Object owner = root.get("ownerUserId");
        if (!(owner instanceof String) || cleanOpaque((String) owner, MAX_ID_LENGTH).isEmpty())
            throw new IllegalArgumentException("ANCHOR_OWNER");
        DshAnchorStore store = new DshAnchorStore(expectedOwner, expectedBaseUrl);
        if (!store.usable()) throw new IllegalArgumentException("FOREIGN_TARGET");
        if (!store.ownerUserId.equals(cleanOpaque((String) owner, MAX_ID_LENGTH))) throw new IllegalArgumentException("FOREIGN_TARGET");
        Object raw = root.get("anchors");
        if (!(raw instanceof List)) throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED");
        List<Anchor> rows = new ArrayList<>();
        for (Object row : (List<?>) raw) {
            if (!(row instanceof Map)) throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED");
            Map<String, Object> map = (Map<String, Object>) row;
            Object unknown = map.get("unknownDelivery");
            if (!(unknown instanceof Boolean)) throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED");
            // Strict read: a present value that is not a clean opaque id rejects the whole document instead of
            // silently degrading into a hollow row that would then look like "no anchor".
            rows.add(new Anchor(strictOf(map, "targetKey"), strictOf(map, "targetFingerprint"), stringOf(map, "alias"),
                strictOf(map, "sessionId"), strictOf(map, "clientMessageId"), ((Boolean) unknown).booleanValue()));
        }
        store.replaceSnapshot(rows);
        return store;
    }

    private static String stringOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof String ? (String) value : "";
    }

    /** Absence is allowed (it means "not tracked"), but a present malformed value fails the document closed. */
    private static String strictOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return "";
        if (!(value instanceof String)) throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED");
        String trimmed = ((String) value).trim();
        if (trimmed.isEmpty()) return "";
        if (cleanOpaque(trimmed, MAX_TEXT_BYTES).isEmpty()) throw new IllegalArgumentException("ANCHOR_DOCUMENT_MALFORMED");
        return trimmed;
    }
}
