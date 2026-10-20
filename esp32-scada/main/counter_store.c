#include "counter_store.h"

#include "esp_log.h"

static const char *TAG = "counter_store";
static const char *HIGH_WATER_KEY = "ctr_hi";

esp_err_t counter_store_init(counter_store_t *store, nvs_handle_t nvs)
{
    if (store == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    uint64_t high_water = 0;
    esp_err_t err = nvs_get_u64(nvs, HIGH_WATER_KEY, &high_water);
    if (err != ESP_OK) {
        return err;
    }
    if (high_water > COUNTER_SPACE_END) {
        ESP_LOGE(TAG, "Stored counter high-water mark is invalid");
        return ESP_ERR_INVALID_SIZE;
    }

    store->nvs = nvs;
    store->next = high_water;
    store->end = high_water;
    return ESP_OK;
}

esp_err_t counter_store_next(counter_store_t *store, uint32_t *counter)
{
    if (store == NULL || counter == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    if (store->next == store->end) {
        if (store->end >= COUNTER_SPACE_END) {
            return ESP_ERR_INVALID_STATE;
        }

        const uint64_t start = store->end;
        uint64_t new_end = start + COUNTER_RESERVATION_BLOCK_SIZE;
        if (new_end > COUNTER_SPACE_END) {
            new_end = COUNTER_SPACE_END;
        }

        esp_err_t err = nvs_set_u64(store->nvs, HIGH_WATER_KEY, new_end);
        if (err != ESP_OK) {
            return err;
        }
        err = nvs_commit(store->nvs);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "Could not persist counter reservation (%s)", esp_err_to_name(err));
            return err;
        }
        store->next = start;
        store->end = new_end;
    }

    *counter = (uint32_t)store->next;
    store->next++;
    return ESP_OK;
}
