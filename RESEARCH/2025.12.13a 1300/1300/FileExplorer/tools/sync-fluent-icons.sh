#!/usr/bin/env bash
set -euo pipefail

BASE="src/main/resources/com/fileexplorer/icons"
SIZES=("16" "32" "48" "64" "128" "256" "512")
TYPES=("folder" "file" "image" "video" "audio")

for mode in light dark; do
  for size in "${SIZES[@]}"; do
    DIR="$BASE/$mode/$size"
    mkdir -p "$DIR"
    for t in "${TYPES[@]}"; do
      # Copy from your master icons (adjust path to wherever you drop them)
      SRC="$BASE/$mode/master/${t}.png"
      if [[ -f "$SRC" ]]; then
        cp "$SRC" "$DIR/${t}.png"
      fi
    done
  done
done

echo "Synced Fluent icon set into $BASE"
