#pragma once
#import <Foundation/Foundation.h>
#include <arpa/inet.h>

#ifndef TIO_LOCAL_HTTP_ENABLED
#define TIO_LOCAL_HTTP_ENABLED 0
#endif

static inline BOOL TIOEndpointIsIPAddress(NSString *host) {
    if ([host hasPrefix:@"["] && [host hasSuffix:@"]"]) host=[host substringWithRange:NSMakeRange(1,host.length-2)];
    struct in_addr v4;struct in6_addr v6;
    if (inet_pton(AF_INET,host.UTF8String,&v4)==1) {
        char canonical[INET_ADDRSTRLEN];
        return inet_ntop(AF_INET,&v4,canonical,sizeof(canonical))&&[host isEqual:[NSString stringWithUTF8String:canonical]];
    }
    return inet_pton(AF_INET6,host.UTF8String,&v6)==1;
}

static inline NSURL *TIOEndpointPolicyValidate(NSString *input) {
    if (![input isKindOfClass:NSString.class]) return nil;
    NSString *raw=[input stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    NSURLComponents *c=[NSURLComponents componentsWithString:raw];
    if (!c || !c.host.length || c.user || c.password || c.fragment || c.query) return nil;
    if (![c.path hasSuffix:@"/chat/completions"]) return nil;
    if ([c.scheme.lowercaseString isEqual:@"https"]) return c.URL;
#if TIO_LOCAL_HTTP_ENABLED
    if (![c.scheme isEqual:@"http"] || !TIOEndpointIsIPAddress(c.host)) return nil;
    if (c.port && (c.port.integerValue<1 || c.port.integerValue>65535)) return nil;
    if (![c.percentEncodedPath isEqual:c.path]) return nil;
    NSCharacterSet *unsafe=[[NSCharacterSet characterSetWithCharactersInString:@"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._/"] invertedSet];
    if ([c.path rangeOfCharacterFromSet:unsafe].location!=NSNotFound) return nil;
    for (NSString *part in [c.path componentsSeparatedByString:@"/"]) if ([part isEqual:@"."] || [part isEqual:@".."]) return nil;
    if ([c.path containsString:@"//"]) return nil;
    NSString *host=c.host;
    if ([host containsString:@":"] && ![host hasPrefix:@"["]) host=[NSString stringWithFormat:@"[%@]",host];
    NSString *authority=c.port?[host stringByAppendingFormat:@":%@",c.port]:host;
    NSString *canonical=[NSString stringWithFormat:@"http://%@%@",authority,c.path];
    // Reject alternate IP/port spellings and any URL normalization before credentials attach.
    if ([raw isEqual:canonical]) return c.URL;
#endif
    return nil;
}

static inline BOOL TIOEndpointPolicyIsHTTP(NSURL *url) {
#if TIO_LOCAL_HTTP_ENABLED
    return [url.scheme isEqual:@"http"] && TIOEndpointPolicyValidate(url.absoluteString)!=nil;
#else
    (void)url; return NO;
#endif
}

static inline NSString *TIOHTTPPolicyBuildMarker(void) {
#if TIO_LOCAL_HTTP_ENABLED
    return @"turboio-http-any-ip-v1";
#else
    return @"turboio-https-only";
#endif
}

static inline NSString *TIOEndpointConfigurationMessage(void) {
#if TIO_LOCAL_HTTP_ENABLED
    return @"填写完整 HTTPS /chat/completions 地址，或 HTTP IP 地址（支持公网/局域网 IPv4 与 [IPv6]，端口 1–65535，省略为80）。HTTP 不接受域名。Key 和对话明文传输，公网尤其不安全，优先 HTTPS。Key 仅存手机钥匙串；改地址需重新输入 Key，不自动转移。";
#else
    return @"填写完整 HTTPS /chat/completions 地址。Key 仅存手机钥匙串；留空保留同一地址的旧 Key，改地址不会带过去。";
#endif
}

static inline NSString *TIOEndpointValidationError(void) {
#if TIO_LOCAL_HTTP_ENABLED
    return @"需要有效的 HTTPS 地址或 http://IP[:端口]/…/chat/completions，以及模型名称。IPv6 用方括号；HTTP 不接受域名、编码路径、相对路径、用户名密码、查询参数或片段。";
#else
    return @"需要有效的 HTTPS chat/completions 地址和模型名称。";
#endif
}
