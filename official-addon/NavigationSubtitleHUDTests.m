#import "NavigationSubtitleHUD.h"
#import "SubtitleHUDCore.h"
#import "NavigationCore.h"
#include <assert.h>
static TIOSubtitleTrial *Trial;
static NSMutableArray *Sent;
static double Clock;
static BOOL Pending;
static NSUInteger Stops,RefreshEpoch;
static BOOL Foreground=YES;
NSDictionary *TIOSubtitleNavigationStatus(void){return @{@"sid":Trial.sid?:@"",@"navigation":@(Trial.navigation),@"phase":Trial.phase,@"pending":@(Pending),@"foreground":@(Foreground),@"refreshEpoch":@(RefreshEpoch),@"routeScoped":@(Trial.routeScoped),@"backgroundAllowed":@(Trial.routeScoped)};}
BOOL TIOSubtitleConfirmIdle(void){return YES;}
NSString *TIOSubtitleNavigationStart(void){
    NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}};
    return [Trial startNavigationWithPreview:p stop:@{@"sid":@"official",@"reason_code":@10,@"text":@""} now:Clock]?Trial.sid:nil;
}
NSString *TIOSubtitleRouteStart(NSString *token){
    if(![token isEqual:@"test-route"])return nil;NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}};
    return [Trial startRouteWithPreview:p stop:@{@"sid":@"official",@"reason_code":@10,@"text":@""} now:Clock]?Trial.sid:nil;
}
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text){return [sid isEqual:Trial.sid]&&[Trial sendNavigationText:text now:Clock];}
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason){if([sid isEqual:Trial.sid]){Stops++;[Trial stop:reason now:Clock];}}
static NSDictionary *F(NSInteger meters){NSMutableDictionary *f=[TIONavDisplay(@"navigating",3,@"测试道路",meters,800,600,YES) mutableCopy];f[@"segment"]=@0;return f;}
static NSDictionary *Live(NSInteger meters){NSMutableDictionary *f=[F(meters) mutableCopy];f[@"mode"]=@"实时导航 · 高德";return f;}
static void Reset(void){Trial=[TIOSubtitleTrial new];Sent=[NSMutableArray new];Clock=100;Stops=0;Pending=NO;Foreground=YES;RefreshEpoch=0;Trial.send=^BOOL(NSUInteger type,NSDictionary *j){assert(([@[@3,@5,@7] containsObject:@(type)]));[Sent addObject:@{@"type":@(type),@"json":j}];return YES;};}
static TIONavSubtitleHUD *RunningFrame(NSDictionary *frame){TIONavSubtitleHUD *h=[TIONavSubtitleHUD new];assert([h startWithFrame:frame at:Clock]);assert(Sent.count==1);[h pumpAt:Clock];assert(Sent.count==1);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];[h pumpAt:Clock];assert(Sent.count==2);return h;}
static TIONavSubtitleHUD *Running(void){return RunningFrame(F(80));}
int main(void){@autoreleasepool{
    NSMutableDictionary *live=[TIONavDisplay(@"navigating",3,@"测试道路",80,800,600,NO) mutableCopy];live[@"segment"]=@0;
    NSCAssert(TIONavSubtitleText(live)!=nil,@"Realtime phone guidance must reach subtitle formatter");
    NSString *text=TIONavSubtitleText(F(80));assert([text containsString:@"80"]&&[text lengthOfBytesUsingEncoding:NSUTF8StringEncoding]<=384);
    NSMutableDictionary *bad=[F(80) mutableCopy];bad[@"mode"]=@"步行导航";assert(!TIONavSubtitleText(bad));bad=[F(80) mutableCopy];bad[@"segment"]=@YES;assert(!TIONavSubtitleText(bad));bad[@"segment"]=@(-1);assert(!TIONavSubtitleText(bad));
    bad=[F(80) mutableCopy];bad[@"road"]=@"测试\n伪造行";assert([TIONavSubtitleText(bad) componentsSeparatedByString:@"\n"].count==4);
    assert(!TIONavSubtitleText(TIONavDisplay(@"arrived",0,@"",0,0,0,YES)));
    Reset();TIONavSubtitleHUD *h=Running();assert(![Trial nextAt:104]); // cannot use manual buttons to alter nav session
    Clock=101;[h offer:F(60) at:Clock];[h pumpAt:Clock];assert(Sent.count==2);
    Clock=102;[h offer:F(35) at:Clock];[h pumpAt:Clock];assert(Sent.count==2);
    Clock=104;[h pumpAt:Clock];assert(Sent.count==3&&[Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"35"]);
    Clock=108;[h offer:F(35) at:Clock];[h pumpAt:Clock];assert(Sent.count==3); // no unchanged resend
    Pending=YES;Clock=109;[h offer:F(20) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);
    Clock=110;[h offer:F(10) at:Clock];Pending=NO;[h pumpAt:Clock];assert(Sent.count==4&&[Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"10"]);
    assert(!TIOSubtitleNavigationText(@"wrong",text));[h stop:@"用户停止"];assert(Stops==1);[h stop:@"重复"];assert(Stops==1);
    Reset();h=Running();Clock=116;[h pumpAt:Clock];assert(Stops==1); // stale
    Reset();h=Running();Clock=101;[h offer:TIONavDisplay(@"rerouting",0,@"",-1,-1,-1,YES) at:Clock];assert(Stops==1);
    Reset();h=Running();Trial.sid=@"foreign";[h pumpAt:Clock];assert(Stops==0&&! [h.status[@"enabled"] boolValue]);
    Reset();h=Running();[Trial receive:@{@"type":@4,@"json":@{},@"binaryBytes":@200} now:101];[h pumpAt:101];assert(![h.status[@"enabled"] boolValue]);
    Reset();h=Running();Clock=340;[h offer:F(20) at:Clock];[h pumpAt:Clock];assert(Stops==1); // original 4-minute bound
    Reset();h=Running();NSString *huge=[@"x" stringByPaddingToLength:385 withString:@"x" startingAtIndex:0];assert(![Trial sendNavigationText:huge now:104]);
    Reset();assert(![Trial sendNavigationText:text now:100]);
    Reset();h=RunningFrame(Live(80));Clock=101;[h offer:TIONavDisplay(@"weak",0,@"",-1,-1,-1,NO) at:Clock];
    NSCAssert([h.status[@"enabled"] boolValue],@"Weak real navigation must show neutral status without closing the owned subtitle SID");
    assert([TIONavSubtitleText(Live(80)) hasPrefix:@"高德实时导航"]);
    assert([TIONavSubtitleText(F(80)) hasPrefix:@"高德模拟导航"]);
    for(NSString *phase in @[@"planning",@"arrived",@"stopped",@"error"]){
        Reset();h=RunningFrame(Live(80));Clock=101;[h offer:TIONavDisplay(phase,0,@"",-1,-1,-1,NO) at:Clock];assert(Stops==1&&! [h.status[@"enabled"] boolValue]);NSUInteger count=Sent.count;[h offer:Live(60) at:102];[h pumpAt:102];assert(Sent.count==count);
    }
    Reset();h=RunningFrame(Live(80));Clock=101;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(Sent.count==2);Clock=104;[h offer:Live(35) at:Clock];[h pumpAt:Clock];assert(Sent.count==3&&[Sent.lastObject[@"json"][@"content"][@"source_transcript"] hasPrefix:@"高德实时导航"]);
    Clock=108;[h offer:Live(35) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);Pending=YES;Clock=109;[h offer:Live(20) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);Pending=NO;Clock=110;[h offer:Live(10) at:Clock];[h pumpAt:Clock];assert(Sent.count==4);
    Reset();h=RunningFrame(Live(80));[Trial receive:@{@"type":@4,@"json":@{},@"binaryBytes":@200} now:101];[h pumpAt:101];assert(![h.status[@"enabled"] boolValue]);
    Reset();h=RunningFrame(Live(80));Trial.sid=@"foreign";[h pumpAt:Clock];assert(Stops==0&&! [h.status[@"enabled"] boolValue]);
    Reset();h=RunningFrame(Live(80));Clock=116;[h pumpAt:Clock];assert(Stops==0&&[h.status[@"holding"] boolValue]);assert(![Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"80"]);
    Reset();h=RunningFrame(Live(80));Clock=340;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(Stops==1);
    Reset();h=RunningFrame(Live(80));[h offer:F(60) at:101];assert(Stops==1);
    Reset();h=RunningFrame(F(80));[h offer:Live(60) at:101];assert(Stops==1);
    Reset();h=RunningFrame(Live(80));[h offer:Live(60) at:99];assert(Stops==1);
    Reset();h=RunningFrame(Live(80));[Trial stop:@"手机离开前台" now:Clock];[h pumpAt:Clock];assert(![h.status[@"enabled"] boolValue]);
    Reset();h=RunningFrame(Live(80));[h stop:@"用户停止眼镜显示"];assert(Stops==1);[h offer:Live(20) at:104];[h pumpAt:104];assert(Sent.count==3);
    for(id meters in @[@(-1),@YES,@(NAN),@"80"]){bad=[Live(80) mutableCopy];bad[@"meters"]=meters;assert(!TIONavSubtitleText(bad));}
    for(NSString *phase in @[@"waiting",@"ready",@"weak",@"stale",@"rerouting",@"invalid"]){
        Reset();h=RunningFrame(Live(80));NSString *sid=Trial.sid;Clock=104;
        [h offer:TIONavSubtitleHoldingFrame(phase) at:Clock];[h pumpAt:Clock];assert(Stops==0&&[h.status[@"enabled"] boolValue]);
        NSString *safe=Sent.lastObject[@"json"][@"content"][@"source_transcript"];assert([safe containsString:@"不提供过期转向或距离"]&&![safe containsString:@"测试道路"]&&![safe containsString:@"80"]);
        Clock=108;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert([Trial.sid isEqual:sid]&&[h.status[@"enabled"] boolValue]&&![h.status[@"holding"] boolValue]);
        assert([Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"60"]);
        assert([Sent filteredArrayUsingPredicate:[NSPredicate predicateWithFormat:@"type == 7"]].count==1);
    }
    Reset();h=RunningFrame(TIONavSubtitleHoldingFrame(@"waiting"));Clock=150;[h pumpAt:Clock];assert(Stops==0&&[h.status[@"enabled"] boolValue]);Clock=154;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(![h.status[@"holding"] boolValue]);
    Reset();h=RunningFrame(Live(80));Foreground=NO;Clock=101;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(Sent.count==2&&Stops==0);Foreground=YES;RefreshEpoch++;Clock=104;[h pumpAt:Clock];assert(Sent.count==3&&[Sent.lastObject[@"json"][@"sid"] isEqual:Trial.sid]);
    Clock=105;RefreshEpoch++;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);Clock=108;[h pumpAt:Clock];assert(Sent.count==4); // same content repaint after overlay, same SID, rate gate
    Clock=118;[h offer:Live(60) at:Clock];[h pumpAt:Clock];assert(Sent.count==5); // bounded 10s repaint, fresh callbacks only
    Reset();h=RunningFrame(Live(80));Clock=116;[h pumpAt:Clock];NSString *neutral=Sent.lastObject[@"json"][@"content"][@"source_transcript"];Clock=126;[h pumpAt:Clock];assert([Sent.lastObject[@"json"][@"content"][@"source_transcript"] isEqual:neutral]&&! [neutral containsString:@"80"]);
    Reset();h=RunningFrame(Live(80));Foreground=NO;Clock=340;[h pumpAt:Clock];assert(Stops==1); // pause never resets 240s limit
    Reset();h=RunningFrame(Live(80));bad=[Live(80) mutableCopy];bad[@"segment"]=@(-1);Clock=104;[h offer:bad at:Clock];[h pumpAt:Clock];assert(Stops==0&&[h.status[@"holding"] boolValue]);
    Reset();h=RunningFrame(Live(80));for(NSUInteger i=1;i<80;i++){Clock=100+i*3.01;[h offer:Live(80-(NSInteger)i) at:Clock];[h pumpAt:Clock];} // strict 3.2s guard means <80 frames here
    assert([h.status[@"frames"] unsignedIntegerValue]<80);Clock=341;[h pumpAt:Clock];assert(Stops==1);
    assert(!TIONavSubtitleHoldingFrame(nil)&&!TIONavSubtitleHoldingFrame(@"arrived"));
    Reset();h=RunningFrame(Live(80));RefreshEpoch++;Trial.sid=@"foreign-overlay";Clock=104;[h pumpAt:Clock];assert(Stops==0&&! [h.status[@"enabled"] boolValue]); // notification hint cannot override SID ownership
    // Preserve the provider formatter seam introduced by the independent MapKit HUD work.
    Reset();h=[[TIONavSubtitleHUD alloc]initWithFrameFormatter:^NSString *(NSDictionary *f){return [f[@"provider"] isEqual:@"test"]?@"独立来源测试文字":nil;}];assert([h startWithFrame:@{@"provider":@"test"} at:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];[h pumpAt:Clock];assert(Sent.count==2);[h offer:@{@"provider":@"test"} at:101];assert([h.status[@"enabled"] boolValue]);[h offer:@{@"provider":@"unknown"} at:102];assert(Stops==1);
    Reset();h=[TIONavSubtitleHUD new];assert([h startRouteWithFrame:Live(80) token:@"test-route" at:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];[h pumpAt:Clock];NSString *routeSID=Trial.sid;Foreground=NO;for(NSUInteger i=1;i<=100;i++){Clock=100+i*10;[h offer:Live(80) at:Clock];[Trial tick:Clock];[h pumpAt:Clock];}
    NSCAssert([Trial.sid isEqual:routeSID]&&[h.status[@"enabled"] boolValue]&&Trial.frame>80,@"Real route must survive 240 seconds and 80 submitted frames without a new SID");
    assert([Sent filteredArrayUsingPredicate:[NSPredicate predicateWithFormat:@"type == 7"]].count==1);[h stop:@"路线结束"];assert(Stops==1);
    Reset();h=[TIONavSubtitleHUD new];assert([h startRouteWithFrame:Live(80) token:@"test-route" at:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];[h pumpAt:Clock];[Trial receive:@{@"type":@4,@"json":@{},@"binaryBytes":@200} now:101];[h pumpAt:101];assert(![h.status[@"enabled"] boolValue]);
    Reset();h=[TIONavSubtitleHUD new];assert([h startRouteWithFrame:Live(80) token:@"test-route" at:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];[h pumpAt:Clock];Clock=100.5;[h offer:Live(60) at:Clock];[h pumpAt:Clock];NSCAssert(Sent.count==3,@"Changed real route guidance must pass both HUD and Core throttle at 500ms");
    Clock=100.6;[h offer:Live(50) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);Clock=100.8;[h offer:Live(40) at:Clock];[h pumpAt:Clock];assert(Sent.count==3);Clock=101;[h pumpAt:Clock];assert(Sent.count==4&&[Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"40"]);
    Pending=YES;Clock=101.5;[h offer:Live(30) at:Clock];[h pumpAt:Clock];Clock=102;[h offer:Live(20) at:Clock];[h pumpAt:Clock];assert(Sent.count==4);Pending=NO;Clock=102.1;[h pumpAt:Clock];assert(Sent.count==5&&[Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"20"]);
    Clock=102.6;[h offer:Live(20) at:Clock];[h pumpAt:Clock];assert(Sent.count==5);Clock=102.7;RefreshEpoch++;[h pumpAt:Clock];assert(Sent.count==6);
    Clock=103;[h pumpAt:Clock];assert(Sent.count==6);Clock=120;[h pumpAt:Clock];assert([h.status[@"holding"] boolValue]&&![Sent.lastObject[@"json"][@"content"][@"source_transcript"] containsString:@"20"]);
    NSLog(@"PASS: 500ms real-route dual throttle / latest-only / single pending / dedup / stale neutrality; real route >240s and >80 frames in background, same SID; neutral recovery, bounded repaint and ACK/rate/ownership/audio/stop; legacy simulation/custom formatters retain 240s limit. Mock transport, no SDK/device.");
}return 0;}
