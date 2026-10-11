#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
@interface TIOMapKitRoads : NSObject
@property(nonatomic,copy) void(^changed)(void);
- (void)updateCoordinate:(CLLocationCoordinate2D)coordinate mode:(NSString *)mode;
- (NSArray *)segments;
- (void)cancel;
@end
