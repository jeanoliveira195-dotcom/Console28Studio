#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
for dep in java javac cargo rustup; do
  if ! command -v "$dep" >/dev/null 2>&1; then
    printf 'Falta %s.\n' "$dep" >&2
    exit 2
  fi
done
if [[ -x "$ROOT/gradlew" ]]; then
  BUILD=("$ROOT/gradlew")
elif command -v gradle >/dev/null 2>&1; then
  BUILD=(gradle)
else
  echo 'Gradle não instalado. Use Android Studio ou GitHub Actions (workflow incluso).' >&2
  exit 2
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f local.properties ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' local.properties | head -1)"
fi
if [[ -z "$SDK" || ! -d "$SDK/platforms/android-36" || ! -d "$SDK/ndk/27.2.12479018" ]]; then
  echo "Android SDK API 36 + NDK 27.2.12479018 não encontrados. Configure ANDROID_HOME e instale-os via Android Studio." >&2
  exit 2
fi
export ANDROID_HOME="$SDK"
echo 'Rodando testes nativos Rust...'
cargo test --offline --manifest-path rust/Cargo.toml
echo 'Compilando APK Android debug...'
"${BUILD[@]}" --no-daemon :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "Falha: APK não gerado em $APK" >&2
  exit 3
fi
printf '\nAPK criado: %s\n' "$APK"
