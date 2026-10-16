#import "../../TIONewsScheduler.h"
#import "../../TIOImportQueue.h"
NSUInteger NewsArmCalls,NewsEnqueueCalls;
@implementation TIONewsScheduler
+ (instancetype)sharedScheduler { static id s; static dispatch_once_t once; dispatch_once(&once,^{s=[self new];});return s; }
- (NSString *)statusMessage { return @"已生成真实fixture稿，未保存至官方"; }
- (NSArray *)todayArticles { return @[[[TIONewsArticle alloc]initWithSourceID:@"fixture" sourceName:@"隔离公开数据fixture" title:@"已生成待预览文稿" date:@"2026-10-14" readingText:@"这份fixture用于验证生成就绪不是官方已保存，不调用网络和导入。" canonicalURL:nil]]; }
- (NSArray *)allSources { return @[]; }
- (void)triggerCheckNowForce:(BOOL)force { abort(); }
- (void)saveSources:(NSArray *)sources { abort(); }
- (void)addSource:(TIONewsSourceConfig *)source { abort(); }
- (void)removeSourceWithID:(NSString *)sourceID { abort(); }
- (void)start { abort(); }
- (void)stop {}
- (TIONSchedulerStatus)status { return TIONSchedulerStatusComplete; }
- (NSString *)todayDateStr { return @"2026-10-14"; }
@end
@implementation TIOImportQueue
+ (instancetype)sharedQueue { static id q; static dispatch_once_t once; dispatch_once(&once,^{q=[self new];});return q; }
- (BOOL)headlessImportAvailable { return NO; }
- (NSString *)headlessImportUnavailableReason { return @"尚未验证官方后台保存与回读契约；无法自动入库。"; }
- (NSUInteger)suppliedCount { return 1; }
- (NSUInteger)officialSavedCount { return 0; }
- (NSUInteger)remainingCount { return 1; }
- (NSUInteger)completedCount { return 1; }
- (TIONewsArticle *)currentArticle { return [TIONewsScheduler sharedScheduler].todayArticles.firstObject; }
- (void)enqueueArticles:(NSArray *)articles { NewsEnqueueCalls++; }
- (BOOL)armQueueAutoImport { NewsArmCalls++;return YES; }
- (void)clearQueue {}
- (void)disarm {}
- (void)triggerAutoImportFlow { abort(); }
@end
