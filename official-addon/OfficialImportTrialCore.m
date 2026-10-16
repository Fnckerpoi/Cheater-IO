#import "OfficialImportTrialCore.h"
#import <CommonCrypto/CommonDigest.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <math.h>

NSString *TIOImportTrialSHA(NSData *data){unsigned char hash[CC_SHA256_DIGEST_LENGTH];CC_SHA256(data.bytes,(CC_LONG)data.length,hash);NSMutableString *s=[NSMutableString new];for(unsigned i=0;i<sizeof(hash);i++)[s appendFormat:@"%02x",hash[i]];return s;}
static BOOL IsBoolean(id v){return [v isKindOfClass:NSNumber.class]&&CFGetTypeID((__bridge CFTypeRef)v)==CFBooleanGetTypeID();}
static BOOL Filters(id v){if(![v isKindOfClass:NSArray.class]||[v count]>16)return NO;NSCharacterSet *bad=[[NSCharacterSet characterSetWithCharactersInString:@"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_"] invertedSet];for(id s in v)if(![s isKindOfClass:NSString.class]||![s length]||[s length]>160||[s rangeOfCharacterFromSet:bad].location!=NSNotFound)return NO;return YES;}
NSDictionary *TIOImportTrialProfile(NSString *method,id args){
 if(![method isKindOfClass:NSString.class]||![@[@"any",@"custom"] containsObject:method]||![args isKindOfClass:NSDictionary.class])return nil;
 NSSet *allowed=[NSSet setWithArray:@[@"allowMultipleSelection",@"withData",@"compressionQuality",@"allowedExtensions",@"allowedUniformTypeIdentifiers",@"allowCompression",@"allowedMimeTypes",@"dialogTitle",@"readData",@"lockParentWindow",@"type"]];
 for(id key in args)if(![key isKindOfClass:NSString.class]||![allowed containsObject:key])return nil;
 if(!IsBoolean(args[@"allowMultipleSelection"])||[args[@"allowMultipleSelection"] boolValue]||!IsBoolean(args[@"withData"]))return nil;
 NSMutableDictionary *p=[@{@"method":[method copy],@"allowMultipleSelection":@NO,@"withData":args[@"withData"]} mutableCopy];
 id q=args[@"compressionQuality"];if(q){if(![q isKindOfClass:NSNumber.class]||IsBoolean(q)||!isfinite([q doubleValue])||[q doubleValue]!=[q integerValue]||[q integerValue]<0||[q integerValue]>100)return nil;p[@"compressionQuality"]=q;}
 for(NSString *k in @[@"allowedExtensions",@"allowedUniformTypeIdentifiers"]){id v=args[k];if(v==NSNull.null)p[k]=NSNull.null;else if(v){if(!Filters(v))return nil;NSMutableArray *f=[NSMutableArray new];for(NSString *value in v)[f addObject:[value copy]];p[k]=[f copy];}}
 if([method isEqual:@"custom"]&&!([p[@"allowedExtensions"] isKindOfClass:NSArray.class]&&[p[@"allowedExtensions"] count])&&!([p[@"allowedUniformTypeIdentifiers"] isKindOfClass:NSArray.class]&&[p[@"allowedUniformTypeIdentifiers"] count]))return nil;
 return p;
}
static NSData *PublicRead(NSURL *url,NSString *root,NSString *name,NSUInteger size){
 if(![url isKindOfClass:NSURL.class]||!url.isFileURL||![url.lastPathComponent isEqual:name]||![[url.URLByDeletingLastPathComponent.path stringByResolvingSymlinksInPath] isEqual:root])return nil;
 // Only one basename below an approved root, never enumerate or follow a file link.
 NSString *rawParent=[url.URLByDeletingLastPathComponent.path stringByStandardizingPath];if(![rawParent isEqual:root]&&![rawParent isEqual:[NSTemporaryDirectory() stringByStandardizingPath]])return nil;
 int dir=open(root.fileSystemRepresentation,O_RDONLY|O_DIRECTORY|O_NOFOLLOW);if(dir<0)return nil;
 int fd=openat(dir,name.fileSystemRepresentation,O_RDONLY|O_NOFOLLOW|O_NONBLOCK);close(dir);if(fd<0)return nil;
 struct stat st;if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_size!=(off_t)size||size>48000){close(fd);return nil;}
 NSMutableData *d=[NSMutableData dataWithLength:size];size_t at=0;while(at<size){ssize_t n=read(fd,(char *)d.mutableBytes+at,size-at);if(n<=0){close(fd);return nil;}at+=(size_t)n;}
 char more;ssize_t tail=read(fd,&more,1);struct stat end;BOOL stable=!fstat(fd,&end)&&st.st_ino==end.st_ino&&st.st_dev==end.st_dev&&st.st_size==end.st_size&&st.st_mtimespec.tv_sec==end.st_mtimespec.tv_sec&&st.st_mtimespec.tv_nsec==end.st_mtimespec.tv_nsec;close(fd);return tail==0&&stable?d:nil;
}
@implementation TIOImportTrialCore{
 NSString *_state,*_publicText,*_hash,*_root,*_name;NSURL *_fileURL;NSUInteger _size,_generation;NSTimeInterval _deadline,_lease;BOOL _armed,_functional,_requested,_matched,_consumed,_saved,_spent,_submitted,_nativeCleared;NSDictionary *_profile,*_candidate;NSMutableDictionary *_summary;
}
- (instancetype)init{if((self=[super init])){_state=@"DISABLED";_summary=[NSMutableDictionary new];}return self;}
- (NSUInteger)generation{return _generation;}
- (NSString *)state{return _state;}- (NSDictionary *)summary{NSData *d=[NSJSONSerialization dataWithJSONObject:_summary options:0 error:nil];return d?[NSJSONSerialization JSONObjectWithData:d options:0 error:nil]:@{};}- (NSURL *)fileURL{return _fileURL;}- (NSString *)publicText{return _publicText?:@"";}- (BOOL)armed{return _armed;}- (BOOL)functional{return _functional;}- (BOOL)requested{return _requested;}- (BOOL)canConfirm{return _matched&&!_consumed;}- (BOOL)canFunction{return _matched&&_consumed&&_profile&&!_spent&&!_armed;}
- (void)invalidateAuthorization{_generation++;_armed=_functional=_requested=_matched=_consumed=_saved=NO;_profile=_candidate=nil;[_summary removeAllObjects];}
- (BOOL)createFixture{
 if(_armed||_fileURL)return NO;[self invalidateAuthorization];_submitted=_nativeCleared=NO;
 NSString *base=[NSTemporaryDirectory() stringByResolvingSymlinksInPath];_root=[base stringByAppendingPathComponent:[@"TIOImportTrial-" stringByAppendingString:NSUUID.UUID.UUIDString]];
 if(mkdir(_root.fileSystemRepresentation,0700))return NO;
 _name=[NSString stringWithFormat:@"TurboIO-Public-7392-%@.txt",NSUUID.UUID.UUIDString];_fileURL=[NSURL fileURLWithPath:[_root stringByAppendingPathComponent:_name]];
 _publicText=@"TurboIO 公开导入验证 7392\n这是一份不含个人资料的公开测试稿。\n第一段：官方文件导入返回之后，请核对标题与正文。\n第二段：这不是保存成功回执。若出现编辑器，请由用户保存并核对官方稿件列表。\n尾部标记 7392 END\n";
 NSData *d=[_publicText dataUsingEncoding:NSUTF8StringEncoding];_size=d.length;_hash=TIOImportTrialSHA(d);
 int fd=open(_fileURL.path.fileSystemRepresentation,O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW,0600);if(fd<0)return NO;size_t at=0;while(at<d.length){ssize_t n=write(fd,(const char *)d.bytes+at,d.length-at);if(n<=0){close(fd);return NO;}at+=(size_t)n;}close(fd);
 _lease=NSProcessInfo.processInfo.systemUptime+3600;_summary[@"publicManifest"]=@{@"basename":_name,@"size":@(_size),@"sha256":_hash,@"scope":@"PUBLIC_FIXTURE_ONLY_7392"};return [self verifyOwnFile];
}
- (BOOL)verifyOwnFile{NSData *d=PublicRead(_fileURL,_root,_name,_size);return d&&[TIOImportTrialSHA(d) isEqual:_hash];}
- (BOOL)armFunctional:(BOOL)functional now:(NSTimeInterval)now{
 if(_armed||![self verifyOwnFile]||now>=_lease||(functional&&![self canFunction]))return NO;
 _generation++;_armed=YES;_functional=functional;_requested=NO;_candidate=nil;if(functional){_matched=NO;_saved=NO;_consumed=NO;[_summary removeObjectForKey:@"response"];[_summary removeObjectForKey:@"nativePendingCleared"];[_summary removeObjectForKey:@"confirmation"];[_summary removeObjectForKey:@"save"];}_deadline=now+120;_state=functional?@"B_ARMED_UNSAVED":@"A_ARMED";
 if(!functional){NSDictionary *manifest=_summary[@"publicManifest"];[self invalidateAuthorization];if(manifest)_summary[@"publicManifest"]=manifest;_armed=YES;_state=@"A_ARMED";_summary[@"runToken"]=NSUUID.UUID.UUIDString;}else _summary[@"runToken"]=NSUUID.UUID.UUIDString;
 return YES;
}
- (void)expireAt:(NSTimeInterval)now{if(_armed&&now>=_deadline)[self stop:@"TIMEOUT_OBSERVER_DISABLED"];}
- (BOOL)acceptMethod:(NSString *)method args:(id)args now:(NSTimeInterval)now{
 [self expireAt:now];if(!_armed)return NO;if(_requested){[self stop:@"REENTRANT_DISABLED"];return NO;}
 NSDictionary *p=TIOImportTrialProfile(method,args);
 if(!p||(_functional&&![p isEqual:_profile])){
  NSMutableDictionary *unrecognized=[NSMutableDictionary new];
  if(method)unrecognized[@"method"]=[method copy];
  if([args isKindOfClass:NSDictionary.class]){
   unrecognized[@"argKeys"]=[[args allKeys] sortedArrayUsingSelector:@selector(compare:)];
   if(args[@"allowMultipleSelection"])unrecognized[@"allowMultipleSelection"]=args[@"allowMultipleSelection"];
   if(args[@"allowedExtensions"])unrecognized[@"allowedExtensions"]=args[@"allowedExtensions"];
   if(args[@"withData"])unrecognized[@"withData"]=args[@"withData"];
  }
  _summary[@"unrecognizedCall"]=[unrecognized copy];
  [self stop:@"UNRECOGNIZED_OR_MISMATCH_DISABLED"];
  return NO;
 }
 _candidate=p;_requested=YES;_state=_functional?@"B_REQUEST_MATCHED_UNSAVED":@"A_WAITING_PUBLIC_RESULT";return YES;
}
- (BOOL)observeResult:(id)result profile:(NSDictionary *)profile now:(NSTimeInterval)now{
 [self expireAt:now];if(!_armed||!_requested||![_candidate isEqual:profile])return NO;
 if(![result isKindOfClass:NSArray.class]||[result count]!=1){[self stop:result==nil||result==NSNull.null?@"CANCELLED":@"UNMATCHED_RESULT_DISABLED"];return NO;}
 id row=result[0];if(![row isKindOfClass:NSDictionary.class]){[self stop:@"UNMATCHED_RESULT_DISABLED"];return NO;}
 for(id k in row)if(![@[@"path",@"identifier",@"name",@"size",@"bytes"] containsObject:k]){[self stop:@"UNKNOWN_SHAPE_DISABLED"];return NO;}
 id name=row[@"name"],sz=row[@"size"],path=row[@"path"],identifier=row[@"identifier"];
 if(![name isKindOfClass:NSString.class]||![name isEqual:_name]||![sz isKindOfClass:NSNumber.class]||IsBoolean(sz)||[sz doubleValue]!=(double)_size||![path isKindOfClass:NSString.class]||![identifier isKindOfClass:NSString.class]){[self stop:@"UNMATCHED_PUBLIC_IDENTITY_DISABLED"];return NO;}
 NSURL *url=[NSURL fileURLWithPath:path],*ident=[NSURL URLWithString:identifier];if(!ident.isFileURL||![ident.path isEqual:url.path]){[self stop:@"UNMATCHED_URL_SHAPE_DISABLED"];return NO;}
 NSData *d=nil;id bytes=row[@"bytes"];
 if([profile[@"withData"] boolValue]){if([bytes isKindOfClass:NSData.class])d=bytes;}
 else if(!bytes){NSString *temp=[NSTemporaryDirectory() stringByResolvingSymlinksInPath];NSString *parent=[url.URLByDeletingLastPathComponent.path stringByResolvingSymlinksInPath];if([parent isEqual:temp]||[parent isEqual:_root])d=PublicRead(url,parent,_name,_size);}
 if(!d||d.length!=_size||![TIOImportTrialSHA(d) isEqual:_hash]){[self stop:@"PUBLIC_HASH_OR_CODEC_MISMATCH_DISABLED"];return NO;}
 _profile=[profile copy];_matched=YES;_summary[@"request"]=_profile;_summary[@"response"]=@{@"arrayCount":@1,@"keys":[[row allKeys] sortedArrayUsingSelector:@selector(compare:)],@"sha256":_hash,@"size":@(_size),@"bytesType":[profile[@"withData"] boolValue]?@"NSData":@"ABSENT",@"identifierMeaning":@"FILE_URL_NOT_DOCID"};
 _armed=NO;_candidate=nil;_state=_functional?@"B_NATIVE_RETURNED_SAVE_UNKNOWN":@"A_PUBLIC_MATCHED_CONSUMPTION_UNKNOWN";return YES;
}
- (BOOL)beginSubmissionAt:(NSTimeInterval)now{[self expireAt:now];if(!_armed||!_functional||!_requested||_spent||![self verifyOwnFile])return NO;_spent=YES;_submitted=YES;_nativeCleared=NO;_lease=MAX(_lease,now+3600);_state=@"B_SUBMITTED_SAVE_UNKNOWN";return YES;}
- (void)submissionCleared:(BOOL)cleared{if(!_submitted)return;_nativeCleared=cleared;_summary[@"nativePendingCleared"]=@(cleared);_summary[@"publicResultMatched"]=@(_matched);_armed=NO;_candidate=nil;_state=!cleared?@"B_PENDING_UNKNOWN_NO_RETRY":(_matched?@"B_RETURNED_USER_SAVE_UNKNOWN":@"B_RESULT_MISMATCH_SAVE_UNKNOWN_NO_RETRY");}
- (BOOL)confirmConsumption{if(!_matched||_armed)return NO;_consumed=YES;_summary[@"confirmation"]=@"USER_CONFIRMED_CONSUMPTION_NOT_API_PASS";return YES;}
- (BOOL)confirmSave{if(!_consumed||_armed)return NO;_saved=YES;_summary[@"save"]=@"USER_CONFIRMED_SAVE_LIST_TITLE_BODY_NOT_API_PASS";return YES;}
- (void)stop:(NSString *)reason{_armed=NO;_candidate=nil;_state=reason;}
- (BOOL)cleanupAt:(NSTimeInterval)now{
 if(_armed||(_submitted&&!_nativeCleared)||(_submitted&&!_saved&&now<_lease)||(!_saved&&now<_lease)||!_fileURL)return NO;
 NSString *base=[NSTemporaryDirectory() stringByResolvingSymlinksInPath];if(![[_root stringByDeletingLastPathComponent] isEqual:base]||![_root.lastPathComponent hasPrefix:@"TIOImportTrial-"]||![[NSUUID alloc] initWithUUIDString:[_root.lastPathComponent substringFromIndex:15]]||![_fileURL.path isEqual:[_root stringByAppendingPathComponent:_name]])return NO;
 int dir=open(_root.fileSystemRepresentation,O_RDONLY|O_DIRECTORY|O_NOFOLLOW);if(dir<0)return NO;struct stat st;BOOL ok=!fstatat(dir,_name.fileSystemRepresentation,&st,AT_SYMLINK_NOFOLLOW)&&S_ISREG(st.st_mode)&&!unlinkat(dir,_name.fileSystemRepresentation,0);close(dir);if(ok)ok=!rmdir(_root.fileSystemRepresentation);if(ok){_fileURL=nil;_publicText=_hash=_root=_name=nil;_size=0;_submitted=_nativeCleared=NO;[self invalidateAuthorization];_state=@"OWN_PUBLIC_FILE_REMOVED";}return ok;
}
@end
