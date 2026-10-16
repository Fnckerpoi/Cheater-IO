#import "TIONewsHubPanel.h"
#import "TIONewsScheduler.h"
#import "TIOImportQueue.h"

@interface TIONewsHubPanel () <UITableViewDataSource, UITableViewDelegate, TIONewsSchedulerDelegate>
@property(nonatomic, strong) UITableView *tableView;
@property(nonatomic, strong) UISwitch *scheduleSwitch;
@property(nonatomic, strong) UILabel *statusLabel;
@property(nonatomic, strong) UILabel *queueLabel;
@property(nonatomic, strong) UIButton *fetchNowButton;
@property(nonatomic, strong) UIButton *armQueueButton;
@end

@implementation TIONewsHubPanel

+ (void)showFromViewController:(UIViewController *)parent {
    TIONewsHubPanel *panel = [TIONewsHubPanel new];
    UINavigationController *nav = [[UINavigationController alloc] initWithRootViewController:panel];
    nav.modalPresentationStyle = UIModalPresentationFullScreen;
    [parent presentViewController:nav animated:YES completion:nil];
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"每日资讯调度中枢";
    self.view.backgroundColor = UIColor.systemBackgroundColor;

    self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"关闭" style:UIBarButtonItemStylePlain target:self action:@selector(close)];
    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemAdd target:self action:@selector(addNewSource)];

    [TIONewsScheduler sharedScheduler].delegate = self;

    [self setupHeaderAndTable];
    [self refreshUI];
}

- (void)close {
    if (self.navigationController.viewControllers.firstObject != self) {
        [self.navigationController popToRootViewControllerAnimated:YES];
    } else {
        [self dismissViewControllerAnimated:YES completion:nil];
    }
}

