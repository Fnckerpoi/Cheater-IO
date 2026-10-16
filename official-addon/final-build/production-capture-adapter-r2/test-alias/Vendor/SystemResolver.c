#define _DARWIN_C_SOURCE
#include "SystemResolver.h"
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach/mach.h>
#include <string.h>
#include <stdbool.h>
#include <errno.h>
#include <limits.h>
#define TRIE_MAX 65536u
typedef struct{uint64_t remaining;}ReadBudget;
static bool add(uintptr_t a,uintptr_t b,uintptr_t*out){if(a>UINTPTR_MAX-b)return false;*out=a+b;return true;}
static bool copy(ReadBudget*budget,uintptr_t address,void*out,size_t n){if(!address||!n||n>65536||address>UINTPTR_MAX-n||n>budget->remaining)return false;budget->remaining-=n;vm_size_t count=0;return vm_read_overwrite(mach_task_self(),address,n,(vm_address_t)out,&count)==KERN_SUCCESS&&count==n;}
static bool uuid(ReadBudget*budget,uintptr_t base,unsigned char out[16]){struct mach_header_64 h;if(!copy(budget,base,&h,sizeof h)||h.magic!=MH_MAGIC_64||h.ncmds>512||h.sizeofcmds>65536)return false;uintptr_t cmd;if(!add(base,sizeof h,&cmd))return false;size_t used=0;unsigned found=0;for(unsigned i=0;i<h.ncmds;i++){struct load_command l;if(used>h.sizeofcmds||h.sizeofcmds-used<8||!copy(budget,cmd+used,&l,sizeof l)||l.cmdsize<8||l.cmdsize>h.sizeofcmds-used)return false;if(l.cmd==LC_UUID){struct uuid_command u;if(l.cmdsize!=sizeof u||!copy(budget,cmd+used,&u,sizeof u))return false;memcpy(out,u.uuid,16);found++;}used+=l.cmdsize;}unsigned any=0;for(unsigned i=0;i<16;i++)any|=out[i];return found==1&&any;}
static bool uleb(const unsigned char*b,size_t n,size_t*offset,uint64_t*out){uint64_t v=0;unsigned shift=0;for(unsigned i=0;i<10;i++){if(*offset>=n)return false;unsigned char c=b[(*offset)++];if(shift==63&&(c&0x7e))return false;v|=(uint64_t)(c&127)<<shift;if(!(c&128)){*out=v;return true;}shift+=7;}return false;}
static bool exported(const unsigned char*b,size_t n,uintptr_t base,uintptr_t pointer){const char target[]="_dlsym";size_t node=0,matched=0;for(unsigned depth=0;depth<16;depth++){size_t p=node;uint64_t terminal;if(!uleb(b,n,&p,&terminal)||terminal>n-p)return false;size_t end=p+(size_t)terminal;if(matched==sizeof(target)-1){uint64_t flags,value;if(!terminal||!uleb(b,end,&p,&flags)||flags!=0||!uleb(b,end,&p,&value)||p!=end||value>UINTPTR_MAX-base)return false;return base+(uintptr_t)value==pointer;}p=end;if(p>=n)return false;unsigned children=b[p++],hits=0;size_t next=0,advance=0;for(unsigned i=0;i<children;i++){size_t begin=p;while(p<n&&b[p])p++;if(p==n)return false;size_t length=p-begin;p++;uint64_t child;if(!length||!uleb(b,n,&p,&child)||child>=n)return false;if(length<=sizeof(target)-1-matched&&!memcmp(b+begin,target+matched,length)){hits++;next=(size_t)child;advance=length;}}if(hits!=1||next==node)return false;node=next;matched+=advance;}return false;}
static bool executable(uintptr_t pointer){vm_address_t address=pointer;vm_size_t size=0;natural_t depth=0;for(unsigned level=0;level<16;level++){vm_region_submap_info_data_64_t info;mach_msg_type_number_t count=VM_REGION_SUBMAP_INFO_COUNT_64;kern_return_t code=vm_region_recurse_64(mach_task_self(),&address,&size,&depth,(vm_region_recurse_info_t)&info,&count);if(code!=KERN_SUCCESS||address>pointer||!size||pointer-address>=size)return false;if(info.is_submap){depth++;address=pointer;continue;}return(info.protection&VM_PROT_EXECUTE)&&!(info.protection&VM_PROT_WRITE);}return false;}
static bool path_is(ReadBudget*budget,const char*path,const char*expected){if(!path)return false;for(size_t i=0;i<64;i++){unsigned char c;if(!copy(budget,(uintptr_t)path+i,&c,1))return false;if(c!=(unsigned char)expected[i])return false;if(!c)return true;}return false;}
static bool attest(ReadBudget*budget,uintptr_t imported){
 const char expected_path[]="/usr/lib/system/libdyld.dylib";uintptr_t base=0;intptr_t slide=0;unsigned paths=0;uint32_t count=_dyld_image_count();if(!count||count>4096)return false;
 for(uint32_t j=0;j<count;j++){const char*name=_dyld_get_image_name(j);if(path_is(budget,name,expected_path)){base=(uintptr_t)_dyld_get_image_header(j);slide=_dyld_get_image_vmaddr_slide(j);paths++;}}
 if(paths!=1||!base||!executable(imported))return false;unsigned char expected_uuid[16]={0};if(!uuid(budget,base,expected_uuid))return false;unsigned identities=0;
 for(uint32_t j=0;j<count;j++){unsigned char candidate[16]={0};uintptr_t header=(uintptr_t)_dyld_get_image_header(j);if(!uuid(budget,header,candidate))return false;if(!memcmp(expected_uuid,candidate,16))identities++;}if(identities!=1||_dyld_image_count()!=count)return false;
 struct mach_header_64 h;if(!copy(budget,base,&h,sizeof h))return false;uintptr_t cmds;if(!add(base,sizeof h,&cmds))return false;size_t used=0;uint64_t linkVA=0,linkOff=0,linkSize=0;uint32_t exportOff=0,exportSize=0;unsigned links=0,exports=0;bool inText=false;
 for(unsigned j=0;j<h.ncmds;j++){struct load_command l;if(!copy(budget,cmds+used,&l,sizeof l)||l.cmdsize<8||l.cmdsize>h.sizeofcmds-used)return false;
 if(l.cmd==LC_SEGMENT_64){struct segment_command_64 s;if(l.cmdsize<sizeof s||!copy(budget,cmds+used,&s,sizeof s))return false;if(!memcmp(s.segname,"__LINKEDIT",10)){links++;linkVA=s.vmaddr;linkOff=s.fileoff;linkSize=s.filesize;}if(!memcmp(s.segname,"__TEXT",6)){uintptr_t start;if(slide<0||!add((uintptr_t)s.vmaddr,(uintptr_t)slide,&start)||s.vmsize>UINTPTR_MAX-start)return false;if(imported>=start&&imported-start<s.vmsize&&(s.initprot&VM_PROT_EXECUTE))inText=true;}}
 if(l.cmd==LC_DYLD_EXPORTS_TRIE){struct linkedit_data_command d;if(l.cmdsize!=sizeof d||!copy(budget,cmds+used,&d,sizeof d))return false;exportOff=d.dataoff;exportSize=d.datasize;exports++;}
 if(l.cmd==LC_DYLD_INFO||l.cmd==LC_DYLD_INFO_ONLY){struct dyld_info_command d;if(l.cmdsize!=sizeof d||!copy(budget,cmds+used,&d,sizeof d))return false;if(d.export_size){exportOff=d.export_off;exportSize=d.export_size;exports++;}}
 used+=l.cmdsize;}
 if(!inText||links!=1||exports!=1||!exportSize||exportSize>TRIE_MAX||exportOff<linkOff||exportOff-linkOff>linkSize||exportSize>linkSize-(exportOff-linkOff)||slide<0)return false;
 uintptr_t address;if(!add(linkVA,(uintptr_t)slide,&address)||!add(address,exportOff-linkOff,&address))return false;unsigned char trie[TRIE_MAX];if(!copy(budget,address,trie,exportSize))return false;return _dyld_image_count()==count&&exported(trie,exportSize,base,imported);}
bool TIOConstructorSystemResolver(uintptr_t current){int saved=errno;uintptr_t imported=(uintptr_t)dlsym;ReadBudget budget={UINT64_C(32)*1024*1024};bool ok=current&&current==imported&&attest(&budget,imported);errno=saved;return ok;}
