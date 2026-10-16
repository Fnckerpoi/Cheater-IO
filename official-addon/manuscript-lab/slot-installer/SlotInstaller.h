#ifndef TIO_SLOT_INSTALLER_H
#define TIO_SLOT_INSTALLER_H
#include <stdbool.h>
#include <stdint.h>
typedef enum { TIOSlotBlockedNotProven, TIOSlotReadOnly, TIOSlotWrongValue, TIOSlotRaceLost, TIOSlotAlreadyAttempted, TIOSlotInstalled, TIOSlotIdentityChanged, TIOSlotUnsupportedLifetime } TIOSlotStatus;
typedef struct { TIOSlotStatus status; bool module_linked,slot_can_install,slot_installed,event_observed,schema_validated; } TIOSlotResult;
/* No proof/bool/caller expected-pointer parameter. Current deployment is not
 * authorized; internally verifies fixed policy twice but never writes a live slot. */
TIOSlotResult TIOSlotInstallerInspect(const void *flutter_header,const char *canonical_app_root);
#ifdef TIO_SLOT_TESTING
typedef enum { TIOFixtureValid,TIOFixtureReadOnly,TIOFixtureUnaligned,TIOFixtureForeignValue,TIOFixtureRace,TIOFixtureTextTampered,TIOFixtureUUIDDuplicate,TIOFixtureSlideOverflow,TIOFixtureUnreadable,TIOFixtureSignatureOnly,TIOFixtureUnmapped,TIOFixtureForeignSlot } TIOSlotFixtureKind;
/* Constructor owns the mmap+policy and lifetime. Not a production capability. */
typedef struct TIOSlotFixture TIOSlotFixture;
TIOSlotFixture *TIOSlotFixtureCreate(TIOSlotFixtureKind);
TIOSlotResult TIOSlotFixtureAttempt(TIOSlotFixture *);
bool TIOSlotFixtureValueMatchesOriginal(TIOSlotFixture *);
bool TIOSlotFixtureValueMatchesCompetitor(TIOSlotFixture *);
bool TIOSlotFixtureCachedForwardStillValid(TIOSlotFixture *);
#endif
#endif
