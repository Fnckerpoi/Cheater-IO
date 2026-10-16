#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, TIONewsSourceType) {
    TIONewsSourceTypeRSS = 0,         // 标准 RSS / Atom 订阅
    TIONewsSourceTypeMCP = 1,         // HTTP / SSE 远程只读 MCP 来源
    TIONewsSourceTypeSearch = 2,      // 原生 TinyFish 搜索 + 模型生成简报
    TIONewsSourceTypeAIHot = 3        // AIHOT 官方日报专线（已验证）
};

// 单份生成的提词器文稿
@interface TIONewsArticle : NSObject <NSSecureCoding>
@property(nonatomic, copy, readonly) NSString *articleID;     // 唯一文稿标识
@property(nonatomic, copy, readonly) NSString *sourceID;      // 所属配置源 ID
@property(nonatomic, copy, readonly) NSString *sourceName;    // 来源名称（如 "AIHOT日报"）
@property(nonatomic, copy, readonly) NSString *title;         // 文稿标题
@property(nonatomic, copy, readonly) NSString *date;          // 版期日期 (yyyy-MM-dd)
@property(nonatomic, copy, readonly) NSString *readingText;   // 提词器滚动正文 (UTF-8, 含来源署名)
@property(nonatomic, copy, readonly) NSString *canonicalURL;  // 溯源 URL
@property(nonatomic, readonly) NSUInteger characterCount;     // 字符数
@property(nonatomic, readonly) NSTimeInterval createdAt;      // 生成时间戳

- (instancetype)initWithSourceID:(NSString *)sourceID
                      sourceName:(NSString *)sourceName
                           title:(NSString *)title
                            date:(NSString *)date
                     readingText:(NSString *)readingText
                    canonicalURL:(nullable NSString *)canonicalURL;
@end

// 信息源配置项
@interface TIONewsSourceConfig : NSObject <NSSecureCoding, NSCopying>
@property(nonatomic, copy) NSString *sourceID;                // 唯一配置 ID (UUID)
@property(nonatomic, copy) NSString *name;                    // 展示名称 (如 "AI科技早报")
@property(nonatomic) TIONewsSourceType type;                  // 源类型
@property(nonatomic, copy) NSString *endpointOrTopic;         // RSS URL / MCP URL / 搜索关键词
@property(nonatomic) BOOL enabled;                            // 是否启用
@property(nonatomic, copy, nullable) NSString *customPrompt;  // 自定义提示词（类型为 Search 或 MCP 时可选）

- (NSDictionary *)dictionaryRepresentation;
+ (nullable instancetype)fromDictionary:(NSDictionary *)dict;
@end

typedef void (^TIONewsFetchCompletion)(TIONewsArticle * _Nullable article, NSError * _Nullable error);

// 多源抓取引擎接口
@interface TIONewsFetcher : NSObject
+ (instancetype)sharedFetcher;
- (void)fetchArticleFromSource:(TIONewsSourceConfig *)config
                          date:(NSDate *)now
                    completion:(TIONewsFetchCompletion)completion;
@end

NS_ASSUME_NONNULL_END
