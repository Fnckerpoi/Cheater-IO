#define _DARWIN_C_SOURCE
#include "Capture.h"
#include "CaptureInternal.h"
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
static unsigned Calls;
static void *ExpectedKey,*ExpectedSchema;
static uint8_t original(void**out,uint32_t id,void*n,void*p,bool sql,void*s,uint32_t max,void*k,uint32_t cf,uint32_t cb,float ratio){Calls++;uint32_t bits;memcpy(&bits,&ratio,4);assert(id==201&&n==(void*)1&&p==(void*)2&&sql&&s==ExpectedSchema&&k==ExpectedKey&&max==65536&&cf==3&&cb==4&&bits==0x80000000&&errno==E2BIG);*out=(void*)0x105;errno=EDOM;return 0x7e;}
static void call(void *schema,void *key){ExpectedKey=key;ExpectedSchema=schema;void*out=NULL;uint32_t bits=0x80000000;float f;memcpy(&f,&bits,4);errno=E2BIG;unsigned before=Calls;assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,schema,65536,key,3,4,f)==0x7e);assert(Calls==before+1&&out==(void*)0x105&&errno==EDOM);}
int main(int argc,char**argv){assert(argc==3);int fd=open(argv[1],O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);assert(fd>=0);assert(TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);assert(!TIOCaptureStartInternal(fd));assert(TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);assert(TIOCaptureBindOriginalInternal(original));call((void*)1,(void*)2);assert(TIOCaptureGetState()==TIOCaptureWaiting);
 assert(!TIOCaptureStartInternal(-1));
 char regular[4096];snprintf(regular,sizeof(regular),"%s/public-file",argv[1]);int filefd=open(regular,O_RDWR|O_CREAT|O_EXCL|O_CLOEXEC,0600);assert(filefd>=0&&!TIOCaptureStartInternal(filefd));close(filefd);assert(!unlink(regular));
 char link[4096];snprintf(link,sizeof(link),"%s/public-link",argv[1]);assert(!symlink(argv[1],link));int linkfd=open(link,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);assert(linkfd<0&&!TIOCaptureStartInternal(linkfd));assert(!unlink(link));
 int plain=open(argv[1],O_RDONLY|O_DIRECTORY);assert(plain>=0);assert(!TIOCaptureStartInternal(plain));close(plain);
 if(!strcmp(argv[2],"unsafe-mode")){assert(!TIOCaptureStartInternal(fd));close(fd);puts("START_UNSAFE_MODE_REJECT_PASS");return 0;}
 assert(TIOCaptureStartInternal(fd));assert(!TIOCaptureStartInternal(fd));close(fd);
 char file[4096];snprintf(file,sizeof(file),"%s/captured_schema_projection.json",argv[1]);assert(access(file,F_OK)!=0&&TIOCaptureGetState()==TIOCaptureWaiting);
 const char*json="[{\"name\":\"PublicActivationFixture\",\"properties\":[]}]";uint64_t schema[3]={0,(uintptr_t)json,strlen(json)};call(schema,(void*)0x555);assert(access(file,F_OK)!=0&&TIOCaptureGetState()==TIOCaptureKeyRejected);call(schema,(void*)0x555);assert(access(file,F_OK)!=0);
 call(schema,NULL);assert(TIOCaptureGetState()==TIOCaptureWritten);struct stat st;assert(!lstat(file,&st)&&S_ISREG(st.st_mode)&&(st.st_mode&07777)==0600);int readfd=open(file,O_RDONLY|O_NOFOLLOW);assert(readfd>=0);char bytes[512]={0};ssize_t length=read(readfd,bytes,sizeof(bytes)-1);close(readfd);assert(length>0&&strstr(bytes,"PublicActivationFixture"));call((void*)1,NULL);assert(!lstat(file,&st)&&st.st_size==length);assert(!TIOCaptureStartInternal(-1));puts("HIDDEN_START_WAITING_NO_FILE_TYPED_ORIGINAL_ONCE_ONESHOT_PASS");return 0;}
