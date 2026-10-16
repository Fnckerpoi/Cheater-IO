#include "Integration.h"
#include <stdatomic.h>
static _Atomic unsigned State=TIOIntegrationBlockedBundle;
static _Atomic bool Ran,Installed,Observed;
#if defined(TIO_INTEGRATION_FIXTURE)
#include "OwnPolicy.h"
#include "Capture.h"
#include "CaptureInternal.h"
#include "ResolverSlot.h"
#include "ResolverBridge.h"
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach/mach.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <limits.h>
static TIOCaptureOpen Expected;
static _Atomic bool Bound;
extern void *TIOIntegrationSelector(void*,const char*);
extern unsigned IntegrationFixtureLookupCount(void);
static const unsigned char RunnerUUID[16]=TIO_OWN_RUNNER_UUID;
static const unsigned char FlutterUUID[16]=TIO_OWN_FLUTTER_UUID,EngineUUID[16]=TIO_OWN_ENGINE_UUID;
static bool mode(const char*s){const char*m=getenv("TIO_INTEGRATION_MODE");return m&&!strcmp(s,m);}
static bool uuid(const struct mach_header*h,const unsigned char*expected){if(!h||h->magic!=MH_MAGIC_64)return false;const struct mach_header_64*m=(const void*)h;if(m->ncmds>512||m->sizeofcmds>65536)return false;size_t offset=0;unsigned found=0;const unsigned char*b=(const void*)(m+1);for(unsigned i=0;i<m->ncmds;i++){if(offset+8>m->sizeofcmds)return false;const struct load_command*l=(const void*)(b+offset);if(l->cmdsize<8||l->cmdsize>m->sizeofcmds-offset)return false;if(l->cmd==LC_UUID){if(l->cmdsize!=24||memcmp(((const struct uuid_command*)l)->uuid,expected,16))return false;found++;}offset+=l->cmdsize;}return found==1;}
static bool rw(uintptr_t slot){vm_address_t a=slot;vm_size_t n=0;vm_region_basic_info_data_64_t i;mach_msg_type_number_t c=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=0;kern_return_t k=vm_region_64(mach_task_self(),&a,&n,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&i,&c,&object);if(object)mach_port_deallocate(mach_task_self(),object);return k==KERN_SUCCESS&&a<=slot&&n>=8&&slot-a<=n-8&&(i.protection&3)==3&&!(i.protection&4)&&!(slot%8);}
static bool target_name(const char*s){static const char expected[]="isar_open_instance";for(size_t i=0;i<sizeof expected;i++){unsigned char byte=0;vm_size_t count=0;uintptr_t address=(uintptr_t)s;if(address>UINTPTR_MAX-i||vm_read_overwrite(mach_task_self(),address+i,1,(vm_address_t)&byte,&count)!=KERN_SUCCESS||count!=1||byte!=(unsigned char)expected[i])return false;}return true;}
static int capture_directory(void){
 const char*mainpath=_dyld_get_image_name(0);if(!mainpath||strcmp(mainpath,TIO_OWN_RUNNER_PATH)||!uuid(_dyld_get_image_header(0),RunnerUUID))return -1;
 char path[PATH_MAX];size_t length=strlen(mainpath);if(length>=sizeof path)return -1;memcpy(path,mainpath,length+1);char*last=strrchr(path,'/');if(!last||strcmp(last,"/Runner"))return -1;*last=0;
 int current=open("/",O_RDONLY|O_DIRECTORY|O_CLOEXEC);if(current<0)return -1;char*part=path+1;
 while(*part){char*end=strchr(part,'/');if(end)*end=0;if(!*part||!strcmp(part,".")||!strcmp(part,"..")){close(current);return -1;}int next=openat(current,part,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);close(current);if(next<0)return -1;current=next;if(!end)break;part=end+1;}
 int info=openat(current,"Info.plist",O_RDONLY|O_NOFOLLOW|O_CLOEXEC);static const char expected[]=TIO_OWN_INFO;char bytes[sizeof expected];ssize_t got=info<0?-1:read(info,bytes,sizeof bytes);if(info>=0)close(info);if(got!=(ssize_t)(sizeof expected-1)||memcmp(bytes,expected,sizeof expected-1)){close(current);return -1;}
 const char*names[]={"Documents","ManuscriptLab"};for(unsigned i=0;i<2;i++){if(mkdirat(current,names[i],0700)&&errno!=EEXIST){close(current);return -1;}int next=openat(current,names[i],O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);close(current);if(next<0)return -1;struct stat st;if(fstat(next,&st)||!S_ISDIR(st.st_mode)||st.st_uid!=geteuid()||(i==1&&(st.st_mode&07777)!=0700)){close(next);return -1;}current=next;}
 return current;
}
void *TIOIntegrationDefault(void*handle,const char*name){int before=errno;bool eligible=target_name(name);errno=before;void*result=dlsym(handle,name);int after=errno;
 if(eligible&&result==(void*)Expected){bool unused=false;if(atomic_compare_exchange_strong(&Bound,&unused,true)){if(TIOCaptureBindOriginalInternal(Expected)){atomic_store(&Observed,true);atomic_store(&State,TIOIntegrationWaitingCapture);int directory=capture_directory();if(directory>=0&&!mode("nostart")){bool started=TIOCaptureStartInternal(directory);close(directory);(void)started;}}else{errno=after;return result;}}if(atomic_load(&Observed)){errno=after;return(void*)TIOCaptureForward;}}
 errno=after;return result;}
