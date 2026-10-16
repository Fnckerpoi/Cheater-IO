#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
static unsigned Lookups;
void *IntegrationFixtureLookup(void*h,const char*n){Lookups++;void*r=dlsym(h,n);__asm__ volatile("" : "+r"(r));return r;}
unsigned IntegrationFixtureLookupCount(void){return Lookups;}
__attribute__((constructor))static void earlier(void){const char*m=getenv("TIO_INTEGRATION_MODE");if(m&&!strcmp(m,"early"))IntegrationFixtureLookup(RTLD_DEFAULT,"malloc");}
