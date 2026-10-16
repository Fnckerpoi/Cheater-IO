#import "TIOStorageIdentity.h"
#import "NavigationUI.h"
#import "NavigationSession.h"
#import "NavigationRouteSession.h"
#import "NavigationCore.h"
#import "NavigationModes.h"
#import "NavigationTransport.h"
#import "ManualHUD.h"
#import "NavigationTeleHUD.h"
#import "NavigationSubtitleHUD.h"
#import "SubtitleHUD.h"
#import "NewsTeleprompter.h"
#import "NavigationPlaces.h"
#import "NavigationBackground.h"
#import <CoreLocation/CoreLocation.h>
#import <Security/Security.h>
#include <math.h>
#if TIO_AMAP_ENABLED
#import <AMapNaviKit/AMapNaviKit.h>
#import <AMapNaviKit/MAMapKit.h>
#import <AMapFoundationKit/AMapFoundationKit.h>
// A packaging capability marker, not evidence of online authentication.
__attribute__((used)) const char TIOAMapBuildPolicy[]="turboio-amap-navi-11.3.100-search-9.8.1-v1";
static BOOL NavSDKInitialized=NO;
#endif

static NSString *const NavConsent=@"io.turboio.navigation.privacy.v1";
static NSDictionary *KeyQuery(void){return @{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword,(__bridge id)kSecAttrService:TIOStorageNavigationDomain(),(__bridge id)kSecAttrAccount:@"amap-ios"};}
static NSString *ReadNavKey(void){NSMutableDictionary *q=[KeyQuery() mutableCopy];q[(__bridge id)kSecReturnData]=@YES;CFTypeRef v=NULL;if(SecItemCopyMatching((__bridge CFDictionaryRef)q,&v)!=errSecSuccess)return nil;return [[NSString alloc]initWithData:CFBridgingRelease(v) encoding:NSUTF8StringEncoding];}
static BOOL ValidKey(NSString *s){return [s isKindOfClass:NSString.class]&&s.length==32&&[s rangeOfCharacterFromSet:[NSCharacterSet characterSetWithCharactersInString:@"0123456789abcdefABCDEF"].invertedSet].location==NSNotFound;}
static BOOL WriteNavKey(NSString *s){if(!ValidKey(s))return NO;NSDictionary *a=@{(__bridge id)kSecValueData:[s dataUsingEncoding:NSUTF8StringEncoding],(__bridge id)kSecAttrAccessible:(__bridge id)kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly};OSStatus r=SecItemUpdate((__bridge CFDictionaryRef)KeyQuery(),(__bridge CFDictionaryRef)a);if(r==errSecItemNotFound){NSMutableDictionary *q=[KeyQuery() mutableCopy];[q addEntriesFromDictionary:a];r=SecItemAdd((__bridge CFDictionaryRef)q,NULL);}return r==errSecSuccess;}

