#import <Foundation/Foundation.h>
#import <objc/message.h>
#import "SubtitleHUDCore.h"
#import "NavigationRouteSession.h"
#include <assert.h>
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject
@property UIApplicationState applicationState;
+ (instancetype)sharedApplication;
@end
@implementation UIApplication
+ (instancetype)sharedApplication{static id a;static dispatch_once_t once;dispatch_once(&once,^{a=[self new];});return a;}
@end
@interface FlutterStandardTypedData:NSObject
@property NSData *data;
+ (instancetype)typedDataWithBytes:(NSData *)bytes;
@end
@implementation FlutterStandardTypedData
+ (instancetype)typedDataWithBytes:(NSData *)bytes{FlutterStandardTypedData *d=[self new];d.data=bytes;return d;}
@end
@interface FlutterMethodCall:NSObject
@property NSString *method;@property NSDictionary *arguments;
+ (instancetype)methodCallWithMethodName:(NSString *)method arguments:(NSDictionary *)args;
@end
@implementation FlutterMethodCall
+ (instancetype)methodCallWithMethodName:(NSString *)m arguments:(NSDictionary *)a{FlutterMethodCall *c=[self new];c.method=m;c.arguments=a;return c;}
@end
static id Plugin;static NSDictionary *Route;static NSString *Device,*CurrentDevice;
static BOOL Sending,TextPending,Allowed=YES;static double TextDeadline,Clock;
static TIOSubtitleTrial *Trial;
static NSString *TIOProtocolDevice(void){return CurrentDevice;}
static BOOL RouteCanContinue(void){return Trial.routeScoped&&Allowed;}
static double Now(void){return Clock;}
static void Save(void){}
static void Evidence(NSString *d,NSDictionary *e){}
@interface FakeBridge:NSObject
@property NSUInteger sends;@property NSDictionary *last;@property(copy) void(^completion)(id);
- (void)handleMethodCall:(FlutterMethodCall *)call result:(void(^)(id))result;
@end
@implementation FakeBridge
- (void)handleMethodCall:(FlutterMethodCall *)call result:(void(^)(id))result{self.sends++;self.last=TIOSubtitleEnvelope([(FlutterStandardTypedData *)call.arguments[@"payload"] data]);assert([call.arguments[@"deviceId"] isEqual:@"synthetic-device"]&&[call.arguments[@"businessId"] isEqual:@19]);self.completion=result;}
@end
#include "SubtitleHUDSendPolicy.inc"
#include "SubtitleHUDSend.inc"
static void Drain(void){[[NSRunLoop mainRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];}
static void Start(BOOL scoped){
    Trial=[TIOSubtitleTrial new];Clock=100;TextPending=NO;Allowed=YES;UIApplication.sharedApplication.applicationState=UIApplicationStateActive;
    Trial.send=^BOOL(NSUInteger t,NSDictionary *j){return Send(t,j);};
    NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}},*s=@{@"sid":@"official",@"reason_code":@10,@"text":@""};
    assert(scoped?[Trial startRouteWithPreview:p stop:s now:Clock]:[Trial startNavigationWithPreview:p stop:s now:Clock]);
    [Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];
}
int main(void){@autoreleasepool{
 FakeBridge *b=[FakeBridge new];Plugin=b;Device=CurrentDevice=@"synthetic-device";Route=@{@"deviceId":Device};
 Start(YES);NSString *sid=Trial.sid;UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;Clock=110;assert([Trial sendNavigationText:@"real test" now:Clock]);assert(TextPending&&[b.last[@"type"] isEqual:@5]&&[b.last[@"json"][@"sid"] isEqual:sid]);
 assert(!Send(7,@{})&&!Send(5,@{}));b.completion(@YES);Drain();assert(!TextPending);Clock=120;assert([Trial sendNavigationText:@"updated test" now:Clock]);void(^oldCompletion)(id)=[b.completion copy];Trial.sid=@"foreign";oldCompletion(@YES);Drain();assert(TextPending);Trial.sid=sid;TextPending=NO;
 Allowed=NO;Clock=130;assert(!Send(5,@{}));assert(Send(3,@{@"sid":sid,@"reason_code":@10,@"text":@""}));
 Start(NO);UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;assert(!Send(5,@{}));
 Start(YES);CurrentDevice=@"different-device";assert(!Send(5,@{})&&!Send(3,@{}));CurrentDevice=Device;
 Start(YES);UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;Clock=140;assert([Trial sendNavigationText:@"failure test" now:Clock]);b.completion(@{ @"success":@NO });Drain();assert([Trial.phase isEqual:@"stopping"]);
 puts("PASS: actual native send include reaches fake bridge in background only for real route text; no background preview, single pending, SID callback guard, device mismatch/revoke and failure stop.");
}}
