#import "TIOImportQueue.h"
#import <fcntl.h>
#import <sys/stat.h>
#import <unistd.h>
#if TIO_IMPORT_QUEUE_LEGACY_TESTING
#import <objc/runtime.h>
#import <objc/message.h>
#endif

@interface TIOImportQueue ()
@property(nonatomic, strong) NSMutableArray<TIONewsArticle *> *queue;
@property(nonatomic, strong) NSMutableSet<NSString *> *attemptedArticleIDs;
@property(nonatomic, assign) BOOL isArmed;
@property(nonatomic, strong, nullable) NSURL *currentTempFileURL;
@property(nonatomic, assign) NSUInteger suppliedCount;
#if TIO_IMPORT_QUEUE_LEGACY_TESTING
@property(nonatomic, assign) dev_t currentDirectoryDevice;
@property(nonatomic, assign) ino_t currentDirectoryInode;
@property(nonatomic, assign) ino_t currentFileInode;
@property(nonatomic, assign) off_t currentFileSize;
#endif
@end

@implementation TIOImportQueue
+ (instancetype)sharedQueue {
    static TIOImportQueue *instance;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{ instance = [TIOImportQueue new]; });
    return instance;
}
- (instancetype)init {
    if ((self = [super init])) {
        _queue = [NSMutableArray new];
        _attemptedArticleIDs = [NSMutableSet new];
    }
    return self;
}
- (void)enqueueArticles:(NSArray<TIONewsArticle *> *)articles {
    @synchronized (self) {
        for (TIONewsArticle *article in articles) {
            if (!article.articleID.length || [_attemptedArticleIDs containsObject:article.articleID]) continue;
            BOOL exists = NO;
            for (TIONewsArticle *item in _queue) {
                if ([item.articleID isEqualToString:article.articleID]) { exists = YES; break; }
            }
            if (!exists) [_queue addObject:article];
        }
    }
}
- (void)clearQueue {
    @synchronized (self) {
        [self disarm];
        [_queue removeAllObjects];
        // Unknown handoffs retain their process-local attempt budget and files.
    }
}
- (NSUInteger)remainingCount { @synchronized (self) { return _queue.count; } }
- (nullable TIONewsArticle *)currentArticle { @synchronized (self) { return _queue.firstObject; } }
- (NSUInteger)officialSavedCount { return 0; }
- (NSUInteger)completedCount { return self.officialSavedCount; }
- (BOOL)headlessImportAvailable { return NO; }
- (NSString *)headlessImportUnavailableReason {
    return @"尚未接通并验证无页面跳转的官方稿件入库接口；已停用自动导入，不会回退到手动导入。";
}
- (void)triggerAutoImportFlow {
    // No verified headless backend: do not navigate, dismiss, arm, or install hooks.
}
- (void)disarm {
    @synchronized (self) {
        _isArmed = NO;
        _currentTempFileURL = nil;
        // Native return is not consumer acknowledgement. No unproven cleanup/TTL.
    }
}
- (BOOL)armQueueAutoImport {
#if TIO_IMPORT_QUEUE_LEGACY_TESTING
    @synchronized (self) { return [self armLegacySupplyForTesting]; }
#else
    return NO;
#endif
}

