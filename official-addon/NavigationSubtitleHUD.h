#import <Foundation/Foundation.h>
// Runtime bridge implemented by SubtitleHUD.m; no network/coordinates here.
NSDictionary *TIOSubtitleNavigationStatus(void);
BOOL TIOSubtitleConfirmIdle(void); // Call only after explicit lens-idle confirmation.
NSString *TIOSubtitleNavigationStart(void);
NSString *TIOSubtitleRouteStart(NSString *routeToken);
BOOL TIOSubtitleNavigationText(NSString *sid,NSString *text);
void TIOSubtitleNavigationStop(NSString *sid,NSString *reason);
NSString *TIONavSubtitleText(NSDictionary *frame);
NSDictionary *TIONavSubtitleHoldingFrame(NSString *phase); // Real AMap: no turn, road or distance.
BOOL TIONavSubtitleIsHolding(NSDictionary *frame);
@interface TIONavSubtitleHUD:NSObject
@property(readonly) NSDictionary *status;
- (instancetype)initWithFrameFormatter:(NSString *(^)(NSDictionary *))formatter;
- (BOOL)startRouteWithFrame:(NSDictionary *)frame token:(NSString *)token at:(NSTimeInterval)now;
- (BOOL)startWithFrame:(NSDictionary *)frame at:(NSTimeInterval)now;
- (void)offer:(NSDictionary *)frame at:(NSTimeInterval)now;
- (void)pumpAt:(NSTimeInterval)now;
- (void)stop:(NSString *)reason;
@end
