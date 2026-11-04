#import "ExperimentalOTAAutoRun.h"
#import "ExperimentalOTAFeed.h"
#import "ExperimentalOTAFlash.h"
#import "ProtocolContext.h"
#import <TargetConditionals.h>
#if TARGET_OS_IPHONE
#import <UIKit/UIKit.h>
#endif

@interface TIODefaultOTAAutoRunEnv : NSObject <TIOOTAAutoRunEnv>
@end

@implementation TIODefaultOTAAutoRunEnv {
    BOOL _foregroundObserved;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _foregroundObserved = YES;
#if TARGET_OS_IPHONE
        NSNotificationCenter *nc = NSNotificationCenter.defaultCenter;
        [nc addObserver:self selector:@selector(onActive) name:UIApplicationDidBecomeActiveNotification object:nil];
        [nc addObserver:self selector:@selector(onBackground) name:UIApplicationDidEnterBackgroundNotification object:nil];
        if ([NSThread isMainThread]) {
            _foregroundObserved = (UIApplication.sharedApplication.applicationState == UIApplicationStateActive);
        }
#endif
    }
    return self;
}

- (void)dealloc {
    [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (void)onActive { @synchronized(self) { _foregroundObserved = YES; } }
- (void)onBackground { @synchronized(self) { _foregroundObserved = NO; } }

- (NSTimeInterval)uptime {
    return NSProcessInfo.processInfo.systemUptime;
}

- (NSString *)currentDevice {
    return TIOProtocolDevice();
}

- (BOOL)isForeground {
    @synchronized(self) { return _foregroundObserved; }
}

- (BOOL)isFeedReady {
    NSDictionary *feed = TIOExperimentalOTAFeedStatus();
    return [feed[@"running"] boolValue] && [feed[@"armed"] boolValue] && [feed[@"healthPassed"] boolValue];
}

- (BOOL)ensureFeedListening:(NSError **)error {
    return TIOBeginExperimentalOTAPreparation(error);
}

- (BOOL)isHostBlocked {
    NSDictionary *prefs = NSUserDefaults.standardUserDefaults.dictionaryRepresentation;
    return !TIOOTAAutoUpdateDisabled(prefs) || [TIOOTAFlashStatus()[@"stage"] unsignedIntegerValue] == 3;
}

- (BOOL)isGateArmed {
    return [TIOOTAFlashStatus()[@"stage"] unsignedIntegerValue] == 1;
}

- (BOOL)isGateStarted {
    return [TIOOTAFlashStatus()[@"stage"] unsignedIntegerValue] == 2;
}

- (BOOL)isTargetVersionRecent {
    return [TIOOTAFlashStatus()[@"autoPreflightReady"] boolValue];
}

- (BOOL)recoveryAllowed:(NSString *)device uptime:(NSTimeInterval)now {
    return TIOOTAFlashRecoveryAllowed(device, now);
}

- (void)cancelGate { TIOOTAFlashCancel(); }

- (BOOL)isFreshBoot:(NSTimeInterval)now {
    return TIOOTAFlashFreshBoot(now);
}

- (BOOL)sendProbe:(NSUInteger)type device:(NSString *)device {
    return TIOOTASendProbe(type, device);
}

- (BOOL)authorizeGate:(NSError **)error {
    return TIOOTAFlashAuthorize(error);
}

- (BOOL)releaseGate:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error {
    return TIOOTAFlashRelease(device, now, error);
}

@end

static void OnMain(dispatch_block_t block){
    if(NSThread.isMainThread)block();else dispatch_sync(dispatch_get_main_queue(),block);
}

static const NSTimeInterval INITIAL_SILENCE_SECONDS = 8.0;
static const NSTimeInterval RETRY_INTERVAL_SECONDS = 5.0;
static const NSUInteger PREFLIGHT_CAP = 3;
static const NSUInteger ARM_CAP = 3;
static const NSUInteger READBACK_CAP = 11;
static const NSUInteger RELEASE_CAP = 3;

@implementation TIOOTAAutoRun {
    id<TIOOTAAutoRunEnv> _env;
    dispatch_source_t _timer;
    NSString *_device;
    NSUInteger _feedAttempts;
    NSTimeInterval _armedAt;
    NSTimeInterval _initialUptime;
    NSTimeInterval _lastActionUptime;
}

+ (instancetype)shared {
    static TIOOTAAutoRun *s;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        s = [TIOOTAAutoRun new];
    });
    return s;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _env = [TIODefaultOTAAutoRunEnv new];
        _phase = TIOOTAAutoRunPhaseIDLE;
        _statusNote = @"未启动";
    }
    return self;
}

