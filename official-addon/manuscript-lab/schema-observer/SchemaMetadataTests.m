#import "SchemaMetadata.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static NSDictionary *decode(NSData *data, TIOSchemaJSONStatus *status) {
    TIOSchemaOwnedBytes bytes = {(unsigned char *)data.bytes, data.length};
    return TIOSchemaDecodeMetadata(&bytes, status);
}
static void reject(NSString *json) {
    TIOSchemaJSONStatus status = TIOSchemaJSONOK;
    assert(decode([json dataUsingEncoding:NSUTF8StringEncoding], &status) == nil);
    assert(status != TIOSchemaJSONOK);
}
int main(void) { @autoreleasepool {
    NSString *valid = @"[{\"name\":\"PublicSchemaFixture\",\"idName\":null,\"embedded\":false,\"properties\":[{\"name\":\"title\",\"type\":\"String\"}],\"indexes\":[]}]";
    TIOSchemaJSONStatus status;
    NSDictionary *metadata = decode([valid dataUsingEncoding:NSUTF8StringEncoding], &status);
    assert(status == TIOSchemaJSONOK && [metadata[@"collection_count"] unsignedIntegerValue] == 1);
    assert([metadata[@"property_count"] unsignedIntegerValue] == 1);
    assert([metadata[@"actual_manuscript_schema_known"] isEqual:@NO]);
    assert(![[metadata description] containsString:@"title"] && ![[metadata description] containsString:@"PublicSchemaFixture"]);
    const unsigned char invalid[] = {0xc0,0xaf};
    assert(decode([NSData dataWithBytes:invalid length:2], &status) == nil && status == TIOSchemaJSONInvalidUTF8);
    TIOSchemaJSONStatus typed;
    decode([@"[{\"name\":\"P\",\"properties\":[{\"name\":\"a\",\"type\":\"String\",\"enumMap\":{}}]}]" dataUsingEncoding:NSUTF8StringEncoding], &typed); assert(typed == TIOSchemaJSONUnsupportedPolicy);
    decode([@"[{\"name\":\"A\",\"name\":\"B\",\"properties\":[]}]" dataUsingEncoding:NSUTF8StringEncoding], &typed); assert(typed == TIOSchemaJSONDuplicateKey);
    reject(@"[] garbage"); reject(@"[{}"); reject(@"{}");
    reject(@"[{\"name\":\"First\",\"name\":\"Second\",\"properties\":[]}]");
    reject(@"[{\"name\":\"First\",\"na\\u006de\":\"Second\",\"properties\":[]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[],\"records\":[{\"secret\":\"x\"}]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"a\",\"type\":\"Unknown\"}]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"a\",\"type\":\"String\",\"enumMap\":{\"private\":\"x\"}}]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"a\",\"type\":\"String\"},{\"name\":\"a\",\"type\":\"Long\"}]}]");
    reject(@"[{\"name\":\"Public\",\"embedded\":1,\"properties\":[]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[],\"version\":256}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"obj\",\"type\":\"Object\",\"target\":\"Absent\"}]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"x\",\"type\":\"String\",\"target\":\"Public\"}]}]");
    reject(@"[{\"name\":\"Public\",\"properties\":[],\"indexes\":[{\"name\":\"ix\",\"properties\":[\"missing\"],\"unique\":false,\"hash\":true}]}]");
    reject(@"[[[[[[[[[[]]]]]]]]]]");
    NSMutableData *large = [NSMutableData dataWithLength:65537]; assert(decode(large, &status) == nil && status == TIOSchemaJSONLimitExceeded);
    NSMutableArray *many = [NSMutableArray array];
    for (unsigned i=0;i<65;i++) [many addObject:@{@"name":[NSString stringWithFormat:@"C%u",i], @"properties":@[]}];
    assert(decode([NSJSONSerialization dataWithJSONObject:many options:0 error:nil], &status) == nil && status == TIOSchemaJSONLimitExceeded);
    NSDictionary *empty = decode([@"[]" dataUsingEncoding:NSUTF8StringEncoding], &status);
    assert(empty && [empty[@"collection_count"] unsignedIntegerValue] == 0);
    puts("SCHEMA_METADATA_OBJC_PASS");
} return 0; }
