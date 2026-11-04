#import "ExperimentalOTAAutoRun.h"
#include <assert.h>

@interface ManualAutoRun : TIOOTAAutoRun
@end
@implementation ManualAutoRun
- (void)startTimerLocked {}
@end

@interface TestAutoRunEnv : NSObject <TIOOTAAutoRunEnv>
@property NSTimeInterval now;
@property(copy) NSString *device;
@property BOOL foreground, feedReady, blocked, armed, started, recent, recovered, fresh, authorizeOK, releaseOK;
@property NSUInteger feeds, probes1, probes2, probes11, arms, releases, cancels;
@end
@implementation TestAutoRunEnv
- (instancetype)init {
    if((self=[super init])){_now=100;_device=@"TEST_DEVICE";_foreground=YES;_feedReady=YES;_authorizeOK=YES;_releaseOK=YES;}
    return self;
}
- (NSTimeInterval)uptime {return _now;}
- (NSString *)currentDevice {return _device;}
- (BOOL)isForeground {return _foreground;}
- (BOOL)isFeedReady {return _feedReady;}
- (BOOL)ensureFeedListening:(NSError **)error {_feeds++;return YES;}
- (BOOL)isHostBlocked {return _blocked;}
- (BOOL)isGateArmed {return _armed;}
- (BOOL)isGateStarted {return _started;}
- (BOOL)isTargetVersionRecent {return _recent;}
- (BOOL)recoveryAllowed:(NSString *)device uptime:(NSTimeInterval)now {return _recovered && [device isEqual:_device];}
- (BOOL)isFreshBoot:(NSTimeInterval)now {return _fresh;}
- (void)cancelGate {_cancels++;if(!_started)_armed=NO;}
- (BOOL)sendProbe:(NSUInteger)type device:(NSString *)device {
    assert([device isEqual:_device]);
    if(type==1)_probes1++;else if(type==2)_probes2++;else if(type==11)_probes11++;else assert(NO);
    return YES;
}
- (BOOL)authorizeGate:(NSError **)error {_arms++;if(_authorizeOK)_armed=YES;return _authorizeOK;}
- (BOOL)releaseGate:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error {
    assert(_started && _recovered && _fresh && [device isEqual:_device]);
    _releases++;if(_releaseOK){_started=NO;_armed=NO;}return _releaseOK;
}
@end

static void Tick(TIOOTAAutoRun *r,TestAutoRunEnv *e,NSTimeInterval now){e.now=now;[r tick];}
static TIOOTAAutoRun *Runner(TestAutoRunEnv *e){TIOOTAAutoRun *r=[ManualAutoRun new];[r setEnvironment:e];[r start];return r;}
static void Preflight(TIOOTAAutoRun *r,TestAutoRunEnv *e){
    Tick(r,e,100);assert(r.phase==TIOOTAAutoRunPhaseINITIAL);
    Tick(r,e,107);assert(e.probes1==0);
    Tick(r,e,108);assert(e.probes1==1 && e.probes2==1 && e.probes11==1);
    e.recent=YES;Tick(r,e,109);assert(r.phase==TIOOTAAutoRunPhaseAUTO_ARM);
}
static void Armed(TIOOTAAutoRun *r,TestAutoRunEnv *e){Preflight(r,e);Tick(r,e,110);assert(r.phase==TIOOTAAutoRunPhaseAFTER_INSTALL);}

