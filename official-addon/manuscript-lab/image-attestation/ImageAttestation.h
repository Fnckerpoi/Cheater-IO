#ifndef TIO_IMAGE_ATTESTATION_H
#define TIO_IMAGE_ATTESTATION_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
typedef bool (*TIOImageCopy)(void *, uintptr_t, size_t, void *, size_t *);
typedef enum { TIOImageFlutter=1, TIOImageIsar=2 } TIOImageRole;
typedef enum { TIOImageOK=0, TIOImageBadArgument, TIOImageBudgetExceeded, TIOImageCopyFailed, TIOImageMalformed, TIOImageIdentityMismatch, TIOImagePathRejected, TIOImageDuplicate, TIOImageNotLoaded, TIOImageUnsupported } TIOImageStatus;
typedef struct { uint64_t bytes_remaining; unsigned images_remaining; } TIOImageBudget;
typedef struct {
    bool content_verified;
    bool loaded_identity_verified;
    bool slot_metadata_verified;
    bool slot_runtime_writable_verified;
    uintptr_t slot_address;
    uintptr_t open_entry;
    uint64_t bytes_read;
} TIOImageProof;
void TIOImageBudgetInit(TIOImageBudget *);
/* Nominee must be an actual current dyld header. Root must be an existing
 * canonical absolute .app directory. No caller attestation booleans accepted.
 * Copy is a trusted transport, not an identity/policy provider. No GOT reads. */
TIOImageStatus TIOImageAttestLoaded(TIOImageRole, const void *nominated_header,
    const char *canonical_app_root, TIOImageCopy, void *, TIOImageBudget *, TIOImageProof *);
/* Static member bytes verify provenance/content only, never loaded capability. */
TIOImageStatus TIOImageAttestStatic(TIOImageRole, const void *bytes, size_t length,
    TIOImageBudget *, TIOImageProof *);
#ifdef TIO_IMAGE_ATTESTATION_TESTING
typedef struct { uint32_t cpu; unsigned char uuid[16]; uint64_t text_va,text_size; unsigned char text_sha[32]; uint64_t entry_va; unsigned char entry_sha[32]; const char *relative_path; } TIOImageTestPolicy;
TIOImageStatus TIOImageTestView(const TIOImageTestPolicy *,uintptr_t header,intptr_t slide,
    const char *relative_path,unsigned matching_images,TIOImageCopy,void *,TIOImageBudget *,TIOImageProof *);
TIOImageStatus TIOImageTestLoaded(const TIOImageTestPolicy *,const void *,const char *,TIOImageCopy,void *,TIOImageBudget *,TIOImageProof *);
#endif
#endif
