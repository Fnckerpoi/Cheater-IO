#import <Foundation/Foundation.h>
#import <Security/Security.h>
#import "TIOStorageIdentity.h"
#include <assert.h>
int main(void) {@autoreleasepool {
#if TIO_TEST_LAB_SECOND
    NSString *addon=@"com.duriea.turboio.manuscriptlab106.public-run-b.private-addon";
    NSString *profile=@"com.duriea.turboio.manuscriptlab106.public-run-b.profile";
    NSString *knowledge=@"com.duriea.turboio.manuscriptlab106.public-run-b.knowledge";
#elif defined(TIO_STORAGE_LAB_BUNDLE_ID)
    NSString *addon=@"com.duriea.turboio.manuscriptlab106.public-run-a.private-addon";
    NSString *profile=@"com.duriea.turboio.manuscriptlab106.public-run-a.profile";
    NSString *knowledge=@"com.duriea.turboio.manuscriptlab106.public-run-a.knowledge";
#else
    NSString *addon=@"io.turboio.official-private-addon";
    NSString *profile=@"io.turboio.official-profile";
    NSString *knowledge=@"io.turboio.official-knowledge";
#endif
#if TIO_TEST_LAB_SECOND
    NSString *preview=@"com.duriea.turboio.manuscriptlab106.public-run-b.preview";
    NSString *navigation=@"com.duriea.turboio.manuscriptlab106.public-run-b.navigation";
#elif defined(TIO_STORAGE_LAB_BUNDLE_ID)
    NSString *preview=@"com.duriea.turboio.manuscriptlab106.public-run-a.preview";
    NSString *navigation=@"com.duriea.turboio.manuscriptlab106.public-run-a.navigation";
#else
    NSString *preview=@"io.turboio.research.preview";
    NSString *navigation=@"io.turboio.navigation";
#endif
    assert([TIOStoragePreviewDomain() isEqual:preview]);
    assert([TIOStorageNavigationDomain() isEqual:navigation]);
    NSSet *unique=[NSSet setWithArray:@[addon,profile,knowledge,preview,navigation]];
    assert(unique.count==5);
    NSDictionary *navigationQuery=@{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword,(__bridge id)kSecAttrService:TIOStorageNavigationDomain(),(__bridge id)kSecAttrAccount:@"amap-ios"};
    assert([navigationQuery[(__bridge id)kSecAttrService] isEqual:navigation]);
    assert([TIOStorageAddonDomain() isEqual:addon]);
    assert([TIOStorageProfileDomain() isEqual:profile]);
    assert([TIOStorageKnowledgeDomain() isEqual:knowledge]);
    NSDictionary *addonQuery=@{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword,(__bridge id)kSecAttrService:TIOStorageAddonDomain(),(__bridge id)kSecAttrAccount:@"public-host"};
    NSDictionary *knowledgeQuery=@{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword,(__bridge id)kSecAttrService:TIOStorageKnowledgeDomain(),(__bridge id)kSecAttrAccount:@"bridge-token"};
    assert([addonQuery[(__bridge id)kSecAttrService] isEqual:addon]);
    assert([knowledgeQuery[(__bridge id)kSecAttrService] isEqual:knowledge]);
    assert([addonQuery[(__bridge id)kSecAttrAccount] isEqual:@"public-host"]);
    assert([knowledgeQuery[(__bridge id)kSecAttrAccount] isEqual:@"bridge-token"]);
    assert(![addon isEqual:profile] && ![profile isEqual:knowledge] && ![addon isEqual:knowledge]);
    NSDictionary *result=@{@"addon":TIOStorageAddonDomain(),@"profile":TIOStorageProfileDomain(),@"knowledge":TIOStorageKnowledgeDomain(),@"preview":TIOStoragePreviewDomain(),@"navigation":TIOStorageNavigationDomain()};
    NSData *data=[NSJSONSerialization dataWithJSONObject:result options:NSJSONWritingSortedKeys error:NULL];
    puts([[NSString alloc]initWithData:data encoding:NSUTF8StringEncoding].UTF8String);
    puts("STORAGE_IDENTITY_QUERY_DICTIONARY_PASS_NO_SERVICE_ACCESS");
}return 0;}
