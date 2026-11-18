#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COUNT=0
while IFS= read -r -d '' manifest; do
  COUNT=$((COUNT + 1))
  if grep -q "android.permission.INTERNET" "$manifest"; then
    echo "[POLICY VIOLATION] Found android.permission.INTERNET in $manifest"
    exit 1
  fi
  if [[ "$manifest" == *"playStore"* ]] && grep -q "android.permission.REQUEST_INSTALL_PACKAGES" "$manifest"; then
    echo "[PLAY STORE VIOLATION] Found REQUEST_INSTALL_PACKAGES in $manifest"
    exit 1
  fi
  echo "[PASS MANIFEST AUDIT] $manifest"
done < <(find "$PROJECT_ROOT/app/build/intermediates/merged_manifest" -name "AndroidManifest.xml" -print0)

if (( COUNT == 0 )); then
  echo "No merged manifests found!"
  exit 1
fi
echo "=== ALL $COUNT MERGED MANIFESTS PASSED ZERO-INTERNET & PLAY POLICY AUDIT ==="
