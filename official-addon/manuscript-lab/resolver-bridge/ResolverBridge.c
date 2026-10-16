#include "ResolverBridge.h"
#include <dlfcn.h>
#include <stdatomic.h>
#include <stdint.h>
#include <string.h>

_Atomic(TIOResolverFunction) TIOBridgeRealResolver __attribute__((visibility("hidden")));

bool TIOResolverBridgeBind(TIOResolverFunction resolver) {
    if (!resolver || resolver == TIOResolverBridge) return false;
    TIOResolverFunction empty = NULL;
    return atomic_compare_exchange_strong_explicit(&TIOBridgeRealResolver,&empty,resolver,
        memory_order_release,memory_order_relaxed);
}
const char *TIOResolverBridgeRuntimeStatus(void) { return "UNOBSERVED_NO_OFFICIAL_RUNTIME_CAPABILITY"; }

#if defined(TIO_BRIDGE_TESTING)
/* Immutable qualification storage is initialized before concurrent publication.
 * Only Active changes afterwards. Expiry never unbinds cached forwarding pointers. */
void *TIOBridgeFixtureHandle;
const char *TIOBridgeFixtureName;
static void *Expected, *Replacement;
_Atomic(unsigned int) TIOBridgeFixtureActive;
static bool EverQualified;
static void *IssuedFixtureHandle;
void *TIOResolverBridgeTestOpenFixture(const char *owned_fixture_path) {
    if (IssuedFixtureHandle || !owned_fixture_path || !*owned_fixture_path) return NULL;
    IssuedFixtureHandle = dlopen(owned_fixture_path,RTLD_NOW|RTLD_LOCAL);
    return IssuedFixtureHandle;
}
bool TIOResolverBridgeTestQualify(void *handle,const char *name,void *expected,void *replacement) {
    TIOResolverFunction real = atomic_load_explicit(&TIOBridgeRealResolver,memory_order_acquire);
    if (EverQualified || !real || !name || strcmp(name,"isar_open_instance") || !expected || !replacement) return false;
    if (handle == RTLD_DEFAULT) {
        /* Exact platform macro only. Setup-time selected-export verification;
         * the live query path still makes exactly one resolver call. */
        if (real(RTLD_DEFAULT,name) != expected) return false;
    } else if (!IssuedFixtureHandle || handle != IssuedFixtureHandle || !handle || handle == RTLD_NEXT) {
        return false;
    }
#ifdef RTLD_SELF
    if (handle == RTLD_SELF) return false;
#endif
#ifdef RTLD_MAIN_ONLY
    if (handle == RTLD_MAIN_ONLY) return false;
#endif
    EverQualified=true; TIOBridgeFixtureHandle=handle; TIOBridgeFixtureName=name;
    Expected=expected; Replacement=replacement;
    atomic_store_explicit(&TIOBridgeFixtureActive,1,memory_order_release);
    return true;
}
void TIOResolverBridgeTestExpire(void) {
    atomic_store_explicit(&TIOBridgeFixtureActive,0,memory_order_release);
}
/* Eligible ordinary-handle path only; no further resolver/clock/log/identity work
 * after the one genuine dlsym. Ordinary handles are verified by native fixture. */
void *TIOBridgeFixtureResolve(void *handle,const char *name) {
    TIOResolverFunction real=atomic_load_explicit(&TIOBridgeRealResolver,memory_order_acquire);
    void *result=real(handle,name);
    return result == Expected ? Replacement : result;
}
#endif
