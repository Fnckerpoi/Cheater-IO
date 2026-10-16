#include "Capture.h"
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
static unsigned Calls;
uint8_t isar_open_instance(void**out,uint32_t id,void*name,void*path,bool sqlite,void*schema,uint32_t max,void*key,uint32_t cf,uint32_t cb,float ratio){(void)id;(void)name;(void)path;(void)sqlite;(void)max;(void)key;(void)cf;(void)cb;(void)ratio;Calls++;uint64_t*object=schema;free((void*)(uintptr_t)object[1]);free(object);*out=(void*)0x201;return 0x5a;}
unsigned OwnEngineCalls(void){return Calls;}
