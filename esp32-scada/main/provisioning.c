#include "provisioning.h"

#include <string.h>

static const char *READY_KEY = "provisioned";
static const char *AES_KEY = "aes_key";
static const char *PREFIX_KEY = "dev_pref";
static const char *HIGH_WATER_KEY = "ctr_hi";

static esp_err_t read_exact_blob(nvs_handle_t nvs, const char *key, uint8_t *output, size_t expected_len)
{
    size_t length = expected_len;
    esp_err_t err = nvs_get_blob(nvs, key, output, &length);
    if (err != ESP_OK) {
        return err;
    }
    return length == expected_len ? ESP_OK : ESP_ERR_INVALID_SIZE;
}

esp_err_t provisioning_load(nvs_handle_t nvs, device_credentials_t *credentials)
{
    if (credentials == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t ready = 0;
    esp_err_t err = nvs_get_u8(nvs, READY_KEY, &ready);
    if (err != ESP_OK) {
        return err;
    }
    if (ready != 1) {
        return ESP_ERR_NOT_FOUND;
    }

    err = read_exact_blob(nvs, AES_KEY, credentials->key, sizeof(credentials->key));
    if (err != ESP_OK) {
        return err;
    }
    return read_exact_blob(nvs, PREFIX_KEY, credentials->nonce_prefix, sizeof(credentials->nonce_prefix));
}

esp_err_t provisioning_store_once(nvs_handle_t nvs, const device_credentials_t *credentials)
{
    if (credentials == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t ready = 0;
    esp_err_t err = nvs_get_u8(nvs, READY_KEY, &ready);
    if (err == ESP_OK && ready == 1) {
        return ESP_ERR_INVALID_STATE;
    }
    if (err != ESP_OK && err != ESP_ERR_NVS_NOT_FOUND) {
        return err;
    }

    const uint64_t first_counter = 0;
    ready = 1;
    err = nvs_set_blob(nvs, AES_KEY, credentials->key, sizeof(credentials->key));
    if (err == ESP_OK) {
        err = nvs_set_blob(nvs, PREFIX_KEY, credentials->nonce_prefix, sizeof(credentials->nonce_prefix));
    }
    if (err == ESP_OK) {
        err = nvs_set_u64(nvs, HIGH_WATER_KEY, first_counter);
    }
    if (err == ESP_OK) {
        err = nvs_set_u8(nvs, READY_KEY, ready);
    }
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    return err;
}