int main(void){@autoreleasepool{
    assert(NSThread.isMainThread);
    NSUInteger groups=0;
    TestAutoRunEnv *e=[TestAutoRunEnv new];TIOOTAAutoRun *r=Runner(e);
    Tick(r,e,100);[r start];assert(r.phase==TIOOTAAutoRunPhaseINITIAL);
    [r stop];NSUInteger actions=e.probes1+e.arms+e.releases+e.feeds;
    Tick(r,e,1000);assert(actions==e.probes1+e.arms+e.releases+e.feeds);
    [r reset];assert(r.phase==TIOOTAAutoRunPhaseIDLE && r.preflightAttempts==0);groups++;

    e=[TestAutoRunEnv new];e.foreground=NO;r=Runner(e);Tick(r,e,100);assert(r.phase==TIOOTAAutoRunPhaseDISCOVER);
    e.foreground=YES;e.device=nil;Tick(r,e,101);assert(r.phase==TIOOTAAutoRunPhaseDISCOVER);
    e.device=@"TEST_DEVICE";e.blocked=YES;Tick(r,e,102);assert(e.feeds==0 && e.probes1==0);
    e.blocked=NO;e.feedReady=NO;Tick(r,e,103);Tick(r,e,104);assert(e.feeds==1);
    Tick(r,e,108);Tick(r,e,113);Tick(r,e,118);assert(e.feeds==3 && r.phase==TIOOTAAutoRunPhaseSTOPPED);groups++;

    e=[TestAutoRunEnv new];r=Runner(e);e.recent=YES;Tick(r,e,100);Tick(r,e,108);
    assert(e.probes1==1 && r.phase==TIOOTAAutoRunPhaseINITIAL);Tick(r,e,109);
    assert(r.phase==TIOOTAAutoRunPhaseAUTO_ARM);[r stop];groups++;

    e=[TestAutoRunEnv new];r=Runner(e);Tick(r,e,100);
    for(NSUInteger i=0;i<4;i++)Tick(r,e,108+5*i);
    assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && r.preflightAttempts==3 && e.probes1==3 && e.arms==0);groups++;

    e=[TestAutoRunEnv new];r=Runner(e);Preflight(r,e);e.recent=NO;Tick(r,e,110);
    assert(e.arms==0 && r.phase==TIOOTAAutoRunPhaseAUTO_ARM);e.recent=YES;Tick(r,e,115);
    assert(e.arms==1 && r.phase==TIOOTAAutoRunPhaseAFTER_INSTALL);[r stop];groups++;

    e=[TestAutoRunEnv new];e.authorizeOK=NO;r=Runner(e);Preflight(r,e);
    for(NSUInteger i=0;i<4;i++)Tick(r,e,110+5*i);
    assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && e.arms==3);groups++;

    for(NSUInteger mode=0;mode<4;mode++){
        e=[TestAutoRunEnv new];r=Runner(e);Armed(r,e);
        if(mode==0)e.foreground=NO;if(mode==1)e.device=@"OTHER_DEVICE";if(mode==2)e.blocked=YES;if(mode==3)e.now=99;
        Tick(r,e,mode==3?99:111);assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && !e.armed && e.releases==0);
    }groups++;

    e=[TestAutoRunEnv new];r=Runner(e);Armed(r,e);Tick(r,e,111);assert(e.releases==0 && e.probes1==1);
    e.started=YES;e.armed=NO;e.fresh=YES;Tick(r,e,180);assert(e.releases==0 && r.readbackAttempts==0);
    e.device=nil;Tick(r,e,181);assert(e.releases==0);e.device=@"TEST_DEVICE";
    e.recovered=YES;e.fresh=NO;Tick(r,e,182);assert(r.readbackAttempts==1 && e.probes2==1 && e.probes11==2);
    e.fresh=YES;Tick(r,e,183);assert(e.releases==0);Tick(r,e,187);
    assert(r.phase==TIOOTAAutoRunPhaseDONE && e.releases==1);Tick(r,e,200);assert(e.releases==1);groups++;

    e=[TestAutoRunEnv new];r=Runner(e);Armed(r,e);e.started=YES;e.recovered=YES;
    for(NSUInteger i=0;i<12;i++)Tick(r,e,180+5*i);
    assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && r.readbackAttempts==11 && e.probes11==12 && e.releases==0);groups++;

    e=[TestAutoRunEnv new];e.releaseOK=NO;r=Runner(e);Armed(r,e);e.started=YES;e.recovered=YES;e.fresh=YES;
    for(NSUInteger i=0;i<4;i++)Tick(r,e,180+5*i);
    assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && e.releases==3 && e.started);groups++;

    e=[TestAutoRunEnv new];r=Runner(e);Armed(r,e);Tick(r,e,1010);
    assert(r.phase==TIOOTAAutoRunPhaseSTOPPED && !e.armed);groups++;

    e=[TestAutoRunEnv new];e.started=YES;r=Runner(e);assert(r.phase==TIOOTAAutoRunPhaseIDLE && e.cancels==0);groups++;
    NSLog(@"PASS: %lu auto-run groups; production state machine, manual clock, no timer or device I/O",(unsigned long)groups);
}return 0;}
