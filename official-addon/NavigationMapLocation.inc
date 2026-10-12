// One-shot map centering, separate from navigation route/GPS lifetime.
- (BOOL)mapLocationVisible{return self.view.window&&UIApplication.sharedApplication.applicationState==UIApplicationStateActive;}
- (void)cancelMapLocation{
    self.locationGeneration++;self.locating=NO;self.awaitingMapLocationAuthorization=NO;
    if(!self.active)self.map.showsUserLocation=NO;
}
- (void)expireMapLocation:(NSUInteger)epoch{
    if(!self.locating||epoch!=self.locationGeneration)return;
    [self cancelMapLocation];self.note=@"暂未获得可靠位置，请到开阔处或点击“我的位置”重试";[self refresh];
}
- (void)beginMapLocation{
    if(!self.initialized||![self mapLocationVisible]||self.locating||self.awaitingMapLocationAuthorization)return;
    CLAuthorizationStatus auth=self.permission.authorizationStatus;
    if(auth==kCLAuthorizationStatusNotDetermined){
        self.awaitingMapLocationAuthorization=YES;self.locationGeneration++;
        self.note=@"允许使用期间定位后自动居中到我的位置；尚未开始导航";[self refresh];
        [self.permission requestWhenInUseAuthorization];return;
    }
    if(auth!=kCLAuthorizationStatusAuthorizedAlways&&auth!=kCLAuthorizationStatusAuthorizedWhenInUse){
        [self cancelMapLocation];self.note=@"定位未授权，请在系统设置允许定位；地图仍可浏览和选点";[self refresh];return;
    }
    self.locating=YES;NSUInteger epoch=++self.locationGeneration;self.map.showsUserLocation=YES;
    self.note=@"正在定位到我的位置，最多等待15秒…";[self refresh];
    __weak typeof(self) weak=self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,15*NSEC_PER_SEC),dispatch_get_main_queue(),^{[weak expireMapLocation:epoch];});
}
- (void)locateMapOnEntry:(NSUInteger)attempt{
    if(self.autoLocateAttempted||!self.initialized||self.active||self.planning||![self mapLocationVisible])return;
    if(self.presentedViewController){if(attempt<15){__weak typeof(self) weak=self;dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/10),dispatch_get_main_queue(),^{[weak locateMapOnEntry:attempt+1];});}return;}
    self.autoLocateAttempted=YES;[self beginMapLocation];
}
- (void)resumeMapLocationAuthorization{
    if(!self.awaitingMapLocationAuthorization)return;
    CLAuthorizationStatus auth=self.permission.authorizationStatus;if(auth==kCLAuthorizationStatusNotDetermined)return;
    if(auth==kCLAuthorizationStatusDenied||auth==kCLAuthorizationStatusRestricted){
        [self cancelMapLocation];self.note=@"定位授权被拒绝，未开启位置更新；可在系统设置允许后点击“我的位置”";[self refresh];return;
    }
    if(![self mapLocationVisible]||self.active)return;
    self.awaitingMapLocationAuthorization=NO;[self beginMapLocation];
}
- (BOOL)acceptMapLocation:(CLLocation *)location coordinate:(CLLocationCoordinate2D)c{
    if(!self.locating||!location||![self mapLocationVisible])return NO;
    CLAuthorizationStatus auth=self.permission.authorizationStatus;
    if(auth!=kCLAuthorizationStatusAuthorizedAlways&&auth!=kCLAuthorizationStatusAuthorizedWhenInUse){[self cancelMapLocation];return NO;}
    double accuracy=location.horizontalAccuracy,age=location.timestamp.timeIntervalSinceNow;
    if(!isfinite(accuracy)||accuracy<0||accuracy>200||!isfinite(age)||fabs(age)>15||!TIONavCoordinate(c.latitude,c.longitude))return NO;
    self.locating=NO;self.locationGeneration++;[self.map setCenterCoordinate:c animated:YES];
    if(!self.active)self.map.showsUserLocation=NO;
    self.note=self.active?@"地图已居中到当前位置，当前路线继续":@"地图已定位到我的位置；选择终点后规划，尚未开始导航";
    [self refresh];return YES;
}
