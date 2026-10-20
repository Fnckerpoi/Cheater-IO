#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "bmp280.h"
#include "counter_store.h"
#include "provisioning.h"
#include "telemetry.h"

#include "driver/gpio.h"
#include "driver/i2c.h"
#include "esp_console.h"
#include "esp_err.h"
#include "esp_log.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "host/ble_hs.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"

#define I2C_SDA_GPIO GPIO_NUM_4
#define I2C_SCL_GPIO GPIO_NUM_5
#define I2C_CLOCK_HZ 100000
#define SAMPLE_PERIOD_MS 1000
#define PAYLOAD_REFRESH_PERIOD_MS 500
#define BLE_EVENT_INTERVAL_MS 100
#define BLE_READY_BIT BIT0

static const char *TAG = "esp32_scada";
static const char *NVS_NAMESPACE = "scada";

static nvs_handle_t g_nvs;
static device_credentials_t g_credentials;
static counter_store_t g_counter_store;
static bool g_provisioned;
static portMUX_TYPE g_config_mux = portMUX_INITIALIZER_UNLOCKED;

static bmp280_sample_t g_latest_sample;
static bool g_sample_valid;
static portMUX_TYPE g_sample_mux = portMUX_INITIALIZER_UNLOCKED;

static EventGroupHandle_t g_ble_events;
static SemaphoreHandle_t g_advertising_mutex;
static uint8_t g_own_address_type;

static bool config_snapshot(device_credentials_t *credentials)
{
    bool ready;
    portENTER_CRITICAL(&g_config_mux);
    ready = g_provisioned;
    if (ready && credentials != NULL) {
        *credentials = g_credentials;
    }
    portEXIT_CRITICAL(&g_config_mux);
    return ready;
}

static bool sample_snapshot(bmp280_sample_t *sample)
{
    bool valid;
    portENTER_CRITICAL(&g_sample_mux);
    valid = g_sample_valid;
    if (valid && sample != NULL) {
        *sample = g_latest_sample;
    }
    portEXIT_CRITICAL(&g_sample_mux);
    return valid;
}

static void publish_sample(const bmp280_sample_t *sample)
{
    portENTER_CRITICAL(&g_sample_mux);
    if (sample == NULL) {
        g_sample_valid = false;
    } else {
        g_latest_sample = *sample;
        g_sample_valid = true;
    }
    portEXIT_CRITICAL(&g_sample_mux);
}

static void stop_advertising(void)
{
    if (g_ble_events == NULL || (xEventGroupGetBits(g_ble_events) & BLE_READY_BIT) == 0) {
        return;
    }
    if (xSemaphoreTake(g_advertising_mutex, pdMS_TO_TICKS(100)) == pdTRUE) {
        (void)ble_gap_adv_stop();
        xSemaphoreGive(g_advertising_mutex);
    }
}

static int advertising_event(struct ble_gap_event *event, void *arg)
{
    (void)event;
    (void)arg;
    return 0;
}

static void update_advertisement(void)
{
    if (xSemaphoreTake(g_advertising_mutex, pdMS_TO_TICKS(250)) != pdTRUE) {
        return;
    }

    (void)ble_gap_adv_stop();
    bmp280_sample_t sample;
    device_credentials_t credentials;
    if (!sample_snapshot(&sample) || !config_snapshot(&credentials)) {
        xSemaphoreGive(g_advertising_mutex);
        return;
    }

    uint32_t counter = 0;
    esp_err_t err = counter_store_next(&g_counter_store, &counter);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Counter unavailable; advertisement suppressed (%s)", esp_err_to_name(err));
        xSemaphoreGive(g_advertising_mutex);
        return;
    }

    uint8_t payload[TELEMETRY_APP_PAYLOAD_LEN];
    if (!telemetry_build_payload(credentials.key, credentials.nonce_prefix, counter,
                                 sample.temperature_centi_c, sample.pressure_pa, payload)) {
        ESP_LOGE(TAG, "Could not encode sample; advertisement suppressed");
        xSemaphoreGive(g_advertising_mutex);
        return;
    }

    uint8_t manufacturer_data[TELEMETRY_ADV_DATA_LEN];
    telemetry_make_manufacturer_data(payload, manufacturer_data);
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.mfg_data = manufacturer_data;
    fields.mfg_data_len = sizeof(manufacturer_data);

    int rc = ble_gap_adv_set_fields(&fields);
    if (rc == 0) {
        struct ble_gap_adv_params params = {0};
        // NimBLE repeats these fields every BLE_EVENT_INTERVAL_MS until the next payload refresh.
        params.conn_mode = BLE_GAP_CONN_MODE_NON;
        params.disc_mode = BLE_GAP_DISC_MODE_GEN;
        params.itvl_min = BLE_GAP_ADV_ITVL_MS(BLE_EVENT_INTERVAL_MS);
        params.itvl_max = BLE_GAP_ADV_ITVL_MS(BLE_EVENT_INTERVAL_MS);
        params.channel_map = 0x07;
        rc = ble_gap_adv_start(g_own_address_type, NULL, BLE_HS_FOREVER,
                               &params, advertising_event, NULL);
    }
    if (rc != 0) {
        ESP_LOGW(TAG, "BLE advertising update failed (%d)", rc);
    } else {
        ESP_LOGD(TAG, "Advertising telemetry counter %lu", (unsigned long)counter);
    }
    xSemaphoreGive(g_advertising_mutex);
}

