#import <Foundation/Foundation.h>

BOOL TIOMapKitCoordinateValid(double latitude, double longitude);
double TIOMapKitDistance(NSArray *a, NSArray *b);
double TIOMapKitBearing(NSArray *a, NSArray *b);
NSString *TIOMapKitTurn(NSString *instruction, double geometryDegrees);
BOOL TIOMapKitFixValid(double latitude, double longitude, double accuracy, NSTimeInterval timestamp, NSTimeInterval now);

@interface TIOMapKitProgress : NSObject
@property(nonatomic, readonly) NSArray<NSArray<NSNumber *> *> *points;
@property(nonatomic, readonly) double length;
@property(nonatomic, readonly) double progress;
- (instancetype)initWithPoints:(NSArray *)points;
- (NSDictionary *)projectLatitude:(double)latitude longitude:(double)longitude forwardWindow:(double)window;
- (NSArray *)coordinateAtDistance:(double)distance;
- (NSArray *)pointsAroundDistance:(double)distance;
@end

@interface TIOMapKitRerouteGate : NSObject
- (BOOL)observeDistance:(double)distance valid:(BOOL)valid uptime:(NSTimeInterval)uptime;
- (void)didRequestAt:(NSTimeInterval)uptime;
- (void)resetEvidence;
@end
