package com.turboio.addon;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import java.util.Collections;

/** Selective BLE advertisement scanner; it never reads BluetoothDevice identity. */
final class ScadaBleScanner {
    interface Listener {
        void onAdvertisement(byte[] manufacturerData, int rssiDbm, long capturedAtNanos);
        void onScannerError(String status);
    }

    private final Context app;
    private BluetoothLeScanner scanner;
    private ScanCallback callback;

    ScadaBleScanner(Context context) { app = context.getApplicationContext(); }

    boolean start(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        if (callback != null) return true;
        try {
            BluetoothManager manager = (BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
            if (scanner == null) {
                listener.onScannerError("Bluetooth scan is unavailable; check that Bluetooth is on");
                return false;
            }
            ScanFilter filter = new ScanFilter.Builder()
                    .setManufacturerData(ScadaPacketCodec.COMPANY_ID,
                            new byte[]{(byte)ScadaPacketCodec.VERSION}, new byte[]{(byte)0xff})
                    .build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build();
            callback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    deliver(result);
                }
                @Override public void onBatchScanResults(java.util.List<ScanResult> results) {
                    for (ScanResult result : results) deliver(result);
                }
                @Override public void onScanFailed(int errorCode) {
                    callback = null;
                    listener.onScannerError("BLE scan failed (" + errorCode + ")");
                }
                private void deliver(ScanResult result) {
                    if (result == null || result.getScanRecord() == null) return;
                    ScanRecord record = result.getScanRecord();
                    byte[] data = record.getManufacturerSpecificData(ScadaPacketCodec.COMPANY_ID);
                    if (data != null && data.length == ScadaPacketCodec.PACKET_BYTES)
                        listener.onAdvertisement(data.clone(), result.getRssi(), result.getTimestampNanos());
                }
            };
            scanner.startScan(Collections.singletonList(filter), settings, callback);
            return true;
        } catch (SecurityException denied) {
            callback = null;
            listener.onScannerError("BLE scan permission was denied");
            return false;
        } catch (RuntimeException failure) {
            callback = null;
            listener.onScannerError("BLE scanner could not start");
            return false;
        }
    }

    void stop() {
        BluetoothLeScanner activeScanner = scanner;
        ScanCallback activeCallback = callback;
        scanner = null;
        callback = null;
        if (activeScanner == null || activeCallback == null) return;
        try { activeScanner.stopScan(activeCallback); }
        catch (SecurityException ignored) { }
        catch (RuntimeException ignored) { }
    }
}