#define _DARWIN_C_SOURCE
#include "ImageAttestation.h"
#include <CommonCrypto/CommonDigest.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach-o/nlist.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#ifdef TIO_IMAGE_ATTESTATION_TESTING
typedef TIOImageTestPolicy Policy;
#else
typedef struct { uint32_t cpu; unsigned char uuid[16]; uint64_t text_va,text_size; unsigned char text_sha[32]; uint64_t entry_va; unsigned char entry_sha[32]; const char *relative_path; } Policy;
#endif
#include "generated/ImagePolicy.inc"
#define MAX_COMMAND_BYTES 65504u
#define MAX_SEGMENTS 32u
#define MAX_POINTER_SECTIONS 16u
#define MAX_METADATA_ITEMS 262144u
#define MAX_BYTES (UINT64_C(64)*1024*1024)
typedef struct {uint64_t va,size,off,filesize;int32_t initprot,maxprot;char name[16];} Segment;
typedef struct {struct section_64 s; unsigned seg;} PointerSection;
typedef struct {
 uintptr_t header; intptr_t slide; bool file; const unsigned char *bytes; size_t length;
 TIOImageCopy copy;void *user;TIOImageBudget *budget;uint64_t used;
 Segment segments[MAX_SEGMENTS];unsigned nseg;
} View;
static bool range64(uint64_t a,uint64_t n){return n && a<=UINT64_MAX-n;}
static bool addptr(uintptr_t a,uint64_t b,uintptr_t *o){if(b>UINTPTR_MAX-a)return false;*o=a+(uintptr_t)b;return true;}
static bool slid(uint64_t va,intptr_t slide,uintptr_t *o){
 if(va>UINTPTR_MAX)return false;
 if(slide>=0)return addptr((uintptr_t)va,(uint64_t)slide,o);
 uint64_t n=(uint64_t)(-(slide+1))+1;if(n>va)return false;*o=(uintptr_t)(va-n);return true;
}
static bool nameis(const char n[16],const char *s){size_t l=strlen(s);return l<16&&!memcmp(n,s,l)&&n[l]==0;}
static bool overlaps64(uint64_t a,uint64_t n,uint64_t b,uint64_t m){return n&&m&&a<b+m&&b<a+n;}
static bool relative_ok(const char *p){
 if(!p||!*p||*p=='/'||strlen(p)>=PATH_MAX)return false;
 const char *s=p;while(*s){const char *e=strchr(s,'/');size_t n=e?(size_t)(e-s):strlen(s);if(!n||(n==1&&s[0]=='.')||(n==2&&!memcmp(s,"..",2)))return false;for(size_t i=0;i<n;i++)if(s[i]=='\\'||(unsigned char)s[i]<32)return false;if(!e)break;s=e+1;if(!*s)return false;}return true;
}
static TIOImageStatus rawread(View *v,uintptr_t address,void *out,size_t n){
 if(!n||n>65536||n>v->budget->bytes_remaining||address>UINTPTR_MAX-n)return TIOImageBudgetExceeded;
 v->budget->bytes_remaining-=n;v->used+=n;size_t copied=0;
 if(v->file){if(address>v->length||n>v->length-address)return TIOImageCopyFailed;memcpy(out,v->bytes+address,n);return TIOImageOK;}
 if(!v->copy(v->user,address,n,out,&copied)||copied!=n){memset(out,0,n);return TIOImageCopyFailed;}return TIOImageOK;
}
static TIOImageStatus headerread(View *v,uint64_t off,void *out,size_t n){uintptr_t p;if(v->file)p=(uintptr_t)off;else if(!addptr(v->header,off,&p))return TIOImageMalformed;return rawread(v,p,out,n);}
static TIOImageStatus varead(View *v,uint64_t va,void *out,size_t n){
 if(!range64(va,n))return TIOImageMalformed;
 unsigned matches=0;uintptr_t p=0;
 for(unsigned i=0;i<v->nseg;i++){Segment *s=&v->segments[i];uint64_t span=v->file?s->filesize:s->size;if(va>=s->va&&va-s->va<=span&&n<=span-(va-s->va)){
 if(v->file){uint64_t delta=va-s->va;if(s->off>UINTPTR_MAX-delta)return TIOImageMalformed;p=(uintptr_t)(s->off+delta);}else if(!slid(va,v->slide,&p))return TIOImageMalformed;matches++;}}
 if(matches!=1)return TIOImageMalformed;return rawread(v,p,out,n);
}
static TIOImageStatus fileread(View *v,uint64_t off,void *out,size_t n){
 unsigned matches=0;uint64_t va=0;for(unsigned i=0;i<v->nseg;i++){Segment *s=&v->segments[i];if(nameis(s->name,"__LINKEDIT")&&off>=s->off&&off-s->off<=s->filesize&&n<=s->filesize-(off-s->off)){if(s->va>UINT64_MAX-(off-s->off))return TIOImageMalformed;va=s->va+off-s->off;matches++;}}
 return matches==1?varead(v,va,out,n):TIOImageMalformed;
}
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
static TIOImageStatus hashtext(View *v,uint64_t va,uint64_t length,const unsigned char wanted[32]){
 unsigned char buffer[65536],digest[32];CC_SHA256_CTX ctx;CC_SHA256_Init(&ctx);
 while(length){size_t n=length>sizeof buffer?sizeof buffer:(size_t)length;TIOImageStatus s=varead(v,va,buffer,n);if(s!=TIOImageOK){memset(buffer,0,sizeof buffer);return s;}CC_SHA256_Update(&ctx,buffer,(CC_LONG)n);va+=n;length-=n;}
 CC_SHA256_Final(digest,&ctx);memset(buffer,0,sizeof buffer);return !memcmp(digest,wanted,32)?TIOImageOK:TIOImageIdentityMismatch;
}
#pragma clang diagnostic pop
void TIOImageBudgetInit(TIOImageBudget *b){if(b){b->bytes_remaining=MAX_BYTES;b->images_remaining=3;}}
static const Policy *policy_for(TIOImageRole role){return role==TIOImageFlutter?&kFlutterPolicy:role==TIOImageIsar?&kIsarPolicy:NULL;}
static TIOImageStatus slot_decode(View *v,const PointerSection *sections,unsigned count,const struct symtab_command *sym,const struct dysymtab_command *dys,uintptr_t *address){
 if(!sym->nsyms||sym->nsyms>MAX_METADATA_ITEMS||!sym->strsize||sym->strsize>16*1024*1024||!dys->nindirectsyms||dys->nindirectsyms>MAX_METADATA_ITEMS)return TIOImageUnsupported;
 unsigned found=0;
 for(unsigned k=0;k<count;k++){const struct section_64 *s=&sections[k].s;if((s->size%8)||s->size/8>4096||s->reserved1>dys->nindirectsyms||s->size/8>dys->nindirectsyms-s->reserved1)return TIOImageMalformed;
 for(uint64_t j=0;j<s->size/8;j++){uint32_t idx=0;TIOImageStatus st=fileread(v,(uint64_t)dys->indirectsymoff+4*((uint64_t)s->reserved1+j),&idx,4);if(st)return st;if(idx&(INDIRECT_SYMBOL_LOCAL|INDIRECT_SYMBOL_ABS))continue;if(idx>=sym->nsyms)return TIOImageMalformed;struct nlist_64 n;st=fileread(v,(uint64_t)sym->symoff+sizeof n*(uint64_t)idx,&n,sizeof n);if(st)return st;if(n.n_un.n_strx>=sym->strsize)return TIOImageMalformed;size_t len=sym->strsize-n.n_un.n_strx;len=len>8?8:len;char name[8]={0};st=fileread(v,(uint64_t)sym->stroff+n.n_un.n_strx,name,len);if(st)return st;if(len<7||memcmp(name,"_dlsym",7))continue;
 const Segment *seg=&v->segments[sections[k].seg];uint64_t slot=s->addr+8*j;
 if((n.n_type&N_TYPE)!=N_UNDF||!(n.n_type&N_EXT)||(n.n_type&N_STAB)||GET_LIBRARY_ORDINAL(n.n_desc)!=1||!nameis(seg->name,"__DATA")||!nameis(s->sectname,"__la_symbol_ptr")||(s->flags&SECTION_TYPE)!=S_LAZY_SYMBOL_POINTERS||seg->initprot!=3||seg->maxprot!=3||slot!=UINT64_C(0x8341d8)||slot%8||!slid(slot,v->slide,address))return TIOImageIdentityMismatch;found++;}}
 return found==1?TIOImageOK:TIOImageUnsupported;
}
static TIOImageStatus verify(const Policy *p,View *v,TIOImageProof *out,bool flutter){
 if(!out)return TIOImageBadArgument;memset(out,0,sizeof *out);
 if(!p||!v->budget||!v->budget->images_remaining||v->budget->images_remaining>3||v->budget->bytes_remaining>MAX_BYTES||(!v->file&&!v->copy))return TIOImageBadArgument;v->budget->images_remaining--;
 struct mach_header_64 h;TIOImageStatus result=headerread(v,0,&h,sizeof h);if(result)return result;
 if(h.magic!=MH_MAGIC_64||(uint32_t)h.cputype!=p->cpu||h.cpusubtype!=(p->cpu==0x1000007U?3:0)||h.filetype!=MH_DYLIB||h.ncmds==0||h.ncmds>1024||h.sizeofcmds<8||h.sizeofcmds>MAX_COMMAND_BYTES||h.ncmds>h.sizeofcmds/8)return TIOImageMalformed;
 unsigned char *commands=malloc(h.sizeofcmds);if(!commands)return TIOImageCopyFailed;result=headerread(v,sizeof h,commands,h.sizeofcmds);if(result){free(commands);return result;}
 size_t off=0;unsigned uuids=0,texts=0,syms=0,dyss=0;uint64_t text_va=0,text_size=0,text_off=0;PointerSection pointers[MAX_POINTER_SECTIONS];unsigned npointer=0;struct symtab_command sym={0};struct dysymtab_command dys={0};
 for(uint32_t i=0;i<h.ncmds;i++){
 if(h.sizeofcmds-off<8){result=TIOImageMalformed;goto done;}struct load_command lc;memcpy(&lc,commands+off,8);if(lc.cmdsize<8||lc.cmdsize%8||lc.cmdsize>h.sizeofcmds-off){result=TIOImageMalformed;goto done;}
 if(lc.cmd==LC_UUID){struct uuid_command u;if(lc.cmdsize!=sizeof u){result=TIOImageMalformed;goto done;}memcpy(&u,commands+off,sizeof u);if(++uuids!=1||memcmp(u.uuid,p->uuid,16)){result=TIOImageIdentityMismatch;goto done;}}
 else if(lc.cmd==LC_SEGMENT_64){struct segment_command_64 s;if(lc.cmdsize<sizeof s||v->nseg==MAX_SEGMENTS){result=TIOImageMalformed;goto done;}memcpy(&s,commands+off,sizeof s);if(s.nsects>(lc.cmdsize-sizeof s)/sizeof(struct section_64)||lc.cmdsize!=sizeof s+s.nsects*sizeof(struct section_64)||!range64(s.vmaddr,s.vmsize)||s.filesize>s.vmsize||(s.filesize&&!range64(s.fileoff,s.filesize))){result=TIOImageMalformed;goto done;}
 for(unsigned j=0;j<v->nseg;j++)if(overlaps64(s.vmaddr,s.vmsize,v->segments[j].va,v->segments[j].size)||(s.filesize&&v->segments[j].filesize&&overlaps64(s.fileoff,s.filesize,v->segments[j].off,v->segments[j].filesize))){result=TIOImageMalformed;goto done;}
 unsigned si=v->nseg++;Segment *seg=&v->segments[si];*seg=(Segment){s.vmaddr,s.vmsize,s.fileoff,s.filesize,s.initprot,s.maxprot,{0}};memcpy(seg->name,s.segname,16);
 if(flutter&&(!memcmp(s.segname,"__AUTH",6))){result=TIOImageUnsupported;goto done;}
 for(uint32_t j=0;j<s.nsects;j++){struct section_64 sec;memcpy(&sec,commands+off+sizeof s+j*sizeof sec,sizeof sec);if(memcmp(sec.segname,s.segname,16)||!range64(sec.addr,sec.size)||sec.addr<s.vmaddr||sec.addr-s.vmaddr>s.vmsize||sec.size>s.vmsize-(sec.addr-s.vmaddr)){result=TIOImageMalformed;goto done;}
 if(nameis(s.segname,"__TEXT")&&nameis(sec.sectname,"__text")){if(++texts!=1||!(s.initprot&4)||!(sec.flags&S_ATTR_PURE_INSTRUCTIONS)||sec.offset<s.fileoff||(uint64_t)sec.offset-s.fileoff>s.filesize||sec.size>s.filesize-(sec.offset-s.fileoff)||sec.addr-s.vmaddr!=(uint64_t)sec.offset-s.fileoff){result=TIOImageMalformed;goto done;}text_va=sec.addr;text_size=sec.size;text_off=sec.offset;}
 unsigned type=sec.flags&SECTION_TYPE;if(type==S_LAZY_SYMBOL_POINTERS||type==S_NON_LAZY_SYMBOL_POINTERS){if(npointer==MAX_POINTER_SECTIONS){result=TIOImageUnsupported;goto done;}pointers[npointer++]=(PointerSection){sec,si};}}}
 else if(lc.cmd==LC_SYMTAB){if(lc.cmdsize!=sizeof sym||++syms!=1){result=TIOImageMalformed;goto done;}memcpy(&sym,commands+off,sizeof sym);}
 else if(lc.cmd==LC_DYSYMTAB){if(lc.cmdsize!=sizeof dys||++dyss!=1){result=TIOImageMalformed;goto done;}memcpy(&dys,commands+off,sizeof dys);}
 else if(flutter&&lc.cmd==LC_DYLD_CHAINED_FIXUPS){result=TIOImageUnsupported;goto done;}
 off+=lc.cmdsize;}
 if(off!=h.sizeofcmds||uuids!=1||texts!=1||text_va!=p->text_va||text_size!=p->text_size){result=TIOImageIdentityMismatch;goto done;}
 bool header_bound=false;for(unsigned i=0;i<v->nseg;i++){Segment *s=&v->segments[i];if(nameis(s->name,"__TEXT")&&s->off==0&&s->filesize>=sizeof h+h.sizeofcmds){uintptr_t hp;if(!slid(s->va,v->slide,&hp)||(!v->file&&hp!=v->header)){result=TIOImageMalformed;goto done;}header_bound=true;}}if(!header_bound){result=TIOImageMalformed;goto done;}
 (void)text_off;result=hashtext(v,text_va,text_size,p->text_sha);if(result)goto done;
 if(p->entry_va){if(p->entry_va<text_va||p->entry_va-text_va>text_size||64>text_size-(p->entry_va-text_va)){result=TIOImageMalformed;goto done;}result=hashtext(v,p->entry_va,64,p->entry_sha);if(result)goto done;if(!v->file&&!slid(p->entry_va,v->slide,&out->open_entry)){result=TIOImageMalformed;goto done;}}
 if(flutter){result=slot_decode(v,pointers,npointer,&sym,&dys,&out->slot_address);if(result)goto done;out->slot_metadata_verified=true;}
 {unsigned char recheck[65536];result=headerread(v,0,recheck,sizeof h);if(result)goto done;if(memcmp(recheck,&h,sizeof h)){result=TIOImageIdentityMismatch;goto done;}result=headerread(v,sizeof h,recheck,h.sizeofcmds);if(result)goto done;if(memcmp(recheck,commands,h.sizeofcmds)){result=TIOImageIdentityMismatch;goto done;}}
 out->content_verified=true;
 done:free(commands);out->bytes_read=v->used;if(result){uint64_t n=out->bytes_read;memset(out,0,sizeof *out);out->bytes_read=n;}return result;
}
TIOImageStatus TIOImageAttestStatic(TIOImageRole role,const void *bytes,size_t length,TIOImageBudget *b,TIOImageProof *o){if(o)memset(o,0,sizeof *o);const Policy *p=policy_for(role);if(!bytes||!length||!p)return TIOImageBadArgument;View v={.file=true,.bytes=bytes,.length=length,.budget=b};TIOImageStatus s=verify(p,&v,o,role==TIOImageFlutter);if(o)o->slot_address=0;return s;}
static TIOImageStatus loaded(const Policy *p,const void *header,const char *root,TIOImageCopy copy,void *user,TIOImageBudget *b,TIOImageProof *o,bool flutter){
 if(o)memset(o,0,sizeof *o);if(!p||!header||!root||!copy||!o||!b||!relative_ok(p->relative_path))return TIOImageBadArgument;
 char canonical[PATH_MAX],expected[PATH_MAX];size_t len=strlen(root);if(!len||len>=PATH_MAX||root[0]!='/'||len<4||strcmp(root+len-4,".app")||!realpath(root,canonical)||strcmp(root,canonical)||len+1+strlen(p->relative_path)>=PATH_MAX)return TIOImagePathRejected;
 memcpy(expected,root,len);expected[len]='/';strcpy(expected+len+1,p->relative_path);const char *leaf=strrchr(p->relative_path,'/');leaf=leaf?leaf+1:p->relative_path;unsigned found=0,matching=0;intptr_t slide=0;uint32_t count=_dyld_image_count();if(count>4096)return TIOImageUnsupported;
 for(uint32_t i=0;i<count;i++){const char *path=_dyld_get_image_name(i);if(!path)continue;const char *name=strrchr(path,'/');name=name?name+1:path;if(strcmp(name,leaf))continue;matching++;if(_dyld_get_image_header(i)==header){if(!realpath(path,canonical)||strcmp(path,canonical)||strcmp(path,expected))return TIOImagePathRejected;found++;slide=_dyld_get_image_vmaddr_slide(i);}}
 if(matching!=1)return matching?TIOImageDuplicate:TIOImageNotLoaded;if(found!=1)return TIOImageNotLoaded;View v={.header=(uintptr_t)header,.slide=slide,.copy=copy,.user=user,.budget=b};TIOImageStatus s=verify(p,&v,o,flutter);if(s==TIOImageOK){if(_dyld_image_count()!=count)return (memset(o,0,sizeof *o),TIOImageDuplicate);o->loaded_identity_verified=true;}return s;
}
TIOImageStatus TIOImageAttestLoaded(TIOImageRole role,const void *h,const char *root,TIOImageCopy copy,void *user,TIOImageBudget *b,TIOImageProof *o){return loaded(policy_for(role),h,root,copy,user,b,o,role==TIOImageFlutter);}
#ifdef TIO_IMAGE_ATTESTATION_TESTING
TIOImageStatus TIOImageTestView(const TIOImageTestPolicy *p,uintptr_t h,intptr_t slide,const char *path,unsigned matching,TIOImageCopy copy,void *user,TIOImageBudget *b,TIOImageProof *o){if(o)memset(o,0,sizeof *o);if(!p||!path||!relative_ok(path)||strcmp(path,p->relative_path))return TIOImagePathRejected;if(matching!=1)return TIOImageDuplicate;View v={.header=h,.slide=slide,.copy=copy,.user=user,.budget=b};return verify(p,&v,o,false);}
TIOImageStatus TIOImageTestLoaded(const TIOImageTestPolicy *p,const void *h,const char *root,TIOImageCopy copy,void *user,TIOImageBudget *b,TIOImageProof *o){return loaded(p,h,root,copy,user,b,o,false);}
#endif
