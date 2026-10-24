"""Add SCADA BLE permissions and non-exported foreground services to binary AXML."""
import struct
from tts_manifest import patch as tts_patch, NS, U

SERVICE = 'com.turboio.addon.BackgroundWork'
OTA_SERVICE = 'com.turboio.addon.OtaService'
PERMISSION = 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'
SCAN_PERMISSION = 'android.permission.BLUETOOTH_SCAN'

# name -> (maxSdkVersion, usesPermissionFlags bits)
PERMISSIONS = [
    ('android.permission.BLUETOOTH', 30, 0),
    ('android.permission.BLUETOOTH_ADMIN', 30, 0),
    ('android.permission.ACCESS_COARSE_LOCATION', None, 0),
    ('android.permission.ACCESS_FINE_LOCATION', None, 0),
    ('android.permission.ACCESS_BACKGROUND_LOCATION', 30, 0),
    (SCAN_PERMISSION, None, 0),
    ('android.permission.POST_NOTIFICATIONS', None, 0),
    ('android.permission.FOREGROUND_SERVICE', None, 0),
    (PERMISSION, None, 0),
    ('android.permission.FOREGROUND_SERVICE_LOCATION', None, 0),
    ('android.permission.FOREGROUND_SERVICE_MICROPHONE', None, 0),
    ('android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE', None, 0),
]

ATTR_IDS = {
    'name': 0x01010003,
    'exported': 0x01010010,
    'maxSdkVersion': 0x01010271,
    'usesCleartextTraffic': 0x010104ec,
}


def _chunks(data):
    u16 = lambda b, p: struct.unpack_from('<H', b, p)[0]
    u32 = lambda b, p: struct.unpack_from('<I', b, p)[0]
    if len(data) < 8 or u16(data, 0) != 3 or u16(data, 2) != 8 or u32(data, 4) != len(data):
        raise ValueError('Not binary manifest XML')
    chunks = []
    at = 8
    while at < len(data):
        size = u32(data, at + 4)
        if size < 8 or at + size > len(data):
            raise ValueError('chunk')
        chunks.append(data[at:at + size])
        at += size
    if at != len(data):
        raise ValueError('chunk boundary')
    return chunks


def _decode_pool(pool):
    u16 = lambda b, p: struct.unpack_from('<H', b, p)[0]
    u32 = lambda b, p: struct.unpack_from('<I', b, p)[0]
    if u16(pool, 2) != 28:
        raise ValueError('string pool header')
    count, styles, flags, start, style_start = struct.unpack_from('<IIIII', pool, 8)
    if styles or style_start:
        raise ValueError('styled manifest pool unsupported')
    utf8 = bool(flags & 0x100)

    def length(pos):
        if utf8:
            n = pool[pos]
            pos += 1
            if n & 0x80:
                n = ((n & 0x7f) << 8) | pool[pos]
                pos += 1
        else:
            n = u16(pool, pos)
            pos += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | u16(pool, pos)
                pos += 2
        return n, pos

    strings = []
    for i in range(count):
        pos = start + u32(pool, 28 + i * 4)
        n, pos = length(pos)
        if utf8:
            n, pos = length(pos)
            strings.append(pool[pos:pos + n].decode('utf-8'))
        else:
            strings.append(pool[pos:pos + n * 2].decode('utf-16le'))
    return strings, count, flags, start, utf8


def _append_pool_strings(pool, strings, wanted):
    old_strings, old_count, flags, start, utf8 = _decode_pool(pool)
    missing = []
    indices = {value: index for index, value in enumerate(old_strings)}
    for value in wanted:
        if value not in indices:
            indices[value] = old_count + len(missing)
            missing.append(value)
    if not missing:
        return pool, old_strings, indices

    raw = pool[start:]
    offsets = []
    for value in missing:
        offsets.append(len(raw))
        encoded = value.encode('utf-8' if utf8 else 'utf-16le')
        if utf8:
            byte_count = len(encoded)
            char_count = len(value)
            if byte_count >= 0x80 or char_count >= 0x80:
                raise ValueError('long manifest string unsupported')
            raw += bytes((char_count, byte_count)) + encoded + b'\0'
        else:
            char_count = len(encoded) // 2
            if char_count >= 0x8000:
                raise ValueError('long manifest string unsupported')
            raw += struct.pack('<H', char_count) + encoded + b'\0\0'
    raw += b'\0' * (-len(raw) % 4)
    new_count = old_count + len(missing)
    new_start = start + 4 * len(missing)
    size = new_start + len(raw)
    head = struct.pack('<HHIIIIII', 1, 28, size, new_count, 0, flags & ~1, new_start, 0)
    offsets_data = pool[28:start] + b''.join(struct.pack('<I', x) for x in offsets)
    strings = old_strings + missing
    return head + offsets_data + raw, strings, indices


