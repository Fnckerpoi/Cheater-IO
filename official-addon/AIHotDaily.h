#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSString * const TIOAIHotDailyErrorDomain;
FOUNDATION_EXPORT NSString * const TIOAIHotDailyRetryAfterKey;
FOUNDATION_EXPORT NSUInteger const TIOAIHotDailyMaximumResponseBytes;

typedef NS_ENUM(NSInteger, TIOAIHotDailyErrorCode) {
    TIOAIHotDailyInvalidDate = 1,
    TIOAIHotDailyInvalidPayload,
    TIOAIHotDailyWrongDate,
    TIOAIHotDailyNotPublished,
    TIOAIHotDailyHTTPError,
    TIOAIHotDailyNoValidatedCache,
    TIOAIHotDailyTooLarge,
    TIOAIHotDailyBusy,
    TIOAIHotDailyCancelled,
    TIOAIHotDailyTimedOut,
    TIOAIHotDailyNetworkError
};

@protocol TIOAIHotDailyCancellable <NSObject>
- (void)cancel;
@end

typedef void (^TIOAIHotDailyTransportCompletion)(NSData * _Nullable data,
    NSHTTPURLResponse * _Nullable response, NSError * _Nullable error);

// Transports must enforce maximumBytes while receiving, not after buffering.
@protocol TIOAIHotDailyTransport <NSObject>
- (id<TIOAIHotDailyCancellable>)startRequest:(NSURLRequest *)request
    maximumBytes:(NSUInteger)maximumBytes completion:(TIOAIHotDailyTransportCompletion)completion;
@end

@interface TIOAIHotDailyReport : NSObject
@property(nonatomic, copy, readonly) NSString *stableID;
@property(nonatomic, copy, readonly) NSString *date;
@property(nonatomic, copy, readonly) NSString *title;
@property(nonatomic, copy, readonly) NSString *generatedAt;
@property(nonatomic, copy, readonly) NSString *windowStart;
@property(nonatomic, copy, readonly) NSString *windowEnd;
@property(nonatomic, copy, readonly) NSString *canonicalURL;
@property(nonatomic, copy, readonly) NSString *rssSourceURL;
@property(nonatomic, copy, readonly) NSString *fullText;
@property(nonatomic, copy, readonly) NSString *readingText;
// Validated official JSON report, and provenance records in report order.
// Source canonicalURL/attribution are omitted when the official payload has none.
@property(nonatomic, copy, readonly) NSDictionary *metadata;
@property(nonatomic, copy, readonly) NSArray<NSDictionary *> *sources;
@end

typedef void (^TIOAIHotDailyCompletion)(TIOAIHotDailyReport * _Nullable report, NSError * _Nullable error);
FOUNDATION_EXPORT NSString *TIOAIHotDailyShanghaiDate(NSDate *date);
FOUNDATION_EXPORT TIOAIHotDailyReport * _Nullable TIOAIHotDailyParse(NSData *data,
    NSString *requestedDate, NSError * _Nullable * _Nullable error);

// Direct official JSON only. No RSS, model, scheduler, persistence or third-party fetch.
// One active HTTP request per client, no automatic retries, four URL-scoped ETag entries.
// Reading keeps the lead paragraph and item summaries; only exact lead/item titles are deduplicated.
// Completion is always asynchronous on completionQueue (main by default), exactly once.
@interface TIOAIHotDailyClient : NSObject
- (instancetype)init;
- (instancetype)initWithSessionConfiguration:(NSURLSessionConfiguration *)configuration
    clock:(NSDate *(^)(void))clock completionQueue:(dispatch_queue_t)completionQueue;
- (instancetype)initWithTransport:(id<TIOAIHotDailyTransport>)transport
    clock:(NSDate *(^)(void))clock timeout:(NSTimeInterval)timeout
    completionQueue:(dispatch_queue_t)completionQueue NS_DESIGNATED_INITIALIZER;
- (id<TIOAIHotDailyCancellable>)fetchTodayWithCompletion:(TIOAIHotDailyCompletion)completion;
- (id<TIOAIHotDailyCancellable>)fetchDate:(NSString *)date completion:(TIOAIHotDailyCompletion)completion;
@end
NS_ASSUME_NONNULL_END
