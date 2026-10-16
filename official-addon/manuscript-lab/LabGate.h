#ifndef TIO_MANUSCRIPT_LAB_GATE_H
#define TIO_MANUSCRIPT_LAB_GATE_H
#include <stdbool.h>
bool TIOManuscriptLabIdentityAllowed(const char *bundle, const char *run, const char *version, const char *build, const char *stage);
bool TIOManuscriptLabPathAllowed(const char *home, const char *candidate);
bool TIOManuscriptLabWritesEnabled(void);
bool TIOManuscriptLabCollectionEnabled(void);
#endif
