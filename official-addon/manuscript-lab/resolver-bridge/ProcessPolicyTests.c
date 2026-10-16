#include "ResolverBridge.h"
#include "FixtureABI.h"
#include <dlfcn.h>
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
typedef void *(*Caller)(TIOResolverFunction,void *,const char *);
static Caller A,B;
static TIOResolverFunction Nested;
static const char owned_name[]="isar_open_instance";
static FixtureOpen Original __attribute__((unused));
static uint8_t __attribute__((unused)) replacement(void **out,uint32_t id,void *name,void *path,bool sqlite,void *schema,uint32_t max_size,void *key,uint32_t compact_file,uint32_t compact_bytes,float ratio) {
    return Original(out,id,name,path,sqlite,schema,max_size,key,compact_file,compact_bytes,ratio);
}
static void compare(Caller caller,const char *name) {
    dlerror();errno=E2BIG;void *expected=caller(dlsym,RTLD_DEFAULT,name);int saved=errno;const char *err=dlerror();char message[2048]={0};if(err)snprintf(message,sizeof(message),"%s",err);
    TIOResolverFunction resolvers[]={Nested,TIOResolverBridge};
    for(unsigned i=0;i<2;i++) {
        dlerror();errno=E2BIG;void *actual=caller(resolvers[i],RTLD_DEFAULT,name);int got=errno;err=dlerror();
        assert(actual==expected && saved==got);assert((!err&&!message[0])||(err&&!strcmp(err,message)));
    }
}
static void *threaded(void *arg) {uintptr_t id=(uintptr_t)arg;char name[96];for(unsigned i=0;i<100;i++){snprintf(name,sizeof(name),"missing_process_thread_%lu_%u",(unsigned long)id,i);compare(id%2?A:B,name);compare(A,owned_name);}return NULL;}
int main(int argc,char **argv) {
    assert(argc==9); // local, firstGlobal, secondGlobal, callerA, callerB, nested, expected-match
    void *local=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);assert(local);
    void *first=dlopen(argv[2],RTLD_NOW|RTLD_GLOBAL),*second=dlopen(argv[3],RTLD_NOW|RTLD_GLOBAL);assert(first&&second);
    void *ca=dlopen(argv[4],RTLD_NOW|RTLD_LOCAL),*cb=dlopen(argv[5],RTLD_NOW|RTLD_LOCAL),*nested=dlopen(argv[6],RTLD_NOW|RTLD_LOCAL);assert(ca&&cb&&nested);
    A=(Caller)dlsym(ca,"ProcessCaller");B=(Caller)dlsym(cb,"ProcessCaller");Nested=(TIOResolverFunction)dlsym(nested,"ProcessNested");assert(A&&B&&Nested);
    assert(dlopen(argv[8],RTLD_NOW|RTLD_GLOBAL));
    assert(TIOResolverBridgeBind(dlsym));
    void *selected=dlsym(RTLD_DEFAULT,owned_name);assert(selected && selected==dlsym(first,owned_name) && selected!=dlsym(second,owned_name) && selected!=dlsym(local,owned_name));
    dlerror();assert(!dlsym(RTLD_DEFAULT,"local_only_fixture") && dlerror());
    dlerror();void *zero=dlsym(RTLD_DEFAULT,"process_null_symbol");const char *zeroError=dlerror();assert(zero==NULL && zeroError==NULL);
    compare(A,"process_null_symbol");compare(B,"process_null_symbol");
    compare(A,owned_name);compare(B,owned_name);compare(A,"local_only_fixture");compare(B,"missing_process_public");
    pthread_t threads[4];for(uintptr_t i=0;i<4;i++)assert(!pthread_create(&threads[i],NULL,threaded,(void *)i));for(unsigned i=0;i<4;i++)assert(!pthread_join(threads[i],NULL));
#if defined(TIO_BRIDGE_TESTING)
    assert(!TIOResolverBridgeTestQualify(RTLD_NEXT,owned_name,selected,(void *)replacement));
    assert(!TIOResolverBridgeTestQualify(NULL,owned_name,selected,(void *)replacement));
    assert(!TIOResolverBridgeTestQualify((void *)0x1234,owned_name,selected,(void *)replacement));
    assert(!TIOResolverBridgeTestQualify((void *)(intptr_t)-99,owned_name,selected,(void *)replacement));
    assert(!TIOResolverBridgeTestQualify(first,owned_name,selected,(void *)replacement));
#ifdef RTLD_SELF
    assert(!TIOResolverBridgeTestQualify(RTLD_SELF,owned_name,selected,(void *)replacement));
#endif
#ifdef RTLD_MAIN_ONLY
    assert(!TIOResolverBridgeTestQualify(RTLD_MAIN_ONLY,owned_name,selected,(void *)replacement));
#endif
    int match=atoi(argv[7]);void *expected=match?selected:dlsym(second,owned_name);Original=(FixtureOpen)expected;
    bool qualified=TIOResolverBridgeTestQualify(RTLD_DEFAULT,owned_name,expected,(void *)replacement);
    assert(qualified==!!match && "DEFAULT qualification requires actual selected global export, not a shadow same-name symbol");
    if(match) {
        void *wrapped=A(TIOResolverBridge,RTLD_DEFAULT,owned_name);assert(wrapped==(void *)replacement);
        char unknown_owned[]="isar_open_instance";assert(A(TIOResolverBridge,RTLD_DEFAULT,unknown_owned)==selected);
        void *out=NULL;float ratio;uint32_t bits=0x80000000;memcpy(&ratio,&bits,4);
        const FixtureOpenRecord *(*record)(void)=dlsym(first,"ProcessFixtureRecord");assert(record);unsigned before=record()->calls;
        uint8_t status=((FixtureOpen)wrapped)(&out,0xfedcba98,(void *)0x111,(void *)0x222,true,(void *)0x333,0xabcdef01,(void *)0x555,0x98765432,0x10203040,ratio);
        const FixtureOpenRecord *r=record();assert(r->calls==before+1 && r->out==&out && r->id==0xfedcba98 && r->name==(void *)0x111 && r->path==(void *)0x222 && r->sqlite && r->schema==(void *)0x333 && r->max_size==0xabcdef01 && r->key==(void *)0x555 && r->compact_file==0x98765432 && r->compact_bytes==0x10203040 && r->ratio_bits==bits && status && out);
        TIOResolverBridgeTestExpire();assert(A(TIOResolverBridge,RTLD_DEFAULT,owned_name)==selected);before=record()->calls;assert(((FixtureOpen)wrapped)(&out,1,NULL,NULL,false,NULL,0,NULL,0,0,0) && record()->calls==before+1);
    }else assert(A(TIOResolverBridge,RTLD_DEFAULT,owned_name)==selected);
#endif
    puts("PROCESS_DEFAULT_NATIVE_PASS: two callers, both global orders, LOCAL exclusion, shadow refusal, original Flutter error protocol, TLS, typed forwarding, expiry");return 0;
}
