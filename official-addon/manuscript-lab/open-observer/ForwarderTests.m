#import <Foundation/Foundation.h>
#include "OpenObserver.h"
#include <assert.h>
#include <pthread.h>
#include <string.h>

static unsigned calls;
static pthread_t thread;
static float expectedRatio;
static TIOIsar **ExpectedOut;
static uint8_t Original(TIOIsar **out,uint32_t id,TIOCString *name,TIOCString *path,bool sqlite,TIOCString *schema,uint32_t max,TIOCString *key,uint32_t compactFile,uint32_t compactBytes,float ratio) {
    assert(pthread_equal(thread,pthread_self()));assert(out==ExpectedOut);calls++;
    assert(id==0xfedcba98u && name==(void *)0x111 && path==(void *)0x222 && sqlite);
    assert(schema==(void *)0x333 && max==0xabcdef01u && key==NULL);
    assert(compactFile==0x98765432u && compactBytes==0x10203040u);
    assert(!memcmp(&ratio,&expectedRatio,4));*out=(void *)0x777;return 0xa5;
}
int main(void) {@autoreleasepool {
    thread=pthread_self();uint32_t bits=0x80000000;memcpy(&expectedRatio,&bits,4);
    assert(TIOOpenBind(Original));assert(!TIOOpenBind(Original));
    TIOIsar *out=NULL;ExpectedOut=&out;
    uint8_t status=TIOOpenForward(&out,0xfedcba98u,(void *)0x111,(void *)0x222,true,(void *)0x333,0xabcdef01u,NULL,0x98765432u,0x10203040u,expectedRatio);
    assert(status==0xa5 && out==(void *)0x777 && calls==1);
    const uint32_t patterns[]={0x7fc12345,0x00000001,0x3f800000,0xff800000};
    for(unsigned i=0;i<4;i++){
        memcpy(&expectedRatio,&patterns[i],4);out=NULL;
        status=TIOOpenForward(&out,0xfedcba98u,(void *)0x111,(void *)0x222,true,(void *)0x333,0xabcdef01u,NULL,0x98765432u,0x10203040u,expectedRatio);
        assert(status==0xa5 && out==(void *)0x777);
    }
    assert(calls==5);
    puts("OPEN_FORWARD_EXACT_ABI_PASS: 5 exact original invocations/f32 bit patterns/out slot/thread/status");
}return 0;}
