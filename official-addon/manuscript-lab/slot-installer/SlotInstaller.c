#define _DARWIN_C_SOURCE
#include "SlotInstaller.h"
#include "../image-attestation/ImageAttestation.h"
#include "../memory-copy/MemoryCopy.h"
#include "../resolver-bridge/ResolverBridge.h"
#include <mach/mach.h>
#include <mach-o/dyld.h>
#include <stdatomic.h>
#include <string.h>

static bool writable_slot(uintptr_t slot) {
 if(!slot||slot%sizeof(uintptr_t)||slot>UINTPTR_MAX-sizeof(uintptr_t))return false;
 vm_address_t address=(vm_address_t)slot;vm_size_t size=0;vm_region_basic_info_data_64_t info;
 mach_msg_type_number_t count=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=MACH_PORT_NULL;
 kern_return_t code=vm_region_64(mach_task_self(),&address,&size,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&info,&count,&object);
 if(object!=MACH_PORT_NULL)mach_port_deallocate(mach_task_self(),object);
 if(code!=KERN_SUCCESS||address>slot||size<sizeof(uintptr_t)||slot-address>size-sizeof(uintptr_t))return false;
 return (info.protection&(VM_PROT_READ|VM_PROT_WRITE))==(VM_PROT_READ|VM_PROT_WRITE)&&!(info.protection&VM_PROT_EXECUTE);
}
static TIOSlotResult result(TIOSlotStatus status) {return (TIOSlotResult){.status=status,.module_linked=true};}
TIOSlotResult TIOSlotInstallerInspect(const void *header,const char *root) {
 TIOImageBudget budget;TIOImageBudgetInit(&budget);TIOImageProof first={0},second={0};
 uint32_t count=_dyld_image_count();
 if(TIOImageAttestLoaded(TIOImageFlutter,header,root,TIOMemoryCopySelf,NULL,&budget,&first)!=TIOImageOK||!first.loaded_identity_verified||!first.slot_metadata_verified)return result(TIOSlotBlockedNotProven);
 TIOImageBudgetInit(&budget);
 if(TIOImageAttestLoaded(TIOImageFlutter,header,root,TIOMemoryCopySelf,NULL,&budget,&second)!=TIOImageOK||_dyld_image_count()!=count||!second.loaded_identity_verified||!second.slot_metadata_verified||first.slot_address!=second.slot_address)return result(TIOSlotIdentityChanged);
 if(!writable_slot(second.slot_address))return result(TIOSlotReadOnly);
 /* Static identity and two enumerations do not pin dyld mappings or certify
  * a current lazy-helper value. No deployment capability exists in this scope.
  * Do not even read the real slot contents, much less CAS it. */
 return result(TIOSlotUnsupportedLifetime);
}

