#include "ResolverBridge.h"
__attribute__((noinline)) void *ProcessCaller(TIOResolverFunction resolver,void *handle,const char *name) {
    void *result=resolver(handle,name);__asm__ volatile("" : "+r"(result));return result;
}
