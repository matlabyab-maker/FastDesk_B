#!/usr/bin/env sh
# Small bootstrap for environments where the Gradle wrapper JAR is not included.
set -eu
GRADLE_VERSION=8.7
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME_DIR/bin/gradle" ]; then
  mkdir -p "$(dirname "$GRADLE_HOME_DIR")"
  ARCHIVE="$(dirname "$GRADLE_HOME_DIR")/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"; else wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$ARCHIVE"; fi
  unzip -q -o "$ARCHIVE" -d "$(dirname "$GRADLE_HOME_DIR")"
fi
exec "$GRADLE_HOME_DIR/bin/gradle" "$@"
