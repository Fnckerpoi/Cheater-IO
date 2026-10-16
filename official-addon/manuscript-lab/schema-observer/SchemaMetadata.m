#import "SchemaMetadata.h"
#import <CoreFoundation/CoreFoundation.h>

static BOOL IsBoolean(id value) { return value && CFGetTypeID((__bridge CFTypeRef)value) == CFBooleanGetTypeID(); }
static BOOL Name(id value) {
    return [value isKindOfClass:NSString.class] && [value length] > 0 &&
        [value length] <= 256 && ![value hasPrefix:@"_"] && ![value hasPrefix:@"sqlite_"];
}
static BOOL OptionalName(id value) { return !value || value == NSNull.null || Name(value); }
static BOOL Keys(NSDictionary *object, NSArray *allowed) {
    if (![object isKindOfClass:NSDictionary.class]) return NO;
    for (id key in object) if (![allowed containsObject:key]) return NO;
    return YES;
}
static TIOSchemaJSONStatus BoundedNesting(NSData *data) {
    const unsigned char *p = data.bytes;
    NSUInteger depth = 0, stringStart = 0; BOOL quoted = NO, escaped = NO;
    NSMutableArray *keysByDepth = [NSMutableArray array];
    for (NSUInteger i = 0; i < data.length; i++) {
        unsigned char c = p[i];
        if (quoted) {
            if (escaped) escaped = NO;
            else if (c == '\\') escaped = YES;
            else if (c == '"') {
                quoted = NO;
                NSUInteger next = i + 1;
                while (next < data.length && (p[next] == ' ' || p[next] == '\t' || p[next] == '\r' || p[next] == '\n')) next++;
                if (next < data.length && p[next] == ':') {
                    id keys = keysByDepth.lastObject;
                    if (![keys isKindOfClass:NSMutableSet.class]) return TIOSchemaJSONInvalidSyntax;
                    NSData *token = [data subdataWithRange:NSMakeRange(stringStart, i-stringStart+1)];
                    id key = [NSJSONSerialization JSONObjectWithData:token options:NSJSONReadingFragmentsAllowed error:NULL];
                    if (![key isKindOfClass:NSString.class]) return TIOSchemaJSONInvalidSyntax;
                    if ([keys containsObject:key]) return TIOSchemaJSONDuplicateKey;
                    [keys addObject:key];
                }
            }
        } else if (c == '"') { quoted = YES; stringStart = i; }
        else if (c == '[' || c == '{') {
            if (++depth > 8) return TIOSchemaJSONLimitExceeded;
            [keysByDepth addObject:c == '{' ? (id)[NSMutableSet set] : (id)NSNull.null];
        } else if (c == ']' || c == '}') {
            if (!depth) return TIOSchemaJSONInvalidSyntax;
            depth--; [keysByDepth removeLastObject];
        }
    }
    return !depth && !quoted ? TIOSchemaJSONOK : TIOSchemaJSONInvalidSyntax;
}

