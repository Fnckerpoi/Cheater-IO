#import <UIKit/UIKit.h>
#import <objc/message.h>
#import "../../TIONewsHubPanel.h"
extern NSUInteger NewsArmCalls,NewsEnqueueCalls;
static void Check(BOOL condition,NSString *label) { if(!condition){NSLog(@"HEADLESS_UI_RED: %@",label);exit(42);}NSLog(@"HEADLESS_UI_PASS: %@",label); }
static UIView *Find(UIView *view,Class cls,NSString *text) {
    if([view isKindOfClass:cls]){NSString *value=[view isKindOfClass:UIButton.class]?[(UIButton *)view titleForState:UIControlStateNormal]:[(UILabel *)view text];if([value containsString:text])return view;}
    for(UIView *child in view.subviews){UIView *found=Find(child,cls,text);if(found)return found;}return nil;
}
@interface HeadlessUIApp:UIResponder<UIApplicationDelegate>
@property(nonatomic,strong)UIWindow *window;
@end
@implementation HeadlessUIApp
- (BOOL)application:(UIApplication *)app didFinishLaunchingWithOptions:(NSDictionary *)options {
    TIONewsHubPanel *panel=[TIONewsHubPanel new];UIViewController *root=[UIViewController new];
    UINavigationController *nav=[[UINavigationController alloc]initWithRootViewController:root];
    self.window=[[UIWindow alloc]initWithFrame:UIScreen.mainScreen.bounds];self.window.rootViewController=nav;[self.window makeKeyAndVisible];
    [nav pushViewController:panel animated:NO];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,NSEC_PER_SEC),dispatch_get_main_queue(),^{
        ((void (*)(id,SEL))objc_msgSend)(panel,NSSelectorFromString(@"armQueue"));
        Check(NewsArmCalls==0&&NewsEnqueueCalls==0,@"headless未接通时不enqueue、不arm被动hook");
        Check(nav.topViewController==panel&&self.window.rootViewController==nav&&!panel.presentedViewController,@"不跳转、不dismiss、不要求逐份手工点击");
        UIButton *button=(id)Find(panel.view,UIButton.class,@"自动入库未接通");Check(button&&!button.enabled,@"按钮明确不可用并disabled");
        UILabel *status=(id)Find(panel.view,UILabel.class,@"生成就绪：1 篇");
        Check(status&&[status.text containsString:@"官方已保存：0 篇"]&&[status.text containsString:@"原生交付：1 次"]&&[status.text containsString:@"不代表保存"],@"生成、原生交付与官方保存状态分离");
        Check([status.text containsString:@"尚未验证"]&&![status.text containsString:@"已导入"]&&![status.text containsString:@"进入官方"],@"显示阻断原因，无虚假成功或人工导入指引");
        UITableView *table=nil;for(UIView *view in panel.view.subviews)if([view isKindOfClass:UITableView.class])table=(id)view;
        [table.delegate tableView:table didSelectRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:1]];
        Check(nav.topViewController!=panel&&[nav.topViewController.title isEqual:@"已生成待预览文稿"],@"仅用户主动点击时可预览生成稿");
        [nav popToViewController:panel animated:NO];
        UIGraphicsImageRenderer *renderer=[[UIGraphicsImageRenderer alloc]initWithSize:self.window.bounds.size];
        UIImage *image=[renderer imageWithActions:^(UIGraphicsImageRendererContext *ctx){[self.window drawViewHierarchyInRect:self.window.bounds afterScreenUpdates:YES];}];
        NSString *path=[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,NSUserDomainMask,YES).firstObject stringByAppendingPathComponent:@"headless-unavailable.png"];
        Check([UIImagePNGRepresentation(image) writeToFile:path atomically:YES],@"不可用状态截图已记录");
        NSLog(@"HEADLESS_UI_ALL_PASS");
    });return YES;
}
@end
int main(int argc,char **argv){@autoreleasepool{return UIApplicationMain(argc,argv,nil,NSStringFromClass(HeadlessUIApp.class));}}
