// Native navigation workspace. Included inside TIONavigationPanel implementation.
- (UILabel *)navLabel:(NSString *)text size:(CGFloat)size weight:(UIFontWeight)weight{
    UILabel *l=[UILabel new];l.text=text;l.numberOfLines=0;l.font=[UIFont systemFontOfSize:size weight:weight];l.adjustsFontForContentSizeCategory=YES;return l;
}
- (UIStackView *)navCard{
    UIStackView *s=[UIStackView new];s.axis=UILayoutConstraintAxisVertical;s.spacing=12;s.layoutMargins=UIEdgeInsetsMake(16,16,16,16);s.layoutMarginsRelativeArrangement=YES;s.backgroundColor=UIColor.secondarySystemGroupedBackgroundColor;s.layer.cornerRadius=20;[self.stack addArrangedSubview:s];return s;
}
- (UIButton *)navAction:(NSString *)title icon:(NSString *)icon selector:(SEL)sel primary:(BOOL)primary{
    UIButton *b=[UIButton buttonWithType:UIButtonTypeSystem];UIButtonConfiguration *c=primary?[UIButtonConfiguration filledButtonConfiguration]:[UIButtonConfiguration tintedButtonConfiguration];c.title=title;c.image=[UIImage systemImageNamed:icon withConfiguration:[UIImageSymbolConfiguration configurationWithPointSize:17 weight:UIImageSymbolWeightMedium]];c.imagePadding=6;c.baseBackgroundColor=UIColor.systemTealColor;c.baseForegroundColor=primary?UIColor.whiteColor:UIColor.systemTealColor;c.cornerStyle=UIButtonConfigurationCornerStyleLarge;c.contentInsets=NSDirectionalEdgeInsetsMake(12,8,12,8);c.titleTextAttributesTransformer=^NSDictionary *(NSDictionary *a){NSMutableDictionary *m=[a mutableCopy];m[NSFontAttributeName]=[UIFont preferredFontForTextStyle:UIFontTextStyleSubheadline];return m;};b.configuration=c;[b addTarget:self action:sel forControlEvents:UIControlEventTouchUpInside];[b.heightAnchor constraintGreaterThanOrEqualToConstant:48].active=YES;return b;
}
- (void)buildWorkspace{
    for(UIView *v in self.stack.arrangedSubviews.copy){[self.stack removeArrangedSubview:v];[v removeFromSuperview];}
    self.title=@"出行导航";self.note=@"搜索地点或单击地图选终点；以手机当前位置为起点。选择终点或切换出行方式后自动规划；路线就绪再开始并自动显示到眼镜。";self.stack.spacing=14;self.scroll.keyboardDismissMode=UIScrollViewKeyboardDismissModeOnDrag;self.scroll.delaysContentTouches=NO;
    self.navigationItem.rightBarButtonItem=[[UIBarButtonItem alloc]initWithImage:[UIImage systemImageNamed:@"ellipsis.circle"] style:UIBarButtonItemStylePlain target:self action:@selector(navigationMore)];self.navigationItem.rightBarButtonItem.accessibilityIdentifier=@"navigation-more";
    UILabel *eyebrow=[self navLabel:@"TURBO IO  /  实时出行导航" size:12 weight:UIFontWeightSemibold];eyebrow.textColor=UIColor.secondaryLabelColor;[self.stack addArrangedSubview:eyebrow];
    self.searchButton=[self navAction:@"去哪里？搜索地点或地址" icon:@"magnifyingglass" selector:@selector(searchPlaces) primary:NO];self.searchButton.accessibilityIdentifier=@"navigation-search";[self.stack addArrangedSubview:self.searchButton];
    self.transportMode=[[UISegmentedControl alloc]initWithItems:TIONavigationModeTitles()];self.transportMode.selectedSegmentIndex=TIONavigationWalk;self.transportMode.accessibilityIdentifier=@"navigation-transport-mode";[self.transportMode addTarget:self action:@selector(transportModeChanged) forControlEvents:UIControlEventValueChanged];[self.stack addArrangedSubview:self.transportMode];
    self.mapPickRole=[[UISegmentedControl alloc]initWithItems:@[@"选终点",@"选模拟起点"]];self.mapPickRole.selectedSegmentIndex=0;self.mapPickRole.accessibilityIdentifier=@"navigation-pick-role";[self.mapPickRole addTarget:self action:@selector(pickRoleChanged) forControlEvents:UIControlEventValueChanged];self.mapPickRole.hidden=YES;
    self.mapHost=[UIView new];self.mapHost.backgroundColor=UIColor.secondarySystemGroupedBackgroundColor;self.mapHost.layer.cornerRadius=22;self.mapHost.clipsToBounds=YES;self.mapHeight=[self.mapHost.heightAnchor constraintEqualToConstant:240];self.mapHeight.active=YES;[self.stack addArrangedSubview:self.mapHost];
    self.mapHint=[self navLabel:@"地图尚未开启\n\n高德地图与地点搜索需要网络\n同意隐私说明后加载" size:16 weight:UIFontWeightMedium];self.mapHint.textAlignment=NSTextAlignmentCenter;self.mapHint.textColor=UIColor.secondaryLabelColor;self.mapHint.translatesAutoresizingMaskIntoConstraints=NO;[self.mapHost addSubview:self.mapHint];[NSLayoutConstraint activateConstraints:@[[self.mapHint.leadingAnchor constraintEqualToAnchor:self.mapHost.leadingAnchor constant:24],[self.mapHint.trailingAnchor constraintEqualToAnchor:self.mapHost.trailingAnchor constant:-24],[self.mapHint.centerYAnchor constraintEqualToAnchor:self.mapHost.centerYAnchor]]];
    self.mapCrosshair=[[UIImageView alloc]initWithImage:[UIImage systemImageNamed:@"scope"]];self.mapCrosshair.tintColor=UIColor.systemTealColor;self.mapCrosshair.translatesAutoresizingMaskIntoConstraints=NO;[self.mapHost addSubview:self.mapCrosshair];self.originHint=[self navLabel:@"  单击地图选终点  " size:11 weight:UIFontWeightSemibold];self.originHint.backgroundColor=UIColor.secondarySystemGroupedBackgroundColor;self.originHint.layer.cornerRadius=8;self.originHint.clipsToBounds=YES;self.originHint.translatesAutoresizingMaskIntoConstraints=NO;[self.mapHost addSubview:self.originHint];[NSLayoutConstraint activateConstraints:@[[self.mapCrosshair.centerXAnchor constraintEqualToAnchor:self.mapHost.centerXAnchor],[self.mapCrosshair.centerYAnchor constraintEqualToAnchor:self.mapHost.centerYAnchor],[self.mapCrosshair.widthAnchor constraintEqualToConstant:24],[self.mapCrosshair.heightAnchor constraintEqualToConstant:24],[self.originHint.centerXAnchor constraintEqualToAnchor:self.mapHost.centerXAnchor],[self.originHint.topAnchor constraintEqualToAnchor:self.mapHost.topAnchor constant:8]]];
    UIStackView *mapActions=[[UIStackView alloc]initWithArrangedSubviews:@[[self navAction:@"我的位置" icon:@"location" selector:@selector(locateMe) primary:NO],[self navAction:@"全览" icon:@"arrow.up.left.and.arrow.down.right" selector:@selector(routeOverview) primary:NO]]];mapActions.axis=UILayoutConstraintAxisHorizontal;mapActions.spacing=6;mapActions.distribution=UIStackViewDistributionFillEqually;[self.stack addArrangedSubview:mapActions];
    self.centerButton=[self navAction:@"将地图中心设为终点" icon:@"mappin.and.ellipse" selector:@selector(selectMapCenter) primary:NO];self.centerButton.accessibilityIdentifier=@"navigation-select-center";[self.stack addArrangedSubview:self.centerButton];
    self.mapCrosshair.image=[UIImage systemImageNamed:@"scope"];self.mapCrosshair.isAccessibilityElement=NO;
    self.zoomInButton=[self mapZoomButton:@"plus" action:@selector(zoomIn) label:@"放大地图" identifier:@"navigation-zoom-in"];
    self.zoomOutButton=[self mapZoomButton:@"minus" action:@selector(zoomOut) label:@"缩小地图" identifier:@"navigation-zoom-out"];
    UIStackView *zoom=[[UIStackView alloc]initWithArrangedSubviews:@[self.zoomInButton,self.zoomOutButton]];zoom.axis=UILayoutConstraintAxisVertical;zoom.spacing=2;zoom.translatesAutoresizingMaskIntoConstraints=NO;[self.mapHost addSubview:zoom];[NSLayoutConstraint activateConstraints:@[[zoom.trailingAnchor constraintEqualToAnchor:self.mapHost.trailingAnchor constant:-8],[zoom.bottomAnchor constraintEqualToAnchor:self.mapHost.bottomAnchor constant:-28]]];
    self.routeCard=[self navCard];self.destinationLabel=[self navLabel:@"选择你的目的地" size:21 weight:UIFontWeightSemibold];self.destinationLabel.accessibilityIdentifier=@"navigation-destination";[self.routeCard addArrangedSubview:self.destinationLabel];
    self.routeSummary=[self navLabel:@"单击地图选终点，也可以搜索地点" size:14 weight:UIFontWeightRegular];self.routeSummary.textColor=UIColor.secondaryLabelColor;[self.routeCard addArrangedSubview:self.routeSummary];
    self.travelMode=[[UISegmentedControl alloc]initWithItems:@[@"模拟验收",@"实时导航"]];self.travelMode.selectedSegmentIndex=1;self.travelMode.accessibilityIdentifier=@"navigation-mode";[self.travelMode addTarget:self action:@selector(modeChanged) forControlEvents:UIControlEventValueChanged];self.travelMode.hidden=YES; // Internal preview fixture only, not a normal user mode picker.
    self.startLabel=[self navLabel:@"起点：手机当前位置（规划时获取）" size:13 weight:UIFontWeightMedium];self.startLabel.accessibilityIdentifier=@"navigation-start-point";[self.routeCard addArrangedSubview:self.startLabel];
    self.briefLabel=[self navLabel:@"实时导航使用手机位置；仅开始导航后持续定位。" size:12 weight:UIFontWeightRegular];self.briefLabel.textColor=UIColor.secondaryLabelColor;[self.routeCard addArrangedSubview:self.briefLabel];
    UIStackView *actions=[UIStackView new];actions.axis=UILayoutConstraintAxisHorizontal;actions.spacing=10;actions.distribution=UIStackViewDistributionFillEqually;self.planButton=[self navAction:@"规划路线" icon:@"point.topleft.down.curvedto.point.bottomright.up" selector:@selector(planRoute) primary:NO];self.planButton.accessibilityIdentifier=@"navigation-plan";self.beginButton=[self navAction:@"开始步行" icon:@"play.fill" selector:@selector(beginRoute) primary:YES];self.beginButton.accessibilityIdentifier=@"navigation-begin";self.planButton.hidden=YES; // Retained only for old offline fixtures, never a normal control.
    [actions addArrangedSubview:self.beginButton];[self.routeCard addArrangedSubview:actions];
    self.lensCard=[self navCard];[self.lensCard addArrangedSubview:[self navLabel:@"眼镜显示" size:18 weight:UIFontWeightSemibold]];
    self.lensStatus=[self navLabel:@"尚未向眼镜发送导航" size:12 weight:UIFontWeightRegular];self.lensStatus.textColor=UIColor.secondaryLabelColor;self.lensStatus.accessibilityIdentifier=@"navigation-lens-status";[self.lensCard addArrangedSubview:self.lensStatus];
    self.hudLabel=[self navLabel:@"等待导航指引" size:19 weight:UIFontWeightMedium];self.hudLabel.textAlignment=NSTextAlignmentCenter;self.hudLabel.textColor=[UIColor colorWithRed:0.28 green:1 blue:0.42 alpha:1];self.hudLabel.backgroundColor=[UIColor colorWithWhite:0.035 alpha:1];self.hudLabel.layer.cornerRadius=14;self.hudLabel.clipsToBounds=YES;self.hudLabel.accessibilityIdentifier=@"navigation-hud";[self.hudLabel.heightAnchor constraintGreaterThanOrEqualToConstant:148].active=YES;[self.lensCard addArrangedSubview:self.hudLabel];
    UILabel *preview=[self navLabel:@"文字内容预览，非镜片截图；实际排版由眼镜决定。" size:11 weight:UIFontWeightRegular];preview.textColor=UIColor.secondaryLabelColor;[self.lensCard addArrangedSubview:preview];
    self.lensButton=[self navAction:@"开启眼镜字幕显示" icon:@"eyeglasses" selector:@selector(toggleLens) primary:NO];self.lensButton.accessibilityIdentifier=@"navigation-subtitle-enable";[self.lensCard addArrangedSubview:self.lensButton];
    self.stopButton=[self navAction:@"结束导航" icon:@"stop.fill" selector:@selector(stopUser) primary:YES];UIButtonConfiguration *stopConfig=self.stopButton.configuration;stopConfig.baseBackgroundColor=UIColor.systemRedColor;self.stopButton.configuration=stopConfig;self.stopButton.accessibilityIdentifier=@"navigation-stop";self.stopButton.translatesAutoresizingMaskIntoConstraints=NO;[self.view addSubview:self.stopButton];[NSLayoutConstraint activateConstraints:@[[self.stopButton.leadingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.leadingAnchor constant:16],[self.stopButton.trailingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.trailingAnchor constant:-16],[self.stopButton.bottomAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.bottomAnchor constant:-8]]];
    UILabel *foot=[self navLabel:@"支持步行、骑行、驾车，分别使用对应高德路线。手机实时导航在定位授权及location后台模式就绪后可退后台；真实路线自动显示并持续至结束，满足权限时尝试后台推送；正常页面仅实时出行。离开页面不结束路线，请主动结束。已有后台链路获用户初步实测，长期与异常可靠性仍需验证，请勿边驾驶边操作。设置与诊断在右上角“更多”。" size:12 weight:UIFontWeightRegular];foot.textColor=UIColor.secondaryLabelColor;[self.stack addArrangedSubview:foot];
}
#include "NavigationStopPresentation.inc"
- (void)refreshWorkspace{
    if(!self.planButton)return;BOOL running=self.active&&!self.planning&&!self.routeReady;
    self.searchButton.enabled=YES;self.travelMode.enabled=!self.active||self.routeReady;self.planButton.enabled=!self.active&&self.hasDestination&&self.initialized;self.beginButton.enabled=self.routeReady;
    self.title=[NSString stringWithFormat:@"%@导航",TIONavigationModeTitle(self.selectedTransport)?:@"出行"];self.transportMode.enabled=!self.navigationStarted&&(self.planning||TIONavigationMayChangeMode(self.active,self.routeReady));
    UIButtonConfiguration *begin=self.beginButton.configuration;begin.title=[NSString stringWithFormat:@"开始%@",TIONavigationModeTitle(self.selectedTransport)];self.beginButton.configuration=begin;
    self.briefLabel.text=self.note?:@"";self.stopButton.hidden=![self endNavigationVisible];
    self.scroll.contentInset=UIEdgeInsetsMake(0,0,self.stopButton.hidden?0:68,0);self.scroll.verticalScrollIndicatorInsets=self.scroll.contentInset;self.mapHeight.constant=running?180:240;
    BOOL picking=!self.active||self.routeReady;BOOL sim=NO;
    self.originHint.hidden=self.mapCrosshair.hidden=!self.initialized||!picking;self.mapPickRole.hidden=!sim||!picking;self.mapPickRole.enabled=self.initialized;self.centerButton.hidden=!picking;self.centerButton.enabled=self.initialized;
    BOOL origin=sim&&self.mapPickRole.selectedSegmentIndex==1;self.originHint.text=origin?@"  单击地图选模拟起点  ":@"  单击地图选终点  ";UIButtonConfiguration *center=self.centerButton.configuration;center.title=origin?@"将地图中心设为模拟起点":@"将地图中心设为终点";self.centerButton.configuration=center;
    self.startLabel.text=sim?(self.hasSimulationStart?[NSString stringWithFormat:@"模拟起点：%@\n%.5f, %.5f · 拖动地图不会改变",self.simulationStartName?:@"地图选点",self.simulationStart.latitude,self.simulationStart.longitude]:@"模拟起点：开启地图后选择"):@"起点：手机当前位置（开始规划时获取）";
    self.zoomInButton.hidden=self.zoomOutButton.hidden=!self.initialized;
    self.travelMode.hidden=YES;self.beginButton.superview.hidden=running;
    NSUInteger desired=running?2:self.stack.arrangedSubviews.count-2;if([self.stack.arrangedSubviews indexOfObject:self.lensCard]!=desired){[self.stack removeArrangedSubview:self.lensCard];[self.lensCard removeFromSuperview];[self.stack insertArrangedSubview:self.lensCard atIndex:running?2:self.stack.arrangedSubviews.count-1];}
    if(running)self.routeSummary.text=self.display[@"summary"]?:@"导航进行中";
    if(!self.active&&!self.hasDestination)self.routeSummary.text=@"搜索地点，或单击地图选择终点";
    NSDictionary *s=self.subtitleHUD.status,*runtime=TIOSubtitleNavigationStatus();BOOL on=[s[@"enabled"] boolValue];
    UIButtonConfiguration *lens=self.lensButton.configuration;lens.title=on?@"停止眼镜显示（保留手机导航）":@"开启眼镜字幕显示";self.lensButton.configuration=lens;
    self.lensButton.enabled=on||[self canStartSubtitle];
    NSString *blocked=[self subtitleBlockReason];
    self.lensStatus.text=on?[NSString stringWithFormat:@"nav-subtitle-v7-auto-plan\n%@ · 已提交 %@ 帧（非镜片回执）\n路线持续：%@ · 后台条件：%@",s[@"note"],s[@"frames"],[runtime[@"routeScoped"] boolValue]?@"是":@"否（短时实验）",[runtime[@"backgroundAllowed"] boolValue]?@"已满足":@"未满足"]:[NSString stringWithFormat:@"nav-subtitle-v7-auto-plan\n%@\n%@\n%@",s[@"note"]?:@"尚未开启",blocked?:@"可开启；等待状态不提供转向/距离",[runtime[@"available"] boolValue]?@"字幕格式已就绪 · 需确认眼镜空闲":@"等待当前眼镜连接或兼容配置；无需每次预览"];
    if(!running)self.hudLabel.text=self.planning?@"正在规划路线…":self.routeReady?@"路线已就绪\n点击开始后产生指引":@"等待导航指引\n\n转向 · 距离 · 道路";
}
- (void)modeChanged{if(![self prepareForSelection])return;self.mapPickRole.selectedSegmentIndex=0;self.routeSummary.text=@"尚未规划路线";self.note=self.travelMode.selectedSegmentIndex==0?@"模拟起点单独保存：切换“选模拟起点”后点地图。":@"实时导航使用手机位置；开始后可手动开启眼镜字幕，需保持手机前台，仅作研究辅助，不替代实际路况。请勿边驾驶边操作。";[self refresh];}
- (void)transportModeChanged{
    NSInteger mode=self.transportMode.selectedSegmentIndex;if(!TIONavigationModeTitle(mode)||self.navigationStarted||(!self.planning&&!TIONavigationMayChangeMode(self.active,self.routeReady))){self.transportMode.selectedSegmentIndex=self.selectedTransport;[self alert:@"请先结束当前导航" message:@"结束后再切换出行方式，不会使用旧模式路线。"] ;return;}
    if(![self prepareForSelection])return;self.selectedTransport=mode;self.routeReady=NO;self.routeSummary.text=@"出行方式已更改，自动重新规划";
#if TIO_AMAP_ENABLED
    if(self.routeLine){[self.map removeOverlay:self.routeLine];self.routeLine=nil;}
#endif
    self.note=[NSString stringWithFormat:@"已切换%@，起终点保留；使用%@引擎重新算路。使用手机位置实时导航，请勿边驾驶/骑车边操作。",TIONavigationModeTitle(mode),TIONavigationModeTitle(mode)];[self refresh];[self scheduleAutoPlan];
}
- (void)planRoute{[self startWalking];}
- (void)beginRoute{
#if TIO_AMAP_ENABLED
    if(!self.routeReady||!self.manager||self.planning)return;
    if(UIApplication.sharedApplication.applicationState!=UIApplicationStateActive)return;
    NSUInteger generation=self.generation;
    UIAlertController *a=[UIAlertController alertControllerWithTitle:@"开始导航并自动显示到眼镜" message:@"确认眼镜已回首页，没有录音、智记、字幕、提词或问答。实际导航显示随路线持续，满足权限时尝试后台推送；返回首页或关闭研究页不结束真实路线，请点结束导航。普通页面只提供实时导航。不会抢占已有会话，不用于行车安全决策。" preferredStyle:UIAlertControllerStyleAlert];
    [a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [a addAction:[UIAlertAction actionWithTitle:@"已确认，开始并自动显示" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){if(generation!=self.generation||!self.routeReady)return;self.lensStartApproved=YES;[self beginApprovedRoute];}]];
    [self presentViewController:a animated:YES completion:nil];
#endif
}
#include "NavigationBeginRoute.inc"
- (void)toggleLens{if([self.subtitleHUD.status[@"enabled"] boolValue]){[self.subtitleHUD stop:@"用户停止眼镜显示，手机导航继续"];[self refresh];}else [self enableSubtitleHUD];}
- (UIButton *)mapZoomButton:(NSString *)symbol action:(SEL)action label:(NSString *)label identifier:(NSString *)identifier{
    UIButton *b=[UIButton buttonWithType:UIButtonTypeSystem];[b setImage:[UIImage systemImageNamed:symbol] forState:UIControlStateNormal];b.backgroundColor=UIColor.secondarySystemGroupedBackgroundColor;b.tintColor=UIColor.labelColor;b.layer.cornerRadius=10;b.accessibilityLabel=label;b.accessibilityIdentifier=identifier;[b addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];[NSLayoutConstraint activateConstraints:@[[b.widthAnchor constraintEqualToConstant:44],[b.heightAnchor constraintEqualToConstant:44]]];return b;
}
- (BOOL)prepareForSelection{
    if(self.navigationStarted){[self alert:@"先结束当前导航" message:@"实际导航期间不自动更换终点或方式，结束后选择会自动规划。"] ;return NO;}
    if(self.planning)[self halt];
    if(self.active&&!self.routeReady){[self alert:@"先结束当前导航" message:@"算路或导航进行中，点击“结束导航”后即可更换起终点。地图缩放不受影响。"] ;return NO;}
    if(self.routeReady)[self halt];return YES;
}
- (void)pickRoleChanged{self.note=self.mapPickRole.selectedSegmentIndex==1?@"点击地图选择模拟起点；也可拖动地图后点“将地图中心设为模拟起点”。":@"点击地图选择终点，或点击顶部搜索。";[self refresh];}
- (void)selectSimulationStart:(CLLocationCoordinate2D)c name:(NSString *)name{
    if(!TIONavCoordinate(c.latitude,c.longitude)||![self prepareForSelection])return;self.simulationStart=c;self.hasSimulationStart=YES;self.simulationStartName=name;self.routeSummary.text=@"起点已更新，请重新规划路线";
#if TIO_AMAP_ENABLED
    if(self.startPin)[self.map removeAnnotation:self.startPin];self.startPin=[MAPointAnnotation new];self.startPin.coordinate=c;self.startPin.title=@"模拟起点";[self.map addAnnotation:self.startPin];if(self.routeLine){[self.map removeOverlay:self.routeLine];self.routeLine=nil;}
#endif
    self.note=@"模拟起点已固定。切换“选终点”或搜索目的地，拖动地图不会改变起点。";[self refresh];
}
- (void)selectMapCoordinate:(CLLocationCoordinate2D)c{
    if(!TIONavCoordinate(c.latitude,c.longitude)||![self prepareForSelection])return;
    if(self.travelMode.selectedSegmentIndex==0&&self.mapPickRole.selectedSegmentIndex==1)[self selectSimulationStart:c name:@"地图选点"];
    else [self selectPlace:@{@"name":@"地图选点",@"address":[NSString stringWithFormat:@"高德坐标 %.5f, %.5f",c.latitude,c.longitude],@"lat":@(c.latitude),@"lon":@(c.longitude)}];
}
- (void)selectMapCenter{
#if TIO_AMAP_ENABLED
    if(!self.initialized){self.pendingMapAction=@"selectMapCenter";[self consent];return;}[self selectMapCoordinate:self.map.centerCoordinate];
#else
    [self consent];
#endif
}
- (void)zoomIn{
#if TIO_AMAP_ENABLED
    if(self.initialized)[self.map setZoomLevel:MIN(self.map.maxZoomLevel,self.map.zoomLevel+1) animated:YES];
#endif
}
- (void)zoomOut{
#if TIO_AMAP_ENABLED
    if(self.initialized)[self.map setZoomLevel:MAX(self.map.minZoomLevel,self.map.zoomLevel-1) animated:YES];
#endif
}
- (void)searchPlaces{
    if(![self prepareForSelection])return;
    if(!self.initialized){self.pendingMapAction=@"searchPlaces";[self consent];return;}
    __weak typeof(self) weak=self;UIViewController *picker=TIONavPlacePicker(^(NSDictionary *p){if(!weak.active)[weak selectPlace:p];});picker.modalPresentationStyle=UIModalPresentationPageSheet;picker.sheetPresentationController.detents=@[UISheetPresentationControllerDetent.largeDetent];picker.sheetPresentationController.prefersGrabberVisible=YES;[self presentViewController:picker animated:YES completion:nil];
}
- (void)continueMapAction:(NSUInteger)attempt{
    if(!self.pendingMapAction.length)return;if(!self.view.window||self.active){self.pendingMapAction=nil;return;}
    if(self.presentedViewController){if(attempt<15)dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/10),dispatch_get_main_queue(),^{[self continueMapAction:attempt+1];});else{self.pendingMapAction=nil;self.note=@"地图已开启，请点击搜索或选点继续";[self refresh];}return;}
    NSString *action=self.pendingMapAction;self.pendingMapAction=nil;if([@[@"searchPlaces",@"locateMe",@"demoRoute",@"selectMapCenter"] containsObject:action])[self performSelector:NSSelectorFromString(action)];
}
- (void)selectPlace:(NSDictionary *)value{
    NSDictionary *p=TIONavPlace(value);if(!p||![self prepareForSelection])return;self.destination=CLLocationCoordinate2DMake([p[@"lat"] doubleValue],[p[@"lon"] doubleValue]);self.hasDestination=YES;self.destinationName=p[@"name"];self.destinationLabel.text=[@"终点 · " stringByAppendingString:self.destinationName];self.routeSummary.text=p[@"address"];self.note=@"终点已选择，将使用手机位置自动规划；路线就绪后点击开始。";
#if TIO_AMAP_ENABLED
    if(self.pin)[self.map removeAnnotation:self.pin];self.pin=[MAPointAnnotation new];self.pin.coordinate=self.destination;self.pin.title=self.destinationName;[self.map addAnnotation:self.pin];[self.map setCenterCoordinate:self.destination animated:YES];if(self.routeLine){[self.map removeOverlay:self.routeLine];self.routeLine=nil;}
#endif
    [self refresh];UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification,@"终点已选择，自动规划路线");[self scheduleAutoPlan];
}
- (void)routeOverview{
#if TIO_AMAP_ENABLED
    if(self.routeLine)[self.map setVisibleMapRect:self.routeLine.boundingMapRect edgePadding:UIEdgeInsetsMake(35,30,35,30) animated:YES];else if(self.hasDestination)[self.map setCenterCoordinate:self.destination animated:YES];else [self consent];
#else
    [self consent];
#endif
}
#if TIO_AMAP_ENABLED
#include "NavigationMapLocation.inc"
#endif
- (void)locateMe{
#if TIO_AMAP_ENABLED
    if(!self.initialized){self.pendingMapAction=@"locateMe";self.autoMapEntryAttempted=NO;[self openMapOnEntry:0];return;}
    [self beginMapLocation];
#else
    [self consent];
#endif
}
#if TIO_AMAP_ENABLED
- (void)mapView:(MAMapView *)map didUpdateUserLocation:(MAUserLocation *)user updatingLocation:(BOOL)updating{
    if(map==self.map&&updating&&user.location)[self acceptMapLocation:user.location coordinate:user.coordinate];
}
#endif