- (void)setupHeaderAndTable {
    UIView *header = [[UIView alloc] initWithFrame:CGRectMake(0, 0, self.view.bounds.size.width, 290)];
    header.backgroundColor = [UIColor secondarySystemBackgroundColor];

    // 定时开关
    UILabel *switchTitle = [[UILabel alloc] initWithFrame:CGRectMake(16, 16, 200, 30)];
    switchTitle.text = @"每日自动定时抓取";
    switchTitle.font = [UIFont boldSystemFontOfSize:16];
    [header addSubview:switchTitle];

    self.scheduleSwitch = [[UISwitch alloc] initWithFrame:CGRectMake(self.view.bounds.size.width - 70, 16, 50, 30)];
    self.scheduleSwitch.on = [TIONewsScheduler sharedScheduler].enabled;
    [self.scheduleSwitch addTarget:self action:@selector(toggleSchedule:) forControlEvents:UIControlEventValueChanged];
    [header addSubview:self.scheduleSwitch];

    // 状态标签
    self.statusLabel = [[UILabel alloc] initWithFrame:CGRectMake(16, 54, self.view.bounds.size.width - 32, 40)];
    self.statusLabel.numberOfLines = 0;
    self.statusLabel.font = [UIFont systemFontOfSize:13];
    self.statusLabel.textColor = UIColor.secondaryLabelColor;
    [header addSubview:self.statusLabel];

    // 立即抓取按钮
    self.fetchNowButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.fetchNowButton.frame = CGRectMake(16, 104, (self.view.bounds.size.width - 40) / 2, 44);
    [self.fetchNowButton setTitle:@"立即检查与生成" forState:UIControlStateNormal];
    self.fetchNowButton.backgroundColor = [UIColor systemBlueColor];
    [self.fetchNowButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.fetchNowButton.layer.cornerRadius = 8;
    [self.fetchNowButton addTarget:self action:@selector(fetchNow) forControlEvents:UIControlEventTouchUpInside];
    [header addSubview:self.fetchNowButton];

    // 后台入库可用性
    self.armQueueButton = [UIButton buttonWithType:UIButtonTypeSystem];
    self.armQueueButton.frame = CGRectMake(CGRectGetMaxX(self.fetchNowButton.frame) + 8, 104, (self.view.bounds.size.width - 40) / 2, 44);
    [self.armQueueButton setTitle:@"自动入库未接通" forState:UIControlStateNormal];
    self.armQueueButton.backgroundColor = [UIColor systemGreenColor];
    [self.armQueueButton setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    self.armQueueButton.layer.cornerRadius = 8;
    [self.armQueueButton addTarget:self action:@selector(armQueue) forControlEvents:UIControlEventTouchUpInside];
    [header addSubview:self.armQueueButton];

    // 队列状态
    self.queueLabel = [[UILabel alloc] initWithFrame:CGRectMake(16, 160, self.view.bounds.size.width - 32, 110)];
    self.queueLabel.numberOfLines = 0;
    self.queueLabel.font = [UIFont systemFontOfSize:13 weight:UIFontWeightMedium];
    [header addSubview:self.queueLabel];

    self.tableView = [[UITableView alloc] initWithFrame:self.view.bounds style:UITableViewStyleGrouped];
    self.tableView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.tableView.tableHeaderView = header;
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    [self.view addSubview:self.tableView];
}

- (void)toggleSchedule:(UISwitch *)sender {
    [TIONewsScheduler sharedScheduler].enabled = sender.on;
    [self refreshUI];
}

- (void)fetchNow {
    [[TIONewsScheduler sharedScheduler] triggerCheckNowForce:YES];
    [self refreshUI];
}

- (void)armQueue {
    TIOImportQueue *queue = [TIOImportQueue sharedQueue];
    if (!queue.headlessImportAvailable) {
        [self refreshUI];
        return;
    }
    NSArray<TIONewsArticle *> *articles = [[TIONewsScheduler sharedScheduler] todayArticles];
    if (!articles.count) return;
    [queue enqueueArticles:articles];
    [queue armQueueAutoImport];
    [self refreshUI];
}

- (void)refreshUI {
    self.statusLabel.text = [NSString stringWithFormat:@"调度状态：%@\n定时配置：%02ld:%02ld (北京时间)",
                             [TIONewsScheduler sharedScheduler].statusMessage,
                             (long)[TIONewsScheduler sharedScheduler].scheduledHour,
                             (long)[TIONewsScheduler sharedScheduler].scheduledMinute];

    TIOImportQueue *q = [TIOImportQueue sharedQueue];
    BOOL available = q.headlessImportAvailable;
    self.armQueueButton.enabled = available && [TIONewsScheduler sharedScheduler].todayArticles.count > 0;
    [self.armQueueButton setTitle:available ? @"后台自动入库" : @"自动入库未接通" forState:UIControlStateNormal];
    self.armQueueButton.backgroundColor = available ? UIColor.systemGreenColor : UIColor.systemGrayColor;
    NSString *reason = available ? @"以官方保存回执为准，生成与原生交付均不代表保存。" : q.headlessImportUnavailableReason;
    self.queueLabel.text = [NSString stringWithFormat:@"生成就绪：%lu 篇 · 官方已保存：%lu 篇\n原生交付：%lu 次（不代表保存）\n%@",
                            (unsigned long)[[TIONewsScheduler sharedScheduler] todayArticles].count,
                            (unsigned long)q.officialSavedCount, (unsigned long)q.suppliedCount, reason];
    self.queueLabel.textColor = UIColor.secondaryLabelColor;
    [self.tableView reloadData];
}

#pragma mark - 添加来源
- (void)addNewSource {
    UIAlertController *sheet = [UIAlertController alertControllerWithTitle:@"新增订阅来源" message:@"选择要添加的信息源类型" preferredStyle:UIAlertControllerStyleActionSheet];

    [sheet addAction:[UIAlertAction actionWithTitle:@"标准 RSS / Atom 订阅" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [self promptAddSourceWithType:TIONewsSourceTypeRSS typeName:@"RSS 订阅" placeholder:@"https://.../feed.xml"];
    }]];

    [sheet addAction:[UIAlertAction actionWithTitle:@"远程只读 MCP 来源" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [self promptAddSourceWithType:TIONewsSourceTypeMCP typeName:@"HTTP MCP 服务" placeholder:@"https://.../mcp"];
    }]];

    [sheet addAction:[UIAlertAction actionWithTitle:@"TinyFish 搜索 + 模型生成" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [self promptAddSourceWithType:TIONewsSourceTypeSearch typeName:@"搜索主题" placeholder:@"如：具身智能最新进展"];
    }]];

    [sheet addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [self presentViewController:sheet animated:YES completion:nil];
}

