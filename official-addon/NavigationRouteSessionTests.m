#import "NavigationRouteSession.h"
#include <assert.h>
@interface TestRouteOwner:NSObject @end
@implementation TestRouteOwner @end
int main(void){@autoreleasepool{
    TIONavigationRouteSession *s=[TIONavigationRouteSession new];__weak id weakOwner;__block BOOL allowed=YES;__block NSUInteger pulses=0,stops=0;
    @autoreleasepool{id owner=[TestRouteOwner new];weakOwner=owner;NSString *token=[s beginWithOwner:owner backgroundAllowed:^BOOL{return allowed;} pump:^{pulses++;} stop:^(NSString *reason){stops++;}];assert(token&&[s owns:owner]);assert(![s beginWithOwner:[NSObject new] backgroundAllowed:^BOOL{return YES;} pump:^{} stop:^(NSString *r){}]);assert([s backgroundAllowedForToken:token]&&![s backgroundAllowedForToken:@"foreign"]);}
    assert(weakOwner&&s.running);[s pulse];assert(pulses==1);allowed=NO;assert(![s backgroundAllowedForToken:s.token]);[s endOwner:[NSObject new]];assert(s.running);[s stopWithReason:@"用户结束"];assert(stops==1&&!s.running&&!weakOwner);[s stopWithReason:@"重复"];[s pulse];assert(stops==1&&pulses==1);
    id owner=[TestRouteOwner new];NSString *old=s.token;NSString *fresh=[s beginWithOwner:owner backgroundAllowed:^BOOL{return YES;} pump:^{} stop:^(NSString *r){}];assert(fresh&&![s isActiveToken:old]);[s endOwner:owner];assert(![s isActiveToken:fresh]);
    puts("PASS: route service retains live engine owner across page release, exclusive token capability, revoke/pulse/stop exactly once, no restart persistence.");
}}
