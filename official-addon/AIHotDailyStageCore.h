#import "AIHotDaily.h"
NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSString * const TIOAIHotDailyStageErrorDomain;
FOUNDATION_EXPORT BOOL TIOAIHotDailyStageHostAllowed(NSDictionary *info, NSString *expectedBundle);
@interface TIOAIHotDailyStageExport : NSObject
@property(nonatomic, copy, readonly) NSString *filename;
@property(nonatomic, copy, readonly) NSData *data;
@end
// Owns one public temporary TXT, independent of fetch generation/background cancellation.
// Retain while the activity consumes the URL; complete only after activity completion.
@interface TIOAIHotDailyStageShareFile : NSObject
@property(nonatomic, copy, readonly) NSURL *fileURL;
+ (nullable instancetype)fileWithExport:(TIOAIHotDailyStageExport *)export error:(NSError * _Nullable * _Nullable)error;
- (void)complete;
@end
// In-memory main-thread UI state only. No persistence, scheduler or automatic IO.
@interface TIOAIHotDailyStageState : NSObject
@property(nonatomic, readonly) NSUInteger generation;
@property(nonatomic, readonly) BOOL busy;
@property(nonatomic, copy, readonly, nullable) NSString *requestedDate;
@property(nonatomic, readonly, nullable) TIOAIHotDailyReport *report;
@property(nonatomic, readonly, nullable) NSError *error;
- (NSUInteger)beginAtDate:(NSDate *)now;
- (void)cancel;
- (BOOL)finishGeneration:(NSUInteger)generation report:(nullable TIOAIHotDailyReport *)report error:(nullable NSError *)error;
- (nullable TIOAIHotDailyStageExport *)exportAtDate:(NSDate *)now error:(NSError * _Nullable * _Nullable)error;
@end
NS_ASSUME_NONNULL_END
