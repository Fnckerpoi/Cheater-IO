// Entry loads map only after existing key + explicit SDK privacy consent.
- (void)openMapOnEntry:(NSUInteger)attempt{
#if TIO_AMAP_ENABLED
    if(self.initialized||self.autoMapEntryAttempted||!self.view.window||UIApplication.sharedApplication.applicationState!=UIApplicationStateActive)return;
    if(self.presentedViewController){if(attempt<15)dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC/10),dispatch_get_main_queue(),^{[self openMapOnEntry:attempt+1];});return;}
    self.autoMapEntryAttempted=YES;
    if(!ValidKey(ReadNavKey())){self.note=@"请在更多中填写自己的高德 iOS Key，保存后自动加载地图";[self refresh];return;}
    if(![NSUserDefaults.standardUserDefaults boolForKey:NavConsent]){[self consent];return;} // Never auto-agree.
    [self openMap];
#endif
}
