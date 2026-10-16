#import <Foundation/Foundation.h>
#import <mach-o/dyld.h>
#import <mach-o/loader.h>
#import "LabGate.h"

static BOOL OriginalHostUUID(void) {
    const struct mach_header_64 *header=(const void *)_dyld_get_image_header(0);
    if (!header || header->magic!=MH_MAGIC_64 || header->filetype!=MH_EXECUTE || header->ncmds>1024 || header->sizeofcmds>1024*1024) return NO;
    const uint8_t *p=(const void *)(header+1),*end=p+header->sizeofcmds;
    for (uint32_t i=0;i<header->ncmds;i++) {
        if ((size_t)(end-p)<sizeof(struct load_command)) return NO;
        const struct load_command *c=(const void *)p;
        if (c->cmdsize<8 || c->cmdsize%8 || c->cmdsize>(size_t)(end-p)) return NO;
        if (c->cmd==LC_UUID) {
            if (c->cmdsize!=sizeof(struct uuid_command)) return NO;
            NSUUID *uuid=[[NSUUID alloc]initWithUUIDBytes:((const struct uuid_command *)c)->uuid];
            return [uuid.UUIDString isEqual:@"748FD301-DA60-3095-A244-9BD0558FACA5"];
        }
        p+=c->cmdsize;
    }
    return NO;
}

BOOL TIOManuscriptLabRuntimeAllowed(void) {
    NSBundle *bundle=NSBundle.mainBundle;
    NSDictionary *info=bundle.infoDictionary;
    NSString *run=info[@"TIOManuscriptLabRunID"];
    for (id value in @[bundle.bundleIdentifier?:@"",run?:@"",info[@"CFBundleShortVersionString"]?:@"",info[@"CFBundleVersion"]?:@"",info[@"TIOManuscriptLabStage"]?:@""]) {
        if (![value isKindOfClass:NSString.class]) return NO;
    }
    if (!TIOManuscriptLabIdentityAllowed(bundle.bundleIdentifier.UTF8String,run.UTF8String,[info[@"CFBundleShortVersionString"] UTF8String],[info[@"CFBundleVersion"] UTF8String],[info[@"TIOManuscriptLabStage"] UTF8String]) || !OriginalHostUUID()) return NO;
    NSURL *url=[bundle URLForResource:@"ManuscriptLabStageA" withExtension:@"json"];
    if (!url) return NO;
    NSData *data=[NSData dataWithContentsOfURL:url];
    if (!data) return NO;
    NSDictionary *marker=[NSJSONSerialization JSONObjectWithData:data options:0 error:NULL];
    if (![marker isKindOfClass:NSDictionary.class] || ![marker[@"run"] isEqual:run] || ![marker[@"expected_bundle"] isEqual:bundle.bundleIdentifier] || ![marker[@"stage"] isEqual:@"A_DISABLED"]) return NO;
    if (![marker[@"writes_enabled"] isEqual:@NO] || ![marker[@"collection_enabled"] isEqual:@NO]) return NO;
    NSString *root=[NSHomeDirectory() stringByAppendingPathComponent:@"Library/Application Support/ManuscriptLab"];
    return TIOManuscriptLabPathAllowed(NSHomeDirectory().UTF8String,root.UTF8String);
}

__attribute__((constructor)) static void LabStageADisabled(void) {
    // No activation, hooks, file creation, collection, database, or networking.
}
