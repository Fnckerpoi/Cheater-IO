#import "MapKitSubtitleHUD.h"
#import "../NavigationSubtitleHUD.h"
#import "../NavigationCore.h"
#import <CoreFoundation/CoreFoundation.h>
#include <math.h>

static BOOL Number(id value){return [value isKindOfClass:NSNumber.class]&&CFGetTypeID((__bridge CFTypeRef)value)!=CFBooleanGetTypeID()&&isfinite([value doubleValue]);}
static BOOL Revision(id value){return Number(value)&&[value doubleValue]>=1&&[value doubleValue]<=9007199254740991.0&&floor([value doubleValue])==[value doubleValue];}
static NSString *Distance(id value){if(!Number(value)||[value doubleValue]<0||[value doubleValue]>10000000)return @"距离未知";double meters=[value doubleValue];return meters>=1000?[NSString stringWithFormat:@"%.1f公里",meters/1000]:[NSString stringWithFormat:@"%.0f米",meters];}
static BOOL Shape(NSDictionary *frame){
    if(![frame isKindOfClass:NSDictionary.class])return NO;
    id simulation=frame[@"simulated"],session=frame[@"sessionId"];
    return [simulation isKindOfClass:NSNumber.class]&&CFGetTypeID((__bridge CFTypeRef)simulation)==CFBooleanGetTypeID()&&[simulation boolValue]&&[session isKindOfClass:NSString.class]&&[[NSUUID alloc]initWithUUIDString:session]&&Revision(frame[@"revision"])&&Number(frame[@"updatedAt"])&&[frame[@"updatedAt"] doubleValue]>=0&&[frame[@"provider"] isEqual:@"mapkit"]&&[@[@"walk",@"ride",@"drive"] containsObject:frame[@"mode"]]&&[frame[@"phase"] isEqual:@"navigating"]&&[frame[@"turn"] isKindOfClass:NSString.class]&&[frame[@"instruction"] isKindOfClass:NSString.class];
}
NSString *TIOMapKitSubtitleText(NSDictionary *frame){
    if(!Shape(frame))return nil;
    NSDictionary *arrows=@{@"left":@"←",@"slightLeft":@"←",@"sharpLeft":@"←",@"right":@"→",@"slightRight":@"→",@"sharpRight":@"→",@"straight":@"↑"};
    NSMutableCharacterSet *controls=[NSCharacterSet.controlCharacterSet mutableCopy];[controls formUnionWithCharacterSet:NSCharacterSet.newlineCharacterSet];[controls formUnionWithCharacterSet:NSCharacterSet.illegalCharacterSet];
    NSString *instruction=TIONavClip([[frame[@"instruction"] componentsSeparatedByCharactersInSet:controls] componentsJoinedByString:@" "],180);
    if(![instruction stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet].length)instruction=@"请查看手机指引";
    return [NSString stringWithFormat:@"MapKit模拟·非实际定位\n%@ %@\n%@\n剩余 %@ · 以手机地图为准",arrows[frame[@"turn"]]?:@"?",Distance(frame[@"distance_m"]),instruction,Distance(frame[@"remaining_m"])];
}
static BOOL Fresh(NSDictionary *frame,NSTimeInterval now){return Shape(frame)&&isfinite(now)&&now>=[frame[@"updatedAt"] doubleValue]&&now-[frame[@"updatedAt"] doubleValue]<=15;}
static NSDictionary *Snapshot(NSDictionary *frame){
    NSMutableDictionary *copy=[NSMutableDictionary new];
    for(NSString *key in @[@"simulated",@"sessionId",@"revision",@"provider",@"mode",@"phase",@"turn",@"instruction",@"distance_m",@"remaining_m",@"updatedAt"]){id value=frame[key];if([value isKindOfClass:NSString.class]||[value isKindOfClass:NSNumber.class])copy[key]=[value copy];}
    return [copy copy];
}
@implementation TIOMapKitSubtitleHUD {
    TIONavSubtitleHUD *_hud;
    NSDictionary *_frame;
    NSString *_sid,*_note;
    NSUInteger _frames;
    BOOL _enabled;
}
- (instancetype)init{if((self=[super init]))_note=@"MapKit模拟字幕未开启；需明确确认镜片空闲";return self;}
- (NSDictionary *)status{
    NSDictionary *transport=TIOSubtitleNavigationStatus();BOOL owner=_enabled&&[transport[@"sid"] isEqual:_sid]&&[transport[@"navigation"] boolValue];
    BOOL active=owner&&[@[@"starting",@"ready"] containsObject:transport[@"phase"]];
    return @{@"version":@"mapkit-stock-text-v1",@"enabled":@(_enabled),@"active":@(active),@"ready":@(active&&[transport[@"phase"] isEqual:@"ready"]&&[transport[@"available"] boolValue]),@"pending":@(owner&&[transport[@"pending"] boolValue]),@"frames":_hud?_hud.status[@"frames"]:@(_frames),@"note":_note?:@"",@"transport":@{@"phase":transport[@"phase"]?:@"unknown",@"available":transport[@"available"]?:@NO,@"pending":transport[@"pending"]?:@NO,@"owned":@(owner),@"note":transport[@"note"]?:@""}};
}
- (BOOL)startWithFrame:(NSDictionary *)frame at:(NSTimeInterval)now{
    if(_enabled)return NO;
    if(!Fresh(frame,now)){_note=@"拒绝：仅支持当前 MapKit 模拟帧（有效会话／版本／15秒时间戳）";return NO;}
    TIONavSubtitleHUD *hud=[[TIONavSubtitleHUD alloc]initWithFrameFormatter:^NSString *(NSDictionary *f){return TIOMapKitSubtitleText(f);}];
    NSDictionary *snapshot=Snapshot(frame);
    if(![hud startWithFrame:snapshot at:now]){_note=hud.status[@"note"];return NO;}
    NSDictionary *transport=TIOSubtitleNavigationStatus();
    _hud=hud;_sid=[transport[@"sid"] copy];_frame=snapshot;_enabled=YES;_frames=0;_note=hud.status[@"note"];
    if(!_sid.length||![transport[@"navigation"] boolValue]){[self stop:@"字幕会话所有权不可确认，不自动重开"];return NO;}return YES;
}
- (void)offer:(NSDictionary *)frame at:(NSTimeInterval)now{
    if(!_enabled||![frame isKindOfClass:NSDictionary.class]||![frame[@"sessionId"] isEqual:_frame[@"sessionId"]]||!Revision(frame[@"revision"])||[frame[@"revision"] doubleValue]<=[_frame[@"revision"] doubleValue])return;
    if(!Fresh(frame,now)||![frame[@"mode"] isEqual:_frame[@"mode"]]||[frame[@"updatedAt"] doubleValue]<[_frame[@"updatedAt"] doubleValue]){[self stop:@"当前模拟帧无效／弱信号／重算／到达，停止旧指引且不自动恢复"];return;}
    _frame=Snapshot(frame);[_hud offer:_frame at:now];
}
- (void)pumpAt:(NSTimeInterval)now{
    if(!_enabled)return;
    NSDictionary *transport=TIOSubtitleNavigationStatus();
    if(![transport[@"sid"] isEqual:_sid]||![transport[@"navigation"] boolValue]||![@[@"starting",@"ready"] containsObject:transport[@"phase"]]){[self stop:@"字幕会话已退出或由其他任务接管，不自动重开"];return;}
    if(![transport[@"available"] boolValue]){[self stop:@"前台连接／设备／字幕格式不可用，不自动重开"];return;}
    if(!Fresh(_frame,now)){[self stop:@"模拟帧过期／未来时间戳，停止旧指引"];return;}
    [_hud pumpAt:now];_frames=[_hud.status[@"frames"] unsignedIntegerValue];_note=_hud.status[@"note"];
    if(![_hud.status[@"enabled"] boolValue]){_enabled=NO;_frame=nil;_sid=nil;_hud=nil;}
}
- (void)stop:(NSString *)reason{
    if(!_enabled)return;
    _frames=[_hud.status[@"frames"] unsignedIntegerValue];
    NSDictionary *transport=TIOSubtitleNavigationStatus();
    if([transport[@"sid"] isEqual:_sid]&&[transport[@"navigation"] boolValue])[_hud stop:reason?:@"MapKit模拟字幕停止"];
    _enabled=NO;_hud=nil;_frame=nil;_sid=nil;_note=[NSString stringWithFormat:@"%@；镜片退出需确认，不自动恢复",reason?:@"MapKit模拟字幕停止"];
}
@end
