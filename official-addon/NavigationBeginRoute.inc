// Production SDK start path, shared with Foundation doubles for ordering tests.
- (void)beginApprovedRoute{
#if TIO_AMAP_ENABLED
    if(!self.routeReady||!self.manager||self.planning||!self.lensStartApproved)return;
    if(UIApplication.sharedApplication.applicationState!=UIApplicationStateActive){[self alert:@"请在前台开始导航" message:@"后台不会自动开始新的定位会话。"];return;}
    if(!self.simulated&&![self navigationLocationAuthorized]){[self alert:@"需要定位权限" message:@"请允许使用期间定位后重试；不自动申请始终定位。"];return;}
    self.backgroundLocationEnabled=!self.simulated&&[self navigationLocationModeAvailable]&&[self navigationLocationAuthorized];
    self.manager.allowsBackgroundLocationUpdates=self.backgroundLocationEnabled;
    self.manager.pausesLocationUpdatesAutomatically=!self.backgroundLocationEnabled;
    BOOL ok=self.simulated?[self.manager startEmulatorNavi]:[self.manager startGPSNavi];if(!ok){[self fail:@"导航启动失败，可停止后重新规划"];return;}self.routeReady=NO;self.navigationStarted=YES;if(![self retainLiveRoute]){[self fail:@"路线持有失败，已安全停止"];return;}self.lastInfo=0;self.staleShown=NO;if(!self.simulated)[self setFrame:TIONavSubtitleHoldingFrame(@"waiting")];self.note=self.simulated?@"模拟已开始；可单独开启眼镜显示，不会自动抢占眼镜。":(self.backgroundLocationEnabled?@"实时导航已开始，可退后台或锁屏；不要强退App。请以手机地图及实际路况为准。":@"实时导航已开始，但宿主缺少location后台模式，只能前台运行。");[self startAutomaticDisplay];[self refresh];
#endif
}
