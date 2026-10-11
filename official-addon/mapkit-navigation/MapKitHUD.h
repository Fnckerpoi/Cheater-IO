#import <Foundation/Foundation.h>
BOOL TIOMapKitHUDStart(NSDictionary *frame,NSData *pixels);
void TIOMapKitHUDOffer(NSDictionary *frame,NSData *pixels);
void TIOMapKitHUDStop(NSString *reason);
NSDictionary *TIOMapKitHUDStatus(void);
BOOL TIOMapKitHUDRouteEvent(NSDictionary *event,void(^completion)(BOOL owned));
