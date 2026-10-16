#import <Foundation/Foundation.h>
#import "TIONewsSource.h"

NS_ASSUME_NONNULL_BEGIN

@interface TIOImportQueue : NSObject
+ (instancetype)sharedQueue;
- (void)enqueueArticles:(NSArray<TIONewsArticle *> *)articles;
- (void)clearQueue;
@property(nonatomic, readonly) NSUInteger remainingCount;
@property(nonatomic, readonly, nullable) TIONewsArticle *currentArticle;

@property(nonatomic, readonly) BOOL headlessImportAvailable;
@property(nonatomic, readonly, copy) NSString *headlessImportUnavailableReason;
// Native handoff attempts, including unknown results; never official-save evidence.
@property(nonatomic, readonly) NSUInteger suppliedCount;
@property(nonatomic, readonly) NSUInteger officialSavedCount;
// Compatibility alias for officialSavedCount, not native return count.
@property(nonatomic, readonly) NSUInteger completedCount;

// Fail closed without a verified headless backend. Legacy supply is test-build-only.
- (BOOL)armQueueAutoImport;
- (void)disarm;
- (void)triggerAutoImportFlow;
@end

NS_ASSUME_NONNULL_END
