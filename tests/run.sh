#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo '[1/3] Validação de cabeçalhos de WAV em Kotlin (JVM)'
JAR="$(mktemp /tmp/console28-wav-XXXXXX.jar)"
trap 'rm -f "$JAR"' EXIT
if command -v kotlinc >/dev/null 2>&1; then
  kotlinc "$ROOT/app/src/main/java/com/jean/console28/audio/WavWriter.kt" \
    "$ROOT/tests/WavSelfTest.kt" -include-runtime -d "$JAR"
  java -jar "$JAR"
else
  echo 'NÃO EXECUTADO: kotlinc indisponível'
fi

echo '[2/3] Testes nativos Rust'
if command -v cargo >/dev/null 2>&1; then
  cargo test --offline --manifest-path "$ROOT/rust/Cargo.toml"
else
  echo 'NÃO EXECUTADO: rustc/cargo não instalados neste ambiente'
fi

echo '[3/3] Validação da ponte JNI em C (headers da JDK)'
JAVA_BIN="$(readlink -f "$(command -v javac)")"
JAVA_HOME_LOCAL="$(dirname "$(dirname "$JAVA_BIN")")"
if command -v clang >/dev/null 2>&1 && [[ -f "$JAVA_HOME_LOCAL/include/jni.h" ]]; then
  clang -std=c11 -fsyntax-only -Wall -Wextra \
    -I"$JAVA_HOME_LOCAL/include" -I"$JAVA_HOME_LOCAL/include/linux" \
    "$ROOT/native/console28_jni.c"
  echo 'PASS C/JNI: sintaxe válida'
else
  echo 'NÃO EXECUTADO: compilador C ou headers JNI ausentes'
fi
