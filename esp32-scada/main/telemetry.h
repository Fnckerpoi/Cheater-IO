#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define TELEMETRY_VERSION 0x01u
#define TELEMETRY_APP_PAYLOAD_LEN 22u
#define TELEMETRY_PLAINTEXT_LEN 5u
#define TELEMETRY_TAG_LEN 12u
#define TELEMETRY_NONCE_PREFIX_LEN 8u
#define TELEMETRY_COMPANY_ID_LSB 0xffu
#define TELEMETRY_COMPANY_ID_MSB 0xffu
#define TELEMETRY_ADV_DATA_LEN (2u + TELEMETRY_APP_PAYLOAD_LEN)

bool telemetry_pack_plaintext(int32_t temperature_centi_c, int64_t pressure_pa,
                              uint8_t plaintext[TELEMETRY_PLAINTEXT_LEN]);
bool telemetry_encrypt_payload(const uint8_t key[16], const uint8_t nonce_prefix[TELEMETRY_NONCE_PREFIX_LEN],
                               uint32_t counter, const uint8_t plaintext[TELEMETRY_PLAINTEXT_LEN],
                               uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN]);
bool telemetry_build_payload(const uint8_t key[16], const uint8_t nonce_prefix[TELEMETRY_NONCE_PREFIX_LEN],
                             uint32_t counter, int32_t temperature_centi_c, int64_t pressure_pa,
                             uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN]);
void telemetry_make_manufacturer_data(const uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN],
                                      uint8_t manufacturer_data[TELEMETRY_ADV_DATA_LEN]);
