#import "MapKitNavigation.h"
#import "MapKitNavigationCore.h"
#import "MapKitRoads.h"
#import "MapKitWireframe.h"
#import "MapKitHUD.h"
#import "NavigationSession.h"
#if TIO_MAPKIT_HUD_ENABLED
#import "MapKitHUDHost.h"
#endif
#import <MapKit/MapKit.h>
#import <CoreLocation/CoreLocation.h>
#include <math.h>

// Selectively adapted route/workspace/place-selection patterns from sattarde/
// rayneo-ios-english-edition, commit 6268990646ebd41188cb084bb8ef96d70e94b869:
// focus-edition/NavigationUI.m, NavigationWorkspace.inc, NavigationPlaces.m.
// Independent foreground-only state machine; no fork transport/background imports.
__attribute__((used)) const char TIOMapKitBuildPolicy[]="turboio-mapkit-foreground-v1";
static NSString *const RecentKey=@"TIO.MapKit.SelectedPlaces.v1";
static double Uptime(void){return NSProcessInfo.processInfo.systemUptime;}
static BOOL UnverifiedChina(CLLocationCoordinate2D c){return c.latitude>=18&&c.latitude<=54&&c.longitude>=73&&c.longitude<=135;}
static NSString *Space(CLLocationCoordinate2D c){
    // Outside the conservative China exclusion, MapKit/CoreLocation use WGS84.
    // Never claim mainland route/GPS/OSM alignment from visual similarity.
    return UnverifiedChina(c)?@"mapkit-unverified":@"wgs84";
}
static NSArray *Pair(CLLocationCoordinate2D c){return @[@(c.latitude),@(c.longitude)];}
static CLLocationCoordinate2D Coordinate(NSArray *p){return CLLocationCoordinate2DMake([p[0] doubleValue],[p[1] doubleValue]);}
static NSArray *PolylinePoints(MKPolyline *line){
    if(!line||line.pointCount<2||line.pointCount>100000)return @[];
    CLLocationCoordinate2D *p=calloc(line.pointCount,sizeof(*p));if(!p)return @[];
    [line getCoordinates:p range:NSMakeRange(0,line.pointCount)];NSMutableArray *out=[NSMutableArray new];
    for(NSUInteger i=0;i<line.pointCount;i++){
        if(!TIOMapKitCoordinateValid(p[i].latitude,p[i].longitude)){[out removeAllObjects];break;}[out addObject:Pair(p[i])];
    }
    free(p);return out;
}
static NSDictionary *ValidPlace(id obj){
    if(![obj isKindOfClass:NSDictionary.class])return nil;NSDictionary *p=obj;
    if(![p[@"schema"] isEqual:@1]||![p[@"provider"] isEqual:@"mapkit"]||![p[@"name"] isKindOfClass:NSString.class]||![p[@"name"] length])return nil;
    if(![p[@"latitude"] isKindOfClass:NSNumber.class]||![p[@"longitude"] isKindOfClass:NSNumber.class]||CFGetTypeID((__bridge CFTypeRef)p[@"latitude"])==CFBooleanGetTypeID()||CFGetTypeID((__bridge CFTypeRef)p[@"longitude"])==CFBooleanGetTypeID())return nil;
    if(p[@"address"]&&![p[@"address"] isKindOfClass:NSString.class])return nil;
    double lat=[p[@"latitude"] doubleValue],lon=[p[@"longitude"] doubleValue];
    if(!TIOMapKitCoordinateValid(lat,lon)||![p[@"coordinateSpace"] isEqual:Space(CLLocationCoordinate2DMake(lat,lon))])return nil;
    return p;
}

@interface TIOMapKitNavigationPanel : UIViewController<MKMapViewDelegate,CLLocationManagerDelegate,UISearchBarDelegate,UITableViewDelegate,UITableViewDataSource,MKLocalSearchCompleterDelegate>
@property(nonatomic) UIStackView *stack;
@property(nonatomic) MKMapView *map;
@property(nonatomic) UISearchBar *searchBar;
@property(nonatomic) UITableView *table;
@property(nonatomic) UILabel *noteLabel,*routeLabel,*hudLabel;
@property(nonatomic) UIImageView *preview;
@property(nonatomic) UISegmentedControl *modeControl,*pickControl,*originControl;
@property(nonatomic) UIButton *consentButton,*planButton,*simulateButton,*liveButton,*hudButton,*stopButton;
@property(nonatomic) CLLocationManager *locationManager;
@property(nonatomic) CLLocation *validLocation;
@property(nonatomic) NSTimer *timer;
@property(nonatomic) MKLocalSearchCompleter *completer;
@property(nonatomic) MKLocalSearch *search;
@property(nonatomic) MKDirections *directions;
@property(nonatomic) NSArray *rows,*recents,*stepStarts;
@property(nonatomic) MKRoute *route;
@property(nonatomic) MKPolyline *routeOverlay;
@property(nonatomic) MKPointAnnotation *destinationPin,*simulationPin;
@property(nonatomic) TIOMapKitProgress *progress;
@property(nonatomic) TIOMapKitRerouteGate *rerouteGate;
@property(nonatomic) TIOMapKitRoads *roads;
@property(nonatomic) NSDictionary *frame;
@property(nonatomic) NSString *sessionId,*mode,*coordinateSpace,*note;
@property(nonatomic) NSUInteger generation,searchGeneration,revision;
@property(nonatomic) BOOL consented,ownsNavigation,ready,active,simulated,planning,hasDestination,hasSimulationStart,plannedFromSimulation,hudRequested,visible;
@property(nonatomic) CLLocationCoordinate2D destination,simulationStart,plannedOrigin,currentCoordinate;
@property(nonatomic) double heading,simulationDistance,lastFixTimestamp,lastValidUptime,lastHUDOffer,lastSimulationUptime,lastProjectionUptime;
@end

