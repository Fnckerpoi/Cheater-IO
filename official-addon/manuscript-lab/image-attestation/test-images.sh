#!/bin/bash
set -euo pipefail
root=$(python3 -c "import os,sys;print(os.path.realpath(sys.argv[1]))" "$(dirname "${BASH_SOURCE[0]}")")
# Override TIO_TEST_ORIGINAL_IPA to run the historical 1.0.5 input explicitly.
ipa=${TIO_TEST_ORIGINAL_IPA:-"$root/../../.build/ios-inputs/RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa"}
out=$(mktemp -d /tmp/tio-image-check.XXXXXX)
out=$(python3 -c "import os,sys;print(os.path.realpath(sys.argv[1]))" "$out")
mkdir -p "$out/Fixture.app/Frameworks"
app="$out/Fixture.app"
printf "int tio_public_fixture(int x) { return x*7+3; }\n" > "$out/fixture.c"
xcrun clang -dynamiclib "$out/fixture.c" -o "$app/Frameworks/Public.dylib"
python3 - "$out" "$ipa" <<'PY'
import pathlib,sys,zipfile,hashlib
out=pathlib.Path(sys.argv[1]);p=pathlib.Path(sys.argv[2])
if p.is_symlink(): raise SystemExit(f"refusing symlink IPA input: {p}")
expected_sha={
 "RayNeo_AI_1.0.6_218_decrypted_unsigned_original.ipa":"f20d23c2e90c9f6effb73463491725d2a359af14b1afa1bc06cf68f61bf2a852",
 # Historical 1.0.5 input remains available at official-addon/build/manuscript-lab-input-20261015-original105/.
 "RayNeo_AI_1.0.5_201_decrypted_original.ipa":"39d12aff8f2b312dc3b40ce784a444e52c536e61378434005255177548df966a",
}.get(p.name)
if expected_sha is None: raise SystemExit(f"unsupported IPA input name: {p.name}")
h=hashlib.sha256()
with p.open("rb") as f:
 for b in iter(lambda:f.read(1048576),b""):h.update(b)
actual_sha=h.hexdigest()
if actual_sha!=expected_sha: raise SystemExit(f"unexpected IPA SHA-256 for {p.name}: {actual_sha}")
with zipfile.ZipFile(p) as z:
 for name in ["Flutter","isar_plus_flutter_libs"]:
  b=z.read(f"Payload/Runner.app/Frameworks/{name}.framework/{name}");(out/name).write_bytes(b)
PY
flags=(-std=c11 -Wall -Wextra -Werror -g -DTIO_IMAGE_ATTESTATION_TESTING)
for mode in normal asan ubsan; do
  sanitize=(-fno-common)
  if [[ $mode == asan ]]; then sanitize=(-fsanitize=address -fno-omit-frame-pointer); fi
  if [[ $mode == ubsan ]]; then sanitize=(-fsanitize=undefined -fno-sanitize-recover=all); fi
  xcrun clang "${flags[@]}" "${sanitize[@]}" "$root/ImageAttestation.c" "$root/../memory-copy/MemoryCopy.c" "$root/ExpandedTests.c" -o "$out/$mode"
  "$out/$mode" "$app/Frameworks/Public.dylib" "$app" "$out/Flutter" "$out/isar_plus_flutter_libs"
done
sdk=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -std=c11 -Wall -Wextra -Werror -target arm64-apple-ios15.0 -isysroot "$sdk" -c "$root/ImageAttestation.c" -o "$out/ImageAttestation-arm64.o"
if xcrun nm -g "$out/ImageAttestation-arm64.o" | grep TIOImageTest; then echo "testpolicy leaked"; exit 1; fi
xcrun nm -u "$out/ImageAttestation-arm64.o"
printf "image-attestation checks PASS artifacts=%s\n" "$out"
