#import <Foundation/Foundation.h>
#include <stdint.h>

typedef struct { double x, y; } TIOMapPoint;
BOOL TIOMapKitNumber(id value, double *number);
BOOL TIOMapKitCoordinate(double latitude, double longitude);
BOOL TIOMapKitWGS84Established(double latitude, double longitude);
BOOL TIOMapKitPoint(id value, double *latitude, double *longitude);
TIOMapPoint TIOMapKitProject(double latitude, double longitude, double originLatitude, double originLongitude, double heading, double scale);
NSArray *TIOMapKitParseRoads(NSData *data, NSString *mode);
NSString *TIOMapKitRoadQuery(double latitude, double longitude, NSString *mode);
NSString *TIOMapKitTurnLabel(id turn);
BOOL TIOMapKitAppendResponse(NSMutableData *buffer, NSData *chunk);
BOOL TIOMapKitAcceptResponse(NSInteger status, long long expectedBytes);

@interface TIOMapKitRoadPolicy : NSObject
@property(nonatomic,readonly) NSUInteger generation;
@property(nonatomic,readonly) NSArray *segments;
- (NSUInteger)beginLatitude:(double)latitude longitude:(double)longitude mode:(NSString *)mode time:(double)time;
- (BOOL)completeGeneration:(NSUInteger)generation segments:(NSArray *)segments time:(double)time;
- (void)cancel;
@end
