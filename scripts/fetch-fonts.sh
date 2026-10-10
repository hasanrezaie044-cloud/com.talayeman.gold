#!/usr/bin/env bash
# Downloads the Vazirmatn Persian font (SIL OFL licence) into app/src/main/assets/fonts.
# The app uses it automatically when present and falls back to the system font otherwise,
# so a failed download never breaks the build.
set -u
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/fonts"
BASE="https://github.com/rastikerdar/vazirmatn/raw/v33.003/fonts/ttf"
mkdir -p "$DEST"
for w in Regular Medium Bold; do
  if [ ! -s "$DEST/Vazirmatn-$w.ttf" ]; then
    echo "Downloading Vazirmatn-$w.ttf"
    if ! curl -fsSL --retry 2 "$BASE/Vazirmatn-$w.ttf" -o "$DEST/Vazirmatn-$w.ttf"; then
      rm -f "$DEST/Vazirmatn-$w.ttf"
      echo "WARNING: could not download Vazirmatn-$w.ttf (the app will use the system font)"
    fi
  fi
done
ls -la "$DEST" || true
exit 0
