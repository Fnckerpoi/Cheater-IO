#import "NavigationSubtitleHUD.h"
#import "NavigationCore.h"
#include <math.h>

static NSString *HoldingTitle(NSString *phase){
    if(![phase isKindOfClass:NSString.class])return nil;
    return @{@"ready":@"等待首条导航指引",@"waiting":@"等待定位与首条导航指引",@"weak":@"定位信号弱",@"rerouting":@"正在重新规划路线",@"stale":@"导航指引暂未更新",@"invalid":@"导航数据暂不可用"}[phase];
}
NSDictionary *TIONavSubtitleHoldingFrame(NSString *phase){
    if(!HoldingTitle(phase))return nil;
    return @{@"phase":phase,@"mode":@"实时导航 · 高德",@"turn":HoldingTitle(phase),@"distance":@"",@"road":@"不提供过期转向或距离，请查看手机与路况",@"summary":@"等待有效指引，字幕会话保留",@"meters":@(-1)};
}
BOOL TIONavSubtitleIsHolding(NSDictionary *f){return [f isKindOfClass:NSDictionary.class]&&[f[@"mode"] isEqual:@"实时导航 · 高德"]&&[f[@"phase"] isKindOfClass:NSString.class]&&HoldingTitle(f[@"phase"])!=nil;}
NSString *TIONavSubtitleText(NSDictionary *f){
    if(TIONavSubtitleIsHolding(f))return [NSString stringWithFormat:@"高德实时导航\n%@\n不提供过期转向或距离\n请查看手机与实际路况",HoldingTitle(f[@"phase"])];
    if(![f isKindOfClass:NSDictionary.class]||![f[@"phase"] isEqual:@"navigating"]||![@[@"模拟导航 · 非实际定位",@"实时导航 · 高德"] containsObject:f[@"mode"]?:@""])return nil;
    NSNumber *meters=f[@"meters"];if(![meters isKindOfClass:NSNumber.class]||CFGetTypeID((__bridge CFTypeRef)meters)==CFBooleanGetTypeID()||!isfinite(meters.doubleValue)||meters.doubleValue<0)return nil;
    NSNumber *s=f[@"segment"];if(![s isKindOfClass:NSNumber.class]||CFGetTypeID((__bridge CFTypeRef)s)==CFBooleanGetTypeID()||!isfinite(s.doubleValue)||s.doubleValue<0||s.doubleValue>100000||s.doubleValue!=s.integerValue)return nil;
    for(NSString *k in @[@"turn",@"road",@"distance"])if(![f[k] isKindOfClass:NSString.class]||![f[k] length]||[f[k] lengthOfBytesUsingEncoding:NSUTF8StringEncoding]>240)return nil;
    if([f[@"distance"] containsString:@"—"])return nil;
    NSString *(^clean)(NSString *,NSUInteger)=^NSString *(NSString *v,NSUInteger n){return TIONavClip([[v componentsSeparatedByCharactersInSet:NSCharacterSet.controlCharacterSet] componentsJoinedByString:@" "],n);};
    NSString *title=[f[@"mode"] isEqual:@"实时导航 · 高德"]?@"高德实时导航":@"高德模拟导航";
    return [NSString stringWithFormat:@"%@\n%@  %@\n%@\n以手机地图为准",title,clean(f[@"turn"],72),clean(f[@"distance"],36),clean(f[@"road"],120)];
}
@implementation TIONavSubtitleHUD {
    BOOL _enabled,_amapFrames,_needsRefresh,_holding;
    NSString *(^_formatter)(NSDictionary *);
    NSString *_sid,*_latest,*_sent,*_note,*_mode,*_phase;
    NSTimeInterval _latestAt,_began,_lastSent;
    NSUInteger _frames,_seenRefresh;
}
- (instancetype)init{if((self=[self initWithFrameFormatter:^NSString *(NSDictionary *frame){return TIONavSubtitleText(frame);}]))_amapFrames=YES;return self;}
- (instancetype)initWithFrameFormatter:(NSString *(^)(NSDictionary *))formatter{if(!formatter)return nil;if((self=[super init])){_formatter=[formatter copy];_note=@"字幕导航未开启";}return self;}
- (BOOL)isRealtime{return _amapFrames&&[_mode isEqual:@"实时导航 · 高德"];}
- (NSDictionary *)status{return @{@"version":@"nav-subtitle-v3-neutral-resume",@"mode":_mode?:@"",@"phase":_phase?:@"",@"holding":@(_holding),@"enabled":@(_enabled),@"frames":@(_frames),@"note":_note?:@""};}
- (BOOL)startWithFrame:(NSDictionary *)frame at:(NSTimeInterval)now{
    NSString *text=_formatter(frame);if(_enabled||!text||!isfinite(now)||now<0)return NO;
    NSString *sid=TIOSubtitleNavigationStart();if(!sid){_note=@"未启动：需要字幕配置、空闲确认和前台连接";return NO;}
    _sid=sid;_mode=[frame[@"mode"] isKindOfClass:NSString.class]?[frame[@"mode"] copy]:nil;_phase=[frame[@"phase"] copy];_holding=TIONavSubtitleIsHolding(frame);
    _enabled=YES;_latest=text;_latestAt=_began=now;_sent=nil;_frames=0;_lastSent=0;_needsRefresh=NO;_seenRefresh=[TIOSubtitleNavigationStatus()[@"refreshEpoch"] unsignedIntegerValue];_note=@"等待字幕临时会话成功回执";return YES;
}
- (void)offer:(NSDictionary *)frame at:(NSTimeInterval)now{
    if(!_enabled)return;
    if(!isfinite(now)||now<_latestAt||(_amapFrames&&![frame[@"mode"] isEqual:_mode])){[self stop:@"导航模式或时间异常，停止本次会话"];return;}
    NSString *text=_formatter(frame);
    if(!text&&[self isRealtime]&&[frame[@"phase"] isEqual:@"navigating"])frame=TIONavSubtitleHoldingFrame(@"invalid"),text=_formatter(frame);
    if(!text){[self stop:@"导航结束或状态不可用，停止本次会话"];return;}
    _latest=text;_latestAt=now;_phase=[frame[@"phase"] copy];_holding=TIONavSubtitleIsHolding(frame);
}
- (void)pumpAt:(NSTimeInterval)now{
    if(!_enabled)return;NSDictionary *s=TIOSubtitleNavigationStatus();
    if(![s[@"sid"] isEqual:_sid]||![s[@"navigation"] boolValue]||![@[@"starting",@"ready"] containsObject:s[@"phase"]]){[self stop:@"字幕会话已退出或不可用，不自动重开"];return;}
    if(!isfinite(now)||now<_latestAt){[self stop:@"导航时间异常，停止本次会话"];return;}
    if(now-_began>=240||_frames>=80){[self stop:@"4分钟／80帧字幕研究保护上限"];return;}
    NSString *text=_latest;
    if(now-_latestAt>15){
        if(![self isRealtime]){[self stop:@"导航数据15秒未更新，停止旧指引"];return;}
        if(!_holding)text=TIONavSubtitleText(TIONavSubtitleHoldingFrame(@"stale"));
        _holding=YES; // Never advance _latestAt or relabel stale coordinates as fresh.
        if(!TIONavSubtitleIsHolding(@{@"mode":_mode,@"phase":_phase?:@""}))_phase=@"stale";
        _latest=text;
    }
    NSUInteger epoch=[s[@"refreshEpoch"] unsignedIntegerValue];if(epoch!=_seenRefresh){_seenRefresh=epoch;_needsRefresh=YES;}
    if([s[@"foreground"] isEqual:@NO]){_needsRefresh=YES;_note=@"系统通知暂时遮挡，暂停发送；同会话回前台后续显";return;}
    // Repaint only the current owned SID, not a new preview or a foreign display.
    BOOL repaint=[self isRealtime]&&_frames&&now-_lastSent>=10;
    if(![s[@"phase"] isEqual:@"ready"]||[s[@"pending"] boolValue]||(_frames&&now-_lastSent<3.2)||([text isEqual:_sent]&&!_needsRefresh&&!repaint))return;
    if(!TIOSubtitleNavigationText(_sid,text)){[self stop:@"导航文字未能提交，不循环重试"];return;}
    _frames++;_sent=text;_lastSent=now;_needsRefresh=NO;_note=_holding?@"中性等待状态已提交，指引有效后同会话恢复":@"字幕指引已更新；同会话有界续显，距离为最近提交值";
}
- (void)stop:(NSString *)reason{
    if(!_enabled)return;NSString *sid=_sid;_enabled=NO;_sid=nil;_latest=nil;_needsRefresh=NO;
    TIOSubtitleNavigationStop(sid,reason);_note=[NSString stringWithFormat:@"%@；镜片退出需确认",reason?:@"字幕导航停止"];
}
@end
