#import <Foundation/Foundation.h>
#import "EndpointPolicy.h"
#include <assert.h>
int main(void){@autoreleasepool{
    assert(TIOEndpointPolicyValidate(@"https://example.invalid/v1/chat/completions"));
#if TIO_LOCAL_HTTP_ENABLED
    assert(TIOEndpointPolicyValidate(@"http://203.0.113.17:8080/v1/chat/completions"));
    assert(TIOEndpointPolicyValidate(@"http://8.8.8.8/v1/chat/completions"));
    assert(TIOEndpointPolicyValidate(@"http://[2001:db8::17]:8080/v1/chat/completions"));
#else
    assert(!TIOEndpointPolicyValidate(@"http://203.0.113.17:8080/v1/chat/completions"));
#endif
    assert(!TIOEndpointPolicyValidate(@"http://user:password@203.0.113.17/v1/chat/completions"));
    assert(!TIOEndpointPolicyValidate(@"http://example.invalid/v1/chat/completions"));
    assert(!TIOEndpointPolicyValidate(@"http://203.0.113.17:70000/v1/chat/completions"));
    puts("HTTP_ENDPOINT_POLICY_OWN_STRINGS_PASS_NO_NETWORK");
}return 0;}
