#include "Bootstrap.h"
#include <mach-o/dyld.h>
#include <stdatomic.h>
static _Atomic unsigned State,Callbacks;
static _Atomic bool ConstructorRan,Registered,RunnerVerified,BundleVerified,Installed;
static void image_added(const struct mach_header *header,intptr_t slide){(void)header;(void)slide;atomic_fetch_add_explicit(&Callbacks,1,memory_order_relaxed);}
#if defined(TIO_BOOTSTRAP_TESTING)
#include "FixtureFlutter.h"
#include "FixtureIdentity.h"
#include <mach-o/loader.h>
#include <mach/mach.h>
#include <pthread.h>
#include <string.h>
#include <dlfcn.h>
#include <stdlib.h>
extern bool TIOResolverBridgeBind(FixtureResolver);
extern void *TIOResolverBridge(void *,const char *);
static pthread_t ConstructorThread;
static _Thread_local unsigned ConstructorTLS;
bool TIOBootstrapFixtureConstructorSameThread(void){return ConstructorTLS==0x105&&pthread_equal(ConstructorThread,pthread_self());}
static unsigned char ExpectedRunnerUUID[16]=TIO_FIXTURE_RUNNER_UUID;
static unsigned char ExpectedFlutterUUID[16]=TIO_FIXTURE_FLUTTER_UUID;
static bool header_uuid(const struct mach_header *header,const unsigned char expected[16]){
 if(!header||header->magic!=MH_MAGIC_64)return false;const struct mach_header_64*h=(const void*)header;if(h->ncmds>1024||h->sizeofcmds>65536)return false;
 const unsigned char*commands=(const void*)(h+1);size_t offset=0;unsigned found=0;
 for(uint32_t i=0;i<h->ncmds;i++){if(offset+8>h->sizeofcmds)return false;const struct load_command*l=(const void*)(commands+offset);if(l->cmdsize<8||l->cmdsize>h->sizeofcmds-offset)return false;if(l->cmd==LC_UUID){if(l->cmdsize!=sizeof(struct uuid_command)||memcmp(((const struct uuid_command*)l)->uuid,expected,16))return false;found++;}offset+=l->cmdsize;}return found==1;
}
static bool main_identity(void){
 const struct mach_header*header=_dyld_get_image_header(0);const char*path=_dyld_get_image_name(0);if(!path||strcmp(path,TIO_FIXTURE_RUNNER_PATH)||!header_uuid(header,ExpectedRunnerUUID))return false;
 const struct mach_header_64*h=(const void*)header;const unsigned char*commands=(const void*)(h+1);size_t offset=0;unsigned flutter=0,native=0,bundle=0;
 for(uint32_t i=0;i<h->ncmds;i++){const struct load_command*l=(const void*)(commands+offset);if(l->cmd==LC_LOAD_DYLIB){const struct dylib_command*d=(const void*)l;if(d->dylib.name.offset>=l->cmdsize)return false;const char*name=(const void*)((const unsigned char*)l+d->dylib.name.offset);size_t remaining=l->cmdsize-d->dylib.name.offset;if(!memchr(name,0,remaining))return false;if(!strcmp(name,TIO_FIXTURE_FLUTTER_PATH))flutter++;if(!strcmp(name,TIO_FIXTURE_NATIVE_PATH))native++;}
 if(l->cmd==LC_SEGMENT_64){const struct segment_command_64*s=(const void*)l;if(s->cmdsize<sizeof(*s)||s->nsects>(s->cmdsize-sizeof(*s))/sizeof(struct section_64))return false;const struct section_64*t=(const void*)(s+1);for(uint32_t j=0;j<s->nsects;j++)if(!memcmp(t[j].sectname,"__info_plist",12)){if(t[j].size!=sizeof(TIO_FIXTURE_INFO)-1)return false;uintptr_t address=(uintptr_t)t[j].addr+(uintptr_t)_dyld_get_image_vmaddr_slide(0);if(memcmp((const void*)address,TIO_FIXTURE_INFO,sizeof(TIO_FIXTURE_INFO)-1))return false;bundle++;}}
 offset+=l->cmdsize;}
 atomic_store(&BundleVerified,bundle==1);return flutter==1&&native==1&&bundle==1;
}
static bool flutter_identity(void){unsigned candidates=0,exact=0;for(uint32_t i=0;i<_dyld_image_count();i++){const char*name=_dyld_get_image_name(i);if(!name)continue;const char*leaf=strrchr(name,'/');leaf=leaf?leaf+1:name;if(!strcmp(leaf,"FlutterFixture.dylib"))candidates++;if(!strcmp(name,TIO_FIXTURE_FLUTTER_PATH)){if(!header_uuid(_dyld_get_image_header(i),ExpectedFlutterUUID))return false;exact++;}}return candidates==1&&exact==1;}
static bool writable(uintptr_t slot){if(!slot||slot%8||slot>UINTPTR_MAX-8)return false;vm_address_t address=slot;vm_size_t size=0;vm_region_basic_info_data_64_t info;mach_msg_type_number_t count=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=MACH_PORT_NULL;kern_return_t result=vm_region_64(mach_task_self(),&address,&size,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&info,&count,&object);if(object)mach_port_deallocate(mach_task_self(),object);return result==KERN_SUCCESS&&address<=slot&&size>=8&&slot-address<=size-8&&(info.protection&3)==3&&!(info.protection&VM_PROT_EXECUTE);}
static bool mode(const char*expected){const char*v=getenv("TIO_FIXTURE_MODE");return v&&!strcmp(v,expected);}
static void fixture_install(void){
 ConstructorThread=pthread_self();ConstructorTLS=0x105;
 if(mode("duplicate")&&!dlopen(TIO_FIXTURE_DUPLICATE_PATH,RTLD_NOW|RTLD_LOCAL)){atomic_store(&State,TIOBootstrapBlockedIdentity);return;}
 bool runner=main_identity();atomic_store(&RunnerVerified,runner);if(!runner||!flutter_identity()||mode("wrongidentity")){atomic_store(&State,TIOBootstrapBlockedIdentity);return;}
 FixtureFlutterView view=FixtureFlutterViewNow();if(!view.constructor_ran||view.lookups){atomic_store(&State,TIOBootstrapBlockedEarlyLookup);return;}
 if(!writable(view.slot)||mode("wrongheader")){atomic_store(&State,TIOBootstrapBlockedSlot);return;}
 /* Only self fixture's known already-resolved real system import, never a disk lazy helper. */
 if(view.original!=(uintptr_t)dlsym||atomic_load((_Atomic uintptr_t*)view.slot)!=view.original||!TIOResolverBridgeBind(dlsym)){atomic_store(&State,TIOBootstrapBlockedSlot);return;}
 if(mode("race"))FixtureFlutterCompete();uintptr_t expected=view.original;
 if(!atomic_compare_exchange_strong_explicit((_Atomic uintptr_t*)view.slot,&expected,(uintptr_t)TIOResolverBridge,memory_order_acq_rel,memory_order_acquire)){atomic_store(&State,TIOBootstrapRaceLost);return;}
 if(!pthread_equal(ConstructorThread,pthread_self())||atomic_load((_Atomic uintptr_t*)view.slot)!=(uintptr_t)TIOResolverBridge){atomic_store(&State,TIOBootstrapRaceLost);return;}
 atomic_store(&Installed,true);atomic_store(&State,TIOBootstrapFixtureInstalled);
}
#endif
__attribute__((constructor))static void bootstrap_ctor(void){
 atomic_store(&ConstructorRan,true);atomic_store(&State,TIOBootstrapBlockedIdentity);
 _dyld_register_func_for_add_image(image_added);atomic_store(&Registered,true);
#if defined(TIO_BOOTSTRAP_TESTING)
 fixture_install();
#endif
}
TIOBootstrapStatus TIOBootstrapGetStatus(void){return(TIOBootstrapStatus){atomic_load(&State),atomic_load(&ConstructorRan),atomic_load(&Registered),atomic_load(&RunnerVerified),atomic_load(&BundleVerified),atomic_load(&Installed),false,atomic_load(&Callbacks)};}
