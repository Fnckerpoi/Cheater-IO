#import "MapKitRoadsCore.h"
#include <assert.h>
#include <math.h>
static NSData *JSON(id value) { return [NSJSONSerialization dataWithJSONObject:value options:0 error:NULL]; }
static NSDictionary *Way(NSString *highway, NSArray *geometry) { return @{@"type":@"way", @"tags":@{@"highway":highway}, @"geometry":geometry}; }
static NSArray *Geometry(void) { return @[@{@"lat":@37, @"lon":@(-122)}, @{@"lat":@37.001, @"lon":@(-122.001)}]; }
static NSUInteger Begin(TIOMapKitRoadPolicy *p, double now, NSString *mode) { return [p beginLatitude:37 longitude:-122 mode:mode time:now]; }
int main(void) { @autoreleasepool {
    double n; assert(!TIOMapKitNumber(@YES, &n)); assert(!TIOMapKitNumber(@NO, &n)); assert(!TIOMapKitPoint(@[@YES,@0],NULL,NULL));
    assert(!TIOMapKitNumber(@"4", &n)); assert(!TIOMapKitNumber(@(NAN), &n)); assert(!TIOMapKitNumber(NSNull.null, &n));
    assert(TIOMapKitCoordinate(90, 180)); assert(!TIOMapKitCoordinate(91, 0)); assert(!TIOMapKitCoordinate(INFINITY, 0));
    assert(TIOMapKitPoint(@[@37,@(-122)], NULL, NULL)); assert(!TIOMapKitPoint(@[@37], NULL, NULL)); assert(!TIOMapKitPoint(@[@"37",@0], NULL, NULL));
    assert(!TIOMapKitWGS84Established(31.2,121.5)); assert(TIOMapKitWGS84Established(37,-122));
    assert(!TIOMapKitRoadQuery(31.2,121.5,@"drive")); assert(!TIOMapKitRoadQuery(37,-122,@"invalid"));
    assert([TIOMapKitRoadQuery(37,-122,@"walk") containsString:@"around:450"]);
    TIOMapPoint north = TIOMapKitProject(37.001,-122,37,-122,-1,1);
    assert(fabs(north.x) < 1e-6 && fabs(north.y + 111.32) < 0.01);
    TIOMapPoint east = TIOMapKitProject(37,-121.999,37,-122,90,1); assert(fabs(east.x) < 1e-6 && east.y < -80);
    TIOMapPoint northEast = TIOMapKitProject(37.001,-122,37,-122,90,1); assert(northEast.x < -111 && fabs(northEast.y) < 1e-6);
    TIOMapPoint wrap = TIOMapKitProject(0,-179.999,0,179.999,0,1); assert(wrap.x > 200 && wrap.x < 230);
    assert([TIOMapKitTurnLabel(@"unknown") isEqual:@"?"]); assert([TIOMapKitTurnLabel(NSNull.null) isEqual:@"?"]);
    assert([TIOMapKitTurnLabel(@"straight") isEqual:@"直行"]);
    NSData *roads = JSON(@{@"elements":@[Way(@"footway",Geometry()), Way(@"cycleway",Geometry()), Way(@"residential",Geometry()), Way(@"motorway",Geometry()),Way(@"steps",Geometry())]});
    assert(TIOMapKitParseRoads(roads,@"walk").count == 4); assert(TIOMapKitParseRoads(roads,@"ride").count == 3); assert(TIOMapKitParseRoads(roads,@"drive").count == 2);
    NSDictionary *access = @{@"type":@"way",@"tags":@{@"highway":@"path",@"access":@"no",@"foot":@"yes",@"bicycle":@"no"},@"geometry":Geometry()};
    assert(TIOMapKitParseRoads(JSON(@{@"elements":@[access]}),@"walk").count == 1);
    assert(TIOMapKitParseRoads(JSON(@{@"elements":@[access]}),@"ride").count == 0);
    assert(!TIOMapKitParseRoads(JSON(@[]),@"walk")); assert(!TIOMapKitParseRoads([@"oops" dataUsingEncoding:NSUTF8StringEncoding],@"walk"));
    assert(!TIOMapKitParseRoads(JSON(@{@"elements":@[],@"remark":@"timeout"}),@"walk"));
    assert(TIOMapKitParseRoads(JSON(@{@"elements":@[]}),@"walk").count == 0);
    NSArray *broken = @[Geometry()[0],NSNull.null,Geometry()[1],@{@"lat":@90,@"lon":@181},@{@"lat":@"37",@"lon":@0}];
    assert(TIOMapKitParseRoads(JSON(@{@"elements":@[NSNull.null,@"bad",Way(@"residential",broken)]}),@"drive").count == 0);
    NSMutableArray *many = [NSMutableArray array]; for (int i=0;i<300;i++) [many addObject:Way(@"residential",Geometry())];
    assert(TIOMapKitParseRoads(JSON(@{@"elements":many}),@"drive").count == 256);
    NSMutableArray *points = [NSMutableArray array]; for(int i=0;i<5000;i++) [points addObject:Geometry()[0]];
    NSArray *limited = TIOMapKitParseRoads(JSON(@{@"elements":@[Way(@"residential",points)]}),@"drive"); assert([limited[0] count] == 4096);
    assert(TIOMapKitAcceptResponse(200, -1)); assert(TIOMapKitAcceptResponse(200, 512*1024));
    assert(!TIOMapKitAcceptResponse(200, 512*1024+1)); assert(!TIOMapKitAcceptResponse(429, -1));
    assert(!TIOMapKitAcceptResponse(500, 100)); assert(!TIOMapKitAcceptResponse(503, -1));
    assert(!TIOMapKitAcceptResponse(0, -1)); assert(!TIOMapKitAcceptResponse(302, -1));
    NSMutableData *buffer = [NSMutableData data]; assert(TIOMapKitAppendResponse(buffer,[NSMutableData dataWithLength:512*1024]));
    assert(!TIOMapKitAppendResponse(buffer,[NSMutableData dataWithLength:1])); assert(buffer.length == 512*1024);
    assert(!TIOMapKitParseRoads([NSMutableData dataWithLength:512*1024+1],@"drive"));
    TIOMapKitRoadPolicy *p = [TIOMapKitRoadPolicy new]; NSUInteger g = Begin(p,0,@"walk"); assert(g);
    assert(!Begin(p,1,@"walk")); assert([p completeGeneration:g segments:@[] time:1]);
    assert(!Begin(p,31,@"walk")); assert(!Begin(p,120,@"walk")); // empty response is negatively cached
    g = Begin(p,121,@"walk"); assert(g); assert([p completeGeneration:g segments:@[Geometry()] time:122]);
    assert(![p beginLatitude:37.0005 longitude:-122 mode:@"walk" time:153]); assert(p.segments.count == 1);
    g = [p beginLatitude:37.002 longitude:-122 mode:@"walk" time:153]; assert(g && !p.segments.count);
    assert([p completeGeneration:g segments:nil time:154]); assert(!Begin(p,183,@"walk"));
    g = Begin(p,184,@"walk"); assert(g); assert([p completeGeneration:g segments:nil time:185]);
    assert(!Begin(p,244,@"walk")); g=Begin(p,245,@"walk"); assert(g); assert([p completeGeneration:g segments:nil time:246]);
    assert(!Begin(p,365,@"walk")); g=Begin(p,366,@"walk"); assert(g); assert([p completeGeneration:g segments:nil time:367]);
    assert(!Begin(p,606,@"walk")); g=Begin(p,607,@"walk"); assert(g); assert([p completeGeneration:g segments:nil time:608]);
    g=Begin(p,848,@"walk"); assert(g); // bounded retry, only explicit update
    [p cancel]; assert(![p completeGeneration:g segments:@[Geometry()] time:849]); assert(!Begin(p,849,@"walk"));
    NSUInteger newg=Begin(p,878,@"walk"); assert(newg && newg != g);
    assert(![p completeGeneration:g segments:@[Geometry()] time:879]); assert(!Begin(p,908,@"walk")); // old completion cannot unblock new request
    assert([p completeGeneration:newg segments:@[Geometry()] time:909]); assert(p.segments.count == 1);
    assert(Begin(p,910,@"drive")); assert(!p.segments.count); [p cancel]; // mode invalidates cache
    newg=Begin(p,940,@"drive"); assert(newg); [p cancel];
    assert(![p completeGeneration:newg segments:@[Geometry()] time:941]);
    assert(![p beginLatitude:31 longitude:121 mode:@"drive" time:1000]); assert(!p.segments.count);
    TIOMapKitRoadPolicy *moving = [TIOMapKitRoadPolicy new];
    NSUInteger oldMoving = Begin(moving,0,@"walk"); assert(oldMoving);
    assert(![moving beginLatitude:37.002 longitude:-122 mode:@"walk" time:1]);
    assert(![moving completeGeneration:oldMoving segments:@[Geometry()] time:2]);
    NSUInteger newMoving = [moving beginLatitude:37.002 longitude:-122 mode:@"walk" time:30]; assert(newMoving);
    assert(![moving completeGeneration:oldMoving segments:@[] time:31]);
    assert(![moving beginLatitude:37.002 longitude:-122 mode:@"walk" time:60]);
    assert([moving completeGeneration:newMoving segments:@[Geometry()] time:61]);
    assert([moving beginLatitude:37.002 longitude:-122 mode:@"ride" time:62]);
    assert(!moving.segments.count); [moving cancel];
    TIOMapKitRoadPolicy *modeChange = [TIOMapKitRoadPolicy new];
    NSUInteger oldMode = Begin(modeChange,0,@"walk"); assert(oldMode);
    assert(!Begin(modeChange,1,@"drive")); assert(![modeChange completeGeneration:oldMode segments:@[Geometry()] time:2]);
    NSUInteger newMode = Begin(modeChange,30,@"drive"); assert(newMode);
    assert(![modeChange completeGeneration:oldMode segments:@[Geometry()] time:31]);
    assert(!Begin(modeChange,60,@"drive")); assert([modeChange completeGeneration:newMode segments:@[] time:61]);
    assert(!Begin(modeChange,62,@"wrong")); assert(!modeChange.segments.count);
    puts("PASS roads core: mode filtering, malformed JSON, finite coordinates, caps, streaming bound, projection, negative cache, TTL/distance, throttling, retries, cancel generations");
} return 0; }
