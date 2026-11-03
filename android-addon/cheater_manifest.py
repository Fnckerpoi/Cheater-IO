"""Replace the application label in an Android binary manifest."""
import struct

import background_manifest as ax
from tmig1_coexist import parse
from tts_manifest import NS

LABEL = 'Cheater-IO'


def patch(data):
    """Return a manifest with only android:label on application changed."""
    chunks, pool_index, strings, nodes = parse(data)
    applications = [(index, attrs) for index, tag, attrs in nodes if tag == 'application']
    if len(applications) != 1:
        raise ValueError('Expected one application node')
    index, attrs = applications[0]
    labels = [a for a in attrs if a['name'] == 'label' and a['namespace'] == NS]
    if len(labels) != 1:
        raise ValueError('Expected one android:label attribute')
    label = labels[0]
    if label['type'] == 3 and label['value'] == LABEL:
        return data
    chunks[pool_index], _, indices = ax._append_pool_strings(chunks[pool_index], strings, [LABEL])
    chunk = bytearray(chunks[index])
    offset = label['offset']
    value = indices[LABEL]
    struct.pack_into('<I', chunk, offset + 8, value)
    struct.pack_into('<HBBI', chunk, offset + 12, 8, 0, 3, value)
    chunks[index] = bytes(chunk)
    body = b''.join(chunks)
    return struct.pack('<HHI', 3, 8, len(body) + 8) + body
