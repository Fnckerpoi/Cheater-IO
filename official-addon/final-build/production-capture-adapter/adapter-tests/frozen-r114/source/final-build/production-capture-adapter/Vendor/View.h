#ifndef TIO_CONSTRUCTOR_VIEW_H
#define TIO_CONSTRUCTOR_VIEW_H
#include "Policy.h"
#include <stdbool.h>
#include <stddef.h>
typedef struct {uintptr_t header;intptr_t slide;uint64_t budget,bytes;bool info_verified,slot_metadata_verified;uintptr_t slot,lazy;} TIOConstructorView;
__attribute__((visibility("hidden"))) bool TIOConstructorVerify(TIOConstructorView*,const TIOFixedImagePolicy*);
#endif
