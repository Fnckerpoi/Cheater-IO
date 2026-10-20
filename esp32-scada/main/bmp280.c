#include "bmp280.h"

#include "driver/i2c.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define BMP280_REG_CHIP_ID 0xD0u
#define BMP280_REG_CALIBRATION 0x88u
#define BMP280_REG_STATUS 0xF3u
#define BMP280_REG_CONTROL 0xF4u
#define BMP280_REG_PRESSURE 0xF7u
#define BMP280_CHIP_ID 0x58u
#define BMP280_I2C_TIMEOUT_MS 100u

static esp_err_t read_registers(uint8_t address, uint8_t reg, uint8_t *data, size_t length)
{
    return i2c_master_write_read_device(I2C_NUM_0, address, &reg, 1, data, length,
                                        pdMS_TO_TICKS(BMP280_I2C_TIMEOUT_MS));
}

static esp_err_t write_register(uint8_t address, uint8_t reg, uint8_t value)
{
    const uint8_t bytes[2] = {reg, value};
    return i2c_master_write_to_device(I2C_NUM_0, address, bytes, sizeof(bytes),
                                      pdMS_TO_TICKS(BMP280_I2C_TIMEOUT_MS));
}

static uint16_t unsigned_le(const uint8_t *bytes)
{
    return (uint16_t)bytes[0] | ((uint16_t)bytes[1] << 8);
}

static int16_t signed_le(const uint8_t *bytes)
{
    const uint16_t bits = unsigned_le(bytes);
    int16_t value;
    __builtin_memcpy(&value, &bits, sizeof(value));
    return value;
}