@interface TIONavigationPanel:UIViewController<CLLocationManagerDelegate
#if TIO_AMAP_ENABLED
,MAMapViewDelegate,AMapNaviWalkManagerDelegate,AMapNaviWalkDataRepresentable,AMapNaviRideManagerDelegate,AMapNaviRideDataRepresentable,AMapNaviDriveManagerDelegate,AMapNaviDriveDataRepresentable
#endif
>
@property UILabel *statusLabel,*hudLabel,*destinationLabel;
@property UIStackView *stack;
@property UIScrollView *scroll;
@property NSTimer *timer;
@property CLLocationManager *permission;
@property BOOL ownsNavigation;
@property NSDictionary *display;
@property TIONavTeleHUD *teleHUD;
@property TIONavSubtitleHUD *subtitleHUD;
@property NSDictionary *lastSubtitleDiagnostic;
@property NSTimeInterval lastSubtitleDiagnosticWrite;
@property NSDictionary *lastTeleDiagnostic;
@property NSString *note;
@property BOOL initialized,active,planning,simulated,fixture,hasDestination,gpsWeak,staleShown,rerouting;
@property NSUInteger generation,fixtureStep;
@property NSTimeInterval lastInfo,lastSDKInfo;
@property NSUInteger infoCallbacks;
@property NSInteger lastGPSStrength;
@property CLLocationCoordinate2D destination;
@property UIView *mapHost;
@property NSLayoutConstraint *mapHeight;
@property UIStackView *routeCard,*lensCard;
@property UILabel *routeSummary,*briefLabel,*lensStatus,*mapHint;
@property UILabel *originHint;
@property UILabel *startLabel;
@property UIImageView *mapCrosshair;
@property UIButton *centerButton,*zoomInButton,*zoomOutButton;
@property UISegmentedControl *mapPickRole;
@property NSString *pendingMapAction,*simulationStartName;
@property BOOL hasSimulationStart;
@property UIButton *planButton,*beginButton,*lensButton,*stopButton,*searchButton;
@property UISegmentedControl *travelMode;
@property UISegmentedControl *transportMode;
@property NSInteger selectedTransport,sessionTransport;
@property BOOL routeReady,locating,awaitingMapLocationAuthorization,autoLocateAttempted,autoPlanPending,autoPlanWaitingAuthorization;
@property NSUInteger autoPlanEpoch;
@property BOOL navigationStarted,backgroundLocationEnabled;
@property BOOL lensStartApproved,autoDisplayAttempted,autoMapEntryAttempted,standaloneNavigation;
@property NSString *routeToken;
@property UIBackgroundTaskIdentifier navigationBackgroundTask;
@property NSUInteger backgroundEpoch;
@property NSString *destinationName;
@property CLLocationCoordinate2D simulationStart;
@property NSUInteger locationGeneration;
#if TIO_AMAP_ENABLED
@property MAMapView *map;
@property id<TIONavigationManager> manager;
@property Class retiringManagerClass;
@property(weak) id<TIONavigationManager> retiringManager;
@property MAPointAnnotation *pin;
@property MAPointAnnotation *startPin;
@property MAPolyline *routeLine;
#endif
@end
static __weak TIONavigationPanel *LiveRoutePanel;
@implementation TIONavigationPanel
- (UIButton *)button:(NSString *)title action:(SEL)action identifier:(NSString *)identifier{UIButton *b=[UIButton buttonWithType:UIButtonTypeSystem];b.configuration=[UIButtonConfiguration tintedButtonConfiguration];[b setTitle:title forState:UIControlStateNormal];b.accessibilityIdentifier=identifier;[b addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];[self.stack addArrangedSubview:b];return b;}
- (void)viewDidLoad{
    [super viewDidLoad];self.title=@"多模式导航 · 后台连接";self.view.backgroundColor=UIColor.systemGroupedBackgroundColor;self.note=@"字幕直传：点击开始导航时确认眼镜空闲，SDK开始成功后自动显示。真实路线字幕随导航持续，满足定位权限时尝试后台原生推送；正常页面只提供实时出行；诊断测试保持独立保护。开始导航确认空闲后自动显示，不抢占。请勿边驾驶边调试。";self.teleHUD=[TIONavTeleHUD new];self.subtitleHUD=[TIONavSubtitleHUD new];
#ifndef TIO_UI_PREVIEW
#endif
    self.scroll=[UIScrollView new];self.scroll.translatesAutoresizingMaskIntoConstraints=NO;[self.view addSubview:self.scroll];
    self.stack=[UIStackView new];self.stack.axis=UILayoutConstraintAxisVertical;self.stack.spacing=12;self.stack.translatesAutoresizingMaskIntoConstraints=NO;[self.scroll addSubview:self.stack];
    [NSLayoutConstraint activateConstraints:@[[self.scroll.leadingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.leadingAnchor],[self.scroll.trailingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.trailingAnchor],[self.scroll.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],[self.scroll.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],[self.stack.leadingAnchor constraintEqualToAnchor:self.scroll.contentLayoutGuide.leadingAnchor constant:16],[self.stack.trailingAnchor constraintEqualToAnchor:self.scroll.contentLayoutGuide.trailingAnchor constant:-16],[self.stack.topAnchor constraintEqualToAnchor:self.scroll.contentLayoutGuide.topAnchor constant:16],[self.stack.bottomAnchor constraintEqualToAnchor:self.scroll.contentLayoutGuide.bottomAnchor constant:-24],[self.stack.widthAnchor constraintEqualToAnchor:self.scroll.frameLayoutGuide.widthAnchor constant:-32]]];
    self.statusLabel=[UILabel new]; // Diagnostic text only; not part of the primary layout.
    [self buildWorkspace];
    self.permission=[CLLocationManager new];self.permission.delegate=self;self.navigationBackgroundTask=UIBackgroundTaskInvalid;self.lastGPSStrength=-1;
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(refresh) name:@"TIONavigationChanged" object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(researchClosed) name:@"TIOResearchClosed" object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(background) name:UIApplicationDidEnterBackgroundNotification object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(navigationForeground) name:UIApplicationDidBecomeActiveNotification object:nil];
    self.display=TIONavDisplay(@"stopped",0,@"",-1,-1,-1,NO);[self refresh];
}
- (void)viewWillAppear:(BOOL)animated{[super viewWillAppear:animated];if(!self.timer){__weak typeof(self) weak=self;self.timer=[NSTimer scheduledTimerWithTimeInterval:1 repeats:YES block:^(NSTimer *t){[weak tick];}];} [self refresh];}
- (void)viewDidAppear:(BOOL)animated{[super viewDidAppear:animated];[self openMapOnEntry:0];[self resumeAutoPlan];} // Refusing consent must not reopen the same prompt on dismissal.
#include "NavigationMapEntry.inc"
#include "NavigationAutoPlan.inc"
- (void)viewDidDisappear:(BOOL)animated{[super viewDidDisappear:animated];self.pendingMapAction=nil;[self cancelAutoPlan];
#if TIO_AMAP_ENABLED
    if(!self.navigationStarted)[self cancelMapLocation];
#endif
    if([TIONavigationRouteSession.shared owns:self]){[self.timer invalidate];self.timer=nil;return;}if(UIApplication.sharedApplication.applicationState!=UIApplicationStateActive&&!self.isMovingFromParentViewController&&!self.isBeingDismissed&&!self.navigationController.isBeingDismissed)return;[self stopUser];[self.timer invalidate];self.timer=nil;}
