#import "MapKitNavigationCore.h"
#include <math.h>

BOOL TIOMapKitCoordinateValid(double lat,double lon){return isfinite(lat)&&isfinite(lon)&&fabs(lat)<=90&&fabs(lon)<=180;}
static BOOL PointValid(id p){return [p isKindOfClass:NSArray.class]&&[p count]==2&&[p[0] isKindOfClass:NSNumber.class]&&[p[1] isKindOfClass:NSNumber.class]&&CFGetTypeID((__bridge CFTypeRef)p[0])!=CFBooleanGetTypeID()&&CFGetTypeID((__bridge CFTypeRef)p[1])!=CFBooleanGetTypeID()&&TIOMapKitCoordinateValid([p[0] doubleValue],[p[1] doubleValue]);}
static double Rad(double n){return n*M_PI/180;}
static double DeltaLon(double n){return remainder(n,360);}
double TIOMapKitDistance(NSArray *a,NSArray *b){
    if(!PointValid(a)||!PointValid(b))return INFINITY;
    double x=Rad([b[0] doubleValue]-[a[0] doubleValue]),y=Rad(DeltaLon([b[1] doubleValue]-[a[1] doubleValue]));
    double h=pow(sin(x/2),2)+cos(Rad([a[0] doubleValue]))*cos(Rad([b[0] doubleValue]))*pow(sin(y/2),2);
    return 6371000*2*asin(sqrt(fmin(1,fmax(0,h))));
}
double TIOMapKitBearing(NSArray *a,NSArray *b){
    if(!PointValid(a)||!PointValid(b)||TIOMapKitDistance(a,b)<0.1)return -1;
    double p=Rad([a[0] doubleValue]),q=Rad([b[0] doubleValue]),d=Rad(DeltaLon([b[1] doubleValue]-[a[1] doubleValue]));
    return fmod(atan2(sin(d)*cos(q),cos(p)*sin(q)-sin(p)*cos(q)*cos(d))*180/M_PI+360,360);
}
// Selective maneuver taxonomy from sattarde/rayneo-ios-english-edition,
// 6268990646ebd41188cb084bb8ef96d70e94b869 NavigationUI.m; unknown remains unknown.
NSString *TIOMapKitTurn(NSString *instruction,double angle){
    NSString *s=[instruction isKindOfClass:NSString.class]?instruction.lowercaseString:@"";
    NSArray *chinese=@[@[@"到达终点",@"arrive"],@[@"到达目的地",@"arrive"],@[@"抵达",@"arrive"],@[@"进入环岛",@"roundabout"],@[@"驶入环岛",@"roundabout"],@[@"在环岛",@"roundabout"],@[@"掉头",@"uturn"],@[@"调头",@"uturn"],@[@"急左转",@"sharpLeft"],@[@"向左急转",@"sharpLeft"],@[@"急右转",@"sharpRight"],@[@"向右急转",@"sharpRight"],@[@"向左前",@"slightLeft"],@[@"稍向左",@"slightLeft"],@[@"向右前",@"slightRight"],@[@"稍向右",@"slightRight"],@[@"靠左",@"slightLeft"],@[@"靠右",@"slightRight"],@[@"左转",@"left"],@[@"右转",@"right"],@[@"直行",@"straight"]];
    for(NSArray *rule in chinese)if([s containsString:rule[0]])return rule[1];
    NSArray *english=@[
        @[@"\\b(?:you have arrived|arrive(?:d)?(?: at|$)|reach(?:ed)? (?:your|the) destination)\\b",@"arrive"],
        @[@"\\b(?:enter|at|onto|around) (?:the |a )?roundabout\\b",@"roundabout"],
        @[@"\\b(?:make|take|perform) (?:a )?u[ -]?turn\\b|^u[ -]?turn$",@"uturn"],
        @[@"\\bturn (?:sharply|sharp) left\\b|^sharp left(?:$| onto\\b)",@"sharpLeft"],
        @[@"\\bturn (?:sharply|sharp) right\\b|^sharp right(?:$| onto\\b)",@"sharpRight"],
        @[@"\\b(?:bear|keep) left\\b|\\bturn (?:slightly|slight) left\\b|^slight left(?:$| onto\\b)",@"slightLeft"],
        @[@"\\b(?:bear|keep) right\\b|\\bturn (?:slightly|slight) right\\b|^slight right(?:$| onto\\b)",@"slightRight"],
        @[@"\\b(?:turn|head) left\\b|\\b(?:make|take) (?:a )?left (?:turn|onto)\\b",@"left"],
        @[@"\\b(?:turn|head) right\\b|\\b(?:make|take) (?:a )?right (?:turn|onto)\\b",@"right"],
        @[@"\\b(?:continue|go|head|proceed) straight\\b|^straight(?:$| ahead\\b)",@"straight"]];
    for(NSArray *rule in english)if([s rangeOfString:rule[0] options:NSRegularExpressionSearch].location!=NSNotFound)return rule[1];
    if(!isfinite(angle))return @"unknown";
    angle=remainder(angle,360);double a=fabs(angle);
    if(a>=160)return @"uturn";
    if(a>=110)return angle<0?@"sharpLeft":@"sharpRight";
    if(a>=45)return angle<0?@"left":@"right";
    if(a>=18)return angle<0?@"slightLeft":@"slightRight";
    // Small geometry angles do not establish a straight instruction.
    return @"unknown";
}
BOOL TIOMapKitFixValid(double lat,double lon,double accuracy,double timestamp,double now){return TIOMapKitCoordinateValid(lat,lon)&&isfinite(accuracy)&&accuracy>=0&&accuracy<=50&&isfinite(timestamp)&&isfinite(now)&&now-timestamp<=15&&now-timestamp>=-2;}

