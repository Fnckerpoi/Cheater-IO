#include "SlotInstaller.h"
#include <assert.h>
#include <stdio.h>
#include <stdatomic.h>
#include <pthread.h>
static TIOSlotFixture *shared;
static _Atomic unsigned installed,refused;
static void *attempt(void *unused){(void)unused;TIOSlotResult r=TIOSlotFixtureAttempt(shared);if(r.slot_installed)atomic_fetch_add(&installed,1);else if(r.status==TIOSlotAlreadyAttempted)atomic_fetch_add(&refused,1);else assert(0);return NULL;}
int main(void){
 TIOSlotFixture*f=TIOSlotFixtureCreate(TIOFixtureValid);assert(f);
 TIOSlotResult r=TIOSlotFixtureAttempt(f);assert(r.status==TIOSlotInstalled&&r.slot_installed&&!r.event_observed&&!r.schema_validated);
 assert(TIOSlotFixtureAttempt(f).status==TIOSlotAlreadyAttempted);assert(TIOSlotFixtureCachedForwardStillValid(f));
 struct{TIOSlotFixtureKind kind;TIOSlotStatus status;}cases[]={
 {TIOFixtureReadOnly,TIOSlotReadOnly},{TIOFixtureUnaligned,TIOSlotReadOnly},
 {TIOFixtureForeignValue,TIOSlotWrongValue},{TIOFixtureRace,TIOSlotRaceLost},
 {TIOFixtureTextTampered,TIOSlotIdentityChanged},{TIOFixtureUUIDDuplicate,TIOSlotIdentityChanged},
 {TIOFixtureSlideOverflow,TIOSlotIdentityChanged},{TIOFixtureUnreadable,TIOSlotReadOnly},
 {TIOFixtureUnmapped,TIOSlotReadOnly},{TIOFixtureForeignSlot,TIOSlotReadOnly}};
 for(unsigned i=0;i<sizeof(cases)/sizeof(cases[0]);i++){TIOSlotFixture*n=TIOSlotFixtureCreate(cases[i].kind);assert(n);r=TIOSlotFixtureAttempt(n);assert(r.status==cases[i].status&&!r.slot_installed&&!r.event_observed&&!r.schema_validated);if(cases[i].kind==TIOFixtureRace)assert(TIOSlotFixtureValueMatchesCompetitor(n));}
 TIOSlotFixture*outside=TIOSlotFixtureCreate(TIOFixtureSignatureOnly);assert(TIOSlotFixtureAttempt(outside).slot_installed);
 shared=TIOSlotFixtureCreate(TIOFixtureValid);pthread_t threads[8];for(unsigned i=0;i<8;i++)assert(!pthread_create(&threads[i],NULL,attempt,NULL));for(unsigned i=0;i<8;i++)assert(!pthread_join(threads[i],NULL));assert(installed==1&&refused==7);
 // Demonstrate the value-CAS ABA limitation rather than pretending to detect it.
 _Atomic uintptr_t raw=1;uintptr_t expected=1;assert(atomic_compare_exchange_strong(&raw,&expected,2));expected=2;assert(atomic_compare_exchange_strong(&raw,&expected,1));expected=1;assert(atomic_compare_exchange_strong(&raw,&expected,3));
 TIOSlotResult production=TIOSlotInstallerInspect(NULL,NULL);assert(production.status==TIOSlotBlockedNotProven&&!production.slot_installed&&!production.event_observed&&!production.schema_validated);
 puts("SLOT_OWN_MAPPED_CAS_PASS: actual current-task VM protections, CAS/readback once, competitor preserved, no actual official slot writes; ABA limitation explicit");return 0;
}
