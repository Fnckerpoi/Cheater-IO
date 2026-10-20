package com.turboio.addon;

import java.util.Map;

/** Reads the durable fingerprint ledger without turning malformed history into first use. */
public final class ScadaReplayHistory {
    public static String key(String fingerprint) { return "counter_" + fingerprint; }
    public static String marker(String fingerprint) { return "initialized_" + fingerprint; }
    private static long counter(Object value) {
        if (!(value instanceof Long)) throw new IllegalArgumentException("corrupt_replay_counter");
        long n = (Long)value;
        if (n < -1 || n > ScadaPacketCodec.MAX_COUNTER) throw new IllegalArgumentException("corrupt_replay_counter");
        return n;
    }
    public static long load(Map<String, ?> saved, String fingerprint) {
        long result = -1;
        for (Map.Entry<String, ?> e : saved.entrySet()) {
            if (e.getKey().startsWith("counter_")) counter(e.getValue());
            if (e.getKey().startsWith("initialized_") && (!Boolean.TRUE.equals(e.getValue()) || !saved.containsKey(key(e.getKey().substring(12)))))
                throw new IllegalArgumentException("missing_initialized_counter");
        }
        if (saved.containsKey(key(fingerprint))) result = counter(saved.get(key(fingerprint)));
        // A legacy counter without reliable identity is conservatively applied to every credential.
        if (saved.containsKey("last_counter")) {
            long legacy = counter(saved.get("last_counter"));
            Object old = saved.get("config_fingerprint");
            if (!(old instanceof String) || !((String)old).matches("[A-Fa-f0-9]{64}") || fingerprint.equalsIgnoreCase((String)old))
                result = Math.max(result, legacy);
        } else if (saved.containsKey("config_fingerprint")) {
            Object old = saved.get("config_fingerprint");
            if (!(old instanceof String) || !((String)old).matches("[A-Fa-f0-9]{64}") || !saved.containsKey(key((String)old)))
                throw new IllegalArgumentException("corrupt_replay_identity");
        }
        return result;
    }
}