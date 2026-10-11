#import "MapKitWireframe.h"
#import "MapKitRoadsCore.h"
#include <assert.h>
#include <math.h>
static NSDictionary *Frame(NSString *turn) {
    return @{@"sessionId":NSUUID.UUID.UUIDString,@"revision":@1,@"provider":@"mapkit",@"mode":@"walk",@"phase":@"navigating",@"turn":turn,
        @"instruction":@"沿测试道路前行",@"road":@"测试道路",@"distance_m":@120,@"remaining_m":@1250,@"remaining_s":@600,
        @"updatedAt":@1,@"latitude":@37,@"longitude":@(-122),@"heading":@(-1),@"coordinateSpace":@"wgs84",
        @"routePoints":@[@[@37,@(-122)],@[@37.001,@(-122)],@[@37.001,@(-121.999)]]};
}
static NSData *Region(NSData *pixels, NSUInteger x, NSUInteger y, NSUInteger width, NSUInteger height) {
    NSMutableData *out = [NSMutableData data]; for (NSUInteger row=y;row<y+height;row++)
        [out appendBytes:(const uint8_t *)pixels.bytes+row*512+x length:width]; return out;
}
static BOOL Lit(NSData *data) { for (NSUInteger i=0;i<data.length;i++) if (((const uint8_t *)data.bytes)[i]) return YES; return NO; }
int main(void) { @autoreleasepool {
    NSArray *turns = @[@"unknown",@"straight",@"left",@"right",@"slightLeft",@"slightRight",@"sharpLeft",@"sharpRight",@"uturn",@"roundabout",@"arrive"];
    NSMutableSet *arrows = [NSMutableSet set];
    for (NSString *turn in turns) { NSData *pixels = TIOMapKitRenderPixels(Frame(turn)); assert(pixels.length == 65536);
        NSData *arrow = Region(pixels,0,0,90,110); assert(Lit(arrow)); [arrows addObject:arrow];
        assert(Lit(Region(pixels,96,0,290,124))); assert(Lit(Region(pixels,394,108,115,17))); }
    assert(arrows.count == turns.count); // UNKNOWN must be question mark, not straight
    assert(TIOMapKitRenderPixels(nil).length == 65536); assert(TIOMapKitRenderPixels((id)NSNull.null).length == 65536);
    NSMutableDictionary *stale = [Frame(@"straight") mutableCopy]; stale[@"phase"] = @"stale";
    assert([Region(TIOMapKitRenderPixels(stale),0,0,90,110) isEqual:Region(TIOMapKitRenderPixels(Frame(@"unknown")),0,0,90,110)]);
    NSMutableDictionary *weak = [Frame(@"straight") mutableCopy]; weak[@"phase"] = @"weak";
    assert(![Region(TIOMapKitRenderPixels(weak),96,49,292,29) isEqual:Region(TIOMapKitRenderPixels(Frame(@"straight")),96,49,292,29)]);
    NSArray *bad = @[NSNull.null,@"bad",@{},@[],@(NAN),@(INFINITY),@YES,@NO];
    NSArray *keys = @[@"turn",@"instruction",@"road",@"distance_m",@"remaining_m",@"remaining_s",@"latitude",@"longitude",@"heading",@"routePoints",@"roadSegments",@"coordinateSpace"];
    for (id value in bad) for (NSString *key in keys) { NSMutableDictionary *frame = [Frame(@"unknown") mutableCopy]; frame[key] = value;
        assert(TIOMapKitRenderPixels(frame).length == 65536); }
    NSMutableDictionary *frame = [Frame(@"straight") mutableCopy];
    frame[@"instruction"] = [@"中文😀\n" stringByPaddingToLength:40000 withString:@"路" startingAtIndex:0];
    frame[@"road"] = [@"路" stringByPaddingToLength:40000 withString:@"路" startingAtIndex:0];
    NSData *longPixels = TIOMapKitRenderPixels(frame); NSData *base = TIOMapKitRenderPixels(Frame(@"straight"));
    assert([Region(longPixels,0,0,90,128) isEqual:Region(base,0,0,90,128)]);
    assert([Region(longPixels,394,0,118,128) isEqual:Region(base,394,0,118,128)]); // clipped text cannot damage map
    frame = [Frame(@"straight") mutableCopy];
    frame[@"roadSegments"] = @[@[@[@37.0002,@(-122.001)],@[@37.0002,@(-121.998)]]];
    NSData *roads = TIOMapKitRenderPixels(frame); assert(![Region(roads,395,24,110,81) isEqual:Region(base,395,24,110,81)]);
    frame[@"coordinateSpace"] = @"unverified"; NSData *unverified = TIOMapKitRenderPixels(frame);
    [frame removeObjectForKey:@"roadSegments"]; assert([unverified isEqual:TIOMapKitRenderPixels(frame)]);
    frame = [Frame(@"straight") mutableCopy]; frame[@"heading"] = @0; assert([base isEqual:TIOMapKitRenderPixels(frame)]);
    frame[@"heading"] = @90; assert(![Region(base,395,24,110,81) isEqual:Region(TIOMapKitRenderPixels(frame),395,24,110,81)]);
    frame[@"heading"] = @360; assert([base isEqual:TIOMapKitRenderPixels(frame)]);
    frame[@"heading"] = @(NAN); assert([base isEqual:TIOMapKitRenderPixels(frame)]);
    frame[@"routePoints"] = @[@[@(-90),@(-180)],NSNull.null,@[@90,@180],@[@37,@(-122)],@[@"37",@0]];
    assert(TIOMapKitRenderPixels(frame).length == 65536);
    NSMutableArray *oversize = [NSMutableArray array]; for (int i=0;i<10000;i++) [oversize addObject:@[@37,@(-122)]];
    frame[@"routePoints"] = oversize; frame[@"roadSegments"] = @[oversize,oversize]; assert(TIOMapKitRenderPixels(frame).length == 65536);
    UIImage *image = TIOMapKitPixelsPreview(base); assert(image && image.size.width == 512 && image.size.height == 128);
    assert(CGImageGetBitsPerPixel(image.CGImage) == 8 && CGImageGetBitsPerComponent(image.CGImage) == 8);
    assert(!TIOMapKitPixelsPreview(nil)); assert(!TIOMapKitPixelsPreview([NSData data])); assert(!TIOMapKitPixelsPreview((id)NSNull.null));
    // Optional preview fixture is outside the repository, consumed by the integration script.
    NSString *output = NSProcessInfo.processInfo.environment[@"TIO_MAPKIT_PREVIEW_PATH"];
    if (output.length) assert([UIImagePNGRepresentation(image) writeToFile:output atomically:YES]);
    puts("PASS UIKit wireframe: all arrows distinct incl ?, Chinese/clipped text, malformed fields, WGS84 road gate, heading/north projection, culling/caps, 512x128 grayscale and UIImage");
} return 0; }