@implementation TIOMapKitProgress{
    NSArray<NSNumber *> *_cumulative;
    NSUInteger _segment;
}
- (instancetype)initWithPoints:(NSArray *)points{
    if(!(self=[super init]))return nil;
    if(![points isKindOfClass:NSArray.class]||points.count>100000)return nil;
    NSMutableArray *clean=[NSMutableArray new],*cum=[NSMutableArray arrayWithObject:@0];
    for(id p in points){if(!PointValid(p))return nil;if(clean.count){double d=TIOMapKitDistance(clean.lastObject,p);if(d<0.01)continue;_length+=d;[cum addObject:@(_length)];}[clean addObject:[p copy]];}
    if(clean.count<2||!isfinite(_length))return nil;
    _points=[clean copy];_cumulative=[cum copy];return self;
}
- (NSDictionary *)projectLatitude:(double)lat longitude:(double)lon forwardWindow:(double)window{
    if(!TIOMapKitCoordinateValid(lat,lon)||!isfinite(window)||window<0)return nil;
    double best=INFINITY,bestAlong=_progress;NSUInteger bestSegment=_segment;NSArray *bestPoint=nil;
    double lower=fmax(0,_progress-25),upper=fmin(_length,_progress+fmin(window,1000));
    for(NSUInteger i=0;i+1<_points.count;i++){
        double begin=_cumulative[i].doubleValue,end=_cumulative[i+1].doubleValue;if(end<lower)continue;if(begin>upper)break;
        NSArray *a=_points[i],*b=_points[i+1];double scale=fmax(0.0001,cos(Rad(lat)))*111195;
        double ax=DeltaLon([a[1] doubleValue]-lon)*scale,ay=([a[0] doubleValue]-lat)*111195;
        double dx=DeltaLon([b[1] doubleValue]-[a[1] doubleValue])*scale,dy=([b[0] doubleValue]-[a[0] doubleValue])*111195;
        double t=fmax(0,fmin(1,-(ax*dx+ay*dy)/fmax(1e-12,dx*dx+dy*dy)));
        t=fmax(t,fmax(0,(lower-begin)/(end-begin)));t=fmin(t,fmin(1,(upper-begin)/(end-begin)));
        double distance=hypot(ax+t*dx,ay+t*dy),along=begin+t*(end-begin);
        // At a loop crossing prefer the first reachable segment, not a far future lap.
        if(distance<best-1){best=distance;bestAlong=along;bestSegment=i;bestPoint=@[@([a[0] doubleValue]+t*([b[0] doubleValue]-[a[0] doubleValue])),@(remainder([a[1] doubleValue]+t*DeltaLon([b[1] doubleValue]-[a[1] doubleValue]),360))];}
    }
    if(!bestPoint)return nil;
    if(best<=50){_progress=fmax(_progress,bestAlong);_segment=bestSegment;}
    return @{@"distance":@(best),@"progress":@(_progress),@"projected":bestPoint,@"segment":@(bestSegment)};
}
- (NSArray *)coordinateAtDistance:(double)distance{
    if(!isfinite(distance))return nil;distance=fmax(0,fmin(_length,distance));
    NSUInteger low=0,high=_points.count-1;
    while(low+1<high){NSUInteger middle=low+(high-low)/2;if(_cumulative[middle].doubleValue<distance)low=middle;else high=middle;}
    double a=_cumulative[low].doubleValue,b=_cumulative[high].doubleValue,t=(distance-a)/(b-a);NSArray *p=_points[low],*q=_points[high];
    return @[@([p[0] doubleValue]+t*([q[0] doubleValue]-[p[0] doubleValue])),@(remainder([p[1] doubleValue]+t*DeltaLon([q[1] doubleValue]-[p[1] doubleValue]),360))];
}
- (NSArray *)pointsAroundDistance:(double)distance{
    if(!isfinite(distance))return @[];distance=fmax(0,fmin(_length,distance));
    double start=fmax(0,distance-100),end=fmin(_length,distance+450);NSMutableArray *window=[NSMutableArray new];
    for(double d=start;d<end&&window.count<127;d+=5)[window addObject:[self coordinateAtDistance:d]];
    [window addObject:[self coordinateAtDistance:end]];return window;
}
@end
@implementation TIOMapKitRerouteGate{
    NSTimeInterval _first,_lastRequest,_lastObservation;
    NSUInteger _count;
    BOOL _hasRequest;
}
- (void)resetEvidence{_count=0;_first=0;_lastObservation=0;}
- (BOOL)observeDistance:(double)d valid:(BOOL)valid uptime:(double)t{
    if(!valid||!isfinite(d)||!isfinite(t)||t<0||d<=50){[self resetEvidence];return NO;}
    if(_count&&t<=_lastObservation)return NO;
    if(_count&&t-_lastObservation>15)[self resetEvidence];
    if(!_count)_first=t;_lastObservation=t;_count++;
    return _count>=3&&t-_first>=10&&(!_hasRequest||t-_lastRequest>=30);
}
- (void)didRequestAt:(double)t{if(!isfinite(t)||t<0)return;_lastRequest=t;_hasRequest=YES;[self resetEvidence];}
@end