def _attr_resource_map(chunk):
    if len(chunk) < 8 or struct.unpack_from('<H', chunk, 2)[0] != 8:
        raise ValueError('resource map header')
    if (len(chunk) - 8) % 4:
        raise ValueError('resource map size')
    return list(struct.unpack_from('<' + 'I' * ((len(chunk) - 8) // 4), chunk, 8))


def _attr_record(ns, name, raw, value_type, value):
    return struct.pack('<IIIHBBI', ns, name, raw, 8, 0, value_type, value)


def _begin(name_index, attrs):
    return (struct.pack('<HHIII', 0x0102, 16, 36 + 20 * len(attrs), 0, U)
            + struct.pack('<IIHHHHHH', U, name_index, 20, 20, len(attrs), 0, 0, 0)
            + b''.join(attrs))


def _end(name_index):
    return struct.pack('<HHIIIII', 0x0103, 16, 24, 0, U, U, name_index)


def _node_name(chunk):
    if len(chunk) >= 36 and struct.unpack_from('<H', chunk, 0)[0] == 0x0102:
        return struct.unpack_from('<I', chunk, 20)[0]
    return None


def _attributes(chunk):
    start = 16 + struct.unpack_from('<H', chunk, 24)[0]
    stride = struct.unpack_from('<H', chunk, 26)[0]
    count = struct.unpack_from('<H', chunk, 28)[0]
    if stride < 20 or start + stride * count > len(chunk):
        raise ValueError('attribute bounds')
    return [chunk[start + i * stride:start + i * stride + 20] for i in range(count)], start, stride


def _attribute_string(attribute, strings):
    raw = struct.unpack_from('<I', attribute, 8)[0]
    value_type = attribute[15]
    value = struct.unpack_from('<I', attribute, 16)[0]
    index = value if value_type == 3 else raw
    if index == U:
        return None
    if index >= len(strings):
        raise ValueError('attribute string index')
    return strings[index]


def _rewrite_permission(chunk, permission, spec, indices, resource_map, strings, ns_index):
    attrs, start, stride = _attributes(chunk)
    by_name = {}
    for attribute in attrs:
        name_index = struct.unpack_from('<I', attribute, 4)[0]
        if name_index >= len(strings):
            raise ValueError('attribute name index')
        by_name[strings[name_index]] = attribute
    if _attribute_string(by_name.get('name', b''), strings) != permission:
        raise ValueError('permission name attribute missing')

    maximum, flags = spec
    replacements = {}
    if maximum is not None:
        replacements['maxSdkVersion'] = _attr_record(ns_index, indices['maxSdkVersion'], U, 0x10, maximum)
    else:
        by_name.pop('maxSdkVersion', None)
    if flags:
        old = by_name.get('usesPermissionFlags')
        old_value = 0
        if old is not None:
            old_type = old[15]
            old_value = struct.unpack_from('<I', old, 16)[0]
            if old_type not in (0x10, 0x11):
                raise ValueError('unexpected usesPermissionFlags type')
        replacements['usesPermissionFlags'] = _attr_record(ns_index, indices['usesPermissionFlags'], U,
                                                           0x11, old_value | flags)
    else:
        by_name.pop('usesPermissionFlags', None)
    for name, attribute in replacements.items():
        by_name[name] = attribute
    ordered = sorted(by_name.values(), key=lambda item: resource_map[struct.unpack_from('<I', item, 4)[0]])
    prefix = bytearray(chunk[:start])
    struct.pack_into('<I', prefix, 4, start + 20 * len(ordered) + len(chunk) - (start + stride * len(attrs)))
    struct.pack_into('<H', prefix, 28, len(ordered))
    suffix = chunk[start + stride * len(attrs):]
    return bytes(prefix) + b''.join(ordered) + suffix


def _rewrite_service(chunk, class_name, service_type, indices, resource_map, strings, ns_index):
    attrs, start, stride = _attributes(chunk)
    by_name = {}
    for attribute in attrs:
        name_index = struct.unpack_from('<I', attribute, 4)[0]
        if name_index >= len(strings):
            raise ValueError('attribute name index')
        by_name[strings[name_index]] = attribute
    if _attribute_string(by_name.get('name', b''), strings) != class_name:
        raise ValueError('service name attribute missing')

    old_type = by_name.get('foregroundServiceType')
    old_bits = 0
    if old_type is not None:
        if old_type[15] not in (0x10, 0x11):
            raise ValueError('unexpected foregroundServiceType type')
        old_bits = struct.unpack_from('<I', old_type, 16)[0]
    by_name['exported'] = _attr_record(ns_index, indices['exported'], U, 0x12, 0)
    by_name['foregroundServiceType'] = _attr_record(
        ns_index, indices['foregroundServiceType'], U, 0x11, old_bits | service_type)
    ordered = sorted(by_name.values(), key=lambda item: resource_map[struct.unpack_from('<I', item, 4)[0]])
    prefix = bytearray(chunk[:start])
    struct.pack_into('<I', prefix, 4, start + 20 * len(ordered) + len(chunk) - (start + stride * len(attrs)))
    struct.pack_into('<H', prefix, 28, len(ordered))
    suffix = chunk[start + stride * len(attrs):]
    return bytes(prefix) + b''.join(ordered) + suffix


def _rewrite_application(chunk, indices, resource_map, strings, ns_index):
    attrs, start, stride = _attributes(chunk)
    by_name = {}
    for attribute in attrs:
        name_index = struct.unpack_from('<I', attribute, 4)[0]
        if name_index >= len(strings):
            raise ValueError('application attribute name index')
        by_name[strings[name_index]] = attribute
    by_name['usesCleartextTraffic'] = _attr_record(
        ns_index, indices['usesCleartextTraffic'], U, 0x12, 1)
    ordered = sorted(by_name.values(), key=lambda item: resource_map[struct.unpack_from('<I', item, 4)[0]])
    prefix = bytearray(chunk[:start])
    struct.pack_into('<I', prefix, 4, start + 20 * len(ordered) + len(chunk) - (start + stride * len(attrs)))
    struct.pack_into('<H', prefix, 28, len(ordered))
    suffix = chunk[start + stride * len(attrs):]
    return bytes(prefix) + b''.join(ordered) + suffix


def _permission_node(permission, spec, indices, resource_map, ns_index):
    maximum, flags = spec
    attrs = [_attr_record(ns_index, indices['name'], indices[permission], 3, indices[permission])]
    if maximum is not None:
        attrs.append(_attr_record(ns_index, indices['maxSdkVersion'], U, 0x10, maximum))
    if flags:
        attrs.append(_attr_record(ns_index, indices['usesPermissionFlags'], U, 0x11, flags))
    attrs.sort(key=lambda item: resource_map[struct.unpack_from('<I', item, 4)[0]])
    return _begin(indices['uses-permission'], attrs)


def patch(data):
    data = tts_patch(data)
    chunks = _chunks(data)
    pool_indices = [i for i, chunk in enumerate(chunks) if struct.unpack_from('<H', chunk)[0] == 1]
    if len(pool_indices) != 1:
        raise ValueError('string pool count')
    pool_index = pool_indices[0]
    pool = chunks[pool_index]
    strings, _, _, _, _ = _decode_pool(pool)

    map_indices = [i for i, chunk in enumerate(chunks) if struct.unpack_from('<H', chunk)[0] == 0x0180]
    if len(map_indices) != 1:
        raise ValueError('resource map count')
    map_index = map_indices[0]
    wanted = [SERVICE, OTA_SERVICE, 'maxSdkVersion']
    wanted.extend(permission for permission, _, _ in PERMISSIONS)
    wanted.extend(['manifest', 'application', 'service', 'uses-permission', 'name', 'exported',
                   'foregroundServiceType', 'usesCleartextTraffic', NS])
    chunks[pool_index], strings, indices = _append_pool_strings(pool, strings, wanted)
    if any(name not in indices for name in ('manifest', 'application', 'service', 'uses-permission', 'name',
                                             'exported', 'foregroundServiceType', 'usesCleartextTraffic', NS)):
        raise ValueError('missing host vocabulary')
    resource_map = _attr_resource_map(chunks[map_index])
    if len(resource_map) > len(strings):
        raise ValueError('resource map exceeds string count')
    resource_map.extend([0] * (len(strings) - len(resource_map)))
    for name, resource_id in ATTR_IDS.items():
        at = indices[name]
        if resource_map[at] not in (0, resource_id):
            raise ValueError('unexpected framework attribute mapping: ' + name)
        resource_map[at] = resource_id
    if resource_map[indices['foregroundServiceType']] == 0:
        raise ValueError('foregroundServiceType resource mapping missing')
    chunks[map_index] = struct.pack('<HHI', 0x0180, 8, 8 + 4 * len(resource_map)) + struct.pack(
        '<' + 'I' * len(resource_map), *resource_map)

    ns_index = indices[NS]
    permission_specs = {name: (maximum, flags) for name, maximum, flags in PERMISSIONS}
    seen_permissions = set()
    app_start = None
    for i, chunk in enumerate(chunks):
        name_index = _node_name(chunk)
        if name_index is None:
            continue
        node_name = strings[name_index] if name_index < len(strings) else None
        if node_name == 'uses-permission':
            attrs, _, _ = _attributes(chunk)
            permission = None
            for attribute in attrs:
                attr_name = strings[struct.unpack_from('<I', attribute, 4)[0]]
                if attr_name == 'name':
                    permission = _attribute_string(attribute, strings)
                    break
            if permission in permission_specs:
                chunks[i] = _rewrite_permission(chunk, permission, permission_specs[permission], indices,
                                                resource_map, strings, ns_index)
                seen_permissions.add(permission)
        elif node_name == 'application' and app_start is None:
            app_start = i
            chunks[i] = _rewrite_application(chunk, indices, resource_map, strings, ns_index)
    if app_start is None:
        raise ValueError('application node missing')
    missing_nodes = []
    for permission, _, _ in PERMISSIONS:
        if permission not in seen_permissions:
            missing_nodes.append(_permission_node(permission, permission_specs[permission], indices, resource_map, ns_index))
            missing_nodes.append(_end(indices['uses-permission']))
    chunks[app_start:app_start] = missing_nodes

    def attr(name, value_type, value, raw=U):
        return _attr_record(ns_index, indices[name], raw, value_type, value)

    def service_node(class_name, service_type):
        attrs = [attr('name', 3, indices[class_name], indices[class_name]),
                 attr('exported', 0x12, 0),
                 attr('foregroundServiceType', 0x11, service_type)]
        attrs.sort(key=lambda item: resource_map[struct.unpack_from('<I', item, 4)[0]])
        return _begin(indices['service'], attrs)

    service_specs = {SERVICE: 154, OTA_SERVICE: 16}
    seen_services = set()
    for i, chunk in enumerate(chunks):
        name_index = _node_name(chunk)
        if name_index is None or name_index >= len(strings) or strings[name_index] != 'service':
            continue
        attrs, _, _ = _attributes(chunk)
        service_name = None
        for attribute in attrs:
            attr_name_index = struct.unpack_from('<I', attribute, 4)[0]
            if attr_name_index < len(strings) and strings[attr_name_index] == 'name':
                service_name = _attribute_string(attribute, strings)
                break
        if service_name not in service_specs:
            continue
        if service_name in seen_services:
            raise ValueError('duplicate service declaration: ' + service_name)
        chunks[i] = _rewrite_service(chunk, service_name, service_specs[service_name],
                                     indices, resource_map, strings, ns_index)
        seen_services.add(service_name)

    stack = []
    app_end = None
    for i, chunk in enumerate(chunks):
        kind = struct.unpack_from('<H', chunk)[0]
        if kind == 0x0102:
            name_index = _node_name(chunk)
            if name_index is None or name_index >= len(strings):
                raise ValueError('start element name')
            stack.append(name_index)
        elif kind == 0x0103:
            name_index = struct.unpack_from('<I', chunk, 20)[0]
            if not stack or stack.pop() != name_index:
                raise ValueError('XML nesting mismatch')
            if name_index == indices['application']:
                if app_end is not None:
                    raise ValueError('multiple application nodes')
                app_end = i
    if stack or app_end is None:
        raise ValueError('application end or XML nesting missing')
    missing_services = []
    if SERVICE not in seen_services:
        missing_services.extend([service_node(SERVICE, service_specs[SERVICE]), _end(indices['service'])])
    if OTA_SERVICE not in seen_services:
        missing_services.extend([service_node(OTA_SERVICE, service_specs[OTA_SERVICE]), _end(indices['service'])])
    chunks[app_end:app_end] = missing_services
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body
