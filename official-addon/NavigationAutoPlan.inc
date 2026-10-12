// Latest explicit endpoint/mode intent only: calculate, never start navigation.
- (void)cancelAutoPlan{self.autoPlanEpoch++;self.autoPlanPending=NO;self.autoPlanWaitingAuthorization=NO;}
- (void)scheduleAutoPlan{
#if TIO_AMAP_ENABLED || TIO_AUTO_PLAN_TESTS
    if(self.navigationStarted||self.fixture||!self.hasDestination)return;
    self.autoPlanPending=YES;NSUInteger epoch=++self.autoPlanEpoch;
    self.note=@"终点或出行方式已更新，准备自动规划…";[self refresh];
    __weak typeof(self) weak=self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/4),dispatch_get_main_queue(),^{[weak drainAutoPlan:epoch attempt:0];});
#endif
}
- (void)drainAutoPlan:(NSUInteger)epoch attempt:(NSUInteger)attempt{
#if TIO_AMAP_ENABLED || TIO_AUTO_PLAN_TESTS
    if(epoch!=self.autoPlanEpoch||!self.autoPlanPending||self.navigationStarted||self.fixture||!self.hasDestination)return;
    if(self.planning||self.active){return;} // Do not submit a second request into an engine that is still working.
    if(!self.view.window||UIApplication.sharedApplication.applicationState!=UIApplicationStateActive)return;
    if(!self.initialized){self.note=@"等待地图与Key/隐私设置就绪后自动规划";[self refresh];return;}
    if(self.presentedViewController||self.retiringManagerClass){
        if(attempt<40){__weak typeof(self) weak=self;dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/10),dispatch_get_main_queue(),^{[weak drainAutoPlan:epoch attempt:attempt+1];});}
        else{[self cancelAutoPlan];self.note=@"弹层或旧引擎未就绪，本次未算路；选择终点或切换方式可重试";[self refresh];}return;
    }
    CLAuthorizationStatus auth=self.permission.authorizationStatus;
    if(auth==kCLAuthorizationStatusNotDetermined){
        if(!self.autoPlanWaitingAuthorization){self.autoPlanWaitingAuthorization=YES;self.note=@"允许使用期间定位后自动规划当前终点";[self refresh];if(!self.awaitingMapLocationAuthorization)[self.permission requestWhenInUseAuthorization];}return;
    }
    if(auth!=kCLAuthorizationStatusAuthorizedWhenInUse&&auth!=kCLAuthorizationStatusAuthorizedAlways){[self cancelAutoPlan];self.note=@"定位未授权，未自动规划；系统设置允许后选择终点可重试";[self refresh];return;}
    self.autoPlanPending=NO;self.autoPlanWaitingAuthorization=NO;
    [self startWalking]; // Calculate only; begin remains a separate user action.
#endif
}
- (void)resumeAutoPlan{
    if(!self.autoPlanPending)return;NSUInteger epoch=self.autoPlanEpoch;[self drainAutoPlan:epoch attempt:0];
}
