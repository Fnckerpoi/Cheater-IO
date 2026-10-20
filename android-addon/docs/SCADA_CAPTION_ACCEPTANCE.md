# SCADA facts caption implementation and acceptance

Confirmed scope: user starts the existing single-device encrypted ESP32-C3/BMP280 BLE scanner. Authenticated fresh near readings show temperature and ambient pressure as plain facts through AI_SUBTITLE text display; no microphone, cloud transcription or AI request. Keep existing user-started background service lease behavior. RSSI requires on-site calibration; default -70 dBm is not a measurement of one metre.

## Required implementation boundaries

- Preserve authentication, field validation, replay rejection and durable counter commit before presentation. Persist replay high-water marks per key/prefix fingerprint across configuration switches and provisioning clear. Distinguish genuinely first-use credentials from corrupt saved counters; corrupt state must reject scan startup rather than silently reset to -1. Migrate an existing valid high-water conservatively. Test A-to-B-to-A and clear/re-enter same credentials against recorded packets; never reset replay history for reused credentials.
- Propagate ScanResult.getTimestampNanos() in the elapsed-realtime clock domain, not callback delivery time. Reject stale (> or equal to 10,000 ms), future, previous-scan and out-of-order samples when consumed. Check expiry again before actual subtitle transmission.
- Preserve three-packet entry and configured hysteresis. Emit immediate exit when RSSI < entry threshold minus hysteresis; equality remains near. Do not extend freshness with remote, replayed or invalid packets.
- A thin SCADA caption controller owns scan generation, current facts and absolute capture-time expiry. Invalidate local pending text immediately on exit/expiry/scan stop/failure/lease revocation. Schedule expiry against the absolute 10-second deadline rather than only the existing one-second polling loop.
- Shared NavGlasses API requires opaque owner/epoch/SID-bound handles for every producer, including existing navigation/test and CloudCaption paths. Cross-owner start/update/stop/confirmation must fail without modifying the current session. Old callbacks must not revive or release a newer session. Cover the CloudCaption connection-preparation race.
- Continuing fresh SCADA readings must not be silently interrupted by the existing one-hour caption lifetime. Avoid retaining or replaying an old cached reading across stopping/uncertain states.

## Clear semantics

On exit or expiry, immediately stop local submission and request the owning session's type 3 close. The 10-second local freshness deadline means stale facts are no longer accepted or submitted when the app scheduler runs the expiry work; it is not a hard real-time guarantee for Android scheduling and does not guarantee that the lens is physically clear by 10 seconds. SDK submission success is not proof of visible clearing. Existing protocol research does not establish type 3 as a verified device-cleared acknowledgement. Report requested, uncertain and manual confirmation separately; do not invent automatic confirmed state or release a stopping/uncertain session. Device disconnection, deep sleep/main-thread stalls, SDK queued type 5/type 7 frames and ordering against close can affect actual clearing time. Protocol and hardware evidence are required to establish lens-cleared latency.

## Automated acceptance

- Three near packets enter; threshold equality and exit hysteresis boundaries.
- Exit requests clear in the same transition; 9,999 ms stays fresh and 10,000 ms expires.
- Remote, invalid, replayed and queued stale/out-of-order samples cannot renew or revive facts.
- Stop during preparation, configuration, pending text and closing; late ready/SDK/events cannot restart or clear another owner.
- Cross-owner start/offer/stop/confirmation rejected, including cloud preparation and navigation/test paths.
- Formatting and units correct; unchanged values with a new accepted counter renew sample freshness.
- Continued fresh data beyond one hour; no cloud/microphone/model calls from SCADA.

## Local verification snapshot

Final verification command: `android-addon/tests/scadacaption/RunTests.ps1 -JavaBin 'C:/Program Files/JetBrains/WebStorm 2026.2.0.1/jbr/bin'`. Seven focused suites passed: packet codec, replay guard, proximity, durability/time (17 checks), caption/controller (87 checks), NavCore (20 checks), and NavSessionPolicy (15 checks); the runner also verified SensorController timestamp conversion wiring. All 84 Android Java sources compiled with javac against `artifacts/tools/android-sdk/platforms/android-36/android.jar` (Java 8 source/target and deprecated API warnings only). Target-path `git diff --check` passed. A subsequent read-only review identified unresolved blockers after the prior passing tests: uncorrelated type 4 can stop a newer owner; expiry can elapse during SDK message preparation; missing replay high-water can be misclassified as first use; mutually exclusive card/caption fan-out causes failures; old cloud overflow callbacks can close a newer socket. The five issues were subsequently repaired: uncorrelated type 4 is ignored; final SDK submission rechecks identity/ready/expiry after preparation; replay initialization markers and missing legacy counters fail closed; SCADA scanning now has caption-only output; cloud queue accounting and overflow are socket-scoped. The dispatcher independently reran all seven suites (caption 200 checks, durability/time 24 checks), timestamp/caption-only/replay/cloud wiring guards, compiled all 85 Android Java sources, and passed diff checks. These replace the earlier 87/17-check and 84-source results. Final independent patch review is pending; hardware acceptance remains pending. ADB detects one attached Android phone, but no installation or hardware validation was performed.

## Hardware acceptance (pending)

Record phone/Android/app/glasses firmware, calibrated entry and exit RSSI samples around one metre, and correlated monotonic timestamps/counter/RSSI/near/owner/epoch/SID and frame submissions. Film visible temperature/pressure, exit clearing, 10-second silence clearing, reconnection/permission loss and notification Stop. Distinguish clear-request latency from actual lens-cleared latency. Do not claim physical clearing or end-to-end completion without this evidence.
