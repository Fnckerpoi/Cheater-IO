#import "MapKitHUDPolicy.h"
#import "MapKitHUD.h"
#import "MapKitHUDHost.h"
#include <math.h>
static NSUInteger Checks;
static void Check(BOOL value,NSString *note){Checks++;if(!value){NSLog(@"FAIL %@",note);exit(1);}}
static NSDictionary *Frame(NSString *sid,NSUInteger revision,double at){return @{@"sessionId":sid,@"revision":@(revision),@"updatedAt":@(at),@"provider":@"mapkit",@"mode":@"walk",@"phase":@"navigating"};}
int main(void){@autoreleasepool{
 __block double now=100;NSString *sid=NSUUID.UUID.UUIDString;NSData *pixels=[NSData dataWithBytesNoCopy:calloc(65536,1) length:65536 freeWhenDone:YES];
 TIOMapKitHUDPolicy *p=[[TIOMapKitHUDPolicy alloc]initWithClock:^{return now;}];
 Check(![p start:Frame(sid,1,now) pixels:pixels foreground:NO allowed:YES],@"foreground required");
 Check(![p start:Frame(sid,1,now) pixels:pixels foreground:YES allowed:NO],@"ownership/conflict required");
 Check(![p start:Frame(sid,1,now-16) pixels:pixels foreground:YES allowed:YES],@"stale start rejected");
 Check(![p start:Frame(sid,1,now+1) pixels:pixels foreground:YES allowed:YES],@"future start rejected");
 Check(![p start:Frame(sid,1,now) pixels:[NSData data] foreground:YES allowed:YES],@"pixel bound");
 Check([p start:Frame(sid,1,now) pixels:pixels foreground:YES allowed:YES],@"explicit start");
 Check(p.active&&!p.ready&&![p canSubmitWithBusy:NO],@"query active not ready without ACK");
 Check(![p start:Frame(sid,2,now) pixels:pixels foreground:YES allowed:YES],@"one page only");
 [p acknowledgeReady];Check([p canSubmitWithBusy:NO]&&![p canSubmitWithBusy:YES],@"real ACK and one file gate");
 [p didSubmit];Check(![p canSubmitWithBusy:NO],@"same revision not queued");
 now+=0.1;Check([p offer:Frame(sid,2,now) pixels:pixels],@"latest accepted");
 now+=0.1;Check([p offer:Frame(sid,3,now) pixels:pixels],@"newest replaces previous");
 Check([p.frame[@"revision"] isEqual:@3]&&![p canSubmitWithBusy:NO],@"one Hz throttle");
 Check(![p offer:Frame(sid,2,now) pixels:pixels],@"old revision rejected");
 Check(![p offer:Frame(NSUUID.UUID.UUIDString,4,now) pixels:pixels],@"wrong session rejected");
 Check(![p offer:Frame(sid,2,99) pixels:pixels]&&p.active,@"old revision cannot invalidate latest");
 now=101;Check([p canSubmitWithBusy:NO],@"one second permits latest");[p didSubmit];
 now=116;Check(!p.fresh&&![p canSubmitWithBusy:NO],@"15 second stale disables send");
 [p stop:@"background"];Check(!p.active&&!p.ready&&!p.pixels,@"stop clears target and ACK");
 Check(![p offer:Frame(sid,4,now) pixels:pixels],@"no auto resume");[p acknowledgeReady];Check(!p.ready,@"late ACK never resumes");
 Check([p start:Frame(NSUUID.UUID.UUIDString,1,now) pixels:pixels foreground:YES allowed:YES],@"new explicit session can start");
 [p stop:@"device changed"];Check(![p canSubmitWithBusy:NO],@"device lifecycle stop");
 NSMutableDictionary *reroute=[Frame(sid,1,now) mutableCopy];reroute[@"phase"]=@"rerouting";Check(![p start:reroute pixels:pixels foreground:YES allowed:YES],@"rerouting start is not actionable");
 Check([p start:Frame(sid,1,now) pixels:pixels foreground:YES allowed:YES],@"fresh session for signal transition");reroute[@"revision"]=@2;Check(![p offer:reroute pixels:pixels]&&!p.active&&!p.pixels,@"higher rerouting clears actionable target");
 Check([p start:Frame(sid,1,now) pixels:pixels foreground:YES allowed:YES],@"fresh session for stale transition");Check(![p offer:Frame(sid,2,now-16) pixels:pixels]&&!p.active&&!p.pixels,@"higher stale target stops rather than retaining old arrow");
 NSMutableDictionary *bad=[Frame(sid,2,now) mutableCopy];bad[@"revision"]=@1.5;Check(![p start:bad pixels:pixels foreground:YES allowed:YES],@"integer revisions only");bad[@"revision"]=@2;bad[@"updatedAt"]=@(NAN);Check(![p start:bad pixels:pixels foreground:YES allowed:YES],@"finite uptime only");
#if !TIO_MAPKIT_HUD_ENABLED
 Check(![TIOMapKitHUDStatus()[@"enabled"] boolValue],@"disabled note unavailable");Check(!TIOMapKitHUDStart(Frame(sid,1,now),pixels)&&!TIOMapKitHUDConfirmIdle(),@"disabled no start side effects");
 __block NSUInteger callbacks=0;Check(!TIOMapKitHUDRouteEvent(@{},^(BOOL owned){callbacks++;Check(!owned,@"disabled event unowned");}),@"disabled route false");Check(callbacks==0,@"unadmitted route never invokes completion");
#endif
 bad[@"revision"]=@YES;bad[@"updatedAt"]=@(now);Check(![p start:bad pixels:pixels foreground:YES allowed:YES],@"boolean revision rejected");bad[@"revision"]=@2;bad[@"updatedAt"]=@YES;Check(![p start:bad pixels:pixels foreground:YES allowed:YES],@"boolean uptime rejected");
 Check([p start:Frame(sid,1,now) pixels:pixels foreground:YES allowed:YES],@"fresh session for mode transition");NSMutableDictionary *mode=[Frame(sid,2,now) mutableCopy];mode[@"mode"]=@"drive";Check(![p offer:mode pixels:pixels]&&!p.active,@"mode mutation requires new explicit session");
 NSLog(@"MapKit HUD policy: %lu checks passed",(unsigned long)Checks);
}return 0;}
