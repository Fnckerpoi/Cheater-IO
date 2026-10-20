idf_component_register(
    SRCS
        "main.c"
        "bmp280.c"
        "telemetry.c"
        "counter_store.c"
        "provisioning.c"
    INCLUDE_DIRS "."
    REQUIRES bt driver console mbedtls nvs_flash
)
