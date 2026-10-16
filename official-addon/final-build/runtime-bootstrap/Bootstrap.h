#ifndef TIO_RUNTIME_BOOTSTRAP_H
#define TIO_RUNTIME_BOOTSTRAP_H
#include <stdbool.h>
#include <stdint.h>
typedef enum { TIOBootstrapUninitialized,TIOBootstrapBlockedIdentity,TIOBootstrapBlockedEarlyLookup,TIOBootstrapBlockedSlot,TIOBootstrapRaceLost,TIOBootstrapFixtureInstalled } TIOBootstrapState;
typedef struct {TIOBootstrapState state;bool constructor_ran,add_image_registered,runner_identity_verified,bundle_identity_verified,slot_installed,event_observed;uint32_t image_callbacks;} TIOBootstrapStatus;
TIOBootstrapStatus TIOBootstrapGetStatus(void);
#ifdef TIO_BOOTSTRAP_TESTING
bool TIOBootstrapFixtureConstructorSameThread(void);
#endif
#endif
