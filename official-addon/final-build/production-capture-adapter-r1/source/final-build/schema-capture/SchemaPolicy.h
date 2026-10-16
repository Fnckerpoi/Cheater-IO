#ifndef TIO_CAPTURE_SCHEMA_POLICY_H
#define TIO_CAPTURE_SCHEMA_POLICY_H
#include <stdbool.h>
#include <stddef.h>
bool TIOCaptureValidateSchema(const unsigned char*,size_t);
#endif
/* Owns returned strict canonical schema-only UTF-8 bytes; caller wipes/frees. */
unsigned char *TIOCaptureCanonicalSchema(const unsigned char*,size_t,size_t*);
