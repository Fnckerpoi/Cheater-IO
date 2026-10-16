#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <objc/message.h>
#import "../ResearchUI.h"
#import "../NewsReader.h"
#import "../NewsHubNavigation.h"

#if TIO_NEWS_RED
BOOL TIONewsHubNavigationInstall(void) { return NO; }
#endif
static NSMutableArray<NSString *> *Receipts;
static void Require(BOOL condition, NSString *message) {
    if (!condition) { NSLog(@"NEWS_UI_RED: %@",message); exit(42); }
    [Receipts addObject:message]; NSLog(@"NEWS_UI_PASS: %@",message);
}
@interface NoNewsNetwork : NSURLProtocol @end
@implementation NoNewsNetwork
+ (BOOL)canInitWithRequest:(NSURLRequest *)request { NSLog(@"NEWS_UI_RED: network attempted"); abort(); }
+ (NSURLRequest *)canonicalRequestForRequest:(NSURLRequest *)request { return request; }
- (void)startLoading { abort(); }
- (void)stopLoading {}
@end
static NSString *EvidenceDirectory(void) {
    NSString *path=[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,NSUserDomainMask,YES).firstObject stringByAppendingPathComponent:@"NewsUIEvidence"];
    [NSFileManager.defaultManager createDirectoryAtPath:path withIntermediateDirectories:YES attributes:nil error:nil];
    return path;
}
static void Capture(UIWindow *window,NSString *name) {
    UIGraphicsImageRenderer *renderer=[[UIGraphicsImageRenderer alloc]initWithSize:window.bounds.size];
    UIImage *image=[renderer imageWithActions:^(UIGraphicsImageRendererContext *context){[window drawViewHierarchyInRect:window.bounds afterScreenUpdates:YES];}];
    Require([UIImagePNGRepresentation(image) writeToFile:[EvidenceDirectory() stringByAppendingPathComponent:[name stringByAppendingString:@".png"]] atomically:YES],[name stringByAppendingString:@" screenshot captured"]);
}
static NSArray *Pages(UIViewController *reader) { return @[[UIViewController new],reader,[UIViewController new],[UIViewController new]]; }
static UIViewController *NewsRoot(UITabBarController *tabs) { return ((UINavigationController *)tabs.viewControllers[1]).viewControllers.firstObject; }
static void AssertRoot(UITabBarController *tabs) {
    UITableViewController *root=(id)NewsRoot(tabs);
    Require([root isKindOfClass:UITableViewController.class]&&![root isKindOfClass:NSClassFromString(@"TIONewsReader")],@"新闻根为同级模块菜单，不再直接是 TinyFish reader");
    [root loadViewIfNeeded];
    Require([root.tableView.dataSource tableView:root.tableView numberOfRowsInSection:0]==2,@"新闻模块恰有两个同级入口");
    UITableViewCell *tiny=[root.tableView.dataSource tableView:root.tableView cellForRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:0]];
    UITableViewCell *hub=[root.tableView.dataSource tableView:root.tableView cellForRowAtIndexPath:[NSIndexPath indexPathForRow:1 inSection:0]];
    Require([tiny.textLabel.text isEqual:@"TinyFish · 新闻阅读"]&&[tiny.accessibilityIdentifier isEqual:@"news-module-tinyfish"],@"TinyFish 可达入口label与selector正确");
    Require([hub.textLabel.text isEqual:@"NewsHub · 多源新闻调度中枢"]&&[hub.accessibilityIdentifier isEqual:@"news-module-newshub"],@"NewsHub 可达入口label与selector正确");
}
@interface NewsValidationApp : UIResponder <UIApplicationDelegate>
@property(nonatomic,strong) UIWindow *window;
@end
@implementation NewsValidationApp
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options {
    Receipts=[NSMutableArray new];
    [NSURLProtocol registerClass:NoNewsNetwork.class];
    [NSUserDefaults.standardUserDefaults setBool:NO forKey:@"io.turboio.newshub.scheduler.enabled"];
    TIONewsConfigure(^TIONewsCancel(NSString *prompt,void (^completion)(NSString *,NSString *)){ abort(); });
    UIViewController *reader=TIONewsReaderController();
    UITabBarController *tabs=TIOCreateResearchTabs(Pages(reader));
    NSArray *oldNavs=[tabs.viewControllers copy];
    IMP before=class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:));
    IMP beforeAnimated=class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:animated:));
    self.window=[[UIWindow alloc]initWithFrame:UIScreen.mainScreen.bounds];self.window.rootViewController=tabs;
    [self.window makeKeyAndVisible];tabs.selectedIndex=1;
    BOOL installed=TIONewsHubNavigationInstall();
    if([NSProcessInfo.processInfo.arguments containsObject:@"--reject-host"]||[NSProcessInfo.processInfo.arguments containsObject:@"--install-failure"]){
        Require(!installed&&!TIONewsHubNavigationInstall(),@"拒绝host或部分hook失败后install持续fail-closed");
        Require(NewsRoot(tabs)==reader,@"拒绝路径不改现有reader根");
        Require(before==class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:))&&beforeAnimated==class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:animated:)),@"拒绝路径UIKit IMP均未变");
        Require(before==class_getMethodImplementation(NSClassFromString(@"TIOResearchTabs"),@selector(setViewControllers:)),@"部分失败后本类plain IMP恢复原值");
        NSLog(@"NEWS_UI_REJECTION_ALL_PASS");
        return YES;
    }
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC),dispatch_get_main_queue(),^{
        AssertRoot(tabs);
        Require(before==class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:))&&beforeAnimated==class_getMethodImplementation(UITabBarController.class,@selector(setViewControllers:animated:)),@"UIKit 两个 inherited setter IMP均未污染");
        for(NSNumber *index in @[@0,@2,@3])Require(oldNavs[index.integerValue]==tabs.viewControllers[index.integerValue],@"另三个tabs的导航对象保持原样");
        UIViewController *root=NewsRoot(tabs);
        Require(TIONewsHubNavigationInstall()&&NewsRoot(tabs)==root,@"重复install不重新wrap已形成root");
        [tabs setViewControllers:tabs.viewControllers];
        [tabs setViewControllers:tabs.viewControllers animated:NO];
        Require(NewsRoot(tabs)==root,@"重复plain及animated setter不重新wrap");
        UITabBarController *future=TIOCreateResearchTabs(Pages([NSClassFromString(@"TIONewsReader") new]));
        AssertRoot(future);
        UITabBarController *animated=[NSClassFromString(@"TIOResearchTabs") new];
        NSMutableArray *navs=[NSMutableArray new];
        for(NSUInteger i=0;i<4;i++){
            UINavigationController *nav=[[UINavigationController alloc]initWithRootViewController:i==1?[NSClassFromString(@"TIONewsReader") new]:[UIViewController new]];
            nav.tabBarItem=[[UITabBarItem alloc]initWithTitle:i==1?@"新闻":@"其他" image:nil tag:i];[navs addObject:nav];
        }
        [animated setViewControllers:navs animated:NO];AssertRoot(animated);
        UITabBarController *foreign=[UITabBarController new];
        UIViewController *foreignReader=[NSClassFromString(@"TIONewsReader") new];
        UINavigationController *foreignNav=[[UINavigationController alloc]initWithRootViewController:foreignReader];
        foreignNav.tabBarItem=[[UITabBarItem alloc]initWithTitle:@"新闻" image:nil tag:1];
        [foreign setViewControllers:@[[UIViewController new],foreignNav,[UIViewController new],[UIViewController new]]];
        Require(NewsRoot(foreign)==foreignReader,@"非研究UITabBarController保持不变");
        UITabBarController *wrong=TIOCreateResearchTabs(Pages([UIViewController new]));
        Require(![NewsRoot(wrong) isKindOfClass:root.class],@"错误reader class不注入菜单");
        [UIView setAnimationsEnabled:NO];
        Capture(self.window,@"01-news-root");
        UINavigationController *nav=(id)tabs.viewControllers[1];
        UITableViewController *menu=(id)root;
        [menu.tableView.delegate tableView:menu.tableView didSelectRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:0]];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW,600*NSEC_PER_MSEC),dispatch_get_main_queue(),^{
            Require(nav.topViewController==reader,@"点击TinyFish保留原reader实例");
            Require([reader.title isEqual:@"新闻阅读"],@"TinyFish真实旧reader UI已呈现");
            Capture(self.window,@"02-tinyfish-reader");
            [nav popToRootViewControllerAnimated:NO];
            Require(nav.topViewController==root,@"TinyFish返回同级新闻root");
            [menu.tableView.delegate tableView:menu.tableView didSelectRowAtIndexPath:[NSIndexPath indexPathForRow:1 inSection:0]];
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW,600*NSEC_PER_MSEC),dispatch_get_main_queue(),^{
                Require(object_getClass(nav.topViewController)==NSClassFromString(@"TIONewsHubPanel"),@"点击NewsHub打开真实HubPanel");
                Require([nav.topViewController.title isEqual:@"每日资讯调度中枢"]&&!tabs.presentedViewController,@"Hub在新闻nav内push而不是浮层或独立modal");
                Capture(self.window,@"03-newshub-panel");
                UIBarButtonItem *close=nav.topViewController.navigationItem.leftBarButtonItem;
                ((void (*)(id,SEL))objc_msgSend)(close.target,close.action);
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW,600*NSEC_PER_MSEC),dispatch_get_main_queue(),^{
                    Require(nav.topViewController==root&&nav.viewControllers.count==1&&self.window.rootViewController==tabs,@"Hub关闭返回新闻root且研究壳仍在");
                    Capture(self.window,@"04-returned-news-root");
                    [Receipts addObject:@"NETWORK_REQUESTS=0; TRANSPORT_CALLS=0; SOURCE=actual ResearchUI+NewsReader+HubPanel"];
                    [[Receipts componentsJoinedByString:@"\n"] writeToFile:[EvidenceDirectory() stringByAppendingPathComponent:@"assertions.txt"] atomically:YES encoding:NSUTF8StringEncoding error:nil];
                    NSLog(@"NEWS_UI_ALL_PASS: %lu assertions",(unsigned long)Receipts.count);
                });
            });
        });
    });
    return YES;
}
@end
int main(int argc,char **argv) { @autoreleasepool { return UIApplicationMain(argc,argv,nil,NSStringFromClass(NewsValidationApp.class)); } }
