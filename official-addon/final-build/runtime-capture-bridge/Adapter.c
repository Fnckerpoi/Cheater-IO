#include "Adapter.h"
#include <stdatomic.h>
static _Atomic unsigned State=TIOAdapterBlockedBundlePolicy;
static _Atomic bool Ran,Installed,Observed;
static uintptr_t Initial;
#if defined(TIO_RUNTIME_CAPTURE_TESTING)
#include "FixturePolicy.h"
#include "Capture.h"
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach/mach.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
void *(*TIOOwnRealResolver)(void*,const char*);
extern void *TIOOwnSelector(void*,const char*);
extern uint8_t TIOCaptureForward(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
static void*ExpectedOpen;
static uint64_t seconds(void){return 100;}
static unsigned char ExpectedUUID[16]=TIO_OWN_UUID;
static bool uuid_ok(const struct mach_header*h){if(h->magic!=MH_MAGIC_64)return false;const struct mach_header_64*m=(const void*)h;size_t o=0;unsigned n=0;const unsigned char*b=(const void*)(m+1);for(unsigned i=0;i<m->ncmds;i++){if(o+8>m->sizeofcmds)return false;const struct load_command*l=(const void*)(b+o);if(l->cmdsize<8||l->cmdsize>m->sizeofcmds-o)return false;if(l->cmd==LC_UUID){if(l->cmdsize!=24||memcmp(((const struct uuid_command*)l)->uuid,ExpectedUUID,16))return false;n++;}o+=l->cmdsize;}return n==1;}
static bool writable(uintptr_t slot){vm_address_t a=slot;vm_size_t n=0;vm_region_basic_info_data_64_t i;mach_msg_type_number_t c=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=0;kern_return_t k=vm_region_64(mach_task_self(),&a,&n,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&i,&c,&object);if(object)mach_port_deallocate(mach_task_self(),object);return k==0&&a<=slot&&n>=8&&slot-a<=n-8&&(i.protection&3)==3&&!(i.protection&4)&&!(slot%8);}
void *TIOOwnSelectDefault(void*h,const char*name){int e=errno;void*r=TIOOwnRealResolver(h,name);int after=errno;/* Expected pointer equality, no name dereference or extra lookup. */if(r==ExpectedOpen){atomic_store(&Observed,true);errno=after;return(void*)TIOCaptureForward;}errno=after;(void)e;return r;}
static void fixture(void){
 uintptr_t slot=0;unsigned found=0;
 for(unsigned i=0;i<_dyld_image_count();i++){const char*p=_dyld_get_image_name(i);if(p&&!strcmp(p,TIO_OWN_FLUTTER_PATH)){if(!uuid_ok(_dyld_get_image_header(i)))return;intptr_t slide=_dyld_get_image_vmaddr_slide(i);slot=(uintptr_t)slide+TIO_OWN_SLOT;Initial=(uintptr_t)slide+TIO_OWN_HELPER;found++;}}
 if(found!=1||!slot||!writable(slot)||atomic_load((_Atomic uintptr_t*)slot)!=Initial){atomic_store(&State,TIOAdapterBlockedSlotState);return;}
 /* Self-owned fixture setup uses an ordinary imported target, never an official resolver query. */
 extern uint8_t isar_open_instance(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
 ExpectedOpen=(void*)isar_open_instance;TIOOwnRealResolver=dlsym;
 const char*home=getenv("TIO_OWN_PUBLIC_HOME");if(!home||!TIOCaptureFixtureBind((TIOCaptureOpen)ExpectedOpen,home,seconds))return;
 uintptr_t expected=Initial;if(!atomic_compare_exchange_strong((_Atomic uintptr_t*)slot,&expected,(uintptr_t)TIOOwnSelector)){atomic_store(&State,TIOAdapterRaceLost);return;}
 atomic_store(&Installed,true);atomic_store(&State,TIOAdapterOwnFixtureInstalled);
}
#endif
__attribute__((constructor))static void adapter_constructor(void){atomic_store(&Ran,true);
#if defined(TIO_RUNTIME_CAPTURE_TESTING)
 fixture();
#endif
}
TIOAdapterStatus TIOAdapterGetStatus(void){return(TIOAdapterStatus){atomic_load(&State),atomic_load(&Ran),atomic_load(&Installed),atomic_load(&Observed),
#if defined(TIO_RUNTIME_CAPTURE_TESTING)
 TIOCaptureGetState()==TIOCaptureWritten,
#else
 false,
#endif
 Initial};}