@implementation TIOMapKitNavigationPanel
- (UILabel *)label{
    UILabel *l=[UILabel new];l.numberOfLines=0;l.font=[UIFont preferredFontForTextStyle:UIFontTextStyleBody];[self.stack addArrangedSubview:l];return l;
}
- (UIButton *)button:(NSString *)title selector:(SEL)selector{
    UIButton *b=[UIButton buttonWithType:UIButtonTypeSystem];b.configuration=[UIButtonConfiguration tintedButtonConfiguration];[b setTitle:title forState:UIControlStateNormal];[b addTarget:self action:selector forControlEvents:UIControlEventTouchUpInside];[self.stack addArrangedSubview:b];return b;
}
- (void)viewDidLoad{
    [super viewDidLoad];self.title=@"MapKit 导航";self.view.backgroundColor=UIColor.systemBackgroundColor;self.mode=@"walk";self.heading=-1;self.coordinateSpace=@"mapkit-unverified";self.sessionId=NSUUID.UUID.UUIDString;self.rerouteGate=[TIOMapKitRerouteGate new];
    UIScrollView *scroll=[UIScrollView new];scroll.translatesAutoresizingMaskIntoConstraints=NO;scroll.keyboardDismissMode=UIScrollViewKeyboardDismissModeOnDrag;[self.view addSubview:scroll];
    self.stack=[UIStackView new];self.stack.axis=UILayoutConstraintAxisVertical;self.stack.spacing=12;self.stack.translatesAutoresizingMaskIntoConstraints=NO;[scroll addSubview:self.stack];
    [NSLayoutConstraint activateConstraints:@[[scroll.leadingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.leadingAnchor],[scroll.trailingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.trailingAnchor],[scroll.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor],[scroll.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor],[self.stack.leadingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.leadingAnchor constant:16],[self.stack.trailingAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.trailingAnchor constant:-16],[self.stack.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:16],[self.stack.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-20],[self.stack.widthAnchor constraintEqualToAnchor:scroll.frameLayoutGuide.widthAnchor constant:-32]]];
    self.noteLabel=[self label];self.note=@"仅前台导航。搜索、规划会把地点与坐标发送给 Apple；附近道路查询会把坐标发送给 Overpass（OpenStreetMap）。不保存轨迹，不迁移高德历史。中国区域未验证坐标对齐，仅可模拟，不叠加 OSM 道路。";
    self.consentButton=[self button:@"同意并打开 Apple 地图" selector:@selector(consent)];
    self.searchBar=[UISearchBar new];self.searchBar.placeholder=@"搜索地点后明确选择结果";self.searchBar.delegate=self;self.searchBar.userInteractionEnabled=NO;[self.stack addArrangedSubview:self.searchBar];
    self.table=[[UITableView alloc]initWithFrame:CGRectZero style:UITableViewStylePlain];self.table.delegate=self;self.table.dataSource=self;[self.table.heightAnchor constraintEqualToConstant:170].active=YES;[self.stack addArrangedSubview:self.table];
    NSMutableArray *recents=[NSMutableArray new];id saved=[NSUserDefaults.standardUserDefaults objectForKey:RecentKey];if([saved isKindOfClass:NSArray.class])for(id p in saved){if(ValidPlace(p))[recents addObject:p];if(recents.count>=12)break;}self.recents=recents;self.rows=recents;
    self.pickControl=[[UISegmentedControl alloc]initWithItems:@[@"选终点",@"选模拟起点"]];self.pickControl.selectedSegmentIndex=0;[self.stack addArrangedSubview:self.pickControl];
    self.originControl=[[UISegmentedControl alloc]initWithItems:@[@"真实定位起点",@"指定模拟起点"]];self.originControl.selectedSegmentIndex=0;[self.originControl addTarget:self action:@selector(settingsChanged) forControlEvents:UIControlEventValueChanged];[self.stack addArrangedSubview:self.originControl];
    self.modeControl=[[UISegmentedControl alloc]initWithItems:@[@"步行",@"骑行",@"驾车"]];self.modeControl.selectedSegmentIndex=0;[self.modeControl addTarget:self action:@selector(settingsChanged) forControlEvents:UIControlEventValueChanged];[self.stack addArrangedSubview:self.modeControl];
    self.routeLabel=[self label];[self button:@"请求前台定位 / 定位到我" selector:@selector(locate)];self.planButton=[self button:@"规划路线（不自动开始）" selector:@selector(plan)];self.simulateButton=[self button:@"明确开始模拟" selector:@selector(startSimulation)];self.liveButton=[self button:@"明确开始实地导航" selector:@selector(startLive)];self.stopButton=[self button:@"停止导航与眼镜 HUD" selector:@selector(stopUser)];self.hudButton=[self button:@"启动眼镜 HUD" selector:@selector(toggleHUD)];
    self.hudLabel=[self label];self.preview=[UIImageView new];self.preview.contentMode=UIViewContentModeScaleAspectFit;[self.preview.heightAnchor constraintEqualToConstant:200].active=YES;[self.stack addArrangedSubview:self.preview];
    UILabel *credit=[self label];credit.text=@"地图与路线 © Apple · 道路 © OpenStreetMap contributors（ODbL，经 Overpass）\nApple 路线区域与骑行支持可能有限；不把骑行失败替换为步行。眼镜未收到 ACK 前无法保证镜片显示。";credit.font=[UIFont preferredFontForTextStyle:UIFontTextStyleFootnote];
    self.locationManager=[CLLocationManager new];self.locationManager.delegate=self;self.locationManager.desiredAccuracy=kCLLocationAccuracyBestForNavigation;self.locationManager.distanceFilter=5;self.locationManager.allowsBackgroundLocationUpdates=NO;
    self.roads=[TIOMapKitRoads new];__weak typeof(self) weak=self;self.roads.changed=^{if(weak.visible&&weak.active&&[weak.coordinateSpace isEqual:@"wgs84"])[weak emitPhase:weak.frame[@"phase"]?:@"navigating"];};
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(background) name:UIApplicationWillResignActiveNotification object:nil];[NSNotificationCenter.defaultCenter addObserver:self selector:@selector(background) name:UIApplicationDidEnterBackgroundNotification object:nil];[NSNotificationCenter.defaultCenter addObserver:self selector:@selector(stopUser) name:@"TIOResearchClosed" object:nil];
    [self emitPhase:@"stopped"];
}
- (void)viewWillAppear:(BOOL)animated{
    [super viewWillAppear:animated];self.visible=YES;if(!self.timer){__weak typeof(self) weak=self;self.timer=[NSTimer timerWithTimeInterval:1 repeats:YES block:^(NSTimer *timer){(void)timer;[weak tick];}];[NSRunLoop.mainRunLoop addTimer:self.timer forMode:NSRunLoopCommonModes];}[self refresh];
}
- (void)viewDidDisappear:(BOOL)animated{[super viewDidDisappear:animated];self.visible=NO;[self stopWithReason:@"已离开导航页面" phase:@"stopped"];[self.timer invalidate];self.timer=nil;}
- (void)dealloc{
    [self.timer invalidate];[self.directions cancel];[self.search cancel];self.completer.delegate=nil;[self.completer cancel];self.locationManager.delegate=nil;[self.locationManager stopUpdatingLocation];[self.roads cancel];
    if(self.ownsNavigation){TIOMapKitHUDStop(@"导航页面释放");TIONavigationRelease(@"mapkit");}
    [NSNotificationCenter.defaultCenter removeObserver:self];
}
- (void)alert:(NSString *)title message:(NSString *)message{
    if(self.presentedViewController)return;UIAlertController *a=[UIAlertController alertControllerWithTitle:title message:message preferredStyle:UIAlertControllerStyleAlert];[a addAction:[UIAlertAction actionWithTitle:@"知道了" style:UIAlertActionStyleCancel handler:nil]];[self presentViewController:a animated:YES completion:nil];
}
- (void)consent{
    if(self.consented)return;self.consented=YES;self.consentButton.hidden=YES;self.searchBar.userInteractionEnabled=YES;
    self.map=[MKMapView new];self.map.delegate=self;[self.map.heightAnchor constraintEqualToConstant:300].active=YES;[self.stack insertArrangedSubview:self.map atIndex:4];[self.map addGestureRecognizer:[[UILongPressGestureRecognizer alloc]initWithTarget:self action:@selector(longPress:)]];
    self.note=@"长按地图选择终点或模拟起点；搜索结果须再次点选。先规划，再明确开始。中国区域仅模拟，OSM 叠加关闭。";[self refresh];
}
- (BOOL)isForeground{return self.visible&&UIApplication.sharedApplication.applicationState==UIApplicationStateActive;}
- (void)locate{
    if(!self.consented){[self alert:@"请先同意" message:@"定位和地图服务需要上方的坐标共享知情同意。"];return;}
    if(![NSBundle.mainBundle objectForInfoDictionaryKey:@"NSLocationWhenInUseUsageDescription"]){[self alert:@"无法请求定位" message:@"当前构建未声明前台定位用途。可指定模拟起点。"];return;}
    CLAuthorizationStatus status=self.locationManager.authorizationStatus;
    if(status==kCLAuthorizationStatusNotDetermined){[self.locationManager requestWhenInUseAuthorization];return;}
    if(status==kCLAuthorizationStatusDenied||status==kCLAuthorizationStatusRestricted){[self alert:@"定位不可用" message:@"请在系统设置允许使用 App 期间定位，或选择模拟起点。"];return;}
    if([self isForeground]){self.map.showsUserLocation=YES;[self.locationManager startUpdatingLocation];}
    if([self currentFixValid])[self.map setRegion:MKCoordinateRegionMakeWithDistance(self.validLocation.coordinate,1500,1500) animated:YES];
}
- (void)locationManagerDidChangeAuthorization:(CLLocationManager *)manager{
    CLAuthorizationStatus s=manager.authorizationStatus;if(s==kCLAuthorizationStatusAuthorizedWhenInUse||s==kCLAuthorizationStatusAuthorizedAlways){if(self.consented&&[self isForeground])[self locate];}else if(s==kCLAuthorizationStatusDenied||s==kCLAuthorizationStatusRestricted){self.validLocation=nil;if(self.active&&!self.simulated)[self stopWithReason:@"定位权限已撤销" phase:@"error"];}
}
- (BOOL)currentFixValid{
    CLLocation *l=self.validLocation;return l&&TIOMapKitFixValid(l.coordinate.latitude,l.coordinate.longitude,l.horizontalAccuracy,l.timestamp.timeIntervalSince1970,NSDate.date.timeIntervalSince1970);
}
- (void)cancelSearch{
    self.searchGeneration++;[self.search cancel];self.search=nil;self.completer.delegate=nil;[self.completer cancel];self.completer=nil;
}
- (void)searchBar:(UISearchBar *)bar textDidChange:(NSString *)text{
    [self cancelSearch];if(self.active||self.planning||!self.consented||![self isForeground])return;
    if(![text stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet].length){self.rows=self.recents;[self.table reloadData];return;}
    self.rows=@[];[self.table reloadData];self.completer=[MKLocalSearchCompleter new];self.completer.delegate=self;self.completer.resultTypes=MKLocalSearchCompleterResultTypeAddress|MKLocalSearchCompleterResultTypePointOfInterest;if(self.map)self.completer.region=self.map.region;self.completer.queryFragment=text;
}
- (void)completerDidUpdateResults:(MKLocalSearchCompleter *)completer{
    if(completer!=self.completer||![self isForeground])return;self.rows=completer.results;[self.table reloadData];
}
- (void)completer:(MKLocalSearchCompleter *)completer didFailWithError:(NSError *)error{
    if(completer!=self.completer)return;self.note=[@"Apple 搜索失败：" stringByAppendingString:error.localizedDescription];[self refresh];
}
- (NSInteger)tableView:(UITableView *)table numberOfRowsInSection:(NSInteger)section{return self.rows.count;}
- (UITableViewCell *)tableView:(UITableView *)table cellForRowAtIndexPath:(NSIndexPath *)index{
    UITableViewCell *cell=[table dequeueReusableCellWithIdentifier:@"place"];if(!cell)cell=[[UITableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"place"];
    id p=self.rows[index.row];if([p isKindOfClass:MKLocalSearchCompletion.class]){cell.textLabel.text=[p title];cell.detailTextLabel.text=[p subtitle];}else{cell.textLabel.text=p[@"name"];cell.detailTextLabel.text=p[@"address"]?:@"已明确选择的 MapKit 地点";}return cell;
}
- (void)tableView:(UITableView *)table didSelectRowAtIndexPath:(NSIndexPath *)index{
    [table deselectRowAtIndexPath:index animated:YES];if(!self.consented||self.active||self.planning||index.row<0||(NSUInteger)index.row>=self.rows.count||![self isForeground])return;id row=self.rows[index.row];
    if([row isKindOfClass:NSDictionary.class]){NSDictionary *p=ValidPlace(row);if(p)[self selectCoordinate:CLLocationCoordinate2DMake([p[@"latitude"] doubleValue],[p[@"longitude"] doubleValue]) name:p[@"name"] address:p[@"address"]?:@""];return;}
    MKLocalSearchRequest *request=[[MKLocalSearchRequest alloc]initWithCompletion:row];request.region=self.map.region;[self cancelSearch];NSUInteger generation=self.searchGeneration;self.search=[[MKLocalSearch alloc]initWithRequest:request];__weak typeof(self) weak=self;
    [self.search startWithCompletionHandler:^(MKLocalSearchResponse *response,NSError *error){dispatch_async(dispatch_get_main_queue(),^{
        typeof(self) s=weak;if(!s||generation!=s.searchGeneration||![s isForeground]||s.active||s.planning)return;s.search=nil;NSMutableArray *results=[NSMutableArray new];
        for(MKMapItem *item in response.mapItems){CLLocationCoordinate2D c=item.placemark.coordinate;if(!TIOMapKitCoordinateValid(c.latitude,c.longitude))continue;[results addObject:@{@"schema":@1,@"provider":@"mapkit",@"coordinateSpace":Space(c),@"latitude":@(c.latitude),@"longitude":@(c.longitude),@"name":item.name?:@"地图地点",@"address":item.placemark.title?:@""}];}
        s.rows=results;[s.table reloadData];s.note=error?[@"搜索失败：" stringByAppendingString:error.localizedDescription]:(results.count?@"请点选一个精确地点；搜索结果不会自动设为终点。":@"未找到地点，请更换关键词。");[s refresh];
    });}];
}
- (void)longPress:(UILongPressGestureRecognizer *)gesture{
    if(gesture.state!=UIGestureRecognizerStateBegan||self.active||self.planning)return;CLLocationCoordinate2D c=[self.map convertPoint:[gesture locationInView:self.map] toCoordinateFromView:self.map];[self selectCoordinate:c name:self.pickControl.selectedSegmentIndex?@"手选模拟起点":@"手选终点" address:@""];
}
- (void)selectCoordinate:(CLLocationCoordinate2D)c name:(NSString *)name address:(NSString *)address{
    if(!TIOMapKitCoordinateValid(c.latitude,c.longitude))return;[self cancelSearch];if(self.ownsNavigation)[self stopWithReason:@"地点变更，请重新规划" phase:@"stopped"];
    if(self.pickControl.selectedSegmentIndex==1){self.simulationStart=c;self.hasSimulationStart=YES;self.originControl.selectedSegmentIndex=1;if(self.simulationPin)[self.map removeAnnotation:self.simulationPin];self.simulationPin=[MKPointAnnotation new];self.simulationPin.coordinate=c;self.simulationPin.title=@"模拟起点";[self.map addAnnotation:self.simulationPin];}
    else{self.destination=c;self.hasDestination=YES;if(self.destinationPin)[self.map removeAnnotation:self.destinationPin];self.destinationPin=[MKPointAnnotation new];self.destinationPin.coordinate=c;self.destinationPin.title=name;[self.map addAnnotation:self.destinationPin];}
    NSDictionary *place=@{@"schema":@1,@"provider":@"mapkit",@"coordinateSpace":Space(c),@"name":[name substringToIndex:MIN(name.length,150)],@"address":[address substringToIndex:MIN(address.length,300)],@"latitude":@(c.latitude),@"longitude":@(c.longitude)};NSMutableArray *recent=[NSMutableArray arrayWithObject:place];
    for(NSDictionary *p in self.recents){if(TIOMapKitDistance(Pair(c),@[@([p[@"latitude"] doubleValue]),@([p[@"longitude"] doubleValue])])>2)[recent addObject:p];if(recent.count>=12)break;}self.recents=recent;[NSUserDefaults.standardUserDefaults setObject:recent forKey:RecentKey];self.rows=recent;[self.table reloadData];[self.searchBar resignFirstResponder];
    self.note=UnverifiedChina(c)?@"已选择地点。中国区域坐标对齐未验证：仅模拟，禁用实地导航和 OSM 道路叠加。":@"已选择地点，请规划路线。";[self refresh];
}
- (void)settingsChanged{
    if(self.ownsNavigation)[self stopWithReason:@"模式或起点变更，请重新规划" phase:@"stopped"];self.mode=@[@"walk",@"ride",@"drive"][self.modeControl.selectedSegmentIndex];[self refresh];
}
- (void)plan{
    if(![self isForeground]||!self.consented||!self.hasDestination||self.active||self.planning)return;
    if(self.originControl.selectedSegmentIndex==1){if(!self.hasSimulationStart){[self alert:@"选择模拟起点" message:@"切换到「选模拟起点」，长按地图或明确选取搜索地点。不会制造起点。"];return;}self.plannedOrigin=self.simulationStart;self.plannedFromSimulation=YES;}
    else{if(![self currentFixValid]){[self alert:@"需要有效定位" message:@"请请求定位；精度需不大于 50 米、时间不超过 15 秒。也可指定模拟起点。"];return;}self.plannedOrigin=self.validLocation.coordinate;self.plannedFromSimulation=NO;}
    if(!self.ownsNavigation){if(!TIONavigationClaim(@"mapkit")){[self alert:@"另一导航正在占用" message:@"请先明确停止已有高德 / MapKit 导航。本页面不会自动抢占或停止其他导航。"];return;}self.ownsNavigation=YES;}
    self.sessionId=NSUUID.UUID.UUIDString;self.mode=@[@"walk",@"ride",@"drive"][self.modeControl.selectedSegmentIndex];self.coordinateSpace=([Space(self.plannedOrigin) isEqual:@"wgs84"]&&[Space(self.destination) isEqual:@"wgs84"])?@"wgs84":@"mapkit-unverified";self.currentCoordinate=self.plannedOrigin;[self requestRouteFrom:self.plannedOrigin rerouting:NO];
}
- (void)requestRouteFrom:(CLLocationCoordinate2D)origin rerouting:(BOOL)rerouting{
    if(!rerouting){self.route=nil;self.progress=nil;self.stepStarts=@[];if(self.routeOverlay)[self.map removeOverlay:self.routeOverlay];self.routeOverlay=nil;[self.roads cancel];}
    [self.directions cancel];NSUInteger generation=++self.generation;self.planning=YES;self.ready=NO;[self cancelSearch];self.note=rerouting?@"偏离路线持续超过 10 秒，向 Apple 请求重新规划…":@"正在向 Apple 规划路线；成功后仍须明确开始。";[self emitPhase:rerouting?@"rerouting":@"planning"];
    MKDirectionsRequest *request=[MKDirectionsRequest new];request.source=[[MKMapItem alloc]initWithPlacemark:[[MKPlacemark alloc]initWithCoordinate:origin]];request.destination=[[MKMapItem alloc]initWithPlacemark:[[MKPlacemark alloc]initWithCoordinate:self.destination]];
    request.transportType=[self.mode isEqual:@"drive"]?MKDirectionsTransportTypeAutomobile:MKDirectionsTransportTypeWalking;
    if([self.mode isEqual:@"ride"]){if(@available(iOS 14.0,*)){request.transportType=MKDirectionsTransportTypeCycling;}else{[self stopWithReason:@"当前系统不支持骑行路线；未替换为步行" phase:@"error"];return;}}
    self.directions=[[MKDirections alloc]initWithRequest:request];__weak typeof(self) weak=self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,45*NSEC_PER_SEC),dispatch_get_main_queue(),^{typeof(self) panel=weak;if(panel&&generation==panel.generation&&panel.planning)[panel stopWithReason:@"Apple 路线请求超过 45 秒，已取消并释放导航占用；请明确重试。" phase:@"error"];});
    [self.directions calculateDirectionsWithCompletionHandler:^(MKDirectionsResponse *response,NSError *error){dispatch_async(dispatch_get_main_queue(),^{
        typeof(self) s=weak;if(!s||generation!=s.generation||!s.ownsNavigation||![s isForeground])return;s.directions=nil;s.planning=NO;
        MKRoute *route=response.routes.firstObject;TIOMapKitProgress *progress=[[TIOMapKitProgress alloc]initWithPoints:PolylinePoints(route.polyline)];
        if(error||!route||!progress){[s stopWithReason:[NSString stringWithFormat:@"Apple 路线不可用%@：%@",[s.mode isEqual:@"ride"]?@"（骑行未降级步行）":@"",error.localizedDescription?:@"缺少有效路线"] phase:@"error"];return;}
        s.route=route;s.progress=progress;if(!rerouting)s.currentCoordinate=Coordinate(progress.points.firstObject);s.plannedOrigin=origin;s.lastProjectionUptime=0;s.simulationDistance=0;s.heading=-1;NSMutableArray *starts=[NSMutableArray new];double total=0;
        for(MKRouteStep *step in route.steps){[starts addObject:@(total)];if(isfinite(step.distance)&&step.distance>=0)total+=step.distance;}
        if(total>0)for(NSUInteger i=0;i<starts.count;i++)starts[i]=@([starts[i] doubleValue]*progress.length/total);s.stepStarts=starts;
        // Any route crossing the conservative exclusion is also unverified.
        for(NSArray *p in progress.points)if(UnverifiedChina(Coordinate(p))){s.coordinateSpace=@"mapkit-unverified";break;}
        if(![s.coordinateSpace isEqual:@"wgs84"]){[s.roads cancel];if(rerouting){[s stopWithReason:@"新路线涉及未验证坐标区域，仅允许重新规划模拟" phase:@"error"];return;}}
        if(s.routeOverlay)[s.map removeOverlay:s.routeOverlay];s.routeOverlay=route.polyline;[s.map addOverlay:s.routeOverlay];[s.map setVisibleMapRect:route.polyline.boundingMapRect edgePadding:UIEdgeInsetsMake(35,35,35,35) animated:YES];
        [s.rerouteGate resetEvidence];s.ready=!rerouting;s.note=rerouting?@"重规划成功，继续前台实地导航（无实时 ETA）。":@"路线已就绪，请明确开始模拟或实地导航。规划耗时仅 Apple 估计，非实时 ETA。";
        if(![s.coordinateSpace isEqual:@"wgs84"])s.note=@"路线已就绪，但中国区域坐标对齐未验证：仅模拟；实地导航和 OSM 道路叠加禁用。";[s emitPhase:rerouting?([s currentFixValid]?@"navigating":@"stale"):@"ready"];
    });}];
}
- (void)startSimulation{
    if(!self.ready||!self.ownsNavigation||![self isForeground])return;self.ready=NO;self.active=YES;self.simulated=YES;self.lastSimulationUptime=Uptime();self.simulationDistance=0;self.currentCoordinate=Coordinate([self.progress coordinateAtDistance:0]);self.note=@"模拟进行中（非实际位置，非实时 ETA）。眼镜 HUD 不会自动启动。";[self.locationManager stopUpdatingLocation];[self emitPhase:@"navigating"];
}
- (void)startLive{
    if(!self.ready||!self.ownsNavigation||![self isForeground])return;
    if(self.plannedFromSimulation||![self.coordinateSpace isEqual:@"wgs84"]||![self currentFixValid]||TIOMapKitDistance(Pair(self.validLocation.coordinate),Pair(self.plannedOrigin))>50){[self alert:@"不能开始实地导航" message:@"需使用有效真实定位起点重新规划；中国区域在坐标对齐验证前仅允许模拟。"];return;}
    self.ready=NO;self.active=YES;self.simulated=NO;self.lastValidUptime=Uptime();self.lastProjectionUptime=0;self.note=@"实地前台导航开始，无实时 ETA。离开页面或进入后台会立即停止。";[self.locationManager startUpdatingLocation];[self applyFix:self.validLocation];
}
- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray<CLLocation *> *)locations{
    if(![self isForeground]||!self.consented)return;BOOL accepted=NO;
    for(CLLocation *fix in locations){double timestamp=fix.timestamp.timeIntervalSince1970;if(timestamp<=self.lastFixTimestamp||!TIOMapKitFixValid(fix.coordinate.latitude,fix.coordinate.longitude,fix.horizontalAccuracy,timestamp,NSDate.date.timeIntervalSince1970))continue;self.lastFixTimestamp=timestamp;self.validLocation=fix;accepted=YES;if(self.active&&!self.simulated)[self applyFix:fix];}
    if(!accepted&&self.active&&!self.simulated){[self.rerouteGate resetEvidence];self.note=@"定位精度或时间无效：暂停路线进度与眼镜 HUD，请确认环境。";if(Uptime()-self.lastValidUptime<=15)[self emitPhase:@"weak"];}
    [self refresh];
}
- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error{
    if(self.active&&!self.simulated){[self.rerouteGate resetEvidence];self.note=[@"定位信号不可用：" stringByAppendingString:error.localizedDescription];[self emitPhase:Uptime()-self.lastValidUptime>15?@"stale":@"weak"];}
}
- (void)updateHeading:(double)heading{
    if(!isfinite(heading)||heading<0){self.heading=-1;return;}
    self.heading=self.heading<0?fmod(heading,360):fmod(self.heading+0.3*remainder(heading-self.heading,360)+360,360);
}
- (void)applyFix:(CLLocation *)fix{
    if(!self.simulated&&UnverifiedChina(fix.coordinate)){self.coordinateSpace=@"mapkit-unverified";[self stopWithReason:@"真实定位进入坐标未验证区域：已停止实地导航与 HUD。中国区域仅允许明确重新规划模拟。" phase:@"error"];return;}
    double now=Uptime(),elapsed=self.lastProjectionUptime?fmin(15,fmax(0,now-self.lastProjectionUptime)):1;self.lastValidUptime=now;self.currentCoordinate=fix.coordinate;[self updateHeading:(fix.speed>=0.7&&fix.courseAccuracy>=0&&fix.courseAccuracy<=45)?fix.course:-1];
    if(self.planning)return;
    double speed=isfinite(fix.speed)&&fix.speed>=0?fmin(55,fix.speed):([self.mode isEqual:@"drive"]?20:5);NSDictionary *projection=[self.progress projectLatitude:fix.coordinate.latitude longitude:fix.coordinate.longitude forwardWindow:fmax(80,fmin(1000,speed*elapsed+fix.horizontalAccuracy+30))];self.lastProjectionUptime=now;
    if(!projection){[self emitPhase:@"weak"];return;}double off=[projection[@"distance"] doubleValue];
    if([self.rerouteGate observeDistance:off valid:YES uptime:now]){[self.rerouteGate didRequestAt:now];[self requestRouteFrom:fix.coordinate rerouting:YES];return;}
    if(off<=30&&self.progress.length-self.progress.progress<=15){[self arrive];return;}
    if([self.coordinateSpace isEqual:@"wgs84"])[self.roads updateCoordinate:self.currentCoordinate mode:self.mode];
    self.note=off>50?@"偏离路线，等待至少 3 个有效定位且持续 10 秒后重规划。":@"前台定位导航中；不制造道路名称或实时 ETA。";[self emitPhase:off>50?@"weak":@"navigating"];
}
- (void)tick{
    if(![self isForeground])return;double now=Uptime();
    if(self.active&&self.simulated){double speed=[self.mode isEqual:@"drive"]?12:([self.mode isEqual:@"ride"]?4.5:1.4);self.simulationDistance=fmin(self.progress.length,self.simulationDistance+speed*fmin(2,fmax(0,now-self.lastSimulationUptime)));self.lastSimulationUptime=now;NSArray *p=[self.progress coordinateAtDistance:self.simulationDistance],*q=[self.progress coordinateAtDistance:fmin(self.progress.length,self.simulationDistance+5)];self.currentCoordinate=Coordinate(p);[self updateHeading:TIOMapKitBearing(p,q)];[self.progress projectLatitude:self.currentCoordinate.latitude longitude:self.currentCoordinate.longitude forwardWindow:80];if(self.simulationDistance>=self.progress.length){[self arrive];return;}if([self.coordinateSpace isEqual:@"wgs84"])[self.roads updateCoordinate:self.currentCoordinate mode:self.mode];[self emitPhase:@"navigating"];}
    else if(self.active&&!self.simulated&&(now-self.lastValidUptime>15||![self currentFixValid])){[self.rerouteGate resetEvidence];self.heading=-1;self.note=@"定位超过 15 秒未更新：已暂停路线进度与重规划，请停下确认环境。";[self emitPhase:@"stale"];}
    else if(self.active&&self.frame)[self offerHUD];[self refresh];
}
- (void)arrive{
    self.note=self.simulated?@"模拟到达终点。导航与 HUD 已停止。":@"已到达终点。导航与 HUD 已停止。";[self stopWithReason:self.note phase:@"arrived"];
}
- (void)background{[self stopWithReason:@"仅前台：进入后台已停止导航和 HUD" phase:@"stopped"] ;}
- (void)stopUser{[self stopWithReason:@"用户已停止导航与 HUD" phase:@"stopped"] ;}
- (void)stopWithReason:(NSString *)reason phase:(NSString *)phase{
    self.generation++;[self.directions cancel];self.directions=nil;[self cancelSearch];self.active=NO;self.ready=NO;self.planning=NO;[self.locationManager stopUpdatingLocation];self.map.showsUserLocation=NO;[self.roads cancel];[self.rerouteGate resetEvidence];
    if(self.ownsNavigation){TIOMapKitHUDStop(reason);TIONavigationRelease(@"mapkit");self.ownsNavigation=NO;}self.hudRequested=NO;self.heading=-1;self.note=reason;[self emitPhase:phase];
}
- (void)emitPhase:(NSString *)phase{
    if(self.hudRequested&&([phase isEqual:@"weak"]||[phase isEqual:@"stale"]||[phase isEqual:@"rerouting"])){if(self.ownsNavigation)TIOMapKitHUDStop(@"定位不可靠或重规划：停止 HUD，不自动恢复");self.hudRequested=NO;}
    BOOL tracking=([phase isEqual:@"navigating"]||[phase isEqual:@"weak"])&&self.active;double along=self.simulated?self.simulationDistance:self.progress.progress;double remaining=tracking?fmax(0,self.progress.length-along):-1,turnDistance=-1;NSString *turn=@"unknown",*instruction=@"";
    if(tracking){NSUInteger stepIndex=NSNotFound;for(NSUInteger i=0;i<self.stepStarts.count;i++)if([self.stepStarts[i] doubleValue]>along+5){stepIndex=i;break;}
        if(stepIndex!=NSNotFound&&stepIndex<self.route.steps.count){MKRouteStep *step=self.route.steps[stepIndex];instruction=step.instructions?:@"";turnDistance=fmax(0,[self.stepStarts[stepIndex] doubleValue]-along);double angle=NAN;NSArray *next=PolylinePoints(step.polyline);NSArray *prev=stepIndex?PolylinePoints(self.route.steps[stepIndex-1].polyline):@[];
            if(prev.count>=2&&next.count>=2){double a=TIOMapKitBearing(prev[prev.count-2],prev.lastObject),b=TIOMapKitBearing(next[0],next[1]);if(a>=0&&b>=0)angle=remainder(b-a,360);}turn=TIOMapKitTurn(instruction,angle);
        }else instruction=@"继续沿 Apple 路线到终点；请确认现场环境";
    }else if([phase isEqual:@"arrived"]){turn=@"arrive";instruction=@"已到达";remaining=0;turnDistance=0;}else instruction=self.note?:@"";
    NSArray *segments=@[];if(tracking&&[self.coordinateSpace isEqual:@"wgs84"])segments=[self.roads segments]?:@[];
    self.frame=@{@"sessionId":self.sessionId?:NSUUID.UUID.UUIDString,@"revision":@(++self.revision),@"provider":@"mapkit",@"mode":self.mode?:@"walk",@"phase":phase,@"turn":turn,@"instruction":instruction,@"road":@"",@"distance_m":@(turnDistance),@"remaining_m":@(remaining),@"remaining_s":@(-1),@"updatedAt":@(Uptime()),@"latitude":@(self.currentCoordinate.latitude),@"longitude":@(self.currentCoordinate.longitude),@"heading":@(tracking?self.heading:-1),@"routePoints":self.progress?[self.progress pointsAroundDistance:along]:@[],@"roadSegments":segments,@"coordinateSpace":self.coordinateSpace?:@"mapkit-unverified"};
    NSData *pixels=TIOMapKitRenderPixels(self.frame);if(pixels.length==65536)self.preview.image=TIOMapKitPixelsPreview(pixels);[self offerHUD];[self refresh];
}
- (void)offerHUD{
    if(!self.hudRequested||!self.ownsNavigation||!self.active||![self isForeground]||Uptime()-self.lastHUDOffer<1)return;self.lastHUDOffer=Uptime();NSData *pixels=TIOMapKitRenderPixels(self.frame);if(pixels.length==65536)TIOMapKitHUDOffer(self.frame,pixels);
}
- (void)toggleHUD{
    if(self.hudRequested){if(self.ownsNavigation)TIOMapKitHUDStop(@"用户停止眼镜 HUD");self.hudRequested=NO;[self refresh];return;}
#if TIO_MAPKIT_HUD_ENABLED
    if(!self.active||!self.ownsNavigation||![self isForeground]||![self.frame[@"phase"] isEqual:@"navigating"]){[self alert:@"需要可靠前台导航" message:@"眼镜 HUD 只可在当前导航可靠、非重规划时明确启动；弱信号、过期定位会停止 HUD，恢复后也不自动重启。"];return;}
    UIAlertController *a=[UIAlertController alertControllerWithTitle:@"确认眼镜空闲" message:@"请先停止眼镜录音、语音、字幕、提词器和原生导航等任务。主机无法可靠确认录音或语音是否运行。发送会生成含导航像素的私有临时文件，回执未知时保留。确认空闲后才会申请 HUD；收到 ACK 前仍无法保证镜片显示。" preferredStyle:UIAlertControllerStyleAlert];[a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    NSUInteger generation=self.generation;__weak typeof(self) weak=self;[a addAction:[UIAlertAction actionWithTitle:@"已停止其他任务，确认空闲" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action){(void)action;typeof(self) s=weak;if(!s||generation!=s.generation||!s.active||!s.ownsNavigation||![s isForeground]||![s.frame[@"phase"] isEqual:@"navigating"])return;NSData *pixels=TIOMapKitRenderPixels(s.frame);if(pixels.length==65536&&TIOMapKitHUDConfirmIdle()){s.hudRequested=TIOMapKitHUDStart(s.frame,pixels);s.lastHUDOffer=Uptime();}[s refresh];}]];if(!self.presentedViewController)[self presentViewController:a animated:YES completion:nil];
#else
    [self alert:@"眼镜 HUD 不可用" message:@"此构建未启用 MapKit HUD。可在手机查看灰度预览。"];
#endif
}
- (void)refresh{
    self.noteLabel.text=self.note;BOOL busy=self.active||self.planning;self.planButton.enabled=self.consented&&self.hasDestination&&!busy;self.simulateButton.enabled=self.ready;self.liveButton.enabled=self.ready&&!self.plannedFromSimulation&&[self.coordinateSpace isEqual:@"wgs84"];self.modeControl.enabled=!busy;self.originControl.enabled=!busy;self.pickControl.enabled=!busy;self.searchBar.userInteractionEnabled=self.consented&&!busy;
    NSString *summary=@"选择终点和起点后先规划。";if(self.route&&self.ready)summary=[NSString stringWithFormat:@"Apple 规划：%.1f 公里 · 约 %.0f 分钟（规划估计，非实时 ETA）",self.route.distance/1000,self.route.expectedTravelTime/60];else if(self.active)summary=[NSString stringWithFormat:@"%@ · %@\n%@\n剩余：%@ · 实时 ETA：未知",self.simulated?@"模拟":@"实地",self.frame[@"phase"],self.frame[@"instruction"]?:@"",[self.frame[@"remaining_m"] doubleValue]>=0?[NSString stringWithFormat:@"%.0f 米",[self.frame[@"remaining_m"] doubleValue]]:@"未知"];self.routeLabel.text=summary;
    NSDictionary *status=TIOMapKitHUDStatus();if(self.hudRequested&&![status[@"active"] boolValue])self.hudRequested=NO;self.hudLabel.text=[NSString stringWithFormat:@"眼镜 HUD：%@ / %@ / %@\n%@\n%@\nACK 前无法保证镜片显示。",[status[@"enabled"] boolValue]?@"已启用":@"本构建不可用",[status[@"active"] boolValue]?@"活动":@"停止",[status[@"ready"] boolValue]?@"已确认就绪":@"未确认",status[@"note"]?:@"",[status[@"busy"] boolValue]?@"主机发现占用":@"主机未发现占用（不等于眼镜空闲）"];[self.hudButton setTitle:self.hudRequested?@"停止眼镜 HUD":@"启动眼镜 HUD" forState:UIControlStateNormal];self.hudButton.enabled=[status[@"enabled"] boolValue]&&(self.active||self.hudRequested);
}
- (MKOverlayRenderer *)mapView:(MKMapView *)map rendererForOverlay:(id<MKOverlay>)overlay{
    if([overlay isKindOfClass:MKPolyline.class]){MKPolylineRenderer *r=[[MKPolylineRenderer alloc]initWithPolyline:(MKPolyline *)overlay];r.strokeColor=UIColor.systemBlueColor;r.lineWidth=5;return r;}return [[MKOverlayRenderer alloc]initWithOverlay:overlay];
}
@end
UIViewController *TIOMapKitNavigationController(void){return [TIOMapKitNavigationPanel new];}
