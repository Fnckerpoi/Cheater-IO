#include "Adapter.h"
#include "Capture.h"
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
extern void*OwnFlutterCachedOpen(void);
extern unsigned IntegrationFixtureOriginalCalls(void);
static uint64_t*schema(void){const char*j="[{\"name\":\"OwnPublicSchema\",\"properties\":[]}]";uint64_t*o=calloc(3,8);assert(o);o[1]=(uintptr_t)strdup(j);o[2]=strlen(j);return o;}
int main(void){TIOAdapterStatus s=TIOStageBCaptureAdapterGetStatus();assert(s.constructor_ran);fprintf(stderr,"BEFORE state=%u bundle=%u engine=%u resolver=%u installed=%u observed=%u\n",s.state,s.bundle_verified,s.engine_verified,s.slot_resolver_verified,s.cas_installed,s.lookup_observed);assert(s.cas_installed&&s.lookup_observed&&"Flutter strong dependency observer must precede actual target lookup");TIOCaptureOpen fn=(TIOCaptureOpen)OwnFlutterCachedOpen();assert(fn);void*out=NULL;uint32_t bits=0x7fc00042;float ratio;memcpy(&ratio,&bits,4);errno=E2BIG;assert(fn(&out,201,(void*)1,(void*)2,true,schema(),65536,(void*)0x555,3,4,ratio)==0x5a&&out==(void*)0x201&&IntegrationFixtureOriginalCalls()==1);assert(!TIOStageBCaptureAdapterGetStatus().file_created);errno=E2BIG;assert(fn(&out,201,(void*)1,(void*)2,true,schema(),65536,NULL,3,4,ratio)==0x5a&&IntegrationFixtureOriginalCalls()==2);s=TIOStageBCaptureAdapterGetStatus();fprintf(stderr,"ADAPTER_STATE=%u bundle=%u engine=%u resolver=%u installed=%u observed=%u open=%u file=%u\n",s.state,s.bundle_verified,s.engine_verified,s.slot_resolver_verified,s.cas_installed,s.lookup_observed,s.open_observed,s.file_created);assert(s.file_created&&s.schema_validated);puts("PRODUCTION_CODE_STRONG_DEPENDENCY_TYPED_CAPTURE_FILE_PASS");return 0;}
