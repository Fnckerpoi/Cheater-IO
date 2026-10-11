#import <Foundation/Foundation.h>
// One-use foreground user consent, never call from automatic state inference.
BOOL TIOMapKitHUDConfirmIdle(void);
// RouteEvent: NO means not admitted and completion is NOT invoked.
// YES means completion exactly once (owned=NO: forward to the prior listener).
// Invoke before forwarding every host SDK method call. Never swallow that call.
void TIOMapKitHUDObserveCall(NSString *method,NSDictionary *args);
BOOL TIOMapKitHUDIsScopedCall(NSString *method,NSDictionary *args);
