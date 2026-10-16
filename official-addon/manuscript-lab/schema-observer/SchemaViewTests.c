#include "SchemaView.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

typedef struct {
    unsigned char object[24];
    const unsigned char *payload;
    size_t length;
    unsigned reads;
    unsigned fail_at;
    bool short_copy;
    bool reenter;
    TIOSchemaContext *context;
} Memory;

static TIOSchemaContext context(void) {
    TIOSchemaContext c;
    TIOSchemaContextInit(&c);
    c.enabled = true;
    c.caller_verified_lab_identity = true;
    c.caller_verified_entry_lifetime = true;
    c.caller_verified_arm64_c_abi = true;
    memcpy(c.caller_verified_engine_sha, TIO_SCHEMA_ENGINE_SHA, 65);
    c.caller_verified_open_va = TIO_SCHEMA_OPEN_VA;
    c.schema_argument_ordinal = 6;
    c.armed_at_seconds = 100;
    c.ttl_seconds = 120;
    c.event_limit = 16;
    return c;
}
static void word(unsigned char *p, uint64_t value) {
    for (unsigned i = 0; i < 8; i++) p[i] = (unsigned char)(value >> (8 * i));
}
static Memory memory(void) {
    Memory m = {0};
    static const unsigned char json[] = "[]";
    m.payload = json; m.length = 2;
    word(m.object + 8, 0x2000); word(m.object + 16, 2);
    return m;
}
static bool copy(void *user, uintptr_t address, size_t length, void *destination, size_t *copied) {
    Memory *m = user;
    m->reads++;
    assert(address != 0x1000); /* Never read the +0 capacity field. */
    if (m->reenter) {
        m->reenter = false;
        TIOSchemaOwnedBytes nested = {0};
        assert(TIOSchemaCopyAtOpenEntry(m->context, 0x1000, 0, 101, copy, m, &nested) == TIOSchemaReentrant);
        assert(nested.bytes == NULL);
    }
    if (m->fail_at && m->reads == m->fail_at) return false;
    const unsigned char *source = NULL;
    if (address == 0x1008 && length == 8) source = m->object + 8;
    if (address == 0x1010 && length == 8) source = m->object + 16;
    if (address == 0x2000 && length == m->length) source = m->payload;
    if (!source) return false;
    size_t n = m->short_copy ? length - 1 : length;
    memcpy(destination, source, n); *copied = n;
    return true;
}
static TIOSchemaStatus capture(TIOSchemaContext *c, Memory *m, uintptr_t object, uintptr_t key, uint64_t now) {
    TIOSchemaOwnedBytes out = {0};
    TIOSchemaStatus status = TIOSchemaCopyAtOpenEntry(c, object, key, now, copy, m, &out);
    if (status == TIOSchemaOK) {
        assert(out.length == m->length && !memcmp(out.bytes, m->payload, out.length));
        assert(out.bytes != m->payload);
        TIOSchemaOwnedBytesRelease(&out);
    }
    assert(out.bytes == NULL && out.length == 0);
    return status;
}
int main(void) {
    TIOSchemaContext c = context(); Memory m = memory();
    assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaOK); assert(m.reads == 3);
    c = context(); m = memory(); assert(capture(&c, &m, 0x1000, 1, 101) == TIOSchemaKeyPresent); assert(m.reads == 0);
    c = context(); m = memory(); c.enabled = false; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaDisabled); assert(m.reads == 0);
    c = context(); m = memory(); c.caller_verified_lab_identity = false; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); c.caller_verified_engine_sha[0] = '0'; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); c.schema_argument_ordinal = 5; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); assert(capture(&c, &m, 0x1000, 0, 221) == TIOSchemaExpired); assert(m.reads == 0);
    c = context(); m = memory(); assert(capture(&c, &m, 0x1000, 0, 99) == TIOSchemaExpired); assert(m.reads == 0);
    c = context(); m = memory(); c.events = 16; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaEventLimit); assert(m.reads == 0);
    c = context(); m = memory(); assert(capture(&c, &m, UINTPTR_MAX - 7, 0, 101) == TIOSchemaAddressInvalid); assert(m.reads == 0);
    c = context(); m = memory(); assert(capture(&c, &m, 0x1001, 0, 101) == TIOSchemaAddressInvalid); assert(m.reads == 0);
    for (unsigned i = 1; i <= 3; i++) { c = context(); m = memory(); m.fail_at = i; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaCopyFailed); assert(m.reads == i); }
    c = context(); m = memory(); m.short_copy = true; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaCopyFailed);
    c = context(); m = memory(); word(m.object + 16, 65537); assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaLengthInvalid); assert(m.reads == 2);
    c = context(); m = memory(); word(m.object + 16, 0); assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaLengthInvalid); assert(m.reads == 2);
    c = context(); m = memory(); word(m.object + 8, UINTPTR_MAX); assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaAddressInvalid); assert(m.reads == 2);
    c = context(); m = memory(); m.reenter = true; m.context = &c; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaOK); assert(m.reads == 3);
    c = context(); m = memory(); c.caller_verified_entry_lifetime = false; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); c.caller_verified_arm64_c_abi = false; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); c.caller_verified_open_va++; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaUnverified); assert(m.reads == 0);
    c = context(); m = memory(); c.ttl_seconds = 121; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaExpired); assert(m.reads == 0);
    c = context(); m = memory(); c.event_limit = 17; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaEventLimit); assert(m.reads == 0);
    c = context(); m = memory(); unsigned char *large = malloc(65536); assert(large); memset(large, 'x', 65536); m.payload = large; m.length = 65536; word(m.object + 16, 65536); assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaOK); assert(m.reads == 3); free(large);
    c = context(); m = memory(); assert(capture(&c, &m, 0x1000, 0, 150) == TIOSchemaOK); m.reads = 0; assert(capture(&c, &m, 0x1000, 0, 101) == TIOSchemaExpired); assert(m.reads == 0);
    puts("SCHEMA_VIEW_C_PASS");
    return 0;
}