- (void)setEnvironment:(id<TIOOTAAutoRunEnv>)env {
    OnMain(^{
        _env = env ?: [TIODefaultOTAAutoRunEnv new];
    });
}

- (void)start {
    OnMain(^{
        if (_phase == TIOOTAAutoRunPhaseDISCOVER || _phase == TIOOTAAutoRunPhaseINITIAL || _phase == TIOOTAAutoRunPhaseAUTO_ARM || _phase == TIOOTAAutoRunPhaseAFTER_INSTALL) return;
        if([_env isGateStarted]){_statusNote=@"已有安装会话，请使用手动回查";return;}
        [_env cancelGate];
        _device=nil;_feedAttempts=0;_armedAt=0;
        _phase = TIOOTAAutoRunPhaseDISCOVER;
        _preflightAttempts = 0;
        _armAttempts = 0;
        _readbackAttempts = 0;
        _releaseAttempts = 0;
        _initialUptime = 0;
        _lastActionUptime = 0;
        _statusNote = @"已启动一键升级，正在等待前台、下载源与设备连接...";
        [self startTimerLocked];
    });
}

- (void)stop {
    OnMain(^{
        [_env cancelGate];
        _phase = TIOOTAAutoRunPhaseSTOPPED;
        _statusNote = @"已停止自动推进，未开始的授权已撤销";
        [self stopTimerLocked];
    });
}

- (void)reset {
    OnMain(^{
        [_env cancelGate];
        _device=nil;_feedAttempts=0;_armedAt=0;
        _phase = TIOOTAAutoRunPhaseIDLE;
        _preflightAttempts = 0;
        _armAttempts = 0;
        _readbackAttempts = 0;
        _releaseAttempts = 0;
        _initialUptime = 0;
        _lastActionUptime = 0;
        _statusNote = @"已重置，等待启动";
        [self stopTimerLocked];
    });
}

- (void)startTimerLocked {
    if (_timer) return;
    _timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, dispatch_get_main_queue());
    dispatch_source_set_timer(_timer, dispatch_time(DISPATCH_TIME_NOW, 0), 1000 * NSEC_PER_MSEC, 100 * NSEC_PER_MSEC);
    __weak typeof(self) weakSelf = self;
    dispatch_source_set_event_handler(_timer, ^{
        [weakSelf tickLocked];
    });
    dispatch_resume(_timer);
}

- (void)stopTimerLocked {
    if (_timer) {
        dispatch_source_cancel(_timer);
        _timer = nil;
    }
}

- (void)tick {
    OnMain(^{
        [self tickLocked];
    });
}

- (void)tickWithUptime:(NSTimeInterval)now {
    OnMain(^{
        [self tickInternalWithUptime:now];
    });
}

- (void)tickLocked {
    NSTimeInterval now = [_env uptime];
    [self tickInternalWithUptime:now];
}

