package com.turboio.addon;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Decoder for the SCADA v1 manufacturer specific advertisement. */
public final class ScadaPacketCodec {
    public static final int COMPANY_ID = 0xffff;
    public static final int VERSION = 1;
    public static final int PACKET_BYTES = 22;
    public static final long MAX_COUNTER = 0xffffffffL;
    public static final int MIN_TEMPERATURE_CENTI_C = -4000;
    public static final int MAX_TEMPERATURE_CENTI_C = 12500;
    public static final int MIN_PRESSURE_PA = 30000;
    public static final int MAX_PRESSURE_PA = 120000;
    private static final int NONCE_PREFIX_BYTES = 8;
    private static final int NONCE_BYTES = 12;
    private static final int PLAINTEXT_BYTES = 5;
    private static final int TAG_BYTES = 12;

    private ScadaPacketCodec() {}

    /** Decode the 22 app bytes returned by ScanRecord.getManufacturerSpecificData. */
    public static ScadaReading decode(int companyId, byte[] packet, byte[] key, byte[] devicePrefix,
                                      String deviceId, int rssiDbm, long receivedAtMillis)
            throws GeneralSecurityException {
        if (companyId != COMPANY_ID) throw new IllegalArgumentException("company_id");
        if (packet == null || packet.length != PACKET_BYTES) throw new IllegalArgumentException("packet_length");
        if ((packet[0] & 0xff) != VERSION) throw new IllegalArgumentException("packet_version");
        if (key == null || key.length != 16) throw new IllegalArgumentException("key_length");
        if (devicePrefix == null || devicePrefix.length != NONCE_PREFIX_BYTES) throw new IllegalArgumentException("prefix_length");
        if (deviceId == null || deviceId.trim().isEmpty() || deviceId.length() > 128) throw new IllegalArgumentException("device_id");
        if (rssiDbm < -127 || rssiDbm > 20) throw new IllegalArgumentException("rssi_range");
        if (receivedAtMillis < 0) throw new IllegalArgumentException("received_at");

        long counter = ((long)(packet[1] & 0xff) << 24)
                | ((long)(packet[2] & 0xff) << 16)
                | ((long)(packet[3] & 0xff) << 8)
                | (long)(packet[4] & 0xff);
        byte[] nonce = new byte[NONCE_BYTES];
        System.arraycopy(devicePrefix, 0, nonce, 0, NONCE_PREFIX_BYTES);
        System.arraycopy(packet, 1, nonce, NONCE_PREFIX_BYTES, 4);
        byte[] aad = new byte[7];
        aad[0] = (byte)(companyId & 0xff);       // transmitted company ID is little-endian
        aad[1] = (byte)((companyId >>> 8) & 0xff);
        System.arraycopy(packet, 0, aad, 2, 5); // version and transmitted counter bytes

        byte[] encrypted = new byte[PLAINTEXT_BYTES + TAG_BYTES];
        System.arraycopy(packet, 5, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(96, nonce));
        cipher.updateAAD(aad);
        byte[] plaintext = cipher.doFinal(encrypted);
        if (plaintext.length != PLAINTEXT_BYTES) throw new GeneralSecurityException("plaintext_length");

        int temperature = (short)(((plaintext[0] & 0xff) << 8) | (plaintext[1] & 0xff));
        int pressure = ((plaintext[2] & 0xff) << 16) | ((plaintext[3] & 0xff) << 8) | (plaintext[4] & 0xff);
        if (temperature < MIN_TEMPERATURE_CENTI_C || temperature > MAX_TEMPERATURE_CENTI_C)
            throw new IllegalArgumentException("temperature_range");
        if (pressure < MIN_PRESSURE_PA || pressure > MAX_PRESSURE_PA)
            throw new IllegalArgumentException("pressure_range");
        return new ScadaReading(deviceId, counter, temperature, pressure, rssiDbm, receivedAtMillis);
    }

    /** Strict hexadecimal parser used by the local provisioning screen. */
    public static byte[] parseHex(String text, int expectedBytes) {
        if (text == null || text.length() != expectedBytes * 2) throw new IllegalArgumentException("hex_length");
        byte[] out = new byte[expectedBytes];
        for (int i = 0; i < expectedBytes; i++) {
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("hex_format");
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }
}
