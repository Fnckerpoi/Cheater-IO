#import <Foundation/Foundation.h>
#include "OpenObserver.h"
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <stdatomic.h>
#include <string.h>
static uint64_t Now=1000;
static unsigned Calls,Observations,ResolverCalls;
static bool ThrowObserver,ThrowOriginal,Reenter,ThrowClock,FailObserver;
static pthread_t Thread;
static bool WrongExport,NullExport;
static uint64_t Clock(void *context){assert(context==(void *)0xaaa);if(ThrowClock)@throw [NSException exceptionWithName:@"OwnClockFailure" reason:nil userInfo:nil];return Now;}
static uint8_t Original(TIOIsar **out,uint32_t id,TIOCString *n,TIOCString *p,bool s,TIOCString *schema,uint32_t max,TIOCString *key,uint32_t a,uint32_t b,float r) {
    (void)n;(void)p;(void)s;(void)schema;(void)max;(void)key;(void)a;(void)b;(void)r;
    assert(pthread_equal(Thread,pthread_self()));assert(id==42);Calls++;
    if(ThrowOriginal)@throw [NSException exceptionWithName:@"OriginalFailure" reason:nil userInfo:nil];
    if(out)*out=(void *)0xabc;errno=EDOM;return 0x7b;
}
static uint8_t Call(TIOCString *key){TIOIsar *out=NULL;uint8_t value=TIOOpenForward(&out,42,NULL,NULL,false,(void *)0x999,1,key,2,3,0.75f);assert(out==(void *)0xabc);return value;}
static bool Observe(const TIOOpenObservation *event,void *context) {
    assert(context==(void *)0xaaa && event->schema==(void *)0x999);Observations++;
    if(Reenter){Reenter=false;assert(Call(NULL)==0x7b);}
    if(ThrowObserver)@throw [NSException exceptionWithName:@"OwnObserverFailure" reason:nil userInfo:nil];
    errno=EIO;return !FailObserver;
}
static void *Resolver(void *handle,const char *name){assert(handle==(void *)0x111);assert(name);ResolverCalls++;errno=ERANGE;return NullExport?NULL:WrongExport?(void *)0x555:(void *)Original;}
static void *OtherThread(void *unused){(void)unused;Thread=pthread_self();assert(Call(NULL)==0x7b);return NULL;}
int main(int argc,const char **argv){@autoreleasepool{
    Thread=pthread_self();assert(argc==2);assert(TIOOpenBindObserved(Original,Observe,Clock,(void *)0xaaa));
    assert(!TIOOpenBindObserved(Original,Observe,Clock,NULL));
    assert(Call(NULL)==0x7b && Observations==0 && Calls==1 && errno==EDOM);
    TIOOpenTrust trust={0};
    assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)Original && ResolverCalls==1 && errno==ERANGE);
    trust=(TIOOpenTrust){true,true,true,true,true};
    TIOOpenContextSetVerified(true);
    if(!strcmp(argv[1],"expiry")){
        assert(TIOOpenArm(true,1,16));assert(!TIOOpenArm(true,120,16));
        Now=2000;assert(Call(NULL)==0x7b && Observations==0);Now=1001;assert(Call(NULL)==0x7b && Observations==0);
    }else if(!strcmp(argv[1],"exceptions")){
        assert(TIOOpenArm(true,120,16));ThrowObserver=true;assert(Call(NULL)==0x7b && Calls==2 && Observations==1 && errno==EDOM);
        ThrowOriginal=true;bool propagated=false;@try{Call(NULL);}@catch(NSException *e){propagated=[e.name isEqual:@"OriginalFailure"];}
        assert(propagated && Calls==3);ThrowOriginal=false;ThrowObserver=false;assert(Call(NULL)==0x7b);
    }else if(!strcmp(argv[1],"callbackfailure")){
        assert(TIOOpenArm(true,120,16));FailObserver=true;assert(Call(NULL)==0x7b && Observations==1);FailObserver=false;assert(Call(NULL)==0x7b && Observations==1);
    }else if(!strcmp(argv[1],"clockfailure")){
        assert(TIOOpenArm(true,120,16));ThrowClock=true;
        assert(Call(NULL)==0x7b && Calls==2 && Observations==0);
        ThrowClock=false;assert(Call(NULL)==0x7b && Observations==0);
    }else if(!strcmp(argv[1],"budget")){
        assert(!TIOOpenArm(false,120,16));assert(!TIOOpenArm(true,121,16));assert(!TIOOpenArm(true,120,17));assert(TIOOpenArm(true,120,16));
        assert(Call((void *)1)==0x7b && Observations==0);
        Reenter=true;assert(Call(NULL)==0x7b && Observations==1 && Calls==4);
        for(int i=0;i<20;i++)assert(Call(NULL)==0x7b);
        assert(Observations==16);assert(!TIOOpenArm(true,120,16));TIOOpenDisable();assert(Call(NULL)==0x7b && Observations==16);
    }else if(!strcmp(argv[1],"clockback")){
        assert(TIOOpenArm(true,120,16));Now=999;assert(Call(NULL)==0x7b && Observations==0);Now=1001;assert(Call(NULL)==0x7b && Observations==0);
    }else if(!strcmp(argv[1],"thread")){
        assert(TIOOpenArm(true,120,16));pthread_t worker;assert(!pthread_create(&worker,NULL,OtherThread,NULL));assert(!pthread_join(worker,NULL));
        Thread=pthread_self();assert(Observations==0 && Calls==2);assert(Call(NULL)==0x7b && Observations==1);
    }else if(!strcmp(argv[1],"context")){
        assert(TIOOpenArm(true,120,16));TIOOpenContextSetVerified(false);assert(Call(NULL)==0x7b && Observations==0);
        TIOOpenContextSetVerified(true);assert(Call(NULL)==0x7b && Observations==1);
    }else if(!strcmp(argv[1],"slotplan")){
        _Atomic(uintptr_t) fake_slot=0x111;uintptr_t expected=0x111;
        assert(!TIOOpenSlotPlan(0x222,expected,true,true,true));assert(atomic_load(&fake_slot)==0x111);
        assert(!TIOOpenSlotPlan(0x111,expected,false,true,true));assert(!TIOOpenSlotPlan(0x111,expected,true,false,true));assert(!TIOOpenSlotPlan(0x111,expected,true,true,false));
        assert(TIOOpenSlotPlan(0x111,expected,true,true,true));assert(atomic_compare_exchange_strong(&fake_slot,&expected,0x333));
        expected=0x111;assert(!atomic_compare_exchange_strong(&fake_slot,&expected,0x444));assert(atomic_load(&fake_slot)==0x333);
    }else if(!strcmp(argv[1],"resolver")){
        assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)Original);
        assert(TIOOpenArm(true,120,16));
        assert(TIOOpenResolve(Resolver,(void *)0x111,"other",trust)==(void *)Original);
        trust.name_owned=false;assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)Original);
        trust.name_owned=true;
        for(unsigned i=0;i<5;i++){
            TIOOpenTrust bad=trust;
            switch(i){case 0:bad.handle_verified=false;break;case 1:bad.module_verified=false;break;case 2:bad.export_verified=false;break;case 3:bad.name_owned=false;break;default:bad.resolver_semantics_verified=false;}
            assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",bad)==(void *)Original);
        }
        assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)TIOOpenForward);
        WrongExport=true;assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)0x555);WrongExport=false;
        NullExport=true;assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==NULL && errno==ERANGE);NullExport=false;
        assert(Calls==1);
        TIOOpenDisable();assert(TIOOpenResolve(Resolver,(void *)0x111,"isar_open_instance",trust)==(void *)Original);
        assert(Call(NULL)==0x7b && Observations==0);
    }else assert(false);
    printf("PASSIVE_OPEN_STATE_PASS %s calls=%u observations=%u\n",argv[1],Calls,Observations);
}return 0;}
