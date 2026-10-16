#include <dlfcn.h>
void *ProductionFixtureLookup(void*h,const char*n){void*r=dlsym(h,n);__asm__ volatile("" : "+r"(r));return r;}
