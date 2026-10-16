#include <dlfcn.h>
#include <mach-o/getsect.h>
#include <mach-o/dyld.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
int main(int argc,char**argv){if(argc!=2)return 2;void*h=dlopen(argv[1],RTLD_LAZY|RTLD_LOCAL);if(!h){puts("DLOPEN_FAILED");return 3;}for(unsigned i=0;i<_dyld_image_count();i++){const char*p=_dyld_get_image_name(i);if(p&&!strcmp(p,argv[1])){unsigned long size=0;const uint64_t*s=(const void*)getsectiondata((const struct mach_header_64*)_dyld_get_image_header(i),"__DATA","__la_symbol_ptr",&size);printf("SLIDE=%llx SIZE=%lu",(unsigned long long)_dyld_get_image_vmaddr_slide(i),size);for(unsigned long j=0;j<size/8;j++)printf(" SLOT[%lu]=%llx",j,(unsigned long long)s[j]);printf(" SYSTEM=%p\n",(void*)dlsym);}}return 0;}
