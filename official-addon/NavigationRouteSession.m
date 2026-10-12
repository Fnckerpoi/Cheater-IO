#import "NavigationRouteSession.h"
@implementation TIONavigationRouteSession {
    id _owner; NSString *_token; BOOL(^_allowed)(void); void(^_pump)(void);void(^_stop)(NSString *);
}
+ (instancetype)shared{static id s;static dispatch_once_t once;dispatch_once(&once,^{s=[self new];});return s;}
- (BOOL)running{return _owner!=nil;}
- (NSString *)token{return _token;}
- (NSString *)beginWithOwner:(id)owner backgroundAllowed:(BOOL(^)(void))allowed pump:(void(^)(void))pump stop:(void(^)(NSString *))stop{
    if(!NSThread.isMainThread||!owner||_owner||!allowed||!pump||!stop)return nil;
    _owner=owner;_token=NSUUID.UUID.UUIDString;_allowed=[allowed copy];_pump=[pump copy];_stop=[stop copy];return _token;
}
- (BOOL)owns:(id)owner{return owner&&_owner==owner;}
- (BOOL)isActiveToken:(NSString *)token{return NSThread.isMainThread&&_owner&&token&&[_token isEqual:token];}
- (BOOL)backgroundAllowedForToken:(NSString *)token{return NSThread.isMainThread&&_owner&&token&&[_token isEqual:token]&&_allowed&&_allowed();}
- (void)pulse{if(NSThread.isMainThread&&_owner&&_pump)_pump();}
- (void)endOwner:(id)owner{if(!NSThread.isMainThread||![self owns:owner])return;_pump=nil;_stop=nil;_allowed=nil;_token=nil;_owner=nil;}
- (void)stopWithReason:(NSString *)reason{if(!NSThread.isMainThread||!_owner)return;id owner=_owner;void(^stop)(NSString *)=[_stop copy];[self endOwner:owner];if(stop)stop(reason?:@"路线会话停止");}
@end
