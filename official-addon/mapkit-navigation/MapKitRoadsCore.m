#import "MapKitRoadsCore.h"
#include <math.h>
// Selective 450m Overpass/geom reuse: sattarde/rayneo-ios-english-edition
// 6268990646ebd41188cb084bb8ef96d70e94b869 official-addon/focus-edition/NavigationRoadNetwork.inc.
// Cache lifecycle, filtering and streaming safety rewritten.

BOOL TIOMapKitNumber(id value, double *number) {
    if (![value isKindOfClass:NSNumber.class] || CFGetTypeID((__bridge CFTypeRef)value) == CFBooleanGetTypeID()) return NO;
    double n = [value doubleValue];
    if (!isfinite(n)) return NO;
    if (number) *number = n;
    return YES;
}
BOOL TIOMapKitCoordinate(double latitude, double longitude) {
    return isfinite(latitude) && isfinite(longitude) && fabs(latitude) <= 90 && fabs(longitude) <= 180;
}
BOOL TIOMapKitWGS84Established(double latitude, double longitude) {
    // Conservative exclusion until MapKit/mainland-China coordinate alignment is verified.
    return TIOMapKitCoordinate(latitude, longitude) && !(latitude >= 18 && latitude <= 54 && longitude >= 73 && longitude <= 135);
}
BOOL TIOMapKitPoint(id value, double *latitude, double *longitude) {
    double lat, lon;
    if (![value isKindOfClass:NSArray.class] || [value count] != 2 ||
        !TIOMapKitNumber(value[0], &lat) || !TIOMapKitNumber(value[1], &lon) || !TIOMapKitCoordinate(lat, lon)) return NO;
    if (latitude) *latitude = lat;
    if (longitude) *longitude = lon;
    return YES;
}
TIOMapPoint TIOMapKitProject(double latitude, double longitude, double originLatitude, double originLongitude, double heading, double scale) {
    double delta = remainder(longitude - originLongitude, 360);
    double east = delta * cos(originLatitude * M_PI / 180) * 111320;
    double north = (latitude - originLatitude) * 111320;
    double angle = (heading >= 0 && isfinite(heading) ? fmod(heading, 360) : 0) * M_PI / 180;
    return (TIOMapPoint){(east * cos(angle) - north * sin(angle)) * scale,
                         -(east * sin(angle) + north * cos(angle)) * scale};
}
static BOOL Mode(NSString *mode) {
    return [mode isKindOfClass:NSString.class] && ([@"walk" isEqual:mode] || [@"ride" isEqual:mode] || [@"drive" isEqual:mode]);
}
static BOOL Allowed(NSDictionary *tags, NSString *mode) {
    id highway = tags[@"highway"];
    if (![highway isKindOfClass:NSString.class] || ![highway length]) return NO;
    if ([highway isEqual:@"construction"] || [highway isEqual:@"proposed"]) return NO;
    NSString *accessKey = [mode isEqual:@"walk"] ? @"foot" : [mode isEqual:@"ride"] ? @"bicycle" : @"motor_vehicle";
    id explicit = tags[accessKey];
    if ([explicit isEqual:@"no"] || [explicit isEqual:@"private"]) return NO;
    if (([tags[@"access"] isEqual:@"no"] || [tags[@"access"] isEqual:@"private"]) &&
        !([explicit isEqual:@"yes"] || [explicit isEqual:@"designated"] || [explicit isEqual:@"permissive"])) return NO;
    if ([mode isEqual:@"drive"]) return ![@[@"footway", @"pedestrian", @"path", @"cycleway", @"steps", @"bridleway"] containsObject:highway];
    if ([highway isEqual:@"motorway"] || [highway isEqual:@"motorway_link"]) return [explicit isEqual:@"yes"];
    if ([mode isEqual:@"ride"] && [highway isEqual:@"steps"]) return [explicit isEqual:@"yes"];
    return YES;
}
NSString *TIOMapKitRoadQuery(double latitude, double longitude, NSString *mode) {
    if (!Mode(mode) || !TIOMapKitWGS84Established(latitude, longitude)) return nil;
    return [NSString stringWithFormat:@"[out:json][timeout:15];way(around:450,%.7f,%.7f)[highway];out tags geom;", latitude, longitude];
}
NSArray *TIOMapKitParseRoads(NSData *data, NSString *mode) {
    if (![data isKindOfClass:NSData.class] || data.length > 512 * 1024 || !Mode(mode)) return nil;
    id root = [NSJSONSerialization JSONObjectWithData:data options:0 error:NULL];
    if (![root isKindOfClass:NSDictionary.class] || ![root[@"elements"] isKindOfClass:NSArray.class] || root[@"remark"]) return nil;
    NSMutableArray *roads = [NSMutableArray array];
    NSUInteger count = 0;
    for (id element in root[@"elements"]) {
        if (![element isKindOfClass:NSDictionary.class] || ![element[@"type"] isEqual:@"way"] ||
            ![element[@"tags"] isKindOfClass:NSDictionary.class] || !Allowed(element[@"tags"], mode) ||
            ![element[@"geometry"] isKindOfClass:NSArray.class]) continue;
        NSMutableArray *segment = [NSMutableArray array];
        for (id point in element[@"geometry"]) {
            double lat, lon;
            if (![point isKindOfClass:NSDictionary.class] || !TIOMapKitNumber(point[@"lat"], &lat) ||
                !TIOMapKitNumber(point[@"lon"], &lon) || !TIOMapKitCoordinate(lat, lon)) {
                // Do not connect across corrupt/missing geometry.
                if (segment.count >= 2) { [roads addObject:segment.copy]; count += segment.count; }
                segment = [NSMutableArray array];
            } else {
                [segment addObject:@[@(lat), @(lon)]];
            }
            if (count + segment.count >= 4096 || roads.count >= 256) break;
        }
        if (segment.count >= 2 && roads.count < 256) { [roads addObject:segment.copy]; count += segment.count; }
        if (count >= 4096 || roads.count >= 256) break;
    }
    return roads.copy;
}
NSString *TIOMapKitTurnLabel(id turn) {
    if (![turn isKindOfClass:NSString.class]) return @"?";
    return (@{@"straight":@"直行", @"left":@"左转", @"right":@"右转", @"slightLeft":@"向左前方", @"slightRight":@"向右前方",
              @"sharpLeft":@"向左急转", @"sharpRight":@"向右急转", @"uturn":@"掉头", @"roundabout":@"环岛", @"arrive":@"到达"})[turn] ?: @"?";
}
BOOL TIOMapKitAcceptResponse(NSInteger status, long long expectedBytes) {
    return status >= 200 && status < 300 && (expectedBytes < 0 || expectedBytes <= 512 * 1024);
}
BOOL TIOMapKitAppendResponse(NSMutableData *buffer, NSData *chunk) {
    if (![buffer isKindOfClass:NSMutableData.class] || ![chunk isKindOfClass:NSData.class] ||
        buffer.length > 512 * 1024 || chunk.length > 512 * 1024 - buffer.length) return NO;
    [buffer appendData:chunk];
    return YES;
}

