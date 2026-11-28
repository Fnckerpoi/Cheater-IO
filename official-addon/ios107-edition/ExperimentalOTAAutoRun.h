#import <Foundation/Foundation.h>

typedef NS_ENUM(NSUInteger, TIOOTAAutoRunPhase) {
    TIOOTAAutoRunPhaseIDLE = 0,
    TIOOTAAutoRunPhaseDISCOVER = 1,
    TIOOTAAutoRunPhaseINITIAL = 2,
    TIOOTAAutoRunPhaseAUTO_ARM = 3,
    TIOOTAAutoRunPhaseAFTER_INSTALL = 4,
    TIOOTAAutoRunPhaseSTOPPED = 5,
    TIOOTAAutoRunPhaseDONE = 6
};

@protocol TIOOTAAutoRunEnv <NSObject>
- (NSTimeInterval)uptime;
- (NSString *)currentDevice;
- (BOOL)isForeground;
- (BOOL)isFeedReady;
- (BOOL)ensureFeedListening:(NSError **)error;
- (BOOL)isHostBlocked;
- (BOOL)isGateArmed;
- (BOOL)isGateStarted;
- (BOOL)isTargetVersionRecent;
- (BOOL)recoveryAllowed:(NSString *)device uptime:(NSTimeInterval)now;
- (void)cancelGate;
- (BOOL)isFreshBoot:(NSTimeInterval)now;
- (BOOL)sendProbe:(NSUInteger)type device:(NSString *)device;
- (BOOL)authorizeGate:(NSError **)error;
- (BOOL)releaseGate:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error;
@end

@interface TIOOTAAutoRun : NSObject
@property(nonatomic,readonly) TIOOTAAutoRunPhase phase;
@property(nonatomic,copy,readonly) NSString *statusNote;
@property(nonatomic,readonly) NSUInteger preflightAttempts;
@property(nonatomic,readonly) NSUInteger armAttempts;
@property(nonatomic,readonly) NSUInteger readbackAttempts;
@property(nonatomic,readonly) NSUInteger releaseAttempts;

+ (instancetype)shared;
- (void)start;
- (void)stop;
- (void)reset;
- (void)tick;
- (void)tickWithUptime:(NSTimeInterval)now;
- (NSDictionary *)status;
- (void)setEnvironment:(id<TIOOTAAutoRunEnv>)env;
@end

FOUNDATION_EXPORT void TIOOTAAutoRunStart(void);
FOUNDATION_EXPORT void TIOOTAAutoRunStop(void);
FOUNDATION_EXPORT NSDictionary *TIOOTAAutoRunStatus(void);
