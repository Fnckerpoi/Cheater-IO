#import <Foundation/Foundation.h>
#import "../open-observer/OpenObserver.h"
#import "../schema-observer/SchemaView.h"

typedef NS_ENUM(NSUInteger, TIOStageBState) {
    TIOStageBBlockedRuntimeEntry, TIOStageBTestArmed, TIOStageBTerminal
};
/* Production construction only binds an immutable forwarding target. There is
 * deliberately no production capability factory, runtime arm, hook or export. */
@interface TIOStageBController : NSObject
- (instancetype)initWithOriginal:(TIOOpenFunction)original;
- (BOOL)arm;
- (TIOStageBState)state;
- (NSDictionary *)capturedProjection;
- (uint8_t)forwardOut:(TIOIsar **)out identifier:(uint32_t)identifier
    name:(TIOCString *)name path:(TIOCString *)path sqlite:(bool)sqlite
    schema:(TIOCString *)schema maxSize:(uint32_t)maxSize key:(TIOCString *)key
    compactFile:(uint32_t)compactFile compactBytes:(uint32_t)compactBytes ratio:(float)ratio;
@end

#if defined(TIO_STAGE_B_TESTING)
/* Host-only fixtures, not runtime identity proof. Not compiled into production. */
/* Host clock must be bounded and non-reentrant into this controller. Exceptions
 * are contained and terminate the one-shot activation; blocking is not timed out. */
typedef uint64_t (*TIOStageBTestClock)(void *context);
@interface TIOStageBController (HostFixtures)
- (BOOL)testGrantForCurrentThreadWithRole:(BOOL)role lifetime:(BOOL)lifetime
    clock:(TIOStageBTestClock)clock clockContext:(void *)clockContext
    copy:(TIOSchemaBoundedCopy)copy copyContext:(void *)copyContext;
- (BOOL)testOwnedCopyReleased;
@end
#endif

FOUNDATION_EXPORT NSDictionary *TIOStageBProductionStatus(void);
