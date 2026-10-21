"""Manifest-only coexist preparation for the reviewed RayNeo 1.0.7 derivative."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile
import background_manifest as ax
from tmig1_coexist import parse

OLD = 'com.rayneo.venus.pub'
NEW = 'com.turboio.rayneo107.research'
PIN = '800c657c16ed80d226bc3e49544f9ea635d68d8815b3bb37e5747507acc14856'
COMPONENT_ATTRS = {'name', 'targetActivity', 'parentActivityName', 'backupAgent', 'manageSpaceActivity', 'appComponentFactory'}
COMPONENT_TAGS = {'application', 'activity', 'activity-alias', 'service', 'receiver', 'provider', 'instrumentation'}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def patch(data):
    chunks, pi, strings, nodes = parse(data)
    changes = []
    package = [a['value'] for _, tag, attrs in nodes if tag == 'manifest' for a in attrs if a['name'] == 'package']
    if package != [OLD]: raise ValueError('Unexpected source package')
    authorities = []
    for ci, tag, attrs in nodes:
        for a in attrs:
            name, value = a['name'], a['value']
            new = None
            if tag == 'manifest' and name == 'package': new = NEW
            elif tag == 'application' and name == 'label': new = 'TurboIO 1.0.7 Research'
            elif a['type'] == 3:
                if tag in COMPONENT_TAGS and name in COMPONENT_ATTRS:
                    if value.startswith('.'): new = OLD + value
                    elif '.' not in value: new = OLD + '.' + value
                    elif tag == 'activity-alias' and name == 'name' and value.startswith(OLD + '.'):
                        new = NEW + value[len(OLD):]
                elif tag == 'provider' and name == 'authorities':
                    values = value.split(';')
                    if not all(v.startswith(OLD + '.') for v in values): raise ValueError('Unknown provider authority')
                    authorities.extend(values)
                    new = ';'.join(NEW + v[len(OLD):] for v in values)
                elif name in {'permission', 'readPermission', 'writePermission', 'taskAffinity', 'process', 'targetPackage'} or tag in {'permission', 'uses-permission', 'uses-permission-sdk-23', 'action', 'category'} and name == 'name' or tag == 'data' and name == 'host':
                    if value == OLD or value.startswith(OLD + '.'): new = NEW + value[len(OLD):]
                elif tag == 'data' and name == 'scheme' and value in {'rayneo.venus.app', 'signinwithapple'}:
                    new = NEW + ('.apple' if value == 'signinwithapple' else '')
            if new is not None and new != value:
                changes.append({'chunk': ci, 'offset': a['offset'], 'tag': tag, 'attribute': name, 'namespace': a['namespace'], 'before': value, 'after': new})
    if len(authorities) != len(set(authorities)) or not authorities: raise ValueError('Missing or duplicate providers')
    chunks[pi], _, indices = ax._append_pool_strings(chunks[pi], strings, [c['after'] for c in changes])
    for c in changes:
        chunk = bytearray(chunks[c['chunk']]); off = c['offset']; index = indices[c['after']]
        struct.pack_into('<I', chunk, off + 8, index)
        struct.pack_into('<HBBI', chunk, off + 12, 8, 0, 3, index)
        chunks[c['chunk']] = bytes(chunk)
    body = b''.join(chunks)
    result = struct.pack('<HHI', 3, 8, len(body) + 8) + body
    _, _, _, after_nodes = parse(result)
    for _, tag, attrs in after_nodes:
        for a in attrs:
            if tag == 'activity' and a['name'] == 'name' and a['value'] == 'com.rayneo.venus.MainActivity': break
        else: continue
        break
    else: raise ValueError('Host MainActivity missing')
    for _, tag, attrs in after_nodes:
        for a in attrs:
            if a['type'] != 3: continue
            if a['name'] in {'authorities', 'permission', 'readPermission', 'writePermission', 'taskAffinity', 'process'} or tag == 'permission' and a['name'] == 'name':
                if a['value'] == OLD or a['value'].startswith(OLD + '.'): raise ValueError('Residual official identity conflict')
    return result, [{k:v for k,v in c.items() if k not in {'chunk','offset'}} for c in changes]

def prepare(source, output, report):
    if digest(source.read_bytes()) != PIN: raise ValueError('Reviewed input SHA mismatch')
    if output.exists() or report.exists(): raise ValueError('Refuse overwrite')
    with zipfile.ZipFile(source) as before:
        names = before.namelist()
        if len(names) != len(set(names)) or before.testzip(): raise ValueError('Invalid source ZIP')
        manifest, changes = patch(before.read('AndroidManifest.xml'))
        with zipfile.ZipFile(output, 'x', allowZip64=True) as after:
            for info in before.infolist(): after.writestr(info, manifest if info.filename == 'AndroidManifest.xml' else before.read(info))
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(output) as after:
        if after.namelist() != names or after.testzip(): raise ValueError('Output inventory or integrity mismatch')
        changed = [n for n in names if before.read(n) != after.read(n)]
        if changed != ['AndroidManifest.xml']: raise ValueError('Unexpected payload change')
        result = {'sourceSha256': PIN, 'outputSha256': digest(output.read_bytes()), 'package': NEW, 'changedPayloads': changed, 'attributeChanges': changes, 'allOtherPayloadsByteIdentical': True, 'installed': False}
    report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--source', type=Path, required=True); p.add_argument('--output', type=Path, required=True); p.add_argument('--report', type=Path, required=True)
    a = p.parse_args(); prepare(a.source, a.output, a.report)
