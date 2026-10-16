#ifndef TIO_PASSIVE_OPEN_OBSERVER_H
#define TIO_PASSIVE_OPEN_OBSERVER_H
#include <stdbool.h>
#include <stdint.h>
typedef struct TIOIsar TIOIsar;
typedef struct TIOCString TIOCString;
typedef uint8_t (*TIOOpenFunction)(TIOIsar **,uint32_t,TIOCString *,TIOCString *,bool,TIOCString *,uint32_t,TIOCString *,uint32_t,uint32_t,float);
typedef struct { TIOCString *schema; uint32_t instance_id; bool sqlite; } TIOOpenObservation;
typedef bool (*TIOOpenCallback)(const TIOOpenObservation *,void *);
typedef uint64_t (*TIOOpenClock)(void *);
typedef void *(*TIOResolverFunction)(void *,const char *);
typedef struct { bool handle_verified,module_verified,export_verified,name_owned,resolver_semantics_verified; } TIOOpenTrust;
bool TIOOpenBind(TIOOpenFunction original);
bool TIOOpenBindObserved(TIOOpenFunction original,TIOOpenCallback callback,TIOOpenClock clock,void *context);
bool TIOOpenArm(bool user_confirmed,uint32_t ttl_seconds,uint32_t event_cap);
void TIOOpenDisable(void);
void TIOOpenContextSetVerified(bool verified);
void *TIOOpenResolve(TIOResolverFunction resolver,void *handle,const char *owned_name,TIOOpenTrust trust);
bool TIOOpenSlotPlan(uintptr_t observed,uintptr_t expected,bool resolved,bool writable,bool aligned);
uint8_t TIOOpenForward(TIOIsar **out,uint32_t id,TIOCString *name,TIOCString *path,bool sqlite,TIOCString *schema,uint32_t max,TIOCString *key,uint32_t compactFile,uint32_t compactBytes,float ratio);
#endif
