#import "TIONewsScheduler.h"
#import <CommonCrypto/CommonDigest.h>

NSString * const TIONewsJobWaitingRuntimeContext=@"WAITING_RUNTIME_CONTEXT";
NSString * const TIONewsJobUnknownNeedsReconciliation=@"UNKNOWN_NEEDS_RECONCILIATION";
NSString * const TIONewsJobSaved=@"SAVED_CONFIRMED";
NSString * const TIONewsJobRetryNotWritten=@"RETRY_DEFINITELY_NOT_WRITTEN";
NSString * const TIONewsJobUnavailable=@"UNAVAILABLE";
static NSString * const JobsKey=@"io.turboio.newshub.jobs.v1";
static NSString * const SourcesKey=@"io.turboio.newshub.sources";
static NSString * const EnabledKey=@"io.turboio.newshub.scheduler.enabled";
static NSString * const HourKey=@"io.turboio.newshub.scheduler.hour";
static NSString * const MinuteKey=@"io.turboio.newshub.scheduler.minute";
static NSString * const ZoneKey=@"io.turboio.newshub.scheduler.zone";
@implementation TIONewsSaveResult @end

@interface TIONewsScheduler ()
@property(nonatomic,readwrite) TIONSchedulerStatus status;
@property(nonatomic,copy,readwrite) NSString *statusMessage;
@property(nonatomic,strong) NSUserDefaults *defaults;
@property(nonatomic,copy) NSDate *(^clock)(void);
@property(nonatomic,copy) TIONewsScheduledFetch fetcher;
@property(nonatomic,strong) id<TIONewsSaveBridge> saveBridge;
@property(nonatomic,copy) NSString *timeZoneName;
@property(nonatomic,strong) NSTimer *timer;
@property(nonatomic,strong) NSMutableArray<TIONewsSourceConfig *> *sources;
@property(nonatomic,strong) NSMutableDictionary<NSString *,NSMutableDictionary *> *jobs;
@property(nonatomic,strong) NSMutableDictionary<NSString *,NSString *> *activeFetches;
@property(nonatomic,strong) NSMutableDictionary<NSString *,NSString *> *sourceGenerations;
@property(nonatomic,strong) NSMutableDictionary<NSString *,NSDictionary *> *activeSaves;
@end

