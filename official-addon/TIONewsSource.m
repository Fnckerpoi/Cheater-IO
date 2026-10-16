#import "TIONewsSource.h"
#import "AIHotDaily.h"
#import <CommonCrypto/CommonDigest.h>

@implementation TIONewsArticle
+ (BOOL)supportsSecureCoding { return YES; }

- (instancetype)initWithSourceID:(NSString *)sourceID
                      sourceName:(NSString *)sourceName
                           title:(NSString *)title
                            date:(NSString *)date
                     readingText:(NSString *)readingText
                    canonicalURL:(NSString *)canonicalURL {
    if ((self = [super init])) {
        _sourceID = [sourceID copy] ?: @"";
        _sourceName = [sourceName copy] ?: @"";
        _title = [title copy] ?: @"";
        _date = [date copy] ?: @"";
        _readingText = [readingText copy] ?: @"";
        _canonicalURL = [canonicalURL copy] ?: @"";
        _characterCount = _readingText.length;
        _createdAt = NSProcessInfo.processInfo.systemUptime;

        // 基于 sourceID + date 生成稳定 ID
        NSString *seed = [NSString stringWithFormat:@"%@:%@", _sourceID, _date];
        unsigned char hash[CC_SHA256_DIGEST_LENGTH];
        const char *cStr = seed.UTF8String;
        CC_SHA256(cStr, (CC_LONG)strlen(cStr), hash);
        NSMutableString *hex = [NSMutableString new];
        for (int i = 0; i < 8; i++) [hex appendFormat:@"%02x", hash[i]];
        _articleID = [NSString stringWithFormat:@"article-%@-%@", _date, hex];
    }
    return self;
}

- (void)encodeWithCoder:(NSCoder *)coder {
    [coder encodeObject:_articleID forKey:@"articleID"];
    [coder encodeObject:_sourceID forKey:@"sourceID"];
    [coder encodeObject:_sourceName forKey:@"sourceName"];
    [coder encodeObject:_title forKey:@"title"];
    [coder encodeObject:_date forKey:@"date"];
    [coder encodeObject:_readingText forKey:@"readingText"];
    [coder encodeObject:_canonicalURL forKey:@"canonicalURL"];
}

- (nullable instancetype)initWithCoder:(NSCoder *)coder {
    if ((self = [super init])) {
        _articleID = [coder decodeObjectOfClass:NSString.class forKey:@"articleID"] ?: @"";
        _sourceID = [coder decodeObjectOfClass:NSString.class forKey:@"sourceID"] ?: @"";
        _sourceName = [coder decodeObjectOfClass:NSString.class forKey:@"sourceName"] ?: @"";
        _title = [coder decodeObjectOfClass:NSString.class forKey:@"title"] ?: @"";
        _date = [coder decodeObjectOfClass:NSString.class forKey:@"date"] ?: @"";
        _readingText = [coder decodeObjectOfClass:NSString.class forKey:@"readingText"] ?: @"";
        _canonicalURL = [coder decodeObjectOfClass:NSString.class forKey:@"canonicalURL"] ?: @"";
        _characterCount = _readingText.length;
        _createdAt = NSProcessInfo.processInfo.systemUptime;
    }
    return self;
}
@end

@implementation TIONewsSourceConfig
+ (BOOL)supportsSecureCoding { return YES; }

- (id)copyWithZone:(nullable NSZone *)zone {
    TIONewsSourceConfig *c = [[[self class] allocWithZone:zone] init];
    c.sourceID = self.sourceID;
    c.name = self.name;
    c.type = self.type;
    c.endpointOrTopic = self.endpointOrTopic;
    c.enabled = self.enabled;
    c.customPrompt = self.customPrompt;
    return c;
}

- (NSDictionary *)dictionaryRepresentation {
    return @{
        @"sourceID": self.sourceID ?: @"",
        @"name": self.name ?: @"",
        @"type": @(self.type),
        @"endpointOrTopic": self.endpointOrTopic ?: @"",
        @"enabled": @(self.enabled),
        @"customPrompt": self.customPrompt ?: @""
    };
}

