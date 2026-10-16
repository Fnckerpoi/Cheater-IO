#ifndef TIO_RESOLVER_BRIDGE_H
#define TIO_RESOLVER_BRIDGE_H
#include <stdbool.h>
#include <stddef.h>
typedef void *(*TIOResolverFunction)(void *handle,const char *owned_name);
/* Bind once to the real resolver via a normal system import, before publication.
 * No constructor, target slot installation, official capability or activation. */
bool TIOResolverBridgeBind(TIOResolverFunction resolver);
void *TIOResolverBridge(void *handle,const char *owned_name);
const char *TIOResolverBridgeRuntimeStatus(void);
#if defined(TIO_BRIDGE_TESTING)
/* Only issued self-owned ordinary dlopen handles OR the exact RTLD_DEFAULT
 * macro with setup-time actual selected-export equality, plus owned-name pointer.
 * Setup-time expected/replacement are public fixture exports, never official engine.
 * This fake fixture qualification is absent from production. */
void *TIOResolverBridgeTestOpenFixture(const char *owned_fixture_path);
bool TIOResolverBridgeTestQualify(void *handle,const char *owned_name,
    void *expected_export,void *replacement);
void TIOResolverBridgeTestExpire(void);
#endif
#endif
