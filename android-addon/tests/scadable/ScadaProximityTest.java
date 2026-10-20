package com.turboio.addon;

/** Focused expiry regression tests that do not need Android classes. */
public final class ScadaProximityTest {
    private static final class Capture implements ScadaProximity.Listener {
        int readings;
        int exits;
        int stale;
        int removed;
        long capturedAt;
        @Override public void onReading(ScadaReading reading, long capturedAtMillis) { readings++; capturedAt = capturedAtMillis; }
        @Override public void onExit() { exits++; }
        @Override public void markStale() { stale++; }
        @Override public void remove() { removed++; }
    }

    public static void main(String[] args) {
        continuousBelowExitPacketsDoNotKeepPublishedCardFresh();
        weakPacketsWithoutEntryNeverRemoveCard();
        validNearReadingsRefreshTimeout();
        entryRequiresThreeStrongPacketsAndExitIsImmediate();
        staleBoundaryAndOldQueuedPackets();
        delayedFreshPacketKeepsItsCaptureTime();
        System.out.println("ScadaProximityTest passed");
    }

    private static void continuousBelowExitPacketsDoNotKeepPublishedCardFresh() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        proximity.accept(reading(1, -68), 1000);
        proximity.accept(reading(2, -67), 1100);
        proximity.accept(reading(3, -66), 1200);
        check(capture.readings == 1 && proximity.isPublished(), "third strong packet publishes card");

        proximity.accept(reading(4, -77), 1300);
        check(!proximity.isNear(), "RSSI below exit threshold leaves near state");
        check(capture.exits == 1 && capture.stale == 0 && capture.removed == 0,
                "exit clears captions immediately without changing card expiry");
        for (int i = 0; i < 32; i++) {
            long now = 2000L + i * 1000L;
            proximity.accept(reading(5 + i, -80), now);
        }
        check(capture.stale == 1, "weak authenticated packets do not prevent stale transition");
        check(capture.removed == 1, "weak authenticated packets do not prevent card removal");
        check(!proximity.isPublished(), "removed card is no longer published");
    }

    private static void weakPacketsWithoutEntryNeverRemoveCard() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        for (int i = 0; i < 35; i++) proximity.accept(reading(i, -90), i * 1000L);
        check(capture.readings == 0, "weak sensor never enters");
        check(capture.stale == 0 && capture.removed == 0, "no published card receives expiry callbacks");
    }

    private static void validNearReadingsRefreshTimeout() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        proximity.accept(reading(1, -68), 1000);
        proximity.accept(reading(2, -67), 1100);
        proximity.accept(reading(3, -66), 1200);
        proximity.tick(11199);
        check(capture.stale == 0, "fresh near reading is not stale");
        proximity.accept(reading(4, -72), 11199); // inside the exit hysteresis band
        check(capture.readings == 2, "hysteresis keeps the card updated");
        proximity.tick(21198);
        check(capture.stale == 0, "near reading refreshes expiry");
        proximity.tick(21199);
        check(capture.stale == 1, "stale at ten seconds without qualifying near readings");
    }

    private static void entryRequiresThreeStrongPacketsAndExitIsImmediate() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        proximity.accept(reading(1, -70), 100);
        proximity.accept(reading(2, -69), 200);
        check(capture.readings == 0 && proximity.entryPackets() == 2, "two packets cannot enter");
        proximity.accept(reading(3, -71), 300);
        check(proximity.entryPackets() == 0, "weak sample resets entry streak");
        proximity.accept(reading(4, -70), 400);
        proximity.accept(reading(5, -70), 500);
        proximity.accept(reading(6, -70), 600);
        proximity.accept(reading(7, -76), 700);
        check(capture.readings == 2 && capture.exits == 0, "exact exit threshold stays near");
        proximity.accept(reading(8, -77), 800);
        check(capture.exits == 1 && !proximity.isNear(), "one packet below exit threshold exits");
        for (int index = 0; index < 4; index++) proximity.accept(reading(9 + index, -60), 750);
        check(capture.readings == 2 && proximity.entryPackets() == 0,
                "queued near packets older than a newer exit cannot resurrect captions");
        proximity.accept(reading(13, -80), 900);
        check(capture.exits == 1, "far packets do not repeat exit");
    }

    private static void staleBoundaryAndOldQueuedPackets() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        proximity.accept(reading(1, -60), 100);
        proximity.accept(reading(2, -60), 200);
        proximity.accept(reading(3, -60), 300);
        proximity.tick(10299);
        check(capture.stale == 0, "9999 ms remains fresh");
        proximity.tick(10300);
        check(capture.stale == 1 && capture.removed == 0, "10000 ms stales without card removal");
        proximity.accept(reading(4, -60), 299, 10300);
        proximity.accept(reading(5, -60), 300, 10300);
        check(capture.readings == 1 && proximity.isStale(), "old or expired near packets cannot revive stale data");
        proximity.tick(30300);
        check(capture.removed == 1, "card still removes after 30 seconds");
        for (int index = 0; index < 3; index++) proximity.accept(reading(6 + index, -60), 301);
        check(capture.readings == 1 && !proximity.isPublished(), "clock cannot move backward to revive removed data");
        Capture queued = new Capture();
        ScadaProximity unseen = new ScadaProximity(-70, 6, queued);
        for (int index = 0; index < 3; index++) unseen.accept(reading(index, -60), 100, 10100);
        check(queued.readings == 0 && unseen.entryPackets() == 0, "expired packets cannot create an initial entry");
        unseen.accept(reading(10, -60), 10200, 10200);
        unseen.accept(reading(11, -60), 10300, 10300);
        unseen.tick(20300);
        unseen.accept(reading(12, -60), 20300, 20300);
        check(queued.readings == 0 && unseen.entryPackets() == 1, "expired entry streak cannot combine with a new packet");
    }

    private static void delayedFreshPacketKeepsItsCaptureTime() {
        Capture capture = new Capture();
        ScadaProximity proximity = new ScadaProximity(-70, 6, capture);
        proximity.accept(reading(1, -60), 100, 9000);
        proximity.accept(reading(2, -60), 200, 9000);
        proximity.accept(reading(3, -60), 300, 9000);
        check(capture.capturedAt == 300, "capture time reaches the caption owner without a queue-time refresh");
        proximity.tick(10300);
        check(capture.stale == 1, "queue delay consumes freshness budget");
        proximity.accept(reading(4, -60), 11000, 11000);
        proximity.accept(reading(5, -77), 10999, 11000);
        check(!proximity.isNear() && proximity.entryPackets() == 1 && capture.exits == 0, "stale near resets and older far cannot disrupt new entry");
        proximity.accept(reading(6, -73), 11001, 11001);
        check(!proximity.isNear() && proximity.entryPackets()==0, "hysteresis band cannot reenter stale near");
        for(int i=0;i<3;i++)proximity.accept(reading(7+i,-70),11002+i,11002+i);
        check(proximity.isNear(),"stale state requires three entry packets");
    }

    private static ScadaReading reading(long counter, int rssi) {
        return new ScadaReading("lab-device", counter, 2150, 101325, rssi, counter);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}