+ (nullable instancetype)fromDictionary:(NSDictionary *)dict {
    if (![dict isKindOfClass:NSDictionary.class]) return nil;
    TIONewsSourceConfig *c = [TIONewsSourceConfig new];
    c.sourceID = [dict[@"sourceID"] isKindOfClass:NSString.class] ? dict[@"sourceID"] : NSUUID.UUID.UUIDString;
    c.name = [dict[@"name"] isKindOfClass:NSString.class] ? dict[@"name"] : @"信息源";
    c.type = [dict[@"type"] respondsToSelector:@selector(integerValue)] ? [dict[@"type"] integerValue] : TIONewsSourceTypeRSS;
    c.endpointOrTopic = [dict[@"endpointOrTopic"] isKindOfClass:NSString.class] ? dict[@"endpointOrTopic"] : @"";
    c.enabled = [dict[@"enabled"] respondsToSelector:@selector(boolValue)] ? [dict[@"enabled"] boolValue] : YES;
    c.customPrompt = [dict[@"customPrompt"] isKindOfClass:NSString.class] ? dict[@"customPrompt"] : nil;
    return c;
}

- (void)encodeWithCoder:(NSCoder *)coder {
    [coder encodeObject:self.sourceID forKey:@"sourceID"];
    [coder encodeObject:self.name forKey:@"name"];
    [coder encodeInteger:self.type forKey:@"type"];
    [coder encodeObject:self.endpointOrTopic forKey:@"endpointOrTopic"];
    [coder encodeBool:self.enabled forKey:@"enabled"];
    [coder encodeObject:self.customPrompt forKey:@"customPrompt"];
}

- (nullable instancetype)initWithCoder:(NSCoder *)coder {
    if ((self = [super init])) {
        _sourceID = [coder decodeObjectOfClass:NSString.class forKey:@"sourceID"] ?: NSUUID.UUID.UUIDString;
        _name = [coder decodeObjectOfClass:NSString.class forKey:@"name"] ?: @"信息源";
        _type = [coder decodeIntegerForKey:@"type"];
        _endpointOrTopic = [coder decodeObjectOfClass:NSString.class forKey:@"endpointOrTopic"] ?: @"";
        _enabled = [coder decodeBoolForKey:@"enabled"];
        _customPrompt = [coder decodeObjectOfClass:NSString.class forKey:@"customPrompt"];
    }
    return self;
}
@end

@interface TIONewsFetcher () <NSXMLParserDelegate>
@property(nonatomic, strong) TIOAIHotDailyClient *aihotClient;
// XML Parser temp state
@property(nonatomic, strong) NSMutableArray<NSDictionary *> *parsedItems;
@property(nonatomic, strong) NSMutableDictionary *currentItem;
@property(nonatomic, strong) NSMutableString *currentElementValue;
@end

@implementation TIONewsFetcher

+ (instancetype)sharedFetcher {
    static TIONewsFetcher *instance;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [TIONewsFetcher new];
        instance.aihotClient = [TIOAIHotDailyClient new];
    });
    return instance;
}

- (void)fetchArticleFromSource:(TIONewsSourceConfig *)config
                          date:(NSDate *)now
                    completion:(TIONewsFetchCompletion)completion {
    if (!config.enabled) {
        if (completion) completion(nil, [NSError errorWithDomain:@"TIONewsSource" code:1 userInfo:@{NSLocalizedDescriptionKey:@"源未启用"}]);
        return;
    }

    NSString *todayStr = TIOAIHotDailyShanghaiDate(now);

    switch (config.type) {
        case TIONewsSourceTypeAIHot: {
            [self.aihotClient fetchTodayWithCompletion:^(TIOAIHotDailyReport * _Nullable report, NSError * _Nullable error) {
                if (error || !report) {
                    if (completion) completion(nil, error);
                    return;
                }
                NSString *manuscript = [NSString stringWithFormat:@"%@ · %@\n\n%@\n\n官方来源：%@\n",
                                        config.name, report.date, report.readingText, report.canonicalURL];
                TIONewsArticle *article = [[TIONewsArticle alloc] initWithSourceID:config.sourceID
                                                                        sourceName:config.name
                                                                             title:[NSString stringWithFormat:@"%@ · %@", config.name, report.date]
                                                                              date:report.date
                                                                       readingText:manuscript
                                                                      canonicalURL:report.canonicalURL];
                if (completion) completion(article, nil);
            }];
            break;
        }
        case TIONewsSourceTypeRSS: {
            [self fetchRSSWithURLString:config.endpointOrTopic sourceConfig:config dateStr:todayStr completion:completion];
            break;
        }
        case TIONewsSourceTypeMCP: {
            [self fetchMCPWithURLString:config.endpointOrTopic sourceConfig:config dateStr:todayStr completion:completion];
            break;
        }
        case TIONewsSourceTypeSearch: {
            [self fetchSearchWithTopic:config.endpointOrTopic sourceConfig:config dateStr:todayStr completion:completion];
            break;
        }
    }
}

