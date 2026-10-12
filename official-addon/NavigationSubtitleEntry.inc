// Production page policy, shared verbatim with the Foundation harness.
- (NSDictionary *)subtitleFrame{
    if(!self.active||!self.navigationStarted||self.routeReady||self.fixture||self.planning)return nil;
    NSString *mode=self.simulated?@"模拟导航 · 非实际定位":@"实时导航 · 高德";
    if(![self.display[@"mode"] isEqual:mode])return nil;
    if([@[@"arrived",@"error",@"stopped",@"planning"] containsObject:self.display[@"phase"]?:@""])return nil;
    NSTimeInterval now=NSProcessInfo.processInfo.systemUptime;BOOL fresh=isfinite(self.lastInfo)&&self.lastInfo>0&&now>=self.lastInfo&&now-self.lastInfo<=15;
    if(self.simulated)return !self.gpsWeak&&!self.rerouting&&fresh&&TIONavSubtitleText(self.display)?self.display:nil;
    if(self.rerouting)return TIONavSubtitleHoldingFrame(@"rerouting");
    if(self.gpsWeak)return TIONavSubtitleHoldingFrame(@"weak");
    if(TIONavSubtitleIsHolding(self.display))return self.display;
    if(!fresh)return TIONavSubtitleHoldingFrame(self.lastInfo>0?@"stale":@"waiting");
    return TIONavSubtitleText(self.display)?self.display:TIONavSubtitleHoldingFrame(@"invalid");
}
- (NSString *)subtitleBlockReason{
    if(UIApplication.sharedApplication.applicationState!=UIApplicationStateActive)return @"系统通知遮挡或手机不在前台";
    if(!self.active||!self.navigationStarted||self.routeReady)return @"请先点击开始导航；规划完成不等于已开始";
    if(self.fixture)return @"离线夹具不启动眼镜字幕";
    if(self.planning)return @"正在规划，尚未开始导航";
    if(![self subtitleFrame])return @"导航已结束、模拟数据过期或会话模式不匹配";
    NSDictionary *transport=TIONavTransportStatus();
    if([TIONewsTeleStatus()[@"active"] boolValue]||[self.teleHUD.status[@"enabled"] boolValue])return @"请先结束提词显示";
    if([transport[@"enabled"] boolValue]||[transport[@"pending"] boolValue]||[transport[@"noticePending"] boolValue]||[transport[@"notices"] boolValue])return @"请先结束导航卡或自动通知通道";
    if([TIOSubtitleNavigationStatus()[@"available"] isEqual:@NO])return @"当前字幕连接或配置尚未就绪";
    return nil;
}
- (BOOL)canStartSubtitle{return [self subtitleBlockReason]==nil;}
- (NSDictionary *)subtitleGateDiagnostic{
    NSTimeInterval now=NSProcessInfo.processInfo.systemUptime;NSDictionary *frame=[self subtitleFrame];
    return @{@"version":@"amap-subtitle-gate-v4-route",@"routeScoped":@(!self.simulated&&self.navigationStarted),@"blocked":[self subtitleBlockReason]?:@"可开启（等待状态不提供转向/距离）",@"active":@(self.active),@"started":@(self.navigationStarted),@"routeReady":@(self.routeReady),@"simulated":@(self.simulated),@"planning":@(self.planning),@"rerouting":@(self.rerouting),@"gpsWeak":@(self.gpsWeak),@"framePhase":frame[@"phase"]?:@"none",@"validInfoAge":@(isfinite(self.lastInfo)&&self.lastInfo>0&&now>=self.lastInfo?now-self.lastInfo:-1),@"rawInfoAge":@(self.lastSDKInfo>0&&now>=self.lastSDKInfo?now-self.lastSDKInfo:-1),@"infoCallbacks":@(self.infoCallbacks),@"gpsStrength":@(self.lastGPSStrength)};
}
