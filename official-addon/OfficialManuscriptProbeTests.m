#import "OfficialManuscriptProbe.h"
#include <assert.h>
#include <math.h>

static NSDictionary *Fixture(void) {
    return @{@"title": TIOOfficialProbePublicTitle, @"body": TIOOfficialProbePublicBody};
}
static NSDictionary *Observe(TIOOfficialManuscriptProbe *probe, id args, id identifier) {
    return [probe observeChannel:@"synthetic/test" method:@"syntheticSave"
                      arguments:args stableID:identifier];
}
int main(void) { @autoreleasepool {
    __block NSTimeInterval now = 100;
    TIOOfficialManuscriptProbe *probe = [[TIOOfficialManuscriptProbe alloc] initWithClock:^{ return now; }];
    assert(!probe.active && !Observe(probe, Fixture(), @"synthetic-list-id"));
    assert(![probe armForPublicTestWithUserConfirmation:NO]);
    assert(probe.records.count == 0);
    assert([probe armForPublicTestWithUserConfirmation:YES]);
    assert(![probe armForPublicTestWithUserConfirmation:YES]);
    assert(!Observe(probe, @{@"title": TIOOfficialProbePublicTitle, @"body": @"7392"}, nil));
    assert(!Observe(probe, @{@"title": @"another title", @"body": TIOOfficialProbePublicBody}, nil));
    assert(!Observe(probe, @{@"privateText": @"PRIVATE_SECRET", @"token": @"BEARER_SECRET"}, @"synthetic-list-id"));

    NSMutableDictionary *args = [Fixture() mutableCopy];
    args[@"token"] = @"BEARER_SECRET";
    args[@"privateText"] = @"PRIVATE_SECRET";
    args[@"PRIVATE_SECRET key"] = @"hidden";
    NSDictionary *before = [args copy];
    __block NSUInteger forwards = 0;
    [probe forwardChannel:@"synthetic/test" method:@"syntheticSave" arguments:args
                 stableID:@"synthetic-list-id" originalOperation:^{
        forwards++; assert([args isEqual:before]);
    }];
    assert(forwards == 1 && [args isEqual:before] && probe.records.count == 1);
    NSDictionary *record = probe.records.firstObject;
    assert([record[@"fixture_match"] boolValue] && [record[@"observation_only"] boolValue]);
    assert([record[@"keys"] containsObject:@"title"]);
    assert(![record[@"keys"] containsObject:@"PRIVATE_SECRET key"]);
    NSString *json = [[NSString alloc] initWithData:[NSJSONSerialization dataWithJSONObject:record options:0 error:nil]
                                         encoding:NSUTF8StringEncoding];
    for (NSString *secret in @[@"BEARER_SECRET", @"PRIVATE_SECRET", @"synthetic-list-id",
                              TIOOfficialProbePublicTitle, @"校验码"])
        assert(![json containsString:secret]);
    NSString *digest = record[@"id_session_hmac_sha256"];
    assert(digest.length == 64 && [record[@"fixture_sha256"] length] == 64);
    NSDictionary *meta = Observe(probe, @{@"id": @"synthetic-list-id", @"updated": @YES}, @"synthetic-list-id");
    assert(![meta[@"fixture_match"] boolValue] && [meta[@"correlated_public_id"] boolValue]);
    assert([meta[@"id_session_hmac_sha256"] isEqual:digest]);
    assert(!Observe(probe, @{@"updated": @YES}, @"unrelated-id"));
    assert(!Observe(probe, @{@"id": @"synthetic-list-id", @"body": @"PRIVATE_SECRET"}, @"synthetic-list-id"));

    NSDictionary *crlf = @{@"title": TIOOfficialProbePublicTitle,
        @"body": [TIOOfficialProbePublicBody stringByReplacingOccurrencesOfString:@"\n" withString:@"\r\n"]};
    assert([Observe(probe, crlf, nil)[@"id_session_hmac_sha256"] isEqual:@"none"]);
    assert(![Observe(probe, crlf, nil)[@"correlated_public_id"] boolValue]);
    NSMutableArray *oversized = [NSMutableArray arrayWithObjects:TIOOfficialProbePublicTitle, TIOOfficialProbePublicBody, nil];
    for (NSUInteger i = 0; i < 64; i++) [oversized addObject:@0];
    assert(!Observe(probe, oversized, @"synthetic-list-id"));
    id tooDeep = Fixture();
    for (NSUInteger i = 0; i < 5; i++) tooDeep = @[tooDeep];
    assert(!Observe(probe, tooDeep, @"synthetic-list-id"));
    assert(![probe observeChannel:@"unsafe\nchannel" method:@"save" arguments:Fixture() stableID:nil]);
    assert(![probe observeChannel:@"synthetic/test" method:@"unsafe method" arguments:Fixture() stableID:nil]);

    now = 219.999; assert(probe.active);
    now = 220; assert(!probe.active && !Observe(probe, Fixture(), @"synthetic-list-id"));
    assert(![probe armForPublicTestWithUserConfirmation:YES]);
    [probe forwardChannel:@"synthetic/test" method:@"save" arguments:args stableID:nil originalOperation:^{ forwards++; }];
    assert(forwards == 2);

    TIOOfficialManuscriptProbe *another = [[TIOOfficialManuscriptProbe alloc] initWithClock:^{ return now; }];
    assert([another armForPublicTestWithUserConfirmation:YES]);
    NSString *different = Observe(another, Fixture(), @"synthetic-list-id")[@"id_session_hmac_sha256"];
    assert(![different isEqual:digest]);
    while (another.records.count < 16) assert(Observe(another, Fixture(), nil));
    assert(!another.active && !Observe(another, Fixture(), nil) && another.records.count == 16);

    TIOOfficialManuscriptProbe *backward = [[TIOOfficialManuscriptProbe alloc] initWithClock:^{ return now; }];
    assert([backward armForPublicTestWithUserConfirmation:YES]);
    now--; assert(!backward.active);
    TIOOfficialManuscriptProbe *invalid = [[TIOOfficialManuscriptProbe alloc] initWithClock:^{ return (NSTimeInterval)NAN; }];
    assert(![invalid armForPublicTestWithUserConfirmation:YES]);

    __block BOOL throwClock = NO;
    TIOOfficialManuscriptProbe *fault = [[TIOOfficialManuscriptProbe alloc] initWithClock:^{
        if (throwClock) @throw [NSException exceptionWithName:@"SyntheticObserverFault" reason:nil userInfo:nil];
        return (NSTimeInterval)300;
    }];
    assert([fault armForPublicTestWithUserConfirmation:YES]);
    throwClock = YES;
    [fault forwardChannel:@"synthetic/test" method:@"save" arguments:Fixture() stableID:nil originalOperation:^{ forwards++; }];
    assert(forwards == 3 && fault.records.count == 0);
    BOOL originalException = NO;
    @try {
        [fault forwardChannel:@"synthetic/test" method:@"save" arguments:Fixture() stableID:nil originalOperation:^{
            @throw [NSException exceptionWithName:@"SyntheticOriginalFault" reason:nil userInfo:nil];
        }];
    } @catch (NSException *exception) { originalException = [exception.name isEqual:@"SyntheticOriginalFault"]; }
    assert(originalException);
    [fault stop]; assert(!fault.active);

    // Real transport shapes, synthetic values: NewsTeleprompter.m Packet/prepare/file.
    TIOOfficialManuscriptProbe *wire = [TIOOfficialManuscriptProbe new];
    assert([wire armForPublicTestWithUserConfirmation:YES]);
    NSDictionary *prepare = @{ @"action": @1, @"did": @"synthetic-wire-id", @"scroll": @2,
        @"total": @([TIOOfficialProbePublicBody lengthOfBytesUsingEncoding:NSUTF8StringEncoding]),
        @"checksum": @"synthetic-checksum" };
    NSData *prepareJSON = [NSJSONSerialization dataWithJSONObject:prepare options:0 error:nil];
    const uint8_t header[] = {8, 1, 16, 2, 26};
    NSMutableData *packet = [NSMutableData dataWithBytes:header length:sizeof(header)];
    NSUInteger length = prepareJSON.length;
    do { uint8_t byte = length & 127; length >>= 7; if (length) byte |= 128;
        [packet appendBytes:&byte length:1]; } while (length);
    [packet appendData:prepareJSON];
    NSDictionary *sendMessage = @{ @"businessId": @20, @"deviceId": @"synthetic-device", @"payload": packet };
    NSDictionary *sendFile = @{ @"deviceId": @"synthetic-device", @"taskId": @"synthetic-wire-id",
        @"filePath": @"/synthetic/not-a-real-container/public-fixture" };
    __block NSUInteger wireForwards = 0;
    [wire forwardChannel:@"none" method:@"rayneonet_sendMessage" arguments:sendMessage stableID:nil
        originalOperation:^{ wireForwards++; }];
    [wire forwardChannel:@"none" method:@"rayneonet_sendFile" arguments:sendFile stableID:nil
        originalOperation:^{ wireForwards++; }];
    // A decoded transport envelope still only holds transfer metadata, never both fixture fields.
    assert((![wire observeChannel:@"rayneonet" method:@"decodeEnvelope" arguments:@{ @"type": @2, @"json": prepare } stableID:nil]));
    // Separate metadata and file bytes do not prove a complete fixture in one observation.
    assert(![wire observeChannel:@"none" method:@"metadata" arguments:@{ @"title": TIOOfficialProbePublicTitle } stableID:nil]);
    assert(![wire observeChannel:@"none" method:@"fileBytes" arguments:[TIOOfficialProbePublicBody dataUsingEncoding:NSUTF8StringEncoding] stableID:nil]);
    assert(wireForwards == 2 && wire.records.count == 0);
    assert([wire observeChannel:@"synthetic/test" method:@"positiveControl" arguments:Fixture() stableID:nil]);
    assert(wire.records.count == 1);
    [wire stop];
    puts("PASS reachability: known business20/file/envelope shapes do not seed public fixture; synthetic positive control does");
    puts("PASS official probe: disabled, consent, exact fixture, redaction, correlation, bounds, expiry, passthrough");
} return 0; }
