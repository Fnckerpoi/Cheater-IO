#import "stage-b-controller/StageBController.h"
#import "schema-observer/SchemaMetadata.h"
#include <pthread.h>
#include <errno.h>
#include <string.h>

#if defined(TIO_STAGE_B_TESTING)
@interface TIOStageBController (CopyBoundary)
- (BOOL)copyAddress:(uintptr_t)address length:(size_t)length destination:(void *)destination copied:(size_t *)copied;
@end
static bool ControlledCopy(void *context, uintptr_t address, size_t length, void *destination, size_t *copied) {
    return [(__bridge TIOStageBController *)context copyAddress:address length:length destination:destination copied:copied];
}
#endif

@implementation TIOStageBController {
    TIOOpenFunction _original;
    pthread_mutex_t _lock;
    TIOStageBState _state;
    NSDictionary *_projection;
#if defined(TIO_STAGE_B_TESTING)
    BOOL _testGranted, _everArmed, _role, _lifetime, _ownedReleased;
    pthread_t _entryThread;
    TIOStageBTestClock _clock;
    void *_clockContext;
    TIOSchemaBoundedCopy _copy;
    void *_copyContext;
    TIOSchemaContext _schemaContext;
    uint64_t _started, _last;
#endif
}
- (instancetype)initWithOriginal:(TIOOpenFunction)original {
    if (!original) return nil;
    self = [super init];
    if (self) {
        _original = original;
        pthread_mutex_init(&_lock, NULL);
        _state = TIOStageBBlockedRuntimeEntry;
#if defined(TIO_STAGE_B_TESTING)
        _ownedReleased = YES;
        TIOSchemaContextInit(&_schemaContext);
#endif
    }
    return self;
}
- (void)dealloc { pthread_mutex_destroy(&_lock); }
- (BOOL)arm {
#if defined(TIO_STAGE_B_TESTING)
    pthread_mutex_lock(&_lock);
    if (!_testGranted || _everArmed || _state != TIOStageBBlockedRuntimeEntry ||
        !_role || !_lifetime || !pthread_equal(_entryThread, pthread_self())) {
        pthread_mutex_unlock(&_lock); return NO;
    }
    BOOL armed = NO;
    @try {
        _started = _last = _clock(_clockContext);
        if (!_started || _started > UINT64_MAX - 120) {
            _everArmed = YES; _state = TIOStageBTerminal;
        } else {
            _everArmed = YES;
            _schemaContext.enabled = true;
            /* Test-only synthetic attestations. Production object code excludes this. */
            _schemaContext.caller_verified_lab_identity = true;
            _schemaContext.caller_verified_entry_lifetime = true;
            _schemaContext.caller_verified_arm64_c_abi = true;
            memcpy(_schemaContext.caller_verified_engine_sha, TIO_SCHEMA_ENGINE_SHA, 65);
            _schemaContext.caller_verified_open_va = TIO_SCHEMA_OPEN_VA;
            _schemaContext.schema_argument_ordinal = 6;
            _schemaContext.armed_at_seconds = _started;
            _schemaContext.ttl_seconds = 120;
            _schemaContext.event_limit = 16;
            _state = TIOStageBTestArmed;
            armed = YES;
        }
    } @catch (NSException *clockException) {
        (void)clockException; _everArmed = YES; _state = TIOStageBTerminal;
    } @finally {
        pthread_mutex_unlock(&_lock);
    }
    return armed;
#else
    return NO; // No audited production adapter capability exists.
#endif
}
- (TIOStageBState)state {
    pthread_mutex_lock(&_lock); TIOStageBState state = _state; pthread_mutex_unlock(&_lock); return state;
}
- (NSDictionary *)capturedProjection {
    pthread_mutex_lock(&_lock); NSDictionary *value = _projection; pthread_mutex_unlock(&_lock); return value;
}
#if defined(TIO_STAGE_B_TESTING)
- (BOOL)testGrantForCurrentThreadWithRole:(BOOL)role lifetime:(BOOL)lifetime
    clock:(TIOStageBTestClock)clock clockContext:(void *)clockContext
    copy:(TIOSchemaBoundedCopy)copy copyContext:(void *)copyContext {
    pthread_mutex_lock(&_lock);
    BOOL allowed = !_testGranted && !_everArmed && _state == TIOStageBBlockedRuntimeEntry && clock && copy;
    if (allowed) {
        _testGranted = YES; _role = role; _lifetime = lifetime;
        _clock = clock; _clockContext = clockContext; _copy = copy; _copyContext = copyContext;
        _entryThread = pthread_self();
    }
    pthread_mutex_unlock(&_lock); return allowed;
}
- (BOOL)copyAddress:(uintptr_t)address length:(size_t)length destination:(void *)destination copied:(size_t *)copied {
    @try { return _copy(_copyContext, address, length, destination, copied); }
    @catch (NSException *copyException) {
        (void)copyException; memset(destination, 0, length); *copied = 0; return NO;
    }
}
- (BOOL)testOwnedCopyReleased { return _ownedReleased; }
- (void)observeSchema:(TIOCString *)schema realKey:(TIOCString *)key {
    if (key) return; // The real eighth argument, before clock or any memory read.
    pthread_mutex_lock(&_lock);
    if (_state != TIOStageBTestArmed || !_role || !_lifetime || !pthread_equal(_entryThread, pthread_self())) {
        pthread_mutex_unlock(&_lock); return;
    }
    /* Consume activation before external callbacks: no nested capture or rearm. */
    _state = TIOStageBTerminal;
    pthread_mutex_unlock(&_lock);
    TIOSchemaOwnedBytes owned = {0};
    @try {
        uint64_t now = _clock(_clockContext);
        if (now < _last || now < _started || now - _started >= 120) return;
        _last = now;
        _ownedReleased = NO;
        TIOSchemaStatus result = TIOSchemaCopyAtOpenEntry(&_schemaContext, (uintptr_t)schema,
            (uintptr_t)key, now, ControlledCopy, (__bridge void *)self, &owned);
        if (result != TIOSchemaOK) return;
        TIOSchemaJSONStatus jsonStatus;
        NSDictionary *projection = TIOSchemaDecodeProjection(&owned, &jsonStatus);
        if (projection && jsonStatus == TIOSchemaJSONOK) {
            pthread_mutex_lock(&_lock); _projection = projection; pthread_mutex_unlock(&_lock);
        }
    } @finally {
        TIOSchemaOwnedBytesRelease(&owned);
        _ownedReleased = YES;
    }
}
#endif
- (uint8_t)forwardOut:(TIOIsar **)out identifier:(uint32_t)identifier
    name:(TIOCString *)name path:(TIOCString *)path sqlite:(bool)sqlite
    schema:(TIOCString *)schema maxSize:(uint32_t)maxSize key:(TIOCString *)key
    compactFile:(uint32_t)compactFile compactBytes:(uint32_t)compactBytes ratio:(float)ratio {
    int incoming = errno;
#if defined(TIO_STAGE_B_TESTING)
    @try { [self observeSchema:schema realKey:key]; }
    @catch (NSException *observerException) { (void)observerException; }
#endif
    errno = incoming;
    // Original exceptions are deliberately outside the observer catch.
    return _original(out, identifier, name, path, sqlite, schema, maxSize, key,
                     compactFile, compactBytes, ratio);
}
@end

NSDictionary *TIOStageBProductionStatus(void) {
    return @{@"runtime_entry":@"BLOCKED_RUNTIME_ENTRY", @"entry_installed":@NO,
        @"active":@NO, @"device_candidate":@NO, @"schema_observation_enabled":@NO};
}
