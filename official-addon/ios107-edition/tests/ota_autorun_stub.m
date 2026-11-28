#import <Foundation/Foundation.h>
NSDictionary *TIOExperimentalOTAFeedStatus(void){return @{};}
BOOL TIOBeginExperimentalOTAPreparation(NSError **error){return NO;}
NSString *TIOProtocolDevice(void){return nil;}
BOOL TIOOTAAutoUpdateDisabled(NSDictionary *preferences){return NO;}
NSDictionary *TIOOTAFlashStatus(void){return @{};}
BOOL TIOOTAFlashRecoveryAllowed(NSString *device,NSTimeInterval now){return NO;}
BOOL TIOOTAFlashCancel(void){return NO;}
BOOL TIOOTAFlashFreshBoot(NSTimeInterval now){return NO;}
BOOL TIOOTASendProbe(NSUInteger type,NSString *device){return NO;}
BOOL TIOOTAFlashAuthorize(NSError **error){return NO;}
BOOL TIOOTAFlashRelease(NSString *device,NSTimeInterval now,NSError **error){return NO;}
