#include <dlfcn.h>
static unsigned Calls;
void *OwnLookup(void*h,const char*n){Calls++;void*p=dlsym(h,n);__asm__ volatile("" : "+r"(p));return p;}
unsigned OwnLookupCalls(void){return Calls;}
