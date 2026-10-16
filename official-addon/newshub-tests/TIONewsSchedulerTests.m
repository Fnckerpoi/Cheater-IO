#import <Foundation/Foundation.h>
#import "../TIONewsScheduler.h"
static NSUInteger Checks;
static void Check(BOOL value,NSString *label){Checks++;if(!value){fprintf(stderr,"FAIL %s\n",label.UTF8String);exit(42);}printf("PASS %s\n",label.UTF8String);}
static void Pump(void){for(NSUInteger n=0;n<8;n++)[NSRunLoop.currentRunLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.005]];}
static TIONewsSourceConfig *Source(NSString *name){TIONewsSourceConfig *s=[TIONewsSourceConfig new];s.sourceID=name;s.name=name;s.type=TIONewsSourceTypeRSS;s.endpointOrTopic=@"https://example.invalid/no-network";s.enabled=YES;return s;}
static TIONewsArticle *Article(NSString *source,NSString *text){return [[TIONewsArticle alloc]initWithSourceID:source sourceName:source title:@"公开fixture" date:@"2026-10-14" readingText:text canonicalURL:nil];}
static TIONewsSaveResult *Result(TIONewsSaveDisposition state,NSDate *verified,NSString *text){TIONewsSaveResult *r=[TIONewsSaveResult new];r.disposition=state;r.officialID=@"fixture-official-id";r.readbackText=text;r.verifiedAt=verified;return r;}
@interface FakeBridge:NSObject<TIONewsSaveBridge>
@property(nonatomic)BOOL runtimeContextAvailable;
@property(nonatomic)NSUInteger saves,reconciles;
@property(nonatomic,strong)TIONewsSaveResult *reply;
@property(nonatomic,strong)NSUserDefaults *defaults;
@property(nonatomic,strong)NSMutableArray<NSString *> *keys;
@property(nonatomic,strong)NSMutableArray *callbacks;
@end
@implementation FakeBridge
- (instancetype)init {if((self=[super init])){_runtimeContextAvailable=YES;_keys=[NSMutableArray new];_callbacks=[NSMutableArray new];}return self;}
- (void)saveArticle:(TIONewsArticle *)article idempotencyKey:(NSString *)key completion:(void(^)(TIONewsSaveResult *))done {
    self.saves++;[self.keys addObject:key];[self.callbacks addObject:[done copy]];BOOL persisted=NO;
    for(NSDictionary *j in [self.defaults dictionaryForKey:@"io.turboio.newshub.jobs.v1"].allValues)if([j[@"idempotencyKey"] isEqual:key]&&[j[@"state"] isEqual:TIONewsJobUnknownNeedsReconciliation])persisted=YES;
    Check(persisted,@"UNKNOWN persisted before bridge submit");if(self.reply)done(self.reply);
}
- (void)reconcileArticle:(TIONewsArticle *)article idempotencyKey:(NSString *)key completion:(void(^)(TIONewsSaveResult *))done {self.reconciles++;[self.keys addObject:key];[self.callbacks addObject:[done copy]];if(self.reply)done(self.reply);}
@end
@interface FailingDefaults:NSUserDefaults @end
@implementation FailingDefaults
- (BOOL)synchronize { return NO; }
@end
static NSString *State(TIONewsScheduler *s){return s.jobSnapshots.firstObject[@"state"];}
@interface ToggleDefaults:NSUserDefaults
@property(nonatomic)BOOL failFlush;
@end
@implementation ToggleDefaults
- (BOOL)synchronize { return self.failFlush?NO:[super synchronize]; }
@end
static void ReviewRegressions(BOOL persistence){
    NSString *suite=[@"io.turboio.tests.review." stringByAppendingString:NSUUID.UUID.UUIDString];ToggleDefaults *defs=[[ToggleDefaults alloc]initWithSuiteName:suite];
    __block NSDate *now=[NSDate dateWithTimeIntervalSince1970:1791936000];FakeBridge *bridge=[FakeBridge new];bridge.defaults=defs;
    if(persistence){
        TIONewsScheduler *s=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){done(Article(source.sourceID,@"sticky unknown"),nil);} bridge:bridge];
        [s saveSources:@[Source(@"sticky")]];[s triggerCheckNowForce:YES];Pump();
        now=[now dateByAddingTimeInterval:601];defs.failFlush=YES;[s triggerCheckNowForce:YES];Pump();
        Check([State(s) isEqual:TIONewsJobUnknownNeedsReconciliation],@"R1 reconcile persistence failure cannot downgrade UNKNOWN");
        TIONewsScheduler *failedReload=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){abort();} bridge:bridge];
        Check([State(failedReload) isEqual:TIONewsJobUnknownNeedsReconciliation],@"R1 journal reload after failed flush still retains UNKNOWN");
        now=[now dateByAddingTimeInterval:601];defs.failFlush=NO;[s triggerCheckNowForce:YES];Pump();
        Check(bridge.saves==1&&bridge.reconciles==1,@"R1 flush recovery reconciles instead of issuing second save");
        TIONewsScheduler *reloaded=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){abort();} bridge:bridge];
        now=[now dateByAddingTimeInterval:601];[reloaded triggerCheckNowForce:YES];Pump();
        Check(bridge.saves==1&&bridge.reconciles==2,@"R1 reload after failed reconcile stays reconciliation-only");
    }else{
        for(NSNumber *mode in @[@0,@1,@2,@3,@4]){
            [defs removePersistentDomainForName:suite];bridge.saves=0;__block TIONewsFetchCompletion held;
            TIONewsScheduler *s=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){held=[done copy];} bridge:bridge];
            [s saveSources:@[Source(@"late")]];[s triggerCheckNowForce:YES];
            if(mode.integerValue==0||mode.integerValue==2){[s removeSourceWithID:@"late"];if(mode.integerValue==2)[s addSource:Source(@"late")];}
            else if(mode.integerValue==1||mode.integerValue==3){TIONewsSourceConfig *disabled=Source(@"late");disabled.enabled=NO;[s saveSources:@[disabled]];if(mode.integerValue==3)[s saveSources:@[Source(@"late")]];}
            else{TIONewsSourceConfig *changed=Source(@"late");changed.endpointOrTopic=@"https://example.invalid/replaced";[s saveSources:@[changed]];}
            held(Article(@"late",@"late stale article"),nil);Pump();
            Check(bridge.saves==0&&s.todayArticles.count==0,@"R1 removed disabled readded or replaced source rejects late fetch callback");
        }
        [defs removePersistentDomainForName:suite];bridge.saves=0;__block NSMutableArray *callbacks=[NSMutableArray new];
        TIONewsScheduler *aba=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){[callbacks addObject:[done copy]];} bridge:bridge];
        [aba saveSources:@[Source(@"aba")]];[aba triggerCheckNowForce:YES];[aba removeSourceWithID:@"aba"];[aba addSource:Source(@"aba")];[aba triggerCheckNowForce:YES];
        Check(callbacks.count==2,@"R1 readded source can begin a fresh fetch generation");
        TIONewsFetchCompletion old=callbacks.firstObject,current=callbacks.lastObject;
        old(Article(@"aba",@"stale ABA body"),nil);Pump();Check(bridge.saves==0&&aba.todayArticles.count==0,@"R1 old ABA callback cannot consume new active fetch token");
        current(Article(@"aba",@"current ABA body"),nil);Pump();Check(bridge.saves==1&&[aba.todayArticles.firstObject.readingText isEqual:@"current ABA body"],@"R1 current generation alone may submit its payload");
        [aba removeSourceWithID:@"aba"];Check([State(aba) isEqual:TIONewsJobUnknownNeedsReconciliation],@"R1 source removal never deletes an existing UNKNOWN journal");
    }
    [defs removePersistentDomainForName:suite];
}
int main(int argc,char **argv){@autoreleasepool{
    if(argc>1){ReviewRegressions(strcmp(argv[1],"persist")==0);printf("R1 isolated regression PASS\n");return 0;}

    NSString *suite=[@"io.turboio.tests.scheduler." stringByAppendingString:NSUUID.UUID.UUIDString];NSUserDefaults *defs=[[NSUserDefaults alloc]initWithSuiteName:suite];
    __block NSDate *now=[NSDate dateWithTimeIntervalSince1970:1791936000];__block NSUInteger fetched=0;
    TIONewsScheduledFetch fetch=^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){fetched++;done(Article(source.sourceID,@"公开测试正文"),nil);};
    TIONewsScheduler *scheduler=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:nil];[scheduler saveSources:@[Source(@"alpha")]];
    [scheduler triggerCheckNowForce:YES];Pump();
    Check([State(scheduler) isEqual:TIONewsJobWaitingRuntimeContext],@"bridge absent waits for runtime context");
    Check(scheduler.todayArticles.count==1&&scheduler.officialSavedCount==0,@"generated is not official saved");
    [scheduler triggerCheckNowForce:YES];Pump();Check(fetched==1,@"force cannot replace generated pending payload");
    FakeBridge *bridge=[FakeBridge new];bridge.defaults=defs;scheduler.saveBridge=bridge;
    [scheduler triggerCheckNowForce:YES];Pump();Check(bridge.saves==1&&[State(scheduler) isEqual:TIONewsJobUnknownNeedsReconciliation],@"sent without callback remains UNKNOWN");
    NSString *originalKey=scheduler.jobSnapshots.firstObject[@"idempotencyKey"];
    [scheduler triggerCheckNowForce:YES];Pump();Check(bridge.saves==1&&fetched==1,@"UNKNOWN never blindly resubmits or refetches");
    FakeBridge *recovered=[FakeBridge new];recovered.defaults=defs;
    TIONewsScheduler *reloaded=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:recovered];
    now=[now dateByAddingTimeInterval:601];[reloaded triggerCheckNowForce:YES];Pump();
    Check(recovered.saves==0&&recovered.reconciles==1,@"kill/reload same suite reconciles only");
    Check([recovered.keys.lastObject isEqual:originalKey],@"reconciliation retains persisted idempotency key");
    recovered.reply=Result(TIONewsSaveReadbackEvidence,[now dateByAddingTimeInterval:-10000],@"公开测试正文");
    now=[now dateByAddingTimeInterval:601];[reloaded triggerCheckNowForce:NO];Pump();
    Check(reloaded.officialSavedCount==0,@"stale readback cannot mark saved");
    // Restart to model a timed-out reconcile call with a lost completion.
    FakeBridge *verified=[FakeBridge new];verified.defaults=defs;now=[now dateByAddingTimeInterval:601];verified.reply=Result(TIONewsSaveReadbackEvidence,now,@"公开测试正文");
    TIONewsScheduler *confirmed=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:verified];
    [confirmed triggerCheckNowForce:YES];Pump();Check(confirmed.officialSavedCount==1&&[State(confirmed) isEqual:TIONewsJobSaved],@"official id matching fresh readback confirms saved");
    [confirmed triggerCheckNowForce:YES];Pump();Check(verified.saves==0&&verified.reconciles==1&&fetched==1,@"confirmed result reused without duplicate submit");
    TIONewsScheduler *savedReload=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:verified];[savedReload triggerCheckNowForce:YES];Pump();Check(savedReload.officialSavedCount==1&&verified.saves==0,@"confirmed persistence survives restart");
    [defs removePersistentDomainForName:suite];fetched=0;
    FakeBridge *failure=[FakeBridge new];failure.defaults=defs;failure.reply=Result(TIONewsSaveDefinitelyNotWritten,nil,nil);
    TIONewsScheduler *retry=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:failure];[retry saveSources:@[Source(@"beta")]];[retry triggerCheckNowForce:YES];Pump();
    Check([State(retry) isEqual:TIONewsJobRetryNotWritten]&&failure.saves==1,@"explicit definitely-not-written schedules retry");
    now=[now dateByAddingTimeInterval:599];[retry triggerCheckNowForce:YES];Pump();Check(failure.saves==1,@"manual force cannot bypass 600 second retry");
    now=[now dateByAddingTimeInterval:1];[retry triggerCheckNowForce:NO];Pump();Check(failure.saves==2&&fetched==1,@"exact 600 second retry reuses generated payload");
    Check([failure.keys.firstObject isEqual:failure.keys.lastObject],@"retry reuses idempotency key");
    [defs removePersistentDomainForName:suite];fetched=0;
    __block NSUInteger badCalls=0;
    TIONewsScheduler *multi=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *s,NSDate *date,TIONewsFetchCompletion done){if([s.sourceID isEqual:@"bad"]){badCalls++;done(nil,[NSError errorWithDomain:@"fixture" code:1 userInfo:nil]);}else{fetched++;done(Article(s.sourceID,@"真实fixture稿"),nil);}} bridge:nil];
    [multi saveSources:@[Source(@"ok"),Source(@"bad"),Source(@"ok")]];[multi triggerCheckNowForce:NO];Pump();Check(multi.todayArticles.count==1&&multi.allSources.count==2,@"one failing source does not swallow independent success or duplicate job");
    now=[now dateByAddingTimeInterval:599];[multi triggerCheckNowForce:YES];Pump();Check(fetched==1&&badCalls==1,@"generation failures honor independent backoff");
    now=[now dateByAddingTimeInterval:1];[multi triggerCheckNowForce:NO];Pump();Check(badCalls==2&&fetched==1,@"only failed source fetched after ten minutes");
    [defs removePersistentDomainForName:suite];
    TIONewsScheduler *invalid=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *s,NSDate *date,TIONewsFetchCompletion done){done(Article(s.sourceID,@" \n"),nil);} bridge:nil];[invalid saveSources:@[Source(@"empty")]];[invalid triggerCheckNowForce:YES];Pump();Check(invalid.todayArticles.count==0,@"empty UTF8 payload rejected before bridge");
    [defs removePersistentDomainForName:suite];fetched=0;
    FakeBridge *timeout=[FakeBridge new];timeout.defaults=defs;
    TIONewsScheduler *live=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:timeout];[live saveSources:@[Source(@"timeout")]];
    [live triggerCheckNowForce:YES];Pump();now=[now dateByAddingTimeInterval:601];[live triggerCheckNowForce:YES];Pump();
    Check(timeout.saves==1&&timeout.reconciles==1,@"in-process lost callback timeout reconciles instead of resending");
    void (^oldCallback)(TIONewsSaveResult *)=timeout.callbacks.firstObject;
    oldCallback(Result(TIONewsSaveReadbackEvidence,now,@"公开测试正文"));Pump();
    Check(live.officialSavedCount==0,@"late superseded callback cannot confirm a different active attempt");
    void (^currentCallback)(TIONewsSaveResult *)=timeout.callbacks.lastObject;
    currentCallback(Result(TIONewsSaveReadbackEvidence,now,@"公开测试正文"));Pump();
    Check(live.officialSavedCount==1,@"current reconciliation callback can confirm fresh readback");
    [defs removePersistentDomainForName:suite];
    FakeBridge *unavailable=[FakeBridge new];unavailable.defaults=defs;unavailable.reply=Result(TIONewsSaveRuntimeUnavailable,nil,nil);
    TIONewsScheduler *uncertain=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:unavailable];[uncertain saveSources:@[Source(@"uncertain")]];[uncertain triggerCheckNowForce:YES];Pump();
    Check([State(uncertain) isEqual:TIONewsJobUnknownNeedsReconciliation],@"runtime-unavailable reply after submit is not proof of no write");
    now=[now dateByAddingTimeInterval:601];[uncertain triggerCheckNowForce:YES];Pump();Check(unavailable.saves==1&&unavailable.reconciles==1,@"unavailable after submit reconciles before any new write");
    now=[now dateByAddingTimeInterval:24*60*60];[uncertain triggerCheckNowForce:YES];Pump();
    Check(unavailable.saves==1&&uncertain.jobSnapshots.count==1,@"next day cannot bypass an older UNKNOWN with a new source job");
    for(NSNumber *bad in @[@0,@1,@2]){
        [defs removePersistentDomainForName:suite];FakeBridge *b=[FakeBridge new];b.defaults=defs;
        b.reply=Result(TIONewsSaveReadbackEvidence,now,@"公开测试正文");
        if(bad.integerValue==0)b.reply.officialID=@"";
        if(bad.integerValue==1)b.reply.readbackText=@"不同正文";
        if(bad.integerValue==2)b.reply.verifiedAt=[now dateByAddingTimeInterval:60];
        TIONewsScheduler *proof=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:b];[proof saveSources:@[Source(@"proof")]];[proof triggerCheckNowForce:YES];Pump();
        Check(proof.officialSavedCount==0&&[State(proof) isEqual:TIONewsJobUnknownNeedsReconciliation],@"missing id mismatched body or future proof cannot mark saved");
    }
    [defs removePersistentDomainForName:suite];fetched=0;
    NSDateFormatter *iso=[NSDateFormatter new];iso.locale=[[NSLocale alloc]initWithLocaleIdentifier:@"en_US_POSIX"];iso.timeZone=[NSTimeZone timeZoneForSecondsFromGMT:0];iso.dateFormat=@"yyyy-MM-dd'T'HH:mm:ss'Z'";
    now=[iso dateFromString:@"2026-03-08T06:59:00Z"];
    TIONewsScheduler *dst=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:nil];[dst saveSources:@[Source(@"DST")]];dst.timeZoneName=@"America/New_York";dst.scheduledHour=2;dst.scheduledMinute=30;
    [dst checkScheduledAtWake];Pump();Check(fetched==0,@"Calendar does not run before spring DST plan");
    now=[iso dateFromString:@"2026-03-08T07:00:00Z"];[dst checkScheduledAtWake];Pump();Check(fetched==1&&[dst.todayDateStr isEqual:@"2026-03-08"],@"nonexistent spring 02:30 catches up at Calendar next valid time");
    now=[iso dateFromString:@"2026-03-08T08:00:00Z"];[dst checkScheduledAtWake];Pump();Check(fetched==1,@"later foreground wake does not duplicate daily source job");
    [defs removePersistentDomainForName:suite];fetched=0;now=[iso dateFromString:@"2026-11-01T05:30:00Z"];
    TIONewsScheduler *fall=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:fetch bridge:nil];[fall saveSources:@[Source(@"fall")]];fall.timeZoneName=@"America/New_York";fall.scheduledHour=1;fall.scheduledMinute=30;
    [fall checkScheduledAtWake];Pump();now=[iso dateFromString:@"2026-11-01T06:30:00Z"];[fall checkScheduledAtWake];Pump();Check(fetched==1,@"repeated fall DST hour reuses the same daily source job");
    [defs removePersistentDomainForName:suite];
    for(NSNumber *extra in @[@0,@1]){
        NSString *text=[@"a" stringByPaddingToLength:1024*1024+extra.unsignedIntegerValue withString:@"a" startingAtIndex:0];
        TIONewsScheduler *size=[[TIONewsScheduler alloc]initWithDefaults:defs clock:^{return now;} fetch:^(TIONewsSourceConfig *source,NSDate *date,TIONewsFetchCompletion done){done(Article(source.sourceID,text),nil);} bridge:nil];[size saveSources:@[Source(extra.stringValue)]];[size triggerCheckNowForce:YES];Pump();
        Check(size.todayArticles.count==(extra.integerValue?0u:1u),@"UTF8 exact one-megabyte boundary enforced");
        [defs removePersistentDomainForName:suite];
    }
    NSString *failedSuite=[suite stringByAppendingString:@".failed-persist"];
    NSUserDefaults *blocked=[[FailingDefaults alloc]initWithSuiteName:failedSuite];FakeBridge *blockedBridge=[FakeBridge new];blockedBridge.defaults=blocked;
    TIONewsScheduler *noPersist=[[TIONewsScheduler alloc]initWithDefaults:blocked clock:^{return now;} fetch:fetch bridge:blockedBridge];[noPersist saveSources:@[Source(@"blocked-persist")]];[noPersist triggerCheckNowForce:YES];Pump();
    Check(blockedBridge.saves==0&&[State(noPersist) isEqual:TIONewsJobUnavailable],@"failed pending persistence never dispatches bridge request");
    [blocked removePersistentDomainForName:failedSuite];
    ReviewRegressions(YES);ReviewRegressions(NO);
    [defs removePersistentDomainForName:suite];printf("SUMMARY checks=%lu failures=0; isolated fake bridge only, no iOS save claim\n",(unsigned long)Checks);return 0;
}}
