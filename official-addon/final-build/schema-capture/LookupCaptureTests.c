#include "Capture.h"
#include "ResolverBridge.h"
#include <assert.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static const char Name[]="isar_open_instance";
static uint64_t now(void){return 100;}
static uint64_t *schema(void){const char*s="[{\"name\":\"OwnPublicFixture\",\"properties\":[{\"name\":\"title\",\"type\":\"String\"}]}]";uint64_t*object=calloc(3,8);assert(object);char*copy=strdup(s);assert(copy);object[1]=(uintptr_t)copy;object[2]=strlen(copy);return object;}
int main(int argc,char**argv){assert(argc==3);void*engine=dlopen(argv[1],RTLD_NOW|RTLD_GLOBAL);assert(engine);TIOCaptureOpen original=(TIOCaptureOpen)dlsym(RTLD_DEFAULT,Name);unsigned(*calls)(void)=dlsym(engine,"OwnEngineCalls");assert(original&&calls);assert(TIOCaptureFixtureBind(original,argv[2],now));assert(TIOResolverBridgeBind(dlsym));assert(TIOResolverBridgeTestQualify(RTLD_DEFAULT,Name,(void*)original,(void*)TIOCaptureForward));TIOCaptureOpen wrapped=(TIOCaptureOpen)TIOResolverBridge(RTLD_DEFAULT,Name);assert(wrapped==TIOCaptureForward);void*out=NULL;assert(wrapped(&out,201,(void*)1,(void*)2,true,schema(),65536,(void*)0x555,3,4,1.0f)==0x5a&&calls()==1&&TIOCaptureFixtureCopyCount()==0);assert(wrapped(&out,201,(void*)1,(void*)2,true,schema(),65536,NULL,3,4,1.0f)==0x5a&&calls()==2&&TIOCaptureGetState()==TIOCaptureWritten&&TIOCaptureFixtureCopyCount()==3);TIOResolverBridgeTestExpire();assert(TIOResolverBridge(RTLD_DEFAULT,Name)==(void*)original);assert(wrapped(&out,201,NULL,NULL,false,schema(),0,NULL,0,0,0)==0x5a&&calls()==3&&TIOCaptureFixtureCopyCount()==3);puts("REAL_DEFAULT_TYPED_CAPTURE_CONSUMED_ORIGINAL_PASS");return 0;}
