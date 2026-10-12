#import <Foundation/Foundation.h>
#import "NavigationSubtitleHUD.h"
#include <assert.h>
NSDictionary *TIOSubtitleNavigationStatus(void){return @{};}
NSString *TIOSubtitleNavigationStart(void){return nil;}
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text){return NO;}
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason){}
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject
@property UIApplicationState applicationState;
+ (instancetype)sharedApplication;
@end
@implementation UIApplication
+ (instancetype)sharedApplication{static id app;static dispatch_once_t once;dispatch_once(&once,^{app=[self new];});return app;}
@end
@interface FakeManager:NSObject
@property BOOL allowsBackgroundLocationUpdates,pausesLocationUpdatesAutomatically,accept;
@property NSUInteger starts;
- (BOOL)startGPSNavi;
- (BOOL)startEmulatorNavi;
@end
@implementation FakeManager
- (BOOL)startGPSNavi{self.starts++;return self.accept;}
- (BOOL)startEmulatorNavi{self.starts++;return self.accept;}
@end
@interface TIONavigationPanel:NSObject
@property BOOL routeReady,planning,lensStartApproved,simulated,backgroundLocationEnabled,navigationStarted,staleShown,authorized,modes,retainAccepted;
@property NSUInteger failures,retains,automatic,refreshes;
@property NSTimeInterval lastInfo;
@property FakeManager *manager;
@property NSString *note;
@end
@implementation TIONavigationPanel
- (BOOL)navigationLocationAuthorized{return self.authorized;}
- (BOOL)navigationLocationModeAvailable{return self.modes;}
- (BOOL)retainLiveRoute{assert(self.navigationStarted&&self.manager.starts==1);self.retains++;return self.retainAccepted;}
- (void)fail:(NSString *)reason{self.failures++;self.navigationStarted=NO;}
- (void)alert:(NSString *)title message:(NSString *)message{}
- (void)setFrame:(NSDictionary *)frame{assert(self.retains==1);}
- (void)startAutomaticDisplay{assert(self.navigationStarted&&self.retains==1);self.automatic++;}
- (void)refresh{self.refreshes++;}
#define TIO_AMAP_ENABLED 1
#include "NavigationBeginRoute.inc"
@end
static TIONavigationPanel *P(void){UIApplication.sharedApplication.applicationState=UIApplicationStateActive;TIONavigationPanel *p=[TIONavigationPanel new];p.routeReady=p.lensStartApproved=p.authorized=p.modes=p.retainAccepted=YES;p.manager=[FakeManager new];p.manager.accept=YES;return p;}
int main(void){@autoreleasepool{
 TIONavigationPanel *p=P();[p beginApprovedRoute];assert(p.automatic==1&&p.navigationStarted&&p.backgroundLocationEnabled&&!p.manager.pausesLocationUpdatesAutomatically);
 for(NSString *key in @[@"routeReady",@"lensStartApproved",@"authorized"]){p=P();[p setValue:@NO forKey:key];[p beginApprovedRoute];assert(!p.manager.starts&&!p.automatic);}
 p=P();p.manager.accept=NO;[p beginApprovedRoute];assert(p.failures==1&&!p.retains&&!p.automatic);p=P();p.retainAccepted=NO;[p beginApprovedRoute];assert(p.failures==1&&!p.automatic);
 p=P();UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;[p beginApprovedRoute];assert(!p.manager.starts&&!p.automatic);
 p=P();p.modes=NO;[p beginApprovedRoute];assert(p.navigationStarted&&!p.backgroundLocationEnabled&&p.automatic==1);
 p=P();p.simulated=YES;[p beginApprovedRoute];assert(!p.backgroundLocationEnabled&&p.automatic==1);
 puts("PASS: actual SDK begin include: approval/foreground/permission, engine success before retained owner before auto display, failure no auto, missing background mode only foreground.");
}}
