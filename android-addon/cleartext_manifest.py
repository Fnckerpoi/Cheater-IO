"""Set only usesCleartextTraffic on the application node; add no components.

Reuses the reviewed binary AXML helpers. Idempotent: an already-true attribute is
left untouched, and the element inventory, string order and every other attribute
are preserved.
"""
import struct
import background_manifest as ax
from tts_manifest import NS

ATTR = 'usesCleartextTraffic'
RESOURCE_ID = 0x010104EC
TYPE_BOOLEAN = 0x12
POOL = 1
RESOURCE_MAP = 0x0180


def _pool(chunks):
    found = [i for i, c in enumerate(chunks) if struct.unpack_from('<H', c)[0] == POOL]
    if len(found) != 1:
        raise ValueError('string pool count')
    return found[0]


def _application_index(chunks, strings):
    found = None
    for i, chunk in enumerate(chunks):
        name_index = ax._node_name(chunk)
        if name_index is None or name_index >= len(strings):
            continue
        if strings[name_index] == 'application':
            if found is not None:
                raise ValueError('multiple application nodes')
            found = i
    if found is None:
        raise ValueError('application node missing')
    return found


def _cleartext_attribute(chunk, strings):
    attrs, _, _ = ax._attributes(chunk)
    for attribute in attrs:
        name_index = struct.unpack_from('<I', attribute, 4)[0]
        if name_index < len(strings) and strings[name_index] == ATTR:
            return attribute
    return None


def patch(data):
    chunks = ax._chunks(data)
    pi = _pool(chunks)
    maps = [i for i, c in enumerate(chunks) if struct.unpack_from('<H', c)[0] == RESOURCE_MAP]
    if len(maps) != 1:
        raise ValueError('resource map count')
    mi = maps[0]
    strings = ax._decode_pool(chunks[pi])[0]
    app = _application_index(chunks, strings)
    current = _cleartext_attribute(chunks[app], strings)
    if current is not None and current[15] == TYPE_BOOLEAN and struct.unpack_from('<I', current, 16)[0] == 1:
        return data

    chunks[pi], strings, indices = ax._append_pool_strings(chunks[pi], strings, ['application', ATTR, NS])
    for name in ('application', ATTR, NS):
        if name not in indices:
            raise ValueError('missing manifest vocabulary: ' + name)
    resource = ax._attr_resource_map(chunks[mi])
    if len(resource) > len(strings):
        raise ValueError('resource map exceeds string count')
    resource.extend([0] * (len(strings) - len(resource)))
    at = indices[ATTR]
    if resource[at] not in (0, RESOURCE_ID):
        raise ValueError('unexpected framework attribute mapping')
    resource[at] = RESOURCE_ID
    chunks[mi] = struct.pack('<HHI', RESOURCE_MAP, 8, 8 + 4 * len(resource)) + struct.pack('<' + 'I' * len(resource), *resource)

    chunks[app] = ax._rewrite_application(chunks[app], indices, resource, strings, indices[NS])
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body
