package com.turboio.addon;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started SCADA BLE scanner, encrypted provisioning, and card proximity gate. */
public final class ScadaSensorController {
    private static final String LEASE = "SCADA sensor";
    private static final String PREFS = "turboio_scada";
    private static final String KEY_SECRET = "scada_lab_key_hex";
    private static final String PREFIX_SECRET = "scada_nonce_prefix_hex";
    private static final String LAST_COUNTER = "last_counter";
    private static final String CONFIG_FINGERPRINT = "config_fingerprint";
    private static final int REQUEST_SCAN = 8431;
    private static final int REQUEST_BACKGROUND_LOCATION = 8432;
    private static final int REQUEST_NOTIFICATIONS = 8433;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object SCAN_LOCK = new Object();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scada-scan-worker");
        thread.setDaemon(true);
        return thread;
    });
    private static final Runnable TIMEOUT_TICK = new Runnable() {
        @Override public void run() {
            if (!running || proximity == null) return;
            proximity.tick(SystemClock.elapsedRealtime());
            MAIN.postDelayed(this, 1000);
        }
    };

    private static Context app;
    private static ScadaBleScanner scanner;
    private static ScadaProximity proximity;
    private static final ScadaCaptionController captions = new ScadaCaptionController();
    private static volatile ScanConfig activeConfig;
    private static volatile boolean running;
    private static volatile boolean arming;
    private static volatile int generation;
    private static int thresholdDbm = ScadaProximity.DEFAULT_THRESHOLD_DBM;
    private static int hysteresisDb = ScadaProximity.DEFAULT_HYSTERESIS_DB;
    private static volatile long validPackets, rejectedPackets, replayedPackets;
    private static volatile String deviceId = "unprovisioned";
    private static volatile String note = "Stopped; provision a lab device before scanning";

    private ScadaSensorController() {}

    static void init(Context context) {
        app = context.getApplicationContext();
        BackgroundWork.register(LEASE, ScadaSensorController::stopFromLease);
    }

    public static void start(Activity activity) {
        if (activity == null || app == null) { note = "SCADA scanner is not initialized"; return; }
        if (running || arming) { note = "SCADA scan is already armed or preparing"; return; }
        if (!BackgroundWork.enabled()) { note = "Enable background activity in Settings before starting the sensor"; return; }
        if (!requestScanPermissions(activity)) return;
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        if (!BackgroundWork.acquireBleScan(LEASE)) {
            note = BackgroundWork.stateNote();
            return;
        }
        final int token;
        synchronized (SCAN_LOCK) {
            token = ++generation;
            arming = true;
        }
        note = "Preparing encrypted sensor configuration and replay protection";
        WORKER.execute(() -> {
            try {
                final ScanConfig ready = loadScanConfig(token);
                MAIN.post(() -> armOnMain(token, ready));
            } catch (Exception failure) {
                note = "Cannot start: provisioning or saved replay state is invalid";
                MAIN.post(() -> failPreparation(token));
            }
        });
    }

    private static void armOnMain(int token, ScanConfig config) {
        if (!isCurrent(token) || !arming) { enqueueClear(config); return; }
        thresholdDbm = config.thresholdDbm;
        hysteresisDb = config.hysteresisDb;
        deviceId = config.deviceId;
        activeConfig = config;
        arming = false;
        captions.beginScan(token);
        proximity = new ScadaProximity(thresholdDbm, hysteresisDb, new ScadaProximity.Listener() {
            @Override public void onReading(ScadaReading reading, long capturedAtMillis) {
                captions.onReading(token, reading, capturedAtMillis, SystemClock.elapsedRealtime());
            }
            @Override public void onExit() { captions.onExit(token); }
            @Override public void markStale() { captions.onStale(token); }
            @Override public void remove() {}
        });
        scanner = new ScadaBleScanner(app);
        config.startedAtNanos = SystemClock.elapsedRealtimeNanos();
        running = true;
        boolean started = scanner.start(new ScadaBleScanner.Listener() {
            @Override public void onAdvertisement(byte[] data, int rssiDbm, long capturedAtNanos) {
                dispatchAdvertisement(config, data, rssiDbm, capturedAtNanos);
            }
            @Override public void onScannerError(String failure) {
                MAIN.post(() -> failScanner(token, failure));
            }
        });
        if (!started) {
            failScanner(token, "BLE scanner could not start; check Bluetooth and scan permission");
            return;
        }
        MAIN.removeCallbacks(TIMEOUT_TICK);
        MAIN.postDelayed(TIMEOUT_TICK, 1000);
        note = "Low-latency scan armed for the provisioned SCADA sensor";
    }

    private static void failPreparation(int token) {
        if (!isCurrent(token)) return;
        synchronized (SCAN_LOCK) { if (generation == token) { arming = false; generation++; } }
        captions.stopScan();
        BackgroundWork.release(LEASE);
    }

    private static void failScanner(int token, String failure) {
        if (!isCurrent(token)) return;
        stop();
        note = failure + "; foreground lease stopped";
    }

    private static boolean requestScanPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_SCAN);
                note = "Nearby devices and precise location permissions are required for RSSI proximity";
                return false;
            }
        } else {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_SCAN);
                note = "Location permission is required by Android for legacy BLE scanning";
                return false;
            }
            if (Build.VERSION.SDK_INT >= 29 && Build.VERSION.SDK_INT <= 30
                    && activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
                note = "Allow background location for sensor scanning while the screen is off";
                return false;
            }
        }
        return true;
    }

    private static ScanConfig loadScanConfig(int token) throws Exception {
        byte[] key = null;
        byte[] prefix = null;
        byte[] identity = null;
        byte[] digest = null;
        boolean handedOff = false;
        try {
            String keyHex = SecretStore.get(app, KEY_SECRET);
            String prefixHex = SecretStore.get(app, PREFIX_SECRET);
            key = ScadaPacketCodec.parseHex(keyHex, 16);
            prefix = ScadaPacketCodec.parseHex(prefixHex, 8);
            int threshold = app.getSharedPreferences(PREFS, 0)
                    .getInt("threshold_dbm", ScadaProximity.DEFAULT_THRESHOLD_DBM);
            int hysteresis = app.getSharedPreferences(PREFS, 0)
                    .getInt("hysteresis_db", ScadaProximity.DEFAULT_HYSTERESIS_DB);
            if (threshold < -100 || threshold > 0 || hysteresis < 1 || hysteresis > 30)
                throw new IllegalArgumentException("stored_settings");
            identity = new byte[key.length + prefix.length];
            System.arraycopy(key, 0, identity, 0, key.length);
            System.arraycopy(prefix, 0, identity, key.length, prefix.length);
            digest = MessageDigest.getInstance("SHA-256").digest(identity);
            String fingerprint = toHex(digest);
            android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, 0);
            long persisted = ScadaReplayHistory.load(prefs.getAll(), fingerprint);
            if (!prefs.edit().putLong(ScadaReplayHistory.key(fingerprint), persisted).putBoolean(ScadaReplayHistory.marker(fingerprint), true).commit())
                throw new IllegalStateException("replay_state_persist_failed");
            ScanConfig config = new ScanConfig(token, key, prefix, new ScadaReplayGuard(persisted),
                    threshold, hysteresis, toHex(prefix), fingerprint);
            handedOff = true;
            return config;
        } finally {
            if (identity != null) Arrays.fill(identity, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
            if (!handedOff) {
                if (key != null) Arrays.fill(key, (byte) 0);
                if (prefix != null) Arrays.fill(prefix, (byte) 0);
            }
        }
    }

    private static void dispatchAdvertisement(ScanConfig config, byte[] data, int rssiDbm, long capturedAtNanos) {
        if (config == null || data == null) return;
        synchronized (SCAN_LOCK) {
            if (!running || activeConfig != config || generation != config.generation) return;
            WORKER.execute(() -> processAdvertisement(config, data, rssiDbm, capturedAtNanos));
        }
    }

    /** Authentication, replay check and durable counter commit run serially off the main thread. */
    private static void processAdvertisement(ScanConfig config, byte[] data, int rssiDbm, long capturedAtNanos) {
        try {
            if (!isCurrent(config.generation) || !ScadaSampleTime.fresh(capturedAtNanos, SystemClock.elapsedRealtimeNanos(), config.startedAtNanos, config.lastCapturedAtNanos)) return;
            ScadaReading reading = ScadaPacketCodec.decode(ScadaPacketCodec.COMPANY_ID, data,
                    config.key, config.prefix, config.deviceId, rssiDbm, System.currentTimeMillis());
            if (!isCurrent(config.generation)) return;
            if (!config.replayGuard.isNew(reading.counter)) {
                replayedPackets++;
                return;
            }
            if (!isCurrent(config.generation)) return;
            boolean persisted = app.getSharedPreferences(PREFS, 0).edit()
                    .putLong(ScadaReplayHistory.key(config.fingerprint), reading.counter).commit();
            if (!persisted) {
                note = "Authenticated reading ignored because replay protection could not be persisted";
                rejectedPackets++;
                return;
            }
            if (!config.replayGuard.accept(reading.counter)) { replayedPackets++; return; }
            config.lastCapturedAtNanos = capturedAtNanos;
            MAIN.post(() -> {
                if (!isCurrent(config.generation) || activeConfig != config || proximity == null) return;
                if (!ScadaSampleTime.fresh(capturedAtNanos, SystemClock.elapsedRealtimeNanos(), config.startedAtNanos, config.lastPresentedAtNanos)) return;
                config.lastPresentedAtNanos = capturedAtNanos;
                validPackets++;
                proximity.accept(reading, ScadaSampleTime.captureMillis(capturedAtNanos), SystemClock.elapsedRealtime());
                note = "Low-latency scan armed · valid " + validPackets + " · near " + proximity.isNear()
                        + " · replays " + replayedPackets + " · rejected " + rejectedPackets;
            });
        } catch (Exception rejected) {
            if (!isCurrent(config.generation)) return;
            rejectedPackets++;
            note = "Low-latency scan armed · valid " + validPackets + " · replays " + replayedPackets
                    + " · rejected " + rejectedPackets;
        }
    }

    private static boolean isCurrent(int token) { return generation == token; }

    private static void enqueueClear(ScanConfig config) {
        if (config != null) WORKER.execute(config::clear);
    }

    public static void stop() {
        ScanConfig old;
        final boolean hadActiveScan;
        synchronized (SCAN_LOCK) {
            hadActiveScan = running || arming || activeConfig != null;
            running = false;
            arming = false;
            generation++;
            old = activeConfig;
            activeConfig = null;
            if (old != null) WORKER.execute(old::clear);
        }
        MAIN.removeCallbacks(TIMEOUT_TICK);
        if (scanner != null) { scanner.stop(); scanner = null; }
        proximity = null;
        captions.stopScan();
        BackgroundWork.release(LEASE);
        note = "Stopped";
    }

    private static void stopFromLease() { stop(); }

    public static String status() {
        String permissions = "";
        if (app != null && Build.VERSION.SDK_INT >= 31
                && app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            permissions = " · Nearby scan permission denied";
        else if (app != null && Build.VERSION.SDK_INT >= 31
                && (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED))
            permissions = " · precise location permission denied";
        else if (app != null && Build.VERSION.SDK_INT < 31
                && app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions = " · legacy location permission denied";
        if (app != null && Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions += " · notifications denied";
        return note + "\nDevice ID: " + deviceId + "\nThreshold: " + thresholdDbm + " dBm (" + hysteresisDb
                + " dB hysteresis)" + "\nPackets: valid " + validPackets + ", replayed " + replayedPackets
                + ", rejected " + rejectedPackets + "\n" + (BackgroundWork.held(LEASE) ? "Foreground lease active" : "No foreground lease") + permissions
                + "\n" + captions.status();
    }

    public static void show(Activity activity) {
        EditorialUI.Screen screen = new EditorialUI.Screen(activity, "SCADA sensor");
        screen.body.addView(EditorialUI.text(activity,
                "Local lab setup. The AES key and nonce prefix are stored with Android Keystore encryption. Device ID is derived from the provisioned nonce prefix; no Bluetooth address or pairing is used. Low-latency scanning runs only while you explicitly arm the foreground lease; Stop or the notification action ends it.",
                14, EditorialUI.MUTED));
        EditText key = field(activity, "AES-128 key (32 hex characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText prefix = field(activity, "Nonce prefix / device ID (16 hex characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText threshold = field(activity, "Entry RSSI threshold dBm (for example -70)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText hysteresis = field(activity, "Exit hysteresis dB (1–30)", InputType.TYPE_CLASS_NUMBER);
        screen.body.addView(key);
        screen.body.addView(prefix);
        screen.body.addView(threshold);
        screen.body.addView(hysteresis);
        TextView state = EditorialUI.text(activity, status(), 14, EditorialUI.LIME);
        state.setPadding(0, EditorialUI.dp(activity, 14), 0, EditorialUI.dp(activity, 6));
        screen.body.addView(state);
        EditorialUI.button(activity, screen.body, "Save encrypted lab provisioning", true, () -> {
            try {
                saveProvisioning(key.getText().toString(), prefix.getText().toString(),
                        threshold.getText().toString(), hysteresis.getText().toString());
                key.setText(""); prefix.setText("");
                state.setText(status());
            } catch (Exception invalid) { state.setText("Invalid provisioning or settings; nothing was started"); }
        });
        EditorialUI.button(activity, screen.body, "Start SCADA BLE scan", true, () -> { start(activity); state.setText(status()); });
        EditorialUI.button(activity, screen.body, "Stop SCADA BLE scan", false, () -> { stop(); state.setText(status()); });
        EditorialUI.button(activity, screen.body, "I verified the lens is clear", false, () -> { captions.confirmClear(); state.setText(status()); });
        EditorialUI.button(activity, screen.body, "Refresh sensor status", false, () -> state.setText(status()));
        EditorialUI.button(activity, screen.body, "Clear encrypted provisioning", false, () -> {
            stop();
            try {
                SecretStore.put(app, KEY_SECRET, "");
                SecretStore.put(app, PREFIX_SECRET, "");
                // Replay history deliberately survives credential removal.
                deviceId = "unprovisioned";
                note = "Provisioning cleared";
            } catch (Exception failure) { note = "Could not clear provisioning"; }
            state.setText(status());
        });
    }

    private static EditText field(Activity activity, String hint, int inputType) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(EditorialUI.INK);
        input.setHintTextColor(EditorialUI.MUTED);
        input.setInputType(inputType);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(EditorialUI.dp(activity, 12), EditorialUI.dp(activity, 10),
                EditorialUI.dp(activity, 12), EditorialUI.dp(activity, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                EditorialUI.dp(activity, 54));
        params.topMargin = EditorialUI.dp(activity, 8);
        input.setLayoutParams(params);
        return input;
    }

    static void saveProvisioning(String keyText, String prefixText, String thresholdText, String hysteresisText) throws Exception {
        if (running || arming) throw new IllegalStateException("stop_before_provisioning");
        String oldKey = SecretStore.get(app, KEY_SECRET);
        String oldPrefix = SecretStore.get(app, PREFIX_SECRET);
        String keyHex = keyText == null || keyText.trim().isEmpty() ? oldKey : normalizeHex(keyText, 16);
        String prefixHex = prefixText == null || prefixText.trim().isEmpty() ? oldPrefix : normalizeHex(prefixText, 8);
        ScadaPacketCodec.parseHex(keyHex, 16);
        ScadaPacketCodec.parseHex(prefixHex, 8);
        int nextThreshold = thresholdText == null || thresholdText.trim().isEmpty()
                ? ScadaProximity.DEFAULT_THRESHOLD_DBM : Integer.parseInt(thresholdText.trim());
        int nextHysteresis = hysteresisText == null || hysteresisText.trim().isEmpty()
                ? ScadaProximity.DEFAULT_HYSTERESIS_DB : Integer.parseInt(hysteresisText.trim());
        if (nextThreshold < -100 || nextThreshold > 0) throw new IllegalArgumentException("threshold_range");
        if (nextHysteresis < 1 || nextHysteresis > 30) throw new IllegalArgumentException("hysteresis_range");
        SecretStore.put(app, KEY_SECRET, keyHex);
        SecretStore.put(app, PREFIX_SECRET, prefixHex);
        boolean saved = app.getSharedPreferences(PREFS, 0).edit().putInt("threshold_dbm", nextThreshold)
                .putInt("hysteresis_db", nextHysteresis).commit();
        if (!saved) throw new IllegalStateException("settings_save_failed");
        thresholdDbm = nextThreshold;
        hysteresisDb = nextHysteresis;
        deviceId = prefixHex;
    }

    private static String normalizeHex(String value, int byteLength) {
        return toHex(ScadaPacketCodec.parseHex(value.trim(), byteLength));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        return out.toString();
    }

    private static final class ScanConfig {
        final int generation;
        final byte[] key;
        final byte[] prefix;
        final ScadaReplayGuard replayGuard;
        final int thresholdDbm;
        final int hysteresisDb;
        final String deviceId;
        final String fingerprint;
        long startedAtNanos, lastCapturedAtNanos = -1, lastPresentedAtNanos = -1;
        ScanConfig(int generation, byte[] key, byte[] prefix, ScadaReplayGuard replayGuard,
                   int thresholdDbm, int hysteresisDb, String deviceId, String fingerprint) {
            this.fingerprint = fingerprint;
            this.generation = generation;
            this.key = key;
            this.prefix = prefix;
            this.replayGuard = replayGuard;
            this.thresholdDbm = thresholdDbm;
            this.hysteresisDb = hysteresisDb;
            this.deviceId = deviceId;
        }
        void clear() { Arrays.fill(key, (byte)0); Arrays.fill(prefix, (byte)0); }
    }
}