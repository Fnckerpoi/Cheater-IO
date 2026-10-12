#import <Foundation/Foundation.h>
#import "NavigationCore.h"
#import "NavigationSubtitleHUD.h"
#include <math.h>
// OS / transport doubles; the eligibility method itself is production code.
typedef NS_ENUM(NSInteger, UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject
@property UIApplicationState applicationState;
+ (instancetype)sharedApplication;
@end
@implementation UIApplication
+ (instancetype)sharedApplication{static UIApplication *app;static dispatch_once_t once;dispatch_once(&once,^{app=[UIApplication new];});return app;}
@end
static NSDictionary *Transport,*Tele;
NSDictionary *TIONavTransportStatus(void){return Transport;}
NSDictionary *TIONewsTeleStatus(void){return Tele;}
NSDictionary *TIOSubtitleNavigationStatus(void){return @{};}
BOOL TIOSubtitleConfirmIdle(void){return NO;}
NSString *TIOSubtitleNavigationStart(void){return nil;}
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text){return NO;}
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason){}
@interface TIONavigationPanel:NSObject
@property BOOL active,simulated,fixture,planning,rerouting,gpsWeak,navigationStarted,routeReady;
@property NSTimeInterval lastInfo,lastSDKInfo;
@property NSUInteger infoCallbacks;
@property NSInteger lastGPSStrength;
@property NSDictionary *display;
@property TIONavSubtitleHUD *subtitleHUD,*teleHUD;
- (BOOL)canStartSubtitle;
@end
@implementation TIONavigationPanel
#include "NavigationSubtitleEntry.inc"
@end
int main(void){@autoreleasepool{
    Transport=@{};Tele=@{};UIApplication.sharedApplication.applicationState=UIApplicationStateActive;
    TIONavigationPanel *p=[TIONavigationPanel new];p.active=YES;p.navigationStarted=YES;
    NSMutableDictionary *f=[TIONavDisplay(@"navigating",3,@"测试道路",80,800,600,NO) mutableCopy];f[@"segment"]=@0;p.display=f;p.lastInfo=NSProcessInfo.processInfo.systemUptime;
    p.display=TIONavDisplay(@"ready",0,@"",-1,800,600,NO);p.lastInfo=0;
    NSCAssert([p canStartSubtitle],@"Started real route awaiting first guidance must allow neutral subtitle status");
    p.display=f;p.lastInfo=NSProcessInfo.processInfo.systemUptime;
    NSCAssert([p canStartSubtitle],@"Realtime phone guidance must enable the lens button");
    for(NSString *key in @[@"fixture",@"planning",@"routeReady"]){[p setValue:@YES forKey:key];NSCAssert(![p canStartSubtitle],@"Invalid navigation state blocks lens");[p setValue:@NO forKey:key];}
    p.active=NO;NSCAssert(![p canStartSubtitle],@"Stopped blocks lens");p.active=YES;p.navigationStarted=NO;NSCAssert(![p canStartSubtitle],@"Ready is not running");p.navigationStarted=YES;
    for(NSNumber *time in @[@0,@(NAN),@(NSProcessInfo.processInfo.systemUptime-16),@(NSProcessInfo.processInfo.systemUptime+30)]){p.lastInfo=time.doubleValue;NSCAssert([p canStartSubtitle]&&TIONavSubtitleIsHolding([p subtitleFrame]),@"Stale clock only permits neutral status, never old guidance");}p.lastInfo=NSProcessInfo.processInfo.systemUptime;
    for(NSNumber *state in @[@(UIApplicationStateInactive),@(UIApplicationStateBackground)]){UIApplication.sharedApplication.applicationState=state.integerValue;NSCAssert(![p canStartSubtitle],@"Inactive and background block lens");}UIApplication.sharedApplication.applicationState=UIApplicationStateActive;
    for(NSString *key in @[@"enabled",@"pending",@"noticePending",@"notices"]){Transport=@{key:@YES};NSCAssert(![p canStartSubtitle],@"Other transport blocks lens");}Transport=@{};Tele=@{@"active":@YES};NSCAssert(![p canStartSubtitle],@"Teleprompter blocks lens");Tele=@{};
    for(NSString *phase in @[@"arrived",@"stopped",@"error"]){NSMutableDictionary *bad=[f mutableCopy];bad[@"phase"]=phase;p.display=bad;NSCAssert(![p canStartSubtitle],@"Unsafe frame blocks lens");}p.display=f;
    for(NSString *key in @[@"gpsWeak",@"rerouting"]){[p setValue:@YES forKey:key];NSCAssert([p canStartSubtitle]&&TIONavSubtitleIsHolding([p subtitleFrame]),@"Real weak/reroute can only show neutral status");[p setValue:@NO forKey:key];}
    NSCAssert([[p subtitleGateDiagnostic][@"version"] containsString:@"v4"],@"Version and gate diagnostics available without coordinates");
    p.simulated=YES;NSCAssert(![p canStartSubtitle],@"Frame mode must match session");NSMutableDictionary *sim=[f mutableCopy];sim[@"mode"]=@"模拟导航 · 非实际定位";p.display=sim;NSCAssert([p canStartSubtitle],@"Simulation still supported");
    puts("PASS: actual page gate: started real route permits neutral wait/weak/reroute/stale; terminal/planning/mode/foreground/competing-display guards and reason diagnostics; transport mocked, not lens acceptance.");
}}
