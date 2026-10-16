#include <dlfcn.h>
__attribute__((noinline)) void *ProcessNested(void *handle,const char *name) {
    void *result=dlsym(handle,name);__asm__ volatile("" : "+r"(result));return result;
}
