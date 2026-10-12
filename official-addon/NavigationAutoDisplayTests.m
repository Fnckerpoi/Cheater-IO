#import <Foundation/Foundation.h>
#import "NavigationSubtitleHUD.h"
#include <assert.h>
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject
@property UIApplicationState applicationState;
+ (instancetype)sharedApplication;
@end
@implementation UIApplication
+ (instancetype)sharedApplication{static id app;static dispatch_once_t once;dispatch_once(&once,^{app=[self new];});return app;}
@end
static NSDictionary *Runtime;
static NSUInteger Confirmations;
static BOOL Idle=YES;
NSDictionary *TIOSubtitleNavigationStatus(void){return Runtime;}
BOOL TIOSubtitleConfirmIdle(void){Confirmations++;return Idle;}
@interface FakeHUD:NSObject
@property NSUInteger starts;@property BOOL route;@property NSString *token;
- (BOOL)startWithFrame:(NSDictionary *)f at:(NSTimeInterval)n;
- (BOOL)startRouteWithFrame:(NSDictionary *)f token:(NSString *)t at:(NSTimeInterval)n;
@end
@implementation FakeHUD
- (BOOL)startWithFrame:(NSDictionary *)f at:(NSTimeInterval)n{self.starts++;return f!=nil;}
- (BOOL)startRouteWithFrame:(NSDictionary *)f token:(NSString *)t at:(NSTimeInterval)n{self.starts++;self.route=YES;self.token=t;return f&&t;}
@end
@interface TIONavigationPanel:NSObject
@property BOOL autoDisplayAttempted,navigationStarted,fixture,lensStartApproved,simulated,blocked;
@property NSString *note,*routeToken;@property NSDictionary *frame;@property FakeHUD *subtitleHUD;
@end
@implementation TIONavigationPanel
- (NSDictionary *)subtitleFrame{return self.frame;}
- (NSString *)subtitleBlockReason{return self.blocked?@"occupied":nil;}
#include "NavigationAutoDisplay.inc"
@end
static TIONavigationPanel *P(void){Runtime=@{@"available":@YES,@"phase":@"idle"};Idle=YES;Confirmations=0;UIApplication.sharedApplication.applicationState=UIApplicationStateActive;TIONavigationPanel *p=[TIONavigationPanel new];p.navigationStarted=YES;p.lensStartApproved=YES;p.frame=@{@"phase":@"waiting"};p.routeToken=@"route";p.subtitleHUD=[FakeHUD new];return p;}
int main(void){@autoreleasepool{
 TIONavigationPanel *p=P();[p startAutomaticDisplay];assert(p.subtitleHUD.starts==1&&p.subtitleHUD.route&&[p.subtitleHUD.token isEqual:@"route"]&&Confirmations==1);[p startAutomaticDisplay];assert(p.subtitleHUD.starts==1);
 for(NSString *key in @[@"fixture",@"blocked"]){p=P();[p setValue:@YES forKey:key];[p startAutomaticDisplay];assert(p.subtitleHUD.starts==0);}
 p=P();p.navigationStarted=NO;[p startAutomaticDisplay];assert(!p.subtitleHUD.starts);p=P();p.lensStartApproved=NO;[p startAutomaticDisplay];assert(!p.subtitleHUD.starts);
 for(NSString *phase in @[@"starting",@"ready",@"stopping",@"uncertain"]){p=P();Runtime=@{@"available":@YES,@"phase":phase};[p startAutomaticDisplay];assert(!p.subtitleHUD.starts&&Confirmations==0);}
 p=P();Runtime=@{@"available":@NO,@"phase":@"idle"};[p startAutomaticDisplay];assert(!p.subtitleHUD.starts);p=P();UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;[p startAutomaticDisplay];assert(!p.subtitleHUD.starts);
 p=P();p.simulated=YES;p.frame=nil;[p startAutomaticDisplay];assert(!p.autoDisplayAttempted&&!p.subtitleHUD.starts);p.frame=@{};[p startAutomaticDisplay];assert(p.autoDisplayAttempted&&p.subtitleHUD.starts==1&&!p.subtitleHUD.route);
 p=P();Runtime=@{@"available":@YES,@"phase":@"uncertain",@"navigation":@YES,@"routeScoped":@YES,@"note":@"已请求退出，待确认"};[p startAutomaticDisplay];
 NSCAssert(p.subtitleHUD.starts==1&&Confirmations==1,@"New foreground start with explicit lens-idle confirmation must recover own stopped/uncertain session before phase-idle gate");
 p=P();Idle=NO;[p startAutomaticDisplay];assert(!p.subtitleHUD.starts);
 puts("PASS: actual auto-display include: SDK running + current confirmation only, once, no background starts or occupied/unknown sessions, simulation waits for first callback.");
}}
