#import <Foundation/Foundation.h>
#import "display_carrier.h"
#define TIO_MAPKIT_HUD_ENABLED 1
#define TIO_MAPKIT_HUD_TESTING 1
static double FakeNow=100;
static NSTimeInterval TIOMapKitHUDTestUptime(void){return FakeNow;}
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive};
static NSString *const UIApplicationWillResignActiveNotification=@"HUDTestResignActive";
@interface UIApplication:NSObject
@property UIApplicationState applicationState;
+ (instancetype)sharedApplication;
@end
@implementation UIApplication
+ (instancetype)sharedApplication{static UIApplication *app;static dispatch_once_t once;dispatch_once(&once,^{app=[self new];});return app;}
@end
static NSString *Device=@"HUD-TEST-DEVICE";
static NSString *TestHome(void){static NSString *home;static dispatch_once_t once;dispatch_once(&once,^{home=[NSTemporaryDirectory() stringByAppendingPathComponent:[@"MapKitHUD-runtime-" stringByAppendingString:NSUUID.UUID.UUIDString]];});return home;}
#define NSHomeDirectory TestHome
#import "MapKitHUD.m"
static NSUInteger Checks,SDKCalls,ScopedChecks;
static NSData *LastPacket;
static NSString *LastNativeTask;
static BOOL TeleBusy;
static void Check(BOOL value,NSString *note){Checks++;if(!value){NSLog(@"FAIL %@",note);exit(1);}}
@interface FlutterMethodCall:NSObject
@property NSString *method;
@property NSDictionary *arguments;
+ (instancetype)methodCallWithMethodName:(NSString *)method arguments:(NSDictionary *)args;
@end
@implementation FlutterMethodCall
+ (instancetype)methodCallWithMethodName:(NSString *)method arguments:(NSDictionary *)args{FlutterMethodCall *call=[self new];call.method=method;call.arguments=args;return call;}
@end
@interface HUDTestPlugin:NSObject
- (void)handleMethodCall:(FlutterMethodCall *)call result:(void(^)(id))result;
@end
@implementation HUDTestPlugin
- (void)handleMethodCall:(FlutterMethodCall *)call result:(void(^)(id))result{
 Check(TIOMapKitHUDIsScopedCall(call.method,call.arguments),@"SDK invocation exact scoped guard");ScopedChecks++;
 TIOMapKitHUDObserveCall(call.method,call.arguments);SDKCalls++;
 LastPacket=[NSData dataWithContentsOfFile:call.arguments[@"filePath"]];Check(LastPacket.length>=32&&LastPacket.length<=512,@"real bounded .tdp file written");
 LastNativeTask=[NSString stringWithFormat:@"SDK-%lu",(unsigned long)SDKCalls];result(@{@"success":@YES,@"taskId":LastNativeTask});
}
@end
id TIOProtocolPlugin(void){static id plugin;static dispatch_once_t once;dispatch_once(&once,^{plugin=[HUDTestPlugin new];});return plugin;}
NSString *TIOProtocolDevice(void){return Device;}
NSString *TIONavigationOwner(void){return @"mapkit";}
NSDictionary *TIOSubtitleNavigationStatus(void){return @{@"phase":@"idle",@"navigation":@NO,@"pending":@NO};}
NSDictionary *TIONewsTeleStatus(void){return @{@"active":@(TeleBusy)};}
NSDictionary *TIONavTransportStatus(void){return @{@"enabled":@NO,@"pending":@NO,@"uncertain":@NO,@"hasOwner":@NO};}
static void Drain(void){for(NSUInteger i=0;i<4;i++)[NSRunLoop.mainRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.003]];}
static NSDictionary *Frame(void){return @{@"sessionId":NSUUID.UUID.UUIDString,@"revision":@1,@"updatedAt":@(FakeNow),@"provider":@"mapkit",@"mode":@"walk",@"phase":@"navigating"};}
static NSDictionary *Reply(uint32_t requestOffset,NSString *device,BOOL corrupt){
 const uint8_t *p=LastPacket.bytes;unsigned op=p[5];uint32_t sid=op==TDP_QUERY?321:U32(p+8),revision=U32(p+16)+((op==TDP_RECT||op==TDP_FRAME_COMMIT)?1:0);
 TDPResult result=op==TDP_QUERY?TDP_CAPS:op==TDP_KEEPALIVE?TDP_ALIVE:op==TDP_CLOSE?TDP_CLOSED:(op==TDP_RECT||op==TDP_FRAME_COMMIT)?TDP_UI_SUBMITTED:TDP_STAGED;
 TDPReply reply={result,sid,U32(p+12)+requestOffset,revision,512,128,472,120000};uint8_t bytes[256];size_t len=tdp_carrier_reply(&reply,bytes,sizeof bytes);if(corrupt)bytes[len-4]^=1;
 return @{@"eventType":@"messageReceived",@"message":@{@"deviceId":device,@"businessId":@15,@"payload":[NSData dataWithBytes:bytes length:len]}};
}
static NSDictionary *File(void){return @{@"eventType":@"fileShareSuccess",@"device":@{@"id":Device},@"role":@"sender",@"taskId":LastNativeTask,@"fileName":@"turbo-display.tdp"};}
static BOOL Route(NSDictionary *event,BOOL *admitted,NSUInteger *callbacks){__block BOOL owned=NO;__block NSUInteger n=0;BOOL a=TIOMapKitHUDRouteEvent(event,^(BOOL value){owned=value;n++;});if(admitted)*admitted=a;if(callbacks)*callbacks=n;return owned;}
static void AckCurrent(void){Drain();Check(Route(Reply(0,Device,NO),NULL,NULL),[NSString stringWithFormat:@"exact AP accepted op=%u now=%.2f packet=%.2f inflight=%.2f active=%d request=%u state=%@",PacketOp,FakeNow,PacketAt,InFlightAt,Policy.active,Request,Session.state]);Check(Route(File(),NULL,NULL),@"exact SDK file terminal accepted");FakeNow+=0.01;Drain();}
int main(void){@autoreleasepool{
 NSData *pixels=[NSMutableData dataWithLength:65536];BOOL admitted;NSUInteger callbacks;
 Check(!Route(@{},&admitted,&callbacks)&&!admitted&&callbacks==0,@"noncandidate returns NO and never calls completion");
 Check(!Route(@{@"eventType":@"messageReceived",@"message":@"bad"},&admitted,&callbacks)&&!admitted&&callbacks==0,@"malformed message fails safely");
 Check(!TIOMapKitHUDStart(Frame(),pixels)&&SDKCalls==0,@"no consent means no SDK effect");TeleBusy=YES;Check(!TIOMapKitHUDConfirmIdle(),@"known tele conflict denies consent");TeleBusy=NO;
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"one explicit query starts");Drain();Check(SDKCalls==1&&((const uint8_t *)LastPacket.bytes)[5]==TDP_QUERY,@"firmware query first only");
 Check(!Route(Reply(0,@"OTHER-DEVICE",NO),&admitted,&callbacks)&&admitted&&callbacks==1,@"wrong device candidate forwarded once");
 Check(!Route(Reply(1,Device,NO),&admitted,&callbacks)&&admitted&&callbacks==1,@"wrong request not owned");
 Check(!Route(Reply(0,Device,YES),&admitted,&callbacks)&&!admitted&&callbacks==0,@"bad CRC namespace not admitted");
 NSDictionary *queryReply=Reply(0,Device,NO);Check(Route(queryReply,NULL,NULL),@"query AP accepted");Check(![TIOMapKitHUDStatus()[@"ready"] boolValue]&&SDKCalls==1,@"AP alone never renders");Check(!Route(queryReply,&admitted,&callbacks)&&admitted&&callbacks==1,@"duplicate AP forwarded once");
 NSDictionary *queryFile=File();Check(Route(queryFile,NULL,NULL),@"query file accepted");Check(!Route(queryFile,&admitted,&callbacks)&&admitted&&callbacks==1,@"duplicate file not owned");
 Check(SDKCalls==1,@"query dual ACK alone still waits renew");FakeNow+=0.2;Pump();Drain();Check(((const uint8_t *)LastPacket.bytes)[5]==TDP_KEEPALIVE,@"real lease query precedes first frame");
 AckCurrent();Check(((const uint8_t *)LastPacket.bytes)[5]==TDP_FRAME_BEGIN&&[TIOMapKitHUDStatus()[@"ready"] boolValue],@"frame begins only after real lease dual ACK");
 NSUInteger before=SDKCalls;while(Session.frameActive&&SDKCalls-before<150)AckCurrent();Check(Session.hasPixelBaseline&&Session.completedChunks==139,@"all139 chunks commit via exact AP+file guards");
 TIOMapKitHUDStop(@"test stop");Drain();Check(!Timer&&!Policy.active&&Closing&&PacketOp==TDP_CLOSE,@"stop invalidates active timer and sends close when safe");AckCurrent();Check(CloseConfirmed&&!Closing&&!Session.busy,@"close only confirmed by both real receipts");
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"explicit second start");Drain();NSDictionary *lateReply=Reply(0,Device,NO);FakeNow+=5.1;Pump();Check(!Policy.active&&!Timer&&Session.busy,@"no ACK timeout stops and retains uncertain file lease");Check(!Route(lateReply,&admitted,&callbacks)&&admitted&&callbacks==1,@"late ACK cannot revive stopped page");Check(!TIOMapKitHUDConfirmIdle(),@"unknown file terminal blocks new session");Check(Route(File(),NULL,NULL),@"late exact file terminal safely releases ownership");
 Check(TIOMapKitHUDConfirmIdle(),@"explicit consent possible only after exact late file terminal");TIOMapKitHUDObserveCall(@"rayneonet_sendMessage",@{});Check(!TIOMapKitHUDStart(Frame(),pixels),@"external transmit invalidates one-use consent");
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"early file test query");
 NSDictionary *earlyFile=File();Check(!Route(earlyFile,&admitted,&callbacks)&&admitted&&callbacks==1,@"early unbound SDK task stays unowned");Drain();Check(FileAccepted&&!Route(earlyFile,NULL,NULL),@"only exact bound early file is consumed once");Check(Route(Reply(0,Device,NO),NULL,NULL),@"early file plus AP completes query");
 FakeNow+=0.2;Pump();AckCurrent();while(Session.frameActive&&FakeNow-InFlightAt<14.7){FakeNow+=0.11;AckCurrent();}FakeNow+=0.4;Pump();Check(!Policy.active&&!Timer&&!Session.hasPixelBaseline,@"slow full-frame exceeds15s and stops without stale commit");Check(Route(File(),NULL,NULL),@"slow stale frame retains then releases exact outstanding file");
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"background test query");Drain();UIApplication.sharedApplication.applicationState=UIApplicationStateInactive;[NSNotificationCenter.defaultCenter postNotificationName:UIApplicationWillResignActiveNotification object:nil];Check(!Policy.active&&!Timer,@"background notification stops timer without resume");UIApplication.sharedApplication.applicationState=UIApplicationStateActive;Check(Route(File(),NULL,NULL),@"background late exact terminal releases ownership");
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"weak-signal transition query");Drain();NSMutableDictionary *weak=[Policy.frame mutableCopy];weak[@"revision"]=@2;weak[@"phase"]=@"weak";TIOMapKitHUDOffer(weak,pixels);Check(!Policy.active&&!Policy.pixels&&!Timer,@"weak higher revision cleans policy pixels and timer");Check(Route(File(),NULL,NULL),@"weak stop late exact terminal releases ownership");
 Check(TIOMapKitHUDConfirmIdle()&&TIOMapKitHUDStart(Frame(),pixels),@"third explicit query");Drain();Device=@"CHANGED-DEVICE";Pump();Check(!Policy.active&&!Timer&&[Note containsString:@"实体按钮"],@"device change stops and reports unconfirmed close");
 Check(ScopedChecks==SDKCalls,@"every SDK submission exercised exact host hook exemption");
 __block NSUInteger asyncCalls=0;dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT,0),^{TIOMapKitHUDRouteEvent(lateReply,^(BOOL owned){Check(NSThread.isMainThread&&!owned,@"off-main route completion marshaled once");asyncCalls++;});});Drain();Check(asyncCalls==1,@"off-main completion exactly once");
 NSLog(@"MapKit HUD runtime: %lu checks passed (%lu real file submissions; spool %@)",(unsigned long)Checks,(unsigned long)SDKCalls,TestHome());
}return 0;}
