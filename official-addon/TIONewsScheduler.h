#import <Foundation/Foundation.h>
#import "TIONewsSource.h"

NS_ASSUME_NONNULL_BEGIN

// 调度任务执行状态
typedef NS_ENUM(NSInteger, TIONSchedulerStatus) {
    TIONSchedulerStatusIdle = 0,         // 空闲
    TIONSchedulerStatusRunning = 1,      // 正在抓取生成
    TIONSchedulerStatusWaitingRetry = 2, // 抓取未全完成，等待10分钟后重试
    TIONSchedulerStatusComplete = 3      // 当日全部源均已成功生成
};

@protocol TIONewsSchedulerDelegate <NSObject>
@optional
- (void)newsSchedulerDidUpdateStatus:(TIONSchedulerStatus)status message:(NSString *)message;
- (void)newsSchedulerDidGenerateArticles:(NSArray<TIONewsArticle *> *)articles;
@end

@interface TIONewsScheduler : NSObject
@property(nonatomic, weak) id<TIONewsSchedulerDelegate> delegate;

// 配置属性（持久化于 NSUserDefaults）
@property(nonatomic) BOOL enabled;                       // 是否开启每日自动定时
@property(nonatomic) NSInteger scheduledHour;            // 自定义定时小时 (0-23，默认9)
@property(nonatomic) NSInteger scheduledMinute;          // 自定义定时分钟 (0-59，默认0)
@property(nonatomic, readonly) TIONSchedulerStatus status;
@property(nonatomic, readonly) NSString *statusMessage;
@property(nonatomic, copy, readonly) NSString *todayDateStr; // 当前北京时间日期

+ (instancetype)sharedScheduler;

// 启动/重启定时调度服务
- (void)start;
- (void)stop;

// 手动触发一次全面检查与抓取（App 打开前台恢复或用户点击立即生成）
- (void)triggerCheckNowForce:(BOOL)force;

// 配置源管理
- (NSArray<TIONewsSourceConfig *> *)allSources;
- (void)saveSources:(NSArray<TIONewsSourceConfig *> *)sources;
- (void)addSource:(TIONewsSourceConfig *)source;
- (void)removeSourceWithID:(NSString *)sourceID;

// 当日已就绪的待导入文稿列表
- (NSArray<TIONewsArticle *> *)todayArticles;

@end

NS_ASSUME_NONNULL_END

NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSString * const TIONewsJobWaitingRuntimeContext;
FOUNDATION_EXPORT NSString * const TIONewsJobUnknownNeedsReconciliation;
FOUNDATION_EXPORT NSString * const TIONewsJobSaved;
FOUNDATION_EXPORT NSString * const TIONewsJobRetryNotWritten;
FOUNDATION_EXPORT NSString * const TIONewsJobUnavailable;

typedef NS_ENUM(NSInteger, TIONewsSaveDisposition) {
    TIONewsSaveUnknown,
    TIONewsSaveDefinitelyNotWritten,
    TIONewsSaveReadbackEvidence,
    TIONewsSaveRuntimeUnavailable
};
@interface TIONewsSaveResult : NSObject
@property(nonatomic) TIONewsSaveDisposition disposition;
@property(nonatomic,copy,nullable) NSString *officialID;
@property(nonatomic,copy,nullable) NSString *readbackText;
@property(nonatomic,strong,nullable) NSDate *verifiedAt;
@end

@protocol TIONewsSaveBridge <NSObject>
@property(nonatomic,readonly) BOOL runtimeContextAvailable;
- (void)saveArticle:(TIONewsArticle *)article idempotencyKey:(NSString *)key completion:(void (^)(TIONewsSaveResult *result))completion;
- (void)reconcileArticle:(TIONewsArticle *)article idempotencyKey:(NSString *)key completion:(void (^)(TIONewsSaveResult *result))completion;
@end

typedef void (^TIONewsScheduledFetch)(TIONewsSourceConfig *source,NSDate *now,TIONewsFetchCompletion completion);
@interface TIONewsScheduler (RuntimeResults)
- (instancetype)initWithDefaults:(NSUserDefaults *)defaults
                           clock:(NSDate *(^)(void))clock
                           fetch:(TIONewsScheduledFetch)fetch
                          bridge:(nullable id<TIONewsSaveBridge>)bridge;
@property(nonatomic,strong,nullable) id<TIONewsSaveBridge> saveBridge;
@property(nonatomic,copy) NSString *timeZoneName;
@property(nonatomic,readonly) NSUInteger officialSavedCount;
- (NSArray<NSDictionary *> *)jobSnapshots;
// Call on foreground wake. iOS suspension does not promise on-time execution.
- (void)checkScheduledAtWake;
@end
NS_ASSUME_NONNULL_END
