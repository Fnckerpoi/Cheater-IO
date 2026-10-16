#include "ResolverBridge.h"
#include "FixtureABI.h"
#include <dlfcn.h>
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
typedef void *(*Caller)(TIOResolverFunction,void *,const char *);
static Caller caller;
static void *ordinary;
#if defined(TIO_BRIDGE_TESTING)
static const char fixture_name[]="isar_open_instance";
static FixtureOpen OriginalOpen;
static uint8_t substitute(void **out,uint32_t id,void *name,void *path,bool sqlite,void *schema,uint32_t max_size,void *key,uint32_t compact_file,uint32_t compact_bytes,float ratio) {
    return OriginalOpen(out,id,name,path,sqlite,schema,max_size,key,compact_file,compact_bytes,ratio);
}
#endif
static void compare(void *handle,const char *name,bool seeded) {
    char first_error[2048]={0};
    dlerror(); if(seeded) dlsym(RTLD_DEFAULT,"fixture_missing_seed");
    errno=E2BIG;void *expected=caller(dlsym,handle,name);int expected_errno=errno;const char *error=dlerror();if(error)snprintf(first_error,sizeof(first_error),"%s",error);
    dlerror(); if(seeded) dlsym(RTLD_DEFAULT,"fixture_missing_seed");
    errno=E2BIG;void *actual=caller(TIOResolverBridge,handle,name);int actual_errno=errno;error=dlerror();
    assert(expected==actual && expected_errno==actual_errno);
    assert((!error && !first_error[0]) || (error && !strcmp(error,first_error)));
}
static void recursive(unsigned depth) {
    if(!depth)return;
    compare(RTLD_NEXT,"bridge_collision",true);
    recursive(depth-1);
    compare(ordinary,"bridge_collision",false);
}
static void *thread_run(void *unused) {
    (void)unused;
    for(unsigned i=0;i<200;i++) {compare(RTLD_NEXT,"bridge_collision",i%2);compare(RTLD_DEFAULT,"not_present_thread",true);compare(NULL,"bridge_collision",false);}
    recursive(8);return NULL;
}
int main(int argc,char **argv) {
    assert(argc==4);
    void *a=dlopen(argv[1],RTLD_NOW|RTLD_GLOBAL);assert(a);
    void *n=dlopen(argv[2],RTLD_NOW|RTLD_GLOBAL);assert(n);
    ordinary=dlopen(argv[3],RTLD_NOW|RTLD_LOCAL);assert(ordinary);
    caller=(Caller)dlsym(a,"CallerLookup");assert(caller);
    assert(TIOResolverBridgeBind(dlsym));assert(!TIOResolverBridgeBind(dlsym));
    void *handles[]={RTLD_NEXT,RTLD_DEFAULT,NULL,ordinary};
    const char *names[]={"bridge_collision","not_present_public_fixture","isar_open_instance"};
    for(unsigned h=0;h<4;h++)for(unsigned s=0;s<3;s++){compare(handles[h],names[s],false);compare(handles[h],names[s],true);}
#ifdef RTLD_SELF
    compare(RTLD_SELF,"bridge_collision",true);
#endif
#ifdef RTLD_MAIN_ONLY
    compare(RTLD_MAIN_ONLY,"bridge_collision",true);
#endif
    pthread_t threads[4];for(unsigned i=0;i<4;i++)assert(!pthread_create(&threads[i],NULL,thread_run,NULL));for(unsigned i=0;i<4;i++)assert(!pthread_join(threads[i],NULL));
#if defined(TIO_BRIDGE_TESTING)
    assert(!TIOResolverBridgeTestQualify((void *)0x1234,fixture_name,(void *)1,(void *)substitute));
    assert(!TIOResolverBridgeTestQualify(RTLD_NEXT,fixture_name,(void *)1,(void *)substitute));
    assert(TIOResolverBridgeTestOpenFixture(argv[3])==ordinary);
    assert(!TIOResolverBridgeTestOpenFixture(argv[3]));
    assert(!TIOResolverBridgeTestQualify((void *)0x1234,fixture_name,(void *)1,(void *)substitute));
    assert(!TIOResolverBridgeTestQualify(a,fixture_name,(void *)1,(void *)substitute));
    assert(!TIOResolverBridgeTestQualify(RTLD_DEFAULT,fixture_name,(void *)1,(void *)substitute));
    assert(!TIOResolverBridgeTestQualify(NULL,fixture_name,(void *)1,(void *)substitute));
    void *real=dlsym(ordinary,fixture_name);assert(real);OriginalOpen=(FixtureOpen)real;
    const FixtureOpenRecord *(*getRecord)(void)=(const FixtureOpenRecord *(*)(void))dlsym(ordinary,"BridgeFixtureRecord");assert(getRecord);
    assert(TIOResolverBridgeTestQualify(ordinary,fixture_name,real,(void *)substitute));
    assert(!TIOResolverBridgeTestQualify(ordinary,fixture_name,real,(void *)substitute));
    void *selected=caller(TIOResolverBridge,ordinary,fixture_name);assert(selected==(void *)substitute);
    void *out=NULL;uint32_t ratio_bits=0x7fc12345;float ratio;memcpy(&ratio,&ratio_bits,4);
    unsigned before=getRecord()->calls;
    uint8_t status=((FixtureOpen)selected)(&out,0xfedcba98,(void *)0x111,(void *)0x222,true,(void *)0x333,0xabcdef01,(void *)0x555,0x98765432,0x10203040,ratio);
    const FixtureOpenRecord *record=getRecord();
    assert(status==0x7e && out==(void *)0x777 && record->calls==before+1);
    assert(record->out==&out && record->id==0xfedcba98 && record->name==(void *)0x111 && record->path==(void *)0x222 && record->sqlite && record->schema==(void *)0x333 && record->max_size==0xabcdef01 && record->key==(void *)0x555 && record->compact_file==0x98765432 && record->compact_bytes==0x10203040 && record->ratio_bits==ratio_bits);
    char equal_name[]="isar_open_instance";
    assert(caller(TIOResolverBridge,ordinary,equal_name)==real); // Unknown/unowned pointer never dereferenced by bridge.
    compare(RTLD_NEXT,fixture_name,true);compare(RTLD_DEFAULT,fixture_name,true);compare(ordinary,"not_present_public_fixture",true);
    TIOResolverBridgeTestExpire();assert(caller(TIOResolverBridge,ordinary,fixture_name)==real);
    before=getRecord()->calls;out=NULL;
    assert(((FixtureOpen)selected)(&out,1,NULL,NULL,false,NULL,0,NULL,0,0,0)==0x7e && getRecord()->calls==before+1); // Cached forwarder remains valid after expiry; no unload.
#endif
    printf("BRIDGE_REAL_DYLIB_PASS: host=x86_64 RTLD_NEXT/DEFAULT/NULL/ordinary/unknown, errno+dlerror TLS, threads+recursion; official=%s\n",TIOResolverBridgeRuntimeStatus());
    // Keep self-owned handles/bridge alive for process lifetime, just as cached pointer contract requires.
    return 0;
}
