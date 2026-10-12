#import <Foundation/Foundation.h>
#include <assert.h>
typedef NS_ENUM(NSInteger,UIApplicationState){UIApplicationStateActive,UIApplicationStateInactive,UIApplicationStateBackground};
@interface UIApplication:NSObject @property UIApplicationState applicationState; + (instancetype)sharedApplication; @end
@implementation UIApplication + (instancetype)sharedApplication{static id a;static dispatch_once_t once;dispatch_once(&once,^{a=[self new];});return a;} @end
@interface FakeView:NSObject @property id window; @end
@implementation FakeView @end
static NSString *const NavConsent=@"test-amap-entry-consent";static NSString *Key;
static NSString *ReadNavKey(void){return Key;}
static BOOL ValidKey(NSString *k){return [k isEqual:@"synthetic-valid-key"];} // No actual credential.
@interface TIONavigationPanel:NSObject
@property BOOL initialized,autoMapEntryAttempted;@property FakeView *view;@property id presentedViewController;@property NSString *note;@property NSUInteger loads,prompts,refreshes;
@end
@implementation TIONavigationPanel
- (void)refresh{self.refreshes++;}- (void)consent{self.prompts++;}- (void)openMap{self.loads++;self.initialized=YES;}
#define TIO_AMAP_ENABLED 1
#include "NavigationMapEntry.inc"
@end
static TIONavigationPanel *P(void){Key=@"synthetic-valid-key";UIApplication.sharedApplication.applicationState=UIApplicationStateActive;TIONavigationPanel *p=[TIONavigationPanel new];p.view=[FakeView new];p.view.window=[NSObject new];return p;}
int main(void){@autoreleasepool{
    id old=[NSUserDefaults.standardUserDefaults objectForKey:NavConsent];[NSUserDefaults.standardUserDefaults setBool:YES forKey:NavConsent];
    TIONavigationPanel *p=P();[p openMapOnEntry:0];assert(p.loads==1&&!p.prompts);[p openMapOnEntry:0];assert(p.loads==1);
    p=P();[NSUserDefaults.standardUserDefaults setBool:NO forKey:NavConsent];[p openMapOnEntry:0];assert(!p.loads&&p.prompts==1&&![NSUserDefaults.standardUserDefaults boolForKey:NavConsent]);
    p=P();Key=nil;[p openMapOnEntry:0];assert(!p.loads&&!p.prompts&&p.refreshes==1);
    p=P();UIApplication.sharedApplication.applicationState=UIApplicationStateBackground;[p openMapOnEntry:0];assert(!p.loads&&!p.prompts);
    p=P();p.view.window=nil;[p openMapOnEntry:0];assert(!p.loads&&!p.prompts);
    if(old)[NSUserDefaults.standardUserDefaults setObject:old forKey:NavConsent];else[NSUserDefaults.standardUserDefaults removeObjectForKey:NavConsent];
    puts("PASS: production map entry loads once with key+privacy consent; does not agree for user, initialize in background or offscreen, or access GPS.");
}}
