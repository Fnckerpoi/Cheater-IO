static BOOL Send(NSUInteger type,NSDictionary *j){
    if(!NSThread.isMainThread||!Plugin||!Route||!Device||Sending||![TIOProtocolDevice() isEqual:Device])return NO;
    if(type==5&&TextPending)return NO;
    if(!SubtitleCanSend(type,UIApplication.sharedApplication.applicationState==UIApplicationStateActive))return NO;
    Class td=NSClassFromString(@"FlutterStandardTypedData"),call=NSClassFromString(@"FlutterMethodCall");SEL typed=NSSelectorFromString(@"typedDataWithBytes:"),make=NSSelectorFromString(@"methodCallWithMethodName:arguments:"),handle=NSSelectorFromString(@"handleMethodCall:result:");
    NSData *data=TIOSubtitlePacket(type,j);if(!data||![td respondsToSelector:typed]||![call respondsToSelector:make]||![Plugin respondsToSelector:handle])return NO;
    NSMutableDictionary *args=[Route mutableCopy];args[@"businessId"]=@19;args[@"payload"]=((id(*)(id,SEL,id))objc_msgSend)(td,typed,data);
    id c=((id(*)(id,SEL,id,id))objc_msgSend)(call,make,@"rayneonet_sendMessage",args);NSString *sid=Trial.sid;Sending=YES;BOOL ok=YES;
    if(type==5){TextPending=YES;TextDeadline=Now()+8;}else if(type==3)TextPending=NO;
    Evidence(@"submit",TIOSubtitleEnvelope(data));
    @try{((void(*)(id,SEL,id,id))objc_msgSend)(Plugin,handle,c,[^(id result){
        BOOL failure=([result isKindOfClass:NSDictionary.class]&&[result[@"success"] isEqual:@NO])||[NSStringFromClass([result class]) containsString:@"FlutterError"]||[result isEqual:@NO];
        if(type==5&&!failure)dispatch_async(dispatch_get_main_queue(),^{if([Trial.sid isEqual:sid]){TextPending=NO;[TIONavigationRouteSession.shared pulse];}});
        if(failure)dispatch_async(dispatch_get_main_queue(),^{if(![Trial.sid isEqual:sid])return;if(type==3){Trial.phase=@"uncertain";Trial.note=@"SDK拒绝退出提交，请用实体按钮退出";Save();}else[Trial stop:@"SDK拒绝提交" now:Now()];});
    } copy]);}@catch(NSException *e){if(type==5)TextPending=NO;ok=NO;}Sending=NO;return ok;
}
