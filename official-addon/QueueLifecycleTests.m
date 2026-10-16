#import <Foundation/Foundation.h>
#import <objc/runtime.h>
#import "TIOImportQueue.h"

static NSUInteger Checks;
static NSUInteger Failures;
static void Check(BOOL condition, const char *name) {
    Checks++;
    if (!condition) Failures++;
    printf("%s %s\n", condition ? "PASS" : "FAIL", name);
}

@interface FilePickerPlugin : NSObject
@property(nonatomic) BOOL isSaveFile;
@property(nonatomic, strong) id documentPickerController;
@property(nonatomic) BOOL shouldThrow;
@property(nonatomic) NSUInteger originalCalls;
@property(nonatomic) NSUInteger pending;
@property(nonatomic, strong) NSMutableArray<NSURL *> *urls;
@property(nonatomic, strong) NSMutableArray<NSData *> *delayedData;
- (void)resolvePickDocumentWithMultiPick:(BOOL)multi pickDirectory:(BOOL)directory;
- (void)handleResult:(NSArray<NSURL *> *)urls;
- (void)readAfterReturn:(NSURL *)url;
@end
@implementation FilePickerPlugin
- (instancetype)init {
    if ((self = [super init])) {
        _urls = [NSMutableArray new];
        _delayedData = [NSMutableArray new];
    }
    return self;
}
- (void)resolvePickDocumentWithMultiPick:(BOOL)multi pickDirectory:(BOOL)directory {
    (void)multi; (void)directory; self.originalCalls++;
}
- (void)handleResult:(NSArray<NSURL *> *)urls {
    [self.urls addObject:urls.firstObject];
    if (self.shouldThrow) [NSException raise:@"PublicFixtureError" format:@"fake helper unknown result"];
    self.pending++;
    [self performSelector:@selector(readAfterReturn:) withObject:urls.firstObject afterDelay:0];
}
- (void)readAfterReturn:(NSURL *)url {
    [self.delayedData addObject:[NSData dataWithContentsOfURL:url] ?: NSData.data];
    self.pending--;
}
@end

