#include "SchemaView.h"
#include <stdlib.h>
#include <string.h>

void TIOSchemaContextInit(TIOSchemaContext *context) {
    if (!context) return;
    memset(context, 0, sizeof(*context));
    atomic_flag_clear(&context->active);
}
void TIOSchemaOwnedBytesRelease(TIOSchemaOwnedBytes *bytes) {
    if (!bytes) return;
    if (bytes->bytes) {
        volatile unsigned char *p = bytes->bytes;
        for (size_t i = 0; i < bytes->length; i++) p[i] = 0;
        free(bytes->bytes);
    }
    bytes->bytes = NULL;
    bytes->length = 0;
}
TIOSchemaStatus TIOSchemaCopyAtOpenEntry(TIOSchemaContext *context,
    uintptr_t schema_object, uintptr_t key_argument, uint64_t now_seconds,
    TIOSchemaBoundedCopy copy, void *user, TIOSchemaOwnedBytes *output) {
    if (key_argument) return TIOSchemaKeyPresent;
    if (!context || !context->enabled) return TIOSchemaDisabled;
    if (!output || output->bytes || output->length || !copy ||
        !context->caller_verified_lab_identity ||
        !context->caller_verified_entry_lifetime ||
        !context->caller_verified_arm64_c_abi ||
        memcmp(context->caller_verified_engine_sha, TIO_SCHEMA_ENGINE_SHA, 65) ||
        context->caller_verified_open_va != TIO_SCHEMA_OPEN_VA ||
        context->schema_argument_ordinal != 6) return TIOSchemaUnverified;
    if (!context->armed_at_seconds || !context->ttl_seconds ||
        context->ttl_seconds > TIO_SCHEMA_MAX_TTL ||
        now_seconds < context->armed_at_seconds ||
        now_seconds - context->armed_at_seconds >= context->ttl_seconds) return TIOSchemaExpired;
    if (atomic_flag_test_and_set(&context->active)) return TIOSchemaReentrant;
    TIOSchemaStatus status = TIOSchemaEventLimit;
    TIOSchemaOwnedBytes staged = {0};
    if (context->last_seen_seconds && now_seconds < context->last_seen_seconds) {
        status = TIOSchemaExpired;
        goto done;
    }
    context->last_seen_seconds = now_seconds;
    if (!context->event_limit || context->event_limit > TIO_SCHEMA_MAX_EVENTS ||
        context->events >= context->event_limit) goto done;
    context->events++;
    status = TIOSchemaAddressInvalid;
    if (!schema_object || schema_object % 8 || schema_object > UINTPTR_MAX - 24) goto done;
    unsigned char pointer_bytes[8], length_bytes[8];
    size_t copied = 0;
    status = TIOSchemaCopyFailed;
    if (!copy(user, schema_object + 8, 8, pointer_bytes, &copied) || copied != 8) goto done;
    copied = 0;
    if (!copy(user, schema_object + 16, 8, length_bytes, &copied) || copied != 8) goto done;
    uint64_t pointer = 0, length = 0;
    for (unsigned i = 0; i < 8; i++) {
        pointer |= (uint64_t)pointer_bytes[i] << (8 * i);
        length |= (uint64_t)length_bytes[i] << (8 * i);
    }
    status = TIOSchemaLengthInvalid;
    if (!length || length > TIO_SCHEMA_MAX_BYTES) goto done;
    status = TIOSchemaAddressInvalid;
    if (!pointer || pointer > UINTPTR_MAX || length > UINTPTR_MAX - (uintptr_t)pointer) goto done;
    staged.bytes = malloc((size_t)length);
    staged.length = (size_t)length;
    status = TIOSchemaAllocationFailed;
    if (!staged.bytes) goto done;
    copied = 0;
    status = TIOSchemaCopyFailed;
    if (!copy(user, (uintptr_t)pointer, staged.length, staged.bytes, &copied) ||
        copied != staged.length) goto done;
    *output = staged;
    staged.bytes = NULL;
    staged.length = 0;
    status = TIOSchemaOK;
done:
    TIOSchemaOwnedBytesRelease(&staged);
    atomic_flag_clear(&context->active);
    return status;
}
