#include "MemoryCopy.h"
#include <assert.h>
#include <mach/mach.h>
#include <stdio.h>
#include <string.h>

static unsigned calls;
static kern_return_t response;
static vm_size_t reported;

/* Test linker substitute only; production object imports the real Mach API. */
kern_return_t TIOMemoryCopyFakeMachRead(vm_map_read_t task, vm_address_t address,
                                      vm_size_t length, vm_address_t destination,
                                      vm_size_t *actual) {
    (void)address;
    assert(task == mach_task_self() && length == 8);
    ++calls;
    memset((void *)(uintptr_t)destination, 0x5e, 4);
    *actual = reported;
    return response;
}

int main(void) {
    const uint8_t source[8] = {1,2,3,4,5,6,7,8};
    uint8_t guarded[10];
    const kern_return_t results[3] = {KERN_SUCCESS, KERN_INVALID_ADDRESS, KERN_SUCCESS};
    const vm_size_t sizes[3] = {4, 4, 9};
    for (size_t scenario = 0; scenario < 3; ++scenario) {
        memset(guarded, 0xa7, sizeof guarded);
        response = results[scenario]; reported = sizes[scenario];
        size_t copied = 42;
        assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, guarded + 1, &copied));
        assert(copied == 0 && guarded[0] == 0xa7 && guarded[9] == 0xa7);
        for (size_t i = 1; i <= 8; ++i) assert(guarded[i] == 0);
    }
    size_t copied = 42;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 0, guarded + 1, &copied));
    assert(copied == 0 && calls == 3);
    puts("memory-copy simulated short/error cleanup tests PASS");
    return 0;
}