#pragma mark - RSS / Atom 抓取解析
- (void)fetchRSSWithURLString:(NSString *)urlString
                 sourceConfig:(TIONewsSourceConfig *)config
                      dateStr:(NSString *)dateStr
                   completion:(TIONewsFetchCompletion)completion {
    NSURL *url = [NSURL URLWithString:urlString];
    if (!url || (![url.scheme isEqualToString:@"http"] && ![url.scheme isEqualToString:@"https"])) {
        if (completion) completion(nil, [NSError errorWithDomain:@"TIONewsSource" code:2 userInfo:@{NSLocalizedDescriptionKey:@"非法 RSS URL"}]);
        return;
    }

    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:url cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:15];
    [req setValue:@"TurboIO-NewsHub/1.0" forHTTPHeaderField:@"User-Agent"];

    [[[NSURLSession sharedSession] dataTaskWithRequest:req completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
        (void)response;
        if (error || !data) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (completion) completion(nil, error ?: [NSError errorWithDomain:@"TIONewsSource" code:3 userInfo:@{NSLocalizedDescriptionKey:@"数据为空"}]);
            });
            return;
        }

        NSXMLParser *parser = [[NSXMLParser alloc] initWithData:data];
        TIONewsFetcher *helper = [TIONewsFetcher new];
        helper.parsedItems = [NSMutableArray new];
        parser.delegate = helper;

        if (![parser parse] || helper.parsedItems.count == 0) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (completion) completion(nil, [NSError errorWithDomain:@"TIONewsSource" code:4 userInfo:@{NSLocalizedDescriptionKey:@"RSS 解析失败或无条目"}]);
            });
            return;
        }

        // 整理前 8 条
        NSMutableString *body = [NSMutableString stringWithFormat:@"%@ · %@ 订阅播报\n\n", config.name, dateStr];
        NSUInteger count = 0;
        for (NSDictionary *item in helper.parsedItems) {
            if (count >= 8) break;
            NSString *itemTitle = item[@"title"] ?: @"";
            NSString *desc = item[@"description"] ?: @"";
            // 简单去标签
            desc = [desc stringByReplacingOccurrencesOfString:@"<[^>]+>" withString:@"" options:NSRegularExpressionSearch range:NSMakeRange(0, desc.length)];
            desc = [desc stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
            if (desc.length > 200) desc = [[desc substringToIndex:200] stringByAppendingString:@"..."];

            [body appendFormat:@"【%lu】%@\n%@\n\n", (unsigned long)(count + 1), itemTitle, desc];
            count++;
        }
        [body appendFormat:@"来源地址：%@\n", urlString];

        TIONewsArticle *article = [[TIONewsArticle alloc] initWithSourceID:config.sourceID
                                                                sourceName:config.name
                                                                     title:[NSString stringWithFormat:@"%@ · %@", config.name, dateStr]
                                                                      date:dateStr
                                                               readingText:body
                                                              canonicalURL:urlString];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) completion(article, nil);
        });
    }] resume];
}