@implementation TIONewsScheduler
+ (instancetype)sharedScheduler {
    static TIONewsScheduler *scheduler;static dispatch_once_t once;
    dispatch_once(&once,^{scheduler=[self new];});return scheduler;
}
- (instancetype)init {
    return [self initWithDefaults:NSUserDefaults.standardUserDefaults clock:^{return NSDate.date;} fetch:^(TIONewsSourceConfig *source,NSDate *now,TIONewsFetchCompletion done){
        [TIONewsFetcher.sharedFetcher fetchArticleFromSource:source date:now completion:done];
    } bridge:nil];
}
- (instancetype)initWithDefaults:(NSUserDefaults *)defaults clock:(NSDate *(^)(void))clock fetch:(TIONewsScheduledFetch)fetch bridge:(id<TIONewsSaveBridge>)bridge {
    if((self=[super init])){
        _defaults=defaults;_clock=[clock copy];_fetcher=[fetch copy];_saveBridge=bridge;
        _activeFetches=[NSMutableDictionary new];_sourceGenerations=[NSMutableDictionary new];_activeSaves=[NSMutableDictionary new];_jobs=[NSMutableDictionary new];_sources=[NSMutableArray new];
        NSDictionary *saved=[defaults dictionaryForKey:JobsKey];
        for(NSString *key in saved)if([saved[key] isKindOfClass:NSDictionary.class])_jobs[key]=[saved[key] mutableCopy];
        _enabled=[defaults objectForKey:EnabledKey]?[defaults boolForKey:EnabledKey]:YES;
        _scheduledHour=[defaults objectForKey:HourKey]?[defaults integerForKey:HourKey]:9;
        _scheduledMinute=[defaults integerForKey:MinuteKey];
        _timeZoneName=[defaults stringForKey:ZoneKey]?:@"Asia/Shanghai";
        _statusMessage=@"生成与官方保存分别核验；等待唤醒补跑，不保证挂起时到点执行";
        NSData *data=[defaults dataForKey:SourcesKey];
        id items=data?[NSJSONSerialization JSONObjectWithData:data options:0 error:nil]:nil;
        if([items isKindOfClass:NSArray.class])for(id item in items){TIONewsSourceConfig *source=[TIONewsSourceConfig fromDictionary:item];if(source)[_sources addObject:source];}
        for(TIONewsSourceConfig *source in _sources)_sourceGenerations[source.sourceID]=NSUUID.UUID.UUIDString;
        if(!data){
            TIONewsSourceConfig *ai=[TIONewsSourceConfig new];ai.sourceID=@"default-aihot-daily";ai.name=@"AIHOT 每日简报";ai.type=TIONewsSourceTypeAIHot;ai.endpointOrTopic=@"https://aihot.news/api/v1/dailies/";ai.enabled=YES;
            TIONewsSourceConfig *tiny=[TIONewsSourceConfig new];tiny.sourceID=@"default-tinyfish-tech";tiny.name=@"前沿科技速递";tiny.type=TIONewsSourceTypeSearch;tiny.endpointOrTopic=@"人工智能与机器人核心进展";tiny.enabled=YES;
            [self saveSources:@[ai,tiny]];
        }
    }return self;
}
- (NSCalendar *)calendar {
    NSCalendar *calendar=[[NSCalendar alloc]initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
    calendar.timeZone=[NSTimeZone timeZoneWithName:self.timeZoneName]?:[NSTimeZone timeZoneWithName:@"Asia/Shanghai"];return calendar;
}
- (NSString *)dateKey:(NSDate *)date {
    NSDateComponents *c=[[self calendar] components:NSCalendarUnitYear|NSCalendarUnitMonth|NSCalendarUnitDay fromDate:date];
    return [NSString stringWithFormat:@"%04ld-%02ld-%02ld",(long)c.year,(long)c.month,(long)c.day];
}
- (NSString *)todayDateStr { return [self dateKey:self.clock()]; }
- (NSString *)sourceKey:(NSString *)sourceID date:(NSString *)date { return [NSString stringWithFormat:@"%lu:%@:%@",(unsigned long)sourceID.length,sourceID,date]; }
- (BOOL)persist { [self.defaults setObject:self.jobs forKey:JobsKey];return [self.defaults synchronize]; }
- (void)setEnabled:(BOOL)enabled { _enabled=enabled;[self.defaults setBool:enabled forKey:EnabledKey];if(enabled)[self start];else [self stop]; }
- (void)setScheduledHour:(NSInteger)hour { if(hour<0||hour>23)return;_scheduledHour=hour;[self.defaults setInteger:hour forKey:HourKey]; }
- (void)setScheduledMinute:(NSInteger)minute { if(minute<0||minute>59)return;_scheduledMinute=minute;[self.defaults setInteger:minute forKey:MinuteKey]; }
- (void)setTimeZoneName:(NSString *)name { if(![NSTimeZone timeZoneWithName:name])return;_timeZoneName=[name copy];[self.defaults setObject:name forKey:ZoneKey]; }
- (NSArray *)allSources { NSMutableArray *copies=[NSMutableArray new];for(TIONewsSourceConfig *source in self.sources)[copies addObject:[source copy]];return copies; }
- (void)saveSources:(NSArray<TIONewsSourceConfig *> *)sources {
    NSMutableArray *unique=[NSMutableArray new],*data=[NSMutableArray new];NSMutableSet *ids=[NSMutableSet new];
    for(TIONewsSourceConfig *source in sources){if(!source.sourceID.length||[ids containsObject:source.sourceID])continue;[ids addObject:source.sourceID];[unique addObject:[source copy]];[data addObject:source.dictionaryRepresentation];}
    NSMutableDictionary *generations=[NSMutableDictionary new];
    for(TIONewsSourceConfig *source in unique){
        TIONewsSourceConfig *previous=nil;for(TIONewsSourceConfig *candidate in self.sources)if([candidate.sourceID isEqual:source.sourceID]){previous=candidate;break;}
        BOOL unchanged=previous&&[previous.dictionaryRepresentation isEqual:source.dictionaryRepresentation];
        generations[source.sourceID]=unchanged?(self.sourceGenerations[source.sourceID]?:NSUUID.UUID.UUIDString):NSUUID.UUID.UUIDString;
    }
    self.sourceGenerations=generations;
    for(NSString *key in self.activeFetches.allKeys){
        NSString *sourceID=self.jobs[key][@"sourceID"];
        if(![self.activeFetches[key] isEqual:generations[sourceID]]){[self.activeFetches removeObjectForKey:key];
            if([self.jobs[key][@"state"] isEqual:@"FETCHING"]){self.jobs[key][@"state"]=@"FETCH_CANCELLED";self.jobs[key][@"retryAt"]=@0;}
        }
    }
    [self persist];
    self.sources=unique;[self.defaults setObject:[NSJSONSerialization dataWithJSONObject:data options:0 error:nil] forKey:SourcesKey];
}
- (void)addSource:(TIONewsSourceConfig *)source { if(!source)return;NSMutableArray *all=[self.sources mutableCopy];[all addObject:source];[self saveSources:all]; }
- (void)removeSourceWithID:(NSString *)sourceID { if(!sourceID.length)return;NSMutableArray *all=[self.sources mutableCopy];[all filterUsingPredicate:[NSPredicate predicateWithFormat:@"sourceID != %@",sourceID]];[self saveSources:all]; }
- (TIONewsArticle *)articleForJob:(NSDictionary *)job {
    if(![job[@"text"] isKindOfClass:NSString.class])return nil;
    return [[TIONewsArticle alloc]initWithSourceID:job[@"sourceID"] sourceName:job[@"sourceName"] title:job[@"title"] date:job[@"date"] readingText:job[@"text"] canonicalURL:job[@"url"]];
}
- (NSArray *)todayArticles {
    NSMutableArray *articles=[NSMutableArray new];for(NSDictionary *job in self.jobs.allValues){if([job[@"date"] isEqual:self.todayDateStr]){TIONewsArticle *article=[self articleForJob:job];if(article)[articles addObject:article];}}
    return articles;
}
- (NSArray<NSDictionary *> *)jobSnapshots {
    NSMutableArray *result=[NSMutableArray new];for(NSDictionary *job in self.jobs.allValues){NSMutableDictionary *copy=[job mutableCopy];[copy removeObjectForKey:@"text"];[result addObject:copy];}return result;
}
- (NSUInteger)officialSavedCount { NSUInteger count=0;for(NSDictionary *job in self.jobs.allValues)if([job[@"date"] isEqual:self.todayDateStr]&&[job[@"state"] isEqual:TIONewsJobSaved])count++;return count; }
- (void)start { [self.timer invalidate];self.timer=[NSTimer scheduledTimerWithTimeInterval:30 target:self selector:@selector(checkScheduledAtWake) userInfo:nil repeats:YES];[self checkScheduledAtWake]; }
- (void)stop { [self.timer invalidate];self.timer=nil;self.status=TIONSchedulerStatusIdle;self.statusMessage=@"自动调度已停止；已发请求仍须对账，不能视为失败重发";[self notifyStatus]; }
- (void)checkScheduledAtWake {
    if(!self.enabled)return;
    NSCalendar *calendar=[self calendar];NSDate *now=self.clock();NSDate *start=[calendar startOfDayForDate:now];
    NSDate *scheduled=[calendar dateBySettingHour:self.scheduledHour minute:self.scheduledMinute second:0 ofDate:start options:NSCalendarMatchNextTime];
    if(!scheduled||[now compare:scheduled]==NSOrderedAscending){self.statusMessage=@"等待今日计划时间；系统挂起后将在下次唤醒补跑";[self notifyStatus];return;}
    [self triggerCheckNowForce:NO];
}
- (BOOL)validArticle:(TIONewsArticle *)article source:(TIONewsSourceConfig *)source {
    NSData *bytes=[article.readingText dataUsingEncoding:NSUTF8StringEncoding allowLossyConversion:NO];
    NSString *trim=[article.readingText stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    return bytes.length>0&&bytes.length<=1024*1024&&trim.length>0&&[article.sourceID isEqual:source.sourceID]&&[article.readingText rangeOfString:@"\0"].location==NSNotFound;
}
- (NSString *)idempotency:(NSString *)base text:(NSString *)text {
    NSData *bytes=[text dataUsingEncoding:NSUTF8StringEncoding];unsigned char digest[CC_SHA256_DIGEST_LENGTH];CC_SHA256(bytes.bytes,(CC_LONG)bytes.length,digest);
    NSMutableString *hash=[NSMutableString new];for(NSUInteger i=0;i<sizeof(digest);i++)[hash appendFormat:@"%02x",digest[i]];return [base stringByAppendingFormat:@":%@",hash];
}
- (void)triggerCheckNowForce:(BOOL)force {
    NSDate *now=self.clock();NSString *date=[self dateKey:now];
    for(TIONewsSourceConfig *source in [self allSources]){
        if(!source.enabled)continue;
        BOOL olderUnknown=NO;
        for(NSString *oldKey in self.jobs.allKeys){NSMutableDictionary *old=self.jobs[oldKey];
            if([old[@"sourceID"] isEqual:source.sourceID]&&![old[@"date"] isEqual:date]&&[old[@"state"] isEqual:TIONewsJobUnknownNeedsReconciliation]){
                [self advanceJob:old key:oldKey];olderUnknown=YES;
            }
        }
        if(olderUnknown)continue;
        NSString *key=[self sourceKey:source.sourceID date:date];NSMutableDictionary *job=self.jobs[key];
        if(job[@"text"]){[self advanceJob:job key:key];continue;}
        if(self.activeFetches[key])continue;
        // Manual checks never bypass a known failure's ten-minute backoff.
        if([job[@"retryAt"] doubleValue]>now.timeIntervalSince1970)continue;
        if(!job){job=[@{@"sourceID":source.sourceID,@"date":date,@"state":@"FETCHING"} mutableCopy];self.jobs[key]=job;}
        NSString *generation=self.sourceGenerations[source.sourceID];
        self.activeFetches[key]=generation;job[@"state"]=@"FETCHING";[self persist];
        self.fetcher(source,now,^(TIONewsArticle *article,NSError *error){
            dispatch_async(dispatch_get_main_queue(),^{
                if(![self.activeFetches[key] isEqual:generation])return;
                [self.activeFetches removeObjectForKey:key];
                TIONewsSourceConfig *current=nil;
                for(TIONewsSourceConfig *candidate in self.sources)if([candidate.sourceID isEqual:source.sourceID]){current=candidate;break;}
                if(!current.enabled||![self.sourceGenerations[source.sourceID] isEqual:generation]||![current.dictionaryRepresentation isEqual:source.dictionaryRepresentation])return;
                if(!error&&[self validArticle:article source:source]){
                    job[@"sourceName"]=article.sourceName;job[@"title"]=article.title;job[@"text"]=article.readingText;job[@"url"]=article.canonicalURL?:@"";
                    job[@"idempotencyKey"]=[self idempotency:key text:article.readingText];job[@"state"]=TIONewsJobWaitingRuntimeContext;job[@"retryAt"]=@0;
                    [self advanceJob:job key:key];
                }else{job[@"state"]=@"FETCH_RETRY";job[@"retryAt"]=@(self.clock().timeIntervalSince1970+600);job[@"error"]=error.localizedDescription?:@"正文必须是非空 UTF-8 且不超过 1MB";}
                [self persist];[self updateStatus];
            });
        });
    }
    [self updateStatus];
}
- (void)advanceJob:(NSMutableDictionary *)job key:(NSString *)key {
    NSString *state=job[@"state"];
    if([state isEqual:TIONewsJobSaved])return;
    NSDictionary *active=self.activeSaves[key];
    if(active&&[active[@"deadline"] doubleValue]>self.clock().timeIntervalSince1970)return;
    [self.activeSaves removeObjectForKey:key];
    BOOL unknown=[state isEqual:TIONewsJobUnknownNeedsReconciliation];
    if(!self.saveBridge||!self.saveBridge.runtimeContextAvailable){
        if(!unknown)job[@"state"]=TIONewsJobWaitingRuntimeContext;
        [self persist];return;
    }
    if([job[@"retryAt"] doubleValue]>self.clock().timeIntervalSince1970)return;
    TIONewsArticle *article=[self articleForJob:job];if(!article)return;
    NSString *token=NSUUID.UUID.UUIDString;
    self.activeSaves[key]=@{@"token":token,@"deadline":@(self.clock().timeIntervalSince1970+600)};
    if(!unknown)job[@"attemptedAt"]=@(self.clock().timeIntervalSince1970);
    job[@"state"]=TIONewsJobUnknownNeedsReconciliation;
    job[@"retryAt"]=@(self.clock().timeIntervalSince1970+600);
    // A persistence failure must not permit an outbound save.
    if(![self persist]){[self.activeSaves removeObjectForKey:key];job[@"state"]=unknown?TIONewsJobUnknownNeedsReconciliation:TIONewsJobUnavailable;job[@"error"]=unknown?@"对账状态持久化失败；原保存结果仍未知，只能继续对账":@"事务状态未能持久化；未提交保存请求";return;}
    void (^completion)(TIONewsSaveResult *)=^(TIONewsSaveResult *result){
        dispatch_async(dispatch_get_main_queue(),^{
            if(![self.activeSaves[key][@"token"] isEqual:token])return;
            [self.activeSaves removeObjectForKey:key];
            NSDate *now=self.clock();NSTimeInterval verified=result.verifiedAt.timeIntervalSince1970;
            BOOL fresh=result.verifiedAt&&verified>=[job[@"attemptedAt"] doubleValue]&&verified<=now.timeIntervalSince1970+5;
            if(result.disposition==TIONewsSaveReadbackEvidence&&[result.officialID stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet].length&&[result.readbackText isEqual:article.readingText]&&fresh){
                job[@"state"]=TIONewsJobSaved;job[@"officialID"]=result.officialID;job[@"verifiedAt"]=@(verified);job[@"retryAt"]=@0;
            }else if(result.disposition==TIONewsSaveDefinitelyNotWritten){job[@"state"]=TIONewsJobRetryNotWritten;job[@"retryAt"]=@(now.timeIntervalSince1970+600);}
            else job[@"state"]=TIONewsJobUnknownNeedsReconciliation;
            [self persist];[self updateStatus];
        });
    };
    if(unknown)[self.saveBridge reconcileArticle:article idempotencyKey:job[@"idempotencyKey"] completion:completion];
    else [self.saveBridge saveArticle:article idempotencyKey:job[@"idempotencyKey"] completion:completion];
}
- (void)updateStatus {
    NSUInteger unknown=0,waiting=0,retry=0;
    for(NSDictionary *job in self.jobs.allValues){NSString *state=job[@"state"];
        if([state isEqual:TIONewsJobUnknownNeedsReconciliation]){unknown++;continue;}
        if(![job[@"date"] isEqual:self.todayDateStr])continue;
        if([state isEqual:TIONewsJobWaitingRuntimeContext]||[state isEqual:TIONewsJobUnavailable])waiting++;
        else if([state isEqual:@"FETCH_RETRY"]||[state isEqual:TIONewsJobRetryNotWritten])retry++;
    }
    BOOL allGenerated=YES;NSUInteger enabledSources=0;
    for(TIONewsSourceConfig *source in self.sources)if(source.enabled){enabledSources++;NSString *key=[self sourceKey:source.sourceID date:self.todayDateStr];if(!self.jobs[key][@"text"])allGenerated=NO;}
    if(enabledSources&&allGenerated)[self.defaults setObject:self.todayDateStr forKey:@"io.turboio.newshub.completed.date"];
    self.status=self.activeFetches.count?TIONSchedulerStatusRunning:(retry?TIONSchedulerStatusWaitingRetry:TIONSchedulerStatusComplete);
    self.statusMessage=[NSString stringWithFormat:@"生成就绪 %lu；官方已确认 %lu；WAITING_RUNTIME_CONTEXT %lu；UNKNOWN_NEEDS_RECONCILIATION %lu；明确失败10分钟后重试 %lu。挂起后等待唤醒补跑。",(unsigned long)self.todayArticles.count,(unsigned long)self.officialSavedCount,(unsigned long)waiting,(unsigned long)unknown,(unsigned long)retry];
    if([self.delegate respondsToSelector:@selector(newsSchedulerDidGenerateArticles:)])[self.delegate newsSchedulerDidGenerateArticles:self.todayArticles];
    [self notifyStatus];
}
- (void)notifyStatus { if([self.delegate respondsToSelector:@selector(newsSchedulerDidUpdateStatus:message:)])[self.delegate newsSchedulerDidUpdateStatus:self.status message:self.statusMessage]; }
@end
