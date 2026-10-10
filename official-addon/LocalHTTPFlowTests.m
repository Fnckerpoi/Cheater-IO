#import "Core.h"
#import "WebSearch.h"
#include <assert.h>
#ifndef TIO_LOCAL_HTTP_ENABLED
#define TIO_LOCAL_HTTP_ENABLED 0
#endif
static NSUInteger Requests;
static NSString *ExpectedEndpoint;
static BOOL SimulateRedirect, RedirectRefused;
@interface LocalHTTPProtocol : NSURLProtocol @end
@implementation LocalHTTPProtocol
+ (BOOL)canInitWithRequest:(NSURLRequest *)request { return YES; }
+ (NSURLRequest *)canonicalRequestForRequest:(NSURLRequest *)request { return request; }
- (void)stopLoading {}
- (void)startLoading {
    Requests++;
    assert([self.request.URL.absoluteString isEqual:ExpectedEndpoint]);
    assert([self.request.HTTPMethod isEqual:@"POST"]);
    assert([[self.request valueForHTTPHeaderField:@"Authorization"] isEqual:@"Bearer synthetic-local-key"]);
    NSDictionary *body=self.request.HTTPBody?[NSJSONSerialization JSONObjectWithData:self.request.HTTPBody options:0 error:nil]:nil;
    // NSURLSession may expose the body as a stream. When available, verify it.
    if(body){assert([body[@"model"] isEqual:@"synthetic-model"]);assert([body[@"stream"] boolValue]);}
    NSHTTPURLResponse *response=[[NSHTTPURLResponse alloc] initWithURL:self.request.URL statusCode:200 HTTPVersion:@"HTTP/1.1" headerFields:@{@"Content-Type":@"text/event-stream"}];
    [self.client URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
    NSString *event=@"data: {\"choices\":[{\"delta\":{\"content\":\"合成测试通过\"},\"finish_reason\":\"stop\"}]}\n\n";
    [self.client URLProtocol:self didLoadData:[event dataUsingEncoding:NSUTF8StringEncoding]];
    [self.client URLProtocolDidFinishLoading:self];
}
@end
@interface LocalHTTPChat : TIOWebChatRequest @end
@implementation LocalHTTPChat
- (NSURLSessionConfiguration *)configuration {
    NSURLSessionConfiguration *config=NSURLSessionConfiguration.ephemeralSessionConfiguration;
    config.protocolClasses=@[LocalHTTPProtocol.class];return config;
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveResponse:(NSURLResponse *)response completionHandler:(void (^)(NSURLSessionResponseDisposition))handler {
    if(!SimulateRedirect){[super URLSession:session dataTask:task didReceiveResponse:response completionHandler:handler];return;}
    NSHTTPURLResponse *redirect=[[NSHTTPURLResponse alloc] initWithURL:response.URL statusCode:302 HTTPVersion:@"HTTP/1.1" headerFields:@{@"Location":@"http://evil.example/v1/chat/completions"}];
    NSURLRequest *next=[NSURLRequest requestWithURL:[NSURL URLWithString:@"http://evil.example/v1/chat/completions"]];
    [super URLSession:session task:task willPerformHTTPRedirection:redirect newRequest:next completionHandler:^(NSURLRequest *forwarded){assert(!forwarded);RedirectRefused=YES;}];
    handler(NSURLSessionResponseCancel);
}
@end
static void Run(NSString *endpoint, NSString *key, BOOL allowed) {
    Requests=0;ExpectedEndpoint=endpoint;
    __block BOOL done=NO;__block NSUInteger finals=0;__block NSString *error=nil,*answer=nil;
    LocalHTTPChat *request=[LocalHTTPChat new];
    request.update=^(NSString *text,BOOL final,NSString *failure){if(final){done=YES;finals++;error=failure;answer=text;}};
    [request startEndpoint:[NSURL URLWithString:endpoint] key:key payload:TIOChatRequest(@"synthetic-model",@"合成问题") searchKey:@""];
    NSDate *deadline=[NSDate dateWithTimeIntervalSinceNow:3];
    while(!done&&[deadline timeIntervalSinceNow]>0)[NSRunLoop.mainRunLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:.01]];
    assert(done&&finals==1);
    if(allowed&&SimulateRedirect){assert(Requests==1&&RedirectRefused&&error.length);assert([error containsString:@"重定向"]);}
    else if(allowed){assert(Requests==1&&!error&&[answer isEqual:@"合成测试通过"]);}
    else {assert(Requests==0&&error.length);}
}
int main(void) { @autoreleasepool {
    Run(@"http://example.com/v1/chat/completions",@"synthetic-local-key",NO);
    Run(@"http://203.0.113.20:0/v1/chat/completions",@"synthetic-local-key",NO);
    Run(@"http://192.168.31.101:8080/v1/chat/completions",@"",NO);
    for(NSString *endpoint in @[@"http://192.168.31.101:8080/v1/chat/completions",@"http://192.168.31.102:9000/v1/chat/completions",@"http://203.0.113.20:8080/v1/chat/completions",@"http://[2001:db8::20]:1234/v1/chat/completions"])
        Run(endpoint,@"synthetic-local-key",TIO_LOCAL_HTTP_ENABLED);
    Run(@"https://example.com/v1/chat/completions",@"synthetic-local-key",YES);
    // Active-session redirect contract: exactly one final error, no forwarding.
    SimulateRedirect=YES;RedirectRefused=NO;
    Run(@"https://example.com/v1/chat/completions",@"synthetic-local-key",YES);
#if TIO_LOCAL_HTTP_ENABLED
    RedirectRefused=NO;
    Run(@"http://203.0.113.20:8080/v1/chat/completions",@"synthetic-local-key",YES);
    RedirectRefused=NO;
    Run(@"http://[2001:db8::20]:1234/v1/chat/completions",@"synthetic-local-key",YES);
#endif
    NSLog(@"PASS: model transport opt-in=%d; public/private IPv4/IPv6, required synthetic Bearer key, HTTPS, no redirect forwarding; mock only.",TIO_LOCAL_HTTP_ENABLED);
} return 0; }