#pragma mark - HTTP MCP 抓取解析
- (void)fetchMCPWithURLString:(NSString *)urlString
                 sourceConfig:(TIONewsSourceConfig *)config
                      dateStr:(NSString *)dateStr
                   completion:(TIONewsFetchCompletion)completion {
    NSURL *url = [NSURL URLWithString:urlString];
    if (!url) {
        if (completion) completion(nil, [NSError errorWithDomain:@"TIONewsSource" code:5 userInfo:@{NSLocalizedDescriptionKey:@"非法 MCP URL"}]);
        return;
    }

    // 发送标准 JSON-RPC 2.0 tools/call 请求获取最新资讯
    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:url cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:15];
    req.HTTPMethod = @"POST";
    [req setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    [req setValue:@"TurboIO-NewsHub/1.0" forHTTPHeaderField:@"User-Agent"];

    NSDictionary *rpcBody = @{
        @"jsonrpc": @"2.0",
        @"id": @(1),
        @"method": @"tools/call",
        @"params": @{
            @"name": @"get_daily_news",
            @"arguments": @{@"date": dateStr}
        }
    };
    req.HTTPBody = [NSJSONSerialization dataWithJSONObject:rpcBody options:0 error:nil];

    [[[NSURLSession sharedSession] dataTaskWithRequest:req completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
        (void)response;
        if (error || !data) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (completion) completion(nil, error);
            });
            return;
        }

        id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        NSString *contentStr = nil;
        if ([json isKindOfClass:NSDictionary.class]) {
            id res = json[@"result"];
            if ([res isKindOfClass:NSDictionary.class] && [res[@"content"] isKindOfClass:NSArray.class]) {
                for (id item in res[@"content"]) {
                    if ([item isKindOfClass:NSDictionary.class] && [item[@"type"] isEqualToString:@"text"]) {
                        contentStr = item[@"text"];
                        break;
                    }
                }
            } else if ([res isKindOfClass:NSString.class]) {
                contentStr = res;
            }
        }

        if (!contentStr.length) {
            // 若非标准 tools/call，兼容直接 JSON 字典模式
            contentStr = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        }

        NSString *manuscript = [NSString stringWithFormat:@"%@ · %@ (MCP 汇报)\n\n%@\n\n服务来源：%@\n",
                                config.name, dateStr, contentStr ?: @"无新内容", urlString];

        TIONewsArticle *article = [[TIONewsArticle alloc] initWithSourceID:config.sourceID
                                                                sourceName:config.name
                                                                     title:[NSString stringWithFormat:@"%@ · %@", config.name, dateStr]
                                                                      date:dateStr
                                                               readingText:manuscript
                                                              canonicalURL:urlString];
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) completion(article, nil);
        });
    }] resume];
}

#pragma mark - 原生 TinyFish 搜索生成
- (void)fetchSearchWithTopic:(NSString *)topic
                sourceConfig:(TIONewsSourceConfig *)config
                     dateStr:(NSString *)dateStr
                  completion:(TIONewsFetchCompletion)completion {
    NSError *error = [NSError errorWithDomain:@"TIONewsSourceUnsupported" code:1
                                    userInfo:@{NSLocalizedDescriptionKey:@"NewsHub 的 TinyFish 搜索生成尚未接入真实检索与模型整理流程，未生成文稿。可使用独立 TinyFish 新闻阅读页；本模块不会返回模板新闻或虚构来源。"}];
    dispatch_async(dispatch_get_main_queue(), ^{
        if (completion) completion(nil, error);
    });
}

#pragma mark - NSXMLParserDelegate
- (void)parser:(NSXMLParser *)parser didStartElement:(NSString *)elementName namespaceURI:(nullable NSString *)namespaceURI qualifiedName:(nullable NSString *)qName attributes:(NSDictionary<NSString *, NSString *> *)attributeDict {
    if ([elementName isEqualToString:@"item"] || [elementName isEqualToString:@"entry"]) {
        self.currentItem = [NSMutableDictionary new];
    }
    self.currentElementValue = [NSMutableString new];
}

- (void)parser:(NSXMLParser *)parser foundCharacters:(NSString *)string {
    [self.currentElementValue appendString:string];
}

- (void)parser:(NSXMLParser *)parser didEndElement:(NSString *)elementName namespaceURI:(nullable NSString *)namespaceURI qualifiedName:(nullable NSString *)qName {
    if (self.currentItem) {
        NSString *trimmed = [self.currentElementValue stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
        if ([elementName isEqualToString:@"title"]) {
            self.currentItem[@"title"] = trimmed;
        } else if ([elementName isEqualToString:@"description"] || [elementName isEqualToString:@"summary"] || [elementName isEqualToString:@"content"]) {
            NSString *existingDesc = self.currentItem[@"description"];
            if (!existingDesc || existingDesc.length == 0) {
                self.currentItem[@"description"] = trimmed;
            }
        } else if ([elementName isEqualToString:@"link"]) {
            self.currentItem[@"link"] = trimmed;
        } else if ([elementName isEqualToString:@"item"] || [elementName isEqualToString:@"entry"]) {
            [self.parsedItems addObject:[self.currentItem copy]];
            self.currentItem = nil;
        }
    }
    self.currentElementValue = nil;
}
@end
