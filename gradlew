#!/bin/sh
set -eu

GRADLE_VERSION=8.8
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOOT_DIR="$BASE_DIR/.gradle-wrapper"
DIST_DIR="$BOOT_DIR/gradle-$GRADLE_VERSION"
ZIP="$BOOT_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
    mkdir -p "$BOOT_DIR"
    echo "Gradle $GRADLE_VERSION is not cached; downloading it..."

    if command -v curl >/dev/null 2>&1; then
        curl -fL "$URL" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP" "$URL"
    else
        echo "Error: curl or wget is required for the first Gradle bootstrap." >&2
        exit 1
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "Error: unzip is required for the first Gradle bootstrap." >&2
        exit 1
    fi

    unzip -q -o "$ZIP" -d "$BOOT_DIR"
    rm -f "$ZIP"
fi

exec "$DIST_DIR/bin/gradle" "$@"
