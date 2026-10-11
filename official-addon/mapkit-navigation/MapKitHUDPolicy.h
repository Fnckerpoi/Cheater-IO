#import <Foundation/Foundation.h>

@interface TIOMapKitHUDPolicy : NSObject
@property(nonatomic,readonly) BOOL active;
@property(nonatomic,readonly) BOOL ready;
@property(nonatomic,readonly) NSDictionary *frame;
@property(nonatomic,readonly) NSData *pixels;
@property(nonatomic,readonly) NSString *note;
- (instancetype)initWithClock:(NSTimeInterval(^)(void))clock;
- (BOOL)start:(NSDictionary *)frame pixels:(NSData *)pixels foreground:(BOOL)foreground allowed:(BOOL)allowed;
- (BOOL)offer:(NSDictionary *)frame pixels:(NSData *)pixels;
- (BOOL)fresh;
- (BOOL)canSubmitWithBusy:(BOOL)busy;
- (void)didSubmit;
- (void)acknowledgeReady;
- (void)stop:(NSString *)reason;
@end
