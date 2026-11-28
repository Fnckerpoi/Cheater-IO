#import <Foundation/Foundation.h>
// Separate opt-in build. PREPARE-02 cannot enable this gate.
NSDictionary *TIOOTAFrame(NSData *data);
BOOL TIOOTAAutoUpdateDisabled(NSDictionary *preferences);
@interface TIOOTAFlashGate:NSObject
- (BOOL)prepareDirectory:(NSURL *)directory device:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error;
- (BOOL)allows:(NSData *)payload device:(NSString *)device uptime:(NSTimeInterval)now;
- (NSDictionary *)status;
- (BOOL)cancel;
- (BOOL)releaseDevice:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error;
- (void)observeEvent:(NSDictionary *)event uptime:(NSTimeInterval)now;
- (BOOL)beginReadback:(NSString *)device uptime:(NSTimeInterval)now;
- (BOOL)freshBoot:(NSTimeInterval)now;
- (BOOL)recoveryAllowed:(NSString *)device uptime:(NSTimeInterval)now;
- (BOOL)isArmed;
- (BOOL)isStarted;
@end
BOOL TIOOTAFlashBuild(void);
BOOL TIOOTAFlashProtected(void);
BOOL TIOOTAFlashBlockCall(id call);
BOOL TIOOTAFileCallBlocked(NSString *method, BOOL scopedImage, NSUInteger stage);
BOOL TIOPhoneOTAReadAllowed(NSData *data);
void TIOOTAFlashObserveEvent(NSDictionary *event);
BOOL TIOOTAFlashAuthorize(NSError **error);
BOOL TIOOTAFlashCancel(void);
BOOL TIOOTAFlashRelease(NSString *device, NSTimeInterval now, NSError **error);
BOOL TIOOTASendProbe(NSUInteger type, NSString *device);
BOOL TIOOTAFlashFreshBoot(NSTimeInterval now);
BOOL TIOOTAFlashRecoveryAllowed(NSString *device, NSTimeInterval now);
NSDictionary *TIOOTAFlashStatus(void);
void TIOOTAFlashDisableAutoUpdateIfRequested(void);
