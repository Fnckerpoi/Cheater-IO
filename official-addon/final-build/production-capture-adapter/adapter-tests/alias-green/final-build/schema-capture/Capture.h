#ifndef TIO_SCHEMA_CAPTURE_H
#define TIO_SCHEMA_CAPTURE_H
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
typedef uint8_t (*TIOCaptureOpen)(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
typedef enum{TIOCaptureBlockedEarlyRuntime,TIOCaptureWaiting,TIOCaptureKeyRejected,TIOCaptureInvalidSchema,TIOCaptureExpired,TIOCaptureExportFailed,TIOCaptureWritten}TIOCaptureState;
TIOCaptureState TIOCaptureGetState(void);
uint8_t TIOCaptureForward(void**,uint32_t,void*,void*,bool,void*,uint32_t,void*,uint32_t,uint32_t,float);
#ifdef TIO_CAPTURE_TESTING
bool TIOCaptureFixtureBind(TIOCaptureOpen,const char *owned_fixture_home,uint64_t (*monotonic_seconds)(void));
unsigned TIOCaptureFixtureCopyCount(void);
#endif
#endif