NSDictionary<NSString *, id> *TIOSchemaDecodeMetadata(const TIOSchemaOwnedBytes *owned, TIOSchemaJSONStatus *status) {
    @autoreleasepool {
    if (status) *status = TIOSchemaJSONInvalidBytes;
    if (!owned || !owned->bytes || !owned->length) return nil;
    if (owned->length > TIO_SCHEMA_MAX_BYTES) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
    NSData *data = [NSData dataWithBytes:owned->bytes length:owned->length];
    NSString *text = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (!text || ![[text dataUsingEncoding:NSUTF8StringEncoding] isEqual:data]) {
        if (status) *status = TIOSchemaJSONInvalidUTF8;
        return nil;
    }
    TIOSchemaJSONStatus lexical = BoundedNesting(data);
    if (lexical != TIOSchemaJSONOK) { if (status) *status = lexical; return nil; }
    id parsed = [NSJSONSerialization JSONObjectWithData:data options:0 error:NULL];
    if (!parsed) { if (status) *status = TIOSchemaJSONInvalidSyntax; return nil; }
    if (status) *status = TIOSchemaJSONUnsupportedPolicy;
    if (![parsed isKindOfClass:NSArray.class]) return nil;
    if ([parsed count] > 64) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
    NSSet *types = [NSSet setWithArray:@[@"Bool",@"Byte",@"Int",@"Float",@"Long",@"Double",@"DateTime",@"String",@"Object",@"Json",@"BoolList",@"ByteList",@"IntList",@"FloatList",@"LongList",@"DoubleList",@"DateTimeList",@"StringList",@"ObjectList"]];
    NSMutableDictionary *collections = [NSMutableDictionary dictionary];
    NSUInteger propertyCount = 0, indexCount = 0, embeddedCount = 0;
    NSMutableDictionary *typeCounts = [NSMutableDictionary dictionary];
    for (id item in parsed) {
        if (!Keys(item, @[@"name",@"idName",@"embedded",@"properties",@"indexes",@"version"])) return nil;
        NSString *name = item[@"name"];
        if (([name isKindOfClass:NSString.class] && name.length > 256) || ([item[@"idName"] isKindOfClass:NSString.class] && [item[@"idName"] length] > 256)) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
        if (!Name(name) || collections[name] || !OptionalName(item[@"idName"])) return nil;
        if (item[@"embedded"] && !IsBoolean(item[@"embedded"])) return nil;
        id version = item[@"version"];
        if (version && (![version isKindOfClass:NSNumber.class] || IsBoolean(version) ||
            [version doubleValue] != [version unsignedIntegerValue] || [version unsignedIntegerValue] > 255)) return nil;
        id properties = item[@"properties"], indexes = item[@"indexes"] ?: @[];
        if (![properties isKindOfClass:NSArray.class] || ![indexes isKindOfClass:NSArray.class]) return nil;
        if ([properties count] > 256 || [indexes count] > 128) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
        if ([item[@"embedded"] boolValue] && [indexes count]) return nil;
        collections[name] = item;
    }
    for (NSDictionary *item in parsed) {
        NSArray *properties = item[@"properties"], *indexes = item[@"indexes"] ?: @[];
        NSMutableSet *propertyNames = [NSMutableSet set];
        NSMutableDictionary *propertyTypes = [NSMutableDictionary dictionary];
        for (id property in properties) {
            if (!Keys(property, @[@"name",@"type",@"target"])) return nil;
            id name = property[@"name"] ?: NSNull.null;
            if (([name isKindOfClass:NSString.class] && [name length] > 256) || ([property[@"target"] isKindOfClass:NSString.class] && [property[@"target"] length] > 256)) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
            if (!OptionalName(name) || [propertyNames containsObject:name]) return nil;
            [propertyNames addObject:name];
            NSString *type = property[@"type"];
            if (![type isKindOfClass:NSString.class] || ![types containsObject:type]) return nil;
            id target = property[@"target"];
            if (!OptionalName(target)) return nil;
            BOOL object = [type isEqual:@"Object"] || [type isEqual:@"ObjectList"];
            if (object) {
                if (![target isKindOfClass:NSString.class] || ![collections[target][@"embedded"] boolValue]) return nil;
            } else if (target && target != NSNull.null) return nil;
            if (name != NSNull.null) propertyTypes[name] = type;
            typeCounts[type] = @([typeCounts[type] unsignedIntegerValue] + 1);
        }
        NSMutableSet *indexNames = [NSMutableSet set];
        for (id index in indexes) {
            if ([index isKindOfClass:NSDictionary.class] && [index[@"name"] isKindOfClass:NSString.class] && [index[@"name"] length] > 256) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
            if (!Keys(index, @[@"name",@"properties",@"unique",@"hash"]) || !Name(index[@"name"]) ||
                [indexNames containsObject:index[@"name"]] || !IsBoolean(index[@"unique"]) || !IsBoolean(index[@"hash"])) return nil;
            [indexNames addObject:index[@"name"]];
            NSArray *names = index[@"properties"];
            if (![names isKindOfClass:NSArray.class] || !names.count) return nil;
            if (names.count > 256) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
            for (NSUInteger i=0; i<names.count; i++) {
                id name = names[i];
                if ([name isKindOfClass:NSString.class] && [name length] > 256) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
                if (!Name(name)) return nil;
                NSString *type = propertyTypes[name];
                if (!type || [@[@"Float",@"Double",@"Object",@"Json"] containsObject:type] || [type hasSuffix:@"List"]) return nil;
                if ([type isEqual:@"String"] && ![index[@"hash"] boolValue] && i+1 != names.count) return nil;
            }
        }
        propertyCount += properties.count; indexCount += indexes.count;
        if ([item[@"embedded"] boolValue]) embeddedCount++;
        if (propertyCount > 1024 || indexCount > 512) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
    }
    if (status) *status = TIOSchemaJSONOK;
    return @{@"collection_count":@([parsed count]), @"property_count":@(propertyCount),
        @"index_count":@(indexCount), @"embedded_count":@(embeddedCount),
        @"type_counts":[typeCounts copy], @"observer_policy_supported":@YES,
        @"actual_manuscript_schema_known":@NO};
    }
}

