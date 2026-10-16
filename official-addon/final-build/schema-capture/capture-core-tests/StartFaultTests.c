#define _DARWIN_C_SOURCE
#include <sys/stat.h>
#include <stdbool.h>
static int FixtureStat(int,struct stat*);
#define fstat(...) FixtureStat(__VA_ARGS__)
#include "../Capture.c"
#undef fstat
#include <assert.h>
static unsigned Fault,StatCalls;
static int FixtureStat(int fd,struct stat*st){int result=fstat(fd,st);StatCalls++;if(!result&&Fault==1)st->st_uid=geteuid()+1;if(!result&&Fault==2&&StatCalls==2)st->st_ino++;return result;}
static uint8_t original(void**o,uint32_t i,void*n,void*p,bool q,void*s,uint32_t m,void*k,uint32_t a,uint32_t b,float r){(void)o;(void)i;(void)n;(void)p;(void)q;(void)s;(void)m;(void)k;(void)a;(void)b;(void)r;return 0;}
int main(int argc,char**argv){assert(argc==2);int fd=open(argv[1],O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);assert(fd>=0);assert(TIOCaptureBindOriginalInternal(original));Fault=1;StatCalls=0;assert(!TIOCaptureStartInternal(fd));Fault=2;StatCalls=0;assert(!TIOCaptureStartInternal(fd));Fault=0;StatCalls=0;assert(TIOCaptureStartInternal(fd));assert(TIOCaptureGetState()==TIOCaptureWaiting);close(fd);puts("SYSCALL_FAULT_WRONG_OWNER_AND_INODE_MISMATCH_REJECT_PASS");return 0;}
