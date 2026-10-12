#import <Foundation/Foundation.h>
#import "SubtitleHUDCore.h"
#include <assert.h>
static TIOSubtitleTrial *Trial;static BOOL TextPending,Capturing,ConfirmedIdle;static NSTimeInterval ConfirmedAt,Clock;static NSUInteger Saves,Restores;
static void Ensure(void){if(!Trial)Trial=[TIOSubtitleTrial new];}
static void RestoreContext(void){Restores++;}
static void Save(void){Saves++;}
static NSTimeInterval Now(void){return Clock;}
#include "SubtitleHUDConfirmIdle.inc"
int main(void){@autoreleasepool{
 Clock=100;Ensure();NSMutableArray *sent=[NSMutableArray new];Trial.send=^BOOL(NSUInteger t,NSDictionary *j){[sent addObject:@{@"type":@(t),@"json":j}];return YES;};
 NSDictionary *p=@{@"sid":@"sample",@"scope":@"temporary",@"force":@NO,@"config":@{@"is_display":@YES}},*s=@{@"sid":@"sample",@"reason_code":@10,@"text":@""};
 NSMutableSet *sids=[NSMutableSet new];
 for(int run=0;run<3;run++){
   assert(TIOSubtitleConfirmIdle()&&ConfirmedIdle&&!TextPending&&!Capturing);
   assert([Trial startRouteWithPreview:p stop:s now:Clock]);NSString *sid=Trial.sid;assert(![sids containsObject:sid]);[sids addObject:sid];
   [Trial receive:@{@"type":@8,@"json":@{@"sid":sid,@"code":@1}} now:Clock];assert([Trial.phase isEqual:@"ready"]);
   assert(!TIOSubtitleConfirmIdle()&&[Trial.sid isEqual:sid]);Clock+=4;assert([Trial sendNavigationText:@"synthetic guidance" now:Clock]);
   [Trial stop:@"用户结束" now:Clock];assert([Trial.phase isEqual:@"stopping"]);Clock+=9;[Trial tick:Clock];assert([Trial.phase isEqual:@"uncertain"]);
 }
 assert(sids.count==3&&[sent filteredArrayUsingPredicate:[NSPredicate predicateWithFormat:@"type == 7"]].count==3&&Restores==3);
 puts("PASS: actual runtime confirm + subtitle core completes three navigation cycles in one process, fresh SID each time; running session never cleared.");
}}
