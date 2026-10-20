package com.turboio.addon;

/** Monotonic unsigned 32-bit counter guard for the single provisioned sensor. */
public final class ScadaReplayGuard {
    private long lastCounter;

    public ScadaReplayGuard(long persistedCounter) {
        if (persistedCounter < -1 || persistedCounter > ScadaPacketCodec.MAX_COUNTER)
            throw new IllegalArgumentException("persisted_counter");
        lastCounter = persistedCounter;
    }

    public boolean isNew(long counter) {
        return counter >= 0 && counter <= ScadaPacketCodec.MAX_COUNTER && counter > lastCounter;
    }

    public boolean accept(long counter) {
        if (!isNew(counter)) return false;
        lastCounter = counter;
        return true;
    }

    public long lastCounter() { return lastCounter; }
}
