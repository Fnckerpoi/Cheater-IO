// Production visibility policy and its Foundation test share this method.
- (BOOL)endNavigationVisible{return self.navigationStarted||[self.subtitleHUD.status[@"enabled"] boolValue];}
