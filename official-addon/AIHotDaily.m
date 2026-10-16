#import "AIHotDaily.h"
#import <math.h>

NSString * const TIOAIHotDailyErrorDomain = @"TIOAIHotDaily";
NSString * const TIOAIHotDailyRetryAfterKey = @"retryAfterSeconds";
NSUInteger const TIOAIHotDailyMaximumResponseBytes = 256 * 1024;
static const NSTimeInterval AIHotTimeout = 15;

static NSError *AIHotError(TIOAIHotDailyErrorCode code, NSString *message) {
    return [NSError errorWithDomain:TIOAIHotDailyErrorDomain code:code userInfo:@{NSLocalizedDescriptionKey:message}];
}
static BOOL AIHotString(id value) {
    if (![value isKindOfClass:NSString.class] || ![value length]) return NO;
    if (![[value stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet] length]) return NO;
    for (NSUInteger i = 0; i < [value length]; i++) {
        unichar c = [value characterAtIndex:i];
        if ((c < 32 && c != '\n' && c != '\r' && c != '\t') || c == 127) return NO;
    }
    return YES;
}
static BOOL AIHotDictionary(id v) { return [v isKindOfClass:NSDictionary.class]; }
static NSDateFormatter *AIHotDateFormatter(void) {
    NSDateFormatter *f = [NSDateFormatter new];
    f.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
    f.calendar = [[NSCalendar alloc] initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
    f.timeZone = [NSTimeZone timeZoneWithName:@"Asia/Shanghai"];
    f.dateFormat = @"yyyy-MM-dd";
    f.lenient = NO;
    return f;
}
NSString *TIOAIHotDailyShanghaiDate(NSDate *date) { return [AIHotDateFormatter() stringFromDate:date]; }
static BOOL AIHotDateValid(id date) {
    if (!AIHotString(date) || [date length] != 10) return NO;
    NSDateFormatter *f = AIHotDateFormatter();
    NSDate *parsed = [f dateFromString:date];
    return parsed && [[f stringFromDate:parsed] isEqual:date];
}
static NSDate *AIHotTimestamp(id value) {
    if (!AIHotString(value)) return nil;
    NSISO8601DateFormatter *f = [NSISO8601DateFormatter new];
    f.formatOptions = NSISO8601DateFormatWithInternetDateTime | NSISO8601DateFormatWithFractionalSeconds;
    NSDate *date = [f dateFromString:value];
    if (!date) { f.formatOptions = NSISO8601DateFormatWithInternetDateTime; date = [f dateFromString:value]; }
    return date;
}
static BOOL AIHotURL(id value, BOOL official) {
    if (!AIHotString(value) || [value length] > 4096 || [value rangeOfCharacterFromSet:NSCharacterSet.whitespaceAndNewlineCharacterSet].location != NSNotFound) return NO;
    NSURLComponents *c = [NSURLComponents componentsWithString:value];
    if (!c.URL || !c.host.length || c.user || c.password || (official && c.fragment)) return NO;
    NSString *scheme = c.scheme.lowercaseString;
    if (!([scheme isEqual:@"https"] || (!official && [scheme isEqual:@"http"]))) return NO;
    return !official || ([c.host.lowercaseString isEqual:@"aihot.news"] && (!c.port || c.port.integerValue == 443));
}
static BOOL AIHotAttribution(id value) {
    return AIHotDictionary(value) && AIHotString(value[@"name"]) && AIHotURL(value[@"url"], NO);
}
static BOOL AIHotItem(id item, BOOL flash) {
    if (!AIHotDictionary(item) || !AIHotString(item[@"title"]) || !AIHotDictionary(item[@"source"]) || !AIHotString(item[@"source"][@"name"]) || !AIHotDictionary(item[@"links"])) return NO;
    id canonical = item[@"links"][@"aihot"];
    if (!canonical || (canonical != NSNull.null && !AIHotURL(canonical, YES)) || !AIHotURL(item[@"links"][@"original"], NO)) return NO;
    if (item[@"attribution"] && !AIHotAttribution(item[@"attribution"])) return NO;
    return flash ? AIHotTimestamp(item[@"publishedAt"]) != nil : AIHotString(item[@"summary"]);
}
static NSDictionary *AIHotSource(NSDictionary *item, BOOL flash) {
    NSMutableDictionary *source = [@{@"title":item[@"title"], @"name":item[@"source"][@"name"]} mutableCopy];
    if ([item[@"links"][@"aihot"] isKindOfClass:NSString.class]) source[@"canonicalURL"] = item[@"links"][@"aihot"];
    if (item[@"attribution"]) source[@"attribution"] = item[@"attribution"];
    id original = item[@"links"][@"original"];
    if ([original isKindOfClass:NSString.class]) source[@"originalURL"] = original;
    if (flash) source[@"publishedAt"] = item[@"publishedAt"];
    return [source copy];
}
static void AIHotAppendItem(NSMutableString *text, NSDictionary *item, BOOL flash, BOOL links, BOOL skipTitle) {
    if (!skipTitle) [text appendFormat:@"\n%@\n", item[@"title"]];
    if (!flash) [text appendFormat:@"%@\n", item[@"summary"]];
    else [text appendFormat:@"发布时间：%@\n", item[@"publishedAt"]];
    [text appendFormat:@"来源：%@；%@：%@\n", item[@"source"][@"name"], item[@"attribution"] ? @"署名" : @"数据来源", item[@"attribution"][@"name"] ?: @"AIHOT"];
    if (links) {
        if ([item[@"links"][@"aihot"] isKindOfClass:NSString.class]) [text appendFormat:@"AIHOT：%@\n", item[@"links"][@"aihot"]];
        if (item[@"attribution"]) [text appendFormat:@"署名链接：%@\n", item[@"attribution"][@"url"]];
        id original = item[@"links"][@"original"];
        if ([original isKindOfClass:NSString.class]) [text appendFormat:@"原始来源：%@\n", original];
    }
}
static BOOL AIHotTextFits(NSString *text) {
    return text.length <= 12000 && [text lengthOfBytesUsingEncoding:NSUTF8StringEncoding] <= 48000;
}

@interface TIOAIHotDailyReport ()
@property(nonatomic, copy, readwrite) NSString *stableID, *date, *title, *generatedAt, *windowStart, *windowEnd, *canonicalURL, *rssSourceURL, *fullText, *readingText;
@property(nonatomic, copy, readwrite) NSDictionary *metadata;
@property(nonatomic, copy, readwrite) NSArray<NSDictionary *> *sources;
@end
@implementation TIOAIHotDailyReport
@end

TIOAIHotDailyReport *TIOAIHotDailyParse(NSData *data, NSString *requestedDate, NSError **error) {
#define AIHOT_FAIL(code, message) do { if (error) *error = AIHotError(code, message); return nil; } while (0)
    if (error) *error = nil;
    if (!AIHotDateValid(requestedDate)) AIHOT_FAIL(TIOAIHotDailyInvalidDate, @"Expected a real Shanghai calendar date (yyyy-MM-dd).");
    if (![data isKindOfClass:NSData.class] || data.length > TIOAIHotDailyMaximumResponseBytes) AIHOT_FAIL(TIOAIHotDailyTooLarge, @"Official response exceeds 256 KiB.");
    id root = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (!AIHotDictionary(root) || ![root[@"schemaVersion"] isKindOfClass:NSNumber.class] || CFGetTypeID((__bridge CFTypeRef)root[@"schemaVersion"]) == CFBooleanGetTypeID() || [root[@"schemaVersion"] doubleValue] != 1) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Expected official schemaVersion 1.");
    id report = root[@"report"];
    if (!AIHotDictionary(report) || !AIHotDateValid(report[@"date"])) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Missing daily report date.");
    if (![report[@"date"] isEqual:requestedDate]) AIHOT_FAIL(TIOAIHotDailyWrongDate, @"Official report is not for the requested Shanghai date.");
    NSDate *generated = AIHotTimestamp(report[@"generatedAt"]), *start = AIHotTimestamp(report[@"windowStart"]), *end = AIHotTimestamp(report[@"windowEnd"]);
    if (!generated || !start || !end || [start compare:end] != NSOrderedAscending || !AIHotDictionary(report[@"links"]) || !AIHotURL(report[@"links"][@"aihot"], YES) || (report[@"attribution"] && !AIHotAttribution(report[@"attribution"])) || ![report[@"sections"] isKindOfClass:NSArray.class] || ![report[@"flashes"] isKindOfClass:NSArray.class]) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Invalid official metadata, provenance or report arrays.");
    NSString *canonical = [@"https://aihot.news/daily/" stringByAppendingString:requestedDate];
    if (![report[@"links"][@"aihot"] isEqual:canonical]) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Daily canonical URL does not match date.");
    id lead = report[@"lead"];
    if (lead != NSNull.null && (!AIHotDictionary(lead) || !AIHotString(lead[@"title"]) || !AIHotString(lead[@"leadParagraph"]))) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Expected lead object or explicit null.");
    NSUInteger count = 0;
    for (id section in report[@"sections"]) {
        if (!AIHotDictionary(section) || !AIHotString(section[@"label"]) || ![section[@"items"] isKindOfClass:NSArray.class]) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Invalid section.");
        for (id item in section[@"items"]) {
            if (!AIHotItem(item, NO)) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Invalid section item or source URL.");
            count++;
        }
    }
    for (id flash in report[@"flashes"]) {
        if (!AIHotItem(flash, YES)) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Invalid flash or provenance.");
        count++;
    }
    if (count == 0 && lead == NSNull.null) AIHOT_FAIL(TIOAIHotDailyInvalidPayload, @"Empty daily requires an explicit editorial lead.");
    NSString *title = [@"AIHOT 日报 · " stringByAppendingString:requestedDate];
    NSMutableString *full = [NSMutableString stringWithFormat:@"%@\n", title], *reading = [NSMutableString stringWithFormat:@"%@\n", title];
    if (lead != NSNull.null) {
        [full appendFormat:@"\n%@\n%@\n", lead[@"title"], lead[@"leadParagraph"]];
        [reading appendFormat:@"\n%@\n%@\n", lead[@"title"], lead[@"leadParagraph"]];
    }
    NSMutableArray *sources = [NSMutableArray new];
    for (NSDictionary *section in report[@"sections"]) {
        if (![section[@"items"] count]) continue;
        [full appendFormat:@"\n%@\n", section[@"label"]];
        [reading appendFormat:@"\n%@\n", section[@"label"]];
        for (NSDictionary *item in section[@"items"]) {
            AIHotAppendItem(full, item, NO, YES, NO);
            AIHotAppendItem(reading, item, NO, NO, lead != NSNull.null && [item[@"title"] isEqual:lead[@"title"]]);
            [sources addObject:AIHotSource(item, NO)];
        }
    }
    if ([report[@"flashes"] count]) { [full appendString:@"\n快讯\n"]; [reading appendString:@"\n快讯\n"]; }
    for (NSDictionary *flash in report[@"flashes"]) {
        AIHotAppendItem(full, flash, YES, YES, NO); AIHotAppendItem(reading, flash, YES, NO, NO);
        [sources addObject:AIHotSource(flash, YES)];
    }
    NSString *credit = report[@"attribution"] ? @"署名" : @"数据来源";
    NSString *creditName = report[@"attribution"][@"name"] ?: @"AIHOT";
    [full appendFormat:@"\n%@：%@\n日报：%@", credit, creditName, canonical];
    if (report[@"attribution"]) [full appendFormat:@"\n署名链接：%@", report[@"attribution"][@"url"]];
    [reading appendFormat:@"\n%@：%@。来源链接见手机。", credit, creditName];
    if (!AIHotTextFits(full) || !AIHotTextFits(reading)) AIHOT_FAIL(TIOAIHotDailyTooLarge, @"Daily exceeds 12000 UTF-16 units or 48000 UTF-8 bytes; nothing was truncated.");
    TIOAIHotDailyReport *result = [TIOAIHotDailyReport new];
    result.stableID = [@"daily-" stringByAppendingString:requestedDate]; result.date = requestedDate; result.title = title;
    result.generatedAt = report[@"generatedAt"]; result.windowStart = report[@"windowStart"]; result.windowEnd = report[@"windowEnd"];
    result.canonicalURL = canonical; result.rssSourceURL = @"https://aihot.news/feed/daily.xml"; result.metadata = report; result.sources = sources; result.fullText = full; result.readingText = reading;
    return result;
#undef AIHOT_FAIL
}

@interface AIHotRequest : NSObject<TIOAIHotDailyCancellable>
@property(nonatomic, copy, nullable) dispatch_block_t cancellation;
- (void)detach;
@end
@implementation AIHotRequest
- (void)cancel { dispatch_block_t block; @synchronized(self) { block = self.cancellation; self.cancellation = nil; } if (block) block(); }
- (void)detach { @synchronized(self) { self.cancellation = nil; } }
@end

@interface AIHotSessionTransport : NSObject<TIOAIHotDailyTransport, NSURLSessionDataDelegate>
@property(nonatomic) NSURLSessionConfiguration *configuration;
@property(nonatomic) NSURLSession *session;
@property(nonatomic) NSURLSessionDataTask *task;
@property(nonatomic) NSMutableData *data;
@property(nonatomic) NSHTTPURLResponse *response;
@property(nonatomic) NSUInteger maximumBytes;
@property(nonatomic, copy) TIOAIHotDailyTransportCompletion completion;
@end
@implementation AIHotSessionTransport
- (id<TIOAIHotDailyCancellable>)startRequest:(NSURLRequest *)request maximumBytes:(NSUInteger)maximumBytes completion:(TIOAIHotDailyTransportCompletion)completion {
    AIHotSessionTransport *operation = [AIHotSessionTransport new];
    operation.configuration = self.configuration;
    return [operation beginRequest:request maximumBytes:maximumBytes completion:completion];
}
- (id<TIOAIHotDailyCancellable>)beginRequest:(NSURLRequest *)request maximumBytes:(NSUInteger)maximumBytes completion:(TIOAIHotDailyTransportCompletion)completion {
    self.maximumBytes = maximumBytes; self.completion = completion; self.data = [NSMutableData new];
    self.response = nil;
    NSOperationQueue *queue = [NSOperationQueue new]; queue.maxConcurrentOperationCount = 1;
    self.session = [NSURLSession sessionWithConfiguration:self.configuration delegate:self delegateQueue:queue];
    self.task = [self.session dataTaskWithRequest:request];
    NSURLSessionDataTask *task = self.task;
    AIHotRequest *token = [AIHotRequest new]; token.cancellation = ^{ [task cancel]; };
    [task resume]; return token;
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task willPerformHTTPRedirection:(NSHTTPURLResponse *)response newRequest:(NSURLRequest *)request completionHandler:(void (^)(NSURLRequest * _Nullable))completionHandler {
    (void)session; (void)task; (void)response; (void)request;
    completionHandler(nil);
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveResponse:(NSURLResponse *)response completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    (void)session;
    if (task != self.task) { completionHandler(NSURLSessionResponseCancel); return; }
    if (![response isKindOfClass:NSHTTPURLResponse.class]) { completionHandler(NSURLSessionResponseCancel); return; }
    self.response = (NSHTTPURLResponse *)response;
    if (response.expectedContentLength > (int64_t)self.maximumBytes) {
        completionHandler(NSURLSessionResponseCancel);
        [self finish:AIHotError(TIOAIHotDailyTooLarge, @"Response Content-Length exceeds limit.")];
    } else completionHandler(NSURLSessionResponseAllow);
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveData:(NSData *)data {
    (void)session;
    if (task != self.task || !self.completion) return;
    if (data.length > self.maximumBytes - self.data.length) { [task cancel]; [self finish:AIHotError(TIOAIHotDailyTooLarge, @"Streaming response exceeds limit.")]; }
    else [self.data appendData:data];
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    (void)session; if (task == self.task) [self finish:error];
}
- (void)finish:(NSError *)error {
    TIOAIHotDailyTransportCompletion completion = self.completion;
    if (!completion) return;
    self.completion = nil;
    NSData *data = [self.data copy]; NSHTTPURLResponse *response = self.response;
    self.data = nil; self.task = nil; self.response = nil;
    [self.session finishTasksAndInvalidate]; self.session = nil;
    completion(data, response, error);
}
@end

@interface TIOAIHotDailyClient ()
@property(nonatomic) id<TIOAIHotDailyTransport> transport;
@property(nonatomic, copy) NSDate *(^clock)(void);
@property(nonatomic) NSTimeInterval timeout;
@property(nonatomic) dispatch_queue_t queue, completionQueue;
@property(nonatomic) NSMutableDictionary<NSString *, NSDictionary *> *cache;
@property(nonatomic) NSMutableArray<NSString *> *cacheOrder;
@property(nonatomic) AIHotRequest *active;
@end
@implementation TIOAIHotDailyClient
- (instancetype)init {
    return [self initWithSessionConfiguration:NSURLSessionConfiguration.ephemeralSessionConfiguration clock:^{ return [NSDate date]; } completionQueue:dispatch_get_main_queue()];
}
- (instancetype)initWithSessionConfiguration:(NSURLSessionConfiguration *)configuration clock:(NSDate *(^)(void))clock completionQueue:(dispatch_queue_t)completionQueue {
    AIHotSessionTransport *transport = [AIHotSessionTransport new];
    transport.configuration = [configuration copy]; transport.configuration.URLCache = nil;
    transport.configuration.HTTPCookieStorage = nil; transport.configuration.URLCredentialStorage = nil; transport.configuration.HTTPShouldSetCookies = NO;
    transport.configuration.timeoutIntervalForRequest = AIHotTimeout;
    transport.configuration.timeoutIntervalForResource = AIHotTimeout;
    return [self initWithTransport:transport clock:clock timeout:AIHotTimeout completionQueue:completionQueue];
}
- (instancetype)initWithTransport:(id<TIOAIHotDailyTransport>)transport clock:(NSDate *(^)(void))clock timeout:(NSTimeInterval)timeout completionQueue:(dispatch_queue_t)completionQueue {
    if ((self = [super init])) {
        _transport = transport; _clock = [clock copy]; _timeout = isfinite(timeout) && timeout > 0 ? MIN(timeout, AIHotTimeout) : AIHotTimeout;
        _queue = dispatch_queue_create("io.turbo.aihot.daily", DISPATCH_QUEUE_SERIAL); _completionQueue = completionQueue;
        _cache = [NSMutableDictionary new]; _cacheOrder = [NSMutableArray new];
    } return self;
}
- (id<TIOAIHotDailyCancellable>)fetchTodayWithCompletion:(TIOAIHotDailyCompletion)completion {
    return [self fetchDate:TIOAIHotDailyShanghaiDate(self.clock()) completion:completion];
}
- (id<TIOAIHotDailyCancellable>)fetchDate:(NSString *)date completion:(TIOAIHotDailyCompletion)completion {
    date = [date copy]; completion = [completion copy];
    AIHotRequest *token = [AIHotRequest new];
    __block BOOL finished = NO;
    __block id<TIOAIHotDailyCancellable> network;
    __block dispatch_source_t timer;
    void (^finish)(TIOAIHotDailyReport *, NSError *) = ^(TIOAIHotDailyReport *report, NSError *error) {
        if (finished) return;
        finished = YES; [token detach];
        if (timer) { dispatch_source_cancel(timer); timer = nil; }
        if (error) [network cancel]; network = nil;
        if (self.active == token) self.active = nil;
        dispatch_async(self.completionQueue, ^{ completion(report, error); });
    };
    token.cancellation = ^{ dispatch_async(self.queue, ^{ finish(nil, AIHotError(TIOAIHotDailyCancelled, @"Daily request cancelled.")); }); };
    dispatch_async(self.queue, ^{
        if (finished) return;
        if (!AIHotDateValid(date)) { finish(nil, AIHotError(TIOAIHotDailyInvalidDate, @"Invalid requested calendar date.")); return; }
        if (self.active) { finish(nil, AIHotError(TIOAIHotDailyBusy, @"Only one daily request may be active.")); return; }
        self.active = token;
        NSString *url = [@"https://aihot.news/api/v1/dailies/" stringByAppendingString:date];
        NSDictionary *cached = self.cache[url];
        NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url] cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:self.timeout];
        [request setValue:@"application/json" forHTTPHeaderField:@"Accept"];
        [request setValue:@"TurboIO-AIHotDaily/1.0" forHTTPHeaderField:@"User-Agent"];
        if (cached[@"etag"]) [request setValue:cached[@"etag"] forHTTPHeaderField:@"If-None-Match"];
        timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, self.queue);
        dispatch_source_set_timer(timer, dispatch_time(DISPATCH_TIME_NOW, (int64_t)(self.timeout * NSEC_PER_SEC)), DISPATCH_TIME_FOREVER, NSEC_PER_MSEC);
        dispatch_source_set_event_handler(timer, ^{ finish(nil, AIHotError(TIOAIHotDailyTimedOut, @"Daily request timed out.")); });
        dispatch_resume(timer);
        network = [self.transport startRequest:request maximumBytes:TIOAIHotDailyMaximumResponseBytes completion:^(NSData *data, NSHTTPURLResponse *response, NSError *error) {
            dispatch_async(self.queue, ^{
                if (finished) return;
                if (error) {
                    if ([error.domain isEqual:TIOAIHotDailyErrorDomain]) { finish(nil, error); return; }
                    TIOAIHotDailyErrorCode code = [error.domain isEqual:NSURLErrorDomain] && error.code == NSURLErrorTimedOut ? TIOAIHotDailyTimedOut : TIOAIHotDailyNetworkError;
                    NSError *mapped = [NSError errorWithDomain:TIOAIHotDailyErrorDomain code:code userInfo:@{NSLocalizedDescriptionKey:@"Official daily transport failed.", NSUnderlyingErrorKey:error}];
                    finish(nil, mapped); return;
                }
                if (![response isKindOfClass:NSHTTPURLResponse.class] || ![response.URL.absoluteString isEqual:url]) { finish(nil, AIHotError(TIOAIHotDailyInvalidPayload, @"Unexpected response URL or type.")); return; }
                NSInteger status = response.statusCode;
                if (status == 304) { finish(cached[@"report"], cached[@"report"] ? nil : AIHotError(TIOAIHotDailyNoValidatedCache, @"304 without validated URL-scoped cache.")); return; }
                if (status != 200) {
                    NSMutableDictionary *info = [@{NSLocalizedDescriptionKey: status == 404 ? @"Daily not published for requested date." : @"Official daily HTTP failure.", @"httpStatus":@(status)} mutableCopy];
                    if (status == 429 || status == 503) {
                        NSString *retry = [response valueForHTTPHeaderField:@"Retry-After"];
                        if (!AIHotString(retry) || retry.length > 512) retry = nil;
                        NSScanner *scanner = [NSScanner scannerWithString:retry ?: @""]; double seconds;
                        if ([scanner scanDouble:&seconds] && scanner.isAtEnd && isfinite(seconds) && seconds >= 0) info[TIOAIHotDailyRetryAfterKey] = @(seconds);
                        else if (retry) {
                            NSDateFormatter *f = [NSDateFormatter new]; f.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"]; f.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0]; f.dateFormat = @"EEE, dd MMM yyyy HH:mm:ss zzz";
                            NSDate *retryDate = [f dateFromString:retry];
                            if (retryDate) info[TIOAIHotDailyRetryAfterKey] = @(MAX(0, [retryDate timeIntervalSinceDate:self.clock()]));
                        }
                    }
                    finish(nil, [NSError errorWithDomain:TIOAIHotDailyErrorDomain code:status == 404 ? TIOAIHotDailyNotPublished : TIOAIHotDailyHTTPError userInfo:info]); return;
                }
                NSString *mime = response.MIMEType.lowercaseString;
                if (![mime isEqual:@"application/json"]) { finish(nil, AIHotError(TIOAIHotDailyInvalidPayload, @"Official daily response is not JSON.")); return; }
                NSError *parseError;
                TIOAIHotDailyReport *report = TIOAIHotDailyParse(data, date, &parseError);
                if (!report) { finish(nil, parseError); return; }
                NSString *etag = [response valueForHTTPHeaderField:@"ETag"];
                [self.cache removeObjectForKey:url]; [self.cacheOrder removeObject:url];
                if (AIHotString(etag) && etag.length <= 512 && [etag rangeOfCharacterFromSet:NSCharacterSet.newlineCharacterSet].location == NSNotFound) {
                    self.cache[url] = @{@"etag":etag, @"report":report}; [self.cacheOrder addObject:url];
                    if (self.cacheOrder.count > 4) { [self.cache removeObjectForKey:self.cacheOrder.firstObject]; [self.cacheOrder removeObjectAtIndex:0]; }
                }
                finish(report, nil);
            });
        }];
    });
    return token;
}
@end
