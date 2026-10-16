#include <dlfcn.h>
#include <stdint.h>
#include <stdbool.h>
typedef uint8_t(*Open)(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
static Open Cached;
void *OwnFlutterLookup(void*h,const char*n){void*r=dlsym(h,n);__asm__ volatile("" : "+r"(r));return r;}
__attribute__((constructor))static void first_ffi(void){Cached=(Open)OwnFlutterLookup(RTLD_DEFAULT,"not_the_open_name");}
void *OwnFlutterCachedOpen(void){return(void*)Cached;}
