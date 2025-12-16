#!/usr/bin/env bash
set -euo pipefail

ICON_ROOT="src/main/resources/com/fileexplorer/icons"

if [ ! -d "$ICON_ROOT" ]; then
  echo "ERROR: $ICON_ROOT not found. Run from project root."
  exit 1
fi

SIZES=("16" "32" "48" "64" "128" "256" "512")
TYPES=("file" "folder" "image" "video" "audio")
THEMES=("light" "dark")

echo "Creating Fluent icon directory tree under $ICON_ROOT ..."

for theme in "${THEMES[@]}"; do
  for size in "${SIZES[@]}"; do
    mkdir -p "$ICON_ROOT/$theme/$size"
  done
done

echo "Copying existing icons as placeholders into light/dark packs..."

for theme in "${THEMES[@]}"; do
  for size in "${SIZES[@]}"; do
    for t in "${TYPES[@]}"; do
      src_size_dir="$ICON_ROOT/$size"
      src_root="$ICON_ROOT"

      if [ -f "$src_size_dir/$t.png" ]; then
        cp "$src_size_dir/$t.png" "$ICON_ROOT/$theme/$size/$t.png"
      elif [ -f "$src_root/$t.png" ]; then
        cp "$src_root/$t.png" "$ICON_ROOT/$theme/$size/$t.png"
      else
        echo "WARNING: No source for $t.png at size $size (theme $theme)"
      fi
    done
  done
done

echo "Done. You can now replace icons in:"
echo "  $ICON_ROOT/light/<size>/<type>.png"
echo "  $ICON_ROOT/dark/<size>/<type>.png"
echo "with real Fluent artwork if you like."
