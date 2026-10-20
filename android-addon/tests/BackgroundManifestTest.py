import pathlib
import struct
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from background_manifest import OTA_SERVICE, PERMISSIONS, SCAN_PERMISSION, SERVICE, patch
from tts_manifest import NS, U, patch as patch_tts


def _pool(values):
    offsets = []
    raw = b''
    for value in values:
        encoded = value.encode('utf-8')
        offsets.append(len(raw))
        raw += bytes((len(value), len(encoded))) + encoded + b'\0'
    raw += b'\0' * (-len(raw) % 4)
    start = 28 + 4 * len(values)
    size = start + len(raw)
    header = struct.pack('<HHIIIIII', 1, 28, size, len(values), 0, 0x100, start, 0)
    return header + b''.join(struct.pack('<I', offset) for offset in offsets) + raw


def _record(ns_index, name_index, raw, value_type, value):
    return struct.pack('<IIIHBBI', ns_index, name_index, raw, 8, 0, value_type, value)


def _start(name_index, attrs=()):
    return (struct.pack('<HHIII', 0x102, 16, 36 + 20 * len(attrs), 0, U)
            + struct.pack('<IIHHHHHH', U, name_index, 20, 20, len(attrs), 0, 0, 0)
            + b''.join(attrs))


def _end(name_index):
    return struct.pack('<HHIIIII', 0x103, 16, 24, 0, U, U, name_index)


def _base_manifest(existing_services=(), existing_scan_flags=False):
    values = [NS, 'manifest', 'queries', 'intent', 'action', 'name', 'uses-permission',
              'application', 'service', 'exported', 'foregroundServiceType', 'usesPermissionFlags',
              SERVICE, OTA_SERVICE, SCAN_PERMISSION, 'android.permission.ACCESS_FINE_LOCATION']
    ix = {value: index for index, value in enumerate(values)}
    ids = [0] * len(values)
    ids[ix['name']] = 0x01010003
    ids[ix['exported']] = 0x01010010
    ids[ix['foregroundServiceType']] = 0x010105A3
    ids[ix['usesPermissionFlags']] = 0x01010644
    resource_map = struct.pack('<HHI', 0x180, 8, 8 + 4 * len(ids)) + struct.pack('<' + 'I' * len(ids), *ids)
    fine_location = _record(ix[NS], ix['name'], ix['android.permission.ACCESS_FINE_LOCATION'], 3,
                            ix['android.permission.ACCESS_FINE_LOCATION'])
    permission_nodes = [_start(ix['uses-permission'], [fine_location]), _end(ix['uses-permission'])]
    if existing_scan_flags:
        scan_name = _record(ix[NS], ix['name'], ix[SCAN_PERMISSION], 3, ix[SCAN_PERMISSION])
        scan_flags = _record(ix[NS], ix['usesPermissionFlags'], U, 0x11, 0x00010000)
        permission_nodes.extend([_start(ix['uses-permission'], [scan_name, scan_flags]),
                                 _end(ix['uses-permission'])])
    app_nodes = [_start(ix['application'])]
    for class_name, service_type in existing_services:
        attrs = [
            _record(ix[NS], ix['name'], ix[class_name], 3, ix[class_name]),
            _record(ix[NS], ix['exported'], U, 0x12, 1),
            _record(ix[NS], ix['foregroundServiceType'], U, 0x11, service_type),
        ]
        app_nodes.extend([_start(ix['service'], attrs), _end(ix['service'])])
    app_nodes.append(_end(ix['application']))
    chunks = [
        _pool(values), resource_map,
        _start(ix['manifest']),
        _start(ix['queries']), _end(ix['queries']),
        *permission_nodes,
        *app_nodes,
        _end(ix['manifest']),
    ]
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body


