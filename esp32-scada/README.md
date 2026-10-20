# ESP32-C3 BMP280 telemetry PoC

ESP-IDF firmware for the ESP32-C3-DevKitM-1. It samples a BMP280 once per second and refreshes the encrypted telemetry payload every 500 ms. Each payload gets one new reserved counter; the BLE stack broadcasts that same payload at a configured 100 ms event interval until the next refresh. No key or device prefix is built into the firmware.

## Wiring

| ESP32-C3-DevKitM-1 | BMP280 breakout |
| --- | --- |
| 3V3 | VCC / VIN (use a 3.3 V compatible breakout) |
| GND | GND |
| GPIO4 | SDA |
| GPIO5 | SCL |

The firmware probes I2C address 0x76 and then 0x77; the BMP280 chip ID must be 0x58. Use a BMP280 module with I2C pull-ups. A BME280 is not accepted as a BMP280.

## Build and flash

Use ESP-IDF 5.5.5 and export its environment in the terminal. The confirmed board is an ESP32-C3 QFN32 rev v0.4 with 4 MB XMC flash, connected through native USB Serial/JTAG on COM3. `CONFIG_ESPTOOLPY_FLASHSIZE_4MB=y` configures the build and flash arguments for its 4 MB flash. From this directory:

    idf.py set-target esp32c3
    idf.py build
    idf.py -p COM3 flash monitor

Replace COM3 if the board enumerates on another port. `CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y` selects native USB Serial/JTAG as the primary console, and `CONFIG_ESP_CONSOLE_SECONDARY_NONE=y` disables secondary output. The `scada>` REPL uses `esp_console_new_repl_usb_serial_jtag`, so both console output and local provisioning input use this USB port; no UART0 adapter or UART baud-rate setting is required. This is the ESP32-C3 USB Serial/JTAG controller, not the USB OTG CDC console.

If an existing `sdkconfig` still selects UART0, rename it before running `set-target` and `build` so `sdkconfig.defaults` is applied again (retain any other intentional local settings). This regenerates local build configuration; it does not erase device NVS. To monitor firmware already flashed with these settings, run `idf.py -p COM3 monitor`. To run the host-side contract tests with Python 3:

    python -B -m unittest discover -s tests -v

## Local serial provisioning

Generate a fresh, unique 16-byte key and 8-byte nonce prefix for this device with a trusted local Python terminal:

    python -c "import secrets; print(secrets.token_hex(16), secrets.token_hex(8))"

At the device's scada> serial prompt, enter the returned values:

    provision <32-hex-digit-key> <16-hex-digit-device-prefix>

The command validates both lengths and stores the key, prefix, and initial counter high-water mark in NVS. It does not print them back. The console may echo typed input, so provision only through a trusted local serial session and do not share its transcript. Provisioning is one-time; firmware refuses to replace existing credentials. Keep a record of the provisioned key in the approved secure system used by the receiver.

Never provision the fixed test-vector key as a live key. If NVS is erased or the board is replaced, provision a newly generated key and prefix. Reusing the same key and prefix with a reset counter would reuse AES-GCM nonces. Do not erase NVS as a recovery step unless you will provision a fresh key/prefix pair.

## BLE packet layout

Manufacturer Specific Data uses Company Identifier 0xFFFF, transmitted little-endian as FF FF. The 22-byte application payload is:

| Offset | Length | Value |
| ---: | ---: | --- |
| 0 | 1 | Version 01 |
| 1 | 4 | Counter, unsigned big-endian |
| 5 | 5 | AES-128-GCM ciphertext |
| 10 | 12 | AES-GCM authentication tag |

The plaintext is signed int16 temperature in centi-degrees Celsius, big-endian, followed by unsigned uint24 pressure in pascals, big-endian. The 12-byte nonce is the provisioned 8-byte device prefix followed by that advertisement's 4 counter bytes. AAD is FF FF, version, and counter bytes. The AES-GCM key is read from NVS; encryption uses ESP-IDF mbedTLS.

The legacy advertisement contains a 3-byte Flags AD structure and a 26-byte Manufacturer Specific Data AD structure (2 bytes AD framing, 2 company bytes, 22 application bytes): 29 of the 31 available bytes. Advertising is non-connectable. The encrypted payload is refreshed every 500 ms from the latest valid sensor sample; BLE advertising events use a configured 100 ms interval (plus the BLE legacy advertising random delay), so each payload is sent repeatedly before refresh. Those repeated events reuse the payload and counter; only newly encrypted payloads consume a new counter. An I2C, conversion, or range error suppresses telemetry until a valid sample is available.

The exclusive counter high-water mark is committed to NVS before a block of 4096 counters is used. After reboot the firmware starts at the saved high-water mark, skipping any unused values in the previous block. It never wraps the 32-bit counter; counter exhaustion suppresses advertisements.

## Test vector and limitations

 tests/test_contract.py contains the fixed protocol test key and checks its AES-GCM vector with a dependency-free reference implementation, plus packing ranges, counter reservation/reboot behavior, and invalid-sample suppression. The test key exists only in that test file. These host tests verify the wire contract; they do not compile the ESP-IDF C firmware or replace an ESP-IDF build.

Hardware validation has not been performed. Confirm the BMP280 wiring and BLE reception on the actual ESP32-C3-DevKitM-1 before treating this PoC as field-ready. The project does not enable NVS encryption or flash encryption; protect device access and secure flash through the deployment process.
