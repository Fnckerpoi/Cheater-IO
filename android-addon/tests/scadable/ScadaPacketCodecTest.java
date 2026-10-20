package com.turboio.addon;

import java.security.GeneralSecurityException;

/** Fixed cross-language AES-GCM contract test; requires no Android classes. */
public final class ScadaPacketCodecTest {
    private static final byte[] KEY = ScadaPacketCodec.parseHex(
            "000102030405060708090a0b0c0d0e0f", 16);
    private static final byte[] PREFIX = ScadaPacketCodec.parseHex(
            "1011121314151617", 8);
    private static final byte[] VECTOR = ScadaPacketCodec.parseHex(
            "0101020304d5a18df83405b5915af4d0a3406bfe4324", 22);

    public static void main(String[] args) throws Exception {
        decodesApprovedVector();
        rejectsAlteredTag();
        System.out.println("ScadaPacketCodecTest passed");
    }

    private static void decodesApprovedVector() throws Exception {
        ScadaReading reading = ScadaPacketCodec.decode(ScadaPacketCodec.COMPANY_ID, VECTOR,
                KEY, PREFIX, "1011121314151617", -48, 123456L);
        check(reading.counter == 0x01020304L, "counter");
        check(reading.temperatureCentiC == 2345, "temperature");
        check(reading.pressurePa == 101325, "pressure");
        check(reading.deviceId.equals("1011121314151617"), "provisioned device id");
        check(reading.rssiDbm == -48, "RSSI");
        check(reading.receivedAtMillis == 123456L, "local receive time");
    }

    private static void rejectsAlteredTag() throws Exception {
        byte[] altered = VECTOR.clone();
        altered[altered.length - 1] ^= 1;
        try {
            ScadaPacketCodec.decode(ScadaPacketCodec.COMPANY_ID, altered,
                    KEY, PREFIX, "1011121314151617", -48, 123456L);
            throw new AssertionError("altered authentication tag was accepted");
        } catch (GeneralSecurityException expected) {
            // AES-GCM authentication must fail before a reading is returned.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
