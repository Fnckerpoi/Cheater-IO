#import "Core.h"
#import "EndpointPolicy.h"
#include <assert.h>
int main(void) { @autoreleasepool {
    NSArray *validHTTP=@[
        @"http://192.168.31.101:8080/v1/chat/completions",
        @"http://192.168.1.100:1234/chat/completions",
        @"http://10.0.0.20:9000/api/v1/chat/completions",
        @"http://172.16.0.20/v1/chat/completions",
        @"http://203.0.113.20:8080/v1/chat/completions",
        @"http://198.51.100.40/v1/chat/completions",
        @"http://203.0.113.20:1/v1/chat/completions",
        @"http://203.0.113.20:65535/v1/chat/completions",
        @"http://[2001:db8::20]:8080/v1/chat/completions",
        @"http://[2001:db8::20]/v1/chat/completions",
        @"http://127.0.0.1:8080/v1/chat/completions",
        @"http://[::1]:8080/v1/chat/completions"
    ];
    for(NSString *url in validHTTP) {
#if TIO_LOCAL_HTTP_ENABLED
        NSURL *valid=TIOValidateEndpoint(url);
        assert([valid.absoluteString isEqual:url]);assert(TIOEndpointPolicyIsHTTP(valid));
        assert([TIOValidateEndpoint([@" \n" stringByAppendingString:url]).absoluteString isEqual:url]);
#else
        assert(!TIOValidateEndpoint(url));assert(!TIOEndpointPolicyIsHTTP([NSURL URLWithString:url]));
#endif
    }
    assert(TIOValidateEndpoint(@"https://example.com/v1/chat/completions"));
    assert(!TIOEndpointPolicyIsHTTP(TIOValidateEndpoint(@"https://example.com/v1/chat/completions")));
    for (NSString *bad in @[
        @"http://192.168.31.101:8080/v1/chat/completions/",
        @"http://192.168.31.101:8080/v1/../v1/chat/completions",
        @"http://192.168.31.101:8080/v1/./chat/completions",
        @"http://192.168.31.101:8080/v1//chat/completions",
        @"http://192.168.31.101:8080/v1/chat/%63ompletions",
        @"http://192.168.31.101:8080/v1/chat/completions?key=synthetic",
        @"http://192.168.31.101:8080/v1/chat/completions#fragment",
        @"http://user:pass@192.168.31.101:8080/v1/chat/completions",
        @"http://192.168.31.101:8080@evil.example/v1/chat/completions",
        @"http://192.168.31.101.evil.example:8080/v1/chat/completions",
        @"http://192.168.031.101:8080/v1/chat/completions",
        @"http://2130706433:8080/v1/chat/completions",
        @"http://0x7f000001:8080/v1/chat/completions",
        @"http://127.1:8080/v1/chat/completions",
        @"http://256.1.1.1:8080/v1/chat/completions",
        @"http://203.0.113.20:0/v1/chat/completions",
        @"http://203.0.113.20:65536/v1/chat/completions",
        @"http://203.0.113.20:-1/v1/chat/completions",
        @"http://203.0.113.20:080/v1/chat/completions",
        @"http://203.0.113.20:/v1/chat/completions",
        @"http://203.0.113.20%2eevil.example/v1/chat/completions",
        @"http://[fe80::1%25en0]:8080/v1/chat/completions",
        @"http://localhost:8080/v1/chat/completions",
        @"http://example.com/v1/chat/completions",
        @"https://user:pass@example.com/v1/chat/completions",
        @"https://example.com/v1/chat/completions?key=synthetic",
        @"https://example.com/v1/chat/completions#fragment",
        @"file:///v1/chat/completions", @""
    ]) assert(!TIOValidateEndpoint(bad));
    NSLog(@"PASS: HTTP IP opt-in=%d; IPv4/IPv6, configurable port, canonical URL guards, HTTPS preserved.",TIO_LOCAL_HTTP_ENABLED);
} return 0; }
