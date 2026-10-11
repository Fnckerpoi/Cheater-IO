#import "MapKitRoads.h"
#import "MapKitRoadsCore.h"

@interface TIOMapKitRoads () <NSURLSessionDataDelegate>
@end
@implementation TIOMapKitRoads {
    TIOMapKitRoadPolicy *_policy;
    NSURLSession *_session;
    NSURLSessionDataTask *_task;
    NSMutableData *_buffer;
    NSUInteger _requestGeneration;
    NSString *_requestMode;
    BOOL _responseOK;
}
- (instancetype)init {
    if ((self = [super init])) _policy = [TIOMapKitRoadPolicy new];
    return self;
}
- (NSArray *)segments { NSAssert(NSThread.isMainThread, @"Main thread only"); return _policy.segments; }
- (void)updateCoordinate:(CLLocationCoordinate2D)coordinate mode:(NSString *)mode {
    NSAssert(NSThread.isMainThread, @"Main thread only");
    NSArray *old = _policy.segments;
    NSUInteger generation = [_policy beginLatitude:coordinate.latitude longitude:coordinate.longitude mode:mode time:NSProcessInfo.processInfo.systemUptime];
    if (_task && _requestGeneration != _policy.generation) {
        [_task cancel]; [_session invalidateAndCancel]; _task = nil; _session = nil; _buffer = nil;
    }
    if (![old isEqual:_policy.segments] && self.changed) self.changed();
    if (!generation || generation != _policy.generation) return;
    NSString *query = TIOMapKitRoadQuery(coordinate.latitude, coordinate.longitude, mode);
    if (!query) { [_policy completeGeneration:generation segments:nil time:NSProcessInfo.processInfo.systemUptime]; return; }
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:@"https://overpass-api.de/api/interpreter"]];
    request.HTTPMethod = @"POST";
    [request setValue:@"application/x-www-form-urlencoded; charset=utf-8" forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"Turbo-IO/MapKit-navigation" forHTTPHeaderField:@"User-Agent"];
    NSCharacterSet *safe = [NSCharacterSet characterSetWithCharactersInString:@"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"];
    request.HTTPBody = [[@"data=" stringByAppendingString:[query stringByAddingPercentEncodingWithAllowedCharacters:safe]] dataUsingEncoding:NSUTF8StringEncoding];
    request.timeoutInterval = 20;
    NSURLSessionConfiguration *config = NSURLSessionConfiguration.ephemeralSessionConfiguration;
    config.URLCache = nil; config.HTTPCookieStorage = nil; config.URLCredentialStorage = nil; config.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData;
    config.timeoutIntervalForResource = 25;
    _requestGeneration = generation; _requestMode = [mode copy]; _responseOK = NO; _buffer = [NSMutableData data];
    _session = [NSURLSession sessionWithConfiguration:config delegate:self delegateQueue:NSOperationQueue.mainQueue];
    _task = [_session dataTaskWithRequest:request]; [_task resume];
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveResponse:(NSURLResponse *)response completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    if (task != _task || session != _session || _requestGeneration != _policy.generation) { completionHandler(NSURLSessionResponseCancel); return; }
    NSInteger status = [response isKindOfClass:NSHTTPURLResponse.class] ? [(NSHTTPURLResponse *)response statusCode] : 0;
    _responseOK = TIOMapKitAcceptResponse(status, response.expectedContentLength);
    completionHandler(_responseOK ? NSURLSessionResponseAllow : NSURLSessionResponseCancel);
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveData:(NSData *)data {
    if (task != _task || session != _session || _requestGeneration != _policy.generation) return;
    if (!_responseOK || !TIOMapKitAppendResponse(_buffer, data)) { _responseOK = NO; [_task cancel]; }
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    if (task != _task || session != _session || _requestGeneration != _policy.generation) return;
    NSArray *segments = !error && _responseOK ? TIOMapKitParseRoads(_buffer, _requestMode) : nil;
    BOOL accepted = [_policy completeGeneration:_requestGeneration segments:segments time:NSProcessInfo.processInfo.systemUptime];
    [_session finishTasksAndInvalidate]; _session = nil; _task = nil; _buffer = nil; _requestMode = nil;
    if (accepted && self.changed) self.changed();
}
- (void)cancel {
    NSAssert(NSThread.isMainThread, @"Main thread only");
    [_policy cancel]; [_task cancel]; [_session invalidateAndCancel];
    _session = nil; _task = nil; _buffer = nil; _requestMode = nil;
    if (self.changed) self.changed();
}
@end
