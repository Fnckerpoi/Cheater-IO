// Runtime and test share the exact explicit lens-idle confirmation transition.
BOOL TIOSubtitleConfirmIdle(void){
    Ensure();if(Trial.active&&![@[@"stopping",@"uncertain"] containsObject:Trial.phase])return NO;
    Trial.phase=@"idle";Trial.sid=nil;TextPending=NO;Capturing=NO;RestoreContext();ConfirmedIdle=YES;ConfirmedAt=Now();Trial.note=@"用户确认镜片已退出；非协议回执";Save();return YES;
}
