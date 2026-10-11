#import <Foundation/Foundation.h>

NSString *TIOMapKitSubtitleText(NSDictionary *frame);
@interface TIOMapKitSubtitleHUD : NSObject
@property(readonly) NSDictionary *status;
- (BOOL)startWithFrame:(NSDictionary *)frame at:(NSTimeInterval)now;
- (void)offer:(NSDictionary *)frame at:(NSTimeInterval)now;
- (void)pumpAt:(NSTimeInterval)now;
- (void)stop:(NSString *)reason;
@end
