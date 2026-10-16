#import "OfficialImportTrialHost.h"
#import <objc/runtime.h>
#include <assert.h>
@interface TrialCall:NSObject
@property NSString *method;@property NSDictionary *arguments;
@end
@implementation TrialCall @end
@interface TrialPicker:NSObject
@property(copy) void(^result)(id);
@property bool loadDataToMemory;@property bool isSaveFile;
@property id documentPickerController;@property id galleryPickerController;@property id audioPickerController;@property id interactionController;
@property unsigned calls,picks,handled;
@property BOOL badResponse,throwOnHandle,wrongFlag,forceMulti,forceDir;
- (void)handleMethodCall:(TrialCall *)call result:(void(^)(id))result;
- (void)resolvePickDocumentWithMultiPick:(bool)multi pickDirectory:(bool)directory;
- (void)handleResult:(id)urls;
@end
@implementation TrialPicker
- (void)handleMethodCall:(TrialCall *)call result:(void(^)(id))result{self.calls++;if(self.result){result(@{@"multiple_request":@YES});return;}self.result=result;self.loadDataToMemory=[call.arguments[@"withData"] boolValue];if(self.wrongFlag)self.loadDataToMemory=!self.loadDataToMemory;[self resolvePickDocumentWithMultiPick:self.forceMulti pickDirectory:self.forceDir];}
- (void)resolvePickDocumentWithMultiPick:(bool)multi pickDirectory:(bool)directory{self.picks++;}
- (void)handleResult:(id)urls{self.handled++;if(self.throwOnHandle)@throw [NSException exceptionWithName:@"test" reason:@"test" userInfo:nil];if(self.badResponse){self.result(@[@{}]);self.result=nil;return;}NSURL *u=[urls firstObject];NSData *d=[NSData dataWithContentsOfURL:u];NSMutableDictionary *r=[@{@"path":u.path,@"identifier":u.absoluteString,@"name":u.lastPathComponent,@"size":@(d.length)} mutableCopy];if(self.loadDataToMemory)r[@"bytes"]=d;self.result(@[r]);self.result=nil;}
@end
static NSDictionary *Args(void){return @{@"allowMultipleSelection":@NO,@"withData":@NO,@"allowedExtensions":@[@"txt"],@"compressionQuality":@0};}
static TrialCall *Call(void){TrialCall *c=[TrialCall new];c.method=@"custom";c.arguments=Args();return c;}
static IMP DriftPrior;
static void Later(id o,SEL s,id c,void(^r)(id)){((void(*)(id,SEL,id,void(^)(id)))DriftPrior)(o,s,c,r);}
int main(void){@autoreleasepool{
 TIOImportTrialCore *c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));SEL h=@selector(handleMethodCall:result:),p=@selector(resolvePickDocumentWithMultiPick:pickDirectory:);IMP originalH=class_getMethodImplementation(TrialPicker.class,h),originalP=class_getMethodImplementation(TrialPicker.class,p);
 assert(!TIOOfficialImportTrialArm(YES));assert(TIOOfficialImportTrialArm(NO));TrialPicker *plugin=[TrialPicker new];__block unsigned returns=0;__block id received=nil;
 [plugin handleMethodCall:Call() result:^(id r){returns++;received=r;}];assert(plugin.calls==1&&plugin.picks==1&&returns==0);[plugin handleResult:@[c.fileURL]];assert(returns==1&&[received isKindOfClass:NSArray.class]&&plugin.result==nil&&c.canConfirm);assert(class_getMethodImplementation(TrialPicker.class,h)==originalH);assert([c confirmConsumption]);assert(TIOOfficialImportTrialArm(YES));
 received=nil;[plugin handleMethodCall:Call() result:^(id r){returns++;received=r;}];assert(plugin.calls==2&&plugin.picks==1&&plugin.handled==2&&returns==2&&plugin.result==nil);assert([c.summary[@"nativePendingCleared"] boolValue]);assert(!TIOOfficialImportTrialArm(YES));assert(class_getMethodImplementation(TrialPicker.class,p)==originalP);assert([c confirmConsumption]);assert([c confirmSave]);assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime]);
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));assert(TIOOfficialImportTrialArm(NO));plugin=[TrialPicker new];returns=0;[plugin handleMethodCall:Call() result:^(id r){returns++;received=r;}];void(^late)(id)=plugin.result;TIOOfficialImportTrialStop(@"BACKGROUND_DISABLED");id token=[NSObject new];late(token);late(token);assert(returns==2&&received==token);plugin.result=nil;assert(!c.canConfirm);assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);

 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));assert(TIOOfficialImportTrialArm(NO));plugin=[TrialPicker new];returns=0;[plugin handleMethodCall:Call() result:^(id r){returns++;received=r;}];late=plugin.result;TIOOfficialImportTrialStop(@"TIMEOUT");plugin.result=nil;assert(TIOOfficialImportTrialArm(NO));NSDictionary *fake=@{@"path":c.fileURL.path,@"identifier":c.fileURL.absoluteString,@"name":c.fileURL.lastPathComponent,@"size":c.summary[@"publicManifest"][@"size"]};late(@[fake]);assert(returns==1&&c.armed&&!c.canConfirm);TIOOfficialImportTrialStop(@"USER_STOP");assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));assert(TIOOfficialImportTrialArm(NO));plugin=[TrialPicker new];TrialCall *unknown=Call();unknown.method=@"save";[plugin handleMethodCall:unknown result:^(id r){(void)r;}];assert(plugin.calls==1&&plugin.picks==1&&!c.armed);plugin.result=nil;assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));assert(TIOOfficialImportTrialArm(NO));plugin=[TrialPicker new];plugin.result=^(id r){(void)r;};returns=0;[plugin handleMethodCall:Call() result:^(id r){(void)r;returns++;}];assert(returns==1&&!c.armed);plugin.result=nil;assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);

 for(NSNumber *mode in @[@0,@1,@2,@3]){
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));plugin=[TrialPicker new];assert(TIOOfficialImportTrialArm(NO));[plugin handleMethodCall:Call() result:^(id r){(void)r;}];[plugin handleResult:@[c.fileURL]];assert([c confirmConsumption]);assert(TIOOfficialImportTrialArm(YES));
 plugin.badResponse=mode.intValue==0;plugin.throwOnHandle=mode.intValue==1;plugin.wrongFlag=mode.intValue==2;plugin.forceMulti=mode.intValue==3;
 [plugin handleMethodCall:Call() result:^(id r){(void)r;}];assert(!c.canConfirm&&!c.canFunction);assert(![c confirmConsumption]);if(mode.intValue==0){assert(plugin.result==nil);assert([c.state isEqual:@"B_RESULT_MISMATCH_SAVE_UNKNOWN_NO_RETRY"]);}if(mode.intValue==1){assert(plugin.result!=nil);assert([c.state isEqual:@"B_EXCEPTION_RESULT_UNKNOWN_NO_RETRY"]);}if(mode.intValue>=2)assert(plugin.picks==2);if(mode.intValue==1){assert(![c cleanupAt:NSProcessInfo.processInfo.systemUptime+7201]);plugin.result=nil;[c submissionCleared:YES];}plugin.result=nil;assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);
 }
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));plugin=[TrialPicker new];plugin.galleryPickerController=[NSObject new];assert(TIOOfficialImportTrialArm(NO));[plugin handleMethodCall:Call() result:^(id r){(void)r;}];assert(!c.armed&&plugin.calls==1);plugin.result=nil;assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);

 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));plugin=[TrialPicker new];assert(TIOOfficialImportTrialArm(NO));returns=0;[plugin handleMethodCall:Call() result:^(id r){returns++;received=r;}];late=plugin.result;[plugin handleResult:@[c.fileURL]];assert([c confirmConsumption]);assert([c confirmSave]);NSUInteger oldGen=c.generation;assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime]);assert(c.generation>oldGen);assert([c createFixture]);assert(!c.canFunction);assert(TIOOfficialImportTrialArm(NO));NSDictionary *newRow=@{@"path":c.fileURL.path,@"identifier":c.fileURL.absoluteString,@"name":c.fileURL.lastPathComponent,@"size":c.summary[@"publicManifest"][@"size"]};late(@[newRow]);assert(returns==2&&!c.canConfirm&&c.armed&&c.summary[@"response"]==nil&&c.summary[@"save"]==nil);TIOOfficialImportTrialStop(@"TEST_STOP");assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);
 c=[TIOImportTrialCore new];assert([c createFixture]);assert(TIOImportTrialTestBind(TrialPicker.class,c));assert(TIOOfficialImportTrialArm(NO));DriftPrior=class_getMethodImplementation(TrialPicker.class,h);method_setImplementation(class_getInstanceMethod(TrialPicker.class,h),(IMP)Later);TIOOfficialImportTrialStop(@"USER_STOP");assert(class_getMethodImplementation(TrialPicker.class,h)==(IMP)Later);assert(!TIOOfficialImportTrialArm(NO));plugin=[TrialPicker new];[plugin handleMethodCall:Call() result:^(id r){(void)r;}];assert(plugin.calls==1&&plugin.picks==1);plugin.result=nil;method_setImplementation(class_getInstanceMethod(TrialPicker.class,h),originalH);assert([c cleanupAt:NSProcessInfo.processInfo.systemUptime+3601]);
 NSLog(@"PASS Host synthetic typed ABI: original forward, official continuation, normal handleResult clears pending, bypass helper once, mismatch/busy fallback, late callback identity, IMPrestore/drift idle");return 0;
}}
