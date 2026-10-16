#ifndef TIO_FIXTURE_FLUTTER_H
#define TIO_FIXTURE_FLUTTER_H
#include <stdatomic.h>
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
typedef void *(*FixtureResolver)(void *,const char *);
typedef struct {uintptr_t slot;uintptr_t original;unsigned lookups;bool constructor_ran;uintptr_t competitor;} FixtureFlutterView;
FixtureFlutterView FixtureFlutterViewNow(void);
void *FixtureFlutterLookup(void *handle,const char *name);
void FixtureFlutterCompete(void);
#endif
