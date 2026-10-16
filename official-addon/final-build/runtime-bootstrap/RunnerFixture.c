#include "Bootstrap.h"
#include "FixtureFlutter.h"
#include <assert.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
static _Thread_local unsigned TLSMarker;
int main(void){
 TLSMarker=0x105;TIOBootstrapStatus s=TIOBootstrapGetStatus();assert(s.constructor_ran&&s.add_image_registered&&s.image_callbacks>0);assert(TLSMarker==0x105);assert(TIOBootstrapFixtureConstructorSameThread());
 const char*mode=getenv("TIO_FIXTURE_MODE");FixtureFlutterView before=FixtureFlutterViewNow();
 if(mode&&strcmp(mode,"early")==0){assert(!s.slot_installed&&s.state==TIOBootstrapBlockedEarlyLookup&&before.lookups==1);assert(atomic_load((_Atomic uintptr_t*)before.slot)==before.original);puts("BOOTSTRAP_EARLY_FFI_BLOCKED_PASS: Flutter constructor ran FFI before Native ctor; before-main is not sufficient");return 0;}
 if(mode&&strcmp(mode,"race")==0){assert(!s.slot_installed&&s.state==TIOBootstrapRaceLost&&atomic_load((_Atomic uintptr_t*)before.slot)==before.competitor);puts("BOOTSTRAP_RACE_BLOCKED_PASS");return 0;}
 if(mode&&strcmp(mode,"readonly")==0){assert(!s.slot_installed&&s.state==TIOBootstrapBlockedSlot&&atomic_load((_Atomic uintptr_t*)before.slot)==before.original);puts("BOOTSTRAP_READONLY_BLOCKED_PASS");return 0;}
 if(mode&&(strcmp(mode,"wrongidentity")==0||strcmp(mode,"duplicate")==0||strcmp(mode,"wrongheader")==0||strcmp(mode,"wrongvalue")==0)){assert(!s.slot_installed);assert(atomic_load((_Atomic uintptr_t*)before.slot)==(strcmp(mode,"wrongvalue")==0?before.competitor:before.original));puts("BOOTSTRAP_IDENTITY_OR_SLOT_BLOCKED_PASS");return 0;}
 assert(s.slot_installed&&s.state==TIOBootstrapFixtureInstalled&&s.runner_identity_verified&&s.bundle_identity_verified&&before.lookups==0);
 void *result=FixtureFlutterLookup(RTLD_DEFAULT,"malloc");assert(result==dlsym(RTLD_DEFAULT,"malloc"));assert(FixtureFlutterViewNow().lookups==1);assert(!s.event_observed);puts("BOOTSTRAP_OWN_LOADER_GREEN: actual Native ctor before first main FFI lookup; not official runtime proof");return 0;
}