- (void)promptAddSourceWithType:(TIONewsSourceType)type typeName:(NSString *)typeName placeholder:(NSString *)placeholder {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:[NSString stringWithFormat:@"新增 %@", typeName] message:@"请输入展示名称与地址/主题" preferredStyle:UIAlertControllerStyleAlert];

    [alert addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
        textField.placeholder = @"展示名称 (如：科技早报)";
    }];
    [alert addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
        textField.placeholder = placeholder;
    }];

    [alert addAction:[UIAlertAction actionWithTitle:@"保存" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        NSString *name = alert.textFields[0].text;
        NSString *endpoint = alert.textFields[1].text;
        if (name.length && endpoint.length) {
            TIONewsSourceConfig *c = [TIONewsSourceConfig new];
            c.sourceID = NSUUID.UUID.UUIDString;
            c.name = name;
            c.type = type;
            c.endpointOrTopic = endpoint;
            c.enabled = YES;
            [[TIONewsScheduler sharedScheduler] addSource:c];
            [self refreshUI];
        }
    }]];

    [alert addAction:[UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

#pragma mark - UITableViewDataSource
- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    return 2;
}

- (NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section {
    return section == 0 ? @"已配置的信息来源（独立生成文稿）" : @"今日已生成的独立文稿";
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    if (section == 0) {
        return [TIONewsScheduler sharedScheduler].allSources.count;
    } else {
        return [TIONewsScheduler sharedScheduler].todayArticles.count;
    }
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"cell"] ?: [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:@"cell"];

    if (indexPath.section == 0) {
        TIONewsSourceConfig *c = [TIONewsScheduler sharedScheduler].allSources[indexPath.row];
        NSString *typeStr = @"";
        switch (c.type) {
            case TIONewsSourceTypeAIHot: typeStr = @"[AIHOT专线]"; break;
            case TIONewsSourceTypeRSS: typeStr = @"[RSS订阅]"; break;
            case TIONewsSourceTypeMCP: typeStr = @"[远程MCP]"; break;
            case TIONewsSourceTypeSearch: typeStr = @"[TinyFish搜索]"; break;
        }
        cell.textLabel.text = [NSString stringWithFormat:@"%@ %@", typeStr, c.name];
        cell.detailTextLabel.text = c.endpointOrTopic;
        cell.accessoryType = c.enabled ? UITableViewCellAccessoryCheckmark : UITableViewCellAccessoryNone;
    } else {
        TIONewsArticle *a = [TIONewsScheduler sharedScheduler].todayArticles[indexPath.row];
        cell.textLabel.text = a.title;
        cell.detailTextLabel.text = [NSString stringWithFormat:@"%lu 字符 · 来源：%@", (unsigned long)a.characterCount, a.sourceName];
        cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;
    }
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    [tableView deselectRowAtIndexPath:indexPath animated:YES];
    if (indexPath.section == 0) {
        // 切换开关
        NSMutableArray *sources = [[TIONewsScheduler sharedScheduler].allSources mutableCopy];
        TIONewsSourceConfig *c = sources[indexPath.row];
        c.enabled = !c.enabled;
        [[TIONewsScheduler sharedScheduler] saveSources:sources];
        [self refreshUI];
    } else {
        // 预览正文
        TIONewsArticle *a = [TIONewsScheduler sharedScheduler].todayArticles[indexPath.row];
        UIViewController *vc = [UIViewController new];
        vc.title = a.title;
        vc.view.backgroundColor = UIColor.systemBackgroundColor;
        UITextView *tv = [[UITextView alloc] initWithFrame:vc.view.bounds];
        tv.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        tv.editable = NO;
        tv.font = [UIFont systemFontOfSize:15];
        tv.text = a.readingText;
        [vc.view addSubview:tv];
        [self.navigationController pushViewController:vc animated:YES];
    }
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath {
    return indexPath.section == 0;
}

- (void)tableView:(UITableView *)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath {
    if (editingStyle == UITableViewCellEditingStyleDelete) {
        TIONewsSourceConfig *c = [TIONewsScheduler sharedScheduler].allSources[indexPath.row];
        [[TIONewsScheduler sharedScheduler] removeSourceWithID:c.sourceID];
        [self refreshUI];
    }
}

#pragma mark - TIONewsSchedulerDelegate
- (void)newsSchedulerDidUpdateStatus:(TIONSchedulerStatus)status message:(NSString *)message {
    [self refreshUI];
}

- (void)newsSchedulerDidGenerateArticles:(NSArray<TIONewsArticle *> *)articles {
    [self refreshUI];
}

@end
