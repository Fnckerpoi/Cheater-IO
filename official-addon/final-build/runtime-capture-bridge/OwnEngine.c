#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
uint8_t isar_open_instance(void**out,uint32_t id,void*n,void*p,bool sql,void*s,uint32_t max,void*k,uint32_t a,uint32_t b,float r){(void)id;(void)n;(void)p;(void)sql;(void)max;(void)k;(void)a;(void)b;(void)r;uint64_t*o=s;free((void*)(uintptr_t)o[1]);free(o);*out=(void*)0x201;return 0x5a;}