- (void)demoRoute{
#if TIO_AMAP_ENABLED
    if(![self prepareForSelection])return;if(!self.initialized){self.pendingMapAction=@"demoRoute";[self consent];return;}[self selectPlace:@{@"name":@"北京演示终点",@"address":@"公开测试坐标 · 非真实位置",@"lat":@39.9143,@"lon":@116.4112}];self.travelMode.selectedSegmentIndex=0;[self selectSimulationStart:CLLocationCoordinate2DMake(39.9087,116.3975) name:@"北京演示起点"];[self.map setCenterCoordinate:self.simulationStart animated:NO];self.note=@"北京演示路线已准备，点击规划路线 → 开始模拟。";[self refresh];
#else
    [self startFixture];
#endif
}
- (void)showDiagnostic{[self refresh];NSMutableArray *resources=[NSMutableArray new];for(NSString *name in @[@"AMap",@"AMapNavi",@"AMapSearch"])[resources addObject:[NSString stringWithFormat:@"%@：%@",name,[NSBundle.mainBundle pathForResource:name ofType:@"bundle"]?@"已打包":@"未打包"]];self.statusLabel.text=[self.statusLabel.text stringByAppendingFormat:@"\n门禁：%@",[self subtitleGateDiagnostic]];[self alert:@"导航状态 · v9 自动规划/500ms目标" message:[NSString stringWithFormat:@"Bundle ID（Key须绑定此值）：%@\n资源：%@\nlocation后台模式：%@\n%@",NSBundle.mainBundle.bundleIdentifier?:@"未知",[resources componentsJoinedByString:@" / "],[self navigationLocationModeAvailable]?@"已声明":@"未声明（仅前台）",self.statusLabel.text]];}
- (void)navigationMore{
    UIAlertController *a=[UIAlertController alertControllerWithTitle:@"导航设置与诊断" message:@"主要流程留在地图页；实验入口不会自动执行。" preferredStyle:UIAlertControllerStyleActionSheet];
    NSArray *items=@[@[@"字幕格式与退出检查",@"subtitleCheck"],@[@"路线详情",@"routeDetails"],@[@"读取连接状态",@"refreshConnection"],@[@"详细诊断",@"showDiagnostic"],@[@"高德 Key 设置",@"configureKey"]];
    for(NSArray *item in items)[a addAction:[UIAlertAction actionWithTitle:item[0] style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){[self performSelector:NSSelectorFromString(item[1])];}]];[a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];a.popoverPresentationController.barButtonItem=self.navigationItem.rightBarButtonItem;[self presentViewController:a animated:YES completion:nil];
}
- (void)fallbackMenu{
    UIAlertController *a=[UIAlertController alertControllerWithTitle:@"备用显示通道 · 实验" message:@"提词器换稿有loading；通知和仪表盘会自动熄屏。切换前先停止已有字幕。" preferredStyle:UIAlertControllerStyleActionSheet];for(NSArray *item in @[@[@"手动提词格式检查",@"manualHUD"],@[@"提词器导航",@"enableTeleHUD"],@[@"通知测试7392",@"testNotice"],@[@"自动转弯通知",@"enableNotices"],@[@"仪表盘导航卡",@"enableGlasses"],@[@"离线显示夹具",@"startFixture"]])[a addAction:[UIAlertAction actionWithTitle:item[0] style:UIAlertActionStyleDefault handler:^(UIAlertAction *x){[self performSelector:NSSelectorFromString(item[1])];}]];[a addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];a.popoverPresentationController.barButtonItem=self.navigationItem.rightBarButtonItem;[self presentViewController:a animated:YES completion:nil];
}
- (void)routeDetails{
#if TIO_AMAP_ENABLED
    if(!self.manager.naviRoute){[self alert:@"暂无路线" message:@"先选择目的地并规划路线。"] ;return;}
    NSMutableArray *lines=[NSMutableArray new];for(AMapNaviSegment *s in self.manager.naviRoute.routeSegments){NSString *road=s.links.firstObject.roadName?:@"未命名道路";[lines addObject:[NSString stringWithFormat:@"%lu. %@ · %ld米",(unsigned long)lines.count+1,road,(long)s.length]];if(lines.count>=40)break;}[self alert:@"路线分段（最多40段）" message:[lines componentsJoinedByString:@"\n"]];
#else
    [self alert:@"暂无高德路线" message:@"离线预览不生成虚构路线。"];
#endif
}
