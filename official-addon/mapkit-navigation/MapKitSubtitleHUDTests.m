#import "MapKitSubtitleHUD.h"
#import "../NavigationSubtitleHUD.h"
#import "../SubtitleHUDCore.h"
#import "../NavigationCore.h"
#include <assert.h>
#include <math.h>
static TIOSubtitleTrial *Trial;
static NSMutableArray *Sent;
static double Clock;
static BOOL Pending, Confirmed, Foreground, Available;
static NSUInteger Stops, StopRequests, Confirmations;
NSDictionary *TIOSubtitleNavigationStatus(void){return @{ @"sid":Trial.sid?:@"",@"navigation":@(Trial.navigation),@"phase":Trial.phase,@"pending":@(Pending),@"available":@(Available&&Foreground),@"note":Trial.note?:@""};}
BOOL TIOSubtitleConfirmIdle(void){Confirmations++;Confirmed=YES;return YES;}
NSString *TIOSubtitleNavigationStart(void){
    if(!Confirmed||!Foreground||!Available||Trial.active)return nil;
    Confirmed=NO;
    return [Trial startNavigationWithPreview:@{@"sid":@"official",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}} stop:@{@"sid":@"official",@"reason_code":@10,@"text":@""} now:Clock]?Trial.sid:nil;
}
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text){return Foreground&&Available&&!Pending&&[sid isEqual:Trial.sid]&&[Trial sendNavigationText:text now:Clock];}
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason){StopRequests++;if([sid isEqual:Trial.sid]){Stops++;[Trial stop:reason now:Clock];}}
static NSDictionary *F(NSUInteger revision,NSInteger distance){return @{@"simulated":@YES,@"sessionId":@"12345678-1234-1234-1234-123456789012",@"revision":@(revision),@"provider":@"mapkit",@"mode":@"walk",@"phase":@"navigating",@"turn":@"right",@"instruction":@"Turn right onto Sample Street",@"distance_m":@(distance),@"remaining_m":@800,@"updatedAt":@(Clock)};}
static void Reset(void){Trial=[TIOSubtitleTrial new];Sent=[NSMutableArray new];Clock=100;Pending=Confirmed=NO;Foreground=Available=YES;Stops=StopRequests=Confirmations=0;Trial.send=^BOOL(NSUInteger type,NSDictionary *json){assert(([@[@3,@5,@7] containsObject:@(type)]));assert(TIOSubtitlePacket(type,json));[Sent addObject:@{@"type":@(type),@"json":json}];return YES;};}
static NSString *LastText(void){return Sent.lastObject[@"json"][@"content"][@"source_transcript"];}
static void ACK(void){[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@1}} now:Clock];}
static TIOMapKitSubtitleHUD *Running(void){assert(TIOSubtitleConfirmIdle());TIOMapKitSubtitleHUD *h=[TIOMapKitSubtitleHUD new];assert([h startWithFrame:F(1,80) at:Clock]);assert(Sent.count==1);[h pumpAt:Clock];assert(Sent.count==1);ACK();[h pumpAt:Clock];assert(Sent.count==2&&[h.status[@"ready"] boolValue]);return h;}
int main(void){@autoreleasepool{
    Reset();assert(TIOSubtitleConfirmIdle());
    TIOMapKitSubtitleHUD *hud=[TIOMapKitSubtitleHUD new];
    assert([hud startWithFrame:F(1,80) at:Clock]);
    assert(Sent.count==1&&[Sent[0][@"type"] isEqual:@7]);
    assert(![Sent[0][@"json"][@"sid"] isEqual:F(1,80)[@"sessionId"]]&&[Sent[0][@"json"][@"scope"] isEqual:@"temporary"]&&[Sent[0][@"json"][@"force"] isEqual:@NO]);
    [Trial receive:@{@"type":@8,@"json":@{@"sid":@"foreign-ack",@"code":@1}} now:Clock];
    [hud pumpAt:Clock];assert(Sent.count==1);ACK();[hud pumpAt:Clock];
    assert(Sent.count==2&&[LastText() isEqual:@"MapKit模拟·非实际定位\n→ 80米\nTurn right onto Sample Street\n剩余 800米 · 以手机地图为准"]);
    assert([Sent.lastObject[@"json"][@"mode"] isEqual:@3]&&[Sent.lastObject[@"json"][@"status"] isEqual:@0]);
    Reset();hud=[TIOMapKitSubtitleHUD new];assert(![hud startWithFrame:F(1,80) at:Clock]&&Confirmations==0&&Sent.count==0);
    for(NSString *key in @[@"simulated",@"sessionId",@"revision",@"provider",@"mode",@"phase",@"updatedAt"]){NSMutableDictionary *bad=[F(1,80) mutableCopy];[bad removeObjectForKey:key];assert(![hud startWithFrame:bad at:Clock]&&Sent.count==0);}
    for(id revision in @[@YES,@0,@(-1),@1.5,@(NAN),@(INFINITY),@9007199254740992ULL,@"2"]){NSMutableDictionary *bad=[F(1,80) mutableCopy];bad[@"revision"]=revision;assert(![hud startWithFrame:bad at:Clock]);}
    for(id simulated in @[@NO,@1,@"YES"]){NSMutableDictionary *bad=[F(1,80) mutableCopy];bad[@"simulated"]=simulated;assert(!TIOMapKitSubtitleText(bad));}
    for(id timestamp in @[@YES,@(-1),@84,@100.1,@(NAN),@"100"]){NSMutableDictionary *bad=[F(1,80) mutableCopy];bad[@"updatedAt"]=timestamp;assert(![hud startWithFrame:bad at:Clock]);}
    for(NSString *key in @[@"sessionId",@"provider",@"mode",@"phase"]){NSMutableDictionary *bad=[F(1,80) mutableCopy];bad[key]=@"invalid";assert(!TIOMapKitSubtitleText(bad));}
    NSMutableDictionary *unknown=[F(1,-1) mutableCopy];unknown[@"turn"]=@"unknown";unknown[@"remaining_m"]=NSNull.null;unknown[@"instruction"]=@"";
    assert([TIOMapKitSubtitleText(unknown) isEqual:@"MapKit模拟·非实际定位\n? 距离未知\n请查看手机指引\n剩余 距离未知 · 以手机地图为准"]);
    for(NSString *turn in @[@"left",@"slightLeft",@"sharpLeft",@"right",@"slightRight",@"sharpRight",@"straight",@"roundabout",@"uturn",@"unrecognized"]){unknown[@"turn"]=turn;NSString *text=TIOMapKitSubtitleText(unknown);assert(text&&[text componentsSeparatedByString:@"\n"].count==4&&[text lengthOfBytesUsingEncoding:NSUTF8StringEncoding]<=384);}
    unknown[@"instruction"]=[@"道路\n\r\t\u2028\u2029\u202e伪行" stringByPaddingToLength:1000 withString:@"道路" startingAtIndex:0];NSString *clean=TIOMapKitSubtitleText(unknown);
    assert([clean componentsSeparatedByString:@"\n"].count==4&&[clean lengthOfBytesUsingEncoding:NSUTF8StringEncoding]<=384&&![clean containsString:@"\u202e"]);
    Reset();hud=Running();assert(![Trial nextAt:104]);Clock=101;[hud offer:F(2,60) at:Clock];[hud pumpAt:Clock];Clock=102;[hud offer:F(3,35) at:Clock];[hud pumpAt:Clock];assert(Sent.count==2);
    Clock=103.19;[hud pumpAt:Clock];assert(Sent.count==2);Clock=103.21;[hud pumpAt:Clock];assert(Sent.count==3&&[LastText() containsString:@"35米"]);
    Clock=107;[hud offer:F(4,35) at:Clock];[hud pumpAt:Clock];assert(Sent.count==3);
    Pending=YES;Clock=108;[hud offer:F(5,20) at:Clock];[hud pumpAt:Clock];assert(Sent.count==3&&[hud.status[@"pending"] boolValue]);Clock=109;[hud offer:F(6,10) at:Clock];Pending=NO;[hud pumpAt:Clock];assert(Sent.count==4&&[LastText() containsString:@"10米"]);
    assert(!TIOSubtitleNavigationText(@"foreign",LastText()));[hud stop:@"取消"];[hud stop:@"重复取消"];assert(Stops==1&&[Sent.lastObject[@"type"] isEqual:@3]);[hud offer:F(7,5) at:Clock];[hud pumpAt:Clock];assert(Sent.count==5&&! [hud.status[@"enabled"] boolValue]);
    Reset();hud=Running();Clock=101;NSMutableDictionary *foreign=[F(99,1) mutableCopy];foreign[@"sessionId"]=@"87654321-1234-1234-1234-123456789012";foreign[@"phase"]=@"arrived";[hud offer:foreign at:Clock];[hud offer:F(1,1) at:Clock];assert([hud.status[@"enabled"] boolValue]&&Stops==0);Clock=116;[hud pumpAt:Clock];assert(Stops==1);
    Reset();hud=Running();Clock=101;[hud offer:F(2,60) at:Clock];Clock=104;[hud offer:F(1,1) at:Clock];[hud pumpAt:Clock];assert([LastText() containsString:@"60米"]);
    for(NSString *phase in @[@"weak",@"stale",@"rerouting",@"arrived",@"stopped",@"error"]){Reset();hud=Running();Clock=101;NSMutableDictionary *bad=[F(2,60) mutableCopy];bad[@"phase"]=phase;[hud offer:bad at:Clock];assert(Stops==1&&![hud.status[@"enabled"] boolValue]);[hud offer:F(3,20) at:Clock];[hud pumpAt:Clock];assert(Sent.count==3);}
    for(NSString *key in @[@"provider",@"mode",@"simulated",@"updatedAt"]){Reset();hud=Running();Clock=101;NSMutableDictionary *bad=[F(2,60) mutableCopy];bad[key]=[key isEqual:@"simulated"]?@NO:[key isEqual:@"updatedAt"]?@102:@"invalid";[hud offer:bad at:Clock];assert(Stops==1);}
    Reset();hud=Running();Clock=101;NSMutableDictionary *older=[F(2,60) mutableCopy];older[@"updatedAt"]=@99;[hud offer:older at:Clock];assert(Stops==1);
    Reset();hud=Running();Trial.sid=@"foreign-owner";[hud pumpAt:Clock];[hud stop:@"取消"];assert(Stops==0&&StopRequests==0&&Sent.count==2&&![hud.status[@"enabled"] boolValue]);
    Reset();hud=Running();Trial.phase=@"uncertain";[hud pumpAt:Clock];assert(![hud.status[@"enabled"] boolValue]);
    Reset();hud=Running();[Trial receive:@{@"type":@4,@"json":@{},@"binaryBytes":@200} now:Clock];[hud pumpAt:Clock];assert(![hud.status[@"enabled"] boolValue]&&Trial.audioPackets==1);for(NSDictionary *event in Sent)assert(![event[@"type"] isEqual:@1]&&![event[@"type"] isEqual:@4]);
    Reset();hud=Running();Foreground=NO;[hud pumpAt:Clock];assert(Stops==1&&! [hud.status[@"enabled"] boolValue]);
    Reset();hud=Running();Available=NO;[hud pumpAt:Clock];assert(Stops==1);
    Reset();Available=NO;assert(TIOSubtitleConfirmIdle());hud=[TIOMapKitSubtitleHUD new];assert(![hud startWithFrame:F(1,80) at:Clock]&&Sent.count==0);
    Reset();assert(TIOSubtitleConfirmIdle());hud=[TIOMapKitSubtitleHUD new];assert([hud startWithFrame:F(1,80) at:Clock]);[Trial receive:@{@"type":@8,@"json":@{@"sid":Trial.sid,@"code":@YES}} now:Clock];[hud pumpAt:Clock];assert(Sent.count==2&&![hud.status[@"enabled"] boolValue]);
    Reset();hud=Running();Clock=340;[hud offer:F(2,20) at:Clock];[hud pumpAt:Clock];assert(Stops==1&&[hud.status[@"note"] containsString:@"4分钟"]);
    Reset();assert(TIOSubtitleConfirmIdle());hud=[TIOMapKitSubtitleHUD new];NSMutableString *instruction=[@"原始道路" mutableCopy],*sid=[F(1,80)[@"sessionId"] mutableCopy];NSMutableDictionary *mutable=[F(1,80) mutableCopy];mutable[@"instruction"]=instruction;mutable[@"sessionId"]=sid;assert([hud startWithFrame:mutable at:Clock]);[instruction setString:@"篡改道路"];[sid setString:@"foreign"];mutable[@"simulated"]=@NO;ACK();[hud pumpAt:Clock];assert([LastText() containsString:@"原始道路"]&&![LastText() containsString:@"篡改道路"]);Clock=104;[hud offer:F(2,60) at:Clock];[hud pumpAt:Clock];assert([LastText() containsString:@"60米"]);
    Reset();hud=Running();Clock=101;NSMutableString *offeredInstruction=[@"最新道路" mutableCopy];NSMutableDictionary *offered=[F(2,60) mutableCopy];offered[@"instruction"]=offeredInstruction;[hud offer:offered at:Clock];[offeredInstruction setString:@"篡改"];offered[@"updatedAt"]=@999;Clock=104;[hud pumpAt:Clock];assert([LastText() containsString:@"最新道路"]&&![LastText() containsString:@"篡改"]);
    Reset();hud=Running();Clock=101;NSMutableDictionary *badRevision=[F(2,60) mutableCopy];badRevision[@"revision"]=@YES;[hud offer:badRevision at:Clock];assert(Stops==0);Clock=116;[hud pumpAt:Clock];assert(Stops==1);
    Reset();assert(TIOSubtitleConfirmIdle());hud=[TIOMapKitSubtitleHUD new];NSMutableDictionary *boundary=[F(1,80) mutableCopy];boundary[@"updatedAt"]=@85;assert([hud startWithFrame:boundary at:Clock]);ACK();[hud pumpAt:Clock];assert(Sent.count==2);Clock=100.01;[hud pumpAt:Clock];assert(Stops==1);
    Reset();hud=Running();[hud pumpAt:NAN];assert(Stops==1&&! [hud.status[@"enabled"] boolValue]);
    Reset();assert(TIOSubtitleConfirmIdle());hud=[TIOMapKitSubtitleHUD new];unknown=[F(1,-1) mutableCopy];unknown[@"turn"]=@"unknown";assert([hud startWithFrame:unknown at:Clock]);ACK();[hud pumpAt:Clock];assert([LastText() containsString:@"? 距离未知"]);
    Reset();hud=Running();for(NSUInteger revision=2;revision<100&&[hud.status[@"enabled"] boolValue];revision++){Clock=100+3.21*(revision-1);[hud offer:F(revision,100-revision) at:Clock];[hud pumpAt:Clock];}assert(Stops==1&&[hud.status[@"frames"] unsignedIntegerValue]<=80&&[hud.status[@"note"] containsString:@"4分钟"]);
    Reset();NSMutableDictionary *amap=[TIONavDisplay(@"navigating",3,@"测试道路",80,800,600,YES) mutableCopy];amap[@"segment"]=@0;
    assert([TIONavSubtitleText(amap) isEqual:@"高德模拟导航\n前方右转  80 米\n进入 测试道路\n以手机地图为准"]);TIONavSubtitleHUD *old=[TIONavSubtitleHUD new];assert(([old.status isEqual:@{@"version":@"nav-subtitle-v1",@"enabled":@NO,@"frames":@0,@"note":@"字幕导航未开启"}]));assert(![old startWithFrame:F(1,80) at:Clock]);assert(TIOSubtitleConfirmIdle());assert([old startWithFrame:amap at:Clock]);ACK();[old pumpAt:Clock];assert([LastText() hasPrefix:@"高德模拟导航"]);
    NSLog(@"PASS: MapKit simulation -> actual SubtitleHUDCore ACK8/code1; only types3/5/7; latest/rate/pending/stale/unknown/foreign/default-AMap/cancel/audio/runtime/simulation/mutable-input");
}return 0;}
