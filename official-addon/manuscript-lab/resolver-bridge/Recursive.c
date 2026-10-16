#include "ResolverBridge.h"
#include <assert.h>
#include <stdatomic.h>
static TIOResolverFunction Resolver;
static void *Handle;
static _Atomic unsigned Count;
static int resolved_function(void) { return 987; }
void BridgeFixtureConfigureRecursive(TIOResolverFunction resolver,void *handle) { Resolver=resolver;Handle=handle; }
unsigned BridgeFixtureRecursiveCount(void) { return atomic_load(&Count); }
static void *resolve(void) {
    assert(Resolver && Handle);
    assert(Resolver(Handle,"bridge_collision"));
    atomic_fetch_add(&Count,1);
    return (void *)resolved_function;
}
void *bridge_recursive_direct(void) { return resolve(); }
void *bridge_recursive_tail(void) { return resolve(); }
__asm__(".symbol_resolver _bridge_recursive_direct\n.symbol_resolver _bridge_recursive_tail");
