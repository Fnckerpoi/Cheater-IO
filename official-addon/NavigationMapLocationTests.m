#import <Foundation/Foundation.h>
#import "NavigationCore.h"
#include <assert.h>
#include <math.h>
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
typedef NS_ENUM(int,CLAuthorizationStatus){kCLAuthorizationStatusNotDetermined=0,kCLAuthorizationStatusRestricted=1,kCLAuthorizationStatusDenied=2,kCLAuthorizationStatusAuthorizedAlways=3,kCLAuthorizationStatusAuthorizedWhenInUse=4};
typedef struct {double latitude,longitude;} CLLocationCoordinate2D;
@interface UIApplication:NSObject @property UIApplicationState applicationState; + (instancetype)sharedApplication; @end
@implementation UIApplication + (instancetype)sharedApplication{static id a;static dispatch_once_t once;dispatch_once(&once,^{a=[self new];});return a;} @end
@interface CLLocationManager:NSObject @property CLAuthorizationStatus authorizationStatus; @property NSUInteger requests; - (void)requestWhenInUseAuthorization; @end
@implementation CLLocationManager - (void)requestWhenInUseAuthorization{self.requests++;} @end
@interface TestTimestamp:NSObject @property double timeIntervalSinceNow; @end
@implementation TestTimestamp @end
@interface CLLocation:NSObject @property double horizontalAccuracy; @property TestTimestamp *timestamp; @end
@implementation CLLocation @end
@interface TestView:NSObject @property id window; @end
@implementation TestView @end
@interface TestMap:NSObject @property BOOL showsUserLocation; @property NSUInteger centers; @property CLLocationCoordinate2D center; - (void)setCenterCoordinate:(CLLocationCoordinate2D)c animated:(BOOL)animation; @end
@implementation TestMap - (void)setCenterCoordinate:(CLLocationCoordinate2D)c animated:(BOOL)a{self.center=c;self.centers++;} @end
@interface TIONavigationPanel:NSObject
@property BOOL initialized,locating,awaitingMapLocationAuthorization,autoLocateAttempted,active,planning,routeReady,navigationStarted;
@property NSUInteger locationGeneration,refreshes; @property TestView *view; @property id presentedViewController;
@property CLLocationManager *permission; @property TestMap *map; @property NSString *note,*destinationName;
@end
@implementation TIONavigationPanel
- (void)refresh{self.refreshes++;}
#include "NavigationMapLocation.inc"
@end
static TIONavigationPanel *P(CLAuthorizationStatus auth){UIApplication.sharedApplication.applicationState=UIApplicationStateActive;TIONavigationPanel *p=[TIONavigationPanel new];p.initialized=YES;p.view=[TestView new];p.view.window=[NSObject new];p.permission=[CLLocationManager new];p.permission.authorizationStatus=auth;p.map=[TestMap new];p.destinationName=@"synthetic destination";return p;}
static CLLocation *L(double accuracy,double age){CLLocation *l=[CLLocation new];l.horizontalAccuracy=accuracy;l.timestamp=[TestTimestamp new];l.timestamp.timeIntervalSinceNow=age;return l;}
int main(void){@autoreleasepool{
 CLLocationCoordinate2D c={22.5,113.4};
 TIONavigationPanel *p=P(kCLAuthorizationStatusAuthorizedWhenInUse);[p locateMapOnEntry:0];assert(p.locating&&p.autoLocateAttempted&&p.map.showsUserLocation&&!p.permission.requests&&!p.navigationStarted);NSUInteger epoch=p.locationGeneration;
 [p locateMapOnEntry:0];assert(p.locationGeneration==epoch);assert([p acceptMapLocation:L(20,0) coordinate:c]);assert(p.map.centers==1&&!p.map.showsUserLocation&&!p.locating&&!p.navigationStarted&&[p.destinationName isEqual:@"synthetic destination"]);[p expireMapLocation:epoch];assert(p.map.centers==1);
 p=P(kCLAuthorizationStatusNotDetermined);[p locateMapOnEntry:0];assert(p.awaitingMapLocationAuthorization&&p.permission.requests==1&&!p.map.showsUserLocation);[p beginMapLocation];assert(p.permission.requests==1);p.permission.authorizationStatus=kCLAuthorizationStatusAuthorizedWhenInUse;UIApplication.sharedApplication.applicationState=UIApplicationStateInactive;[p resumeMapLocationAuthorization];assert(p.awaitingMapLocationAuthorization&&!p.locating);UIApplication.sharedApplication.applicationState=UIApplicationStateActive;[p resumeMapLocationAuthorization];assert(!p.awaitingMapLocationAuthorization&&p.locating&&p.permission.requests==1);
 p=P(kCLAuthorizationStatusNotDetermined);[p locateMapOnEntry:0];p.permission.authorizationStatus=kCLAuthorizationStatusDenied;[p resumeMapLocationAuthorization];assert(!p.locating&&!p.map.showsUserLocation&&!p.awaitingMapLocationAuthorization&&[p.note containsString:@"拒绝"]);
 for(NSNumber *status in @[@(kCLAuthorizationStatusDenied),@(kCLAuthorizationStatusRestricted)]){p=P(status.intValue);[p locateMapOnEntry:0];assert(!p.locating&&!p.permission.requests&&!p.map.showsUserLocation);}
 for(NSArray *pair in @[@[@(-1),@0],@[@201,@0],@[@(NAN),@0],@[@20,@16],@[@20,@(-16)],@[@20,@(NAN)]]){p=P(kCLAuthorizationStatusAuthorizedWhenInUse);[p beginMapLocation];assert(![p acceptMapLocation:L([pair[0] doubleValue],[pair[1] doubleValue]) coordinate:c]&&p.locating&&!p.map.centers);[p cancelMapLocation];}
 p=P(kCLAuthorizationStatusAuthorizedWhenInUse);[p beginMapLocation];CLLocationCoordinate2D bad={NAN,113.4};assert(![p acceptMapLocation:L(20,0) coordinate:bad]);bad.latitude=91;assert(![p acceptMapLocation:L(20,0) coordinate:bad]);
 p=P(kCLAuthorizationStatusAuthorizedWhenInUse);[p beginMapLocation];epoch=p.locationGeneration;[p cancelMapLocation];assert(![p acceptMapLocation:L(20,0) coordinate:c]);[p beginMapLocation];NSUInteger newEpoch=p.locationGeneration;[p expireMapLocation:epoch];assert(p.locating);[p expireMapLocation:newEpoch];assert(!p.locating&&!p.map.showsUserLocation&&[p.note containsString:@"重试"]);
 p=P(kCLAuthorizationStatusAuthorizedWhenInUse);p.active=p.routeReady=YES;p.map.showsUserLocation=YES;[p locateMapOnEntry:0];assert(!p.locating&&!p.autoLocateAttempted);[p beginMapLocation];assert(p.locating);assert([p acceptMapLocation:L(20,0) coordinate:c]&&p.active&&p.routeReady&&p.map.showsUserLocation&&[p.destinationName isEqual:@"synthetic destination"]);
 p=P(kCLAuthorizationStatusAuthorizedWhenInUse);UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;[p locateMapOnEntry:0];assert(!p.locating&&!p.permission.requests);p=P(kCLAuthorizationStatusAuthorizedWhenInUse);p.view.window=nil;[p locateMapOnEntry:0];assert(!p.locating&&!p.permission.requests);p=P(kCLAuthorizationStatusAuthorizedWhenInUse);p.initialized=NO;[p locateMapOnEntry:0];assert(!p.locating);
 p=P(kCLAuthorizationStatusNotDetermined);[p locateMapOnEntry:0];[p cancelMapLocation];p.permission.authorizationStatus=kCLAuthorizationStatusAuthorizedWhenInUse;[p resumeMapLocationAuthorization];assert(!p.locating);
 p=P(kCLAuthorizationStatusAuthorizedWhenInUse);[p beginMapLocation];p.permission.authorizationStatus=kCLAuthorizationStatusDenied;assert(![p acceptMapLocation:L(20,0) coordinate:c]&&!p.locating&&!p.map.centers);
 puts("PASS: production auto-location include: one-shot centering, authorization continuation/deny, freshness/accuracy/coordinate gates, stale timeout cancellation, no GPS offscreen/background or automatic route; running/ready route preserved.");
}}
