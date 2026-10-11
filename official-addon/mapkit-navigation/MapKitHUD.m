#import "MapKitHUD.h"
#import "MapKitHUDHost.h"
#ifndef TIO_MAPKIT_HUD_ENABLED
#define TIO_MAPKIT_HUD_ENABLED 0
#endif
#if !TIO_MAPKIT_HUD_ENABLED
BOOL TIOMapKitHUDStart(NSDictionary *frame,NSData *pixels){return NO;}
void TIOMapKitHUDOffer(NSDictionary *frame,NSData *pixels){}
void TIOMapKitHUDStop(NSString *reason){}
NSDictionary *TIOMapKitHUDStatus(void){return @{@"enabled":@NO,@"active":@NO,@"ready":@NO,@"busy":@NO,@"note":@"本构建未启用可选 TDP1 眼镜 HUD；手机导航仍可使用"};}
BOOL TIOMapKitHUDRouteEvent(NSDictionary *event,void(^completion)(BOOL)){return NO;}
BOOL TIOMapKitHUDConfirmIdle(void){return NO;}
void TIOMapKitHUDObserveCall(NSString *method,NSDictionary *args){}
BOOL TIOMapKitHUDIsScopedCall(NSString *method,NSDictionary *args){return NO;}
#else
#if !defined(TIO_MAPKIT_HUD_TESTING)
#import <UIKit/UIKit.h>
#endif
#import <objc/message.h>
#import "MapKitHUDPolicy.h"
#import "DisplayPhoneSession.h"
#import "DisplayPhoneTransport.h"
#import "DisplayDiagnostics.h"
#import "display_carrier.h"
#import "ProtocolContext.h"
#import "NavigationSession.h"
#import "NavigationSubtitleHUD.h"
#import "NavigationTransport.h"
#import "NewsTeleprompter.h"
static TDPPhoneSession *Session;
static TDPPhoneTransport *Transport;
static TIOMapKitHUDPolicy *Policy;
static NSTimer *Timer;
static id BackgroundObserver;
static NSString *Peer,*NativeTask,*Note,*ConsentPeer;
static NSTimeInterval ConsentAt,PacketAt;
static NSDictionary *ScopedArgs;
static NSMutableArray *EarlyFiles;
static NSTimeInterval InFlightAt;
static BOOL ExternalConflict;
static BOOL Consume(NSDictionary *event);
static uint32_t Request,PacketSID;
static unsigned PacketOp;
static NSUInteger Packets,APReplies,FileReplies,CloseEpoch;
static BOOL APAccepted,FileAccepted,Closing,CloseConfirmed;
static NSTimeInterval Now(void){
#if defined(TIO_MAPKIT_HUD_TESTING)
 return TIOMapKitHUDTestUptime();
#else
 return NSProcessInfo.processInfo.systemUptime;
#endif
}
static BOOL Foreground(void){return NSThread.isMainThread&&UIApplication.sharedApplication.applicationState==UIApplicationStateActive;}
static BOOL Owner(void){return [TIONavigationOwner() isEqual:@"mapkit"];}
static uint32_t U32(const uint8_t *p){return p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24);}
static BOOL HostAvailable(void){
 id plugin=TIOProtocolPlugin();Class call=NSClassFromString(@"FlutterMethodCall");
 return TIOProtocolDevice().length&&plugin&&[call respondsToSelector:NSSelectorFromString(@"methodCallWithMethodName:arguments:")]&&[plugin respondsToSelector:NSSelectorFromString(@"handleMethodCall:result:")];
}
static BOOL Conflicts(void){
 NSDictionary *sub=TIOSubtitleNavigationStatus(),*tele=TIONewsTeleStatus(),*nav=TIONavTransportStatus();
 if(![sub isKindOfClass:NSDictionary.class]||![tele isKindOfClass:NSDictionary.class]||![nav isKindOfClass:NSDictionary.class])return YES;
 NSString *phase=sub[@"phase"];
 if(![phase isEqual:@"idle"]||[sub[@"navigation"] boolValue]||[sub[@"pending"] boolValue])return YES;
 for(NSString *key in @[@"active",@"ready",@"playing",@"started",@"stopping",@"manualPending",@"manualBlocked",@"replacing",@"navigation"])if([tele[key] boolValue])return YES;
 for(NSString *key in @[@"enabled",@"pending",@"uncertain",@"hasOwner",@"noticePending"])if([nav[key] boolValue])return YES;
 return NO;
}
BOOL TIOMapKitHUDIsScopedCall(NSString *method,NSDictionary *args){return NSThread.isMainThread&&ScopedArgs&&ScopedArgs==args&&TDPPhoneIsScopedCall(method,args);}
BOOL TIOMapKitHUDConfirmIdle(void){
 if(!Foreground()||!Owner()||!HostAvailable()||Policy.active||Closing||Session.busy||Conflicts())return NO;
 ConsentAt=Now();ConsentPeer=[TIOProtocolDevice() copy];return YES;
}
void TIOMapKitHUDObserveCall(NSString *method,NSDictionary *args){
 if(!NSThread.isMainThread){dispatch_async(dispatch_get_main_queue(),^{TIOMapKitHUDObserveCall([method copy],[args copy]);});return;}
 if(![method isKindOfClass:NSString.class]||TIOMapKitHUDIsScopedCall(method,args))return;
 if(ConsentPeer&&![ConsentPeer isEqual:TIOProtocolDevice()]){ConsentPeer=nil;ConsentAt=0;}
 BOOL transmission=[method isEqual:@"rayneonet_sendFile"]||[method isEqual:@"rayneonet_sendMessage"];
 BOOL lifecycle=[method rangeOfString:@"connect" options:NSCaseInsensitiveSearch].location!=NSNotFound||[method rangeOfString:@"record" options:NSCaseInsensitiveSearch].location!=NSNotFound||[method rangeOfString:@"voice" options:NSCaseInsensitiveSearch].location!=NSNotFound;
 if(transmission||lifecycle){ConsentPeer=nil;ConsentAt=0;if(Policy.active||Closing){ExternalConflict=YES;TIOMapKitHUDStop(@"检测到其他发送／录音／语音／断连操作，已停止；不会自动恢复");ExternalConflict=NO;}}
}
static void CloseCleanup(NSUInteger epoch){
 if(epoch!=CloseEpoch)return;CloseEpoch++;Closing=NO;Request=0;APAccepted=YES;[Session setForegroundActive:NO];
 if(!CloseConfirmed)Note=@"关闭未得到文件与 CLOSED 双回执；请用眼镜实体按钮退出。未知文件租约保留且禁止重发";
}
void TIOMapKitHUDStop(NSString *reason){
 if(!NSThread.isMainThread){NSString *r=[reason copy];dispatch_async(dispatch_get_main_queue(),^{TIOMapKitHUDStop(r);});return;}
 BOOL wasActive=Policy.active||Timer!=nil;ConsentAt=0;ConsentPeer=nil;[Timer invalidate];Timer=nil;
 if(BackgroundObserver){[NSNotificationCenter.defaultCenter removeObserver:BackgroundObserver];BackgroundObserver=nil;}
 if(Closing){CloseEpoch++;Closing=NO;Request=0;APAccepted=YES;[Session setForegroundActive:NO];Note=@"关闭受外部操作中断；请用眼镜实体按钮退出";return;}
 [Policy stop:reason];Note=reason?:@"已停止，不会自动恢复";
 if(!wasActive)return;
 CloseConfirmed=NO;
 if(!ExternalConflict&&[TIOProtocolDevice() isEqual:Peer]&&Session.ready&&!Session.busy&&HostAvailable()){
  Closing=YES;NSUInteger epoch=++CloseEpoch;
  if([Session closePage]){Note=@"已请求退出；等待文件与 CLOSED 双回执，未确认请用实体按钮退出";dispatch_after(dispatch_time(DISPATCH_TIME_NOW,5*NSEC_PER_SEC),dispatch_get_main_queue(),^{CloseCleanup(epoch);});return;}
  Closing=NO;
 }
 Request=0;APAccepted=YES;[Session setForegroundActive:NO];Note=[NSString stringWithFormat:@"%@；关闭未确认，请用眼镜实体按钮退出",Note];
}
static BOOL SendAllowed(unsigned op){
 if(!NSThread.isMainThread||![TIOProtocolDevice() isEqual:Peer]||!HostAvailable())return NO;
 if(Closing)return op==TDP_CLOSE;
 if((op==TDP_FRAME_CHUNK||op==TDP_FRAME_COMMIT)&&Now()-InFlightAt>15)return NO;
 return Policy.active&&Foreground()&&Owner()&&[Policy fresh]&&!Conflicts();
}
static BOOL Setup(void){
 NSString *device=TIOProtocolDevice();if(!device.length||Session.busy||Closing)return NO;
 Peer=[device copy];NativeTask=nil;Request=0;APAccepted=FileAccepted=NO;EarlyFiles=[NSMutableArray new];
 NSURL *root=[NSURL fileURLWithPath:[NSHomeDirectory() stringByAppendingPathComponent:@"Library/Application Support/TurboIOPrivateAddon/MapKitHUD-TDP1"] isDirectory:YES];
 Transport=[[TDPPhoneTransport alloc]initWithRoot:root device:Peer currentDevice:^{return TIOProtocolDevice();} call:^BOOL(NSString *method,NSDictionary *args,void(^result)(id)){
  if(!SendAllowed(PacketOp)||!TDPPhoneIsScopedCall(method,args))return NO;
  id plugin=TIOProtocolPlugin();Class cls=NSClassFromString(@"FlutterMethodCall");SEL make=NSSelectorFromString(@"methodCallWithMethodName:arguments:"),handle=NSSelectorFromString(@"handleMethodCall:result:");
  BOOL invoked=NO;ScopedArgs=args;
  @try{id call=((id(*)(id,SEL,id,id))objc_msgSend)(cls,make,method,args);if(call){((void(*)(id,SEL,id,id))objc_msgSend)(plugin,handle,call,[result copy]);invoked=YES;}}@catch(NSException *e){invoked=NO;}@finally{ScopedArgs=nil;}
  return invoked;
 }];
 TDPPhoneTransport *carrier=Transport;
 Session=[[TDPPhoneSession alloc]initWithDevice:Peer clock:^{return Now();} sender:^(NSData *packet,NSString *task,TDPSubmitted done){
  if(packet.length<32||!SendAllowed(((const uint8_t *)packet.bytes)[5])){done(NO,nil);return;}
  const uint8_t *p=packet.bytes;Request=U32(p+12);PacketSID=U32(p+8);PacketOp=p[5];PacketAt=Now();if(PacketOp==TDP_FRAME_BEGIN)InFlightAt=[Policy.frame[@"updatedAt"] doubleValue];NativeTask=nil;APAccepted=FileAccepted=NO;[EarlyFiles removeAllObjects];Packets++;
  [carrier send:packet task:task submitted:^(BOOL submitted,NSString *nativeTask){
   if(submitted&&[nativeTask isKindOfClass:NSString.class])NativeTask=[nativeTask copy];done(submitted,nativeTask);
   NSArray *early=[EarlyFiles copy];[EarlyFiles removeAllObjects];for(NSDictionary *event in early)if([event[@"taskId"] isEqual:NativeTask])Consume(event);
  }];
 }];
 return Session!=nil;
}
static void Pump(void){
 if(!Policy.active)return;
 if(!Foreground()||!Owner()||![TIOProtocolDevice() isEqual:Peer]||!HostAvailable()||Conflicts()||![Policy fresh]){TIOMapKitHUDStop(@"前台／设备／所有权／显示冲突或超过15秒旧帧，已停止");return;}
 [Session tick];
 if(!Policy.active)return;
 if(Session.ready&&!Session.busy)[Policy acknowledgeReady];
 if((Policy.ready&&!Session.ready)||(!Policy.ready&&Request&&Now()-PacketAt>=5)||(!Session.busy&&!Session.ready&&!Session.sessionID)){TIOMapKitHUDStop(Session.state?:@"未收到固件查询 ACK；本次已停用，不自动重试");return;}
 if([Policy canSubmitWithBusy:Session.busy]){
  BOOL sent=Session.hasPixelBaseline?[Session sendPartialPixels:Policy.pixels]:[Session sendFrame:Policy.pixels];
  if(sent)[Policy didSubmit];else TIOMapKitHUDStop(Session.state?:@"发送未确认；停止而非假成功");
 }
}
BOOL TIOMapKitHUDStart(NSDictionary *frame,NSData *pixels){
 if(!Foreground()||!Owner()||!HostAvailable()||Policy.active||Closing||Session.busy)return NO;
 BOOL consent=ConsentPeer&&[ConsentPeer isEqual:TIOProtocolDevice()]&&Now()-ConsentAt<30;ConsentPeer=nil;ConsentAt=0;
 if(!consent||Conflicts()){Note=@"请先退出字幕／提词／导航显示／录音／语音，并明确确认镜片空闲；未知状态默认拒绝";return NO;}
 if(!Policy)Policy=[[TIOMapKitHUDPolicy alloc]initWithClock:^{return Now();}];
 if(![Policy start:frame pixels:pixels foreground:YES allowed:YES]){Note=@"HUD 帧无效或已过期";return NO;}
 if(!Setup()){[Policy stop:@"当前设备／插件或文件租约未确认"];Note=Policy.note;return NO;}
 [Session setForegroundActive:YES];
 if(![Session query]){TIOMapKitHUDStop(@"查询未提交；不会绕过固件能力检查");return NO;}
 Note=nil;CloseConfirmed=NO;
 BackgroundObserver=[NSNotificationCenter.defaultCenter addObserverForName:UIApplicationWillResignActiveNotification object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *n){TIOMapKitHUDStop(@"手机离开前台，已停止，不会自动恢复");}];
 Timer=[NSTimer scheduledTimerWithTimeInterval:0.25 repeats:YES block:^(NSTimer *t){Pump();}];
 return YES;
}
void TIOMapKitHUDOffer(NSDictionary *frame,NSData *pixels){
 if(!NSThread.isMainThread)return;
 if(!Foreground()||!Owner()){TIOMapKitHUDStop(@"离开前台或导航所有权变化");return;}
 BOOL active=Policy.active;[Policy offer:frame pixels:pixels];if(active&&!Policy.active)TIOMapKitHUDStop(Policy.note);
}
NSDictionary *TIOMapKitHUDStatus(void){
 if(!NSThread.isMainThread)return @{@"enabled":@YES,@"active":@NO,@"ready":@NO,@"busy":@YES,@"note":@"HUD 状态需在主线程读取"};
 return @{@"enabled":@YES,@"active":@(Policy.active),@"ready":@(Policy.active&&Policy.ready&&Session.ready),@"busy":@(Session.busy||Closing),@"note":Note?:Session.state?:Policy.note?:@"需明确确认镜片空闲后启动；未知录音／语音状态不自动推断",@"packets":@(Packets),@"apACKs":@(APReplies),@"fileACKs":@(FileReplies),@"lensACKs":@0,@"physicalDisplayVerified":@NO,@"closeConfirmed":@(CloseConfirmed),@"chunks":@(Session.completedChunks),@"rects":@(Session.completedRects),@"pixelBaseline":@(Session.hasPixelBaseline)};
}
static BOOL Consume(NSDictionary *event){
 if(!Session)return NO;
 if(![TIOProtocolDevice() isEqual:Peer]&&Policy.active)TIOMapKitHUDStop(@"设备已变化；不再继续原设备的显示发送");
 NSString *type=event[@"eventType"];
 BOOL owned=NO;
 if([type isEqual:@"messageReceived"]){
  NSDictionary *m=event[@"message"];if(![m isKindOfClass:NSDictionary.class])return NO;NSData *payload=m[@"payload"];TDPReply reply;
  if(![m[@"deviceId"] isEqual:Peer]||![m[@"businessId"] isEqual:@15]||![payload isKindOfClass:NSData.class]||!tdp_carrier_decode(15,payload.bytes,payload.length,&reply))return NO;
  BOOL physical=reply.request==0&&reply.result==TDP_CLOSED&&Session.sessionID&&reply.sid==Session.sessionID;
  if(!physical&&(!Request||reply.request!=Request||APAccepted||Now()-PacketAt>=5||(PacketOp!=TDP_QUERY&&reply.sid!=PacketSID&&!((reply.result==TDP_NO_SESSION||reply.result==TDP_CLOSED)&&!reply.sid))))return NO;
  owned=[Session consumeEvent:event];if(owned){APAccepted=YES;APReplies++;}
  if(physical&&Policy.active)TIOMapKitHUDStop(@"眼镜实体退出；本次 HUD 已停止");
 }else if([type isEqual:@"fileShareSuccess"]||[type isEqual:@"fileShareFailed"]){
  if(![event[@"device"] isKindOfClass:NSDictionary.class]||![event[@"device"][@"id"] isEqual:Peer]||![event[@"role"] isEqual:@"sender"])return NO;
  if(!NativeTask){
   NSString *task=event[@"taskId"];
   if(Session.busy&&EarlyFiles.count<8&&[task isKindOfClass:NSString.class]&&task.length&&task.length<=256&&(![type isEqual:@"fileShareSuccess"]||[event[@"fileName"] isEqual:@"turbo-display.tdp"]))[EarlyFiles addObject:@{@"eventType":[type copy],@"device":@{@"id":Peer},@"role":@"sender",@"taskId":[task copy],@"fileName":[type isEqual:@"fileShareSuccess"]?@"turbo-display.tdp":@""}];
   return NO;
  }
  if(FileAccepted||![event[@"taskId"] isEqual:NativeTask])return NO;
  owned=[Session consumeEvent:event];if(owned){FileAccepted=YES;FileReplies++;}
 }else return NO;
 if(Closing&&owned&&APAccepted&&FileAccepted&&PacketOp==TDP_CLOSE&&!Session.ready&&!Session.busy){CloseConfirmed=YES;Note=@"收到文件终态与真实 CLOSED；页面已确认退出";CloseCleanup(CloseEpoch);}
 if(Policy.active)Pump();
 return owned;
}
BOOL TIOMapKitHUDRouteEvent(NSDictionary *event,void(^completion)(BOOL)){
 if(![event isKindOfClass:NSDictionary.class])return NO;
 NSString *type=event[@"eventType"];BOOL candidate=[type isEqual:@"fileShareSuccess"]||[type isEqual:@"fileShareFailed"];
 if([type isEqual:@"messageReceived"]){NSDictionary *m=event[@"message"];if(![m isKindOfClass:NSDictionary.class])return NO;NSData *p=m[@"payload"];TDPReply reply;candidate=[m[@"businessId"] isEqual:@15]&&[p isKindOfClass:NSData.class]&&tdp_carrier_decode(15,p.bytes,p.length,&reply);}
 if(!candidate)return NO;
 NSMutableDictionary *snapshot=[event mutableCopy];
 if([event[@"message"] isKindOfClass:NSDictionary.class]){NSMutableDictionary *m=[event[@"message"] mutableCopy];if([m[@"payload"] isKindOfClass:NSData.class])m[@"payload"]=[m[@"payload"] copy];snapshot[@"message"]=[m copy];}
 if([event[@"device"] isKindOfClass:NSDictionary.class])snapshot[@"device"]=[event[@"device"] copy];
 if(NSThread.isMainThread){BOOL owned=Consume(snapshot);if(completion)completion(owned);return YES;}
 dispatch_async(dispatch_get_main_queue(),^{BOOL owned=Consume(snapshot);if(completion)completion(owned);});return YES;
}
#endif
