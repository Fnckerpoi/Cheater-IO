# Fail-closed headless availability verification

This is a scope-specific validation host, not a delivered app or a headless import implementation. It compiles the production `TIONewsHubPanel.m` and `TIONewsSource.m`. `Fixtures.m` supplies only an isolated scheduler and a spy queue with no backend, `suppliedCount=1`, `officialSavedCount=0`, and deliberately misleading legacy `completedCount=1` to catch false-success UI accounting. The test never presses fetching, scheduling, source editing, or real import controls.

## Search regression

```sh
xcrun clang -fobjc-arc -fmodules -Wall -Wextra -Werror -Wno-unused-parameter \
  -framework Foundation official-addon/AIHotDaily.m official-addon/TIONewsSource.m \
  official-addon/TIONewsSearchUnsupportedTests.m -o /tmp/turboio-search-unsupported-review
/tmp/turboio-search-unsupported-review
```

Before correction this exited 42 because Search fabricated a template article. After correction it exits 0, receives a nil article and `TIONewsSourceUnsupported` code 1, and clearly states the real pipeline is not connected. It does not exercise RSS, MCP, or AIHOT network fetching.

## UIKit host

```sh
bash official-addon/news-ui-preview/headless/build.sh /tmp/turboio-headless-ui-review-NEW
device=563E7743-E620-4783-A8D7-6805CE0E51DA
xcrun simctl boot "$device"
xcrun simctl bootstatus "$device" -b
xcrun simctl install "$device" /tmp/turboio-headless-ui-review-NEW/HeadlessUI.app
xcrun simctl launch --console "$device" io.turboio.news-headless-ui-validation \
  > /tmp/turboio-headless-ui-review.log 2>&1 &
console=$!
sleep 7
grep -q HEADLESS_UI_ALL_PASS /tmp/turboio-headless-ui-review.log
! grep -q HEADLESS_UI_RED /tmp/turboio-headless-ui-review.log
xcrun simctl terminate "$device" io.turboio.news-headless-ui-validation
wait "$console"
xcrun simctl shutdown "$device"
```

Use only the dedicated validation simulator, not a phone. The log markers, not `simctl launch`'s exit status, determine the test verdict. The pre-correction Panel emitted `HEADLESS_UI_RED` for calling enqueue and arm when no backend exists. The corrected host passes seven assertions: no enqueue/arm, no navigation/dismiss/manual-import instructions, disabled unavailable button, separated generated/supplied/saved counts, truthful blocker, user-initiated preview, and rendered evidence screenshot.

The screenshot lives in this host's own `Documents/headless-unavailable.png`. Captured RED/GREEN logs and the verified screenshot are in `official-addon/build/news-headless-ui-validation-20261014-1533/`.

## Remaining blockers

The official background save/read contract is not verified, so automatic insertion remains unavailable. Search integration in NewsHub is unsupported; the independent existing TinyFish reader is unchanged. MCP semantics and the full scheduler are outside this change and are not validated by these tests. No new IPA is built by this task.
