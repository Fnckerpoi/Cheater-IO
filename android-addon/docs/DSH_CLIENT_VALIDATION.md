# Android DSH client validation

Validation date: 2026-10-22 (native event timestamps in UTC). Scope: Java client and Agents-Anywhere / Windows DSH integration; no APK assembly, signing, installation, or glasses test.

## Code and regression verification

The initial implementation passed its original tests but failed independent review. Repairs removed guessed session recovery, retained unknown delivery on ambiguous responses, checked durable pending storage, serialized dispatch admission, bound credentials and request ownership, parsed the actual nested state response, validated encoded selections, and scoped completion to the submitted client message. A second review added negative terminal-event precedence and timeline session validation.

Final independent checks: DshClientTest 65 checks; ChatPolicyTest 18 checks; SpeechBufferTest 14 checks. Full Android Java compilation and D8 conversion (min-api 29) passed; classes.dex size 578296 bytes. git diff --check passed. Java source/target 8, bootstrap-classpath and deprecated-API warnings remain; compilation success is not a claim of warning-free output.

## Real production-client request

A temporary Java harness used the production DshClient and its httpTransport. The existing authenticated credential was supplied only via process stdin, not command-line arguments or a repository file. One dispatch POST was made; subsequent calls were status GETs. A create-once marker and nonsecret request ledger prevented accidental repeated dispatch.

- Server: https://web.agents-anywhere.com
- Connector: conn_7NFcgnwVILr2kg (ZS-LAP-120)
- Project: proj_x1MXpyYarpOFBw (Turbo-IO)
- Runtime: rti_NlmB64Derg8WgEKv
- Model: magpie / group/smart-split
- Server session: sess_hyyUjjKgSejHxA
- Native session: aa_a756527744953245_sess_hyyUjjKgSejHxA
- Client message: msg_de93804b-e5c7-4448-b869-8c01cc45c3b7

Observed client statuses: submitted, unknown (user message not yet synchronized), running, completed. Final answer length: 411 characters; completionEvidence=assistant_done_and_idle. This compatibility evidence is not itself a native turn-end receipt.

## Independent native completion evidence

Native persistent event file: ~/.dsh/sessions/--C-Users-38271-Documents-GitHub-Turbo-IO--/aa_a756527744953245_sess_hyyUjjKgSejHxA/session.v4.jsonl.zstd. Reviewer fully decoded 92198 bytes, 36 records.

- seq 3, 01:41:56.296Z: routing-group model selected.
- seq 4, 01:41:56.297Z: permission preset read-only.
- seq 5, 01:41:56.298Z: sandbox mode read-only.
- seq 8, 01:41:56.998Z: turn/start.
- seq 26, 01:42:12.718Z: sole tool call, pwsh, git status -s -b in Turbo-IO.
- seq 29: tool result isError=false; branch android, two modified and two untracked implementation files.
- seq 34, 01:42:17.710Z: turn/end, turn 1, reason.kind=completed.

Creation defaults included a broader permission before the explicit read-only configuration; read-only was applied before turn/start and no later permission changes were observed. Host PowerShell UTF8Encoding initialization emitted CannotCreateTypeConstrainedLanguage warnings, although the Git command returned successfully.

## Limits and next verification layer

No Android SharedPreferences/Keystore, UI, ASR, subtitle, TTS, APK or device validation was performed by the Java harness. No full before/after file-hash baseline was taken; the event record shows only a Git status command, not an absolute proof that every local file was unchanged.

Unknown delivery deliberately remains locked for manual reconciliation, without automatic recovery or a UI unlock flow. Incomplete timeline pagination returns unknown rather than assuming completion. The AA user token is not a narrowly scoped message-only token. Actual settings require catalog selection IDs, not display names.

The next layer is APK preparation with the verified source revision, then configuration and Android/phone/glasses end-to-end testing. Previously installed APKs do not gain these source changes automatically.
