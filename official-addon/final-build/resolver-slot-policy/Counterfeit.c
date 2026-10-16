#include <dlfcn.h>
static void *fake(void*h,const char*n){(void)h;(void)n;__builtin_trap();}
__attribute__((used))static struct{const void*replacement;const void*replacee;} Interpose __attribute__((section("__DATA,__interpose")))={(void*)fake,(void*)dlsym};
