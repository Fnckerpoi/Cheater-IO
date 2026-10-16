#include "MemoryCopy.h"
#include <mach/mach.h>
#include <string.h>

_Static_assert(sizeof(vm_address_t) >= sizeof(uintptr_t), "Mach address must fit native pointer");
_Static_assert(sizeof(vm_size_t) >= sizeof(size_t), "Mach size must fit native size");

static bool overlaps(uintptr_t a, size_t a_length, uintptr_t b, size_t b_length) {
    return a < b + b_length && b < a + a_length;
}

bool TIOMemoryCopySelf(void *context, uintptr_t address, size_t length,
                       void *destination, size_t *copied) {
    if (copied == NULL) return false;
    *copied = 0;
    uintptr_t target = (uintptr_t)destination;
    uintptr_t count = (uintptr_t)copied;
    if (context != NULL || address == 0 || destination == NULL || length == 0 ||
        length > TIO_MEMORY_COPY_MAX_BYTES || address > UINTPTR_MAX - length ||
        target > UINTPTR_MAX - length || count > UINTPTR_MAX - sizeof(*copied)) return false;
    if (overlaps(address, length, target, length) ||
        overlaps(target, length, count, sizeof(*copied)) ||
        overlaps(address, length, count, sizeof(*copied))) return false;
    vm_size_t actual = 0;
    kern_return_t result = vm_read_overwrite(mach_task_self(),
        (vm_address_t)address, (vm_size_t)length,
        (vm_address_t)target, &actual);
    if (result != KERN_SUCCESS || actual != (vm_size_t)length) {
        memset(destination, 0, length);
        return false;
    }
    *copied = length;
    return true;
}
