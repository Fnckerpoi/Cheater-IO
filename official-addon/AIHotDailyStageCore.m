#import "AIHotDailyStageCore.h"
NSString * const TIOAIHotDailyStageErrorDomain = @"TIOAIHotDailyStage";
static NSError *StageError(NSInteger code, NSString *message) {
    return [NSError errorWithDomain:TIOAIHotDailyStageErrorDomain code:code userInfo:@{NSLocalizedDescriptionKey:message}];
}
BOOL TIOAIHotDailyStageHostAllowed(NSDictionary *info, NSString *expectedBundle) {
    if (![info isKindOfClass:NSDictionary.class] || ![expectedBundle isKindOfClass:NSString.class] || !expectedBundle.length) return NO;
    return [info[@"CFBundleIdentifier"] isKindOfClass:NSString.class] && [info[@"CFBundleIdentifier"] isEqual:expectedBundle] &&
        [info[@"CFBundleShortVersionString"] isKindOfClass:NSString.class] && [info[@"CFBundleShortVersionString"] isEqual:@"1.0.5"] &&
        [info[@"CFBundleVersion"] isKindOfClass:NSString.class] && [info[@"CFBundleVersion"] isEqual:@"201"];
}
static BOOL StageDay(NSString *day) {
    if (![day isKindOfClass:NSString.class] || day.length != 10) return NO;
    NSDateFormatter *f = [NSDateFormatter new]; f.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
    f.calendar = [[NSCalendar alloc] initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
    f.timeZone = [NSTimeZone timeZoneWithName:@"Asia/Shanghai"]; f.dateFormat = @"yyyy-MM-dd"; f.lenient = NO;
    NSDate *date = [f dateFromString:day]; return date && [[f stringFromDate:date] isEqual:day];
}
@interface TIOAIHotDailyStageExport ()
@property(nonatomic, copy, readwrite) NSString *filename;
@property(nonatomic, copy, readwrite) NSData *data;
@end
@implementation TIOAIHotDailyStageExport
@end
@interface TIOAIHotDailyStageState ()
@property(nonatomic, readwrite) NSUInteger generation;
@property(nonatomic, readwrite) BOOL busy;
@property(nonatomic, copy, readwrite) NSString *requestedDate;
@property(nonatomic, readwrite) TIOAIHotDailyReport *report;
@property(nonatomic, readwrite) NSError *error;
@end
@implementation TIOAIHotDailyStageState
- (NSUInteger)beginAtDate:(NSDate *)now {
    self.generation++; self.busy = YES; self.requestedDate = TIOAIHotDailyShanghaiDate(now); self.report = nil; self.error = nil;
    return self.generation;
}
- (void)cancel {
    self.generation++; self.busy = NO; self.report = nil; self.error = StageError(1, @"已取消。本次没有可导出的日报。");
}
- (BOOL)finishGeneration:(NSUInteger)generation report:(TIOAIHotDailyReport *)report error:(NSError *)error {
    if (!self.busy || generation != self.generation) return NO;
    self.busy = NO; self.report = nil;
    if (error) self.error = error;
    else if (![report isKindOfClass:TIOAIHotDailyReport.class] || !StageDay(self.requestedDate) || ![report.date isEqual:self.requestedDate] || ![report.stableID isEqual:[@"daily-" stringByAppendingString:self.requestedDate]]) self.error = StageError(2, @"返回日报与本次请求日期不符，不能预览或导出。");
    else { self.report = report; self.error = nil; }
    return YES;
}
- (TIOAIHotDailyStageExport *)exportAtDate:(NSDate *)now error:(NSError **)error {
    if (error) *error = nil;
    TIOAIHotDailyReport *report = self.report;
    if (self.busy || self.error || !report || !StageDay(self.requestedDate) || ![report.date isEqual:self.requestedDate] || ![self.requestedDate isEqual:TIOAIHotDailyShanghaiDate(now)] || ![report.stableID isEqual:[@"daily-" stringByAppendingString:self.requestedDate]]) {
        if (error) *error = StageError(3, @"仅能导出本次成功获取的北京时间当日日报，请重新手动获取。"); return nil;
    }
    NSString *text = report.readingText;
    NSString *canonical = [@"https://aihot.news/daily/" stringByAppendingString:self.requestedDate];
    if (![report.canonicalURL isEqual:canonical]) { if (error) *error = StageError(2, @"日报来源与日期不符，不能导出。"); return nil; }
    NSString *exportText = [text isKindOfClass:NSString.class] && text.length ? [text stringByAppendingFormat:@"\n\n官方日报来源：%@\n", canonical] : nil;
    NSData *data = [exportText dataUsingEncoding:NSUTF8StringEncoding allowLossyConversion:NO];
    if (!text.length || exportText.length > 12000 || !data || data.length > 48000) {
        if (error) *error = StageError(4, @"阅读稿为空或超出 12000 UTF-16 / 48000 UTF-8 限制；未裁剪或导出。"); return nil;
    }
    TIOAIHotDailyStageExport *result = [TIOAIHotDailyStageExport new];
    result.filename = [NSString stringWithFormat:@"AIHOT-%@-reading.txt", self.requestedDate]; result.data = data;
    return result;
}
@end

@interface TIOAIHotDailyStageShareFile ()
@property(nonatomic, copy, readwrite) NSURL *fileURL;
@property(nonatomic, copy) NSURL *directory;
@property(nonatomic, copy) NSURL *temporaryRoot;
@end
@implementation TIOAIHotDailyStageShareFile
+ (instancetype)fileWithExport:(TIOAIHotDailyStageExport *)export error:(NSError **)error {
    if (error) *error = nil;
    NSString *filename = export.filename;
    if (![filename isKindOfClass:NSString.class] || filename.length != 28 || ![filename hasPrefix:@"AIHOT-"] || ![filename hasSuffix:@"-reading.txt"] || !StageDay([filename substringWithRange:NSMakeRange(6, 10)]) || ![export.data isKindOfClass:NSData.class] || !export.data.length || export.data.length > 48000) {
        if (error) *error = StageError(5, @"无效的当期 TXT 导出数据或文件名。"); return nil;
    }
    TIOAIHotDailyStageShareFile *file = [self new];
    file.temporaryRoot = [NSURL fileURLWithPath:NSTemporaryDirectory() isDirectory:YES].URLByStandardizingPath.URLByResolvingSymlinksInPath;
    file.directory = [file.temporaryRoot URLByAppendingPathComponent:[@"TurboIOAIHotDailyStage-" stringByAppendingString:NSUUID.UUID.UUIDString] isDirectory:YES];
    if (![NSFileManager.defaultManager createDirectoryAtURL:file.directory withIntermediateDirectories:NO attributes:nil error:error]) { file.directory = nil; return nil; }
    file.fileURL = [file.directory URLByAppendingPathComponent:filename isDirectory:NO];
    if (![export.data writeToURL:file.fileURL options:NSDataWritingAtomic error:error]) { [file complete]; return nil; }
    return file;
}
- (void)complete {
    NSURL *directory = self.directory; self.directory = nil;
    if (!directory) return;
    NSString *prefix = @"TurboIOAIHotDailyStage-";
    NSString *component = directory.lastPathComponent;
    NSString *suffix = [component hasPrefix:prefix] ? [component substringFromIndex:prefix.length] : nil;
    NSURL *resolved = directory.URLByStandardizingPath.URLByResolvingSymlinksInPath;
    if ([resolved.path isEqual:directory.URLByStandardizingPath.path] && [resolved.URLByDeletingLastPathComponent.path isEqual:self.temporaryRoot.path] && suffix.length == 36 && [[NSUUID alloc] initWithUUIDString:suffix]) [NSFileManager.defaultManager removeItemAtURL:resolved error:nil];
}
- (void)dealloc { [self complete]; }
@end