static void ble_sync(void)
{
    int rc = ble_hs_id_infer_auto(0, &g_own_address_type);
    if (rc != 0) {
        ESP_LOGE(TAG, "BLE address setup failed (%d)", rc);
        return;
    }
    xEventGroupSetBits(g_ble_events, BLE_READY_BIT);
}

static void ble_reset(int reason)
{
    ESP_LOGW(TAG, "BLE host reset (%d)", reason);
    xEventGroupClearBits(g_ble_events, BLE_READY_BIT);
}

static void ble_host_task(void *arg)
{
    (void)arg;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

static void advertising_task(void *arg)
{
    (void)arg;
    TickType_t last_wake = xTaskGetTickCount();
    for (;;) {
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(PAYLOAD_REFRESH_PERIOD_MS));
        if ((xEventGroupGetBits(g_ble_events) & BLE_READY_BIT) != 0) {
            update_advertisement();
        }
    }
}

static esp_err_t i2c_setup(void)
{
    const i2c_config_t config = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = I2C_SDA_GPIO,
        .scl_io_num = I2C_SCL_GPIO,
        .sda_pullup_en = GPIO_PULLUP_ENABLE,
        .scl_pullup_en = GPIO_PULLUP_ENABLE,
        .master.clk_speed = I2C_CLOCK_HZ,
        .clk_flags = 0
    };
    esp_err_t err = i2c_param_config(I2C_NUM_0, &config);
    if (err != ESP_OK) {
        return err;
    }
    return i2c_driver_install(I2C_NUM_0, I2C_MODE_MASTER, 0, 0, 0);
}

static void sample_task(void *arg)
{
    (void)arg;
    bool bus_ready = false;
    bool sensor_ready = false;
    bmp280_t sensor = {0};
    TickType_t last_wake = xTaskGetTickCount();

    for (;;) {
        esp_err_t err = ESP_OK;
        if (!bus_ready) {
            err = i2c_setup();
            bus_ready = err == ESP_OK;
            if (!bus_ready) {
                ESP_LOGW(TAG, "I2C setup failed (%s)", esp_err_to_name(err));
            }
        }
        if (bus_ready && !sensor_ready) {
            err = bmp280_init(&sensor);
            sensor_ready = err == ESP_OK;
            if (sensor_ready) {
                ESP_LOGI(TAG, "BMP280 detected at 0x%02x", sensor.address);
            } else {
                ESP_LOGW(TAG, "BMP280 unavailable (%s)", esp_err_to_name(err));
            }
        }

        bmp280_sample_t sample;
        if (sensor_ready) {
            err = bmp280_read_sample(&sensor, &sample);
            if (err == ESP_OK) {
                publish_sample(&sample);
                ESP_LOGD(TAG, "Sample: %ld centi-C, %lu Pa",
                         (long)sample.temperature_centi_c, (unsigned long)sample.pressure_pa);
            } else {
                sensor_ready = false;
                publish_sample(NULL);
                stop_advertising();
                ESP_LOGW(TAG, "BMP280 sample failed (%s); telemetry suppressed", esp_err_to_name(err));
            }
        } else {
            publish_sample(NULL);
            stop_advertising();
        }
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(SAMPLE_PERIOD_MS));
    }
}

static int hex_value(char character)
{
    if (character >= '0' && character <= '9') return character - '0';
    if (character >= 'a' && character <= 'f') return character - 'a' + 10;
    if (character >= 'A' && character <= 'F') return character - 'A' + 10;
    return -1;
}

static bool parse_hex(const char *text, uint8_t *output, size_t output_len)
{
    if (text == NULL || output == NULL || strlen(text) != output_len * 2u) {
        return false;
    }
    for (size_t i = 0; i < output_len; ++i) {
        int high = hex_value(text[i * 2u]);
        int low = hex_value(text[i * 2u + 1u]);
        if (high < 0 || low < 0) {
            return false;
        }
        output[i] = (uint8_t)((high << 4) | low);
    }
    return true;
}

