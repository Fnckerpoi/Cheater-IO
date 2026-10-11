// Keep only an existing navigation SID across transient inactive notifications.
static void HandleSubtitleLifecycle(NSString *name){
    if([name isEqual:UIApplicationWillResignActiveNotification]){
        ConfirmedIdle=NO;ForegroundSuppressed=YES;
        if(Trial.navigation&&[@[@"starting",@"ready"] containsObject:Trial.phase]){Trial.note=@"系统通知暂时遮挡，暂停文字发送；保留本次导航会话";Save();}
        else [Trial stop:@"手机离开前台" now:Now()];
    }else if([name isEqual:UIApplicationDidBecomeActiveNotification]){
        ForegroundSuppressed=NO;
        if(Trial.navigation&&[@[@"starting",@"ready"] containsObject:Trial.phase]){SubtitleRefreshEpoch++;Trial.note=@"已回前台，请求同会话续显；不重置计时或新建SID";Save();}
    }else if([name isEqual:UIApplicationDidEnterBackgroundNotification]||[name isEqual:@"TIOResearchClosed"]){
        ConfirmedIdle=NO;ForegroundSuppressed=YES;[Trial stop:@"手机进入后台或研究页面关闭" now:Now()];
    }
}