static void fixture(void){if(mode("bundle")||mode("uuid")){atomic_store(&State,TIOIntegrationBlockedImage);return;}
 uintptr_t slot=0,entry=0;unsigned fl=0,en=0;for(unsigned i=0;i<_dyld_image_count();i++){const char*p=_dyld_get_image_name(i);if(!p)continue;if(!strcmp(p,TIO_OWN_FLUTTER_PATH)){if(!uuid(_dyld_get_image_header(i),FlutterUUID))return;intptr_t slide=_dyld_get_image_vmaddr_slide(i);if(slide<0||(uintptr_t)slide>UINTPTR_MAX-TIO_OWN_SLOT)return;slot=(uintptr_t)slide+TIO_OWN_SLOT;fl++;}if(!strcmp(p,TIO_OWN_ENGINE_PATH)){if(!uuid(_dyld_get_image_header(i),EngineUUID))return;intptr_t slide=_dyld_get_image_vmaddr_slide(i);if(slide<0||(uintptr_t)slide>UINTPTR_MAX-TIO_OWN_ENTRY)return;entry=(uintptr_t)slide+TIO_OWN_ENTRY;en++;}}
 if(fl!=1||en!=1||!entry){atomic_store(&State,TIOIntegrationBlockedImage);return;}
 if(IntegrationFixtureLookupCount()!=0){atomic_store(&State,TIOIntegrationUnobserved);return;}
 if(!rw(slot)){atomic_store(&State,TIOIntegrationBlockedVM);return;}
 uintptr_t current=atomic_load((_Atomic uintptr_t*)slot);if(mode("pointer")||TIOClassifyResolverSlot(current)!=TIOSlotExpectedResolver){atomic_store(&State,TIOIntegrationBlockedSlot);return;}
 if(!TIOResolverBridgeBind(dlsym)){atomic_store(&State,TIOIntegrationBlockedSlot);return;}Expected=(TIOCaptureOpen)entry;
 if(!atomic_compare_exchange_strong((_Atomic uintptr_t*)slot,&current,(uintptr_t)TIOIntegrationSelector)){atomic_store(&State,TIOIntegrationBlockedSlot);return;}
 atomic_store(&Installed,true);atomic_store(&State,TIOIntegrationArmed);
}
#endif
__attribute__((constructor))static void initialize(void){atomic_store(&Ran,true);
#if defined(TIO_INTEGRATION_FIXTURE)
 fixture();
#endif
}
TIOIntegrationStatus TIOIntegrationGetStatus(void){return(TIOIntegrationStatus){atomic_load(&State),atomic_load(&Ran),atomic_load(&Installed),atomic_load(&Observed),
#if defined(TIO_INTEGRATION_FIXTURE)
 TIOCaptureGetState()==TIOCaptureWritten
#else
 false
#endif
};}
