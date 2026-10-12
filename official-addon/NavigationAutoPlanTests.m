#import <Foundation/Foundation.h>
#include <assert.h>
typedef NS_ENUM(int,CLAuthorizationStatus){kCLAuthorizationStatusNotDetermined,kCLAuthorizationStatusRestricted,kCLAuthorizationStatusDenied,kCLAuthorizationStatusAuthorizedAlways,kCLAuthorizationStatusAuthorizedWhenInUse};
typedef NS_ENUM(int,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject @property UIApplicationState applicationState; + (instancetype)sharedApplication; @end
@implementation UIApplication + (instancetype)sharedApplication{static id a;static dispatch_once_t once;dispatch_once(&once,^{a=[self new];});return a;} @end
@interface Permission:NSObject @property CLAuthorizationStatus authorizationStatus; @property NSUInteger requests; - (void)requestWhenInUseAuthorization; @end
@implementation Permission - (void)requestWhenInUseAuthorization{self.requests++;} @end
@interface TestView:NSObject @property id window; @end
@implementation TestView @end
@interface TIONavigationPanel:NSObject
@property BOOL navigationStarted,fixture,hasDestination,initialized,planning,active,autoPlanPending,autoPlanWaitingAuthorization,awaitingMapLocationAuthorization;
@property NSUInteger autoPlanEpoch,plans,refreshes,selectedTransport,plannedMode;
@property NSString *note,*destinationName,*plannedDestination;@property TestView *view;@property id presentedViewController;@property Class retiringManagerClass;@property Permission *permission;
@end
@implementation TIONavigationPanel
- (void)refresh{self.refreshes++;}
- (void)startWalking{self.plans++;self.plannedMode=self.selectedTransport;self.plannedDestination=self.destinationName;}
#define TIO_AUTO_PLAN_TESTS 1
#include "NavigationAutoPlan.inc"
@end
static TIONavigationPanel *P(void){UIApplication.sharedApplication.applicationState=UIApplicationStateActive;TIONavigationPanel *p=[TIONavigationPanel new];p.initialized=p.hasDestination=YES;p.destinationName=@"synthetic A";p.permission=[Permission new];p.permission.authorizationStatus=kCLAuthorizationStatusAuthorizedWhenInUse;p.view=[TestView new];p.view.window=[NSObject new];return p;}
int main(void){@autoreleasepool{
 TIONavigationPanel *p=P();[p scheduleAutoPlan];NSUInteger first=p.autoPlanEpoch;p.destinationName=@"synthetic B";p.selectedTransport=1;[p scheduleAutoPlan];NSUInteger second=p.autoPlanEpoch;[p drainAutoPlan:first attempt:0];assert(!p.plans);[p drainAutoPlan:second attempt:0];assert(p.plans==1&&p.plannedMode==1&&[p.plannedDestination isEqual:@"synthetic B"]&&!p.navigationStarted);[p drainAutoPlan:second attempt:0];assert(p.plans==1);
 for(NSUInteger mode=0;mode<3;mode++){p=P();p.selectedTransport=mode;[p scheduleAutoPlan];[p resumeAutoPlan];assert(p.plans==1&&p.plannedMode==mode&&!p.navigationStarted);}
 p=P();p.permission.authorizationStatus=kCLAuthorizationStatusNotDetermined;[p scheduleAutoPlan];[p resumeAutoPlan];[p resumeAutoPlan];assert(p.permission.requests==1&&!p.plans);p.permission.authorizationStatus=kCLAuthorizationStatusAuthorizedWhenInUse;[p resumeAutoPlan];assert(p.plans==1&&!p.autoPlanPending);
 p=P();p.permission.authorizationStatus=kCLAuthorizationStatusNotDetermined;p.awaitingMapLocationAuthorization=YES;[p scheduleAutoPlan];[p resumeAutoPlan];assert(!p.permission.requests&&!p.plans&&p.autoPlanWaitingAuthorization);p.permission.authorizationStatus=kCLAuthorizationStatusAuthorizedAlways;[p resumeAutoPlan];assert(p.plans==1);
 for(NSNumber *auth in @[@(kCLAuthorizationStatusDenied),@(kCLAuthorizationStatusRestricted)]){p=P();p.permission.authorizationStatus=auth.intValue;[p scheduleAutoPlan];[p resumeAutoPlan];assert(!p.plans&&!p.autoPlanPending&&!p.permission.requests);}
 p=P();p.initialized=NO;[p scheduleAutoPlan];[p resumeAutoPlan];assert(!p.plans&&p.autoPlanPending);p.initialized=YES;[p resumeAutoPlan];assert(p.plans==1);
 p=P();[p scheduleAutoPlan];UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;[p resumeAutoPlan];assert(!p.plans);[p cancelAutoPlan];UIApplication.sharedApplication.applicationState=UIApplicationStateActive;[p resumeAutoPlan];assert(!p.plans);
 p=P();p.view.window=nil;[p scheduleAutoPlan];[p resumeAutoPlan];assert(!p.plans);[p cancelAutoPlan];
 for(NSString *key in @[@"navigationStarted",@"fixture"]){p=P();[p setValue:@YES forKey:key];[p scheduleAutoPlan];assert(!p.autoPlanPending&&!p.plans);}p=P();p.hasDestination=NO;[p scheduleAutoPlan];assert(!p.plans&&!p.autoPlanPending);
 p=P();p.retiringManagerClass=NSObject.class;[p scheduleAutoPlan];[p drainAutoPlan:p.autoPlanEpoch attempt:40];assert(!p.plans&&!p.autoPlanPending&&[p.note containsString:@"未就绪"]);p.retiringManagerClass=Nil;[p resumeAutoPlan];assert(!p.plans);
 p=P();p.retiringManagerClass=NSObject.class;[p scheduleAutoPlan];p.retiringManagerClass=Nil;[p resumeAutoPlan];assert(p.plans==1); // latest released owner only
 p=P();p.planning=p.active=YES;[p scheduleAutoPlan];[p resumeAutoPlan];assert(!p.plans&&p.autoPlanPending);p.active=p.planning=NO;[p resumeAutoPlan];assert(p.plans==1);
 p=P();[p scheduleAutoPlan];first=p.autoPlanEpoch;[p cancelAutoPlan];[p drainAutoPlan:first attempt:0];assert(!p.plans);
 p=P();[p scheduleAutoPlan];[[NSRunLoop mainRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.3]];assert(p.plans==1); // real deferred dispatch path; callbacks from old epochs never plan
 puts("PASS: production auto-plan include: latest destination/mode, deferred coalescing, authorization resume/deny, map gates, release bound, cancel/offscreen/background/no-target/no-auto-start; each engine mode preserved.");
}}
