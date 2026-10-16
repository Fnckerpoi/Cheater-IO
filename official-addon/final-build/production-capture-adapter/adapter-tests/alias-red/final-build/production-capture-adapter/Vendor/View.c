#include "View.h"
#include <mach/mach.h>
#include <mach-o/loader.h>
#include <mach-o/nlist.h>
#include <CommonCrypto/CommonDigest.h>
#include <string.h>
#define COMMAND_MAX 65504
#define IMAGE_MAX (UINT64_C(32)*1024*1024)
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
static bool nameis(const char*a,const char*b){size_t n=strlen(b);return n<16&&!memcmp(a,b,n)&&!a[n];}
static bool copy(TIOConstructorView*v,uintptr_t p,void*d,size_t n){if(!p||!n||n>65536||p>UINTPTR_MAX-n||n>v->budget)return false;v->budget-=n;v->bytes+=n;vm_size_t got=0;return vm_read_overwrite(mach_task_self(),p,n,(vm_address_t)d,&got)==KERN_SUCCESS&&got==n;}
static bool slid(TIOConstructorView*v,uint64_t address,uintptr_t*out){if(address>UINTPTR_MAX)return false;if(v->slide>=0){if(address>UINTPTR_MAX-(uintptr_t)v->slide)return false;*out=(uintptr_t)address+(uintptr_t)v->slide;}else{uint64_t delta=(uint64_t)(-(v->slide+1))+1;if(address<delta)return false;*out=(uintptr_t)(address-delta);}return true;}
static bool linkcopy(TIOConstructorView*v,uint64_t linkva,uint64_t linkoff,uint64_t linksize,uint64_t off,void*d,size_t n){if(off<linkoff||off-linkoff>linksize||n>linksize-(off-linkoff)||linkva>UINT64_MAX-(off-linkoff))return false;uintptr_t address;if(!slid(v,linkva+off-linkoff,&address))return false;return copy(v,address,d,n);}
static bool hash(TIOConstructorView*v,uint64_t va,uint64_t n,const unsigned char expected[32]){if(!n||n>IMAGE_MAX||va>UINT64_MAX-n)return false;uintptr_t p;if(!slid(v,va,&p)||p>UINTPTR_MAX-n)return false;unsigned char chunk[16384],digest[32];CC_SHA256_CTX context;if(!CC_SHA256_Init(&context))return false;while(n){size_t amount=n>sizeof chunk?sizeof chunk:(size_t)n;if(!copy(v,p,chunk,amount)||!CC_SHA256_Update(&context,chunk,(CC_LONG)amount)){memset(chunk,0,sizeof chunk);return false;}p+=amount;n-=amount;}CC_SHA256_Final(digest,&context);memset(chunk,0,sizeof chunk);return!memcmp(digest,expected,32);}
bool TIOConstructorVerify(TIOConstructorView*v,const TIOFixedImagePolicy*p){
 if(!v||!p||!v->header||!v->budget||v->budget>64*UINT64_C(1024)*1024)return false;
 struct mach_header_64 h;if(!copy(v,v->header,&h,sizeof h)||h.magic!=MH_MAGIC_64||(uint32_t)h.cputype!=p->cpu||h.filetype!=p->filetype||h.cpusubtype!=(p->cpu==CPU_TYPE_X86_64?3:0)||!h.ncmds||h.ncmds>512||h.sizeofcmds<8||h.sizeofcmds>COMMAND_MAX||h.ncmds>h.sizeofcmds/8||v->header>UINTPTR_MAX-sizeof h-h.sizeofcmds)return false;
 unsigned char commands[COMMAND_MAX];if(!copy(v,v->header+sizeof h,commands,h.sizeofcmds))return false;
 struct{uint64_t va,size,off,filesize;}segments[32];unsigned ns=0,uuids=0,texts=0,infos=0,slots=0;uint64_t text_va=0,text_size=0,info_va=0,info_size=0;size_t offset=0;uint64_t linkva=0,linkoff=0,linksize=0;unsigned links=0,syms=0,dyses=0,dependencies=0;struct symtab_command sym={0};struct dysymtab_command dys={0};struct section_64 lazy[16];unsigned nlazy=0;
 for(unsigned i=0;i<h.ncmds;i++){
  if(offset>h.sizeofcmds||h.sizeofcmds-offset<8)return false;struct load_command l;memcpy(&l,commands+offset,8);if(l.cmdsize<8||l.cmdsize%8||l.cmdsize>h.sizeofcmds-offset)return false;
  if(l.cmd==LC_UUID){struct uuid_command u;if(l.cmdsize!=sizeof u||++uuids!=1)return false;memcpy(&u,commands+offset,sizeof u);if(memcmp(u.uuid,p->uuid,16))return false;}
  if(l.cmd==LC_SEGMENT_64){struct segment_command_64 s;if(l.cmdsize<sizeof s||ns==32)return false;memcpy(&s,commands+offset,sizeof s);if(s.nsects>(l.cmdsize-sizeof s)/sizeof(struct section_64)||l.cmdsize!=sizeof s+s.nsects*sizeof(struct section_64)||s.vmaddr>UINT64_MAX-s.vmsize||s.filesize>s.vmsize||s.fileoff>UINT64_MAX-s.filesize)return false;
   for(unsigned k=0;k<ns;k++)if(s.vmsize&&segments[k].size&&s.vmaddr<segments[k].va+segments[k].size&&segments[k].va<s.vmaddr+s.vmsize)return false;
   segments[ns].va=s.vmaddr;segments[ns].size=s.vmsize;segments[ns].off=s.fileoff;segments[ns].filesize=s.filesize;ns++;
   if(nameis(s.segname,"__LINKEDIT")){if(++links!=1)return false;linkva=s.vmaddr;linkoff=s.fileoff;linksize=s.filesize;}
   if(p->slot_va&&!memcmp(s.segname,"__AUTH",6))return false;
   for(unsigned j=0;j<s.nsects;j++){struct section_64 t;memcpy(&t,commands+offset+sizeof s+j*sizeof t,sizeof t);if(memcmp(t.segname,s.segname,16)||t.addr<s.vmaddr||t.addr-s.vmaddr>s.vmsize||t.size>s.vmsize-(t.addr-s.vmaddr))return false;
    if(nameis(s.segname,"__TEXT")&&nameis(t.sectname,"__text")){if(++texts!=1||!(s.initprot&VM_PROT_EXECUTE)||!(t.flags&S_ATTR_PURE_INSTRUCTIONS)||t.offset<s.fileoff||t.offset-s.fileoff>s.filesize||t.size>s.filesize-(t.offset-s.fileoff)||t.addr-s.vmaddr!=(uint64_t)t.offset-s.fileoff)return false;text_va=t.addr;text_size=t.size;}
    if(nameis(s.segname,"__TEXT")&&nameis(t.sectname,"__info_plist")){if(++infos!=1||t.size>65536)return false;info_va=t.addr;info_size=t.size;}
    if(p->slot_va&&(t.flags&SECTION_TYPE)==S_LAZY_SYMBOL_POINTERS){if(nlazy==16||!nameis(s.segname,"__DATA")||!nameis(t.sectname,"__la_symbol_ptr")||s.initprot!=3||s.maxprot!=3)return false;lazy[nlazy++]=t;}
    if(p->slot_va&&p->slot_va>=t.addr&&p->slot_va-t.addr<t.size){if(nameis(s.segname,"__DATA")&&nameis(t.sectname,"__la_symbol_ptr")&&(t.flags&SECTION_TYPE)==S_LAZY_SYMBOL_POINTERS&&t.size%8==0&&t.size-(p->slot_va-t.addr)>=8&&!(p->slot_va%8)&&s.initprot==3&&s.maxprot==3)slots++;}
   }
  }
  if(p->required_dependency&&*p->required_dependency&&l.cmd==LC_LOAD_DYLIB){struct dylib_command d;if(l.cmdsize<sizeof d)return false;memcpy(&d,commands+offset,sizeof d);if(d.dylib.name.offset>=l.cmdsize)return false;const char*name=(const void*)(commands+offset+d.dylib.name.offset);if(!memchr(name,0,l.cmdsize-d.dylib.name.offset))return false;if(!strcmp(name,p->required_dependency))dependencies++;}
  if(l.cmd==LC_SYMTAB){if(++syms!=1||l.cmdsize!=sizeof sym)return false;memcpy(&sym,commands+offset,sizeof sym);}
  if(l.cmd==LC_DYSYMTAB){if(++dyses!=1||l.cmdsize!=sizeof dys)return false;memcpy(&dys,commands+offset,sizeof dys);}
  if(p->slot_va&&l.cmd==LC_DYLD_CHAINED_FIXUPS)return false;
  offset+=l.cmdsize;
 }
 if(p->required_dependency&&*p->required_dependency&&dependencies!=1)return false;
 if(offset!=h.sizeofcmds||uuids!=1||texts!=1||text_va!=p->text_va||text_size!=p->text_size||!hash(v,text_va,text_size,p->text_sha))return false;
 if(p->info_size){if(infos!=1||info_va!=p->info_va||info_size!=p->info_size||!hash(v,info_va,info_size,p->info_sha))return false;v->info_verified=true;}
 if(p->slot_va){
  if(slots!=1||links!=1||syms!=1||dyses!=1||!sym.nsyms||sym.nsyms>262144||!sym.strsize||sym.strsize>16*1024*1024||!dys.nindirectsyms||dys.nindirectsyms>262144)return false;
  unsigned found=0;
  for(unsigned i=0;i<nlazy;i++){struct section_64*t=&lazy[i];if(t->size%8||t->size/8>4096||t->reserved1>dys.nindirectsyms||t->size/8>dys.nindirectsyms-t->reserved1)return false;
   for(uint64_t j=0;j<t->size/8;j++){uint32_t index=0;if(!linkcopy(v,linkva,linkoff,linksize,(uint64_t)dys.indirectsymoff+4*((uint64_t)t->reserved1+j),&index,4))return false;if(index&(INDIRECT_SYMBOL_ABS|INDIRECT_SYMBOL_LOCAL))continue;if(index>=sym.nsyms)return false;struct nlist_64 symbol;if(!linkcopy(v,linkva,linkoff,linksize,(uint64_t)sym.symoff+sizeof symbol*(uint64_t)index,&symbol,sizeof symbol)||symbol.n_un.n_strx>=sym.strsize)return false;char name[7];if(sym.strsize-symbol.n_un.n_strx<sizeof name||!linkcopy(v,linkva,linkoff,linksize,(uint64_t)sym.stroff+symbol.n_un.n_strx,name,sizeof name))return false;if(memcmp(name,"_dlsym",7))continue;if((symbol.n_type&N_TYPE)!=N_UNDF||!(symbol.n_type&N_EXT)||(symbol.n_type&N_STAB)||GET_LIBRARY_ORDINAL(symbol.n_desc)!=1||t->addr+8*j!=p->slot_va)return false;found++;}
  }
  if(found!=1||!slid(v,p->slot_va,&v->slot)||!slid(v,p->lazy_va,&v->lazy))return false;v->slot_metadata_verified=true;
 }
 return true;
}
