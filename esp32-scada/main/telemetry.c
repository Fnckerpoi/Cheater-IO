#include "telemetry.h"

#include <string.h>

#include "mbedtls/gcm.h"

_Static_assert(3u + 2u + TELEMETRY_ADV_DATA_LEN <= 31u,
               "legacy advertising payload must fit within 31 bytes");

static void counter_bytes(uint32_t counter, uint8_t bytes[4])
{
    bytes[0] = (uint8_t)(counter >> 24);
    bytes[1] = (uint8_t)(counter >> 16);
    bytes[2] = (uint8_t)(counter >> 8);
    bytes[3] = (uint8_t)counter;
}

bool telemetry_pack_plaintext(int32_t temperature_centi_c, int64_t pressure_pa,
                              uint8_t plaintext[TELEMETRY_PLAINTEXT_LEN])
{
    if (plaintext == NULL || temperature_centi_c < INT16_MIN || temperature_centi_c > INT16_MAX ||
        pressure_pa < 0 || pressure_pa > 0xFFFFFF) {
        return false;
    }

    const uint16_t temperature_bits = (uint16_t)(int16_t)temperature_centi_c;
    plaintext[0] = (uint8_t)(temperature_bits >> 8);
    plaintext[1] = (uint8_t)temperature_bits;
    plaintext[2] = (uint8_t)((uint64_t)pressure_pa >> 16);
    plaintext[3] = (uint8_t)((uint64_t)pressure_pa >> 8);
    plaintext[4] = (uint8_t)pressure_pa;
    return true;
}

bool telemetry_encrypt_payload(const uint8_t key[16], const uint8_t nonce_prefix[TELEMETRY_NONCE_PREFIX_LEN],
                               uint32_t counter, const uint8_t plaintext[TELEMETRY_PLAINTEXT_LEN],
                               uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN])
{
    if (key == NULL || nonce_prefix == NULL || plaintext == NULL || payload == NULL) {
        return false;
    }

    uint8_t nonce[12];
    uint8_t aad[7] = {
        TELEMETRY_COMPANY_ID_LSB,
        TELEMETRY_COMPANY_ID_MSB,
        TELEMETRY_VERSION,
        0, 0, 0, 0
    };
    counter_bytes(counter, &aad[3]);
    memcpy(nonce, nonce_prefix, TELEMETRY_NONCE_PREFIX_LEN);
    memcpy(&nonce[TELEMETRY_NONCE_PREFIX_LEN], &aad[3], 4);

    mbedtls_gcm_context gcm;
    mbedtls_gcm_init(&gcm);
    int rc = mbedtls_gcm_setkey(&gcm, MBEDTLS_CIPHER_ID_AES, key, 128);
    if (rc == 0) {
        payload[0] = TELEMETRY_VERSION;
        memcpy(&payload[1], &aad[3], 4);
        rc = mbedtls_gcm_crypt_and_tag(&gcm, MBEDTLS_GCM_ENCRYPT,
                                       TELEMETRY_PLAINTEXT_LEN, nonce, sizeof(nonce),
                                       aad, sizeof(aad), plaintext, &payload[5],
                                       TELEMETRY_TAG_LEN, &payload[10]);
    }
    mbedtls_gcm_free(&gcm);
    return rc == 0;
}

bool telemetry_build_payload(const uint8_t key[16], const uint8_t nonce_prefix[TELEMETRY_NONCE_PREFIX_LEN],
                             uint32_t counter, int32_t temperature_centi_c, int64_t pressure_pa,
                             uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN])
{
    uint8_t plaintext[TELEMETRY_PLAINTEXT_LEN];
    if (!telemetry_pack_plaintext(temperature_centi_c, pressure_pa, plaintext)) {
        return false;
    }
    return telemetry_encrypt_payload(key, nonce_prefix, counter, plaintext, payload);
}

void telemetry_make_manufacturer_data(const uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN],
                                      uint8_t manufacturer_data[TELEMETRY_ADV_DATA_LEN])
{
    manufacturer_data[0] = TELEMETRY_COMPANY_ID_LSB;
    manufacturer_data[1] = TELEMETRY_COMPANY_ID_MSB;
    memcpy(&manufacturer_data[2], payload, TELEMETRY_APP_PAYLOAD_LEN);
}