def _read_manifest(data):
    chunks = []
    offset = 8
    while offset < len(data):
        size = struct.unpack_from('<I', data, offset + 4)[0]
        chunks.append(data[offset:offset + size])
        offset += size
    pool = next(chunk for chunk in chunks if struct.unpack_from('<H', chunk)[0] == 1)
    string_count = struct.unpack_from('<I', pool, 8)[0]
    string_start = struct.unpack_from('<I', pool, 20)[0]
    strings = []
    for index in range(string_count):
        pos = string_start + struct.unpack_from('<I', pool, 28 + index * 4)[0]
        byte_len = pool[pos + 1]
        strings.append(pool[pos + 2:pos + 2 + byte_len].decode('utf-8'))
    permission_attrs = {}
    service_nodes = []
    for chunk in chunks:
        if struct.unpack_from('<H', chunk)[0] != 0x102:
            continue
        name = strings[struct.unpack_from('<I', chunk, 20)[0]]
        if name not in ('uses-permission', 'service'):
            continue
        attr_start = 16 + struct.unpack_from('<H', chunk, 24)[0]
        attr_size = struct.unpack_from('<H', chunk, 26)[0]
        attr_count = struct.unpack_from('<H', chunk, 28)[0]
        attrs = {}
        for i in range(attr_count):
            attr = chunk[attr_start + i * attr_size:attr_start + (i + 1) * attr_size]
            attr_name = strings[struct.unpack_from('<I', attr, 4)[0]]
            value_type = attr[15]
            value = struct.unpack_from('<I', attr, 16)[0]
            if value_type == 3:
                value = strings[value]
            elif value_type == 0x12:
                value = bool(value)
            attrs[attr_name] = value
        if name == 'uses-permission':
            permission_attrs[attrs['name']] = attrs
        else:
            service_nodes.append(attrs)
    return permission_attrs, service_nodes


def _assert_manifest(test, original, expected_service_types=(154, 16)):
    result = patch(original)
    test.assertEqual(patch(result), result, 'patch must be idempotent')
    permissions, service_nodes = _read_manifest(result)
    services = {attrs['name']: attrs for attrs in service_nodes}
    for permission, maximum, _flags in PERMISSIONS:
        test.assertIn(permission, permissions)
        attrs = permissions[permission]
        if maximum is not None:
            test.assertEqual(attrs.get('maxSdkVersion'), maximum, permission)
        else:
            test.assertNotIn('maxSdkVersion', attrs, permission)
    test.assertNotIn('usesPermissionFlags', permissions[SCAN_PERMISSION])
    test.assertNotIn('android.permission.BLUETOOTH_CONNECT', permissions)
    test.assertEqual(sum(attrs['name'] == SERVICE for attrs in service_nodes), 1)
    test.assertEqual(sum(attrs['name'] == OTA_SERVICE for attrs in service_nodes), 1)
    test.assertEqual(services[SERVICE]['exported'], False)
    test.assertEqual(services[SERVICE]['foregroundServiceType'], expected_service_types[0])
    test.assertEqual(services[OTA_SERVICE]['exported'], False)
    test.assertEqual(services[OTA_SERVICE]['foregroundServiceType'], expected_service_types[1])
    return result


class Test(unittest.TestCase):
    def test_synthetic_manifest_patches_even_when_service_names_are_in_pool(self):
        original = _base_manifest()
        _assert_manifest(self, original)
        permissions, _ = _read_manifest(patch(original))
        self.assertEqual(permissions['android.permission.ACCESS_FINE_LOCATION'].get('maxSdkVersion'), None)
        self.assertNotIn('maxSdkVersion', permissions['android.permission.ACCESS_COARSE_LOCATION'])
        self.assertEqual(permissions['android.permission.BLUETOOTH']['maxSdkVersion'], 30)
        self.assertEqual(permissions['android.permission.BLUETOOTH_ADMIN']['maxSdkVersion'], 30)
        self.assertEqual(permissions['android.permission.ACCESS_BACKGROUND_LOCATION']['maxSdkVersion'], 30)

    def test_existing_services_preserve_type_bits_without_duplicates(self):
        original = _base_manifest(((SERVICE, 1), (OTA_SERVICE, 2)))
        _assert_manifest(self, original, expected_service_types=(155, 18))

    def test_scan_permission_drops_never_for_location(self):
        original = _base_manifest(existing_scan_flags=True)
        permissions, _ = _read_manifest(patch(original))
        self.assertNotIn('usesPermissionFlags', permissions[SCAN_PERMISSION])

    def test_pinned_apk(self):
        path = pathlib.Path(__file__).resolve().parents[1] / 'build/host-105/AndroidManifest.xml'
        if not path.exists():
            self.skipTest('no pinned APK')
        original = patch_tts(path.read_bytes())
        _assert_manifest(self, original)


if __name__ == '__main__':
    unittest.main()
