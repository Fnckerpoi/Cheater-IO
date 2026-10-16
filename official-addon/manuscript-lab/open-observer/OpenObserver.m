#import <Foundation/Foundation.h>
#import "OpenObserver.h"
#include <errno.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

static _Atomic(TIOOpenFunction) Original;
static pthread_mutex_t Lock=PTHREAD_MUTEX_INITIALIZER;
static TIOOpenCallback Callback;
static TIOOpenClock Clock;
static void *Context;
static bool Armed,EverArmed,Terminal;
static uint64_t Started,Last,Deadline;
static uint32_t Count,Cap;
static _Thread_local unsigned Depth;
static _Thread_local bool VerifiedContext;

bool TIOOpenBindObserved(TIOOpenFunction original,TIOOpenCallback callback,TIOOpenClock clock,void *context) {
    if (!original || original==TIOOpenForward || ((callback==NULL)!=(clock==NULL))) return false;
    pthread_mutex_lock(&Lock);
    bool result=atomic_load_explicit(&Original,memory_order_relaxed)==NULL;
    if (result) {
        Callback=callback;Clock=clock;Context=context;
        atomic_store_explicit(&Original,original,memory_order_release);
    }
    pthread_mutex_unlock(&Lock);
    return result;
}
bool TIOOpenBind(TIOOpenFunction original) { return TIOOpenBindObserved(original,NULL,NULL,NULL); }
void TIOOpenContextSetVerified(bool verified) { VerifiedContext=verified; }
void TIOOpenDisable(void) {
    pthread_mutex_lock(&Lock);Armed=false;Terminal=true;pthread_mutex_unlock(&Lock);
}
static bool BudgetLocked(uint64_t now) {
    if (!Armed || Terminal) return false;
    if (now<Last || now<Started || now>=Deadline || Count>=Cap) { Armed=false;Terminal=true;return false; }
    Last=now;
    return true;
}
static bool ReadClockLocked(uint64_t *now) {
    @try { *now=Clock(Context);return true; }
    @catch(NSException *exception) { (void)exception;Armed=false;Terminal=true;return false; }
}
bool TIOOpenArm(bool confirmed,uint32_t ttl,uint32_t cap) {
    if (!confirmed || ttl<1 || ttl>120 || cap<1 || cap>16) return false;
    pthread_mutex_lock(&Lock);
    if (EverArmed || Terminal || !Callback || !Clock || !atomic_load_explicit(&Original,memory_order_acquire)) {
        pthread_mutex_unlock(&Lock);return false;
    }
    uint64_t now=0,duration=(uint64_t)ttl*1000;
    if (!ReadClockLocked(&now)) { pthread_mutex_unlock(&Lock);return false; }
    if (now>UINT64_MAX-duration) { pthread_mutex_unlock(&Lock);return false; }
    Started=Last=now;Deadline=now+duration;Cap=cap;Count=0;Armed=true;EverArmed=true;
    pthread_mutex_unlock(&Lock);return true;
}
void *TIOOpenResolve(TIOResolverFunction resolver,void *handle,const char *name,TIOOpenTrust trust) {
    if (!resolver) abort();
    int incoming=errno;
    bool eligible=trust.handle_verified && trust.module_verified && trust.export_verified && trust.name_owned && trust.resolver_semantics_verified && name && strcmp(name,"isar_open_instance")==0;
    bool active=false;
    if (eligible) {
        pthread_mutex_lock(&Lock);
        uint64_t now=0;
        active=Armed && !Terminal && Clock && ReadClockLocked(&now) && BudgetLocked(now);
        pthread_mutex_unlock(&Lock);
    }
    TIOOpenFunction original=atomic_load_explicit(&Original,memory_order_acquire);
    errno=incoming;
    void *result=resolver(handle,name);
    // No resolver/error APIs, callbacks, clock or Objective-C work after resolver.
    if (eligible && active && original && result==(void *)original) return (void *)TIOOpenForward;
    return result;
}
bool TIOOpenSlotPlan(uintptr_t observed,uintptr_t expected,bool resolved,bool writable,bool aligned) {
    return observed && expected && observed==expected && resolved && writable && aligned;
}
uint8_t TIOOpenForward(TIOIsar **out,uint32_t id,TIOCString *name,TIOCString *path,bool sqlite,TIOCString *schema,uint32_t max,TIOCString *key,uint32_t compactFile,uint32_t compactBytes,float ratio) {
    TIOOpenFunction original=atomic_load_explicit(&Original,memory_order_acquire);
    if (!original) abort(); // A forwarder must never be published before immutable binding.
    int incoming=errno;
    bool observe=false;
    if (!Depth && VerifiedContext && key==NULL) {
        pthread_mutex_lock(&Lock);
        uint64_t now=0;
        if (Armed && !Terminal && Clock && ReadClockLocked(&now) && BudgetLocked(now)) { Count++;observe=true; }
        pthread_mutex_unlock(&Lock);
    }
    Depth++;
    if (observe) {
        TIOOpenObservation event={schema,id,sqlite};
        bool accepted=false;
        @try { accepted=Callback(&event,Context); }
        @catch(NSException *exception) { (void)exception; }
        if (!accepted) TIOOpenDisable();
    }
    errno=incoming;
    @try {
        return original(out,id,name,path,sqlite,schema,max,key,compactFile,compactBytes,ratio);
    } @finally {
        Depth--;
    }
}