- (void)dealloc{if(self.ownsNavigation)TIONavigationRelease(@"amap");if(self.navigationBackgroundTask!=UIBackgroundTaskInvalid)[UIApplication.sharedApplication endBackgroundTask:self.navigationBackgroundTask];[self.subtitleHUD stop:@"导航页面已销毁"];[self.teleHUD stop:@"导航页面已销毁"];[self.timer invalidate];[NSNotificationCenter.defaultCenter removeObserver:self];}
- (void)refresh{if(!NSThread.isMainThread){dispatch_async(dispatch_get_main_queue(),^{[self refresh];});return;}NSDictionary *s=TIONavTransportStatus(),*tele=self.teleHUD.status;self.statusLabel.text=[NSString stringWithFormat:@"%@\n常亮：%@ · 已提交%@帧\n通知：%@\n连接／卡片：%@\nKey：%@ · SDK：%@",self.note?:@"",tele[@"note"],tele[@"frames"],s[@"noticeNote"],s[@"note"],ReadNavKey().length?@"已配置（有效性待实际算路）":@"未配置",
#if TIO_AMAP_ENABLED
    @"11.3.100"
#else
    @"此构建未链接（仅离线夹具）"
#endif
    ];NSDictionary *sub=self.subtitleHUD.status;self.statusLabel.text=[NSString stringWithFormat:@"字幕：%@ · 已提交%@帧\n%@\n%@",sub[@"note"],sub[@"frames"],TIOSubtitleNavigationStatus()[@"note"],self.statusLabel.text];
    if(![sub isEqual:self.lastSubtitleDiagnostic]&&(![sub[@"enabled"] boolValue]||![sub[@"routeScoped"] boolValue]||NSProcessInfo.processInfo.systemUptime-self.lastSubtitleDiagnosticWrite>=5)){self.lastSubtitleDiagnosticWrite=NSProcessInfo.processInfo.systemUptime;self.lastSubtitleDiagnostic=sub;NSString *dir=[NSHomeDirectory() stringByAppendingPathComponent:@"Documents/TurboIOResearch/subtitle-hud"];[NSFileManager.defaultManager createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:@{NSFileProtectionKey:NSFileProtectionCompleteUntilFirstUserAuthentication} error:nil];NSMutableDictionary *report=[sub mutableCopy];report[@"time"]=@(NSDate.date.timeIntervalSince1970);[[NSJSONSerialization dataWithJSONObject:report options:NSJSONWritingPrettyPrinted error:nil] writeToFile:[dir stringByAppendingPathComponent:@"navigation.json"] options:NSDataWritingAtomic error:nil];}
    self.hudLabel.text=[NSString stringWithFormat:@"%@\n\n%@  %@\n%@\n%@",self.display[@"mode"]?:@"",self.display[@"turn"]?:@"",self.display[@"distance"]?:@"",self.display[@"road"]?:@"",self.display[@"summary"]?:@""];[self refreshWorkspace];
    if(![tele isEqual:self.lastTeleDiagnostic]){self.lastTeleDiagnostic=tele;NSString *dir=[NSHomeDirectory() stringByAppendingPathComponent:@"Library/Application Support/TurboIOPrivateAddon"];[NSFileManager.defaultManager createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:@{NSFilePosixPermissions:@0700} error:nil];NSMutableDictionary *d=[tele mutableCopy];d[@"time"]=@([NSDate.date timeIntervalSince1970]);NSString *p=[dir stringByAppendingPathComponent:@"navigation-tele-diagnostic.json"];[[NSJSONSerialization dataWithJSONObject:d options:0 error:nil] writeToFile:p options:NSDataWritingAtomic error:nil];[NSFileManager.defaultManager setAttributes:@{NSFilePosixPermissions:@0600} ofItemAtPath:p error:nil];}}
- (void)setFrame:(NSDictionary *)frame{self.display=frame;TIONavOfferDisplay(frame);[self.teleHUD offer:frame at:NSProcessInfo.processInfo.systemUptime];[self.subtitleHUD offer:frame at:NSProcessInfo.processInfo.systemUptime];if(self.navigationStarted)[self pumpRoute];[self refresh];}
- (void)tick{TIONavPump();[self.subtitleHUD pumpAt:NSProcessInfo.processInfo.systemUptime];[self.teleHUD pumpAt:NSProcessInfo.processInfo.systemUptime];[self refresh];if(self.fixture&&self.active){self.fixtureStep++;NSArray *icons=@[@9,@2,@3,@29,@15];NSUInteger i=MIN(self.fixtureStep/4,4);[self setFrame:TIONavDisplay(i==4?@"arrived":@"navigating",[icons[i] integerValue],@"模拟测试道路",MAX(0,160-(NSInteger)self.fixtureStep*10),850,720,YES)];if(i==4){self.fixture=NO;self.active=NO;self.note=@"离线夹具结束；手动停止以清理卡片";[self refresh];}}
    if(self.active&&self.navigationStarted&&self.lastInfo>0&&!self.routeReady&&!self.fixture&&!self.planning&&!self.staleShown&&NSProcessInfo.processInfo.systemUptime-self.lastInfo>15){self.staleShown=YES;[self setFrame:TIONavDisplay(@"stale",0,@"",-1,-1,-1,self.simulated)];}}
- (void)alert:(NSString *)title message:(NSString *)message{UIAlertController *a=[UIAlertController alertControllerWithTitle:title message:message preferredStyle:UIAlertControllerStyleAlert];[a addAction:[UIAlertAction actionWithTitle:@"知道了" style:UIAlertActionStyleCancel handler:nil]];[self presentViewController:a animated:YES completion:nil];}
- (BOOL)sdkKeyLocked{
#if TIO_AMAP_ENABLED
    return NavSDKInitialized; // SDK state is process-wide, not page-scoped.
#else
    return NO;
#endif
}
- (void)configureKey{if([self sdkKeyLocked]){[self alert:@"先重启 App" message:@"SDK 已初始化。本轮不热替换 Key，避免影响已有导航实例；请重启后配置。"] ;return;}UIAlertController *a=[UIAlertController alertControllerWithTitle:@"高德 iOS Key" message:[NSString stringWithFormat:@"创建 iOS 平台 Key，绑定实际 Bundle ID：\n%@\n仅保存于本机钥匙串，不回显旧值。",NSBundle.mainBundle.bundleIdentifier?:@"未知（请先核对应用标识）"] preferredStyle:UIAlertControllerStyleAlert];[a addTextFieldWithConfigurationHandler:^(UITextField *f){f.placeholder=@"32 位 iOS Key";f.secureTextEntry=YES;f.autocorrectionType=UITextAutocorrectionTypeNo;f.autocapitalizationType=UITextAutocapitalizationTypeNone;}];[a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];[a addAction:[UIAlertAction actionWithTitle:@"保存" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){self.note=WriteNavKey(a.textFields.firstObject.text)?@"Key 已保存，尚未进行 SDK 鉴权":@"保存失败：检查格式与钥匙串权限";[self refresh];self.autoMapEntryAttempted=NO;[self openMapOnEntry:0];}]];[self presentViewController:a animated:YES completion:nil];}
- (void)consent{
#if TIO_AMAP_ENABLED
    if(!ValidKey(ReadNavKey())){[self alert:@"尚未配置 Key" message:@"请先填写高德 iOS Key。"] ;return;}
    if([NSUserDefaults.standardUserDefaults boolForKey:NavConsent]){[self openMap];return;}
    UIAlertController *a=[UIAlertController alertControllerWithTitle:@"启用高德地图与导航？" message:@"提供方：高德软件有限公司。地图、定位与导航会按高德隐私政策处理设备、网络、位置及起终点信息，用于地图、算路与导航。本扩展不保存轨迹，不发送位置给大模型。真实导航需定位授权；点击开始实时导航后会持续使用后台定位，直到主动结束或到达；真实路线离开导航页后仍继续，可返回本页结束。模拟只使用系统短时后台额度，不启动真实定位。\n请先阅读高德 SDK 隐私政策，再决定是否同意。" preferredStyle:UIAlertControllerStyleAlert];
    [a addAction:[UIAlertAction actionWithTitle:@"查看高德隐私政策" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){[UIApplication.sharedApplication openURL:[NSURL URLWithString:@"https://lbs.amap.com/pages/privacy/"] options:@{} completionHandler:nil];}]];
    [a addAction:[UIAlertAction actionWithTitle:@"不同意" style:UIAlertActionStyleCancel handler:^(UIAlertAction *x){self.pendingMapAction=nil;}]];
    [a addAction:[UIAlertAction actionWithTitle:@"同意并开启地图" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){[NSUserDefaults.standardUserDefaults setBool:YES forKey:NavConsent];[self openMap];}]];[self presentViewController:a animated:YES completion:nil];
