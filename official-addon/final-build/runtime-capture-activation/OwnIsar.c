#include <stdint.h>
#include <stdbool.h>
static unsigned Calls;
uint8_t isar_open_instance(void**out,uint32_t id,void*n,void*p,bool sql,void*schema,uint32_t max,void*key,uint32_t a,uint32_t b,float r){(void)id;(void)n;(void)p;(void)sql;(void)schema;(void)max;(void)key;(void)a;(void)b;(void)r;Calls++;*out=(void*)0x201;return 0x5a;}
unsigned ActivationOwnOpenCalls(void){return Calls;}