#if TIO_IMPORT_QUEUE_LEGACY_TESTING
static BOOL SafeComponent(NSString *value, NSUInteger limit) {
    if (!value.length || value.length > limit) return NO;
    NSCharacterSet *allowed = [NSCharacterSet characterSetWithCharactersInString:@"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"];
    return [value rangeOfCharacterFromSet:allowed.invertedSet].location == NSNotFound;
}
- (BOOL)currentFileIsIntact {
    if (!_currentTempFileURL) return NO;
    struct stat directory, file;
    return lstat(_currentTempFileURL.URLByDeletingLastPathComponent.fileSystemRepresentation, &directory) == 0 &&
        S_ISDIR(directory.st_mode) && directory.st_dev == _currentDirectoryDevice && directory.st_ino == _currentDirectoryInode &&
        lstat(_currentTempFileURL.fileSystemRepresentation, &file) == 0 && S_ISREG(file.st_mode) &&
        file.st_dev == _currentDirectoryDevice && file.st_ino == _currentFileInode && file.st_nlink == 1 && file.st_size == _currentFileSize;
}
- (BOOL)armLegacySupplyForTesting {
    TIONewsArticle *article = _queue.firstObject;
    if (!article || [_attemptedArticleIDs containsObject:article.articleID]) { [self disarm]; return NO; }
    if (_isArmed) return [self currentFileIsIntact];
    NSData *data = [article.readingText dataUsingEncoding:NSUTF8StringEncoding allowLossyConversion:NO];
    if (!data.length || data.length > 1000000 || !SafeComponent(article.sourceID, 80) || !SafeComponent(article.date, 16)) return NO;
    NSString *safeID = [article.sourceID stringByReplacingOccurrencesOfString:@"-" withString:@"_"];
    NSString *date = [article.date stringByReplacingOccurrencesOfString:@"-" withString:@""];
    NSString *basename = [NSString stringWithFormat:@"News_%@_%@_%@.txt", safeID, date, NSUUID.UUID.UUIDString];
    if (basename.length > 180) return NO;

    const char *configuredTemp = getenv("TMPDIR");
    if (configuredTemp && configuredTemp[0]) {
        NSString *configuredRoot = [NSString stringWithUTF8String:configuredTemp];
        while (configuredRoot.length > 1 && [configuredRoot hasSuffix:@"/"]) configuredRoot = [configuredRoot substringToIndex:configuredRoot.length - 1];
        struct stat configuredInfo;
        if (!configuredRoot.isAbsolutePath || lstat(configuredRoot.fileSystemRepresentation, &configuredInfo) != 0 || !S_ISDIR(configuredInfo.st_mode)) return NO;
    }
    NSString *tempRoot = NSTemporaryDirectory().stringByStandardizingPath;
    struct stat rootInfo;
    if (lstat(tempRoot.fileSystemRepresentation, &rootInfo) != 0 || !S_ISDIR(rootInfo.st_mode)) return NO;
    NSString *canonicalRoot = tempRoot.stringByResolvingSymlinksInPath;
    int rootFD = open(canonicalRoot.fileSystemRepresentation, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (rootFD < 0) return NO;
    NSString *directoryName = [@"TurboQueue-" stringByAppendingString:NSUUID.UUID.UUIDString];
    if (mkdirat(rootFD, directoryName.UTF8String, 0700) != 0) { close(rootFD); return NO; }
    int directoryFD = openat(rootFD, directoryName.UTF8String, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    int fileFD = directoryFD < 0 ? -1 : openat(directoryFD, basename.UTF8String, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    BOOL written = fileFD >= 0;
    NSUInteger offset = 0;
    while (written && offset < data.length) {
        ssize_t count = write(fileFD, (const char *)data.bytes + offset, data.length - offset);
        if (count <= 0) { written = NO; break; }
        offset += (NSUInteger)count;
    }
    struct stat directoryInfo, fileInfo;
    if (written) written = fstat(directoryFD, &directoryInfo) == 0 && fstat(fileFD, &fileInfo) == 0;
    if (fileFD >= 0) close(fileFD);
    if (!written) {
        if (directoryFD >= 0) unlinkat(directoryFD, basename.UTF8String, 0);
        if (directoryFD >= 0) close(directoryFD);
        unlinkat(rootFD, directoryName.UTF8String, AT_REMOVEDIR);
        close(rootFD);
        return NO;
    }
    close(directoryFD);
    close(rootFD);
    _currentTempFileURL = [NSURL fileURLWithPath:[[canonicalRoot stringByAppendingPathComponent:directoryName] stringByAppendingPathComponent:basename]];
    _currentDirectoryDevice = directoryInfo.st_dev;
    _currentDirectoryInode = directoryInfo.st_ino;
    _currentFileInode = fileInfo.st_ino;
    _currentFileSize = fileInfo.st_size;
    _isArmed = YES;
    if (![self currentFileIsIntact] || ![self installLegacyTestHookIfNeeded]) { [self disarm]; return NO; }
    return YES;
}

static BOOL MethodMatches(Class cls, SEL selector, const char *result, NSArray<NSString *> *arguments) {
    Method method = class_getInstanceMethod(cls, selector);
    if (!method || method_getNumberOfArguments(method) != arguments.count + 2) return NO;
    char encoding[64];
    method_getReturnType(method, encoding, sizeof(encoding));
    if (strcmp(encoding, result)) return NO;
    for (NSUInteger i = 0; i < arguments.count; i++) {
        method_getArgumentType(method, (unsigned int)i + 2, encoding, sizeof(encoding));
        if (strcmp(encoding, arguments[i].UTF8String)) return NO;
    }
    return YES;
}
- (BOOL)installLegacyTestHookIfNeeded {
    static BOOL installed = NO;
    if (installed) return YES;
    Class cls = NSClassFromString(@"FilePickerPlugin");
    SEL pickSelector = NSSelectorFromString(@"resolvePickDocumentWithMultiPick:pickDirectory:");
    if (!cls || !MethodMatches(cls, pickSelector, @encode(void), @[@(@encode(BOOL)), @(@encode(BOOL))])) return NO;
    Method method = class_getInstanceMethod(cls, pickSelector);
    IMP original = method_getImplementation(method);
    IMP hook = imp_implementationWithBlock(^(id plugin, BOOL multi, BOOL directory) {
        TIOImportQueue *manager = TIOImportQueue.sharedQueue;
        @synchronized (manager) {
            if (manager.isArmed) {
                SEL resultSelector = NSSelectorFromString(@"handleResult:");
                Class pluginClass = [plugin class];
                BOOL contextValid = !multi && !directory &&
                    MethodMatches(pluginClass, resultSelector, @encode(void), @[@(@encode(id))]) &&
                    MethodMatches(pluginClass, NSSelectorFromString(@"isSaveFile"), @encode(BOOL), @[]) &&
                    MethodMatches(pluginClass, NSSelectorFromString(@"documentPickerController"), @encode(id), @[]);
                if (contextValid) {
                    @try {
                        contextValid = !((BOOL (*)(id, SEL))objc_msgSend)(plugin, NSSelectorFromString(@"isSaveFile")) &&
                            !((id (*)(id, SEL))objc_msgSend)(plugin, NSSelectorFromString(@"documentPickerController"));
                    } @catch (NSException *exception) { (void)exception; contextValid = NO; }
                }
                if (!contextValid || ![manager currentFileIsIntact]) return;
                TIONewsArticle *article = manager.currentArticle;
                if (!article || [manager.attemptedArticleIDs containsObject:article.articleID]) { [manager disarm]; return; }
                NSURL *url = manager.currentTempFileURL;
                [manager.attemptedArticleIDs addObject:article.articleID];
                manager.suppliedCount++;
                [manager disarm];
                @try {
                    ((void (*)(id, SEL, id))objc_msgSend)(plugin, resultSelector, @[url]);
                } @catch (NSException *exception) {
                    (void)exception;
                    return;
                }
                // Supplied but unconfirmed, not saved; never retry this ID blindly.
                if (manager.queue.firstObject == article) [manager.queue removeObjectAtIndex:0];
                if (manager.queue.count) [manager armLegacySupplyForTesting];
                return;
            }
        }
        ((void (*)(id, SEL, BOOL, BOOL))original)(plugin, pickSelector, multi, directory);
    });
    method_setImplementation(method, hook);
    installed = YES;
    return YES;
}
#endif
@end
