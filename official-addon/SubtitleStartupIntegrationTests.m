// Drives the production HUD -> Core -> native Send include -> async bridge double.
#define main ExistingSendRegressionMain
#import "SubtitleHUDSendTests.m"
#undef main
#import "NavigationSubtitleHUD.h"
#import "NavigationCore.h"
NSDictionary *TIOSubtitleNavigationStatus(void){return @{@"sid":Trial.sid?:@"",@"phase":Trial.phase?:@"idle",@"navigation":@(Trial.navigation),@"routeScoped":@(Trial.routeScoped),@"foreground":@YES,@"pending":@(TextPending),@"backgroundAllowed":@(RouteCanContinue()),@"note":Trial.note?:@""};}
BOOL TIOSubtitleConfirmIdle(void){return YES;}
NSString *TIOSubtitleNavigationStart(void){return nil;}
NSString *TIOSubtitleRouteStart(NSString *token){
    if(![token isEqual:@"fixture-route"])return nil;
    NSDictionary *p=@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}},*s=@{@"sid":@"official",@"reason_code":@10,@"text":@""};
    return [Trial startRouteWithPreview:p stop:s now:Clock]?Trial.sid:nil;
}
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text){return [Trial.sid isEqual:sid]&&!TextPending&&[Trial sendNavigationText:text now:Clock];}
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason){if([Trial.sid isEqual:sid])[Trial stop:reason now:Clock];}
static TIONavSubtitleHUD *Begin(FakeBridge **bridge){
    *bridge=[FakeBridge new];Plugin=*bridge;Device=CurrentDevice=@"synthetic-device";Route=@{@"deviceId":Device};UIApplication.sharedApplication.applicationState=UIApplicationStateActive;
    Clock=100;Trial=[TIOSubtitleTrial new];TextPending=NO;Allowed=YES;Trial.send=^BOOL(NSUInteger t,NSDictionary *j){return Send(t,j);};
    TIONavSubtitleHUD *h=[TIONavSubtitleHUD new];assert([h startRouteWithFrame:TIONavSubtitleHoldingFrame(@"waiting") token:@"fixture-route" at:Clock]);assert([(*bridge).last[@"type"] isEqual:@7]);return h;
}
int main(void){@autoreleasepool{
    FakeBridge *b;TIONavSubtitleHUD *h=Begin(&b);NSString *sid=Trial.sid;
    b.completion(@YES);Drain();Clock=100.1;[h pumpAt:Clock];assert(b.sends==1&&Trial.frame==0&&[Trial.phase isEqual:@"starting"]); // SDK submit success is not device settings ACK
    [Trial receive:@{@"type":@8,@"json":@{@"sid":@"foreign",@"code":@1}} now:Clock];[h pumpAt:Clock];assert(b.sends==1);
    Clock=100.2;[Trial receive:@{@"type":@8,@"json":@{@"sid":sid,@"code":@1}} now:Clock];[h pumpAt:Clock];assert(b.sends==2&&[b.last[@"type"] isEqual:@5]&&[b.last[@"json"][@"sid"] isEqual:sid]&&TextPending);
    assert([b.last[@"json"][@"content"][@"source_transcript"] containsString:@"等待定位与首条导航指引"]);
    b.completion(@YES);Drain();assert(!TextPending);
    NSMutableDictionary *f=[TIONavDisplay(@"navigating",3,@"测试道路",80,800,600,NO) mutableCopy];f[@"segment"]=@0;Clock=100.7;[h offer:f at:Clock];[h pumpAt:Clock];assert(b.sends==3&&[b.last[@"json"][@"content"][@"source_transcript"] containsString:@"80"]&&[h.status[@"enabled"] boolValue]);
    h=Begin(&b);Clock=111;[Trial tick:Clock];[h pumpAt:Clock];assert(Trial.frame==0&&![h.status[@"enabled"] boolValue]&&[h.status[@"note"] containsString:@"10秒"]);
    h=Begin(&b);sid=Trial.sid;[Trial receive:@{@"type":@8,@"json":@{@"sid":sid,@"code":@1}} now:Clock];[h pumpAt:Clock];b.completion(@{@"success":@NO});Drain();[h pumpAt:Clock];assert(![h.status[@"enabled"] boolValue]&&[h.status[@"note"] containsString:@"SDK拒绝提交"]);
    puts("PASS: real startup HUD/Core/native Send + async bridge: preview alone submits no guidance; own settings ACK opens first type5 neutral then real text, SDK completion != lens ACK; no-ACK/rejection preserves exact stop cause. Firmware rendering not reproduced.");
}}