#ifdef TIO_SLOT_TESTING
#include <CommonCrypto/CommonDigest.h>
#include <mach-o/loader.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <pthread.h>
#include <dlfcn.h>
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
struct TIOSlotFixture {
 unsigned char *mapping;size_t size;uintptr_t slot;TIOSlotFixtureKind kind;
 TIOImageTestPolicy policy;_Atomic bool attempted;
};
static pthread_once_t BridgeOnce=PTHREAD_ONCE_INIT;
static void bind_bridge(void){if(!TIOResolverBridgeBind(dlsym))abort();}
static uintptr_t fixture_competitor(void){return 0x333;}
TIOSlotFixture *TIOSlotFixtureCreate(TIOSlotFixtureKind kind) {
 if(pthread_once(&BridgeOnce,bind_bridge))abort();
 TIOSlotFixture*f=calloc(1,sizeof(*f));if(!f)return NULL;
 f->size=3*4096;f->mapping=mmap(NULL,f->size,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANON,-1,0);if(f->mapping==MAP_FAILED){free(f);return NULL;}f->kind=kind;
 struct mach_header_64*h=(void*)f->mapping;h->magic=MH_MAGIC_64;h->cputype=CPU_TYPE_ARM64;h->filetype=MH_DYLIB;h->ncmds=2;h->sizeofcmds=152+24;
 struct segment_command_64*s=(void*)(h+1);s->cmd=LC_SEGMENT_64;s->cmdsize=152;memcpy(s->segname,"__TEXT",7);s->vmsize=8192;s->filesize=8192;s->maxprot=5;s->initprot=5;s->nsects=1;
 struct section_64*t=(void*)(s+1);memcpy(t->sectname,"__text",7);memcpy(t->segname,"__TEXT",7);t->addr=4096;t->offset=4096;t->size=32;t->flags=S_ATTR_PURE_INSTRUCTIONS;
 struct uuid_command*u=(void*)(t+1);u->cmd=LC_UUID;u->cmdsize=24;for(unsigned i=0;i<16;i++)u->uuid[i]=(unsigned char)(i+1);memset(f->mapping+4096,0x71,32);
 f->policy.cpu=CPU_TYPE_ARM64;memcpy(f->policy.uuid,u->uuid,16);f->policy.text_va=4096;f->policy.text_size=32;CC_SHA256(f->mapping+4096,32,f->policy.text_sha);f->policy.relative_path="Frameworks/OwnSlot.dylib";
 f->slot=(uintptr_t)(f->mapping+8192);atomic_init((_Atomic uintptr_t*)f->slot,(uintptr_t)dlsym);
 if(kind==TIOFixtureTextTampered)f->mapping[4096]^=1;
 if(kind==TIOFixtureSignatureOnly)f->mapping[4096+128]=0x42; // Outside immutable __text; not an identity credential.
 if(kind==TIOFixtureUnaligned)f->slot++;
 if(kind==TIOFixtureForeignSlot)f->slot=(uintptr_t)(f->mapping+8192+8);
 if(kind==TIOFixtureForeignValue)atomic_store((_Atomic uintptr_t*)f->slot,(uintptr_t)fixture_competitor);
 if(mprotect(f->mapping,8192,PROT_READ)){abort();}
 if(kind==TIOFixtureReadOnly&&mprotect(f->mapping+8192,4096,PROT_READ))abort();
 if(kind==TIOFixtureUnreadable&&mprotect(f->mapping+8192,4096,PROT_NONE))abort();
 if(kind==TIOFixtureUnmapped&&munmap(f->mapping+8192,4096))abort();
 return f;
}
static bool fixture_identity(TIOSlotFixture*f) {
 TIOImageBudget budget;TIOImageBudgetInit(&budget);TIOImageProof proof;
 intptr_t slide=f->kind==TIOFixtureSlideOverflow?INTPTR_MIN:(intptr_t)f->mapping;
 unsigned count=f->kind==TIOFixtureUUIDDuplicate?2:1;
 return TIOImageTestView(&f->policy,(uintptr_t)f->mapping,slide,f->policy.relative_path,count,TIOMemoryCopySelf,NULL,&budget,&proof)==TIOImageOK&&proof.content_verified;
}
static void *competing_cas(void *context) {
 TIOSlotFixture*f=context;uintptr_t expected=(uintptr_t)dlsym;
 if(!atomic_compare_exchange_strong((_Atomic uintptr_t*)f->slot,&expected,(uintptr_t)fixture_competitor))abort();
 return NULL;
}
TIOSlotResult TIOSlotFixtureAttempt(TIOSlotFixture*f) {
 if(!f)return result(TIOSlotBlockedNotProven);
 bool no=false;if(!atomic_compare_exchange_strong(&f->attempted,&no,true))return result(TIOSlotAlreadyAttempted);
 if(!fixture_identity(f)||!fixture_identity(f))return result(TIOSlotIdentityChanged);
 if(f->slot!=(uintptr_t)(f->mapping+8192)||!writable_slot(f->slot))return result(TIOSlotReadOnly);
 uintptr_t before=0;size_t copied=0;if(!TIOMemoryCopySelf(NULL,f->slot,sizeof(before),&before,&copied)||copied!=sizeof(before))return result(TIOSlotBlockedNotProven);
 if(before!=(uintptr_t)dlsym)return result(TIOSlotWrongValue);
 if(f->kind==TIOFixtureRace){pthread_t thread;if(pthread_create(&thread,NULL,competing_cas,f)||pthread_join(thread,NULL))abort();}
 uintptr_t expected=(uintptr_t)dlsym;
 if(!atomic_compare_exchange_strong_explicit((_Atomic uintptr_t*)f->slot,&expected,(uintptr_t)TIOResolverBridge,memory_order_acq_rel,memory_order_acquire))return result(TIOSlotRaceLost);
 if(atomic_load_explicit((_Atomic uintptr_t*)f->slot,memory_order_acquire)!=(uintptr_t)TIOResolverBridge)return result(TIOSlotRaceLost);
 TIOSlotResult r=result(TIOSlotInstalled);r.slot_can_install=true;r.slot_installed=true;return r;
}
bool TIOSlotFixtureValueMatchesOriginal(TIOSlotFixture*f){return f&&atomic_load((_Atomic uintptr_t*)f->slot)==(uintptr_t)dlsym;}
bool TIOSlotFixtureValueMatchesCompetitor(TIOSlotFixture*f){return f&&atomic_load((_Atomic uintptr_t*)f->slot)==(uintptr_t)fixture_competitor;}
bool TIOSlotFixtureCachedForwardStillValid(TIOSlotFixture*f){if(!f||atomic_load((_Atomic uintptr_t*)f->slot)!=(uintptr_t)TIOResolverBridge)return false;TIOResolverFunction cached=TIOResolverBridge;return cached(RTLD_DEFAULT,"malloc")==dlsym(RTLD_DEFAULT,"malloc");}
#endif
