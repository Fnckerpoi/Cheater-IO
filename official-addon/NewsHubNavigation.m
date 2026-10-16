#import "NewsHubNavigation.h"
#import "TIONewsHubPanel.h"
#import "HostCompatibility.h"
#import "NewsHubIdentityPolicy.h"
#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <mach-o/dyld.h>
#import <dlfcn.h>

@interface TIONewsModuleRoot : UITableViewController
@property(nonatomic,strong) UIViewController *reader;
@end
@implementation TIONewsModuleRoot
- (void)viewDidLoad {
    [super viewDidLoad];
    self.title=@"新闻";
    self.tableView.accessibilityIdentifier=@"news-module-root";
    self.tableView.rowHeight=UITableViewAutomaticDimension;
    self.tableView.estimatedRowHeight=88;
    self.tableView.backgroundColor=UIColor.systemGroupedBackgroundColor;
}
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section { return 2; }
- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)path {
    UITableViewCell *cell=[[UITableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:nil];
    cell.textLabel.text=path.row==0?@"TinyFish · 新闻阅读":@"NewsHub · 多源新闻调度中枢";
    cell.detailTextLabel.text=path.row==0?@"公开新闻检索与原有提词器阅读":@"来源配置、每日调度与待导入队列";
    cell.accessibilityIdentifier=path.row==0?@"news-module-tinyfish":@"news-module-newshub";
    cell.textLabel.numberOfLines=cell.detailTextLabel.numberOfLines=0;
    cell.textLabel.font=[UIFont preferredFontForTextStyle:UIFontTextStyleBody];
    cell.detailTextLabel.font=[UIFont preferredFontForTextStyle:UIFontTextStyleFootnote];
    cell.textLabel.adjustsFontForContentSizeCategory=cell.detailTextLabel.adjustsFontForContentSizeCategory=YES;
    cell.detailTextLabel.textColor=UIColor.secondaryLabelColor;
    cell.accessoryType=UITableViewCellAccessoryDisclosureIndicator;
    cell.imageView.image=[UIImage systemImageNamed:path.row==0?@"magnifyingglass":@"newspaper"];
    return cell;
}
- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)path {
    [tableView deselectRowAtIndexPath:path animated:YES];
    if(path.section!=0||path.row<0||path.row>1)return;
    UIViewController *destination=path.row==0?self.reader:[TIONewsHubPanel new];
    if(destination)[self.navigationController pushViewController:destination animated:YES];
}
@end

static Class ResearchClass;
static void (*OriginalSet)(id,SEL,NSArray *);
static void (*OriginalSetAnimated)(id,SEL,NSArray *,BOOL);
static BOOL Installed;
static BOOL InstallFailed;

static NSArray *NewsRoots(UITabBarController *tabs,NSArray *controllers) {
    if(object_getClass(tabs)!=ResearchClass||controllers.count!=4)return controllers;
    id candidate=controllers[1];
    if(![candidate isKindOfClass:UINavigationController.class])return controllers;
    UINavigationController *nav=candidate;
    if(![nav.tabBarItem.title isEqual:@"新闻"]||nav.tabBarItem.tag!=1)return controllers;
    UIViewController *reader=nav.viewControllers.firstObject;
    if([reader isKindOfClass:TIONewsModuleRoot.class])return controllers;
    if(object_getClass(reader)!=NSClassFromString(@"TIONewsReader"))return controllers;
    TIONewsModuleRoot *root=[[TIONewsModuleRoot alloc]initWithStyle:UITableViewStyleInsetGrouped];
    root.reader=reader;
    root.title=@"新闻";
    [nav setViewControllers:@[root] animated:NO];
    return controllers;
}
static void SetRoots(id tabs,SEL selector,NSArray *controllers) {
    OriginalSet(tabs,selector,NewsRoots(tabs,controllers));
}
static void SetRootsAnimated(id tabs,SEL selector,NSArray *controllers,BOOL animated) {
    OriginalSetAnimated(tabs,selector,NewsRoots(tabs,controllers),animated);
}

