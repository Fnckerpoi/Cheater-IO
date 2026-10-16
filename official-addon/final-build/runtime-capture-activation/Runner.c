#include "Activation.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <mach-o/getsect.h>
#include <stdatomic.h>
extern unsigned ActivationOwnLookupCount(void);
extern bool ActivationOwnHasCachedTarget(void);
extern uint8_t ActivationOwnCallCached(void**);
extern unsigned ActivationOwnOpenCalls(void);
static unsigned SpyCalls;
static void*spy(void*h,const char*n){(void)h;(void)n;SpyCalls++;return NULL;}
int main(int argc,char**argv){TIOActivationStatus s=TIOActivationGetStatus();assert(s.constructor_ran);if(argc==2&&!strcmp(argv[1],"missing")){assert(s.identity_verified&&!s.strong_dependencies_verified&&s.state==TIOActivationBlockedDependencyGraph&&!s.slot_installed);puts("MISSING_STRONG_DEPENDENCY_BLOCKED_PASS");return 0;}const char*mode=getenv("TIO_OWN_ORDER");assert(ActivationOwnLookupCount()==(mode&&!strcmp(mode,"early")?1u:0u));assert(s.identity_verified&&s.strong_dependencies_verified&&s.resolved_slot_verified&&"Exact current main strong dependencies and resolved slot must be proven before activation can even be considered");assert(!s.slot_installed&&!s.capture_started&&!s.file_created);
if(mode&&!strcmp(mode,"early")){assert(ActivationOwnHasCachedTarget());Dl_info owner={0};assert(dladdr((void*)ActivationOwnLookupCount,&owner));unsigned long size=0;unsigned char*data=getsectiondata((const struct mach_header_64*)owner.dli_fbase,"__DATA","__la_symbol_ptr",&size);assert(data&&size%8==0);_Atomic(uintptr_t)*target=NULL;for(unsigned long i=0;i<size/8;i++)if(atomic_load(((_Atomic uintptr_t*)data)+i)==(uintptr_t)dlsym){assert(!target);target=((_Atomic uintptr_t*)data)+i;}assert(target);uintptr_t original=(uintptr_t)dlsym;assert(atomic_compare_exchange_strong(target,&original,(uintptr_t)spy));void*out=NULL;assert(ActivationOwnCallCached(&out)==0x5a&&out==(void*)0x201&&ActivationOwnOpenCalls()==1&&SpyCalls==0);uintptr_t expected=(uintptr_t)spy;assert(atomic_compare_exchange_strong(target,&expected,(uintptr_t)dlsym));puts("ACTUAL_TARGET_CACHED_BEFORE_CTOR_BYPASSES_LATER_OWN_SLOT_SPY_PASS");}else assert(!ActivationOwnHasCachedTarget());puts("MAIN_STRONG_DEPENDENCY_AND_SYSTEM_SLOT_VERIFIED_NO_LOADER_AUTHORITY");return 0;}