- (void)tickInternalWithUptime:(NSTimeInterval)now {
    if (_phase == TIOOTAAutoRunPhaseIDLE || _phase == TIOOTAAutoRunPhaseSTOPPED || _phase == TIOOTAAutoRunPhaseDONE) {
        return;
    }

    NSString *device = [_env currentDevice];
    BOOL foreground = [_env isForeground];

    if (!foreground && _phase != TIOOTAAutoRunPhaseDISCOVER) {
        [self stop];_statusNote=@"App 已退后台，自动推进已停止";return;
    }
    if (_device.length && device.length && ![_device isEqual:device]) {
        [self stop];_statusNote=@"连接设备已改变，自动推进已停止";return;
    }
    if ([_env isHostBlocked] && _phase != TIOOTAAutoRunPhaseDISCOVER) {
        [self stop];_statusNote=@"自动更新设置或传输状态不满足条件，自动推进已停止";return;
    }
    if (now < _initialUptime) {[self stop];_statusNote=@"时钟异常，自动推进已停止";return;}

    if (_phase == TIOOTAAutoRunPhaseDISCOVER) {
        if (!foreground) {
            _statusNote = @"App 已退后台，等待切回前台...";
            return;
        }
        if (!device.length) {
            _statusNote = @"未连接眼镜设备，等待连接...";
            return;
        }
        if ([_env isHostBlocked]) {
            _statusNote = @"官方自动更新未关闭，请在眼镜软件版本页关闭...";
            return;
        }
        if (![_env isFeedReady]) {
            if(_lastActionUptime && now-_lastActionUptime<RETRY_INTERVAL_SECONDS)return;
            if(_feedAttempts>=3){[self stop];_statusNote=@"下载来源准备失败，请核对本地固件后重试";return;}
            _feedAttempts++;_lastActionUptime=now;
            NSError *err=nil;[_env ensureFeedListening:&err];
            _statusNote=err.localizedDescription?:@"等待下载来源健康检查";
            return;
        }
        _device=[device copy];
        _phase = TIOOTAAutoRunPhaseINITIAL;
        _initialUptime = now;
        _lastActionUptime = now;
        _statusNote = @"前置条件满足，进入 8 秒静默期...";
        return;
    }

    if (_phase == TIOOTAAutoRunPhaseINITIAL) {
        if (!device.length) {
            _statusNote = @"设备连接中断，等待重新连接...";
            return;
        }
        if (now - _initialUptime < INITIAL_SILENCE_SECONDS) {
            NSTimeInterval left = INITIAL_SILENCE_SECONDS - (now - _initialUptime);
            _statusNote = [NSString stringWithFormat:@"正在进行初始安全静默 (剩余 %.0f 秒)...", left > 0 ? left : 0];
            return;
        }
        if (_preflightAttempts && [_env isTargetVersionRecent]) {
            _phase = TIOOTAAutoRunPhaseAUTO_ARM;
            _lastActionUptime = 0;
            _statusNote = @"已获得有效固件版本回执，准备自动授权...";
            return;
        }
        if (_lastActionUptime == 0 || (now - _lastActionUptime >= RETRY_INTERVAL_SECONDS)) {
            if (_preflightAttempts < PREFLIGHT_CAP) {
                _preflightAttempts++;
                _lastActionUptime = now;
                [_env sendProbe:1 device:device];
                [_env sendProbe:2 device:device];
                [_env sendProbe:11 device:device];
                _statusNote = [NSString stringWithFormat:@"已发送第 %lu/3 次只读预检探针", (unsigned long)_preflightAttempts];
            } else {
                [_env cancelGate];
                _phase = TIOOTAAutoRunPhaseSTOPPED;
                _statusNote = @"3 次预检未收到有效版本回执，已终止";
                [self stopTimerLocked];
            }
        }
        return;
    }

    if (_phase == TIOOTAAutoRunPhaseAUTO_ARM) {
        if(!device.length){[self stop];_statusNote=@"授权前设备已断开，请重新预检";return;}
        if ([_env isGateArmed] || [_env isGateStarted]) {
            _armedAt=now;
            _phase = TIOOTAAutoRunPhaseAFTER_INSTALL;
            _lastActionUptime = 0;
            _statusNote = @"已授权，等待开始安装与设备重启...";
            return;
        }
        if (_lastActionUptime == 0 || (now - _lastActionUptime >= RETRY_INTERVAL_SECONDS)) {
            if (_armAttempts < ARM_CAP) {
                _armAttempts++;
                _lastActionUptime = now;
                NSError *err = nil;
                BOOL ok = [_env isTargetVersionRecent] && [_env authorizeGate:&err];
                if (ok || [_env isGateArmed] || [_env isGateStarted]) {
                    _armedAt=now;
                    _phase = TIOOTAAutoRunPhaseAFTER_INSTALL;
                    _lastActionUptime = 0;
                    _statusNote = @"自动授权完成，请在官方页点击「开始安装」，等待安装重启...";
                } else {
                    _statusNote = [NSString stringWithFormat:@"第 %lu/3 次自动授权失败: %@", (unsigned long)_armAttempts, err.localizedDescription ?: @"未知错误"];
                }
            } else {
                [_env cancelGate];
                _phase = TIOOTAAutoRunPhaseSTOPPED;
                _statusNote = @"自动授权尝试超过 3 次，已终止";
                [self stopTimerLocked];
            }
        }
        return;
    }

    if (_phase == TIOOTAAutoRunPhaseAFTER_INSTALL) {
        if(now-_armedAt>=900){[self stop];_statusNote=@"等待安装或恢复超过15分钟，已停止自动推进";return;}
        if (![_env isGateStarted]) {
            _statusNote = @"已授权，请在官方固件页点击「开始安装」...";
            return;
        }

        if(!device.length || ![_env recoveryAllowed:device uptime:now]){
            _statusNote=@"等待同一设备断连重启并稳定恢复连接";return;
        }
        if(_lastActionUptime && now-_lastActionUptime<RETRY_INTERVAL_SECONDS)return;
        if ([_env isFreshBoot:now]) {
            if (_releaseAttempts < RELEASE_CAP) {
                _releaseAttempts++;
                _lastActionUptime = now;
                NSError *err = nil;
                BOOL relOk = [_env releaseGate:device uptime:now error:&err];
                if (relOk) {
                    _phase = TIOOTAAutoRunPhaseDONE;
                    _statusNote = @"一键升级与保护锁释放成功，固件已更新至 Strix OS 1.0.5.5！";
                    [self stopTimerLocked];
                    return;
                } else {
                    _statusNote = [NSString stringWithFormat:@"第 %lu/3 次释放保护失败: %@", (unsigned long)_releaseAttempts, err.localizedDescription ?: @"未知错误"];
                }
            } else {
                [_env cancelGate];
                _phase = TIOOTAAutoRunPhaseSTOPPED;
                _statusNote = @"释放保护锁尝试超过 3 次，已终止";
                [self stopTimerLocked];
                return;
            }
        } else {
            if (!device.length) {
                _statusNote = @"设备已断开重启，等待重新连回...";
                return;
            }
            if (_lastActionUptime == 0 || (now - _lastActionUptime >= RETRY_INTERVAL_SECONDS)) {
                if (_readbackAttempts < READBACK_CAP) {
                    _readbackAttempts++;
                    _lastActionUptime = now;
                    [_env sendProbe:1 device:device];
                    [_env sendProbe:11 device:device];
                    _statusNote = [NSString stringWithFormat:@"已重新连回，正在等待新固件回查 (第 %lu/11 次探针)...", (unsigned long)_readbackAttempts];
                } else {
                    [_env cancelGate];
                    _phase = TIOOTAAutoRunPhaseSTOPPED;
                    _statusNote = @"回查探针超过 11 次未获得新版本有效启动回执，已终止";
                    [self stopTimerLocked];
                }
            }
        }
        return;
    }
}

