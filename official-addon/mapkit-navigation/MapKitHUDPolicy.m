#import "MapKitHUDPolicy.h"
#import <CoreFoundation/CoreFoundation.h>
#include <math.h>
@implementation TIOMapKitHUDPolicy {
 NSTimeInterval(^_clock)(void);
 NSTimeInterval _lastSubmit;
 unsigned long long _submittedRevision;
 BOOL _hasSubmitted;
}
- (instancetype)initWithClock:(NSTimeInterval(^)(void))clock {
 if(!clock)return nil;
 if((self=[super init])){_clock=[clock copy];_note=@"未启用眼镜 HUD";}return self;
}
- (BOOL)valid:(NSDictionary *)frame pixels:(NSData *)pixels {
 if(!NSThread.isMainThread||![frame isKindOfClass:NSDictionary.class]||![pixels isKindOfClass:NSData.class]||pixels.length!=65536)return NO;
 id sid=frame[@"sessionId"],rev=frame[@"revision"],updated=frame[@"updatedAt"];
 if(![sid isKindOfClass:NSString.class]||![[NSUUID alloc]initWithUUIDString:sid]||![rev isKindOfClass:NSNumber.class]||![updated isKindOfClass:NSNumber.class])return NO;
 if(CFGetTypeID((__bridge CFTypeRef)rev)==CFBooleanGetTypeID()||CFGetTypeID((__bridge CFTypeRef)updated)==CFBooleanGetTypeID())return NO;
 double version=[rev doubleValue],at=[updated doubleValue],now=_clock();
 if(!isfinite(version)||version<1||version>9007199254740991.0||floor(version)!=version||!isfinite(at)||!isfinite(now)||at<0||at>now||now-at>15)return NO;
 return [frame[@"provider"] isEqual:@"mapkit"]&&[@[@"walk",@"ride",@"drive"] containsObject:frame[@"mode"]]&&[frame[@"phase"] isEqual:@"navigating"];
}
- (BOOL)start:(NSDictionary *)frame pixels:(NSData *)pixels foreground:(BOOL)foreground allowed:(BOOL)allowed {
 if(_active||!foreground||!allowed||![self valid:frame pixels:pixels])return NO;
 _active=YES;_ready=NO;_hasSubmitted=NO;_submittedRevision=0;_lastSubmit=0;_frame=@{@"sessionId":[frame[@"sessionId"] copy],@"revision":[frame[@"revision"] copy],@"updatedAt":[frame[@"updatedAt"] copy],@"provider":@"mapkit",@"mode":[frame[@"mode"] copy],@"phase":[frame[@"phase"] copy]};_pixels=[pixels copy];_note=@"查询真实 TDP1 回执；尚未发送画面";return YES;
}
- (BOOL)offer:(NSDictionary *)frame pixels:(NSData *)pixels {
 if(!_active||![frame isKindOfClass:NSDictionary.class]||![frame[@"sessionId"] isEqual:_frame[@"sessionId"]]||![frame[@"revision"] isKindOfClass:NSNumber.class]||[frame[@"revision"] unsignedLongLongValue]<=[_frame[@"revision"] unsignedLongLongValue])return NO;
 if(![self valid:frame pixels:pixels]||![frame[@"mode"] isEqual:_frame[@"mode"]]||[frame[@"updatedAt"] doubleValue]<[_frame[@"updatedAt"] doubleValue]){[self stop:@"当前导航进入弱信号／重算／旧帧状态，停止可操作 HUD 指引"];return NO;}
 _frame=@{@"sessionId":[frame[@"sessionId"] copy],@"revision":[frame[@"revision"] copy],@"updatedAt":[frame[@"updatedAt"] copy],@"provider":@"mapkit",@"mode":[frame[@"mode"] copy],@"phase":[frame[@"phase"] copy]};_pixels=[pixels copy];return YES;
}
- (BOOL)fresh {return _active&&[self valid:_frame pixels:_pixels];}
- (BOOL)canSubmitWithBusy:(BOOL)busy {
 return NSThread.isMainThread&&_ready&&!busy&&[self fresh]&&(!_hasSubmitted||(_clock()-_lastSubmit>=1&&[_frame[@"revision"] unsignedLongLongValue]>_submittedRevision));
}
- (void)didSubmit {if(!NSThread.isMainThread||!_active)return;_lastSubmit=_clock();_submittedRevision=[_frame[@"revision"] unsignedLongLongValue];_hasSubmitted=YES;}
- (void)acknowledgeReady {if(NSThread.isMainThread&&_active){_ready=YES;_note=@"固件能力与文件／AP 回执已验证；镜片效果待确认";}}
- (void)stop:(NSString *)reason {if(!NSThread.isMainThread)return;_active=NO;_ready=NO;_frame=nil;_pixels=nil;_note=[reason copy]?:@"已停止，不会自动恢复";}
@end
