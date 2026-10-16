#import "OfficialManuscriptProbe.h"
#import <CommonCrypto/CommonDigest.h>
#import <CommonCrypto/CommonHMAC.h>
#include <math.h>
#include <stdlib.h>

NSString * const TIOOfficialProbePublicTitle = @"TurboIO 日报入库测试";
NSString * const TIOOfficialProbePublicBody = @"这是一份公开合成测试稿，不是真实新闻。\n用于验证官方提词器稿件的保存、重新打开和持久化。\n校验码：7392。";

static NSString *TIOProbeHex(const unsigned char *bytes, NSUInteger length) {
    NSMutableString *hex = [NSMutableString stringWithCapacity:length * 2];
    for (NSUInteger i = 0; i < length; i++) [hex appendFormat:@"%02x", bytes[i]];
    return [hex copy];
}

static NSString *TIOProbeFixtureDigest(void) {
    NSData *data = [[NSString stringWithFormat:@"%@\n%@", TIOOfficialProbePublicTitle,
                     TIOOfficialProbePublicBody] dataUsingEncoding:NSUTF8StringEncoding];
    unsigned char hash[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(data.bytes, (CC_LONG)data.length, hash);
    return TIOProbeHex(hash, sizeof(hash));
}

static BOOL TIOProbeIdentifier(NSString *value, NSUInteger limit, BOOL channel) {
    if (![value isKindOfClass:NSString.class] || !value.length || value.length > limit) return NO;
    NSString *alphabet = channel ? @"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_./-" :
                                  @"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    return [value rangeOfCharacterFromSet:[[NSCharacterSet characterSetWithCharactersInString:alphabet]
                                          invertedSet]].location == NSNotFound;
}

static NSString *TIOProbeNormalize(NSString *value) {
    return [[value stringByReplacingOccurrencesOfString:@"\r\n" withString:@"\n"]
                   stringByReplacingOccurrencesOfString:@"\r" withString:@"\n"];
}

typedef struct {
    NSUInteger nodes;
    BOOL title;
    BOOL body;
    BOOL exceeded;
    BOOL hasNonIDString;
} TIOProbeMatch;

static void TIOProbeVisit(id value, NSUInteger depth, TIOProbeMatch *match, NSString *identifier) {
    if (depth > 4 || ++match->nodes > 64) { match->exceeded = YES; return; }
    if ([value isKindOfClass:NSString.class]) {
        if (![value isEqual:identifier]) match->hasNonIDString = YES;
        if ([value length] > 1024) return;
        NSString *text = TIOProbeNormalize(value);
        match->title |= [text isEqual:TIOOfficialProbePublicTitle];
        match->body |= [text isEqual:TIOOfficialProbePublicBody];
    } else if ([value isKindOfClass:NSDictionary.class]) {
        if ([value count] > 64) { match->exceeded = YES; return; }
        for (id key in value) {
            TIOProbeVisit([value objectForKey:key], depth + 1, match, identifier);
            if (match->exceeded) return;
        }
    } else if ([value isKindOfClass:NSArray.class]) {
        if ([value count] > 64) { match->exceeded = YES; return; }
        for (id child in value) {
            TIOProbeVisit(child, depth + 1, match, identifier);
            if (match->exceeded) return;
        }
    }
}

static NSArray *TIOProbeKeyNames(id arguments) {
    if (![arguments isKindOfClass:NSDictionary.class]) return @[];
    NSMutableArray *keys = [NSMutableArray array];
    for (id key in arguments) {
        if (TIOProbeIdentifier(key, 64, NO)) [keys addObject:key];
    }
    return [keys sortedArrayUsingSelector:@selector(compare:)];
}

@implementation TIOOfficialManuscriptProbe {
    TIOOfficialProbeClock _clock;
    BOOL _armedOnce;
    BOOL _enabled;
    NSTimeInterval _started;
    NSTimeInterval _lastTime;
    NSData *_salt;
    NSString *_publicIDSummary;
    NSMutableArray<NSDictionary *> *_records;
}

