package com.turboio.addon;

/** Authenticated-reading proximity gate with entry and exit hysteresis and expiry. */
public final class ScadaProximity {
    public interface Listener {
        void onReading(ScadaReading reading, long capturedAtMillis);
        void onExit();
        void markStale();
        void remove();
    }

    public static final int DEFAULT_THRESHOLD_DBM = -70;
    public static final int DEFAULT_HYSTERESIS_DB = 6;
    public static final int ENTRY_PACKETS = 3;
    public static final long STALE_AFTER_MILLIS = 10000;
    public static final long REMOVE_AFTER_MILLIS = 30000;
    private final Listener listener;
    private final int thresholdDbm;
    private final int hysteresisDb;
    private int entryPackets;
    private boolean near;
    private boolean published;
    private boolean staleSent;
    private boolean removedSent;
    private long lastQualifyingNearAtMillis = -1;
    private long lastAcceptedAtMillis = -1;
    private long observedNowMillis;

    public ScadaProximity(int thresholdDbm, int hysteresisDb, Listener listener) {
        if (thresholdDbm < -100 || thresholdDbm > 0) throw new IllegalArgumentException("threshold_range");
        if (hysteresisDb < 1 || hysteresisDb > 30) throw new IllegalArgumentException("hysteresis_range");
        if (listener == null) throw new IllegalArgumentException("listener");
        this.thresholdDbm = thresholdDbm;
        this.hysteresisDb = hysteresisDb;
        this.listener = listener;
    }

    /** Call only after authentication, field validation and replay acceptance. */
    public void accept(ScadaReading reading, long monotonicNowMillis) {
        accept(reading, monotonicNowMillis, monotonicNowMillis);
    }

    public void accept(ScadaReading reading, long capturedAtMillis, long monotonicNowMillis) {
        if (reading == null || capturedAtMillis < 0 || monotonicNowMillis < capturedAtMillis)
            throw new IllegalArgumentException("reading");
        tick(monotonicNowMillis);
        if (capturedAtMillis < lastAcceptedAtMillis) return;
        if (observedNowMillis - capturedAtMillis >= STALE_AFTER_MILLIS) return;
        lastAcceptedAtMillis = capturedAtMillis;
        if (near) {
            if (reading.rssiDbm < thresholdDbm - hysteresisDb) {
                near = false;
                entryPackets = 0;
                listener.onExit();
                return;
            }
            publishNearReading(reading, capturedAtMillis);
            return;
        }
        if (reading.rssiDbm >= thresholdDbm) {
            entryPackets++;
            if (entryPackets >= ENTRY_PACKETS) {
                near = true;
                entryPackets = 0;
                publishNearReading(reading, capturedAtMillis);
            }
        } else {
            entryPackets = 0;
        }
    }

    private void publishNearReading(ScadaReading reading, long monotonicNowMillis) {
        lastQualifyingNearAtMillis = monotonicNowMillis;
        staleSent = false;
        removedSent = false;
        published = true;
        listener.onReading(reading, monotonicNowMillis);
    }

    /** Expiry is based on the last qualifying near reading, never merely a valid advertisement. */
    public void tick(long monotonicNowMillis) {
        observedNowMillis = Math.max(observedNowMillis, monotonicNowMillis);
        if (!near && lastAcceptedAtMillis >= 0 && observedNowMillis - lastAcceptedAtMillis >= STALE_AFTER_MILLIS)
            entryPackets = 0;
        if (!published || lastQualifyingNearAtMillis < 0) return;
        long quietMillis = observedNowMillis - lastQualifyingNearAtMillis;
        if (quietMillis >= STALE_AFTER_MILLIS && !staleSent) {
            staleSent = true;
            near = false;
            entryPackets = 0;
            listener.markStale();
        }
        if (quietMillis >= REMOVE_AFTER_MILLIS && !removedSent) {
            removedSent = true;
            near = false;
            published = false;
            entryPackets = 0;
            listener.remove();
        }
    }

    public boolean isNear() { return near; }
    public boolean isStale() { return staleSent; }
    public boolean isPublished() { return published; }
    public int entryPackets() { return entryPackets; }
    public long lastQualifyingNearAtMillis() { return lastQualifyingNearAtMillis; }
}
