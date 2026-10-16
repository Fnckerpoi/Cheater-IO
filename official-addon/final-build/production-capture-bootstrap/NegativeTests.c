#include "View.h"
#include "SystemResolver.h"
#include <mach-o/dyld.h>
#include <dlfcn.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
int main(void){uintptr_t header=0;intptr_t slide=0;for(unsigned i=0;i<_dyld_image_count();i++){const char*p=_dyld_get_image_name(i);if(p&&strstr(p,"/OwnImage.dylib")){assert(!header);header=(uintptr_t)_dyld_get_image_header(i);slide=_dyld_get_image_vmaddr_slide(i);}}assert(header);TIOConstructorView v={.header=header,.slide=slide,.budget=64*UINT64_C(1024)*1024};assert(TIOConstructorVerify(&v,&FlutterPolicy)&&v.slot_metadata_verified);TIOFixedImagePolicy wrong=FlutterPolicy;wrong.uuid[0]^=1;v=(TIOConstructorView){.header=header,.slide=slide,.budget=64*UINT64_C(1024)*1024};assert(!TIOConstructorVerify(&v,&wrong));wrong=FlutterPolicy;wrong.text_sha[0]^=1;v=(TIOConstructorView){.header=header,.slide=slide,.budget=64*UINT64_C(1024)*1024};assert(!TIOConstructorVerify(&v,&wrong));v=(TIOConstructorView){.header=header,.slide=slide,.budget=1};assert(!TIOConstructorVerify(&v,&FlutterPolicy));v=(TIOConstructorView){.header=1,.slide=0,.budget=64*UINT64_C(1024)*1024};assert(!TIOConstructorVerify(&v,&FlutterPolicy));v=(TIOConstructorView){.header=header,.slide=INTPTR_MIN,.budget=64*UINT64_C(1024)*1024};assert(!TIOConstructorVerify(&v,&FlutterPolicy));errno=E2BIG;assert(TIOConstructorSystemResolver((uintptr_t)dlsym)&&errno==E2BIG);assert(!TIOConstructorSystemResolver((uintptr_t)dlsym+1));assert(!TIOConstructorSystemResolver((uintptr_t)dladdr));assert(!TIOConstructorSystemResolver(1));puts("STACK_CTOR_POLICY_NEGATIVE_AND_EXACT_EXPORT_PROOF_PASS");return 0;}
