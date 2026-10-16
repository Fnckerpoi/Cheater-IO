#import <Foundation/Foundation.h>
#import "TIONewsSource.h"
@interface SearchNoNetwork : NSURLProtocol @end
@implementation SearchNoNetwork
+ (BOOL)canInitWithRequest:(NSURLRequest *)request { fprintf(stderr,"RED: Search attempted network\n");exit(44); }
+ (NSURLRequest *)canonicalRequestForRequest:(NSURLRequest *)request { return request; }
- (void)startLoading { abort(); }
- (void)stopLoading {}
@end
int main(void) { @autoreleasepool {
    [NSURLProtocol registerClass:SearchNoNetwork.class];
    TIONewsSourceConfig *source=[TIONewsSourceConfig new];
    source.sourceID=@"isolated-search-test";source.name=@"前沿科技速递";
    source.type=TIONewsSourceTypeSearch;source.endpointOrTopic=@"人工智能与机器人核心进展";source.enabled=YES;
    [[TIONewsFetcher sharedFetcher] fetchArticleFromSource:source date:[NSDate dateWithTimeIntervalSince1970:1791936000] completion:^(TIONewsArticle *article,NSError *error){
        if(article||!error||![error.domain isEqual:@"TIONewsSourceUnsupported"]||error.code!=1||![error.localizedDescription containsString:@"未接入"]){
            fprintf(stderr,"RED: Search returned fabricated article or no clear unsupported error\n");exit(42);
        }
        printf("PASS: Search fails closed with no article, no fabricated source URL\n");exit(0);
    }];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,3*NSEC_PER_SEC),dispatch_get_main_queue(),^{fprintf(stderr,"RED: Search callback timeout\n");exit(43);});
    dispatch_main();
} }
