#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
static unsigned Calls;
uint8_t isar_open_instance(void**out,uint32_t id,void*n,void*p,bool sql,void*s,uint32_t max,void*k,uint32_t cf,uint32_t cb,float ratio){Calls++;uint32_t bits;memcpy(&bits,&ratio,4);if(id!=201||n!=(void*)1||p!=(void*)2||!sql||max!=65536||cf!=3||cb!=4||bits!=0x7fc00042||errno!=E2BIG)abort();uint64_t*object=s;free((void*)(uintptr_t)object[1]);free(object);*out=(void*)0x201;(void)k;return 0x5a;}
unsigned IntegrationFixtureOriginalCalls(void){return Calls;}

__asm__(".globl _not_the_open_name\n.set _not_the_open_name, _isar_open_instance");
