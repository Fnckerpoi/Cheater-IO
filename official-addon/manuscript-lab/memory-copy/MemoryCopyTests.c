#include "MemoryCopy.h"
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

static void valid_ranges(void) {
    const uint8_t source[8] = {0x19,0x72,0x31,0xa4,0x58,0x06,0xd2,0x9f};
    uint8_t destination[8] = {0};
    size_t copied = 999;
    assert(TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, destination, &copied));
    assert(copied == 8 && memcmp(destination, source, 8) == 0);
    uint8_t *large = malloc(TIO_MEMORY_COPY_MAX_BYTES);
    uint8_t *output = malloc(TIO_MEMORY_COPY_MAX_BYTES + 2);
    assert(large && output);
    memset(large, 0x39, TIO_MEMORY_COPY_MAX_BYTES);
    memset(output, 0xc5, TIO_MEMORY_COPY_MAX_BYTES + 2);
    assert(TIOMemoryCopySelf(NULL, (uintptr_t)large, TIO_MEMORY_COPY_MAX_BYTES, output + 1, &copied));
    assert(copied == TIO_MEMORY_COPY_MAX_BYTES);
    assert(memcmp(large, output + 1, TIO_MEMORY_COPY_MAX_BYTES) == 0);
    assert(output[0] == 0xc5 && output[TIO_MEMORY_COPY_MAX_BYTES + 1] == 0xc5);
    free(output); free(large);
}

static void rejected_arguments(void) {
    uint8_t source[8] = {1,2,3,4,5,6,7,8};
    uint8_t out[8]; memset(out, 0xa6, sizeof out);
    size_t copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 0, out, &copied));
    assert(copied == 0 && out[0] == 0xa6);
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, TIO_MEMORY_COPY_MAX_BYTES + 1, out, &copied));
    assert(copied == 0 && out[0] == 0xa6);
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, UINTPTR_MAX - 3, 8, out, &copied));
    assert(copied == 0);
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, 0, 8, out, &copied));
    assert(copied == 0);
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, NULL, &copied));
    assert(copied == 0);
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, out, NULL));
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, (void *)(UINTPTR_MAX - 3), &copied));
    assert(copied == 0);
    copied = 88;
    assert(!TIOMemoryCopySelf((void *)source, (uintptr_t)source, 8, out, &copied));
    assert(copied == 0);
    copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)source, 8, source, &copied));
    assert(copied == 0 && source[0] == 1);
}

static void inaccessible_ranges(void) {
    size_t page = (size_t)sysconf(_SC_PAGESIZE);
    assert(page >= 16 && page <= TIO_MEMORY_COPY_MAX_BYTES);
    uint8_t *mapping = mmap(NULL, page * 2, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    assert(mapping != MAP_FAILED);
    memset(mapping, 0x5a, page * 2);
    assert(mprotect(mapping + page, page, PROT_NONE) == 0);
    uint8_t guarded[18]; memset(guarded, 0xd7, sizeof guarded);
    size_t copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)(mapping + page - 8), 16, guarded + 1, &copied));
    assert(copied == 0 && guarded[0] == 0xd7 && guarded[17] == 0xd7);
    for (size_t i = 1; i <= 16; ++i) assert(guarded[i] == 0);
    memset(guarded + 1, 0xd7, 16); copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)(mapping + page), 16, guarded + 1, &copied));
    assert(copied == 0);
    for (size_t i = 1; i <= 16; ++i) assert(guarded[i] == 0);
    assert(munmap(mapping, page * 2) == 0);
    memset(guarded + 1, 0xd7, 16); copied = 88;
    assert(!TIOMemoryCopySelf(NULL, (uintptr_t)mapping, 16, guarded + 1, &copied));
    assert(copied == 0 && guarded[0] == 0xd7 && guarded[17] == 0xd7);
    for (size_t i = 1; i <= 16; ++i) assert(guarded[i] == 0);
}

int main(void) {
    valid_ranges(); rejected_arguments(); inaccessible_ranges();
    puts("memory-copy own-buffer and protection tests PASS");
    return 0;
}
