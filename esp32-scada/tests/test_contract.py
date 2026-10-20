"""Dependency-free protocol reference tests. The fixed key is test-only."""

import re
from pathlib import Path
import unittest

TEST_KEY = bytes.fromhex("000102030405060708090a0b0c0d0e0f")
TEST_PREFIX = bytes.fromhex("1011121314151617")
TEST_COUNTER = bytes.fromhex("01020304")
TEST_AAD = bytes.fromhex("ffff0101020304")
TEST_PLAINTEXT = bytes.fromhex("0929018bcd")
EXPECTED_CIPHERTEXT = bytes.fromhex("d5a18df834")
EXPECTED_TAG = bytes.fromhex("05b5915af4d0a3406bfe4324")
EXPECTED_APP = bytes.fromhex("0101020304d5a18df83405b5915af4d0a3406bfe4324")
COUNTER_END = 1 << 32
COUNTER_BLOCK = 4096


def _gf_mul_byte(left, right):
    result = 0
    for _ in range(8):
        if right & 1:
            result ^= left
        carry = left & 0x80
        left = (left << 1) & 0xFF
        if carry:
            left ^= 0x1B
        right >>= 1
    return result


def _gf_pow_byte(value, exponent):
    result = 1
    while exponent:
        if exponent & 1:
            result = _gf_mul_byte(result, value)
        value = _gf_mul_byte(value, value)
        exponent >>= 1
    return result


def _rotl8(value, count):
    return ((value << count) | (value >> (8 - count))) & 0xFF


def _make_sbox():
    box = []
    for value in range(256):
        inverse = 0 if value == 0 else _gf_pow_byte(value, 254)
        box.append(inverse ^ _rotl8(inverse, 1) ^ _rotl8(inverse, 2) ^
                   _rotl8(inverse, 3) ^ _rotl8(inverse, 4) ^ 0x63)
    return tuple(box)


_SBOX = _make_sbox()


def _round_keys(key):
    words = [list(key[i:i + 4]) for i in range(0, 16, 4)]
    rcon = 1
    for index in range(4, 44):
        temp = words[index - 1][:]
        if index % 4 == 0:
            temp = temp[1:] + temp[:1]
            temp = [_SBOX[byte] for byte in temp]
            temp[0] ^= rcon
            rcon = _gf_mul_byte(rcon, 2)
        words.append([a ^ b for a, b in zip(words[index - 4], temp)])
    return [sum(words[i:i + 4], []) for i in range(0, 44, 4)]


def _aes128_encrypt_block(key, block):
    round_keys = _round_keys(key)
    state = list(block)
    state = [a ^ b for a, b in zip(state, round_keys[0])]
    for round_index in range(1, 11):
        state = [_SBOX[byte] for byte in state]
        state = [state[4 * ((column + row) % 4) + row]
                 for column in range(4) for row in range(4)]
        if round_index != 10:
            mixed = state[:]
            for column in range(4):
                offset = column * 4
                a0, a1, a2, a3 = state[offset:offset + 4]
                mixed[offset] = _gf_mul_byte(a0, 2) ^ _gf_mul_byte(a1, 3) ^ a2 ^ a3
                mixed[offset + 1] = a0 ^ _gf_mul_byte(a1, 2) ^ _gf_mul_byte(a2, 3) ^ a3
                mixed[offset + 2] = a0 ^ a1 ^ _gf_mul_byte(a2, 2) ^ _gf_mul_byte(a3, 3)
                mixed[offset + 3] = _gf_mul_byte(a0, 3) ^ a1 ^ a2 ^ _gf_mul_byte(a3, 2)
            state = mixed
        state = [a ^ b for a, b in zip(state, round_keys[round_index])]
    return bytes(state)


def _gf_mul_128(left, right):
    product = 0
    factor = right
    for bit in range(128):
        if (left >> (127 - bit)) & 1:
            product ^= factor
        factor = (factor >> 1) ^ (0xE1000000000000000000000000000000 if factor & 1 else 0)
    return product


def _ghash(hash_subkey, aad, ciphertext):
    h = int.from_bytes(hash_subkey, "big")
    accumulator = 0
    for data in (aad, ciphertext):
        for offset in range(0, len(data), 16):
            block = data[offset:offset + 16].ljust(16, b"\x00")
            accumulator = _gf_mul_128(accumulator ^ int.from_bytes(block, "big"), h)
    lengths = (len(aad) * 8).to_bytes(8, "big") + (len(ciphertext) * 8).to_bytes(8, "big")
    return _gf_mul_128(accumulator ^ int.from_bytes(lengths, "big"), h).to_bytes(16, "big")


def aes_gcm_encrypt(key, nonce, aad, plaintext):
    hash_subkey = _aes128_encrypt_block(key, bytes(16))
    initial_counter = nonce + b"\x00\x00\x00\x01"
    counter = int.from_bytes(initial_counter[12:], "big")
    ciphertext = bytearray()
    for offset in range(0, len(plaintext), 16):
        counter = (counter + 1) & 0xFFFFFFFF
        stream = _aes128_encrypt_block(key, initial_counter[:12] + counter.to_bytes(4, "big"))
        chunk = plaintext[offset:offset + 16]
        ciphertext.extend(left ^ right for left, right in zip(chunk, stream))
    ciphertext = bytes(ciphertext)
    tag_mask = _aes128_encrypt_block(key, initial_counter)
    authentication = _ghash(hash_subkey, aad, ciphertext)
    tag = bytes(left ^ right for left, right in zip(tag_mask, authentication))[:12]
    return ciphertext, tag