static int provision_command(int argc, char **argv)
{
    if (argc != 3) {
        printf("Usage: provision <32-hex-digit-key> <16-hex-digit-device-prefix>\n");
        return 1;
    }
    if (config_snapshot(NULL)) {
        printf("This device is already provisioned; provisioning is one-time.\n");
        return 1;
    }

    device_credentials_t credentials = {0};
    if (!parse_hex(argv[1], credentials.key, sizeof(credentials.key)) ||
        !parse_hex(argv[2], credentials.nonce_prefix, sizeof(credentials.nonce_prefix))) {
        printf("Invalid hex length or character. No credentials were stored.\n");
        return 1;
    }

    esp_err_t err = provisioning_store_once(g_nvs, &credentials);
    if (err != ESP_OK) {
        printf("Provisioning failed (%s).\n", esp_err_to_name(err));
        memset(&credentials, 0, sizeof(credentials));
        return 1;
    }

    counter_store_t counter_store;
    err = counter_store_init(&counter_store, g_nvs);
    if (err != ESP_OK) {
        printf("Credentials were stored, but counter state could not be loaded (%s); reboot and inspect NVS.\n",
               esp_err_to_name(err));
        memset(&credentials, 0, sizeof(credentials));
        return 1;
    }

    portENTER_CRITICAL(&g_config_mux);
    g_credentials = credentials;
    g_counter_store = counter_store;
    g_provisioned = true;
    portEXIT_CRITICAL(&g_config_mux);
    memset(&credentials, 0, sizeof(credentials));
    printf("Provisioning complete. Key and device prefix are stored in NVS.\n");
    return 0;
}

static void start_console(void)
{
    esp_console_dev_usb_serial_jtag_config_t usb_config = ESP_CONSOLE_DEV_USB_SERIAL_JTAG_CONFIG_DEFAULT();
    esp_console_repl_config_t repl_config = ESP_CONSOLE_REPL_CONFIG_DEFAULT();
    repl_config.prompt = "scada>";
    esp_console_repl_t *repl = NULL;
    ESP_ERROR_CHECK(esp_console_new_repl_usb_serial_jtag(&usb_config, &repl_config, &repl));

    const esp_console_cmd_t provision = {
        .command = "provision",
        .help = "Store this device's AES key and 8-byte nonce prefix once",
        .hint = NULL,
        .func = provision_command,
        .argtable = NULL
    };
    ESP_ERROR_CHECK(esp_console_cmd_register(&provision));

    ESP_ERROR_CHECK(esp_console_start_repl(repl));
}

void app_main(void)
{
    esp_err_t err = nvs_flash_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "NVS init failed (%s); refusing to erase or reset counter state", esp_err_to_name(err));
        return;
    }
    err = nvs_open(NVS_NAMESPACE, NVS_READWRITE, &g_nvs);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "NVS open failed (%s)", esp_err_to_name(err));
        return;
    }

    device_credentials_t credentials;
    err = provisioning_load(g_nvs, &credentials);
    if (err == ESP_OK) {
        counter_store_t counter_store;
        err = counter_store_init(&counter_store, g_nvs);
        if (err == ESP_OK) {
            portENTER_CRITICAL(&g_config_mux);
            g_credentials = credentials;
            g_counter_store = counter_store;
            g_provisioned = true;
            portEXIT_CRITICAL(&g_config_mux);
            ESP_LOGI(TAG, "Device credentials loaded from NVS");
        } else {
            ESP_LOGE(TAG, "Counter state unavailable (%s); telemetry is disabled", esp_err_to_name(err));
        }
        memset(&credentials, 0, sizeof(credentials));
    } else if (err == ESP_ERR_NVS_NOT_FOUND) {
        ESP_LOGW(TAG, "Not provisioned. Use the local serial command: provision <key-hex> <prefix-hex>");
    } else {
        ESP_LOGE(TAG, "Provisioning data is invalid (%s); telemetry is disabled", esp_err_to_name(err));
    }

    g_ble_events = xEventGroupCreate();
    g_advertising_mutex = xSemaphoreCreateMutex();
    if (g_ble_events == NULL || g_advertising_mutex == NULL) {
        ESP_LOGE(TAG, "Could not allocate BLE synchronization objects");
        return;
    }

    start_console();

    err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "NimBLE init failed (%s)", esp_err_to_name(err));
        return;
    }
    ble_hs_cfg.sync_cb = ble_sync;
    ble_hs_cfg.reset_cb = ble_reset;
    nimble_port_freertos_init(ble_host_task);

    BaseType_t sample_created = xTaskCreate(sample_task, "sample", 4096, NULL, 5, NULL);
    BaseType_t advertising_created = xTaskCreate(advertising_task, "advertise", 4096, NULL, 5, NULL);
    if (sample_created != pdPASS || advertising_created != pdPASS) {
        ESP_LOGE(TAG, "Could not create firmware tasks");
    }
}
