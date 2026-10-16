#ifndef TIO_RESOLVER_SLOT_POLICY_H
#define TIO_RESOLVER_SLOT_POLICY_H
#include <stdint.h>
typedef enum{TIOSlotUnknown,TIOSlotExpectedResolver}TIOSlotPointerClass;
/* Classifies a pointer value, not a writable slot or runtime capability.
 * No caller proof/expected pointer/lazy helper. No dlsym/dlerror calls. */
__attribute__((visibility("hidden"))) TIOSlotPointerClass TIOClassifyResolverSlot(uintptr_t current);
#endif
