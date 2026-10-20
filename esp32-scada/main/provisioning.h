#pragma once

#include <stdint.h>

#include "esp_err.h"
#include "nvs.h"

#define DEVICE_KEY_LEN 16u
#define DEVICE_PREFIX_LEN 8u

typedef struct {
    uint8_t key[DEVICE_KEY_LEN];
    uint8_t nonce_prefix[DEVICE_PREFIX_LEN];
} device_credentials_t;

esp_err_t provisioning_load(nvs_handle_t nvs, device_credentials_t *credentials);
esp_err_t provisioning_store_once(nvs_handle_t nvs, const device_credentials_t *credentials);
