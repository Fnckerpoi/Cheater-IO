#include "Adapter.h"
#include <stdbool.h>
#include <stdint.h>
#include <assert.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <mach/mach.h>
extern void *OwnFlutterLookup(void*,const char*);
extern unsigned OwnFlutterCount(void);
typedef uint8_t(*Open)(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
static uint64_t*schema(void){const char*j="[{\"name\":\"OwnPublicSchema\",\"properties\":[{\"name\":\"title\",\"type\":\"String\"}]}]";uint64_t*o=calloc(3,8);o[1]=(uintptr_t)strdup(j);o[2]=strlen(j);return o;}
int main(void){TIOAdapterStatus s=TIOAdapterGetStatus();assert(s.constructor_ran);fprintf(stderr,"OWN_STATUS=%u initial=%llx installed=%u\n",s.state,(unsigned long long)s.initial_slot_value,s.slot_installed);Dl_info own={0};assert(dladdr((void*)OwnFlutterLookup,&own));uintptr_t slot=(uintptr_t)own.dli_fbase+0x2000;vm_address_t va=slot;vm_size_t size=0;vm_region_basic_info_data_64_t vi;mach_msg_type_number_t count=VM_REGION_BASIC_INFO_COUNT_64;mach_port_t object=0;kern_return_t kr=vm_region_64(mach_task_self(),&va,&size,VM_REGION_BASIC_INFO_64,(vm_region_info_t)&vi,&count,&object);if(object)mach_port_deallocate(mach_task_self(),object);fprintf(stderr,"OWN_ACTUAL_SLOT=%llx EXPECTED=%llx VM_QUERY=%d PROT=%d\n",(unsigned long long)*(uintptr_t*)slot,(unsigned long long)s.initial_slot_value,kr,kr?0:vi.protection);assert(s.slot_installed&&"Actual self linker lazy dlsym slot must be connected before first FFI lookup");Open p=(Open)OwnFlutterLookup(RTLD_DEFAULT,"isar_open_instance");assert(p);void*out=NULL;assert(p(&out,201,NULL,NULL,false,schema(),65536,NULL,0,0,0)==0x5a);assert(TIOAdapterGetStatus().capture_written);puts("REAL_LINKER_LAZY_LOOKUP_SCHEMA_FILE_PASS");return 0;}
