#!/usr/bin/env sh

# Lightweight wrapper bootstrap for this generated source bundle. Android Studio
# can regenerate the standard wrapper files after the first successful sync.
set -eu

GRADLE_VERSION=8.11.1
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_DIR="$PROJECT_DIR/.gradle-dist/gradle-$GRADLE_VERSION"
ARCHIVE="$PROJECT_DIR/.gradle-dist/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$PROJECT_DIR/.gradle-dist"
  if [ ! -f "$ARCHIVE" ]; then
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ARCHIVE" "$URL"
    else
      echo "curl or wget is required to download Gradle." >&2
      exit 1
    fi
  fi
  unzip -q -o "$ARCHIVE" -d "$PROJECT_DIR/.gradle-dist"
fi

exec "$DIST_DIR/bin/gradle" "$@"
