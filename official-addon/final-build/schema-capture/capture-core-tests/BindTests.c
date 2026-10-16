#include "Capture.h"
#include "CaptureInternal.h"
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
static unsigned Calls;
static uint8_t original(void**out,uint32_t id,void*n,void*p,bool sql,void*s,uint32_t max,void*k,uint32_t cf,uint32_t cb,float ratio){
 Calls++;assert(id==201&&n==(void*)1&&p==(void*)2&&sql&&s==(void*)3&&max==65536&&k==(void*)4&&cf==5&&cb==6);uint32_t bits;memcpy(&bits,&ratio,4);assert(bits==0x80000000&&errno==E2BIG);*out=(void*)0x105;errno=EDOM;return 0x7e;
}
int main(void){
 assert(TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);
 assert(!TIOCaptureBindOriginalInternal(NULL));assert(TIOCaptureGetState()==TIOCaptureBlockedEarlyRuntime);
 assert(TIOCaptureBindOriginalInternal(original));assert(TIOCaptureGetState()==TIOCaptureWaiting);
 assert(!TIOCaptureBindOriginalInternal(original));assert(!TIOCaptureBindOriginalInternal(NULL));
 void*out=NULL;uint32_t bits=0x80000000;float ratio;memcpy(&ratio,&bits,4);errno=E2BIG;
 assert(TIOCaptureForward(&out,201,(void*)1,(void*)2,true,(void*)3,65536,(void*)4,5,6,ratio)==0x7e);
 assert(Calls==1&&out==(void*)0x105&&errno==EDOM&&TIOCaptureGetState()==TIOCaptureWaiting);
 puts("PRIVATE_BIND_ORIGINAL_ONCE_EXACT_ABI_NO_CAPTURE_PASS");return 0;
}
