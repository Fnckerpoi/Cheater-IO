#import "MapKitWireframe.h"
#import "MapKitRoadsCore.h"
#include <math.h>
// Adapted layout, local-meter heading projection and arrow shapes from sattarde/rayneo-ios-english-edition,
// 6268990646ebd41188cb084bb8ef96d70e94b869 official-addon/focus-edition/DisplayPhoneUI.m (238–456).

static NSString *Text(id value, NSString *fallback) {
    if (![value isKindOfClass:NSString.class] || ![value length]) return fallback;
    NSString *text = value;
    if (text.length > 240) text = [text substringWithRange:[text rangeOfComposedCharacterSequencesForRange:NSMakeRange(0, 240)]];
    return [[text componentsSeparatedByCharactersInSet:NSCharacterSet.newlineCharacterSet] componentsJoinedByString:@" "];
}
static void DrawText(NSString *text, CGRect rect, CGFloat size, CGFloat gray, BOOL bold) {
    CGContextRef ctx = UIGraphicsGetCurrentContext();
    CGContextSaveGState(ctx); CGContextClipToRect(ctx, rect);
    NSMutableParagraphStyle *style = [NSMutableParagraphStyle new];
    style.lineBreakMode = NSLineBreakByTruncatingTail;
    [text drawInRect:rect withAttributes:@{NSFontAttributeName:bold ? [UIFont boldSystemFontOfSize:size] : [UIFont systemFontOfSize:size],
        NSForegroundColorAttributeName:[UIColor colorWithWhite:gray alpha:1], NSParagraphStyleAttributeName:style}];
    CGContextRestoreGState(ctx);
}
static NSString *Meters(id value) {
    double n;
    if (!TIOMapKitNumber(value, &n) || n < 0 || n > 1e9) return @"距离未知";
    return n >= 1000 ? [NSString stringWithFormat:@"%.1f 公里", n / 1000] : [NSString stringWithFormat:@"%.0f 米", n];
}
static void Stroke(UIBezierPath *path, CGFloat width) {
    path.lineWidth = width; path.lineCapStyle = kCGLineCapRound; path.lineJoinStyle = kCGLineJoinRound; [path stroke];
}
static void ArrowHead(UIBezierPath *path, CGPoint tip, double dx, double dy) {
    double length = hypot(dx, dy); if (length == 0) return;
    dx /= length; dy /= length;
    [path moveToPoint:CGPointMake(tip.x - dx * 15 - dy * 12, tip.y - dy * 15 + dx * 12)];
    [path addLineToPoint:tip];
    [path addLineToPoint:CGPointMake(tip.x - dx * 15 + dy * 12, tip.y - dy * 15 - dx * 12)];
}
static void DrawArrow(id rawTurn) {
    NSString *turn = [rawTurn isKindOfClass:NSString.class] ? rawTurn : @"unknown";
    [UIColor.whiteColor setStroke]; [UIColor.whiteColor setFill];
    if ([TIOMapKitTurnLabel(turn) isEqual:@"?"]) { DrawText(@"?", CGRectMake(18, 12, 60, 94), 72, 1, YES); return; }
    UIBezierPath *path = [UIBezierPath bezierPath];
    CGPoint start = CGPointMake(47, 100), bend = CGPointMake(47, 57), tip = CGPointMake(47, 20);
    if ([turn isEqual:@"arrive"]) {
        [path moveToPoint:start]; [path addLineToPoint:tip]; Stroke(path, 5);
        path = [UIBezierPath bezierPath]; [path moveToPoint:tip]; [path addLineToPoint:CGPointMake(77, 34)];
        [path addLineToPoint:CGPointMake(47, 49)]; [path closePath]; [path fill]; return;
    }
    if ([turn isEqual:@"roundabout"]) {
        path = [UIBezierPath bezierPathWithOvalInRect:CGRectMake(28, 36, 38, 38)]; Stroke(path, 5);
        path = [UIBezierPath bezierPath]; [path moveToPoint:start]; [path addLineToPoint:CGPointMake(47, 74)];
        [path moveToPoint:CGPointMake(66, 55)]; [path addLineToPoint:CGPointMake(66, 22)];
        ArrowHead(path, CGPointMake(66, 22), 0, -1); Stroke(path, 6); return;
    }
    if ([turn isEqual:@"uturn"]) {
        [path moveToPoint:CGPointMake(61, 100)]; [path addLineToPoint:CGPointMake(61, 40)];
        [path addCurveToPoint:CGPointMake(30, 40) controlPoint1:CGPointMake(61, 14) controlPoint2:CGPointMake(30, 14)];
        tip = CGPointMake(30, 70); [path addLineToPoint:tip]; ArrowHead(path, tip, 0, 1); Stroke(path, 6); return;
    }
    BOOL left = [turn isEqual:@"left"] || [turn isEqual:@"slightLeft"] || [turn isEqual:@"sharpLeft"];
    BOOL right = [turn isEqual:@"right"] || [turn isEqual:@"slightRight"] || [turn isEqual:@"sharpRight"];
    if (left || right) {
        double direction = left ? -1 : 1;
        tip.x = 47 + direction * 30;
        tip.y = [turn hasPrefix:@"slight"] ? 25 : [turn hasPrefix:@"sharp"] ? 88 : 57;
        [path moveToPoint:start]; [path addLineToPoint:bend]; [path addLineToPoint:tip];
        ArrowHead(path, tip, tip.x - bend.x, tip.y - bend.y);
    } else { [path moveToPoint:start]; [path addLineToPoint:tip]; ArrowHead(path, tip, 0, -1); }
    Stroke(path, 6);
}
// Liang–Barsky clipping avoids passing distant or polar geometry into the rasterizer.
static BOOL Clip(CGPoint *a, CGPoint *b, CGRect box) {
    double dx = b->x - a->x, dy = b->y - a->y, lo = 0, hi = 1;
    double p[] = {-dx, dx, -dy, dy};
    double q[] = {a->x - CGRectGetMinX(box), CGRectGetMaxX(box) - a->x, a->y - CGRectGetMinY(box), CGRectGetMaxY(box) - a->y};
    for (int i = 0; i < 4; i++) {
        if (p[i] == 0) { if (q[i] < 0) return NO; continue; }
        double r = q[i] / p[i];
        if (p[i] < 0) lo = MAX(lo, r); else hi = MIN(hi, r);
        if (lo > hi) return NO;
    }
    CGPoint origin = *a; *a = CGPointMake(origin.x + lo * dx, origin.y + lo * dy);
    *b = CGPointMake(origin.x + hi * dx, origin.y + hi * dy); return YES;
}
static void Polyline(id values, double latitude, double longitude, double heading, CGRect box, CGFloat width, CGFloat gray, NSUInteger *budget) {
    if (![values isKindOfClass:NSArray.class]) return;
    CGPoint previous = CGPointZero; BOOL hasPrevious = NO;
    UIBezierPath *path = [UIBezierPath bezierPath];
    for (id value in values) {
        if (!*budget) break; (*budget)--;
        double lat, lon;
        if (!TIOMapKitPoint(value, &lat, &lon)) { hasPrevious = NO; continue; }
        TIOMapPoint projected = TIOMapKitProject(lat, lon, latitude, longitude, heading, 0.22);
        CGPoint next = CGPointMake(450 + projected.x, 85 + projected.y);
        if (!isfinite(next.x) || !isfinite(next.y)) { hasPrevious = NO; continue; }
        if (!hasPrevious) { previous = next; hasPrevious = YES; continue; }
        // Streaming sub-pixel simplification, without joining across invalid points.
        if (hypot(next.x - previous.x, next.y - previous.y) < 0.75 && value != [values lastObject]) continue;
        CGPoint a = previous, b = next; previous = next;
        if (!Clip(&a, &b, box)) continue;
        [path moveToPoint:a]; [path addLineToPoint:b];
    }
    [[UIColor colorWithWhite:gray alpha:1] setStroke]; Stroke(path, width);
}
NSData *TIOMapKitRenderPixels(NSDictionary *rawFrame) {
    NSDictionary *frame = [rawFrame isKindOfClass:NSDictionary.class] ? rawFrame : @{};
    NSMutableData *pixels = [NSMutableData dataWithLength:512 * 128];
    CGColorSpaceRef space = CGColorSpaceCreateDeviceGray();
    CGContextRef ctx = CGBitmapContextCreate(pixels.mutableBytes, 512, 128, 8, 512, space, (CGBitmapInfo)kCGImageAlphaNone);
    CGColorSpaceRelease(space); if (!ctx) return pixels.copy;
    CGContextTranslateCTM(ctx, 0, 128); CGContextScaleCTM(ctx, 1, -1); UIGraphicsPushContext(ctx);
    BOOL stale = [frame[@"phase"] isEqual:@"stale"], weak = [frame[@"phase"] isEqual:@"weak"];
    id turn = stale ? @"unknown" : frame[@"turn"];
    DrawArrow(turn);
    DrawText(Meters(stale ? nil : frame[@"distance_m"]), CGRectMake(96, 3, 292, 44), 33, 1, YES);
    NSString *instruction = Text(frame[@"instruction"], TIOMapKitTurnLabel(turn));
    if (stale) instruction = @"定位已过期 · 等待更新";
    else if (weak) instruction = [@"定位较弱 · " stringByAppendingString:instruction];
    DrawText(instruction, CGRectMake(96, 49, 292, 29), 20, 1, YES);
    DrawText(Text(frame[@"road"], @"道路未知"), CGRectMake(96, 80, 292, 22), 16, 0.85, NO);
    double seconds; NSString *eta = TIOMapKitNumber(frame[@"remaining_s"], &seconds) && seconds >= 0 && seconds < 1e8
        ? [NSString stringWithFormat:@"%.0f 分钟", ceil(seconds / 60)] : @"时间未知";
    NSString *summary = [NSString stringWithFormat:@"剩余 %@ · %@", Meters(frame[@"remaining_m"]), eta];
    DrawText(summary, CGRectMake(96, 107, 292, 17), 11, 0.7, NO);
    CGRect map = CGRectMake(394, 6, 112, 100);
    [[UIColor colorWithWhite:0.3 alpha:1] setStroke]; Stroke([UIBezierPath bezierPathWithRoundedRect:map cornerRadius:6], 1);
    CGContextSaveGState(ctx); CGContextClipToRect(ctx, CGRectInset(map, 1, 1));
    double latitude, longitude, heading = -1;
    TIOMapKitNumber(frame[@"heading"], &heading);
    if (TIOMapKitNumber(frame[@"latitude"], &latitude) && TIOMapKitNumber(frame[@"longitude"], &longitude) && TIOMapKitCoordinate(latitude, longitude)) {
        NSUInteger roadBudget = 4096, roadCount = 0;
        id roads = frame[@"roadSegments"];
        if ([frame[@"coordinateSpace"] isEqual:@"wgs84"] && [roads isKindOfClass:NSArray.class]) {
            for (id road in roads) { if (roadCount++ >= 256 || !roadBudget) break;
                Polyline(road, latitude, longitude, heading, map, 1, 0.35, &roadBudget); }
        }
        NSUInteger routeBudget = 4096; Polyline(frame[@"routePoints"], latitude, longitude, heading, map, 3.5, 1, &routeBudget);
        UIBezierPath *cursor = [UIBezierPath bezierPath]; [cursor moveToPoint:CGPointMake(450, 77)];
        [cursor addLineToPoint:CGPointMake(456, 91)]; [cursor addLineToPoint:CGPointMake(450, 88)];
        [cursor addLineToPoint:CGPointMake(444, 91)]; [cursor closePath]; [UIColor.whiteColor setFill]; [cursor fill];
        static NSString *const cardinals[] = {@"N", @"NE", @"E", @"SE", @"S", @"SW", @"W", @"NW"};
        double h = heading >= 0 && isfinite(heading) ? fmod(heading, 360) : 0;
        DrawText(cardinals[(int)lround(h / 45) % 8], CGRectMake(399, 9, 28, 14), 10, 0.8, YES);
        DrawText(@"100m", CGRectMake(469, 9, 31, 14), 9, 0.6, NO);
        UIBezierPath *scaleBar = [UIBezierPath bezierPath];
        [scaleBar moveToPoint:CGPointMake(471, 22)]; [scaleBar addLineToPoint:CGPointMake(471, 25)];
        [scaleBar addLineToPoint:CGPointMake(493, 25)]; [scaleBar addLineToPoint:CGPointMake(493, 22)];
        [[UIColor colorWithWhite:0.6 alpha:1] setStroke]; Stroke(scaleBar, 1);
    } else DrawText(@"定位未知", CGRectMake(401, 49, 99, 18), 13, 0.7, NO);
    CGContextRestoreGState(ctx);
    DrawText(@"© OpenStreetMap", CGRectMake(394, 109, 115, 14), 9, 0.8, NO);
    UIGraphicsPopContext(); CGContextRelease(ctx); return pixels.copy;
}
UIImage *TIOMapKitPixelsPreview(NSData *pixels) {
    if (![pixels isKindOfClass:NSData.class] || pixels.length != 65536) return nil;
    CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)pixels);
    CGColorSpaceRef space = CGColorSpaceCreateDeviceGray();
    CGImageRef image = CGImageCreate(512, 128, 8, 8, 512, space, (CGBitmapInfo)kCGImageAlphaNone, provider, NULL, NO, kCGRenderingIntentDefault);
    UIImage *preview = image ? [UIImage imageWithCGImage:image scale:1 orientation:UIImageOrientationUp] : nil;
    if (image) CGImageRelease(image); CGColorSpaceRelease(space); CGDataProviderRelease(provider); return preview;
}
