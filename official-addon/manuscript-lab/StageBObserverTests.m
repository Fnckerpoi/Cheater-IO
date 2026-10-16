#import "stage-b-controller/StageBController.h"
#import "memory-copy/MemoryCopy.h"
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

static uint64_t now __attribute__((unused)) = 100;
static unsigned calls, reads;
static TIOStageBController *current;
static pthread_t expectedThread;
static TIOIsar **expectedOut;
static TIOCString *expectedSchema, *expectedKey;
static float expectedRatio;
static BOOL throwingOriginal;
static unsigned throwAfterRead;
static uint64_t __attribute__((unused)) clockNow(void *unused) { (void)unused; return now; }
static uint64_t __attribute__((unused)) throwingArmClock(void *unused) { (void)unused; @throw [NSException exceptionWithName:@"ArmClockException" reason:nil userInfo:nil]; }
static bool __attribute__((unused)) boundedCopy(void *unused,uintptr_t address,size_t length,void *out,size_t *copied) {
    (void)unused; reads++;
    if (throwAfterRead && reads == throwAfterRead) @throw [NSException exceptionWithName:@"PayloadCopyException" reason:nil userInfo:nil];
    return TIOMemoryCopySelf(NULL,address,length,out,copied);
}
static bool __attribute__((unused)) throwingCopy(void *unused,uintptr_t address,size_t length,void *out,size_t *copied) {
    (void)unused;(void)address;(void)length;(void)out;(void)copied;
    @throw [NSException exceptionWithName:@"ObserverOnly" reason:nil userInfo:nil];
}
static uint8_t original(TIOIsar **out,uint32_t id,TIOCString *name,TIOCString *path,bool sqlite,TIOCString *schema,uint32_t max,TIOCString *key,uint32_t compactFile,uint32_t compactBytes,float ratio) {
    assert(pthread_equal(expectedThread,pthread_self())); calls++;
    assert(out==expectedOut && id==0xfedcba98 && name==(void *)0x111 && path==(void *)0x222 && sqlite);
    assert(schema==expectedSchema && max==0xabcdef01 && key==expectedKey);
    assert(compactFile==0x98765432 && compactBytes==0x10203040 && !memcmp(&ratio,&expectedRatio,4));
#if defined(TIO_STAGE_B_TESTING)
    assert([current testOwnedCopyReleased]);
#endif
    assert(errno==E2BIG); *out=(void *)0x777;
    if(throwingOriginal) @throw [NSException exceptionWithName:@"OriginalException" reason:nil userInfo:nil];
    return 0xa5;
}
static void invoke(TIOStageBController *controller,TIOCString *schema,TIOCString *key) {
    current=controller;expectedThread=pthread_self();expectedSchema=schema;expectedKey=key;
    TIOIsar *out=NULL;expectedOut=&out;errno=E2BIG;unsigned before=calls;
    uint8_t status=[controller forwardOut:&out identifier:0xfedcba98 name:(void *)0x111 path:(void *)0x222 sqlite:true schema:schema maxSize:0xabcdef01 key:key compactFile:0x98765432 compactBytes:0x10203040 ratio:expectedRatio];
    assert(status==0xa5 && out==(void *)0x777 && calls==before+1);
}
static void * __attribute__((unused)) otherThread(void *context) {
    @autoreleasepool { invoke((__bridge TIOStageBController *)context,(void *)0x333,NULL); }
    return NULL;
}
int main(void) {@autoreleasepool {
    uint32_t bits=0x7fc12345;memcpy(&expectedRatio,&bits,4);
    TIOStageBController *blocked=[[TIOStageBController alloc] initWithOriginal:original];
    assert(![blocked arm] && [blocked state]==TIOStageBBlockedRuntimeEntry);
    invoke(blocked,(void *)0x333,(void *)0x444);assert(reads==0 && ![blocked capturedProjection]);
    assert([TIOStageBProductionStatus()[@"active"] isEqual:@NO]);
    const uint32_t patterns[]={0x80000000,0x00000001,0xff800000,0x3f800000};
    for(unsigned i=0;i<4;i++){memcpy(&expectedRatio,&patterns[i],4);invoke(blocked,(void *)0x333,NULL);}
    memcpy(&expectedRatio,&bits,4);
#if defined(TIO_STAGE_B_TESTING)
    TIOStageBController *armException=[[TIOStageBController alloc] initWithOriginal:original];
    assert([armException testGrantForCurrentThreadWithRole:YES lifetime:YES clock:throwingArmClock clockContext:NULL copy:boundedCopy copyContext:NULL]);
    BOOL escapedArmException=NO;BOOL armSucceeded=NO;
    @try {armSucceeded=[armException arm];}@catch(NSException *e){(void)e;escapedArmException=YES;}
    assert(!escapedArmException && !armSucceeded);
    assert([armException state]==TIOStageBTerminal); // Must remain reachable, not a timeout-based PASS.
    invoke(armException,(void *)0x333,NULL);assert(reads==0 && ![armException arm]);
    TIOStageBController *unproven=[[TIOStageBController alloc] initWithOriginal:original];
    assert([unproven testGrantForCurrentThreadWithRole:NO lifetime:YES clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert(![unproven arm]);
    invoke(unproven,(void *)0x333,NULL);assert(reads==0);
    TIOStageBController *badLifetime=[[TIOStageBController alloc] initWithOriginal:original];
    assert([badLifetime testGrantForCurrentThreadWithRole:YES lifetime:NO clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert(![badLifetime arm]);
    const char *json="[{\"name\":\"PublicFixture\",\"properties\":[{\"name\":\"title\",\"type\":\"String\"}]}]";
    uint64_t object[3]={0,(uintptr_t)json,strlen(json)};
    TIOStageBController *test=[[TIOStageBController alloc] initWithOriginal:original];
    assert([test testGrantForCurrentThreadWithRole:YES lifetime:YES clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert([test arm]);assert(![test arm]);
    invoke(test,(void *)object,(void *)0x555);assert(reads==0 && ![test capturedProjection]);
    pthread_t foreign;assert(!pthread_create(&foreign,NULL,otherThread,(__bridge void *)test));assert(!pthread_join(foreign,NULL));assert(reads==0 && ![test capturedProjection]);
    invoke(test,(void *)object,NULL);assert(reads==3 && [test capturedProjection] && [test state]==TIOStageBTerminal);assert(![test arm]);
    invoke(test,(void *)object,NULL);assert(reads==3);
    TIOStageBController *rollback=[[TIOStageBController alloc] initWithOriginal:original];
    now=150;assert([rollback testGrantForCurrentThreadWithRole:YES lifetime:YES clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert([rollback arm]);now=101;invoke(rollback,(void *)object,NULL);assert(reads==3 && ![rollback capturedProjection]);
    TIOStageBController *exception=[[TIOStageBController alloc] initWithOriginal:original];now=200;
    assert([exception testGrantForCurrentThreadWithRole:YES lifetime:YES clock:clockNow clockContext:NULL copy:throwingCopy copyContext:NULL]);assert([exception arm]);invoke(exception,(void *)object,NULL);assert(![exception capturedProjection]);
    TIOStageBController *expired=[[TIOStageBController alloc] initWithOriginal:original];now=300;
    assert([expired testGrantForCurrentThreadWithRole:YES lifetime:YES clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert([expired arm]);now=420;invoke(expired,(void *)object,NULL);assert(reads==3 && ![expired capturedProjection]);
    TIOStageBController *payloadException=[[TIOStageBController alloc] initWithOriginal:original];now=500;
    assert([payloadException testGrantForCurrentThreadWithRole:YES lifetime:YES clock:clockNow clockContext:NULL copy:boundedCopy copyContext:NULL]);assert([payloadException arm]);throwAfterRead=reads+3;invoke(payloadException,(void *)object,NULL);throwAfterRead=0;assert(![payloadException capturedProjection] && [payloadException testOwnedCopyReleased]);
    throwingOriginal=YES;BOOL propagated=NO;@try {invoke(blocked,(void *)object,NULL);}@catch(NSException *e){propagated=[e.name isEqual:@"OriginalException"];}assert(propagated);throwingOriginal=NO;
#endif
    puts("STAGE_B_CONTROLLER_PASS: production blocked, full typed original forwarded exactly once; test fixtures are not runtime proof");
}return 0;}