- (instancetype)init {
    return [self initWithClock:^{ return NSProcessInfo.processInfo.systemUptime; }];
}

- (instancetype)initWithClock:(TIOOfficialProbeClock)clock {
    NSParameterAssert(clock);
    if ((self = [super init])) {
        _clock = [clock copy];
        _records = [NSMutableArray array];
    }
    return self;
}

- (BOOL)armForPublicTestWithUserConfirmation:(BOOL)confirmed {
    @synchronized (self) {
        if (!confirmed || _armedOnce) return NO;
        NSTimeInterval now = _clock();
        if (!isfinite(now) || now < 0) return NO;
        unsigned char salt[32];
        arc4random_buf(salt, sizeof(salt));
        _salt = [NSData dataWithBytes:salt length:sizeof(salt)];
        _started = _lastTime = now;
        _armedOnce = _enabled = YES;
        return YES;
    }
}

- (void)stop {
    @synchronized (self) {
        _enabled = NO;
        _salt = nil;
        _publicIDSummary = nil;
    }
}

- (BOOL)isActive {
    @synchronized (self) {
        if (!_enabled) return NO;
        NSTimeInterval now = _clock();
        if (!isfinite(now) || now < _lastTime || now - _started >= 120 || _records.count >= 16) {
            [self stop];
            return NO;
        }
        _lastTime = now;
        return YES;
    }
}

- (NSArray<NSDictionary *> *)records {
    @synchronized (self) { return [_records copy]; }
}

- (NSString *)summaryForID:(id)stableID {
    NSString *text;
    if ([stableID isKindOfClass:NSString.class]) text = stableID;
    else if ([stableID isKindOfClass:NSNumber.class]) text = [stableID stringValue];
    if (!text.length || text.length > 128 || !_salt) return nil;
    NSData *data = [text dataUsingEncoding:NSUTF8StringEncoding];
    unsigned char hash[CC_SHA256_DIGEST_LENGTH];
    CCHmac(kCCHmacAlgSHA256, _salt.bytes, _salt.length, data.bytes, data.length, hash);
    return TIOProbeHex(hash, sizeof(hash));
}

- (NSDictionary *)observeChannel:(NSString *)channel method:(NSString *)method
                       arguments:(id)arguments stableID:(id)stableID {
    @synchronized (self) {
        if (!self.active || !TIOProbeIdentifier(channel, 96, YES) ||
            !TIOProbeIdentifier(method, 96, NO)) return nil;
        NSString *rawID = [stableID isKindOfClass:NSString.class] ? stableID :
            ([stableID isKindOfClass:NSNumber.class] ? [stableID stringValue] : nil);
        TIOProbeMatch match = {0};
        TIOProbeVisit(arguments, 0, &match, rawID);
        if (match.exceeded) return nil;
        BOOL fixture = match.title && match.body;
        NSString *identifier = [self summaryForID:stableID];
        BOOL correlated = !match.hasNonIDString && identifier && [identifier isEqual:_publicIDSummary];
        if (!fixture && !correlated) return nil;
        // Bind only the first complete public fixture's host-observed ID.
        if (fixture && identifier && !_publicIDSummary) _publicIDSummary = identifier;
        NSMutableDictionary *record = [@{@"channel": [channel copy], @"method": [method copy],
            @"keys": TIOProbeKeyNames(arguments), @"fixture_match": @(fixture),
            @"correlated_public_id": @(correlated), @"fixture_sha256": TIOProbeFixtureDigest(),
            @"observation_only": @YES} mutableCopy];
        record[@"id_session_hmac_sha256"] = identifier ?: @"none";
        NSDictionary *immutable = [record copy];
        [_records addObject:immutable];
        if (_records.count >= 16) [self stop];
        return immutable;
    }
}

- (void)forwardChannel:(NSString *)channel method:(NSString *)method arguments:(id)arguments
              stableID:(id)stableID originalOperation:(void (^)(void))operation {
    @try { [self observeChannel:channel method:method arguments:arguments stableID:stableID]; }
    @catch (NSException *exception) { (void)exception; [self stop]; }
    operation();
}
@end