- (NSDictionary *)status {
    __block NSDictionary *dict = nil;
    OnMain(^{
        NSString *phaseName = @"UNKNOWN";
        switch (_phase) {
            case TIOOTAAutoRunPhaseIDLE: phaseName = @"IDLE"; break;
            case TIOOTAAutoRunPhaseDISCOVER: phaseName = @"DISCOVER"; break;
            case TIOOTAAutoRunPhaseINITIAL: phaseName = @"INITIAL"; break;
            case TIOOTAAutoRunPhaseAUTO_ARM: phaseName = @"AUTO_ARM"; break;
            case TIOOTAAutoRunPhaseAFTER_INSTALL: phaseName = @"AFTER_INSTALL"; break;
            case TIOOTAAutoRunPhaseSTOPPED: phaseName = @"STOPPED"; break;
            case TIOOTAAutoRunPhaseDONE: phaseName = @"DONE"; break;
        }
        dict = @{
            @"phase": @(_phase),
            @"phaseName": phaseName,
            @"statusNote": _statusNote ?: @"",
            @"preflightAttempts": @(_preflightAttempts),
            @"armAttempts": @(_armAttempts),
            @"readbackAttempts": @(_readbackAttempts),
            @"releaseAttempts": @(_releaseAttempts)
        };
    });
    return dict;
}

@end

void TIOOTAAutoRunStart(void) {
    [TIOOTAAutoRun.shared start];
}

void TIOOTAAutoRunStop(void) {
    [TIOOTAAutoRun.shared stop];
}

NSDictionary *TIOOTAAutoRunStatus(void) {
    return [TIOOTAAutoRun.shared status];
}
