"""Cleartext patcher: attribute-only, idempotent, everything else preserved."""
import struct
import sys
sys.path.insert(0, '.')
import background_manifest as ax
import cleartext_manifest as cm
from tts_manifest import NS

checks = 0


def check(value):
    global checks
    if not value:
        raise AssertionError('check %d' % checks)
    checks += 1


def _app(chunk, strings):
    return cm._application_index(ax._chunks(chunk), strings) if False else None


def build_manifest():
    strings = ['manifest', 'application', 'package', 'android', 'versionName', 'minSdkVersion']
    pool_strings = list(strings)
    # hand-assemble a minimal but structurally valid AXML
    app_name = ax._attr_record(1, 4, 0xffffffff, 0x03, 2)  # android:versionName -> "android"
    app_node = ax._begin(1, [app_name])
    app_end = ax._end(1)
    chunks = [app_node, app_end]
    body = b''.join(chunks)
    head = struct.pack('<HHI', 3, 8, len(body) + 8)
    return head + body


def decode(data):
    chunks = ax._chunks(data)
    pool = [c for c in chunks if struct.unpack_from('<H', c)[0] == 1]
    strings = ax._decode_pool(pool[0])[0] if pool else []
    return chunks, strings


def main():
    # Use the real reviewed host manifest as the fixture source when available.
    import pathlib, zipfile
    candidates = [
        pathlib.Path('../artifacts/android-105-guard07-tap1-test01/TurboIO-RayNeo-1.0.5-GUARD07-PUBLIC.apk'),
        pathlib.Path('C:/Users'),
    ]
    source = None
    for c in candidates:
        if c.is_file():
            source = c
            break
    if source is None:
        raise SystemExit('SKIP: host APK fixture unavailable')
    with zipfile.ZipFile(source) as z:
        original = z.read('AndroidManifest.xml')

    before = ax._chunks(original)
    strings0 = ax._decode_pool([c for c in before if struct.unpack_from('<H', c)[0] == 1][0])[0]
    app0 = cm._application_index(before, strings0)
    check(cm._cleartext_attribute(before[app0], strings0) is None)

    once = cm.patch(original)
    chunks1 = ax._chunks(once)
    strings1 = ax._decode_pool([c for c in chunks1 if struct.unpack_from('<H', c)[0] == 1][0])[0]
    app1 = cm._application_index(chunks1, strings1)
    attr = cm._cleartext_attribute(chunks1[app1], strings1)
    check(attr is not None)
    check(attr[15] == cm.TYPE_BOOLEAN)
    check(struct.unpack_from('<I', attr, 16)[0] == 1)
    check(struct.unpack_from('<I', attr, 4)[0] == strings1.index(cm.ATTR))
    check(struct.unpack_from('<I', attr, 0)[0] == strings1.index(NS))
    check(struct.unpack_from('<I', attr, 8)[0] == 0xffffffff)

    twice = cm.patch(once)
    check(twice == once)
    check(cm.patch(twice) == once)

    # Every other element node is preserved, and nothing but the attribute changed.
    def names(data):
        cs = ax._chunks(data)
        ss = ax._decode_pool([c for c in cs if struct.unpack_from('<H', c)[0] == 1][0])[0]
        out = []
        for c in cs:
            kind = struct.unpack_from('<H', c)[0]
            if kind == 0x0102:
                out.append(ss[ax._node_name(c)])
            elif kind == 0x0103:
                out.append('/' + ss[struct.unpack_from('<I', c, 20)[0]])
        return out
    check(names(once) == names(original))

    def app_attrs(data):
        cs = ax._chunks(data)
        ss = ax._decode_pool([c for c in cs if struct.unpack_from('<H', c)[0] == 1][0])[0]
        i = cm._application_index(cs, ss)
        attrs, _, _ = ax._attributes(cs[i])
        return sorted(ss[struct.unpack_from('<I', a, 4)[0]] for a in attrs)
    a_before, a_after = app_attrs(original), app_attrs(once)
    check(a_after == sorted(a_before + [cm.ATTR]))
    check(len(a_after) - len(a_before) == 1)

    # Composition order with the other patchers stays valid.
    import embed_manifest, dsh_manifest
    composed = cm.patch(embed_manifest.patch(dsh_manifest.patch(original)))
    cs = ax._chunks(composed)
    ss = ax._decode_pool([c for c in cs if struct.unpack_from('<H', c)[0] == 1][0])[0]
    i = cm._application_index(cs, ss)
    check(cm._cleartext_attribute(cs[i], ss) is not None)
    check('com.turboio.addon.AaEmbeddedActivity' in ss)
    check('com.turboio.addon.DshAuthCallbackActivity' in ss)
    check(cm.patch(composed) == composed)

    print('PASS %d cleartext manifest checks' % checks)


main()
