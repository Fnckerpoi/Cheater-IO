#import <Foundation/Foundation.h>
#import "TIONewsSource.h"
#import "TIONewsScheduler.h"
#import "TIOImportQueue.h"
#include <assert.h>

int main(void) {
    @autoreleasepool {
        NSLog(@"[TEST] 1. 测试 TIONewsSourceConfig 序列化与反序列化...");
        TIONewsSourceConfig *c1 = [TIONewsSourceConfig new];
        c1.sourceID = @"test-rss-1";
        c1.name = @"测试RSS";
        c1.type = TIONewsSourceTypeRSS;
        c1.endpointOrTopic = @"https://example.com/rss.xml";
        c1.enabled = YES;

        NSDictionary *dict = [c1 dictionaryRepresentation];
        TIONewsSourceConfig *c2 = [TIONewsSourceConfig fromDictionary:dict];
        assert([c2.sourceID isEqualToString:c1.sourceID]);
        assert([c2.name isEqualToString:c1.name]);
        assert(c2.type == TIONewsSourceTypeRSS);
        assert(c2.enabled == YES);
        NSLog(@"[TEST] PASS: TIONewsSourceConfig 正常");

        NSLog(@"[TEST] 2. 测试 TIONewsArticle 生成与属性完整性...");
        TIONewsArticle *art = [[TIONewsArticle alloc] initWithSourceID:c1.sourceID
                                                            sourceName:c1.name
                                                                 title:@"测试标题"
                                                                  date:@"2026-10-14"
                                                           readingText:@"正文测试内容"
                                                          canonicalURL:@"https://example.com/item/1"];
        assert(art.articleID.length > 0);
        assert(art.characterCount == 6);
        assert([art.date isEqualToString:@"2026-10-14"]);
        NSLog(@"[TEST] PASS: TIONewsArticle 正常");

        NSLog(@"[TEST] 3. 测试 TIOImportQueue 队列入队与自动消费机制...");
        TIOImportQueue *queue = [TIOImportQueue sharedQueue];
        [queue clearQueue];
        assert(queue.remainingCount == 0);

        TIONewsArticle *art2 = [[TIONewsArticle alloc] initWithSourceID:@"test-rss-2"
                                                             sourceName:@"测试源2"
                                                                  title:@"第二篇测试稿"
                                                                   date:@"2026-10-14"
                                                            readingText:@"第二篇正文内容"
                                                           canonicalURL:nil];
        [queue enqueueArticles:@[art, art2]];
        assert(queue.remainingCount == 2);
        assert([queue.currentArticle.articleID isEqualToString:art.articleID]);

        // 激活免选通道生成临时文件
        BOOL armOk = [queue armQueueAutoImport];
        assert(armOk);
        NSLog(@"[TEST] PASS: TIOImportQueue 队列就绪与免选激活正常");

        NSLog(@"[TEST] 4. 测试 TIONewsScheduler 配置与来源存储...");
        TIONewsScheduler *scheduler = [TIONewsScheduler sharedScheduler];
        [scheduler removeSourceWithID:@"test-unit-id"];
        TIONewsSourceConfig *testSource = [TIONewsSourceConfig new];
        testSource.sourceID = @"test-unit-id";
        testSource.name = @"单元测试源";
        testSource.type = TIONewsSourceTypeSearch;
        testSource.endpointOrTopic = @"机器人技术前沿";
        testSource.enabled = YES;

        [scheduler addSource:testSource];
        BOOL found = NO;
        for (TIONewsSourceConfig *s in [scheduler allSources]) {
            if ([s.sourceID isEqualToString:@"test-unit-id"]) { found = YES; break; }
        }
        assert(found);
        [scheduler removeSourceWithID:@"test-unit-id"];
        NSLog(@"[TEST] PASS: TIONewsScheduler 存储与更新正常");

        NSLog(@"[TEST] 全部 TIONewsHub 核心模块自测 100%% PASS！");
    }
    return 0;
}
