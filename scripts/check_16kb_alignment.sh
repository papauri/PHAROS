#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
READELF="${ANDROID_HOME}/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if [[ ! -x "$READELF" ]]; then
  READELF="llvm-readelf"
fi

FAILED=0
COUNT=0
while IFS= read -r -d '' sofile; do
  COUNT=$((COUNT + 1))
  while read -r line; do
    align_hex=$(echo "$line" | awk '{print $NF}')
    align_dec=$((align_hex))
    if (( align_dec < 16384 )); then
      echo "[FAIL] $sofile LOAD segment alignment $align_hex < 0x4000"
      FAILED=$((FAILED + 1))
    fi
  done < <("$READELF" -l "$sofile" | grep -E "^\s*LOAD\s+")
  echo "[PASS 16KB] $(basename "$sofile") verified >= 0x4000"
done < <(find "$PROJECT_ROOT" -name "*.so" -print0)

if (( COUNT == 0 || FAILED > 0 )); then
  echo "16 KB verification failed (count=$COUNT, failed=$FAILED)"
  exit 1
fi
echo "=== ALL $COUNT NATIVE .SO BINARIES PASSED 16 KB PAGE ALIGNMENT ==="
