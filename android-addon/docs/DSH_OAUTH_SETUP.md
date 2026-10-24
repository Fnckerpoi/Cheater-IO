# DSH browser authorization and queried selections

## Delivery status

Local implementation and independent review completed. This revision has not been packaged or installed on a phone. The upstream patch has not been pushed, submitted as a PR, or deployed to the official service. Do not describe the official browser login as available until deployment and runtime verification.

## User flow

Open Turbo IO → Settings → DSH / Agents-Anywhere. Enter the HTTPS service origin and choose Login. A supported service opens its external-browser authorization page. After login and explicit consent, a one-time authorization code returns to the dedicated Android callback component. The app exchanges the code using PKCE S256 and verifies the account via auth/me before enabling selection.

Connector, Project, Runtime, Model, and Permission selections are loaded from APIs, displayed using human-readable labels, and saved using returned identifiers. Projects belong to connectors; runtimes also belong to connectors. Runtime selection determines model and permission catalogs, including reasoning selection IDs. Changing a parent clears invalid descendants. Offline, empty, unsupported and failed queries are not filled with invented defaults.

## Frozen authorization contract

- Public client: turboio-android
- Exact redirect: turboio-dsh://oauth/callback
- Capability: GET /api/v2/oauth/clients/turboio-android
- Browser page: /#/turboio-oauth
- Authorization: POST /api/v2/oauth/authorize (logged-in Web user, explicit consent)
- Exchange: POST /api/v2/oauth/token, application/x-www-form-urlencoded
- Account confirmation: GET /api/v2/auth/me

Capability fields: client_id, name, available, authorization_path, redirect_uri, token_endpoint, code_challenge_methods_supported. The Android client verifies fixed values and the chosen origin rather than following arbitrary endpoints supplied by the server.

Callback URLs carry code/state or cancellation error/state, never access tokens. Transactions expire after five minutes, are single-use and bound to origin and local generation. Process death loses the in-memory transaction and requires a fresh login. Refresh-token support is not claimed. Pending or unknown dispatch blocks account/target changes. Storage failures disable dispatch and do not display login success.

## Upstream reviewable artifact

Patch: artifacts/aa-turboio-oauth-20261022-134904/upstream.patch

Patch SHA256: 5e47c4d021c20df5f17ed248641110a6e343a13f95c0877d14902b36b8286a54

Source SHA: 7931ff42885dd4982b3ef156d702d14bfbba8080

Receipt: artifacts/aa-turboio-oauth-20261022-134904/receipt.json

Independent upstream checks passed: policy 3, actual ASGI routes with mock identity/store 6, Web parsing/continuation/consent-component events 8, TypeScript check, git diff --check, patch application check against source baseline.

These tests do not verify PostgreSQL expiry/replay transactions, actual Bearer rejection, or the entire browser-to-Android flow. No official service modification occurred.

## Android checks

Independent final results: Client 65 checks, Auth 17 checks, Directory 23 checks, Manifest 2 tests, HostBuild fixtures; full Java compilation and D8 min-api 29 passed. Final DEX size 605556 bytes. Java 8/bootstrap/deprecated-API warnings remain.

Review fixed placeholder/empty-selection invalidation, delayed adapter callbacks, parent generation changes, and login-continuation query loss. SelectionGate is connected to UI listeners and tested with queued callback simulation; tests do not instantiate actual Android Spinner/Looper.

The dedicated callback activity is added through dsh_manifest.shared_patch, shared by APK assembly and verification. Host callback wrappers and original schemes are not repurposed.

## Deployment and acceptance order

1. Official-service maintainer reviews and applies the upstream patch to a compatible revision; deploy both Server and Web. No deployment is authorized or performed by this delivery.
2. Verify the capability endpoint and browser authorization page with the deployed service.
3. Build and audit a new research APK from this source, verify certificate compatibility with the installed research package, then install through the separately established upgrade workflow.
4. On-device test login, explicit consent, cancellation, invalid/replayed/expired callbacks, process death and return, logout, all queried selections, parent resets, request failures and delayed responses.
5. Test speech dispatch and status with a unique request identity and native DSH completion evidence.

The previously installed APK does not acquire this source revision automatically. Existing AA user tokens still have broad account permissions; empty OAuth scope does not provide a message-only permission boundary.
