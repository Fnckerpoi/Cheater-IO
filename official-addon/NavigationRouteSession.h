#import <Foundation/Foundation.h>
// In-memory only. Retains the live engine owner across page/home transitions;
// no launch-time restoration, fake GPS or background-task renewal.
@interface TIONavigationRouteSession:NSObject
@property(readonly) BOOL running;
@property(readonly,copy) NSString *token;
+ (instancetype)shared;
- (NSString *)beginWithOwner:(id)owner backgroundAllowed:(BOOL(^)(void))allowed pump:(void(^)(void))pump stop:(void(^)(NSString *))stop;
- (BOOL)owns:(id)owner;
- (BOOL)isActiveToken:(NSString *)token;
- (BOOL)backgroundAllowedForToken:(NSString *)token;
- (void)pulse;
- (void)endOwner:(id)owner;
- (void)stopWithReason:(NSString *)reason;
@end
