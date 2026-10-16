#include "Bootstrap.h"
#include "View.h"
#include "SystemResolver.h"
#include <mach-o/dyld.h>
#include <mach/mach.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
static TIOProductionBootstrapStatus Status;
static bool string_at(const char*p,char out[PATH_MAX]){if(!p)return false;for(unsigned i=0;i<PATH_MAX;i++){vm_size_t got=0;if((uintptr_t)p>UINTPTR_MAX-i||vm_read_overwrite(mach_task_self(),(vm_address_t)p+i,1,(vm_address_t)&out[i],&got)!=KERN_SUCCESS||got!=1)return false;if(!out[i])return i>0;}return false;}
static bool rw(uintptr_t slot){if(!slot||slot%8)return false;vm_address_t a=slot;vm_size_t n=0;vm_region_basic_info_data_64_t info;mach_msg_type_number_t count=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=0;kern_return_t code=vm_region_64(mach_task_self(),&a,&n,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&info,&count,&object);if(object)mach_port_deallocate(mach_task_self(),object);return code==KERN_SUCCESS&&a<=slot&&n>=8&&slot-a<=n-8&&(info.protection&3)==3&&!(info.protection&4);}
__attribute__((constructor))static void initialize(void){int saved=errno;Status.constructor_ran=true;Status.state=TIOBootstrapBlockedIdentityPolicy;
 if(!TIO_FIXED_POLICY_PRESENT)goto done;
 Status.state=TIOBootstrapBlockedMainIdentity;
 uint32_t count=_dyld_image_count();if(!count||count>1024)goto done;
 const struct mach_header*main=_dyld_get_image_header(0);char path[PATH_MAX];if(!main||!string_at(_dyld_get_image_name(0),path))goto done;
 char*last=strrchr(path,'/');if(!last||strcmp(last,"/Runner"))goto done;*last=0;
 TIOConstructorView root={.header=(uintptr_t)main,.slide=_dyld_get_image_vmaddr_slide(0),.budget=64*UINT64_C(1024)*1024};
 if(!TIOConstructorVerify(&root,&MainPolicy)||!root.info_verified)goto done;Status.main_identity_verified=true;Status.bytes_copied=root.bytes;
 Status.state=TIOBootstrapBlockedImageIdentity;
 char expected[PATH_MAX];size_t n=strlen(path),rel=strlen(FlutterPolicy.relative_path);if(!rel||n+1+rel>=sizeof expected)goto done;memcpy(expected,path,n);expected[n]='/';memcpy(expected+n+1,FlutterPolicy.relative_path,rel+1);
 unsigned candidates=0;TIOConstructorView image={0};const char*leaf=strrchr(expected,'/');leaf=leaf?leaf+1:expected;
 for(uint32_t i=0;i<count;i++){char name[PATH_MAX];if(!string_at(_dyld_get_image_name(i),name))goto done;char*end=strrchr(name,'/');end=end?end+1:name;if(strcmp(end,leaf))continue;candidates++;if(strcmp(name,expected))goto done;image=(TIOConstructorView){.header=(uintptr_t)_dyld_get_image_header(i),.slide=_dyld_get_image_vmaddr_slide(i),.budget=root.budget};}
 if(candidates!=1||_dyld_image_count()!=count||!TIOConstructorVerify(&image,&FlutterPolicy))goto done;Status.images_verified=true;Status.bytes_copied+=image.bytes;
 Status.state=TIOBootstrapBlockedSlotMetadata;if(!image.slot_metadata_verified)goto done;Status.slot_metadata_verified=true;
 Status.state=TIOBootstrapBlockedVM;if(!rw(image.slot))goto done;
 uintptr_t current=0;vm_size_t copied=0;if(vm_read_overwrite(mach_task_self(),image.slot,sizeof current,(vm_address_t)&current,&copied)!=KERN_SUCCESS||copied!=sizeof current)goto done;Status.slot_current_read=true;
 /* Calling the reviewed classifier here would call dladdr under loader lock.
  * Its ordinary-thread provenance proof is not constructor safety evidence. */
 Status.state=TIOBootstrapBlockedResolver;
 if(current==image.lazy){Status.state=TIOBootstrapBlockedLifetime;goto done;}
 if(TIOConstructorSystemResolver(current)){Status.slot_resolver_verified=true;Status.state=TIOBootstrapBlockedLifetime;}
 /* No branch can install, bind or Start without an independent mapping lifetime,
  * first-cache and loader-context contract. Raw helper is not a callable resolver. */
 done:errno=saved;
}
TIOProductionBootstrapStatus TIOProductionBootstrapGetStatus(void){return Status;}
