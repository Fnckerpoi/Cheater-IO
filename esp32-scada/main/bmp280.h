#pragma once

#include <stdint.h>

#include "esp_err.h"

typedef struct {
    uint8_t address;
    uint16_t dig_t1;
    int16_t dig_t2;
    int16_t dig_t3;
    uint16_t dig_p1;
    int16_t dig_p2;
    int16_t dig_p3;
    int16_t dig_p4;
    int16_t dig_p5;
    int16_t dig_p6;
    int16_t dig_p7;
    int16_t dig_p8;
    int16_t dig_p9;
} bmp280_t;

typedef struct {
    int32_t temperature_centi_c;
    uint32_t pressure_pa;
} bmp280_sample_t;

esp_err_t bmp280_init(bmp280_t *device);
esp_err_t bmp280_read_sample(bmp280_t *device, bmp280_sample_t *sample);