#else
    [self alert:@"离线预览构建" message:@"此构建不包含高德 SDK。可使用离线显示夹具；不会伪装为真实导航。"];
#endif
}
- (void)refreshConnection{TIONavRefreshConnection();[self refresh];}
- (void)subtitleCheck{[self stopUser];[self.navigationController pushViewController:TIOSubtitleHUDController() animated:YES];}
#include "NavigationSubtitleEntry.inc"
#include "NavigationAutoDisplay.inc"
- (void)closeStandaloneNavigation{[self.navigationController dismissViewControllerAnimated:YES completion:^{[NSNotificationCenter.defaultCenter postNotificationName:@"TIOResearchClosed" object:nil];}];}
- (void)researchClosed{if(![TIONavigationRouteSession.shared owns:self])[self stopUser];}
- (void)pumpRoute{
    if(!self.navigationStarted)return;
    if(!self.simulated&&![self navigationLocationAuthorized]){[self stopUser];self.note=@"定位权限撤销，路线和字幕已停止";return;}
    [self.subtitleHUD pumpAt:NSProcessInfo.processInfo.systemUptime];
}
- (BOOL)retainLiveRoute{
    if(self.simulated)return YES;__weak typeof(self) weak=self;
    self.routeToken=[TIONavigationRouteSession.shared beginWithOwner:self backgroundAllowed:^BOOL{TIONavigationPanel *s=weak;return s&&s.navigationStarted&&!s.simulated&&s.backgroundLocationEnabled&&[s navigationLocationAuthorized];} pump:^{[weak pumpRoute];} stop:^(NSString *reason){[weak stopUser];weak.note=reason;[weak refresh];}];
    if(self.routeToken){LiveRoutePanel=self;return YES;}return NO;
}
- (void)enableSubtitleHUD{
    if(![self canStartSubtitle]){[self alert:@"先启动高德导航并结束其他显示" message:@"在手机前台开始模拟或实时导航，首次指引尚未到达或定位弱时，可显示中性等待文字；不显示旧转向/旧距离。退出提词、导航卡与自动通知，不支持离线夹具。"] ;return;}
    if(![TIOSubtitleNavigationStatus()[@"available"] boolValue]){[self alert:@"字幕通道未就绪" message:@"先确认眼镜已连接。当前支持版本使用固化协议或已保存配置；若仍不可用，再在诊断页学习一次新配置。"] ;return;}
    NSUInteger generation=self.generation;UIAlertController *a=[UIAlertController alertControllerWithTitle:@"眼镜当前已回首页且无任务？" message:@"确认录音、智记、提词、字幕、语音对话均已结束后开启。使用字幕纯文字通道，不发送录音启动；发现字幕音频消息就停止。真实路线显示持续至结束，满足定位权限时尝试后台推送；模拟保留前台4分钟/80帧保护。定位弱、偏航重算或断流改中性提示，有效指引恢复后同会话续显；会话已关闭或被占用不自动抢占；实际道路以路况和手机为准，勿边驾驶边操作。" preferredStyle:UIAlertControllerStyleAlert];
    [a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [a addAction:[UIAlertAction actionWithTitle:@"已确认，开启字幕导航" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){if(generation!=self.generation||![self canStartSubtitle]||!TIOSubtitleConfirmIdle()){self.note=@"未开启：路线或会话状态变化，请先退出已有字幕";[self refresh];return;}if(self.simulated)[self.subtitleHUD startWithFrame:[self subtitleFrame] at:NSProcessInfo.processInfo.systemUptime];else [self.subtitleHUD startRouteWithFrame:[self subtitleFrame] token:self.routeToken at:NSProcessInfo.processInfo.systemUptime];[self refresh];[self.scroll setContentOffset:CGPointZero animated:YES];}]];[self presentViewController:a animated:YES completion:nil];
}
- (void)manualHUD{[self stopUser];[self.navigationController pushViewController:TIOManualHUDController() animated:YES];}
- (BOOL)subtitleBlocksOtherDisplay{if([TIOSubtitleNavigationStatus()[@"phase"] isEqual:@"idle"])return NO;[self alert:@"请先退出字幕会话" message:@"停止字幕后，在字幕显示检查页确认镜片已回首页，再切换其他显示通道。不会自动抢占。"] ;return YES;}
- (void)enableTeleHUD{if([self subtitleBlocksOtherDisplay])return;if(!self.active||!self.simulated||self.fixture||self.planning||NSProcessInfo.processInfo.systemUptime-self.lastInfo>15){[self alert:@"先启动高德模拟导航" message:@"本轮只测真实高德回调→眼镜手动提词，不支持实际道路导航或离线夹具。收到手机模拟转向后再开启。"] ;return;}TIONavEnableNotices(NO);TIONavEnableDisplay(NO);if([self.teleHUD enable]){[self.teleHUD offer:self.display at:NSProcessInfo.processInfo.systemUptime];[self.teleHUD pumpAt:NSProcessInfo.processInfo.systemUptime];}[self refresh];[self.scroll setContentOffset:CGPointZero animated:YES];}
- (void)testNotice{if([self subtitleBlocksOtherDisplay])return;[self.teleHUD stop:@"切换到通知测试"];TIONavTestNotice();[self refresh];[self.scroll setContentOffset:CGPointZero animated:YES];}
- (void)enableNotices{if([self subtitleBlocksOtherDisplay])return;[self.teleHUD stop:@"切换到自动通知"];TIONavEnableNotices(YES);TIONavOfferDisplay(self.display);TIONavPump();[self refresh];[self.scroll setContentOffset:CGPointZero animated:YES];}
- (void)enableGlasses{if([self subtitleBlocksOtherDisplay])return;UIAlertController *a=[UIAlertController alertControllerWithTitle:@"新增／更新专用导航卡？" message:@"只修改本扩展拥有的导航卡，不覆盖天气与待办。整卡连续更新仍需镜片验收。请先用模拟导航测试。" preferredStyle:UIAlertControllerStyleAlert];[a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];[a addAction:[UIAlertAction actionWithTitle:@"启用" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){if([self subtitleBlocksOtherDisplay])return;[self.teleHUD stop:@"切换到仪表盘导航卡"];TIONavEnableDisplay(YES);TIONavOfferDisplay(self.display);TIONavPump();[self refresh];}]];[self presentViewController:a animated:YES completion:nil];}
- (void)halt{
    [self cancelAutoPlan];
    [TIONavigationRouteSession.shared endOwner:self];if(LiveRoutePanel==self)LiveRoutePanel=nil;self.routeToken=nil;self.lensStartApproved=NO;self.autoDisplayAttempted=NO;
    if(self.ownsNavigation){TIONavigationRelease(@"amap");self.ownsNavigation=NO;}
    self.navigationStarted=NO;self.backgroundLocationEnabled=NO;[self endNavigationBackgroundTask];
    [self.subtitleHUD stop:@"导航停止／切换路线"];
    [self.teleHUD stop:@"导航停止／切换路线"];
    self.generation++;self.locationGeneration++;self.locating=NO;self.awaitingMapLocationAuthorization=NO;[self.permission stopUpdatingLocation];self.routeReady=NO;self.active=NO;self.planning=NO;self.fixture=NO;self.rerouting=NO;
#if TIO_AMAP_ENABLED
    if(self.manager){BOOL owned=self.manager.delegate==self;[self.manager removeDataRepresentative:self];if(owned){self.manager.allowsBackgroundLocationUpdates=NO;self.manager.pausesLocationUpdatesAutomatically=YES;self.manager.delegate=nil;[self.manager stopNavi];self.retiringManagerClass=TIONavigationManagerClass(self.sessionTransport);self.retiringManager=self.manager;}self.manager=nil;if(owned)dispatch_async(dispatch_get_main_queue(),^{[self finishRetiringManager:0];});}
    self.map.showsUserLocation=NO;
#endif
}
- (void)stopUser{[self halt];TIONavEnableNotices(NO);self.note=@"导航更新已停止并请求退出。字幕退出仍需镜片确认；未关闭时用实体按钮。不会自动重新开启。";self.routeSummary.text=@"导航已结束 · 可重新规划路线";self.display=TIONavDisplay(@"stopped",0,@"",-1,-1,-1,self.simulated);TIONavEnableDisplay(NO);[self refresh];}
#include "NavigationBackground.inc"
- (void)startFixture{[self halt];if(!TIONavigationClaim(@"amap")){[self alert:@"请先停止另一导航" message:@"高德与 MapKit 不同时运行，避免显示和定位争用。"];return;}self.ownsNavigation=YES;self.active=YES;self.fixture=YES;self.simulated=YES;self.fixtureStep=0;self.note=@"离线夹具：模拟转向顺序，不使用 Key、网络或定位";[self setFrame:TIONavDisplay(@"navigating",9,@"模拟测试道路",160,850,720,YES)];}
- (void)startWalking{
#if TIO_AMAP_ENABLED
    [self start:NO];
#else
    [self consent];
#endif
}
- (void)startSimulation{
#if TIO_AMAP_ENABLED
    [self start:YES];
#else
    [self consent];
#endif
}
#if TIO_AMAP_ENABLED
- (void)finishRetiringManager:(NSUInteger)attempt{
    Class cls=self.retiringManagerClass;if(!cls)return;
    if(self.retiringManager.delegate){self.retiringManagerClass=Nil;self.retiringManager=nil;self.note=@"旧引擎已被其他业务使用，不强行销毁。请先结束其他导航。";[self refresh];return;}
    if([(id<TIONavigationManagerFactory>)cls destroyInstance]){self.retiringManagerClass=Nil;self.retiringManager=nil;[self refresh];return;}
    if(attempt<3)dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/10),dispatch_get_main_queue(),^{[self finishRetiringManager:attempt+1];});else{self.note=@"旧导航引擎尚未释放，暂不切换；请退出导航页面后重试或重启App。";[self refresh];}
}
- (void)openMap{
    if(!self.initialized){
        for(NSString *name in @[@"AMap",@"AMapNavi",@"AMapSearch"])if(![NSBundle.mainBundle pathForResource:name ofType:@"bundle"]){self.pendingMapAction=nil;[self alert:@"缺少高德资源" message:@"请用匹配 SDK 资源重新合并并签名；只填写 Key 无法补齐资源。"] ;return;}
        if(!NavSDKInitialized){AMapNaviManagerConfig *config=AMapNaviManagerConfig.sharedConfig;[config updatePrivacyShow:AMapPrivacyShowStatusDidShow privacyInfo:AMapPrivacyInfoStatusDidContain];[config updatePrivacyAgree:AMapPrivacyAgreeStatusDidAgree];[MAMapView updatePrivacyShow:AMapPrivacyShowStatusDidShow privacyInfo:AMapPrivacyInfoStatusDidContain];[MAMapView updatePrivacyAgree:AMapPrivacyAgreeStatusDidAgree];AMapServices.sharedServices.apiKey=ReadNavKey();AMapServices.sharedServices.enableHTTPS=YES;NavSDKInitialized=YES;}self.initialized=YES;
        self.map=[MAMapView new];self.map.delegate=self;self.map.zoomLevel=15;self.map.centerCoordinate=CLLocationCoordinate2DMake(39.9087,116.3975);self.map.translatesAutoresizingMaskIntoConstraints=NO;[self.mapHost insertSubview:self.map atIndex:0];[NSLayoutConstraint activateConstraints:@[[self.map.leadingAnchor constraintEqualToAnchor:self.mapHost.leadingAnchor],[self.map.trailingAnchor constraintEqualToAnchor:self.mapHost.trailingAnchor],[self.map.topAnchor constraintEqualToAnchor:self.mapHost.topAnchor],[self.map.bottomAnchor constraintEqualToAnchor:self.mapHost.bottomAnchor]]];self.mapHint.hidden=YES;}
    // Do not create a Beijing demonstration origin when opening the real navigation page.
    self.note=@"地图已加载。单击地图选择终点或搜索地点；起点使用手机当前位置。";[self refresh];
    [self continueMapAction:0];
    [self locateMapOnEntry:0];
    [self resumeAutoPlan];
}
- (void)mapView:(MAMapView *)map didSingleTappedAtCoordinate:(CLLocationCoordinate2D)c{[self selectMapCoordinate:c];}
- (void)mapView:(MAMapView *)map didLongPressedAtCoordinate:(CLLocationCoordinate2D)c{[self selectMapCoordinate:c];}
- (void)start:(BOOL)sim{
#ifndef TIO_UI_PREVIEW
    if(sim){[self alert:@"实时出行导航" message:@"普通导航页不再提供模拟验收，请使用手机位置规划真实路线。"] ;return;}
#endif
    if(!self.initialized){[self consent];return;}
    if(self.active||self.planning){[self alert:@"请先停止当前导航" message:@"避免两条路线的异步回调互相覆盖。"] ;return;}
    if(!self.hasDestination){[self alert:@"先选择终点" message:@"搜索目的地或单击/长按地图选择终点，随后自动规划路线。"] ;return;}
    if(sim){if(!self.hasSimulationStart){[self alert:@"请选择模拟起点" message:@"切换地图上方的“选模拟起点”，然后点击地图。"] ;return;}CLLocation *a=[[CLLocation alloc]initWithLatitude:self.simulationStart.latitude longitude:self.simulationStart.longitude],*b=[[CLLocation alloc]initWithLatitude:self.destination.latitude longitude:self.destination.longitude];if([a distanceFromLocation:b]<30){[self alert:@"起终点距离不足30米" message:@"切换“选模拟起点”后点击另一个位置，或重新选择终点。拖动地图本身不会改变起点。"] ;return;}}
    if(!sim){CLAuthorizationStatus auth=self.permission.authorizationStatus;if(auth==kCLAuthorizationStatusNotDetermined){[self.permission requestWhenInUseAuthorization];self.note=@"等待定位授权后自动规划当前终点";[self refresh];return;}if(auth==kCLAuthorizationStatusDenied||auth==kCLAuthorizationStatusRestricted){[self alert:@"定位未授权" message:@"请在系统设置允许定位后重新选择终点；不会绕过系统权限。"] ;return;}}
    if(self.retiringManagerClass){[self alert:@"正在释放旧导航引擎" message:@"请稍后再规划，不会带着旧路线切换模式。"] ;return;}
    Class cls=TIONavigationManagerClass(self.selectedTransport);if(!cls){[self alert:@"未知出行方式" message:@"请重新选择步行、骑行或驾车。"] ;return;}
    id<TIONavigationManager> manager=[(id<TIONavigationManagerFactory>)cls sharedInstance];if(!manager||manager.naviMode!=AMapNaviModeNone||(manager.delegate&&manager.delegate!=self)){[self alert:@"导航引擎被占用" message:@"请先结束其他导航。本扩展不会停止宿主已有导航，也不会偷偷退回步行算路。"] ;return;}
    if(!TIONavigationClaim(@"amap")){[self alert:@"请先停止另一导航" message:@"高德与 MapKit 不同时运行，本页面不会停止另一导航。"];return;}self.ownsNavigation=YES;
    [self cancelMapLocation];
    self.sessionTransport=self.selectedTransport;
    self.manager=manager;manager.delegate=self;[manager addDataRepresentative:self];manager.isUseInternalTTS=NO;manager.screenAlwaysBright=NO;manager.allowsBackgroundLocationUpdates=NO;
    self.active=YES;self.planning=YES;self.lensStartApproved=NO;self.autoDisplayAttempted=NO;self.simulated=sim;self.fixture=NO;self.gpsWeak=NO;self.staleShown=NO;self.lastInfo=self.lastSDKInfo=0;self.infoCallbacks=0;self.lastGPSStrength=-1;NSUInteger generation=++self.generation;
    self.note=[NSString stringWithFormat:@"%@%@算路中（%@）",TIONavigationModeTitle(self.sessionTransport),sim?@"模拟":@"实时",sim?@"联网，不使用实际定位":@"使用手机定位，开始后支持后台"];[self setFrame:TIONavDisplay(@"planning",0,@"",-1,-1,-1,sim)];
    AMapNaviPoint *end=[AMapNaviPoint locationWithLatitude:self.destination.latitude longitude:self.destination.longitude];
    BOOL submitted=TIONavigationCalculate(manager,self.sessionTransport,sim,[AMapNaviPoint locationWithLatitude:self.simulationStart.latitude longitude:self.simulationStart.longitude],end);
    if(!sim)self.map.showsUserLocation=YES;
    if(!submitted){[self fail:@"SDK 未接受算路请求，请核对 Key、网络和定位"] ;return;}
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,45*NSEC_PER_SEC),dispatch_get_main_queue(),^{if(self.generation==generation&&self.planning)[self fail:@"45 秒未收到算路结果；已停止，本次结果未知"] ;});
}
- (void)fail:(NSString *)message{[self halt];self.note=message;[self setFrame:TIONavDisplay(@"error",0,@"",-1,-1,-1,self.simulated)];}
- (void)navigationRouteSuccess:(id<TIONavigationManager>)manager{NSUInteger generation=self.generation;dispatch_async(dispatch_get_main_queue(),^{if(generation!=self.generation||manager!=self.manager||!self.active)return;BOOL first=self.planning;self.planning=NO;self.rerouting=NO;self.staleShown=NO;[self drawRoute:manager.naviRoute];if(first){self.routeReady=YES;self.note=@"路线已准备，核对地图后点击开始。尚未向眼镜发送导航。";self.routeSummary.text=[NSString stringWithFormat:@"%.1f 公里   ·   约 %ld 分钟",manager.naviRoute.routeLength/1000.0,(long)MAX(1,(manager.naviRoute.routeTime+59)/60)];self.display=TIONavDisplay(@"ready",0,@"",-1,manager.naviRoute.routeLength,manager.naviRoute.routeTime,self.simulated);}else{self.note=@"重算完成，等待本路线新指引；中性字幕保持本次会话。";if(self.navigationStarted&&!self.simulated)[self setFrame:TIONavSubtitleHoldingFrame(@"waiting")];}[self refresh];});}
- (void)navigationManager:(id<TIONavigationManager>)manager onCalculateRouteFailure:(NSError *)error{NSUInteger generation=self.generation;dispatch_async(dispatch_get_main_queue(),^{if(generation==self.generation&&manager==self.manager&&self.active)[self fail:[NSString stringWithFormat:@"高德算路失败（code=%ld），检查 Key 服务权限／Bundle 绑定、网络与路线",(long)error.code]];});}
- (void)navigationManager:(id<TIONavigationManager>)manager error:(NSError *)error{NSUInteger generation=self.generation;dispatch_async(dispatch_get_main_queue(),^{if(generation==self.generation&&manager==self.manager&&self.active)[self fail:[NSString stringWithFormat:@"高德引擎错误 code=%ld",(long)error.code]];});}
- (void)navigationManager:(id<TIONavigationManager>)manager updateNaviInfo:(AMapNaviInfo *)info{
    if(!info)return;NSUInteger generation=self.generation;BOOL simulated=self.simulated;
    NSMutableDictionary *frame=[TIONavDisplay(@"navigating",info.iconType,info.nextRoadName,info.segmentRemainDistance,info.routeRemainDistance,info.routeRemainTime,simulated) mutableCopy];frame[@"segment"]=@(info.currentSegmentIndex);
    dispatch_async(dispatch_get_main_queue(),^{
        if(generation!=self.generation||manager!=self.manager||!self.active||!self.navigationStarted||self.routeReady||self.planning||self.rerouting)return;
        self.lastSDKInfo=NSProcessInfo.processInfo.systemUptime;self.infoCallbacks++;
        if(self.gpsWeak){if(!self.simulated)[self setFrame:TIONavSubtitleHoldingFrame(@"weak")];return;}
        if(!self.simulated&&!TIONavSubtitleText(frame)){[self setFrame:TIONavSubtitleHoldingFrame(@"invalid")];return;}
        self.lastInfo=self.lastSDKInfo;self.staleShown=NO;[self setFrame:frame];if(self.simulated&&!self.autoDisplayAttempted)[self startAutomaticDisplay];
    });
}
- (void)navigationReroute:(id<TIONavigationManager>)manager{dispatch_async(dispatch_get_main_queue(),^{if(manager==self.manager&&self.active){self.rerouting=YES;self.lastInfo=0;self.note=@"偏航，等待高德重新规划";[self setFrame:TIONavDisplay(@"rerouting",0,@"",-1,-1,-1,self.simulated)];}});}
- (void)drawRoute:(AMapNaviRoute *)route{NSArray<AMapNaviPoint *> *points=route.routeCoordinates;if(points.count<2||points.count>100000)return;CLLocationCoordinate2D *coords=calloc(points.count,sizeof(CLLocationCoordinate2D));if(!coords)return;for(NSUInteger i=0;i<points.count;i++)coords[i]=CLLocationCoordinate2DMake(points[i].latitude,points[i].longitude);if(self.routeLine)[self.map removeOverlay:self.routeLine];self.routeLine=[MAPolyline polylineWithCoordinates:coords count:points.count];free(coords);[self.map addOverlay:self.routeLine];[self.map setVisibleMapRect:self.routeLine.boundingMapRect edgePadding:UIEdgeInsetsMake(30,25,30,25) animated:YES];}
- (MAOverlayRenderer *)mapView:(MAMapView *)map rendererForOverlay:(id<MAOverlay>)overlay{if([overlay isKindOfClass:MAPolyline.class]){MAPolylineRenderer *r=[[MAPolylineRenderer alloc]initWithPolyline:overlay];r.lineWidth=6;r.strokeColor=UIColor.systemIndigoColor;return r;}return nil;}
- (void)navigationManager:(id<TIONavigationManager>)manager updateGPSSignalStrength:(AMapNaviGPSSignalStrength)strength{dispatch_async(dispatch_get_main_queue(),^{if(manager!=self.manager||!self.active||self.simulated)return;self.lastGPSStrength=strength;BOOL wasWeak=self.gpsWeak;self.gpsWeak=strength!=AMapNaviGPSSignalStrengthStrong&&strength!=AMapNaviGPSSignalStrengthSmartPos;if(self.gpsWeak){self.lastInfo=0;[self setFrame:TIONavSubtitleHoldingFrame(@"weak")];}else if(wasWeak&&self.navigationStarted){self.lastInfo=0;[self setFrame:TIONavSubtitleHoldingFrame(@"waiting")];}});}
- (void)navigationLocationPulse:(id<TIONavigationManager>)manager{NSUInteger generation=self.generation;dispatch_async(dispatch_get_main_queue(),^{if(generation==self.generation&&manager==self.manager&&self.navigationStarted&&!self.simulated)[self pumpRoute];});}
- (void)walkManager:(AMapNaviWalkManager *)m updateNaviLocation:(AMapNaviLocation *)location{if(location)[self navigationLocationPulse:(id)m];}
- (void)rideManager:(AMapNaviRideManager *)m updateNaviLocation:(AMapNaviLocation *)location{if(location)[self navigationLocationPulse:(id)m];}
- (void)driveManager:(AMapNaviDriveManager *)m updateNaviLocation:(AMapNaviLocation *)location{if(location)[self navigationLocationPulse:(id)m];}
- (void)arrived:(id<TIONavigationManager>)manager{dispatch_async(dispatch_get_main_queue(),^{if(manager!=self.manager||!self.active)return;[self halt];self.note=@"已到达；导航已停止，10 秒后清理本次导航卡";[self setFrame:TIONavDisplay(@"arrived",0,@"",0,0,0,self.simulated)];NSUInteger g=self.generation;dispatch_after(dispatch_time(DISPATCH_TIME_NOW,10*NSEC_PER_SEC),dispatch_get_main_queue(),^{if(g==self.generation)TIONavEnableDisplay(NO);});});}
- (void)walkManagerOnCalculateRouteSuccess:(AMapNaviWalkManager *)m{[self navigationRouteSuccess:(id)m];}
- (void)walkManager:(AMapNaviWalkManager *)m onCalculateRouteFailure:(NSError *)e{[self navigationManager:(id)m onCalculateRouteFailure:e];}
- (void)walkManager:(AMapNaviWalkManager *)m error:(NSError *)e{[self navigationManager:(id)m error:e];}
- (void)walkManager:(AMapNaviWalkManager *)m updateNaviInfo:(AMapNaviInfo *)info{[self navigationManager:(id)m updateNaviInfo:info];}
- (void)walkManager:(AMapNaviWalkManager *)m updateGPSSignalStrength:(AMapNaviGPSSignalStrength)s{[self navigationManager:(id)m updateGPSSignalStrength:s];}
- (void)walkManagerNeedRecalculateRouteForYaw:(AMapNaviWalkManager *)m{[self navigationReroute:(id)m];}
- (void)walkManagerDidEndEmulatorNavi:(AMapNaviWalkManager *)m{[self arrived:(id)m];}
- (void)walkManagerOnArrivedDestination:(AMapNaviWalkManager *)m{[self arrived:(id)m];}
- (void)rideManagerOnCalculateRouteSuccess:(AMapNaviRideManager *)m{[self navigationRouteSuccess:(id)m];}
- (void)rideManager:(AMapNaviRideManager *)m onCalculateRouteFailure:(NSError *)e{[self navigationManager:(id)m onCalculateRouteFailure:e];}
- (void)rideManager:(AMapNaviRideManager *)m error:(NSError *)e{[self navigationManager:(id)m error:e];}
- (void)rideManager:(AMapNaviRideManager *)m updateNaviInfo:(AMapNaviInfo *)info{[self navigationManager:(id)m updateNaviInfo:info];}
- (void)rideManager:(AMapNaviRideManager *)m updateGPSSignalStrength:(AMapNaviGPSSignalStrength)s{[self navigationManager:(id)m updateGPSSignalStrength:s];}
- (void)rideManagerNeedRecalculateRouteForYaw:(AMapNaviRideManager *)m{[self navigationReroute:(id)m];}
- (void)rideManagerDidEndEmulatorNavi:(AMapNaviRideManager *)m{[self arrived:(id)m];}
- (void)rideManagerOnArrivedDestination:(AMapNaviRideManager *)m{[self arrived:(id)m];}
- (void)driveManagerOnCalculateRouteSuccess:(AMapNaviDriveManager *)m{[self navigationRouteSuccess:(id)m];}
- (void)driveManager:(AMapNaviDriveManager *)m onCalculateRouteFailure:(NSError *)e{[self navigationManager:(id)m onCalculateRouteFailure:e];}
- (void)driveManager:(AMapNaviDriveManager *)m error:(NSError *)e{[self navigationManager:(id)m error:e];}
- (void)driveManager:(AMapNaviDriveManager *)m updateNaviInfo:(AMapNaviInfo *)info{[self navigationManager:(id)m updateNaviInfo:info];}
- (void)driveManager:(AMapNaviDriveManager *)m updateGPSSignalStrength:(AMapNaviGPSSignalStrength)s{[self navigationManager:(id)m updateGPSSignalStrength:s];}
- (void)driveManagerNeedRecalculateRouteForYaw:(AMapNaviDriveManager *)m{[self navigationReroute:(id)m];}
- (void)driveManagerDidEndEmulatorNavi:(AMapNaviDriveManager *)m{[self arrived:(id)m];}
- (void)driveManagerOnArrivedDestination:(AMapNaviDriveManager *)m{[self arrived:(id)m];}
- (void)driveManagerNeedRecalculateRouteForTrafficJam:(AMapNaviDriveManager *)m{[self navigationReroute:(id)m];}
#endif
#include "NavigationWorkspace.inc"
@end
UIViewController *TIONavigationController(void){if(LiveRoutePanel&&[TIONavigationRouteSession.shared owns:LiveRoutePanel])return LiveRoutePanel;return [TIONavigationPanel new];}
void TIOPresentStandaloneNavigation(UIViewController *host){
    if(!NSThread.isMainThread||!host||host.presentedViewController)return;
    TIONavigationPanel *page=(id)TIONavigationController();
    if(page.navigationController){UINavigationController *old=page.navigationController;if(old.presentingViewController)return;if(page.standaloneNavigation&&old.viewControllers.firstObject==page){[host presentViewController:old animated:YES completion:nil];return;}NSMutableArray *pages=[old.viewControllers mutableCopy];[pages removeObjectIdenticalTo:page];[old setViewControllers:pages animated:NO];}
    if(page.parentViewController)return;
    page.standaloneNavigation=YES;UINavigationController *nav=[[UINavigationController alloc]initWithRootViewController:page];nav.modalPresentationStyle=UIModalPresentationFullScreen;
    [page loadViewIfNeeded];page.navigationItem.leftBarButtonItem=[[UIBarButtonItem alloc]initWithTitle:@"返回首页" style:UIBarButtonItemStylePlain target:page action:@selector(closeStandaloneNavigation)];
    [host presentViewController:nav animated:YES completion:nil];
}
