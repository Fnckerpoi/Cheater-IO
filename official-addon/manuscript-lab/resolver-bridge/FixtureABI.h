#ifndef TIO_BRIDGE_FIXTURE_ABI_H
#define TIO_BRIDGE_FIXTURE_ABI_H
#include <stdbool.h>
#include <stdint.h>
typedef uint8_t (*FixtureOpen)(void **,uint32_t,void *,void *,bool,void *,uint32_t,void *,uint32_t,uint32_t,float);
typedef struct {
    unsigned calls;
    void **out;
    uint32_t id,max_size,compact_file,compact_bytes,ratio_bits;
    void *name,*path,*schema,*key;
    bool sqlite;
} FixtureOpenRecord;
#endif
