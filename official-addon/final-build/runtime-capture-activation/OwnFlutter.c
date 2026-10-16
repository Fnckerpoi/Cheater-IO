#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
static unsigned Earlier;
typedef uint8_t(*Open)(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
static Open Cached;
bool ActivationOwnHasCachedTarget(void){return Cached!=NULL;}
uint8_t ActivationOwnCallCached(void**out){return Cached(out,201,NULL,NULL,false,NULL,0,NULL,0,0,0);}
void *ActivationOwnLookup(void*h,const char*n){Earlier++;void*r=dlsym(h,n);__asm__ volatile("" : "+r"(r));return r;}
unsigned ActivationOwnLookupCount(void){return Earlier;}
__attribute__((constructor))static void early(void){const char*m=getenv("TIO_OWN_ORDER");if(m&&!strcmp(m,"early"))Cached=(Open)ActivationOwnLookup(RTLD_DEFAULT,"isar_open_instance");}
