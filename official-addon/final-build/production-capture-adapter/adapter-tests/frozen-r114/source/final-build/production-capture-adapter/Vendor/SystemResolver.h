#ifndef TIO_CTOR_SYSTEM_RESOLVER_H
#define TIO_CTOR_SYSTEM_RESOLVER_H
#include <stdbool.h>
#include <stdint.h>
/* Internal descriptive proof, never a slot/write/lifetime capability. */
__attribute__((visibility("hidden"))) bool TIOConstructorSystemResolver(uintptr_t current);
#endif
