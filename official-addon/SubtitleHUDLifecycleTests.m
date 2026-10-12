#import <Foundation/Foundation.h>
#include <assert.h>
#import "SubtitleHUDCore.h"
#import "NavigationRouteSession.h"
static NSString *const UIApplicationWillResignActiveNotification=@"will-resign";
static NSString *const UIApplicationDidBecomeActiveNotification=@"did-become";
static NSString *const UIApplicationDidEnterBackgroundNotification=@"did-enter-background";
static TIOSubtitleTrial *Trial;
static BOOL ConfirmedIdle,ForegroundSuppressed;
static NSUInteger SubtitleRefreshEpoch;
static NSTimeInterval Clock;
static NSMutableArray *Sent;
static NSTimeInterval Now(void){return Clock;}
static void Save(void){}
static BOOL RouteAllowed;
static BOOL RouteCanContinue(void){return Trial.routeScoped&&RouteAllowed;}
#include "SubtitleHUDLifecycle.inc"
#include "SubtitleHUDSendPolicy.inc"
static void Start(BOOL navigation){
    Clock=100;ConfirmedIdle=NO;ForegroundSuppressed=NO;SubtitleRefreshEpoch=0;Trial=[TIOSubtitleTrial new];Sent=[NSMutableArray new];
    Trial.send=^BOOL(NSUInteger t,NSDictionary *j){[Sent addObject:@{@"type":@(t),@"json":j}];return YES;};
    NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}},*s=@{@"sid":@"official",@"reason_code":@10,@"text":@""};
    BOOL ok=navigation?[Trial startNavigationWithPreview:p stop:s now:Clock]:[Trial startWithPreview:p stop:s now:Clock];NSCAssert(ok,@"start");
    [Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];NSCAssert([Trial.phase isEqual:@"ready"],@"matching ACK");
}
int main(void){@autoreleasepool{
    Start(YES);NSString *sid=Trial.sid;HandleSubtitleLifecycle(UIApplicationWillResignActiveNotification);
    NSCAssert([Trial.phase isEqual:@"ready"]&&[Trial.sid isEqual:sid]&&Sent.count==1,@"Transient system notification must not close navigation subtitles");
    NSCAssert(ForegroundSuppressed,@"inactive pauses sends");Clock=102;HandleSubtitleLifecycle(UIApplicationDidBecomeActiveNotification);NSCAssert(!ForegroundSuppressed&&SubtitleRefreshEpoch==1&&[Trial.sid isEqual:sid]&&Sent.count==1,@"same SID, no preview replay");
    HandleSubtitleLifecycle(UIApplicationDidEnterBackgroundNotification);NSCAssert([Trial.phase isEqual:@"stopping"]&&Sent.count==2,@"real background still stops");HandleSubtitleLifecycle(UIApplicationDidBecomeActiveNotification);NSCAssert([Trial.phase isEqual:@"stopping"]&&Sent.count==2,@"no restart after background exit");
    Start(YES);HandleSubtitleLifecycle(@"TIOResearchClosed");NSCAssert([Trial.phase isEqual:@"stopping"],@"research close stops");
    Start(NO);HandleSubtitleLifecycle(UIApplicationWillResignActiveNotification);NSCAssert([Trial.phase isEqual:@"stopping"],@"manual experiment retains old stricter protection");
    Start(YES);HandleSubtitleLifecycle(UIApplicationWillResignActiveNotification);Clock=102;HandleSubtitleLifecycle(UIApplicationDidBecomeActiveNotification);[Trial tick:340];NSCAssert([Trial.phase isEqual:@"stopping"]&&Trial.began==100,@"transient pause cannot extend deadline");
    Start(YES);[Trial receive:@{@"type":@4,@"json":@{},@"binaryBytes":@10} now:101];HandleSubtitleLifecycle(UIApplicationDidBecomeActiveNotification);NSCAssert([Trial.phase isEqual:@"stopping"]&&SubtitleRefreshEpoch==0,@"audio stop cannot auto recover");
    Start(YES);Trial.phase=@"idle";NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}},*s=@{@"sid":@"official",@"reason_code":@10,@"text":@""};
    assert([Trial startRouteWithPreview:p stop:s now:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];RouteAllowed=YES;
    HandleSubtitleLifecycle(UIApplicationDidEnterBackgroundNotification);
    NSCAssert([Trial.phase isEqual:@"ready"],@"Authorized real route must keep its owned subtitle session in background");
    assert(SubtitleCanSend(5,NO)&&!SubtitleCanSend(7,NO)&&SubtitleCanSend(3,NO));
    [Trial tick:Clock+1000];assert([Trial.phase isEqual:@"ready"]);RouteAllowed=NO;assert(!SubtitleCanSend(5,NO));HandleSubtitleLifecycle(UIApplicationDidEnterBackgroundNotification);assert([Trial.phase isEqual:@"stopping"]);
    puts("PASS: actual lifecycle: scoped authorized route continues in background beyond 240s; revoked route stops; legacy navigation/manual close/background/audio protections.");
}}