static BOOL ProjectionName(id value) {
    if (!value || value == NSNull.null) return YES;
    if (![value isKindOfClass:NSString.class]) return NO;
    for (NSUInteger i = 0; i < [value length]; i++) {
        if ([value characterAtIndex:i] < 0x20) return NO;
    }
    return YES;
}

static NSDictionary *ProjectionKeys(NSDictionary *source, NSArray<NSString *> *keys) {
    NSMutableDictionary *projected = [NSMutableDictionary dictionary];
    for (NSString *key in keys) {
        if (source[key]) projected[key] = source[key];
    }
    return [projected copy];
}

NSDictionary<NSString *, id> *TIOSchemaDecodeProjection(const TIOSchemaOwnedBytes *owned, TIOSchemaJSONStatus *status) {
    @autoreleasepool {
        if (status) *status = TIOSchemaJSONInvalidBytes;
        if (!owned || !owned->bytes || !owned->length) return nil;
        if (owned->length > TIO_SCHEMA_MAX_BYTES) { if (status) *status = TIOSchemaJSONLimitExceeded; return nil; }
        NSData *snapshot = [NSData dataWithBytes:owned->bytes length:owned->length];
        TIOSchemaOwnedBytes copied = {(unsigned char *)snapshot.bytes, snapshot.length};
        if (!TIOSchemaDecodeMetadata(&copied, status)) return nil;
        NSArray *validated = [NSJSONSerialization JSONObjectWithData:snapshot options:0 error:NULL];
        NSMutableArray *collections = [NSMutableArray array];
        if (status) *status = TIOSchemaJSONUnsupportedPolicy;
        for (NSDictionary *collection in validated) {
            if (!ProjectionName(collection[@"name"]) || !ProjectionName(collection[@"idName"])) return nil;
            NSMutableDictionary *canonical = [ProjectionKeys(collection, @[@"name",@"idName",@"embedded",@"version"]) mutableCopy];
            NSMutableArray *properties = [NSMutableArray array];
            for (NSDictionary *property in collection[@"properties"]) {
                if (!ProjectionName(property[@"name"]) || !ProjectionName(property[@"target"])) return nil;
                [properties addObject:ProjectionKeys(property, @[@"name",@"type",@"target"])];
            }
            canonical[@"properties"] = [properties copy];
            if (collection[@"indexes"]) {
                NSMutableArray *indexes = [NSMutableArray array];
                for (NSDictionary *index in collection[@"indexes"]) {
                    if (!ProjectionName(index[@"name"])) return nil;
                    for (NSString *name in index[@"properties"]) if (!ProjectionName(name)) return nil;
                    [indexes addObject:ProjectionKeys(index, @[@"name",@"properties",@"unique",@"hash"])];
                }
                canonical[@"indexes"] = [indexes copy];
            }
            [collections addObject:[canonical copy]];
        }
        NSDictionary *envelope = @{@"schema":[collections copy], @"observer_policy_supported":@YES,
            @"actual_manuscript_schema_known":@NO};
        NSData *serialized = [NSJSONSerialization dataWithJSONObject:envelope options:0 error:NULL];
        if (!serialized || serialized.length > TIO_SCHEMA_MAX_BYTES) {
            if (status) *status = TIOSchemaJSONLimitExceeded;
            return nil;
        }
        NSDictionary *immutable = [NSJSONSerialization JSONObjectWithData:serialized options:0 error:NULL];
        if (status) *status = TIOSchemaJSONOK;
        return immutable;
    }
}
