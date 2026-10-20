#pragma once

#include <stdint.h>

#include "esp_err.h"
#include "nvs.h"

#define COUNTER_RESERVATION_BLOCK_SIZE 4096u
#define COUNTER_SPACE_END UINT64_C(0x100000000)

typedef struct {
    nvs_handle_t nvs;
    uint64_t next;
    uint64_t end;
} counter_store_t;

esp_err_t counter_store_init(counter_store_t *store, nvs_handle_t nvs);
esp_err_t counter_store_next(counter_store_t *store, uint32_t *counter);