esp_err_t bmp280_init(bmp280_t *device)
{
    if (device == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    static const char *TAG = "bmp280";
    static unsigned int probe_attempts;
    const bool log_probe = (probe_attempts++ % 30u) == 0u;
    const uint8_t addresses[] = {0x76u, 0x77u};
    for (size_t i = 0; i < sizeof(addresses); ++i) {
        uint8_t chip_id = 0;
        esp_err_t err = read_registers(addresses[i], BMP280_REG_CHIP_ID, &chip_id, sizeof(chip_id));
        if (err != ESP_OK || chip_id != BMP280_CHIP_ID) {
            if (log_probe) {
                if (err != ESP_OK) {
                    ESP_LOGW(TAG, "Probe 0x%02x chip ID read failed: %s", addresses[i], esp_err_to_name(err));
                } else {
                    ESP_LOGW(TAG, "Probe 0x%02x returned unexpected chip ID 0x%02x (expected 0x%02x)",
                             addresses[i], chip_id, BMP280_CHIP_ID);
                }
            }
            continue;
        }

        uint8_t calibration[24];
        err = read_registers(addresses[i], BMP280_REG_CALIBRATION, calibration, sizeof(calibration));
        if (err != ESP_OK) {
            return err;
        }

        device->address = addresses[i];
        device->dig_t1 = unsigned_le(&calibration[0]);
        device->dig_t2 = signed_le(&calibration[2]);
        device->dig_t3 = signed_le(&calibration[4]);
        device->dig_p1 = unsigned_le(&calibration[6]);
        device->dig_p2 = signed_le(&calibration[8]);
        device->dig_p3 = signed_le(&calibration[10]);
        device->dig_p4 = signed_le(&calibration[12]);
        device->dig_p5 = signed_le(&calibration[14]);
        device->dig_p6 = signed_le(&calibration[16]);
        device->dig_p7 = signed_le(&calibration[18]);
        device->dig_p8 = signed_le(&calibration[20]);
        device->dig_p9 = signed_le(&calibration[22]);
        if (device->dig_p1 == 0u) {
            return ESP_ERR_INVALID_RESPONSE;
        }
        return ESP_OK;
    }
    return ESP_ERR_NOT_FOUND;
}

static esp_err_t read_raw(uint8_t address, int32_t *raw_pressure, int32_t *raw_temperature)
{
    esp_err_t err = write_register(address, BMP280_REG_CONTROL, 0x25u); /* x1 pressure, x1 temperature, forced */
    if (err != ESP_OK) {
        return err;
    }

    bool measuring = true;
    for (unsigned int attempt = 0; attempt < 25u; ++attempt) {
        uint8_t status = 0;
        err = read_registers(address, BMP280_REG_STATUS, &status, sizeof(status));
        if (err != ESP_OK) {
            return err;
        }
        measuring = (status & 0x08u) != 0u;
        if (!measuring) {
            break;
        }
        vTaskDelay(pdMS_TO_TICKS(2));
    }
    if (measuring) {
        return ESP_ERR_TIMEOUT;
    }

    uint8_t data[6];
    err = read_registers(address, BMP280_REG_PRESSURE, data, sizeof(data));
    if (err != ESP_OK) {
        return err;
    }
    *raw_pressure = ((int32_t)data[0] << 12) | ((int32_t)data[1] << 4) | (data[2] >> 4);
    *raw_temperature = ((int32_t)data[3] << 12) | ((int32_t)data[4] << 4) | (data[5] >> 4);
    if (*raw_pressure == 0x80000 || *raw_temperature == 0x80000) {
        return ESP_ERR_INVALID_RESPONSE;
    }
    return ESP_OK;
}

esp_err_t bmp280_read_sample(bmp280_t *device, bmp280_sample_t *sample)
{
    if (device == NULL || sample == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    int32_t raw_pressure = 0;
    int32_t raw_temperature = 0;
    esp_err_t err = read_raw(device->address, &raw_pressure, &raw_temperature);
    if (err != ESP_OK) {
        return err;
    }

    const int64_t temperature_delta_1 = ((int64_t)raw_temperature >> 3) -
                                        ((int64_t)device->dig_t1 << 1);
    const int64_t var1 = (temperature_delta_1 * device->dig_t2) >> 11;
    const int64_t temperature_delta_2 = ((int64_t)raw_temperature >> 4) - device->dig_t1;
    const int64_t var2 = (((temperature_delta_2 * temperature_delta_2) >> 12) *
                          device->dig_t3) >> 14;
    const int64_t t_fine = var1 + var2;
    const int64_t temperature_centi_c = (t_fine * 5 + 128) >> 8;

    int64_t p_var1 = t_fine - 128000;
    int64_t p_var2 = p_var1 * p_var1 * device->dig_p6;
    p_var2 += p_var1 * device->dig_p5 * 131072LL;
    p_var2 += (int64_t)device->dig_p4 * 34359738368LL;
    p_var1 = ((p_var1 * p_var1 * device->dig_p3) >> 8) + (p_var1 * device->dig_p2 * 4096LL);
    p_var1 = (((1LL << 47) + p_var1) * device->dig_p1) >> 33;
    if (p_var1 == 0) {
        return ESP_ERR_INVALID_RESPONSE;
    }

    int64_t pressure = 1048576LL - raw_pressure;
    pressure = (((pressure * 2147483648LL) - p_var2) * 3125LL) / p_var1;
    p_var1 = ((int64_t)device->dig_p9 * (pressure >> 13) * (pressure >> 13)) >> 25;
    p_var2 = ((int64_t)device->dig_p8 * pressure) >> 19;
    pressure = ((pressure + p_var1 + p_var2) >> 8) + ((int64_t)device->dig_p7 * 16LL);
    const int64_t pressure_pa = (pressure + 128) >> 8;

    if (temperature_centi_c < -4000 || temperature_centi_c > 8500 ||
        pressure_pa < 30000 || pressure_pa > 110000) {
        return ESP_ERR_INVALID_RESPONSE;
    }

    sample->temperature_centi_c = temperature_centi_c;
    sample->pressure_pa = (uint32_t)pressure_pa;
    return ESP_OK;
}
