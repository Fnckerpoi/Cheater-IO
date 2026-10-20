package com.turboio.addon;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Queue accounting and overflow belong to one socket generation. */
final class CaptionEventQueue {
    private final AtomicInteger queued = new AtomicInteger();
    private final BooleanSupplier current;
    private final Runnable closeSocket;
    CaptionEventQueue(BooleanSupplier current, Runnable closeSocket) { this.current=current; this.closeSocket=closeSocket; }
    boolean acquire() {
        if (!current.getAsBoolean()) return false;
        if (queued.incrementAndGet() > 32) { queued.decrementAndGet(); closeSocket.run(); return false; }
        return true;
    }
    void release() { queued.decrementAndGet(); }
}
