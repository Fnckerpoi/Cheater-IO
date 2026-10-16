#ifndef TIO_SCHEMA_CAPTURE_INTERNAL_H
#define TIO_SCHEMA_CAPTURE_INTERNAL_H
#include "Capture.h"
/* Same-final-dylib adapter only; not an external dynamic ABI or capability grant.
 * Caller must already verify original equals the loaded qualified Isar entry and
 * the exact RTLD_DEFAULT (-2) lookup-name result. This function verifies neither.
 * NULL and every rebind (including the same pointer) fail. No capture/save proof. */
__attribute__((visibility("hidden"))) bool TIOCaptureBindOriginalInternal(TIOCaptureOpen original);
/* Caller must obtain this exact 0700 directory fd by no-follow traversal rooted
 * in its verified app Documents/ManuscriptLab. An fd is not a container proof.
 * FD_CLOEXEC/type/euid/0700 and reopened dev/ino are validated here; Darwin
 * cannot recover an fd's historical symlink traversal. Caller owns that proof.
 * Same-DSO bootstrap only: bound original required; one start; no path override,
 * no activation on load, no rearm. Success is WAITING, never capture/save proof. */
__attribute__((visibility("hidden"))) bool TIOCaptureStartInternal(int trusted_dirfd);
#endif
