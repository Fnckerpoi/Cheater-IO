#include "Capture.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>
static unsigned calls;static void*ExpectedSchema,*ExpectedKey;static uint64_t now=100;
static uint64_t clockNow(void){return now;}
static uint8_t original(void**out,uint32_t id,void*name,void*path,bool sqlite,void*schema,uint32_t max,void*key,uint32_t cf,uint32_t cb,float ratio){calls++;assert(id==201&&name==(void*)1&&path==(void*)2&&sqlite&&schema==ExpectedSchema&&key==ExpectedKey&&max==65536&&cf==3&&cb==4);uint32_t bits;memcpy(&bits,&ratio,4);assert(bits==0x80000000);assert(errno==E2BIG);*out=(void*)0x105;return 0x7e;}
int main(int argc,char**argv){assert(argc==3);assert(TIOCaptureFixtureBind(original,argv[1],clockNow));assert(!TIOCaptureFixtureBind(original,argv[1],clockNow));char json[4096]="[{\"name\":\"PublicFixture\",\"idName\":null,\"embedded\":false,\"properties\":[{\"name\":\"title\",\"type\":\"String\"}],\"indexes\":[]}]";if(!strcmp(argv[2],"invalid"))strcpy(json,"[{\"name\":\"P\",\"records\":[],\"properties\":[]}]");
if(!strcmp(argv[2],"utf8")){json[0]=(char)0xc0;json[1]=(char)0xaf;json[2]=0;}
if(!strcmp(argv[2],"duplicate"))strcpy(json,"[{\"name\":\"A\",\"na\\u006de\":\"B\",\"properties\":[]}]");
if(!strcmp(argv[2],"malformed"))strcpy(json,"[{}");
uint64_t object[3]={0,(uintptr_t)json,!strcmp(argv[2],"oversize")?65537:strlen(json)};ExpectedSchema=object;ExpectedKey=(void*)0x555;void*out=NULL;uint32_t bits=0x80000000;float ratio;memcpy(&ratio,&bits,4);errno=E2BIG;assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,object,65536,ExpectedKey,3,4,ratio)==0x7e);assert(calls==1&&TIOCaptureFixtureCopyCount()==0);
ExpectedKey=NULL;if(!strcmp(argv[2],"expired"))now=220;if(!strcmp(argv[2],"rollback"))now=99;errno=E2BIG;assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,object,65536,NULL,3,4,ratio)==0x7e);assert(calls==2);
if(strcmp(argv[2],"normal")){TIOCaptureState expected=(!strcmp(argv[2],"invalid")||!strcmp(argv[2],"utf8")||!strcmp(argv[2],"duplicate")||!strcmp(argv[2],"malformed")||!strcmp(argv[2],"oversize"))?TIOCaptureInvalidSchema:(!strcmp(argv[2],"expired")||!strcmp(argv[2],"rollback"))?TIOCaptureExpired:TIOCaptureExportFailed;assert(TIOCaptureGetState()==expected);puts("CAPTURE_NEGATIVE_FORWARD_ONLY_PASS");return 0;}
assert(TIOCaptureGetState()==TIOCaptureWritten);char path[4096];snprintf(path,sizeof(path),"%s/Documents/ManuscriptLab/captured_schema_projection.json",argv[1]);struct stat st;assert(!lstat(path,&st)&&S_ISREG(st.st_mode)&&(st.st_mode&0777)==0600);assert(TIOCaptureFixtureCopyCount()==3);errno=E2BIG;TIOCaptureForward(&out,201,(void*)1,(void*)2,true,object,65536,NULL,3,4,ratio);assert(calls==3&&TIOCaptureFixtureCopyCount()==3);puts("CAPTURE_TYPED_COPY_FILE_PASS");return 0;}
