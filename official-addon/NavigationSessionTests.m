#import "NavigationSession.h"
#include <assert.h>
int main(void){@autoreleasepool{
 assert(!TIONavigationOwner()); assert(!TIONavigationClaim(@""));
 assert(TIONavigationClaim(@"amap")); assert(!TIONavigationClaim(@"amap")); assert(!TIONavigationClaim(@"mapkit"));
 TIONavigationRelease(@"mapkit"); assert([TIONavigationOwner() isEqual:@"amap"]);
 TIONavigationRelease(@"amap"); assert(TIONavigationClaim(@"mapkit"));
 TIONavigationRelease(@"amap"); assert([TIONavigationOwner() isEqual:@"mapkit"]);
 TIONavigationRelease(@"mapkit"); assert(!TIONavigationOwner());
 NSLog(@"PASS navigation session: exclusive claim, wrong owner release, stop recovery");
}return 0;}
