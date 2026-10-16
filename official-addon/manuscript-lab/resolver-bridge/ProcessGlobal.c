#include "FixtureABI.h"
#include <string.h>
#ifndef FIXTURE_TAG
#define FIXTURE_TAG 1
#endif
static FixtureOpenRecord record;
uint8_t isar_open_instance(void **out,uint32_t id,void *name,void *path,bool sqlite,void *schema,uint32_t max_size,void *key,uint32_t compact_file,uint32_t compact_bytes,float ratio) {
    record.calls++;record.out=out;record.id=id;record.name=name;record.path=path;record.sqlite=sqlite;record.schema=schema;record.max_size=max_size;record.key=key;record.compact_file=compact_file;record.compact_bytes=compact_bytes;memcpy(&record.ratio_bits,&ratio,4);*out=(void *)(uintptr_t)(0x700+FIXTURE_TAG);return FIXTURE_TAG;
}
const FixtureOpenRecord *ProcessFixtureRecord(void) { return &record; }
int process_fixture_tag(void) { return FIXTURE_TAG; }
