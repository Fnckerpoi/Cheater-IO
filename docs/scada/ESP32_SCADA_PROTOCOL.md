# ESP32-C3 BMP280 BLE Telemetry Protocol v1

Status: implementation contract for the single-device lab PoC.

## Transport

- BLE Legacy, non-connectable advertising.
- Manufacturer Specific Data with lab Company Identifier 0xFFFF. This internal-use identifier is for bench testing only; public/product deployment must use a valid assigned Company Identifier.
- Advertising Flags AD structure: 02 01 06.
- Manufacturer AD structure: length 0x19, type 0xFF, Company Identifier bytes FF FF, followed by the 22-byte application payload. Total with Flags: 29 bytes, within the 31-byte Legacy AdvData limit.
- Sample the sensor once per second and refresh the encrypted payload every 500 ms from the latest valid BMP280 sample. Each newly encrypted payload consumes one packet counter. BLE advertising events use a configured 100 ms interval and repeat the active payload, including its counter, until the next payload refresh; repeated events do not allocate new counters.

## Application payload

| Offset | Length | Field | Encoding |
| --- | ---: | --- | --- |
| 0 | 1 | protocol version | unsigned, value 0x01 |
| 1 | 4 | packet counter | unsigned 32-bit, big-endian |
| 5 | 5 | encrypted sample | AES-GCM ciphertext |
| 10 | 12 | authentication tag | AES-GCM tag, 96 bits |

The plaintext is exactly five bytes: signed temperature in centi-degrees Celsius as int16 big-endian, then ambient pressure in pascals as uint24 big-endian. Invalid sensor samples are not encoded as valid readings. The phone records reception time locally; there is no device wall-clock timestamp in v1.

## Cryptography

- AES-128-GCM, 96-bit nonce, 96-bit tag, no padding.
- Nonce = provisioned 8-byte device prefix || packet counter bytes as transmitted.
- AAD = Company Identifier bytes as transmitted || protocol version || packet counter bytes. The packet counter is therefore both nonce input and authenticated cleartext.
- The lab key is a random 16-byte value provisioned out of band into ESP32 NVS and Turbo-IO local protected storage. Never commit a live key or embed it as a default.
- Counter values must never repeat with the same key and device prefix. Reserve counter blocks in NVS before use and skip unused values after reboot. If the key, prefix, or counter high-water is reset, provision a new key before transmitting. Rotate before counter wrap.
- Android verifies authentication before updating replay state or telemetry. Reject duplicate and older counters; an older accepted packet must never roll back the displayed sample.

## Fixed cross-language test vector

This deterministic test key is for tests only and must not be used for live provisioning.

- Key: 000102030405060708090a0b0c0d0e0f
- Device prefix: 1011121314151617
- Counter: 01020304
- AAD: ffff0101020304
- Plaintext: 0929018bcd
- Expected ciphertext: d5a18df834
- Expected tag: 05b5915af4d0a3406bfe4324
- Expected application payload: 0101020304d5a18df83405b5915af4d0a3406bfe4324

## Presentation semantics

BMP280 values represent ambient temperature and barometric pressure only. Android formats units for the phone and RayNeo card. RSSI is a coarse proximity hint and is never an authentication signal. Android applies proximity only after authentication, field validation, and replay acceptance. By default, entry to near requires three consecutive qualifying readings at or above -70 dBm; while near, readings remain qualifying down to the entry threshold minus 6 dB of exit hysteresis. Readings below that exit threshold immediately clear near state. Stale and removal timers use the last qualifying near reading, not merely any valid advertisement: data is marked stale after 10 seconds without one and the published device data is removed (away) after 30 seconds without one.
