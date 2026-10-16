#include "Activation.h"
#include "Imported102/Bootstrap.h"
#include <mach/mach.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <string.h>
#include <stdint.h>
#include <limits.h>
static TIOActivationStatus Status;
extern void TIOActivationRunIdentity(void);
static bool copy(uintptr_t p,void*d,size_t n){if(!p||!n||n>65536||p>UINTPTR_MAX-n)return false;vm_size_t copied=0;return vm_read_overwrite(mach_task_self(),p,n,(vm_address_t)d,&copied)==KERN_SUCCESS&&copied==n;}
static bool graph(void){uintptr_t base=(uintptr_t)_dyld_get_image_header(0);struct mach_header_64 h;if(!copy(base,&h,sizeof h)||h.magic!=MH_MAGIC_64||h.filetype!=MH_EXECUTE||h.sizeofcmds>65504||h.ncmds>512||base>UINTPTR_MAX-sizeof h-h.sizeofcmds)return false;char mainpath[PATH_MAX];const char*p=_dyld_get_image_name(0);if(!p)return false;unsigned end=0;for(;end<sizeof mainpath;end++){if(!copy((uintptr_t)p+end,&mainpath[end],1))return false;if(!mainpath[end])break;}if(end==sizeof mainpath)return false;char*leaf=strrchr(mainpath,'/');if(!leaf||strcmp(leaf,"/Runner"))return false;*leaf=0;
 const char*leaves[]={"OwnImage.dylib","OwnActivation.dylib","OwnIsar.dylib","OwnA.dylib","OwnB.dylib","OwnC.dylib"};unsigned matches[6]={0};unsigned char commands[65504];if(!copy(base+sizeof h,commands,h.sizeofcmds))return false;size_t offset=0;
 for(unsigned j=0;j<h.ncmds;j++){if(h.sizeofcmds-offset<8)return false;struct load_command l;memcpy(&l,commands+offset,8);if(l.cmdsize<8||l.cmdsize>h.sizeofcmds-offset)return false;if(l.cmd==LC_LOAD_DYLIB){struct dylib_command d;if(l.cmdsize<sizeof d)return false;memcpy(&d,commands+offset,sizeof d);if(d.dylib.name.offset>=l.cmdsize)return false;const char*name=(const void*)(commands+offset+d.dylib.name.offset);if(!memchr(name,0,l.cmdsize-d.dylib.name.offset))return false;for(unsigned k=0;k<6;k++){size_t a=strlen(mainpath),b=strlen(leaves[k]);if(a+1+b>=PATH_MAX)return false;char expected[PATH_MAX];memcpy(expected,mainpath,a);expected[a]='/';memcpy(expected+a+1,leaves[k],b+1);if(!strcmp(name,expected)){unsigned loaded=0;for(unsigned i=0;i<_dyld_image_count();i++){const char*q=_dyld_get_image_name(i);if(q&&!strcmp(q,expected))loaded++;}if(loaded!=1)return false;matches[k]++;}}}offset+=l.cmdsize;}
 if(offset!=h.sizeofcmds)return false;for(unsigned k=0;k<6;k++)if(matches[k]!=1)return false;return true;}
__attribute__((constructor))static void initialize(void){Status.constructor_ran=true;Status.state=TIOActivationBlockedIdentity;TIOActivationRunIdentity();TIOProductionBootstrapStatus proof=TIOProductionBootstrapGetStatus();if(!proof.main_identity_verified)return;Status.identity_verified=true;Status.state=TIOActivationBlockedDependencyGraph;if(!graph())return;Status.strong_dependencies_verified=true;Status.state=TIOActivationBlockedImage;if(!proof.images_verified)return;Status.state=TIOActivationBlockedSlot;if(!proof.slot_metadata_verified||!proof.slot_current_read||!proof.slot_resolver_verified)return;Status.resolved_slot_verified=true;
 /* Strong main dependencies support image retention, not first-FFI ordering or
  * reentrancy safety of OS helper calls under every supported dyld lock. */
 Status.state=TIOActivationBlockedLoaderContext;
}
TIOActivationStatus TIOActivationGetStatus(void){return Status;}
