#import <Foundation/Foundation.h>
#include <assert.h>
@interface TestHUD:NSObject @property NSDictionary *status; @end
@implementation TestHUD @end
@interface TIONavigationPanel:NSObject @property BOOL active,planning,routeReady,navigationStarted;@property TestHUD *subtitleHUD; @end
@implementation TIONavigationPanel
#include "NavigationStopPresentation.inc"
@end
int main(void){@autoreleasepool{
 TIONavigationPanel *p=[TIONavigationPanel new];p.subtitleHUD=[TestHUD new];p.subtitleHUD.status=@{@"enabled":@NO};assert(![p endNavigationVisible]);
 p.active=p.planning=YES;NSCAssert(![p endNavigationVisible],@"Changing transport triggers route calculation, not navigation: end-navigation action must stay hidden");
 p.planning=NO;p.routeReady=YES;assert(![p endNavigationVisible]);p.routeReady=NO;p.navigationStarted=YES;assert([p endNavigationVisible]);
 p.navigationStarted=p.active=NO;p.subtitleHUD.status=@{@"enabled":@YES};assert([p endNavigationVisible]);p.subtitleHUD.status=@{@"enabled":@NO};assert(![p endNavigationVisible]);
 puts("PASS: actual end-action visibility: idle/calculating/ready hide; started route or owned enabled subtitle show; stopped hide.");
}}
