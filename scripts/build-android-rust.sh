#!/usr/bin/env bash
# Compila o motor Rust para Android e une a ponte C/JNI em uma única libconsole28_jni.so.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/app/build/generated/rustJniLibs}"

if ! command -v cargo >/dev/null 2>&1 || ! command -v rustup >/dev/null 2>&1; then
  echo 'ERRO: instale Rust/Cargo usando rustup (https://rustup.rs/).' >&2; exit 2
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f "$ROOT/local.properties" ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -1 | sed 's/\\:/:/g;s/\\\\/\\/g')"
fi
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${SDK:+$SDK/ndk/27.2.12479018}}}"
if [[ ! -d "$NDK/toolchains/llvm/prebuilt" ]]; then
  echo "ERRO: Android NDK 27.2.12479018 não encontrado ($NDK)." >&2
  echo "Instale: sdkmanager 'ndk;27.2.12479018'" >&2; exit 3
fi
case "$(uname -s)" in
  Linux) HOST='linux-x86_64' ;;
  Darwin) HOST="darwin-$(uname -m)" ;;
  *) echo 'Use Linux/macOS/WSL ou workflow do GitHub Actions.' >&2; exit 4;;
esac
TOOLBIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
JNICALL="$NDK/toolchains/llvm/prebuilt/$HOST/sysroot/usr/include"
if [[ ! -f "$JNICALL/jni.h" ]]; then
  echo "ERRO: jni.h ausente em $JNICALL" >&2; exit 5
fi

for spec in 'aarch64-linux-android arm64-v8a aarch64-linux-android26-clang' \
            'x86_64-linux-android x86_64 x86_64-linux-android26-clang'; do
  read -r TARGET ABI CLANG <<< "$spec"
  CC="$TOOLBIN/$CLANG"
  if [[ ! -x "$CC" ]]; then echo "ERRO: compilador ausente: $CC" >&2; exit 6; fi
  rustup target add "$TARGET" >/dev/null
  echo "[Console 28] Compilando Rust para $ABI"
  env CARGO_BUILD_TARGET="$TARGET" RUSTFLAGS="-C linker=$CC" \
    cargo build --offline --manifest-path "$ROOT/rust/Cargo.toml" \
      --release --target "$TARGET"
  mkdir -p "$OUT/$ABI"
  "$CC" -std=c11 -O3 -fPIC -shared -Wall -Wextra \
    -I"$JNICALL" "$ROOT/native/console28_jni.c" \
    "$ROOT/rust/target/$TARGET/release/libconsole28_dsp.a" \
    -Wl,--no-undefined -Wl,--exclude-libs,ALL -Wl,-z,relro -Wl,-z,now \
    -Wl,-z,max-page-size=16384 \
    -ldl -llog -lm -lunwind -o "$OUT/$ABI/libconsole28_jni.so"
  echo "[Console 28] Gerado: $OUT/$ABI/libconsole28_jni.so"
done
