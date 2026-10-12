// Route-scoped sessions may remain only while the real route permits it.
static void HandleSubtitleLifecycle(NSString *name){
    if([name isEqual:UIApplicationWillResignActiveNotification]){
        ConfirmedIdle=NO;ForegroundSuppressed=!RouteCanContinue();
        if(Trial.navigation&&[@[@"starting",@"ready"] containsObject:Trial.phase]){Trial.note=RouteCanContinue()?@"真实路线继续推送，受后台定位与会话权限约束":@"系统通知暂时遮挡，暂停文字发送；保留本次导航会话";Save();}
        else [Trial stop:@"手机离开前台" now:Now()];
    }else if([name isEqual:UIApplicationDidBecomeActiveNotification]){
        ForegroundSuppressed=NO;
        if(Trial.navigation&&[@[@"starting",@"ready"] containsObject:Trial.phase]){SubtitleRefreshEpoch++;Trial.note=@"已回前台，同会话继续；不重置计时或新建SID";Save();}
    }else if([name isEqual:UIApplicationDidEnterBackgroundNotification]){
        ConfirmedIdle=NO;
        if(RouteCanContinue()&&[@[@"starting",@"ready"] containsObject:Trial.phase]){ForegroundSuppressed=NO;Trial.note=@"真实路线后台文字推送中（系统允许的定位/蓝牙回调期间）";Save();}
        else{ForegroundSuppressed=YES;[Trial stop:@"后台路线条件不满足，退出字幕" now:Now()];}
    }else if([name isEqual:@"TIOResearchClosed"]){
        ConfirmedIdle=NO;
        if(Trial.routeScoped&&TIONavigationRouteSession.shared.running){Trial.note=@"已离开页面；真实路线继续，可重进导航页结束";Save();}
        else{ForegroundSuppressed=YES;[Trial stop:@"研究页面关闭" now:Now()];}
    }
}
