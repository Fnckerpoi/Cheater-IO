#include "ResolverBridge.h"
#include <assert.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
uint64_t TIOSentinelWords[11];
unsigned char TIOSentinelVectors[128], TIOSentinelCaptured[128];
extern uintptr_t TIOSentinelInvoke(TIOResolverFunction);
extern void *TIOSentinelResolver(void *,const char *);
int main(void) {
    for(unsigned i=0;i<128;i++)TIOSentinelVectors[i]=(unsigned char)(i+1);
    assert(TIOResolverBridgeBind(TIOSentinelResolver));
    assert(TIOSentinelInvoke(TIOSentinelResolver)==0x12345678);
    uint64_t expected[11];unsigned char expectedVectors[128];
    memcpy(expected,TIOSentinelWords,sizeof(expected));memcpy(expectedVectors,TIOSentinelCaptured,128);
    memset(TIOSentinelWords,0,sizeof(TIOSentinelWords));memset(TIOSentinelCaptured,0,128);
    assert(TIOSentinelInvoke(TIOResolverBridge)==0x12345678);
    assert(!memcmp(expected,TIOSentinelWords,sizeof(expected)));
    assert(!memcmp(expectedVectors,TIOSentinelCaptured,128));
#if defined(__x86_64__)
    const uint64_t arguments[]={0x111,0x222,0x333,0x444,0x555,0x666,8};
    assert(!memcmp(TIOSentinelWords,arguments,sizeof(arguments)));
    assert((TIOSentinelWords[7]&15)==8 && TIOSentinelWords[8]);
    assert(!memcmp(TIOSentinelVectors,TIOSentinelCaptured,128));
    puts("BRIDGE_X86_64_SENTINELS_PASS: six integer args, AL, xmm0-7, exact SP and caller return-PC; native execution only");
#else
    const uint64_t arguments[]={0x111,0x222,0x333,0x444,0x555,0x666,0x777,0x888,0x999};
    assert(!memcmp(TIOSentinelWords,arguments,sizeof(arguments)));
    assert((TIOSentinelWords[9]&15)==0 && TIOSentinelWords[10]);
    puts("BRIDGE_ARM64_SENTINELS_PASS: x0-x8,v0-v7,SP,LR native execution");
#endif
    return 0;
}
