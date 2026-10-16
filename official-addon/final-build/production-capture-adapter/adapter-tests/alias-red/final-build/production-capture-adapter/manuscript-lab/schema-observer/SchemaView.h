#ifndef TIO_SCHEMA_VIEW_H
#define TIO_SCHEMA_VIEW_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdatomic.h>

#define TIO_SCHEMA_MAX_BYTES 65536u
#define TIO_SCHEMA_MAX_EVENTS 16u
#define TIO_SCHEMA_MAX_TTL 120u
#define TIO_SCHEMA_ENGINE_SHA "728997f23a55e0515bb01676fea35c6965a1255ec08f099c2f12227890a98b88"
#define TIO_SCHEMA_OPEN_VA UINT64_C(0x1e530)

typedef enum {
    TIOSchemaOK, TIOSchemaDisabled, TIOSchemaUnverified, TIOSchemaKeyPresent,
    TIOSchemaExpired, TIOSchemaEventLimit, TIOSchemaReentrant,
    TIOSchemaAddressInvalid, TIOSchemaCopyFailed, TIOSchemaLengthInvalid,
    TIOSchemaAllocationFailed
} TIOSchemaStatus;

/* The caller must validate the complete readable range and safely copy it.
 * Return false or a short count for races/unmapped ranges. No partial capture escapes. */
typedef bool (*TIOSchemaBoundedCopy)(void *user, uintptr_t address,
                                    size_t length, void *destination, size_t *copied);

typedef struct {
    bool enabled;
    /* These are caller attestations, not runtime identity verification by this module. */
    bool caller_verified_lab_identity;
    bool caller_verified_entry_lifetime;
    bool caller_verified_arm64_c_abi;
    char caller_verified_engine_sha[65];
    uint64_t caller_verified_open_va;
    unsigned schema_argument_ordinal;
    uint64_t armed_at_seconds;
    unsigned ttl_seconds;
    unsigned event_limit;
    unsigned events;
    uint64_t last_seen_seconds;
    atomic_flag active;
} TIOSchemaContext;

typedef struct { unsigned char *bytes; size_t length; } TIOSchemaOwnedBytes;
/* One-shot construction only. Reinitialization resets per-context advisory limits;
 * a future integration controller must enforce process-wide arming/one-shot policy. */
void TIOSchemaContextInit(TIOSchemaContext *context);
TIOSchemaStatus TIOSchemaCopyAtOpenEntry(TIOSchemaContext *context,
    uintptr_t schema_object, uintptr_t key_argument, uint64_t now_seconds,
    TIOSchemaBoundedCopy copy, void *user, TIOSchemaOwnedBytes *output);
void TIOSchemaOwnedBytesRelease(TIOSchemaOwnedBytes *bytes);
#endif
