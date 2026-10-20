package com.turboio.addon;

/** BLE timestamps and scan boundaries share Android's elapsed realtime nanosecond clock. */
public final class ScadaSampleTime {
    public static long captureMillis(long capturedNanos) { return capturedNanos / 1000000L; }
    public static boolean fresh(long captured, long now, long scanStart, long last) {
        return captured >= scanStart && captured >= 0 && captured >= last
                && captured <= now && now - captured < 10000000000L;
    }
}