def pack_plaintext(temperature_centi_c, pressure_pa):
    if not -(1 << 15) <= temperature_centi_c < (1 << 15):
        raise ValueError("temperature outside int16 range")
    if not 0 <= pressure_pa < (1 << 24):
        raise ValueError("pressure outside uint24 range")
    return temperature_centi_c.to_bytes(2, "big", signed=True) + pressure_pa.to_bytes(3, "big")


class CounterStore:
    """Small model of the firmware's exclusive persisted high-water mark."""

    def __init__(self, persisted_high_water=0, block_size=COUNTER_BLOCK):
        if not 0 <= persisted_high_water <= COUNTER_END:
            raise ValueError("invalid high-water mark")
        self.persisted_high_water = persisted_high_water
        self.next_value = persisted_high_water
        self.end = persisted_high_water
        self.block_size = block_size

    def next(self):
        if self.next_value == self.end:
            if self.end >= COUNTER_END:
                raise OverflowError("counter space exhausted")
            start = self.end
            self.end = min(start + self.block_size, COUNTER_END)
            self.persisted_high_water = self.end
            self.next_value = start
        value = self.next_value
        self.next_value += 1
        return value


def advertisement_for_sample(sample_valid, payload):
    return payload if sample_valid else None


class ProtocolContractTests(unittest.TestCase):
    def test_ble_events_repeat_each_payload_before_refresh(self):
        source = (Path(__file__).resolve().parents[1] / "main" / "main.c").read_text(encoding="utf-8")
        values = {name: int(value) for name, value in re.findall(
            r"#define (PAYLOAD_REFRESH_PERIOD_MS|BLE_EVENT_INTERVAL_MS) (\d+)", source
        )}
        self.assertEqual(values, {"PAYLOAD_REFRESH_PERIOD_MS": 500, "BLE_EVENT_INTERVAL_MS": 100})
        self.assertLess(values["BLE_EVENT_INTERVAL_MS"], values["PAYLOAD_REFRESH_PERIOD_MS"])
        self.assertGreaterEqual(values["PAYLOAD_REFRESH_PERIOD_MS"], 2 * values["BLE_EVENT_INTERVAL_MS"])
        self.assertEqual(source.count("BLE_GAP_ADV_ITVL_MS(BLE_EVENT_INTERVAL_MS)"), 2)
        self.assertEqual(source.count("counter_store_next(&g_counter_store, &counter)"), 1)

    def test_fixed_aes_gcm_vector_and_application_payload(self):
        ciphertext, tag = aes_gcm_encrypt(TEST_KEY, TEST_PREFIX + TEST_COUNTER, TEST_AAD, TEST_PLAINTEXT)
        self.assertEqual(ciphertext, EXPECTED_CIPHERTEXT)
        self.assertEqual(tag, EXPECTED_TAG)
        payload = b"\x01" + TEST_COUNTER + ciphertext + tag
        self.assertEqual(len(payload), 22)
        self.assertEqual(payload, EXPECTED_APP)
        self.assertEqual(TEST_AAD, b"\xff\xff\x01" + TEST_COUNTER)

    def test_plaintext_integer_ranges_and_big_endian_packing(self):
        self.assertEqual(pack_plaintext(2345, 101325), bytes.fromhex("0929018bcd"))
        self.assertEqual(pack_plaintext(-32768, 0), bytes.fromhex("8000000000"))
        self.assertEqual(pack_plaintext(32767, 0xFFFFFF), bytes.fromhex("7fffffffff"))
        for temperature, pressure in ((-32769, 0), (32768, 0), (0, -1), (0, 0x1000000)):
            with self.subTest(temperature=temperature, pressure=pressure):
                with self.assertRaises(ValueError):
                    pack_plaintext(temperature, pressure)

    def test_counter_block_is_committed_and_unused_values_are_skipped_on_reboot(self):
        first_boot = CounterStore()
        self.assertEqual(first_boot.next(), 0)
        self.assertEqual(first_boot.next(), 1)
        self.assertEqual(first_boot.persisted_high_water, COUNTER_BLOCK)

        rebooted = CounterStore(first_boot.persisted_high_water)
        self.assertEqual(rebooted.next(), COUNTER_BLOCK)
        self.assertEqual(rebooted.persisted_high_water, COUNTER_BLOCK * 2)

    def test_counter_stops_at_uint32_boundary_without_wrapping(self):
        store = CounterStore(COUNTER_END - 2, block_size=COUNTER_BLOCK)
        self.assertEqual(store.next(), 0xFFFFFFFE)
        self.assertEqual(store.next(), 0xFFFFFFFF)
        with self.assertRaises(OverflowError):
            store.next()

    def test_invalid_sensor_sample_suppresses_telemetry(self):
        encoded = b"previously-valid-payload"
        self.assertIsNone(advertisement_for_sample(False, encoded))
        self.assertEqual(advertisement_for_sample(True, encoded), encoded)


if __name__ == "__main__":
    unittest.main()