static BOOL SetterABI(Method method,BOOL animated) {
    if(!method||method_getNumberOfArguments(method)!=(animated?4u:3u))return NO;
    char *result=method_copyReturnType(method),*objects=method_copyArgumentType(method,2);
    BOOL matches=result&&strcmp(result,"v")==0&&objects&&objects[0]=='@';
    free(result);free(objects);
    if(animated){char *flag=method_copyArgumentType(method,3);matches=matches&&flag&&(strcmp(flag,@encode(BOOL))==0);free(flag);}
    return matches;
}
static BOOL Declares(Class cls,SEL selector) {
    unsigned count=0;Method *methods=class_copyMethodList(cls,&count);BOOL found=NO;
    for(unsigned i=0;i<count;i++)if(method_getName(methods[i])==selector){found=YES;break;}
    free(methods);return found;
}
#if !TIO_NEWS_UI_VALIDATION
static BOOL OldAddonImage(Class cls) {
    if(!cls)return NO;
    const char *path=class_getImageName(cls);
    if(!path||![[[NSString stringWithUTF8String:path] lastPathComponent] isEqual:@"TurboIOPrivateAddon.dylib"])return NO;
    for(uint32_t i=0;i<_dyld_image_count();i++){
        const char *image=_dyld_get_image_name(i);if(!image||strcmp(path,image)!=0)continue;
        const struct mach_header_64 *header=(const void *)_dyld_get_image_header(i);
        if(!header||header->magic!=MH_MAGIC_64||header->filetype!=MH_DYLIB||header->ncmds>1024||header->sizeofcmds>1024*1024)return NO;
        const uint8_t *cursor=(const uint8_t *)(header+1),*end=cursor+header->sizeofcmds;
        for(uint32_t n=0;n<header->ncmds;n++){
            if((size_t)(end-cursor)<sizeof(struct load_command))return NO;
            const struct load_command *command=(const void *)cursor;
            if(command->cmdsize<sizeof(*command)||command->cmdsize%8||command->cmdsize>(size_t)(end-cursor))return NO;
            if(command->cmd==LC_UUID){
                if(command->cmdsize!=sizeof(struct uuid_command))return NO;
                NSUUID *uuid=[[NSUUID alloc]initWithUUIDBytes:((const struct uuid_command *)command)->uuid];
                return TIONewsHubAddonMatches([[[NSString stringWithUTF8String:path] lastPathComponent] UTF8String],uuid.UUIDString.UTF8String);
            }
            cursor+=command->cmdsize;
        }
    }
    return NO;
}
#endif
static BOOL CompatibleHost(void) {
#if TIO_NEWS_UI_VALIDATION
    return [NSBundle.mainBundle.bundleIdentifier isEqual:@"io.turboio.news-ui-validation"];
#else
    NSBundle *bundle=NSBundle.mainBundle;
    if(!TIONewsHubBundleMatches(bundle.bundleIdentifier.UTF8String))return NO;
    const struct mach_header *executable=NULL;
    for(uint32_t i=0;i<_dyld_image_count();i++){
        const char *path=_dyld_get_image_name(i);
        if(path&&strcmp(path,bundle.executablePath.fileSystemRepresentation)==0){executable=_dyld_get_image_header(i);break;}
    }
    return TIOHostImageMatches(executable,bundle.infoDictionary)&&OldAddonImage(ResearchClass)&&OldAddonImage(NSClassFromString(@"TIONewsReader"));
#endif
}
static void UpgradeExisting(UIViewController *controller,NSUInteger depth) {
    if(!controller||depth>12)return;
    if(object_getClass(controller)==ResearchClass){
        UITabBarController *tabs=(id)controller;
        NewsRoots(tabs,tabs.viewControllers);
        return;
    }
    for(UIViewController *child in controller.childViewControllers)UpgradeExisting(child,depth+1);
    UpgradeExisting(controller.presentedViewController,depth+1);
}
BOOL TIONewsHubNavigationInstall(void) {
    if(!NSThread.isMainThread)return NO;
    ResearchClass=NSClassFromString(@"TIOResearchTabs");
    if(!ResearchClass||!CompatibleHost()||InstallFailed)return NO;
    if(!Installed){
        SEL plain=@selector(setViewControllers:),animated=@selector(setViewControllers:animated:);
        Method first=class_getInstanceMethod(ResearchClass,plain),second=class_getInstanceMethod(ResearchClass,animated);
        if(!SetterABI(first,NO)||!SetterABI(second,YES)||Declares(ResearchClass,plain)||Declares(ResearchClass,animated))return NO;
        OriginalSet=(void (*)(id,SEL,NSArray *))method_getImplementation(first);
        OriginalSetAnimated=(void (*)(id,SEL,NSArray *,BOOL))method_getImplementation(second);
        // Add overrides to this exact class. Never replace the inherited UIKit Method.
        if(!class_addMethod(ResearchClass,plain,(IMP)SetRoots,method_getTypeEncoding(first)))return NO;
#if TIO_NEWS_TEST_FAIL_ANIMATED
        (void)SetRootsAnimated;
        BOOL secondAdded=NO;
#else
        BOOL secondAdded=class_addMethod(ResearchClass,animated,(IMP)SetRootsAnimated,method_getTypeEncoding(second));
#endif
        if(!secondAdded){
            Method ownPlain=class_getInstanceMethod(ResearchClass,plain);
            method_setImplementation(ownPlain,(IMP)OriginalSet);
            InstallFailed=YES;
            return NO;
        }
        Installed=YES;
    }
    for(UIScene *scene in UIApplication.sharedApplication.connectedScenes){
        if(![scene isKindOfClass:UIWindowScene.class])continue;
        for(UIWindow *window in ((UIWindowScene *)scene).windows)UpgradeExisting(window.rootViewController,0);
    }
    id<UIApplicationDelegate> delegate=UIApplication.sharedApplication.delegate;
    if([delegate respondsToSelector:@selector(window)])UpgradeExisting(delegate.window.rootViewController,0);
    return YES;
}
