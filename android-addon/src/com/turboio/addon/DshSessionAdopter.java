package com.turboio.addon;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Adopts rounds that were submitted somewhere else.
 *
 * <p>A task sent from the desktop DSH client never passes through this device's dispatch path, so the
 * phone monitor and the glasses would keep showing the previous result forever. This poller watches the
 * session this device is bound to, takes the newest user turn from its timeline, and hands that turn to
 * the single-slot monitor. A turn that already finished is backfilled by the monitor's own first read.
 *
 * <p>Single-slot by construction: at most one adoption is live, and a new adoption replaces the previous
 * monitor. Ownership is re-checked twice — the {@link Host#binding()} snapshot must still be the device's
 * durable binding, and the host must still refuse to publish a turn whose account, session, target,
 * generations or unconfirmed-delivery flag moved on. An identity switch stops the adoption before the new
 * identity is allowed to adopt anything, so a foreign session's text can never reach the lens.
 *
 * <p>Cadence: {@value #INITIAL_DELAY_MS} ms after start/adoption/binding change, then
 * {@value #NOCHANGE_DELAY_MS} ms doubling to a {@value #BACKOFF_MAX_MS} ms cap while nothing new appears.
 * Timing runs through the injected {@link DshProactiveReport.Scheduler}/{@link DshProactiveReport.Clock}
 * ports, so tests drive every delay without any real wall-clock sleep.
 *
 * <p>Failure handling: every {@link Host} port can throw (transport, durable storage, display channel),
 * and a background task that lets an exception escape simply never runs again, so {@link #poll()} traps
 * every {@link RuntimeException} and re-arms inside the same {@value #BACKOFF_MAX_MS} ms cap. The
 * adoption state is only ever advanced after the call that claims it returned successfully, so a failed
 * publish retries the same turn and a failed {@link Host#stop} keeps the obligation to stop it. Nothing
 * here can publish a result: a retry draws no card, and a stop that never happened is never reported.
 *
 * <p>Threading: the adopter lock is only ever taken alone, so a control call ({@link #pause()},
 * {@link #stop()}, {@link #resume()}) can never queue behind the timeline read — that read runs outside
 * this lock. Every control call bumps {@code controlEpoch}; a poll whose read started under an older epoch
 * is discarded instead of adopting, so a stop landing mid-read still wins. Host calls run under this lock
 * only, and the host answers them from {@code DshClient.LOCK}, which no adopter path holds here.
 */
final class DshSessionAdopter {
    /** Durable device state plus the two display calls the adoption policy needs. */
    interface Host {
        /** Current durable binding, or null when this device has no watchable bound session. */
        ToolClient.Binding binding();
        /** User turns of the bound session, newest first; an empty list discovers nothing. */
        List<String> discoveredMessages(String sessionId);
        /** The turn this device already tracks locally, used only to avoid a pointless re-adoption. */
        String trackedMessageId();
        /** Publishes the discovered turn durably and starts its single-slot monitor. */
        boolean start(DshProactiveReport.RoundIdentity round);
        /** Stops local monitoring of whatever this adopter started. Never touches the remote task. */
        void stop();
    }

    static final long INITIAL_DELAY_MS = 2000L;
    static final long NOCHANGE_DELAY_MS = 5000L;
    static final long BACKOFF_MAX_MS = 30000L;

    private final Host host;
    private final DshProactiveReport.Scheduler scheduler;
    private final DshProactiveReport.Clock clock;
    private DshProactiveReport.Handle handle;
    private ToolClient.Binding adoptedUnder;
    private String adoptedMessageId = "";
    private int noChangeStreak;
    private boolean readInFlight;
    private boolean paused, stopped = true;
    private boolean ownsMonitor, resumeTrackedTurn;
    /** Bumped by every control call; identifies the generation a poll's read belongs to. */
    private long controlEpoch;

    DshSessionAdopter(Host host, DshProactiveReport.Scheduler scheduler, DshProactiveReport.Clock clock) {
        if (host == null || scheduler == null || clock == null) throw new IllegalArgumentException("wiring_required");
        this.host = host;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Process-wide background variant: real timer thread, monotonic elapsed-realtime clock. */
    static DshSessionAdopter startBackground(Host host) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dsh-adoption");
            thread.setDaemon(true);
            return thread;
        });
        DshSessionAdopter adopter = new DshSessionAdopter(host, (task, delay) -> {
            ScheduledFuture<?> future = executor.schedule(task, delay, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }, android.os.SystemClock::elapsedRealtime);
        adopter.start();
        return adopter;
    }

    synchronized void start() {
        stopped = false;
        paused = false;
        controlEpoch++;
        schedule(INITIAL_DELAY_MS);
    }

    /** Stops adoption and cancels its timer. The remote session keeps running. */
    synchronized void stop() {
        stopped = true;
        controlEpoch++;
        cancel();
    }

    /** The 停止本地结果监听 entry: keep the current result, but stop picking up new desktop turns. */
    synchronized void pause() {
        paused = true;
        controlEpoch++;
    }

    /** An explicit look at the card re-arms adoption, including after a stop. */
    synchronized void resume() {
        paused = false;
        controlEpoch++;
        resumeTrackedTurn = true;
        if (!stopped) { schedule(INITIAL_DELAY_MS); return; }
        stopped = false;
        schedule(INITIAL_DELAY_MS);
    }

    synchronized boolean isPaused() { return paused; }
    synchronized boolean isStopped() { return stopped; }

    private void schedule(long delay) {
        cancel();
        handle = scheduler.schedule(this::poll, delay);
    }

    private void cancel() {
        if (handle != null) { handle.cancel(); handle = null; }
    }

    /** No-change waits double from {@value #NOCHANGE_DELAY_MS} and stop growing at the cap. */
    private long backoff() {
        noChangeStreak++;
        long delay = NOCHANGE_DELAY_MS;
        for (int i = 1; i < noChangeStreak; i++) {
            delay *= 2;
            if (delay >= BACKOFF_MAX_MS) return BACKOFF_MAX_MS;
        }
        return Math.min(delay, BACKOFF_MAX_MS);
    }

    private static boolean sameExactBinding(ToolClient.Binding a, ToolClient.Binding b) {
        return a != null && b != null && a.account.equals(b.account)
            && a.authGeneration == b.authGeneration && a.selectionGeneration == b.selectionGeneration
            && a.targetFingerprint.equals(b.targetFingerprint) && a.sessionId.equals(b.sessionId);
    }

    private void clearAdoptionLocked() {
        adoptedUnder = null;
        adoptedMessageId = "";
        ownsMonitor = false;
        noChangeStreak = 0;
    }

    private void poll() {
        try {
            pollOnce();
        } catch (RuntimeException failure) {
            // A host port threw. Adoption must survive this: release the in-flight slot, keep the
            // adoption state exactly as it was, and retry within the same bounded cap. Retrying is
            // silent by construction — only Host#start can ever draw anything.
            // The timeline read releases its own slot in a finally, so this handler never touches
            // readInFlight: an overlapping poll's slot must not be cleared by a poll that died earlier.
            // Adoption state stays as it was, so the next poll retries the same obligation.
            synchronized (this) { if (!stopped) schedule(backoff()); }
        }
    }

    private void pollOnce() {
        final ToolClient.Binding under;
        final long epoch;
        synchronized (this) {
            if (stopped) return;
            if (paused) { schedule(NOCHANGE_DELAY_MS); return; }
            // One read at a time: cancel(false) on the background executor can race a task that already
            // started, so an overlapping poll must not take a second timeline read.
            if (readInFlight) { schedule(NOCHANGE_DELAY_MS); return; }
            // Ownership is settled before the read, so a switch retires the live monitor immediately.
            if (switchedAwayLocked(host.binding())) { schedule(INITIAL_DELAY_MS); return; }
            under = adoptedUnder;
            epoch = controlEpoch;
            readInFlight = true;
        }
        // The timeline read is a network call: it must never block the settings thread's pause/stop.
        List<String> messages = null;
        boolean readFailed = false;
        try {
            messages = host.discoveredMessages(under.sessionId);
        } catch (RuntimeException failure) {
            readFailed = true;
        } finally {
            synchronized (this) { readInFlight = false; }
        }
        // Re-read durable identity after network I/O; never use stale auth/selection generations.
        ToolClient.Binding fresh = host.binding();
        synchronized (this) {
            if (stopped) return;
            if (epoch != controlEpoch) { schedule(paused ? NOCHANGE_DELAY_MS : INITIAL_DELAY_MS); return; }
            if (paused) { schedule(NOCHANGE_DELAY_MS); return; }
            if (fresh == null) { switchedAwayLocked(null); schedule(INITIAL_DELAY_MS); return; }
            if (!sameExactBinding(under, fresh)) {
                if (!under.sameIdentity(fresh)) { if (ownsMonitor) host.stop(); clearAdoptionLocked(); }
                else adoptedUnder = fresh;
                schedule(INITIAL_DELAY_MS);
                return;
            }
            if (readFailed) { schedule(backoff()); return; }
            // A control change or identity switch during the read outranks this discovery.
            if (switchedAwayLocked(fresh)) { schedule(INITIAL_DELAY_MS); return; }
            if (messages == null || messages.isEmpty()) { schedule(backoff()); return; }
            String newest = messages.get(0);
            String tracked = host.trackedMessageId();
            boolean explicitResume = resumeTrackedTurn && !tracked.isEmpty() && tracked.equals(newest);
            if (!explicitResume && (newest.equals(tracked) || newest.equals(adoptedMessageId))) { schedule(backoff()); return; }
            DshProactiveReport.RoundIdentity round = new DshProactiveReport.RoundIdentity(under.account,
                under.authGeneration, under.selectionGeneration, under.targetFingerprint, under.sessionId, newest, false);
            if (!round.valid()) { schedule(backoff()); return; }
            // Single slot: the host replaces whatever monitor is live with this turn.
            if (!host.start(round)) { schedule(backoff()); return; }
            adoptedUnder = fresh;
            adoptedMessageId = newest;
            ownsMonitor = true;
            resumeTrackedTurn = false;
            noChangeStreak = 0;
            schedule(INITIAL_DELAY_MS);
        }
    }

    /**
     * Arms {@code binding} as the live adoption identity when it is still the same owner. Caller holds this
     * lock. True means this poll must not adopt: the device lost its watchable binding (no stop needed —
     * a lost binding already breaks the monitor's own durable guard, so an extra "stopped" card would be
     * invented for a user action that never happened) or the account/target moved on, which retires our
     * monitor first and re-arms under the initial cadence so the next read belongs to the new owner.
     */
    private boolean switchedAwayLocked(ToolClient.Binding binding) {
        if (binding == null) {
            clearAdoptionLocked();
            return true;
        }
        if (adoptedUnder != null && !adoptedUnder.sameIdentity(binding)) {
            // Ownership moved: stop only a monitor this watcher actually started.
            if (ownsMonitor) host.stop();
            ownsMonitor = false;
            clearAdoptionLocked();
            return true;
        }
        adoptedUnder = binding;
        return false;
    }
}
