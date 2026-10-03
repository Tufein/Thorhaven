#!/bin/sh
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK 27.2.12479018}"
case "$(uname -s)" in
 Darwin) host=darwin-x86_64 ;;
 Linux) host=linux-x86_64 ;;
 *) echo "Build this helper on macOS or Linux" >&2; exit 1 ;;
esac
base=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
compiler="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$host/bin/aarch64-linux-android30-clang++"
mkdir -p "$base/app/src/main/assets/native/arm64-v8a"
"$compiler" -std=c++17 -O2 -static-libstdc++ -Wall -Wextra "$base/native/thorpad.cpp" -o "$base/app/src/main/assets/native/arm64-v8a/thorpad"
