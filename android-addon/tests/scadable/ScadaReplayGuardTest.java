package com.turboio.addon;

/** Monotonic unsigned counter checks, independent of Android runtime. */
public final class ScadaReplayGuardTest {
    public static void main(String[] args) {
        ScadaReplayGuard guard = new ScadaReplayGuard(-1L);
        check(guard.accept(0L), "first counter accepted");
        check(!guard.accept(0L), "duplicate rejected");
        check(guard.accept(0xffffffffL), "uint32 maximum accepted");
        check(!guard.accept(1L), "wraparound rejected");
        check(guard.lastCounter() == 0xffffffffL, "counter never rolls back");
        check(!new ScadaReplayGuard(100L).isNew(99L), "persisted counter rejects older packet");
        System.out.println("ScadaReplayGuardTest passed");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
