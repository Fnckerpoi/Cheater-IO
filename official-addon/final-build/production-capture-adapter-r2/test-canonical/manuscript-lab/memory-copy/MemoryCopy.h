#ifndef TIO_MEMORY_COPY_H
#define TIO_MEMORY_COPY_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define TIO_MEMORY_COPY_MAX_BYTES ((size_t)65536)

/* TIOSchemaBoundedCopy-compatible; context must be NULL. Copies only within
 * mach_task_self(), never a caller-supplied task port. This is not a role gate.
 * Caller owns valid writable destination[length] and copied, disjoint from each
 * other and the source. After an attempted failed/short read, destination is
 * zeroed; copied is zero on failure. Rejected arguments leave destination
 * unchanged (copied is reset). No snapshot atomicity
 * or cross-call budget is promised. */
bool TIOMemoryCopySelf(void *context, uintptr_t address, size_t length,
                       void *destination, size_t *copied);

#endif
