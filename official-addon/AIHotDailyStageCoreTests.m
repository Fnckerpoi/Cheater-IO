#import "AIHotDailyStageCore.h"
static int checks, failures;
static void Check(BOOL condition, NSString *name) { checks++; if (!condition) { failures++; fprintf(stderr, "FAIL %s\n", name.UTF8String); } }
static NSDate *Time(NSString *s) { return [[NSISO8601DateFormatter new] dateFromString:s]; }
static TIOAIHotDailyReport *Report(NSString *day) {
    NSString *url = [@"https://aihot.news/daily/" stringByAppendingString:day];
    NSDictionary *payload = @{@"schemaVersion":@1, @"report":@{@"date":day, @"generatedAt":@"2026-10-13T00:00:00Z", @"windowStart":@"2026-10-12T00:00:00Z", @"windowEnd":@"2026-10-13T00:00:00Z", @"links":@{@"aihot":url}, @"attribution":@{@"name":@"AIHOT", @"url":url}, @"lead":NSNull.null, @"sections":@[@{@"label":@"匿名产品", @"items":@[@{@"title":@"标题", @"summary":@"来源文本 $(touch /tmp/not-executed) 仅是数据。", @"source":@{@"name":@"匿名原始来源"}, @"links":@{@"aihot":NSNull.null, @"original":@"https://example.org/article"}, @"attribution":@{@"name":@"AIHOT", @"url":url}}]}], @"flashes":@[]}};
    NSData *data = [NSJSONSerialization dataWithJSONObject:payload options:0 error:nil];
    return TIOAIHotDailyParse(data, day, nil);
}
int main(void) { @autoreleasepool {
    NSDictionary *host = @{@"CFBundleIdentifier":@"research.exact", @"CFBundleShortVersionString":@"1.0.5", @"CFBundleVersion":@"201"};
    Check(TIOAIHotDailyStageHostAllowed(host, @"research.exact"), @"exact host accepts");
    Check(!TIOAIHotDailyStageHostAllowed(host, @"research.exact.other"), @"different bundle rejects");
    for (NSString *field in @[@"CFBundleIdentifier", @"CFBundleShortVersionString", @"CFBundleVersion"]) for (id value in @[NSNull.null, @[], @201, @"other"]) {
        NSMutableDictionary *bad = [host mutableCopy]; bad[field] = value;
        Check(!TIOAIHotDailyStageHostAllowed(bad, @"research.exact"), @"host field invalid fails closed");
    }
    NSDate *now = Time(@"2026-10-12T16:01:00Z");
    TIOAIHotDailyStageState *state = [TIOAIHotDailyStageState new]; NSError *error;
    Check(![state exportAtDate:now error:&error], @"no fetch no export");
    NSUInteger first = [state beginAtDate:now];
    Check(state.busy && [state.requestedDate isEqual:@"2026-10-13"] && !state.report, @"begin uses Shanghai day");
    Check(![state exportAtDate:now error:&error], @"busy no export");
    TIOAIHotDailyReport *report = Report(@"2026-10-13");
    Check([state finishGeneration:first report:report error:nil] && state.report == report && !state.busy, @"successful identity retained");
    TIOAIHotDailyStageExport *export = [state exportAtDate:now error:&error];
    Check(export && [export.filename isEqual:@"AIHOT-2026-10-13-reading.txt"] && ![export.filename containsString:@"/"] && ![export.filename containsString:@".."], @"stable safe date-only file name");
    NSString *text = [[NSString alloc] initWithData:export.data encoding:NSUTF8StringEncoding];
    Check([text hasPrefix:report.readingText] && [text containsString:@"来源：匿名原始来源；署名：AIHOT"] && [text hasSuffix:@"官方日报来源：https://aihot.news/daily/2026-10-13\n"], @"TXT is reading UTF8 with attribution and canonical provenance footer");
    Check([text containsString:@"$(touch /tmp/not-executed)"] && ![text containsString:@"阶段测试"], @"export does not run instructions or inject stage UI into manuscript");
    Check(![state exportAtDate:Time(@"2026-10-13T16:00:00Z") error:&error], @"previous Shanghai day not exported as today");
    NSUInteger refresh = [state beginAtDate:now];
    Check(!state.report && ![state exportAtDate:now error:&error], @"refresh removes old export immediately");
    Check(![state finishGeneration:first report:report error:nil] && state.busy, @"late old callback ignored");
    [state cancel];
    Check(!state.busy && !state.report && ![state finishGeneration:refresh report:report error:nil] && ![state exportAtDate:now error:&error], @"cancel invalidates generation and export");
    NSUInteger failed = [state beginAtDate:now]; NSError *notPublished = [NSError errorWithDomain:TIOAIHotDailyErrorDomain code:TIOAIHotDailyNotPublished userInfo:nil];
    Check([state finishGeneration:failed report:report error:notPublished] && state.error == notPublished && !state.report && ![state exportAtDate:now error:&error], @"error cannot export old success even when report supplied");
    NSUInteger wrong = [state beginAtDate:now];
    Check([state finishGeneration:wrong report:Report(@"2026-10-12") error:nil] && state.error && ![state exportAtDate:now error:&error], @"wrong-day report rejected");
    for (NSString *unsafe in @[@"../2026-10-13", @"2026-02-30", @"2026-10-13/file"]) {
        NSUInteger generation = [state beginAtDate:now]; TIOAIHotDailyReport *bad = Report(@"2026-10-13"); [bad setValue:unsafe forKey:@"date"];
        [state finishGeneration:generation report:bad error:nil];
        Check(!state.report && ![state exportAtDate:now error:&error], @"malicious report date cannot enter export path");
    }
    NSUInteger malformed = [state beginAtDate:now]; TIOAIHotDailyReport *bad = Report(@"2026-10-13"); [bad setValue:@"daily-old" forKey:@"stableID"];
    [state finishGeneration:malformed report:bad error:nil]; Check(!state.report, @"stable ID mismatch rejects");
    for (NSString *overlong in @[@"", [@"中" stringByPaddingToLength:12001 withString:@"中" startingAtIndex:0]]) {
        NSUInteger generation = [state beginAtDate:now]; TIOAIHotDailyReport *badText = Report(@"2026-10-13"); [badText setValue:overlong forKey:@"readingText"];
        [state finishGeneration:generation report:badText error:nil];
        Check(![state exportAtDate:now error:&error] && error.code == 4, @"empty/oversized TXT rejected, never trimmed");
    }
    NSUInteger footer = [state beginAtDate:now]; TIOAIHotDailyReport *nearLimit = Report(@"2026-10-13");
    [nearLimit setValue:[@"a" stringByPaddingToLength:11990 withString:@"a" startingAtIndex:0] forKey:@"readingText"];
    [state finishGeneration:footer report:nearLimit error:nil];
    Check(![state exportAtDate:now error:&error] && error.code == 4, @"canonical footer pushing final TXT beyond limit rejects without clipping");
    NSUInteger badCanonical = [state beginAtDate:now]; TIOAIHotDailyReport *badURL = Report(@"2026-10-13"); [badURL setValue:@"https://example.org/wrong-day" forKey:@"canonicalURL"];
    [state finishGeneration:badCanonical report:badURL error:nil];
    Check(![state exportAtDate:now error:&error], @"export canonical must match official date");
    NSUInteger background = [state beginAtDate:now]; [state cancel]; NSUInteger resumed = [state beginAtDate:now];
    Check(![state finishGeneration:background report:report error:nil] && state.busy && !state.report, @"background-cancel old callback cannot populate resumed manual request");
    TIOAIHotDailyReport *newIdentity = Report(@"2026-10-13");
    Check([state finishGeneration:resumed report:newIdentity error:nil] && state.report == newIdentity && state.report != report && [state exportAtDate:now error:&error] != nil, @"after cancellation only new manual report identity exports");
    TIOAIHotDailyStageExport *publicExport = [state exportAtDate:now error:&error];
    TIOAIHotDailyStageShareFile *share = [TIOAIHotDailyStageShareFile fileWithExport:publicExport error:&error];
    Check(share && !error && [NSFileManager.defaultManager fileExistsAtPath:share.fileURL.path], @"share creates private random temporary public TXT");
    NSURL *firstShareURL = share.fileURL;
    [state cancel];
    Check(![state exportAtDate:now error:&error] && [NSFileManager.defaultManager fileExistsAtPath:firstShareURL.path], @"background cancellation invalidates fetch export but keeps active consumer TXT");
    dispatch_semaphore_t consumed = dispatch_semaphore_create(0); __block NSData *delayedRead;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 40 * NSEC_PER_MSEC), dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
        delayedRead = [NSData dataWithContentsOfURL:firstShareURL]; dispatch_semaphore_signal(consumed);
    });
    Check(dispatch_semaphore_wait(consumed, dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC)) == 0 && [delayedRead isEqual:publicExport.data], @"delayed file consumer reads exact TXT after background cancelled state");
    TIOAIHotDailyStageShareFile *secondShare = [TIOAIHotDailyStageShareFile fileWithExport:publicExport error:&error];
    NSURL *secondURL = secondShare.fileURL;
    Check(secondShare && ![firstShareURL isEqual:secondURL], @"separate activities own distinct random directories");
    [share complete];
    Check(![NSFileManager.defaultManager fileExistsAtPath:firstShareURL.path] && [NSFileManager.defaultManager fileExistsAtPath:secondURL.path], @"old completion cleans only old activity, not new directory despite generation change");
    [share complete];
    Check([NSFileManager.defaultManager fileExistsAtPath:secondURL.path], @"duplicate old completion cannot clean another activity");
    [secondShare complete];
    Check(![NSFileManager.defaultManager fileExistsAtPath:secondURL.path], @"second completion cleans own TXT after consumer finishes");
    for (NSString *unsafe in @[@"../AIHOT-2026-10-13-reading.txt", @"AIHOT-2026-02-30-reading.txt", @"AIHOT-2026-10-13-reading.txt/evil"]) {
        TIOAIHotDailyStageExport *badExport = [TIOAIHotDailyStageExport new]; [badExport setValue:unsafe forKey:@"filename"]; [badExport setValue:publicExport.data forKey:@"data"];
        Check(![TIOAIHotDailyStageShareFile fileWithExport:badExport error:&error], @"share rejects traversal or malformed date filenames before disk IO");
    }
    fprintf(stdout, "AIHotDailyStageCore: %d checks, %d failures\n", checks, failures);
} return failures ? 1 : 0; }
