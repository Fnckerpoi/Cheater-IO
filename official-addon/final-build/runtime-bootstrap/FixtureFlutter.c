#include "FixtureFlutter.h"
#include <dlfcn.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static _Atomic(uintptr_t)*Slot;
static unsigned Lookups;
static bool Ready;
static void *competitor(void*h,const char*n){(void)h;(void)n;return (void*)0x333;}
__attribute__((constructor)) static void flutter_fixture_ctor(void){
 Slot=mmap(NULL,4096,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANON,-1,0);if(Slot==MAP_FAILED)abort();atomic_init(Slot,(uintptr_t)dlsym);Ready=true;
 const char *mode=getenv("TIO_FIXTURE_MODE");
 if(mode&&strcmp(mode,"readonly")==0&&mprotect(Slot,4096,PROT_READ))abort();
 if(mode&&strcmp(mode,"early")==0)FixtureFlutterLookup(RTLD_DEFAULT,"malloc");
 if(mode&&strcmp(mode,"wrongvalue")==0)atomic_store(Slot,(uintptr_t)competitor);
}
FixtureFlutterView FixtureFlutterViewNow(void){return(FixtureFlutterView){(uintptr_t)Slot,(uintptr_t)dlsym,Lookups,Ready,(uintptr_t)competitor};}
void *FixtureFlutterLookup(void *handle,const char *name){Lookups++;return((FixtureResolver)atomic_load(Slot))(handle,name);}
void FixtureFlutterCompete(void){uintptr_t expected=(uintptr_t)dlsym;atomic_compare_exchange_strong(Slot,&expected,(uintptr_t)competitor);}
