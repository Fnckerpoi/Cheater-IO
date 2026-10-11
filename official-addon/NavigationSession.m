#import "NavigationSession.h"
static NSString *Owner;
BOOL TIONavigationClaim(NSString *owner){ @synchronized(NSProcessInfo.processInfo){ if(!owner.length || Owner) return NO; Owner=[owner copy]; return YES; } }
void TIONavigationRelease(NSString *owner){ @synchronized(NSProcessInfo.processInfo){ if([Owner isEqual:owner]) Owner=nil; } }
NSString *TIONavigationOwner(void){ @synchronized(NSProcessInfo.processInfo){ return Owner; } }
