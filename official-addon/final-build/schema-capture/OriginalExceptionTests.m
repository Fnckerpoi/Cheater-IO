#import <Foundation/Foundation.h>
#include "Capture.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>
static unsigned Calls;
static uint64_t now(void){return 100;}
static uint8_t throwingOriginal(void**out,uint32_t id,void*n,void*p,bool sql,void*s,uint32_t max,void*k,uint32_t cf,uint32_t cb,float r){(void)out;(void)id;(void)n;(void)p;(void)sql;(void)s;(void)max;(void)k;(void)cf;(void)cb;(void)r;Calls++;@throw [NSException exceptionWithName:@"RealOriginalException" reason:nil userInfo:nil];}
int main(int argc,char**argv){@autoreleasepool{assert(argc==2);assert(TIOCaptureFixtureBind(throwingOriginal,argv[1],now));const char*json="[{\"name\":\"Public\",\"properties\":[]}]";uint64_t schema[3]={0,(uintptr_t)json,strlen(json)};bool caught=false;@try{TIOCaptureForward(NULL,0,NULL,NULL,false,schema,0,NULL,0,0,0);}@catch(NSException*e){caught=[e.name isEqual:@"RealOriginalException"];}assert(caught&&Calls==1&&TIOCaptureGetState()==TIOCaptureWritten);puts("CAPTURE_ORIGINAL_EXCEPTION_PROPAGATED_PASS");}return 0;}
