#define _DARWIN_C_SOURCE
#include "Capture.h"
#include "CaptureInternal.h"
#include <assert.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
static uint64_t now(void){return 100;}
static uint64_t *schema(void){const char*s="[{\"name\":\"OwnPublicFixture\",\"properties\":[{\"name\":\"title\",\"type\":\"String\"}]}]";uint64_t*o=calloc(3,8);assert(o);o[1]=(uintptr_t)strdup(s);o[2]=strlen(s);return o;}
int main(int argc,char**argv){
 assert(argc==3&&TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);
 assert(!TIOCaptureBindOriginalInternal(NULL));
 void*engine=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);assert(engine);
 TIOCaptureOpen original=(TIOCaptureOpen)dlsym(engine,"isar_open_instance");unsigned(*calls)(void)=dlsym(engine,"OwnEngineCalls");assert(original&&calls);
 assert(TIOCaptureFixtureBind(original,argv[2],now));assert(TIOCaptureGetState()==TIOCaptureWaiting&&calls()==0);
 assert(!TIOCaptureBindOriginalInternal(original));
 void*out=NULL;void*key=(void*)0x555;
 assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,schema(),65536,key,3,4,1.0f)==0x5a&&calls()==1&&TIOCaptureFixtureCopyCount()==0);
 assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,schema(),65536,key,3,4,1.0f)==0x5a&&calls()==2&&TIOCaptureFixtureCopyCount()==0);
 assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,schema(),65536,NULL,3,4,1.0f)==0x5a&&calls()==3&&TIOCaptureFixtureCopyCount()==3&&TIOCaptureGetState()==TIOCaptureWritten);
 assert(TIOCaptureForward(&out,201,NULL,NULL,false,schema(),0,NULL,0,0,0)==0x5a&&calls()==4&&TIOCaptureFixtureCopyCount()==3);
 puts("SELF_ENGINE_PRIVATE_BIND_NONNULL_ZERO_READS_ONESHOT_ORIGINAL_ONCE_PASS");return 0;
}
