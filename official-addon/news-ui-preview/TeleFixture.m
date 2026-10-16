#import "../NewsTeleprompter.h"
NSDictionary *TIONewsTeleStatus(void) {
    return @{@"available":@NO,@"active":@NO,@"ready":@NO,@"playing":@NO,
             @"state":@"隔离 UI 验证：未连接设备",@"prepareReplies":@0,@"offset":@0};
}
BOOL TIONewsTelePrepare(NSString *text, NSInteger speed) { abort(); }
BOOL TIONewsTeleControl(unsigned type, NSInteger speed) { abort(); }