@implementation TIOMapKitRoadPolicy {
    NSUInteger _generation, _inFlight, _failures;
    NSArray *_segments;
    NSString *_mode;
    double _latitude, _longitude, _requestedLatitude, _requestedLongitude, _cacheTime, _lastQuery, _retryTime;
    BOOL _hasCache, _hasQueried;
}
- (instancetype)init { if ((self = [super init])) _segments = @[]; return self; }
- (NSUInteger)generation { return _generation; }
- (NSArray *)segments { return _segments; }
- (NSUInteger)beginLatitude:(double)latitude longitude:(double)longitude mode:(NSString *)mode time:(double)time {
    if (!isfinite(time) || !Mode(mode)) { [self cancel]; return 0; }
    if (![_mode isEqual:mode]) {
        _generation++; _inFlight = 0; _hasCache = NO; _segments = @[]; _failures = 0; _retryTime = 0; _mode = [mode copy];
    }
    if (!TIOMapKitWGS84Established(latitude, longitude)) {
        _generation++; _inFlight = 0; _hasCache = NO; _segments = @[]; return 0;
    }
    if (_inFlight) {
        TIOMapPoint pendingDistance = TIOMapKitProject(latitude, longitude, _requestedLatitude, _requestedLongitude, 0, 1);
        if (hypot(pendingDistance.x, pendingDistance.y) > 150) { _generation++; _inFlight = 0; }
    }
    TIOMapPoint distance = TIOMapKitProject(latitude, longitude, _latitude, _longitude, 0, 1);
    if (_hasCache && time >= _cacheTime && time - _cacheTime < 120 && hypot(distance.x, distance.y) <= 150) return 0;
    _hasCache = NO; _segments = @[];
    if (_inFlight || (_hasQueried && time - _lastQuery < 30) || time < _retryTime) return 0;
    _hasQueried = YES; _lastQuery = time; _requestedLatitude = latitude; _requestedLongitude = longitude;
    _inFlight = ++_generation;
    return _inFlight;
}
- (BOOL)completeGeneration:(NSUInteger)generation segments:(NSArray *)segments time:(double)time {
    if (!generation || generation != _inFlight || generation != _generation || !isfinite(time)) return NO;
    _inFlight = 0;
    if (segments) {
        _segments = [segments copy]; _latitude = _requestedLatitude; _longitude = _requestedLongitude;
        _cacheTime = time; _hasCache = YES; _failures = 0; _retryTime = 0;
    } else {
        _segments = @[]; _hasCache = NO; _failures = MIN(_failures + 1, 4);
        _retryTime = time + MIN(240, 30 * pow(2, _failures - 1));
    }
    return YES;
}
- (void)cancel {
    _generation++; _inFlight = 0; _hasCache = NO; _segments = @[]; _mode = nil; _failures = 0; _retryTime = 0;
    _latitude = _longitude = _requestedLatitude = _requestedLongitude = _cacheTime = 0;
}
@end
