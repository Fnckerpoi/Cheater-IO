#ifndef TIO_BOOTSTRAP_FIXED_POLICY_H
#define TIO_BOOTSTRAP_FIXED_POLICY_H
#include <stdint.h>
typedef struct {uint32_t cpu,filetype;unsigned char uuid[16];uint64_t text_va,text_size;unsigned char text_sha[32];uint64_t info_va,info_size;unsigned char info_sha[32];const char*relative_path;uint64_t slot_va,lazy_va;const char*required_dependency;} TIOFixedImagePolicy;
#ifndef TIO_BOOTSTRAP_POLICY_HEADER
#define TIO_BOOTSTRAP_POLICY_HEADER "PolicyDisabled.inc"
#endif
#include TIO_BOOTSTRAP_POLICY_HEADER
#endif
