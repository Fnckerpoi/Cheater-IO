#import "SchemaMetadata.h"
#include <assert.h>
#include <stdio.h>
static NSDictionary *projection(NSString *text, TIOSchemaJSONStatus *status) {
    NSData *data = [text dataUsingEncoding:NSUTF8StringEncoding];
    TIOSchemaOwnedBytes owned = {(unsigned char *)data.bytes, data.length};
    return TIOSchemaDecodeProjection(&owned, status);
}
static void refuse(NSString *text, TIOSchemaJSONStatus expected) {
    TIOSchemaJSONStatus status = TIOSchemaJSONOK;
    assert(projection(text, &status) == nil && status == expected);
}
int main(void) { @autoreleasepool {
    NSString *sample = @"[{\"name\":\"PublicSchemaFixture\",\"idName\":null,\"embedded\":false,\"version\":0,\"properties\":[{\"name\":\"password\",\"type\":\"String\"},{\"name\":\"child\",\"type\":\"Object\",\"target\":\"Child\"}],\"indexes\":[{\"name\":\"byPassword\",\"properties\":[\"password\"],\"unique\":false,\"hash\":true}]},{\"name\":\"Child\",\"embedded\":true,\"properties\":[{\"name\":null,\"type\":\"Long\"}],\"indexes\":[]}]";
    TIOSchemaJSONStatus status;
    NSDictionary *result = projection(sample, &status);
    assert(result && status == TIOSchemaJSONOK);
    NSArray *schema = result[@"schema"];
    assert([schema isEqual:[NSJSONSerialization JSONObjectWithData:[sample dataUsingEncoding:NSUTF8StringEncoding] options:0 error:NULL]]);
    assert([result[@"actual_manuscript_schema_known"] isEqual:@NO]);
    assert(![schema isKindOfClass:NSMutableArray.class] && ![schema[0] isKindOfClass:NSMutableDictionary.class]);
    assert([schema[0][@"properties"][0][@"name"] isEqual:@"password"]);
    NSData *data = [sample dataUsingEncoding:NSUTF8StringEncoding]; TIOSchemaOwnedBytes owned={(unsigned char *)data.bytes,data.length};
    NSDictionary *counts = TIOSchemaDecodeMetadata(&owned, &status);
    assert(counts && !counts[@"schema"] && ![[counts description] containsString:@"PublicSchemaFixture"] && ![[counts description] containsString:@"password"]);
    assert([projection(@"[]", &status)[@"schema"] isEqual:@[]]);
    refuse(@"[{\"name\":\"Bad\\u0001Name\",\"properties\":[]}]", TIOSchemaJSONUnsupportedPolicy);
    refuse(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"body\",\"type\":\"String\",\"value\":\"RECORD_SECRET\"}]}]", TIOSchemaJSONUnsupportedPolicy);
    refuse(@"[{\"name\":\"Public\",\"password\":\"SECRET\",\"properties\":[]}]", TIOSchemaJSONUnsupportedPolicy);
    refuse(@"[{\"name\":\"Public\",\"records\":[],\"properties\":[]}]", TIOSchemaJSONUnsupportedPolicy);
    refuse(@"[{\"name\":\"Public\",\"properties\":[{\"name\":\"e\",\"type\":\"String\",\"enumMap\":{\"x\":\"SECRET\"}}]}]", TIOSchemaJSONUnsupportedPolicy);
    refuse(@"[{\"name\":\"A\",\"na\\u006de\":\"B\",\"properties\":[]}]", TIOSchemaJSONDuplicateKey);
    refuse(@"[] trailing", TIOSchemaJSONInvalidSyntax);
    const unsigned char bad[]={0xc0,0xaf}; TIOSchemaOwnedBytes invalid={(unsigned char *)bad,2};
    assert(TIOSchemaDecodeProjection(&invalid, &status)==nil && status==TIOSchemaJSONInvalidUTF8);
    NSMutableData *oversize=[NSMutableData dataWithLength:65537]; TIOSchemaOwnedBytes huge={(unsigned char *)oversize.bytes,oversize.length};
    assert(TIOSchemaDecodeProjection(&huge, &status)==nil && status==TIOSchemaJSONLimitExceeded);
    NSString *nullable=@"[{\"name\":\"Nullable\",\"properties\":[{\"type\":\"Long\"},{\"name\":\"title\",\"type\":\"String\",\"target\":null}]}]";
    assert(projection(nullable,&status));
    puts("SCHEMA_PROJECTION_OBJC_PASS");
} return 0; }