static TIONewsArticle *Article(NSString *identifier, NSString *body) {
    return [[TIONewsArticle alloc] initWithSourceID:identifier sourceName:@"public fixture" title:@"公开 测试稿" date:@"2026-10-14" readingText:body canonicalURL:nil];
}
#if TIO_IMPORT_QUEUE_LEGACY_TESTING
static void Drain(FilePickerPlugin *plugin) {
    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:1];
    while (plugin.pending && deadline.timeIntervalSinceNow > 0) [[NSRunLoop currentRunLoop] runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.001]];
    Check(plugin.pending == 0, "consumer finishes next run loop");
}
static void Prepare(NSString *identifier, NSString *body) {
    TIOImportQueue *queue = TIOImportQueue.sharedQueue;
    [queue clearQueue];
    [queue enqueueArticles:@[Article(identifier, body)]];
}
static NSSet<NSString *> *Directories(void) {
    return [NSSet setWithArray:[[NSFileManager defaultManager] contentsOfDirectoryAtPath:NSTemporaryDirectory() error:NULL] ?: @[]];
}
static NSString *NewDirectory(NSSet<NSString *> *before) {
    NSMutableSet *added = [Directories() mutableCopy];
    [added minusSet:before];
    for (NSString *name in added) if ([name hasPrefix:@"TurboQueue-"]) return [NSTemporaryDirectory() stringByAppendingPathComponent:name];
    return nil;
}
static void LegacyTests(void) {
    TIOImportQueue *queue = TIOImportQueue.sharedQueue;
    NSUInteger initialSupplied = queue.suppliedCount;
    Prepare(@"lifetime-first", @"public first");
    [queue enqueueArticles:@[Article(@"lifetime-last", @"公开最后一稿")]];
    Check([queue armQueueAutoImport], "valid public pair arms in explicit test build");
    NSSet *armedDirectories = Directories();
    Check([queue armQueueAutoImport] && [armedDirectories isEqualToSet:Directories()], "rearming a prepared item reuses its file");
    FilePickerPlugin *plugin = [FilePickerPlugin new];
    [plugin resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    Drain(plugin);
    Check([plugin.delayedData.firstObject isEqualToData:[@"public first" dataUsingEncoding:NSUTF8StringEncoding]], "first supplied bytes survive native return");
    [plugin resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    [queue disarm];
    [queue clearQueue];
    Drain(plugin);
    Check([plugin.delayedData.lastObject isEqualToData:[@"公开最后一稿" dataUsingEncoding:NSUTF8StringEncoding]], "last supplied bytes survive disarm and clear before delayed read");
    Check([NSData dataWithContentsOfURL:plugin.urls.firstObject] != nil, "first supplied file retained after clear");
    Check(queue.suppliedCount == initialSupplied + 2, "supplied count measures native attempts");
    Check(queue.officialSavedCount == 0 && queue.completedCount == 0, "native return never confirms official save");
    [queue enqueueArticles:@[Article(@"lifetime-first", @"public first")]];
    Check(queue.remainingCount == 0 && ![queue armQueueAutoImport], "clear cannot bypass unknown-result article budget");

    Prepare(@"exception", @"public unknown outcome");
    Check([queue armQueueAutoImport], "exception fixture armed");
    FilePickerPlugin *throwing = [FilePickerPlugin new];
    throwing.shouldThrow = YES;
    NSUInteger beforeThrow = queue.suppliedCount;
    [throwing resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    Check(queue.suppliedCount == beforeThrow + 1 && queue.officialSavedCount == 0, "exception remains supplied unknown and never saved");
    Check(queue.remainingCount == 1 && ![queue armQueueAutoImport], "exception retains item but forbids blind retry");
    [queue clearQueue];
    Check([NSData dataWithContentsOfURL:throwing.urls.firstObject] != nil, "exception URL remains readable after clear");
    [queue enqueueArticles:@[Article(@"exception", @"public unknown outcome")]];
    Check(queue.remainingCount == 0, "exception attempt budget survives clear");

    Prepare(@"context", @"public context");
    Check([queue armQueueAutoImport], "context fixture arms");
    FilePickerPlugin *context = [FilePickerPlugin new];
    NSUInteger beforeContext = queue.suppliedCount;
    context.isSaveFile = YES;
    [context resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    Check(context.isSaveFile && context.urls.count == 0 && queue.suppliedCount == beforeContext, "save context not mutated or consumed");
    context.isSaveFile = NO;
    context.documentPickerController = [NSObject new];
    [context resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    Check(context.documentPickerController != nil && context.urls.count == 0, "active document picker context not overwritten");
    context.documentPickerController = nil;
    [context resolvePickDocumentWithMultiPick:YES pickDirectory:NO];
    [context resolvePickDocumentWithMultiPick:NO pickDirectory:YES];
    Check(context.urls.count == 0 && queue.remainingCount == 1 && context.originalCalls == 0, "unsupported contexts fail closed without native picker fallback");
    [context resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    Drain(context);
    Check(context.urls.count == 1 && queue.suppliedCount == beforeContext + 1, "valid single-file context supplies once");

    NSArray<NSString *> *badSources = @[@"../escape", @"has/slash", @"has space", @"中文", [@"" stringByPaddingToLength:81 withString:@"a" startingAtIndex:0]];
    for (NSString *source in badSources) {
        Prepare(source, @"public body");
        NSSet *before = Directories();
        Check(![queue armQueueAutoImport] && queue.remainingCount == 1 && [before isEqualToSet:Directories()], "unsafe or overlong source rejected before file creation");
    }
    [queue clearQueue];
    TIONewsArticle *dateArticle = [[TIONewsArticle alloc] initWithSourceID:@"valid-date-source" sourceName:@"public" title:@"public" date:@"../bad-date" readingText:@"public" canonicalURL:nil];
    [queue enqueueArticles:@[dateArticle]];
    Check(![queue armQueueAutoImport] && queue.remainingCount == 1, "unsafe date component rejected");
    Prepare(@"empty", @"");
    Check(![queue armQueueAutoImport] && queue.remainingCount == 1, "empty encoded bytes rejected");
    unichar invalidUnits[] = {0xD800};
    NSString *invalid = [NSString stringWithCharacters:invalidUnits length:1];
    Prepare(@"invalid-utf8", invalid);
    Check(![queue armQueueAutoImport] && queue.remainingCount == 1, "unpaired surrogate cannot be lossily encoded");

    for (NSUInteger size = 999999; size <= 1000001; size++) {
        for (NSUInteger chinese = 0; chinese < 2; chinese++) {
            NSMutableString *body = [[@"" stringByPaddingToLength:chinese ? size / 3 : size withString:chinese ? @"中" : @"a" startingAtIndex:0] mutableCopy];
            if (chinese) for (NSUInteger i = 0; i < size % 3; i++) [body appendString:@"a"];
            Prepare([NSString stringWithFormat:@"gate-%lu-%lu", (unsigned long)size, (unsigned long)chinese], body);
            NSSet *before = Directories();
            BOOL armed = [queue armQueueAutoImport];
            Check(armed == (size <= 1000000), "UTF8 byte boundary independent of character count");
            Check(queue.remainingCount == 1 && queue.officialSavedCount == 0, "validation or arming does not dequeue or save");
            if (armed) {
                FilePickerPlugin *gate = [FilePickerPlugin new];
                [gate resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
                Drain(gate);
                Check([gate.delayedData.firstObject isEqualToData:[body dataUsingEncoding:NSUTF8StringEncoding]], "boundary file delivered byte-exact");
            } else {
                Check([before isEqualToSet:Directories()], "oversize does not create any directory or file");
            }
        }
    }

    Prepare(@"symlink-directory", @"public no-follow directory");
    NSSet *beforeDirectory = Directories();
    Check([queue armQueueAutoImport], "symlink directory fixture arms");
    NSString *directory = NewDirectory(beforeDirectory);
    NSString *moved = [directory stringByAppendingString:@"-moved"];
    BOOL movedOK = directory && [[NSFileManager defaultManager] moveItemAtPath:directory toPath:moved error:NULL];
    BOOL linked = movedOK && [[NSFileManager defaultManager] createSymbolicLinkAtPath:directory withDestinationPath:moved error:NULL];
    Check(linked, "isolated fixture replaces own directory with symlink");
    FilePickerPlugin *directoryPlugin = [FilePickerPlugin new];
    NSUInteger beforeSymlink = queue.suppliedCount;
    [directoryPlugin resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    [queue clearQueue];
    Check(directoryPlugin.urls.count == 0 && queue.suppliedCount == beforeSymlink, "symlink directory is not supplied");
    Check([[NSFileManager defaultManager] fileExistsAtPath:moved], "clear never deletes symlink target directory");

    Prepare(@"symlink-file", @"public no-follow file");
    NSSet *beforeFile = Directories();
    Check([queue armQueueAutoImport], "symlink file fixture arms");
    NSString *fileDirectory = NewDirectory(beforeFile);
    NSString *fileName = [[[NSFileManager defaultManager] contentsOfDirectoryAtPath:fileDirectory error:NULL] firstObject];
    NSString *filePath = [fileDirectory stringByAppendingPathComponent:fileName];
    NSString *movedFile = [fileDirectory stringByAppendingPathComponent:@"public-target.txt"];
    BOOL fileMoved = filePath && [[NSFileManager defaultManager] moveItemAtPath:filePath toPath:movedFile error:NULL];
    BOOL fileLinked = fileMoved && [[NSFileManager defaultManager] createSymbolicLinkAtPath:filePath withDestinationPath:movedFile error:NULL];
    Check(fileLinked, "isolated fixture replaces own file with symlink");
    FilePickerPlugin *filePlugin = [FilePickerPlugin new];
    [filePlugin resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
    [queue disarm];
    [queue clearQueue];
    Check(filePlugin.urls.count == 0 && [[NSFileManager defaultManager] fileExistsAtPath:movedFile], "symlink file not supplied and target never deleted");
}
#endif
int main(int argc, const char *argv[]) {
    @autoreleasepool {
        TIOImportQueue *queue = TIOImportQueue.sharedQueue;
        if (argc > 1 && !strcmp(argv[1], "--symlink-root")) {
            [queue enqueueArticles:@[Article(@"root-symlink", @"public")]];
            Check(![queue armQueueAutoImport], "symlink temporary root rejected");
        } else {
            Check(!queue.headlessImportAvailable && queue.headlessImportUnavailableReason.length > 0, "no unverified headless backend advertised");
#if TIO_IMPORT_QUEUE_LEGACY_TESTING
            LegacyTests();
#else
            [queue enqueueArticles:@[Article(@"production", @"public production draft")]];
            Method method = class_getInstanceMethod(FilePickerPlugin.class, @selector(resolvePickDocumentWithMultiPick:pickDirectory:));
            IMP original = method_getImplementation(method);
            Check(![queue armQueueAutoImport], "production arm refuses absent backend");
            [queue triggerAutoImportFlow];
            Check(method_getImplementation(method) == original, "public arm and trigger install no hook");
            FilePickerPlugin *plugin = [FilePickerPlugin new];
            [plugin resolvePickDocumentWithMultiPick:NO pickDirectory:NO];
            Check(plugin.originalCalls == 1 && plugin.urls.count == 0, "production never intercepts native picker");
            Check(queue.remainingCount == 1 && queue.suppliedCount == 0 && queue.officialSavedCount == 0 && queue.completedCount == 0, "fail-closed production preserves queue without false counts");
#endif
        }
        printf("SUMMARY checks=%lu failures=%lu\n", (unsigned long)Checks, (unsigned long)Failures);
        return Failures ? 1 : 0;
    }
}
