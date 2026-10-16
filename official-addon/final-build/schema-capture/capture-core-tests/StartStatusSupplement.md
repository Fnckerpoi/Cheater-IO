# Task97 frozen-state terminology clarification

Lead explicitly accepted the existing frozen behavior without changing task93 binder or task97 frozen inputs. Manifest remains SHA256 `9645043e00402fb0b2cc6919fe60fb58b6f767fb7046a9c6d65c421a67c01d1e`.

| Reporting status | Public enum | Internal activation | Observation / file export |
|---|---|---|---|
| `unbound_blocked` | `TIOCaptureBlockedEarlyRuntime` | false | blocked |
| `bind_waiting_start_blocked` | `TIOCaptureWaiting` | false | blocked; original forwarding only |
| `capture_active_waiting_first_open` | `TIOCaptureWaiting` | true, only after validated hidden StartInternal | waiting; no file until an eligible null-key open succeeds |
| `capture_key_rejected` | `TIOCaptureKeyRejected` | activation already occurred | that keyed call performs zero copies and no file export |
| `capture_written` | `TIOCaptureWritten` | terminal one-shot consumed | schema-only atomic file successfully persisted; not official data save |

Thus “binding alone remains blocked” means **operationally blocked from capture/observation**, not that the enum remains `TIOCaptureBlockedEarlyRuntime`. Task93's successful binder still sets `TIOCaptureWaiting`, exactly as previously reviewed. `captureEnabled`/Active remains false until hidden StartInternal succeeds. Bind success is not an authority credential, capture proof, or file-export permission.

The public enum getter alone cannot distinguish `bind_waiting_start_blocked` from `capture_active_waiting_first_open`; report the actual internal activation event separately, and never infer capture activation from WAITING. A getter/button cannot activate the module.

No frozen source, frozen receipt, input manifest, or hash is amended by this supplemental reporting clarification. The historical task93/task97 manifests remain separate immutable evidence; future integration receipts should use the reporting names above rather than conflating bound, active, and written.
