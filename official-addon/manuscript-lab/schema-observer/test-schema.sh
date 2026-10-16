#!/bin/bash
set -eu
ROOT=$(dirname "$0")
cd "$ROOT"
mkdir -p build
clang -std=c11 -Wall -Wextra -Werror SchemaView.c SchemaViewTests.c -o build/schema-c-test
build/schema-c-test
clang -fobjc-arc -Wall -Wextra -Werror -Wno-incompatible-pointer-types -framework Foundation SchemaMetadata.m SchemaMetadataTests.m -o build/schema-objc-test
build/schema-objc-test
clang -std=c11 -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer SchemaView.c SchemaViewTests.c -o build/schema-c-asan
ASAN_OPTIONS=detect_leaks=0 build/schema-c-asan
clang -fobjc-arc -Wall -Wextra -Werror -Wno-incompatible-pointer-types -fsanitize=address,undefined -fno-omit-frame-pointer -framework Foundation SchemaMetadata.m SchemaMetadataTests.m -o build/schema-objc-asan
ASAN_OPTIONS=detect_leaks=0 build/schema-objc-asan
SDK=$(xcrun --sdk iphoneos --show-sdk-path)
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -std=c11 -Wall -Wextra -Werror -c SchemaView.c -o build/SchemaView-arm64.o
xcrun --sdk iphoneos clang -target arm64-apple-ios15.0 -isysroot "$SDK" -fobjc-arc -Wall -Wextra -Werror -c SchemaMetadata.m -o build/SchemaMetadata-arm64.o
printf 'SCHEMA_OBSERVER_ARM64_COMPILE_PASS\n'
clang -fobjc-arc -Wall -Wextra -Werror -framework Foundation SchemaMetadata.m SchemaProjectionTests.m -o build/schema-projection-test
build/schema-projection-test
clang -fobjc-arc -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer -framework Foundation SchemaMetadata.m SchemaProjectionTests.m -o build/schema-projection-asan
ASAN_OPTIONS=detect_leaks=0 build/schema-projection-asan
