#import <Foundation/Foundation.h>
#import "../open-observer/OpenObserver.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
extern void TIOStageBObserverArm(void);
extern bool TIOStageBHandleOpen(const TIOOpenObservation *);
static unsigned reads;
bool TIOMemoryCopySelf(void *context,uintptr_t address,size_t length,void *destination,size_t *copied) {
    (void)context;(void)address;(void)length;(void)destination;*copied=0;reads++;return false;
}
int main(void){@autoreleasepool {
    // No audited runtime adapter exists. A public arm plus fabricated observation must never read.
    TIOStageBObserverArm();
    TIOOpenObservation event={(void *)0x1000,1,false};
    assert(!TIOStageBHandleOpen(&event));
    assert(reads==0 && "No capability/direct observation must issue zero memory reads");
    puts("LEGACY_BYPASS_REFUSED");
}return 0;}
