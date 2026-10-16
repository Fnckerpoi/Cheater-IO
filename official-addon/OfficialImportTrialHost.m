#import "OfficialImportTrialHost.h"
#import <objc/runtime.h>
#import <objc/message.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#import <TargetConditionals.h>
#if TARGET_OS_IPHONE
#import <UIKit/UIKit.h>
#import <mach-o/loader.h>
#import <mach-o/dyld.h>
#endif
static Class Picker;
static TIOImportTrialCore *Core;
static IMP PriorHandler,PriorPicker;
static BOOL Installed,Drift,Inside,Completing;
static __weak id Current;
static id CurrentContinuation;
static NSString *Status=@"HOOKS_OFF";
static NSTimeInterval Now(void){return NSProcessInfo.processInfo.systemUptime;}
static SEL Handler(void){return NSSelectorFromString(@"handleMethodCall:result:");}
static SEL Pick(void){return NSSelectorFromString(@"resolvePickDocumentWithMultiPick:pickDirectory:");}
static id Get(id o,NSString *s){return ((id(*)(id,SEL))objc_msgSend)(o,NSSelectorFromString(s));}
static BOOL Bit(id o,NSString *s){return ((BOOL(*)(id,SEL))objc_msgSend)(o,NSSelectorFromString(s));}
static BOOL ABI(Class c,NSString *selector,const char *ret,NSArray<NSString *> *args){Method m=class_getInstanceMethod(c,NSSelectorFromString(selector));if(!m||method_getNumberOfArguments(m)!=args.count+2)return NO;char r[128];method_getReturnType(m,r,sizeof(r));if(strcmp(r,ret))return NO;for(unsigned i=0;i<args.count;i++){char a[128];method_getArgumentType(m,i+2,a,sizeof(a));if(strcmp(a,args[i].UTF8String))return NO;}return YES;}
static BOOL Metadata(Class c){
 return ABI(c,@"handleMethodCall:result:","v",@[@"@",@"@?"])&&ABI(c,@"resolvePickDocumentWithMultiPick:pickDirectory:","v",@[@"B",@"B"])&&ABI(c,@"handleResult:","v",@[@"@"])&&ABI(c,@"result","@?",@[])&&ABI(c,@"loadDataToMemory","B",@[])&&ABI(c,@"isSaveFile","B",@[])&&ABI(c,@"setIsSaveFile:","v",@[@"B"])&&ABI(c,@"documentPickerController","@",@[])&&ABI(c,@"setDocumentPickerController:","v",@[@"@"])&&ABI(c,@"galleryPickerController","@",@[])&&ABI(c,@"audioPickerController","@",@[])&&ABI(c,@"interactionController","@",@[]);
}
static BOOL RuntimeGuard(void){
#if TIO_IMPORT_TRIAL_TESTING
 return Picker&&Metadata(Picker);
#elif TARGET_OS_IPHONE
 NSBundle *b=NSBundle.mainBundle;if(![b.bundleIdentifier isEqual:@"com.duriea.turboio.research"]||![b.infoDictionary[@"CFBundleShortVersionString"] isEqual:@"1.0.5"]||![b.infoDictionary[@"CFBundleVersion"] isEqual:@"201"])return NO;
 Class c=NSClassFromString(@"FilePickerPlugin");if(!c||!Metadata(c))return NO;const char *image=class_getImageName(c);if(!image)return NO;
 NSString *path=@(image);NSString *expected=[b.bundlePath stringByAppendingPathComponent:@"Frameworks/file_picker.framework/file_picker"];if(![path isEqual:expected])return NO;
 struct stat st;if(lstat(image,&st)||!S_ISREG(st.st_mode)||st.st_size<65536||st.st_size>1048576)return NO;
 // Signing changes the signature blob: runtime binding uses UUID, image and ABI, not whole-file SHA.
 for(NSString *sel in @[@"handleResult:",@"resolvePickDocumentWithMultiPick:pickDirectory:",@"handleMethodCall:result:"]){Dl_info info;if(!dladdr((const void *)method_getImplementation(class_getInstanceMethod(c,NSSelectorFromString(sel))),&info)||!info.dli_fname||strcmp(info.dli_fname,image))return NO;}
 BOOL uuid=NO;for(uint32_t i=0;i<_dyld_image_count();i++){if(strcmp(_dyld_get_image_name(i),image))continue;const struct mach_header_64 *h=(const void *)_dyld_get_image_header(i);if(h->magic!=MH_MAGIC_64)return NO;const struct load_command *lc=(const void *)(h+1);for(uint32_t k=0;k<h->ncmds;k++){if(lc->cmd==LC_UUID){const struct uuid_command *u=(const void *)lc;NSUUID *idn=[[NSUUID alloc] initWithUUIDBytes:u->uuid];uuid=[idn.UUIDString isEqual:@"53BB7A4E-6F63-3184-9996-86307D8DB52F"];}lc=(const void *)((const char *)lc+lc->cmdsize);}}
 if(!uuid)return NO;Picker=c;return YES;
#else
 return NO;
#endif
}
static void HookHandler(id,SEL,id,void(^)(id));
static void HookPicker(id,SEL,BOOL,BOOL);
static void Restore(void){
 if(!Installed)return;Method h=class_getInstanceMethod(Picker,Handler()),p=class_getInstanceMethod(Picker,Pick());
 if(method_getImplementation(h)==(IMP)HookHandler)method_setImplementation(h,PriorHandler);else Drift=YES;
 if(method_getImplementation(p)==(IMP)HookPicker)method_setImplementation(p,PriorPicker);else Drift=YES;
 Installed=NO;Status=Drift?@"LATER_SWIZZLER_DRIFT_IDLE_NO_REARM":@"HOOKS_OFF";
}
TIOImportTrialCore *TIOOfficialImportTrialCore(void){if(!Core)Core=[TIOImportTrialCore new];return Core;}
void TIOOfficialImportTrialStop(NSString *reason){[Core stop:reason];if(!Inside&&!Completing)Restore();}
NSString *TIOOfficialImportTrialHostStatus(void){return Status;}
static BOOL ControllerIdle(id plugin){
 for(NSString *key in @[@"galleryPickerController",@"audioPickerController",@"interactionController"]){if(Get(plugin,key))return NO;}
 id c=Get(plugin,@"documentPickerController");if(!c)return YES;
#if TARGET_OS_IPHONE
 if(![c isKindOfClass:UIDocumentPickerViewController.class])return NO;return [c presentingViewController]==nil&&![c isBeingPresented]&&![c isBeingDismissed]&&[c viewIfLoaded].window==nil;
#else
 return NO;
#endif
}
static void HookHandler(id self,SEL cmd,id call,void(^original)(id)){
 IMP prior=PriorHandler;BOOL context=NO;NSDictionary *profile=nil;
 @try{
  [Core expireAt:Now()];if(Core.armed){
   if(!NSThread.isMainThread||Inside||Completing||Get(self,@"result")!=nil||!ControllerIdle(self)||!original){[Core stop:@"BUSY_OR_REENTRANT_DISABLED"];}
   else{NSString *method=Get(call,@"method");id args=Get(call,@"arguments");if([Core acceptMethod:method args:args now:Now()]){profile=TIOImportTrialProfile(method,args);context=YES;}}
  }
 }@catch(NSException *e){[Core stop:@"OBSERVER_EXCEPTION_DISABLED"];}
 if(!context){((void(*)(id,SEL,id,void(^)(id)))prior)(self,cmd,call,original);if(!Core.armed&&!Inside&&!Completing)Restore();return;}
 Inside=YES;Current=self;TIOImportTrialCore *transactionCore=Core;NSUInteger generation=Core.generation;
 void (^wrapped)(id)=[^(id response){
  @try{if(Core==transactionCore&&Core.generation==generation)[Core observeResult:response profile:profile now:Now()];}@catch(NSException *e){[Core stop:@"OBSERVER_EXCEPTION_DISABLED"];}
  // Preserve original result identity and every invocation. No synthetic completion.
  original(response);
  if(!Core.armed&&!Inside&&!Completing)Restore();
 } copy];CurrentContinuation=wrapped;
 @try{((void(*)(id,SEL,id,void(^)(id)))prior)(self,cmd,call,wrapped);}
 @finally{Inside=NO;Current=nil;CurrentContinuation=nil;if(!Core.armed)Restore();}
}
static void HookPicker(id self,SEL cmd,BOOL multiple,BOOL directory){
 IMP prior=PriorPicker;BOOL supply=NO;
 @try{
  [Core expireAt:Now()];supply=Core.armed&&Core.functional&&Core.requested&&Inside&&Current==self&&!multiple&&!directory&&!Completing&&Get(self,@"result")==CurrentContinuation&&CurrentContinuation!=nil&&ControllerIdle(self)&&Bit(self,@"loadDataToMemory")==[Core.summary[@"request"][@"withData"] boolValue]&&[Core verifyOwnFile];
 }@catch(NSException *e){[Core stop:@"HELPER_GUARD_EXCEPTION_DISABLED"];}
 if(!supply){if(Core.armed&&Core.functional)[Core stop:@"HELPER_MISMATCH_FALLBACK"];((void(*)(id,SEL,BOOL,BOOL))prior)(self,cmd,multiple,directory);return;}
 BOOL began=NO;@try{began=[Core beginSubmissionAt:Now()];}@catch(NSException *e){[Core stop:@"SUBMISSION_GUARD_EXCEPTION_FALLBACK"];}
 if(!began){((void(*)(id,SEL,BOOL,BOOL))prior)(self,cmd,multiple,directory);return;}
 // The original handler has stored its actual continuation and configured flags.
 Completing=YES;
 @try{
  ((void(*)(id,SEL,BOOL))objc_msgSend)(self,NSSelectorFromString(@"setIsSaveFile:"),NO);
  ((void(*)(id,SEL,id))objc_msgSend)(self,NSSelectorFromString(@"setDocumentPickerController:"),nil);
  ((void(*)(id,SEL,id))objc_msgSend)(self,NSSelectorFromString(@"handleResult:"),@[Core.fileURL]);
  [Core submissionCleared:Get(self,@"result")==nil&&!Bit(self,@"isSaveFile")&&Get(self,@"documentPickerController")==nil];
 }@catch(NSException *e){[Core stop:@"B_EXCEPTION_RESULT_UNKNOWN_NO_RETRY"];}
 @finally{Completing=NO;}
}
BOOL TIOOfficialImportTrialArm(BOOL functional){
 if(!NSThread.isMainThread||Installed||Drift||Inside||Completing||!RuntimeGuard())return NO;
 TIOImportTrialCore *c=TIOOfficialImportTrialCore();if(![c armFunctional:functional now:Now()])return NO;
 Method h=class_getInstanceMethod(Picker,Handler()),p=class_getInstanceMethod(Picker,Pick());PriorHandler=method_getImplementation(h);PriorPicker=method_getImplementation(p);
 if(PriorHandler==(IMP)HookHandler||PriorPicker==(IMP)HookPicker){Drift=YES;[c stop:@"CHAIN_DRIFT_DISABLED"];return NO;}
 method_setImplementation(h,(IMP)HookHandler);method_setImplementation(p,(IMP)HookPicker);Installed=YES;Status=@"ARMED_SHORT_PUBLIC_TRIAL";
#if TARGET_OS_IPHONE
 static dispatch_once_t once;dispatch_once(&once,^{[NSNotificationCenter.defaultCenter addObserverForName:UIApplicationDidEnterBackgroundNotification object:nil queue:NSOperationQueue.mainQueue usingBlock:^(NSNotification *n){(void)n;if(Core.armed)TIOOfficialImportTrialStop(@"BACKGROUND_DISABLED");}];});
 dispatch_after(dispatch_time(DISPATCH_TIME_NOW,120*NSEC_PER_SEC),dispatch_get_main_queue(),^{[Core expireAt:Now()];if(!Core.armed)Restore();});
#endif
 return YES;
}
#if TIO_IMPORT_TRIAL_TESTING
BOOL TIOImportTrialTestBind(Class c,TIOImportTrialCore *core){if(Installed||Inside||Completing)return NO;Picker=c;Core=core;return Metadata(c);}
#endif
