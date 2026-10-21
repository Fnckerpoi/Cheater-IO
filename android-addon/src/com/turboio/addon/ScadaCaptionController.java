package com.turboio.addon;

import java.util.Locale;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class ScadaCaptionController {
    public static final long COOLDOWN_MILLIS = 8000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object owner = new Object();
    private long epoch, sequence, generation, expires, scanStartedAt;
    private long lastCapturedAtMillis = -1, stoppedAtMillis = -1;
    private boolean scanning, displaying, near, attemptAvailable = true, normalExit;
    private NavGlasses.ClearState clearState;
    private final Runnable expiry = () -> { if (SystemClock.elapsedRealtime() >= expires) onStale(generation); else scheduleExpiry(); };
    private void scheduleExpiry() { main.removeCallbacks(expiry); main.postDelayed(expiry, Math.max(0, expires-SystemClock.elapsedRealtime())); }

    public void beginScan(long token) {
        if (token <= generation) return;
        stop();
        generation = token;
        scanStartedAt = SystemClock.elapsedRealtime();
        scanning = true;
        near = false;
        attemptAvailable = true;
        lastCapturedAtMillis = -1;
    }
    public void stopScan() { scanning = false; stop(); }
    public void onExit(long token) { leave(token); }
    public void onStale(long token) { leave(token); }
    private void leave(long token) {
        if (!scanning || token != generation || !near) return;
        near = false;
        attemptAvailable = true;
        main.removeCallbacks(expiry);
        normalExit = displaying && NavGlasses.phase().equals("ready") || NavGlasses.canReenter(owner,epoch);
        displaying = false;
        stoppedAtMillis = SystemClock.elapsedRealtime();
        if (normalExit) {
            if (NavGlasses.phase().equals("ready")) clearState = NavGlasses.stopForReentry(owner, epoch);
            stoppedAtMillis = NavGlasses.closedAt(owner,epoch);
        } else if (epoch > 0) clearState = NavGlasses.stopAndClear(owner,epoch);
    }
    public void onReading(long token, ScadaReading reading, long capturedAtMillis, long nowMillis) {
        if (!scanning || token != generation || reading == null || capturedAtMillis < 0 || nowMillis < capturedAtMillis
                || capturedAtMillis < scanStartedAt || capturedAtMillis < lastCapturedAtMillis) return;
        if (nowMillis-capturedAtMillis >= ScadaProximity.STALE_AFTER_MILLIS) { onStale(token); return; }
        lastCapturedAtMillis = capturedAtMillis;
        near = true;
        expires = capturedAtMillis + ScadaProximity.STALE_AFTER_MILLIS;
        String text = String.format(Locale.ROOT,"SCADA\n温度：%.2f °C\n环境气压：%.2f kPa",reading.temperatureCentiC/100.0,reading.pressurePa/1000.0);
        if (displaying) {
            if (NavGlasses.update(owner,epoch,text,expires)) scheduleExpiry(); else stop();
            return;
        }
        if (!attemptAvailable) return;
        boolean rotating = clearState == NavGlasses.ClearState.REQUESTED || clearState == NavGlasses.ClearState.UNCERTAIN;
        if (rotating) {
            if (!normalExit) return;
            if (capturedAtMillis <= stoppedAtMillis || nowMillis-stoppedAtMillis < COOLDOWN_MILLIS) return;
            NavGlasses.ReopenState eligibility = NavGlasses.reopenState(owner,epoch);
            if (eligibility == NavGlasses.ReopenState.PENDING) return;
            if (eligibility == NavGlasses.ReopenState.FAILED) { attemptAvailable = false; normalExit = false; return; }
        }
        attemptAvailable = false;
        long oldEpoch = epoch, next = ++sequence;
        epoch = next;
        NavGlasses.ClearListener listener = state -> {
            if (epoch != next) return;
            displaying = false;
            clearState = state;
        };
        boolean ok = rotating
                ? NavGlasses.reopen(owner,oldEpoch,next,text,expires,capturedAtMillis,listener)
                : NavGlasses.claim(owner,next,text,expires,listener);
        if (!ok) {
            epoch = NavGlasses.ownedEpoch(owner);
            normalExit = false;
            main.removeCallbacks(expiry);
            return;
        }
        displaying = true;
        clearState = null;
        normalExit = false;
        scheduleExpiry();
    }
    public void stop() {
        main.removeCallbacks(expiry);
        displaying = false;
        near = false;
        normalExit = false;
        attemptAvailable = false;
        if (epoch > 0) clearState = NavGlasses.stopAndClear(owner,epoch);
    }
    public boolean confirmClear() {
        boolean ok = NavGlasses.confirmClear(owner,epoch);
        if (ok) { clearState = NavGlasses.ClearState.CONFIRMED; attemptAvailable = scanning; normalExit = false; }
        return ok;
    }
    public String status() {
        if (displaying) return "SCADA caption active; lens visibility unverified";
        if (clearState != null) return "SCADA caption clear " + clearState.name().toLowerCase(Locale.ROOT);
        return epoch == 0 ? "SCADA caption idle" : "SCADA caption waiting for display owner";
    }
}
