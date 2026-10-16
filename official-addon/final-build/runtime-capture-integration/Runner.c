#include "Integration.h"
#include "Capture.h"
#include "OwnPolicy.h"
#include <assert.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
extern void *IntegrationFixtureLookup(void*,const char*);
extern unsigned IntegrationFixtureOriginalCalls(void);
static uint64_t*schema(void){const char*s="[{\"name\":\"OwnPublicSchema\",\"properties\":[]}]";uint64_t*o=calloc(3,8);assert(o);o[1]=(uintptr_t)strdup(s);o[2]=strlen(s);return o;}
int main(void){TIOIntegrationStatus initial=TIOIntegrationGetStatus();assert(initial.constructor_ran);const char*mode=getenv("TIO_INTEGRATION_MODE");bool blocked=mode&&(!strcmp(mode,"early")||!strcmp(mode,"bundle")||!strcmp(mode,"uuid")||!strcmp(mode,"pointer"));if(blocked){assert(!initial.slot_installed&&!initial.target_observed&&!initial.capture_written);assert(TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);Dl_info own={0};assert(dladdr((void*)IntegrationFixtureLookup,&own));assert(*(uintptr_t*)((uintptr_t)own.dli_fbase+TIO_OWN_SLOT)==(uintptr_t)dlsym);if(!strcmp(mode,"early"))assert(initial.state==TIOIntegrationUnobserved);}else assert(initial.slot_installed&&"real self linker slot must be installed before first target lookup");TIOCaptureOpen fn=(TIOCaptureOpen)IntegrationFixtureLookup(RTLD_DEFAULT,"isar_open_instance");assert(fn);uint32_t bits=0x7fc00042;float ratio;memcpy(&ratio,&bits,4);void*out=NULL;errno=E2BIG;assert(fn(&out,201,(void*)1,(void*)2,true,schema(),65536,(void*)0x555,3,4,ratio)==0x5a&&out==(void*)0x201);assert(IntegrationFixtureOriginalCalls()==1);TIOIntegrationStatus done=TIOIntegrationGetStatus();if(blocked){assert(!done.target_observed&&!done.capture_written&&TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);puts("EARLY_OR_UNQUALIFIED_SLOT_UNCHANGED_ORIGINAL_FORWARDED_PASS");return 0;}
assert(done.target_observed&&!done.capture_written&&TIOCaptureGetState()==TIOCaptureWaiting);
errno=E2BIG;assert(fn(&out,201,(void*)1,(void*)2,true,schema(),65536,NULL,3,4,ratio)==0x5a&&IntegrationFixtureOriginalCalls()==2&&TIOCaptureGetState()==TIOCaptureWaiting);assert(!TIOIntegrationGetStatus().capture_written);
void*missing=IntegrationFixtureLookup(RTLD_DEFAULT,"__TIO_INTEGRATION_PUBLIC_MISSING__");assert(!missing);const char*error=dlerror();assert(error&&strstr(error,"__TIO_INTEGRATION_PUBLIC_MISSING__"));puts("REAL_LINKER_SELECTOR_INTERNAL_BIND_FORWARD_PASS_NO_CAPTURE_GRANT");return 0;}
