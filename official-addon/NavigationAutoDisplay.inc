// Invoked once, only after this foreground start action succeeds.
- (void)startAutomaticDisplay{
    if(self.autoDisplayAttempted||!self.navigationStarted||self.fixture)return;
    if(self.simulated&&![self subtitleFrame])return; // First simulated SDK callback arms the one-shot attempt.
    self.autoDisplayAttempted=YES;
    if(!self.lensStartApproved||UIApplication.sharedApplication.applicationState!=UIApplicationStateActive){self.note=@"自动显示未开启：本次未确认或已离开前台";return;}
    NSDictionary *state=TIOSubtitleNavigationStatus();
    NSString *phase=state[@"phase"]?:@"idle";
    BOOL endedOwnRoute=[state[@"navigation"] boolValue]&&[state[@"routeScoped"] boolValue]&&[@[@"stopping",@"uncertain"] containsObject:phase];
    if(![state[@"available"] boolValue]||[state[@"pending"] boolValue]||(![phase isEqual:@"idle"]&&!endedOwnRoute)){self.note=@"自动显示未开启：字幕通道不空闲/连接未就绪，手机导航继续；请手动检查";return;}
    if([self subtitleBlockReason]||!TIOSubtitleConfirmIdle()){self.note=@"自动显示未开启：其他显示占用或状态变化，手机导航继续";return;}
    NSDictionary *frame=[self subtitleFrame];
    BOOL ok=self.simulated?[self.subtitleHUD startWithFrame:frame at:NSProcessInfo.processInfo.systemUptime]:[self.subtitleHUD startRouteWithFrame:frame token:self.routeToken at:NSProcessInfo.processInfo.systemUptime];
    self.note=ok?@"已自动请求眼镜显示，等待同SID设置回执；非镜片渲染确认":@"自动显示提交失败，不循环重试；手机导航继续";
}
