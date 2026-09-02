#!/usr/bin/env bash
# Compiles and runs the pure-domain checks with a plain Kotlin compiler.
#
# The Android app itself needs the Android SDK, but com.trio.today.domain has no
# Android dependencies on purpose, so its rules can be verified anywhere kotlinc
# runs. The same rules are also covered by the JUnit tests in app/src/test.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${TMPDIR:-/tmp}/trio-domain-verify"
mkdir -p "$OUT"

kotlinc \
  "$ROOT"/app/src/main/java/com/trio/today/domain/*.kt \
  "$ROOT"/tools/logic-verification/VerifyDomain.kt \
  -include-runtime -d "$OUT/verify.jar"

java -jar "$OUT/verify.jar"
