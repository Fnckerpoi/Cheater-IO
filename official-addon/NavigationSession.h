#import <Foundation/Foundation.h>
BOOL TIONavigationClaim(NSString *owner);
void TIONavigationRelease(NSString *owner);
NSString *TIONavigationOwner(void);
