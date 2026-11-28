#import "OTAHost.h"
#import "ExperimentalOTAFeed.h"
#import "dots-v1/DotsGlassesTransfer.h"
#import "context-english-v1/ContextEnglishTransport.h"
#import "ExperimentalOTAFlash.h"
#import "MusicTransport.h"
#import "ReaderTransport.h"
#import "AppTransport.h"
#import "AppBridge.h"
#import "FocusTransport.h"
#import "RideTransport.h"
#import "TDTransport.h"
#import "ExperimentalOTA.h"
#import "ExperimentalOTAGuard.h"
#import "ProtocolContext.h"
#import <objc/message.h>
#import <objc/runtime.h>
#include <TargetConditionals.h>
#include <math.h>
#if TIO_IMAGE_RX_LAB
#import "ImageUploadTransport.h"
#if TIO_DISPLAY_PHONE
#import "DisplayPhoneTransport.h"
#if TIO_NATIVE_NAV
#import "TNVTransport.h"
#endif
#if TARGET_OS_IPHONE && TIO_DISPLAY_FLASH
#import "DisplayPhoneUI.h"
#endif
#endif
#endif
BOOL TIOOTAFileCallBlocked(NSString *method,BOOL scopedImage,NSUInteger stage){
    if(![method isKindOfClass:NSString.class])return YES;
    if([method isEqual:@"rayneonet_sendFile"]&&scopedImage&&stage==0)return NO;
    NSString *lower=method.lowercaseString;
    return [lower containsString:@"sendfile"]||[lower containsString:@"ota"]||[lower containsString:@"upgrade"];
}
static id Error(NSError **error,NSString *s){if(error)*error=[NSError errorWithDomain:@"TurboIO.OTAFlash" code:1 userInfo:@{NSLocalizedDescriptionKey:s}];return nil;}
static BOOL Number(id n,NSUInteger *out){
    if(![n isKindOfClass:NSNumber.class]||CFGetTypeID((__bridge CFTypeRef)n)==CFBooleanGetTypeID()||CFNumberIsFloatType((__bridge CFNumberRef)n)||[n longLongValue]<0)return NO;
    uint64_t v=[n unsignedLongLongValue];if(v>UINT32_MAX)return NO;*out=(NSUInteger)v;return YES;
}
static BOOL Var(const uint8_t *b,NSUInteger n,NSUInteger *at,uint64_t *v){*v=0;for(unsigned i=0;i<5;i++){if(*at>=n)return NO;uint8_t x=b[(*at)++];if(i==4&&(x&0xf0))return NO;*v|=(uint64_t)(x&127)<<(i*7);if(!(x&128))return i==0||x!=0;}return NO;}
NSDictionary *TIOOTAFrame(NSData *data){
    if(![data isKindOfClass:NSData.class]||data.length<4||data.length>65536)return nil;
    const uint8_t *b=data.bytes;NSUInteger at=0;unsigned seen=0;uint64_t version=0,type=0;NSData *json=[NSData data],*binary=[NSData data];
    while(at<data.length){uint64_t key,v;if(!Var(b,data.length,&at,&key))return nil;unsigned tag=(unsigned)(key>>3),wire=key&7;
        if(tag<1||tag>6||(seen&(1u<<tag)))return nil;seen|=1u<<tag;
        if(tag==3||tag==4){if(wire!=2||!Var(b,data.length,&at,&v)||v>data.length-at)return nil;if(tag==3)json=[data subdataWithRange:NSMakeRange(at,(NSUInteger)v)];else binary=[data subdataWithRange:NSMakeRange(at,(NSUInteger)v)];at+=(NSUInteger)v;}
        else{if(wire!=0||!Var(b,data.length,&at,&v))return nil;if(tag==1)version=v;if(tag==2)type=v;if(tag==6&&v!=0)return nil;}
    }
    if(version!=1||!type)return nil;
    id obj=json.length?[NSJSONSerialization JSONObjectWithData:json options:0 error:nil]:@{};
    if(![obj isKindOfClass:NSDictionary.class]&&![obj isKindOfClass:NSArray.class])return nil;
    return @{@"type":@(type),@"json":obj,@"binary":binary};
}
BOOL TIOPhoneOTAReadAllowed(NSData *data){
    NSDictionary *frame=TIOOTAFrame(data);NSUInteger type=[frame[@"type"] unsignedIntegerValue];
    return [@[@1,@2,@11] containsObject:@(type)]&&[frame[@"json"] isEqual:@{}]&&[frame[@"binary"] length]==0;
}
@implementation TIOOTAFlashGate{
    NSDictionary<NSString *,NSData *> *_files;NSArray *_manifest;NSString *_device,*_failure;
    NSTimeInterval _startedAt,_downAt,_connectedAt,_readbackAt,_bootAt,_idleAt;BOOL _downObserved,_reconnected,_bootGood,_idleGood;
    NSTimeInterval _deadline;NSUInteger _stage,_slices,_bytes;BOOL _versionSent,_manifestSent;
}
- (BOOL)prepareDirectory:(NSURL *)directory device:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error{
    @synchronized(self){
        if(_stage||![device isKindOfClass:NSString.class]||!device.length||device.length>200||!isfinite(now)||now<0){Error(error,@"当前不是可授权状态或没有有效设备；未开放。");return NO;}
        NSDictionary *files=TIOCopyExperimentalOTAPayloads(directory,error);if(!files)return NO;
        id manifest=[NSJSONSerialization JSONObjectWithData:files[@"OtaFileInfo.json"] options:0 error:error];if(![manifest isKindOfClass:NSArray.class]||[manifest count]!=15)return NO;
        _files=files;_manifest=manifest;_device=[device copy];_deadline=now+900;_stage=1;_failure=nil;return YES;
    }
}
- (BOOL)allows:(NSData *)payload device:(NSString *)device uptime:(NSTimeInterval)now{
    @synchronized(self){
        NSDictionary *f=TIOOTAFrame(payload);NSUInteger t=[f[@"type"] unsignedIntegerValue];id j=f[@"json"];NSData *binary=f[@"binary"];
        BOOL dict=[j isKindOfClass:NSDictionary.class],empty=dict&&[j count]==0&&!binary.length;
        // 1 = OS version, 2 = battery. Idle is 11, NOT 2: official 1.0.5
        // failure recovery sent msgType=11 on 2026-09-21 at 17:38:03.477.
        // Allow its empty read even after a blocked start so the official
        // recovery loop can establish that no glasses transfer is active.
        if((t==1||t==2||t==11)&&empty)return YES;
        NSUInteger n=0;
        if(t==3&&dict&&[j count]==1&&Number(j[@"OtaSize"],&n)&&n==8842896&&!binary.length)return YES;
        if(!_stage){if(t==4)_failure=@"TMG2 试刷尚未授权；开始指令未发送。请先在实验固件页完成 TMG2 风险确认。";return NO;}
        if(_stage==3||!isfinite(now)||now<0)return NO;
        if(_stage==1&&now>=_deadline){[self cancel];return NO;}
        BOOL ok=[device isEqual:_device];
        if(t==4)ok=ok&&_stage==1&&dict&&[j count]==1&&Number(j[@"Mode"],&n)&&n==2&&!binary.length;
        else if(t==5)ok=ok&&_stage==2&&dict&&[j isEqual:@{@"OtaVersion":@"Strix OS 1.0.5.5"}]&&!binary.length;
        else if(t==6)ok=ok&&_stage==2&&_versionSent&&[j isKindOfClass:NSArray.class]&&[j isEqual:_manifest]&&!binary.length;
        else if(t==7){
            NSUInteger start=0,size=0;NSString *name=dict?j[@"Name"]:nil;NSData *file=[name isKindOfClass:NSString.class]?_files[name]:nil;
            ok=ok&&_stage==2&&_manifestSent&&dict&&[j count]==3&&file&&![name isEqual:@"OtaFileInfo.json"]&&Number(j[@"Start"],&start)&&Number(j[@"Size"],&size)&&size>0&&size<=51200&&start<file.length&&size<=file.length-start&&binary.length==size;
            if(ok)ok=memcmp(binary.bytes,(const uint8_t *)file.bytes+start,size)==0;
            if(ok){_slices++;_bytes+=size;}
        }else ok=NO;
        if(!ok){_failure=@"非预期设备、会话、清单或分片，发送被拒绝";if(_stage==2)_stage=3;return NO;}
        if(t==4){_stage=2;_startedAt=now;_downObserved=NO;_reconnected=NO;_connectedAt=0;_readbackAt=0;_bootGood=NO;_idleGood=NO;}if(t==5)_versionSent=YES;if(t==6)_manifestSent=YES;return YES;
    }
}
- (BOOL)cancel{@synchronized(self){if(_stage>=2)return NO;_stage=0;_files=nil;_manifest=nil;_device=nil;_deadline=0;return YES;}}
- (BOOL)releaseDevice:(NSString *)device uptime:(NSTimeInterval)now error:(NSError **)error{
    @synchronized(self){
        if(![self recoveryAllowed:device uptime:now]||![self freshBoot:now]){
            if(error)*error=[NSError errorWithDomain:@"TIOOTA" code:1 userInfo:@{NSLocalizedDescriptionKey:@"当前不是已安装待解除保护状态"}];
            return NO;
        }
        if(![device isKindOfClass:NSString.class]||![device isEqual:_device]){
            if(error)*error=[NSError errorWithDomain:@"TIOOTA" code:2 userInfo:@{NSLocalizedDescriptionKey:@"设备标识与授权设备不符"}];
            return NO;
        }
        _stage=0;_files=nil;_manifest=nil;_device=nil;_deadline=0;_slices=0;_bytes=0;_versionSent=NO;_manifestSent=NO;_failure=nil;
        return YES;
    }
}
- (void)observeEvent:(NSDictionary *)event uptime:(NSTimeInterval)now{
    @synchronized(self){
        if(_stage!=2||!isfinite(now)||now<_startedAt)return;
        if([event[@"eventType"] isEqual:@"bleStateChange"]){
            if(![event[@"device"] isKindOfClass:NSDictionary.class]||![event[@"device"][@"id"] isEqual:_device])return;
            NSString *state=event[@"newState"];
            if([@[@"disconnected",@"connectFail",@"interrupted",@"connectTimeOut",@"authFailed",@"authInterrupt"] containsObject:state]){
                _downObserved=YES;_downAt=now;_reconnected=NO;_connectedAt=0;_readbackAt=0;_bootGood=NO;_idleGood=NO;
            }else if([state isEqual:@"clientReady"]&&_downObserved&&now>_downAt&&!_reconnected){_reconnected=YES;_connectedAt=now;}
            return;
        }
        if(![event[@"eventType"] isEqual:@"messageReceived"])return;
        NSDictionary *m=event[@"message"];NSUInteger business=0;
        if(![m isKindOfClass:NSDictionary.class]||!Number(m[@"businessId"],&business)||business!=9||![m[@"deviceId"] isEqual:_device])return;
        NSDictionary *f=TIOOTAFrame(m[@"payload"]);NSUInteger type=[f[@"type"] unsignedIntegerValue];id j=f[@"json"];
        if(type==1){_bootGood=NO;_bootAt=0;}
        if(type==11){_idleGood=NO;_idleAt=0;}
        if(!_readbackAt||now<=_readbackAt||![j isKindOfClass:NSDictionary.class])return;
        if(type==1){
            _bootGood=[j[@"OsVersion"] isEqual:@"Strix OS 1.0.5.5"]&&[j[@"OtaValidationConfirmed"] isEqual:@YES]&&[j[@"OtaValidationState"] isEqual:@"idle"];
            _bootAt=now;
        }else if(type==11){_idleGood=[j[@"Result"] isEqual:@YES];_idleAt=now;}
    }
}
- (BOOL)recoveryAllowed:(NSString *)device uptime:(NSTimeInterval)now{
    @synchronized(self){return _stage==2&&_versionSent&&_manifestSent&&[device isEqual:_device]&&isfinite(now)&&_downObserved&&_reconnected&&now-_startedAt>=60&&now-_connectedAt>=2;}
}
- (BOOL)beginReadback:(NSString *)device uptime:(NSTimeInterval)now{
    @synchronized(self){if(![self recoveryAllowed:device uptime:now])return NO;if(!_readbackAt)_readbackAt=now;return YES;}
}
- (BOOL)freshBoot:(NSTimeInterval)now{
    @synchronized(self){return [self recoveryAllowed:_device uptime:now]&&_readbackAt>0&&_bootGood&&_idleGood&&now-_bootAt>=0&&now-_bootAt<=60&&now-_idleAt>=0&&now-_idleAt<=60;}
}
- (BOOL)isArmed{@synchronized(self){return _stage==1;}}
- (BOOL)isStarted{@synchronized(self){return _stage>=2;}}
- (NSDictionary *)status{@synchronized(self){return @{@"stage":@(_stage),@"authorized":@(_stage==1||_stage==2),@"started":@(_stage>=2),@"versionMatched":@(_versionSent),@"manifestMatched":@(_manifestSent),@"validatedSlices":@(_slices),@"validatedSliceBytes":@(_bytes),@"failure":_failure?:@"",@"runtimeValidated":@NO};}}
@end
BOOL TIOOTAFlashBuild(void){
#if TARGET_OS_IPHONE && TIO_OTA_FLASH_ENABLED
#if TIO_DISPLAY_FLASH
    // Compiled experimental builds must stay gated even if Info.plist is stale.
    // Authorization/feed still require the exact marker below.
    return YES;
#else
    return TIOOTA107HostMatches(NSBundle.mainBundle.infoDictionary);
#endif
#else
    return NO;
#endif
}
static TIOOTAFlashGate *Gate;static NSString *ObservedDevice;static NSTimeInterval ObservedAt;static NSString *ObservedVersion;static BOOL ObservedConfirmed;
static NSString *PreflightDevice;static NSTimeInterval PreflightRequested[12],PreflightAt[12];static BOOL PreflightGood[12];
BOOL TIOOTAAutoUpdateDisabled(NSDictionary *preferences){
    if(![preferences isKindOfClass:NSDictionary.class])return NO;
    NSUInteger found=0;
    for(id key in preferences){if(![key isKindOfClass:NSString.class]||![key hasSuffix:@"_ota_firmware_auto_update"])continue;found++;
        id v=preferences[key];if([v isKindOfClass:NSString.class]){if(![v isEqual:@"false"])return NO;}
        else if(![v isKindOfClass:NSNumber.class]||CFGetTypeID((__bridge CFTypeRef)v)!=CFBooleanGetTypeID()||[v boolValue])return NO;
    }
    return found>0;
}
static TIOOTAFlashGate *Current(void){@synchronized(NSProcessInfo.processInfo){if(!Gate)Gate=[TIOOTAFlashGate new];return Gate;}}
BOOL TIOOTAFlashProtected(void){
#if TARGET_OS_IPHONE && TIO_DISPLAY_FLASH
    if(!TIOOTA107HostMatches(NSBundle.mainBundle.infoDictionary))return NO;
#endif
    return TIOOTAFlashBuild()&&[TIOOTAGuardStatus()[@"transportHookReady"] boolValue];
}
void TIOOTAFlashDisableAutoUpdateIfRequested(void){
    if(!TIOOTAFlashProtected()||![NSProcessInfo.processInfo.environment[@"TIO_OTA_DISABLE_AUTO_ONCE"] isEqual:@"DISABLE_FOR_PRIVATE_TMG2"])return;
    NSUserDefaults *p=NSUserDefaults.standardUserDefaults;NSMutableArray *keys=[NSMutableArray new];
    for(NSString *key in p.dictionaryRepresentation)if([key hasSuffix:@"_ota_firmware_auto_update"])[keys addObject:key];
    // Do not guess between accounts. The private pre-install preference backup
    // preserves the old value; no account identifiers are written to reports.
    if(keys.count!=1)return;id v=[p objectForKey:keys[0]];
    if([v isKindOfClass:NSString.class]&&([v isEqual:@"true"]||[v isEqual:@"false"]))[p setObject:@"false" forKey:keys[0]];
    else if([v isKindOfClass:NSNumber.class]&&CFGetTypeID((__bridge CFTypeRef)v)==CFBooleanGetTypeID())[p setBool:NO forKey:keys[0]];
    [p synchronize];
}
static id Get(id obj,NSString *key){@try{return [obj valueForKey:key];}@catch(NSException *e){return nil;}}
BOOL TIOOTAFlashBlockCall(id call){
    if(!TIOOTAFlashBuild())return NO;
    NSString *method=Get(call,@"method");NSDictionary *args=Get(call,@"arguments");
    if(![method isKindOfClass:NSString.class])return YES;
    if([method.lowercaseString containsString:@"disconnect"]||[method.lowercaseString containsString:@"unbind"]||[method.lowercaseString containsString:@"unpair"]){@synchronized(NSProcessInfo.processInfo){ObservedDevice=nil;ObservedAt=0;}[Current() cancel];}
    if([method isEqual:@"rayneonet_sendMessage"]){
        NSUInteger business=0;if(![args isKindOfClass:NSDictionary.class]||!Number(args[@"businessId"],&business))return YES;
        if(business!=9)return NO;
        id data=args[@"payload"];if(![data isKindOfClass:NSData.class])data=Get(data,@"data");
        if(!TIOOTA107HostMatches(NSBundle.mainBundle.infoDictionary))
            return !TIOOTAIsEmptyVersionQuery(data);
#if TIO_DISPLAY_PHONE && !TIO_DISPLAY_FLASH
        // Phone-only test package: no OTA mutation, even if an old gate was armed.
        return !TIOPhoneOTAReadAllowed(data);
#endif
        return ![Current() allows:data device:args[@"deviceId"] uptime:NSProcessInfo.processInfo.systemUptime];
    }
    // The pinned files path uses business 9 frames, never sendFile / ZIP mode.
    BOOL scopedImage=NO;
#if TIO_IMAGE_RX_LAB
    scopedImage=TIOImageUploadIsScopedCall(method,args);
#if TIO_DISPLAY_PHONE
    scopedImage=scopedImage||TDPPhoneIsScopedCall(method,args);
#if TIO_NATIVE_NAV
    scopedImage=scopedImage||TDGlassesIsScopedCall(method,args)||TAPIsScopedCall(method,args)||TNVIsScopedCall(method,args)||TMIsScopedCall(method,args)||TWIsScopedCall(method,args)||TFIsScopedCall(method,args)||CEIsScopedCall(method,args)||DRIsScopedCall(method,args)||TDIsScopedCall(method,args);
#endif
#endif
#endif
    return TIOOTAFileCallBlocked(method,scopedImage,[[Current() status][@"stage"] unsignedIntegerValue]);
}
void TIOOTAFlashObserveEvent(NSDictionary *event){
    [Current() observeEvent:event uptime:NSProcessInfo.processInfo.systemUptime];
    if(!TIOOTAFlashBuild()||![event[@"eventType"] isEqual:@"messageReceived"])return;
    NSDictionary *m=event[@"message"];NSUInteger business=0;if(![m isKindOfClass:NSDictionary.class]||!Number(m[@"businessId"],&business)||business!=9)return;
    NSDictionary *f=TIOOTAFrame(m[@"payload"]);id j=f[@"json"];
    NSUInteger type=[f[@"type"] unsignedIntegerValue];NSTimeInterval now=NSProcessInfo.processInfo.systemUptime;
    @synchronized(NSProcessInfo.processInfo){
        if((type==1||type==2||type==11)&&[m[@"deviceId"] isEqual:PreflightDevice]&&PreflightRequested[type]>0&&now>=PreflightRequested[type]&&now-PreflightRequested[type]<=15){
            PreflightRequested[type]=0;PreflightGood[type]=NO;PreflightAt[type]=now;
            if([j isKindOfClass:NSDictionary.class]){
                if(type==1)PreflightGood[type]=[@[@"Strix OS 1.0.4.12",@"Strix OS 1.0.5.5"] containsObject:j[@"OsVersion"]]&&[j[@"OtaValidationConfirmed"] isEqual:@YES]&&[j[@"OtaValidationState"] isEqual:@"idle"]&&[j[@"PackageTypes"] isKindOfClass:NSArray.class]&&[j[@"PackageTypes"] containsObject:@"files"];
                else if(type==2){NSUInteger level=0;PreflightGood[type]=Number(j[@"BatteryLevel"],&level)&&level>=50&&level<=100;}
                else PreflightGood[type]=[j[@"Result"] isEqual:@YES];
            }
        }
    }
    if([f[@"type"] isEqual:@1]){@synchronized(NSProcessInfo.processInfo){ObservedDevice=nil;ObservedAt=0;ObservedVersion=nil;ObservedConfirmed=NO;}}
    if([f[@"type"] isEqual:@1]&&[j isKindOfClass:NSDictionary.class]&&[@[@"Strix OS 1.0.4.12",@"Strix OS 1.0.5.5"] containsObject:j[@"OsVersion"]]&&[j[@"OtaValidationConfirmed"] isEqual:@YES]&&[m[@"deviceId"] isKindOfClass:NSString.class]){
        @synchronized(NSProcessInfo.processInfo){
            ObservedDevice=[m[@"deviceId"] copy];
            ObservedAt=NSProcessInfo.processInfo.systemUptime;
            ObservedVersion=[j[@"OsVersion"] copy];
            ObservedConfirmed=[j[@"OtaValidationConfirmed"] boolValue];
        }
    }
}
BOOL TIOOTAFlashAuthorize(NSError **error){
    if(!TIOOTA107HostMatches(NSBundle.mainBundle.infoDictionary)){Error(error,@"1.0.7 OTA 专用宿主或路由标记不匹配，未授权。");return NO;}
    NSDictionary *feed=TIOExperimentalOTAFeedStatus();
    if(![feed[@"bootstrapEligible"] boolValue]||![feed[@"running"] boolValue]||![feed[@"healthPassed"] boolValue]){Error(error,@"未确认本机下载来源与健康检查，未授权。");return NO;}
#if TARGET_OS_IPHONE && TIO_DISPLAY_PHONE
    if(TAPPhoneBridge.shared.busy||TAPPhoneBridge.shared.needsReadback){Error(error,@"应用文件仍在传输或结果未知，先查询完成；未开放升级。");return NO;}
#endif
    if(!TIOOTAFlashProtected()){Error(error,@"没有试刷构建与发送保护；未开放。");return NO;}
#if TARGET_OS_IPHONE && TIO_DISPLAY_FLASH
    if(!TDPPhonePauseForOTA()){Error(error,@"显示文件仍在传输或结果未知，请等待终态；未开放升级。若任务无法收尾，先退出显示页并重启 App 重新检查。");return NO;}
#endif
    if(!TIOOTAAutoUpdateDisabled(NSUserDefaults.standardUserDefaults.dictionaryRepresentation)){Error(error,@"请先在官方眼镜软件版本页关闭自动更新；未读到明确关闭状态，不授予试刷权限。");return NO;}
    NSString *device;NSTimeInterval at;@synchronized(NSProcessInfo.processInfo){device=ObservedDevice;at=ObservedAt;}
    NSTimeInterval now=NSProcessInfo.processInfo.systemUptime;
    if(!device.length||![device isEqual:TIOProtocolDevice()]||now-at<0||now-at>120){Error(error,@"请先从官方固件页重新检查版本；需要两分钟内的 1.0.4.12 或 1.0.5.5 有效回读。");return NO;}
    NSURL *dir=[NSURL fileURLWithPath:[NSHomeDirectory() stringByAppendingPathComponent:@"Library/Application Support/ota/Strix_OS_1.0.5.5"] isDirectory:YES];
    return [Current() prepareDirectory:dir device:device uptime:now error:error];
}
BOOL TIOOTAFlashCancel(void){return [Current() cancel];}
BOOL TIOOTAFlashFreshBoot(NSTimeInterval now){return [Current() freshBoot:now];}
BOOL TIOOTAFlashRecoveryAllowed(NSString *device,NSTimeInterval now){return [Current() recoveryAllowed:device uptime:now];}
BOOL TIOOTAFlashRelease(NSString *device,NSTimeInterval now,NSError **error){
    if(![device isEqual:TIOProtocolDevice()]){Error(error,@"当前设备与回查设备不一致");return NO;}
    return [Current() releaseDevice:device uptime:now error:error];
}
BOOL TIOOTASendProbe(NSUInteger type, NSString *device){
    if(type!=1&&type!=2&&type!=11)return NO;
    if(![device isKindOfClass:NSString.class]||!device.length)return NO;
    id plugin=TIOProtocolPlugin();
    if(!plugin||![device isEqual:TIOProtocolDevice()])return NO;
    if([Current() isStarted]&&![Current() beginReadback:device uptime:NSProcessInfo.processInfo.systemUptime])return NO;
    Class typedCls=NSClassFromString(@"FlutterStandardTypedData");
    Class callCls=NSClassFromString(@"FlutterMethodCall");
    SEL wrapSel=NSSelectorFromString(@"typedDataWithBytes:");
    SEL makeSel=NSSelectorFromString(@"methodCallWithMethodName:arguments:");
    SEL handleSel=NSSelectorFromString(@"handleMethodCall:result:");
    if(![typedCls respondsToSelector:wrapSel]||![callCls respondsToSelector:makeSel]||![plugin respondsToSelector:handleSel])return NO;
    uint8_t raw[4]={8, 1, 16, (uint8_t)type};
    NSData *data=[NSData dataWithBytes:raw length:4];
    id wrapped=((id(*)(id,SEL,id))objc_msgSend)(typedCls,wrapSel,data);
    NSDictionary *args=@{@"businessId":@9,@"deviceId":device,@"payload":wrapped};
    id call=((id(*)(id,SEL,id,id))objc_msgSend)(callCls,makeSel,@"rayneonet_sendMessage",args);
    if(![Current() isStarted]){@synchronized(NSProcessInfo.processInfo){
        if(![PreflightDevice isEqual:device]){PreflightDevice=[device copy];memset(PreflightRequested,0,sizeof(PreflightRequested));memset(PreflightAt,0,sizeof(PreflightAt));memset(PreflightGood,0,sizeof(PreflightGood));}
        PreflightRequested[type]=NSProcessInfo.processInfo.systemUptime;PreflightGood[type]=NO;
    }}
    @try{
        ((void(*)(id,SEL,id,id))objc_msgSend)(plugin,handleSel,call,[^(id r){} copy]);
        return YES;
    }@catch(NSException *e){
        return NO;
    }
}
NSDictionary *TIOOTAFlashStatus(void){
    NSMutableDictionary *s=[[Current() status] mutableCopy];
    s[@"flashBuild"]=@(TIOOTAFlashBuild());
    s[@"automaticUpdateDisabled"]=@(TIOOTAAutoUpdateDisabled(NSUserDefaults.standardUserDefaults.dictionaryRepresentation));
    @synchronized(NSProcessInfo.processInfo){
        NSTimeInterval now=NSProcessInfo.processInfo.systemUptime;
        s[@"recentTargetVersionRead"]=@(ObservedDevice.length&&[ObservedDevice isEqual:TIOProtocolDevice()]&&now-ObservedAt>=0&&now-ObservedAt<=120);
        BOOL ready=PreflightDevice.length&&[PreflightDevice isEqual:TIOProtocolDevice()];
        for(NSNumber *n in @[@1,@2,@11]){NSUInteger t=n.unsignedIntegerValue;ready=ready&&PreflightGood[t]&&now>=PreflightAt[t]&&now-PreflightAt[t]<=120;}
        s[@"autoPreflightReady"]=@(ready);
        s[@"observedDevice"]=ObservedDevice?:@"";
        s[@"observedVersion"]=ObservedVersion?:@"";
        s[@"observedConfirmed"]=@(ObservedConfirmed);
        s[@"observedUptime"]=@(ObservedAt);
        s[@"freshBoot"]=@(TIOOTAFlashFreshBoot(now));
    }
    return s;
}
