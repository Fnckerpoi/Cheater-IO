#include "ResolverSlot.h"
#include "OwnPolicy.h"
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <assert.h>
#include <stdio.h>
#include <errno.h>
#include <pthread.h>
#include <string.h>
extern void *OwnLookup(void*,const char*);
extern unsigned OwnLookupCalls(void);
static void *thread(void*x){(void)x;dlsym(RTLD_DEFAULT,"__TIO_PUBLIC_INTENTIONAL_MISSING__");errno=ERANGE;assert(TIOClassifyResolverSlot((uintptr_t)dlsym)==TIOSlotExpectedResolver);assert(errno==ERANGE);const char*error=dlerror();assert(error&&strstr(error,"__TIO_PUBLIC_INTENTIONAL_MISSING__"));return NULL;}
int main(int argc,char**argv){
 if(argc==2&&!strcmp(argv[1],"counterfeit")){assert(TIOClassifyResolverSlot((uintptr_t)dlsym)==TIOSlotUnknown);puts("COUNTERFEIT_IMPORTED_DLSYM_REFUSED_PASS");return 0;}
 errno=E2BIG;assert(TIOClassifyResolverSlot((uintptr_t)dlsym)==TIOSlotExpectedResolver);assert(errno==E2BIG);assert(TIOClassifyResolverSlot(0)==TIOSlotUnknown);assert(TIOClassifyResolverSlot(1)==TIOSlotUnknown);assert(TIOClassifyResolverSlot((uintptr_t)dladdr)==TIOSlotUnknown);assert(TIOClassifyResolverSlot((uintptr_t)dlsym+1)==TIOSlotUnknown);
 Dl_info own={0};assert(dladdr((void*)OwnLookup,&own));uintptr_t slide=0;unsigned found=0;for(unsigned i=0;i<_dyld_image_count();i++)if((void*)_dyld_get_image_header(i)==own.dli_fbase){slide=(uintptr_t)_dyld_get_image_vmaddr_slide(i);found++;}assert(found==1&&OwnLookupCalls()==0);uintptr_t actual=*(const uintptr_t*)(slide+TIO_OWN_SLOT);assert(actual!=(slide+TIO_OWN_HELPER));assert(actual==(uintptr_t)dlsym);assert(TIOClassifyResolverSlot(actual)==TIOSlotExpectedResolver);assert(TIOClassifyResolverSlot(slide+TIO_OWN_HELPER)==TIOSlotUnknown);assert(OwnLookupCalls()==0);
 pthread_t threads[4];for(unsigned i=0;i<4;i++)assert(!pthread_create(&threads[i],NULL,thread,NULL));for(unsigned i=0;i<4;i++)assert(!pthread_join(threads[i],NULL));puts("SYSTEM_IMPORT_EXPORT_TRIE_CLASSIFICATION_PASS: actual own resolved LA slot; no classifier resolver query; per-thread dlerror/errno preserved");return 0;}
