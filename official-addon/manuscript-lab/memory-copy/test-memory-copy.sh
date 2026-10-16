#!/bin/bash
set -euo pipefail
root=$(dirname "${BASH_SOURCE[0]}")
output=$(mktemp -d /tmp/tio-memory-copy-check.XXXXXX)
flags=(-std=c11 -Wall -Wextra -Werror -g)
for mode in normal asan ubsan; do
  sanitize=(-fno-common)
  if [[ $mode == asan ]]; then sanitize=(-fsanitize=address -fno-omit-frame-pointer); fi
  if [[ $mode == ubsan ]]; then sanitize=(-fsanitize=undefined -fno-sanitize-recover=all); fi
  xcrun clang "${flags[@]}" "${sanitize[@]}" "$root/MemoryCopy.c" "$root/MemoryCopyTests.c" -o "$output/$mode-real"
  "$output/$mode-real"
  xcrun clang "${flags[@]}" "${sanitize[@]}" -Dvm_read_overwrite=TIOMemoryCopyFakeMachRead -c "$root/MemoryCopy.c" -o "$output/$mode-fault.o"
  xcrun clang "${flags[@]}" "${sanitize[@]}" "$root/MemoryCopyFaultTests.c" "$output/$mode-fault.o" -o "$output/$mode-fault"
  "$output/$mode-fault"
done
sdk=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang "${flags[@]}" -target arm64-apple-ios15.0 -isysroot "$sdk" -c "$root/MemoryCopy.c" -o "$output/MemoryCopy-ios-arm64.o"
xcrun nm -u "$output/MemoryCopy-ios-arm64.o"
printf "Memory-copy host/sanitizer/arm64 compile PASS; artifacts: %s\n" "$output"
