#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSString *TIOImportTrialSHA(NSData *data);
FOUNDATION_EXPORT NSDictionary * _Nullable TIOImportTrialProfile(NSString *method,id args);
@interface TIOImportTrialCore : NSObject
@property(nonatomic,readonly) NSString *state;
@property(nonatomic,readonly) NSUInteger generation;
@property(nonatomic,readonly) NSDictionary *summary;
@property(nonatomic,readonly,nullable) NSURL *fileURL;
@property(nonatomic,readonly) NSString *publicText;
@property(nonatomic,readonly) BOOL armed;
@property(nonatomic,readonly) BOOL functional;
@property(nonatomic,readonly) BOOL requested;
@property(nonatomic,readonly) BOOL canConfirm;
@property(nonatomic,readonly) BOOL canFunction;
- (BOOL)createFixture;
- (BOOL)armFunctional:(BOOL)functional now:(NSTimeInterval)now;
- (BOOL)acceptMethod:(NSString *)method args:(id)args now:(NSTimeInterval)now;
- (BOOL)observeResult:(nullable id)result profile:(NSDictionary *)profile now:(NSTimeInterval)now;
- (BOOL)beginSubmissionAt:(NSTimeInterval)now;
- (void)submissionCleared:(BOOL)cleared;
- (BOOL)confirmConsumption;
- (BOOL)confirmSave;
- (void)stop:(NSString *)reason;
- (void)expireAt:(NSTimeInterval)now;
- (BOOL)verifyOwnFile;
- (BOOL)cleanupAt:(NSTimeInterval)now;
@end
NS_ASSUME_NONNULL_END
