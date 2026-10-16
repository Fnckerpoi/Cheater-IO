#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
static unsigned Count;
void *OwnFlutterLookup(void *handle,const char *name){Count++;void*result=dlsym(handle,name);__asm__ volatile("" : "+r"(result));return result;}
unsigned OwnFlutterCount(void){return Count;}
__attribute__((constructor))static void early(void){const char*mode=getenv("TIO_OWN_EARLY_FFI");if(mode&&!strcmp(mode,"yes"))OwnFlutterLookup(RTLD_DEFAULT,"malloc");}
