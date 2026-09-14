#!/bin/sh
# Builds libScreenCaptureBridge.dylib and refreshes the precompiled fallback copy.
set -e
cd "$(dirname "$0")"
swift build -c release
mkdir -p precompiled
cp .build/release/libScreenCaptureBridge.dylib precompiled/libScreenCaptureBridge.dylib
echo "Built precompiled/libScreenCaptureBridge.dylib"
