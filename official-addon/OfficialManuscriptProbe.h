#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSString * const TIOOfficialProbePublicTitle;
FOUNDATION_EXPORT NSString * const TIOOfficialProbePublicBody;

typedef NSTimeInterval (^TIOOfficialProbeClock)(void);

@interface TIOOfficialManuscriptProbe : NSObject
- (instancetype)init;
- (instancetype)initWithClock:(TIOOfficialProbeClock)clock NS_DESIGNATED_INITIALIZER;
// One explicit authorization per instance, 120 seconds maximum, no implicit rearm.
- (BOOL)armForPublicTestWithUserConfirmation:(BOOL)confirmed;
- (void)stop;
@property(nonatomic, readonly, getter=isActive) BOOL active;
@property(nonatomic, readonly) NSArray<NSDictionary *> *records;
// Arguments are borrowed, never retained or emitted. ID must come from a real host observation.
- (nullable NSDictionary *)observeChannel:(NSString *)channel
                                   method:(NSString *)method
                                arguments:(nullable id)arguments
                                 stableID:(nullable id)stableID;
// Observation failure never prevents the original operation; its exceptions still propagate.
- (void)forwardChannel:(NSString *)channel
                method:(NSString *)method
             arguments:(nullable id)arguments
              stableID:(nullable id)stableID
     originalOperation:(void (^)(void))operation;
@end
NS_ASSUME_NONNULL_END
