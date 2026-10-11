#import <Foundation/Foundation.h>
#import "SubtitleHUDCore.h"
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
#include "SubtitleHUDLifecycle.inc"
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
    puts("PASS: actual lifecycle inactive pause / same SID resume, background/user close/audio/manual/240s protections.");
}}
