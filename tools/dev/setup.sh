#!/usr/bin/env bash
# Install a verified project-local JDK 25, then print IDE/environment instructions.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$repo_root"

for dependency in python3 curl tar; do
    command -v "$dependency" >/dev/null || { echo "Missing dependency: $dependency" >&2; exit 1; }
done
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else "Python 3.11+ is required")'

source tools/dev/env.sh
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
if command -v "$java_cmd" >/dev/null && command -v "$javac_cmd" >/dev/null \
    && "$java_cmd" -version 2>&1 | head -1 | grep -Eq 'version "25[.\"]' \
    && "$javac_cmd" -version 2>&1 | grep -Eq '^javac 25([.]|$)'; then
    echo "JDK 25 is already available."
else
    [[ "$(uname -s)" == Linux ]] || { echo "Install a JDK 25 for your OS and set JAVA_HOME first." >&2; exit 1; }
    case "$(uname -m)" in
        x86_64) jdk_arch=x64 ;;
        aarch64|arm64) jdk_arch=aarch64 ;;
        *) echo "Unsupported JDK architecture: $(uname -m)" >&2; exit 1 ;;
    esac
    mkdir -p .toolchains
    if [[ -e .toolchains/jdk-25 ]]; then
        echo "Existing .toolchains/jdk-25 is invalid; move it aside before retrying." >&2
        exit 1
    fi
    setup_tmp="$(mktemp -d "$repo_root/.toolchains/setup.XXXXXX")"
    trap 'rm -rf -- "$setup_tmp"' EXIT
    curl --fail --location --retry 2 --connect-timeout 20 --max-time 120 \
        "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=$jdk_arch&image_type=jdk&os=linux" \
        --output "$setup_tmp/release.json"
    python3 - "$setup_tmp/release.json" "$setup_tmp" <<'PY'
import json, sys
from pathlib import Path
release = json.loads(Path(sys.argv[1]).read_text())[0]
if release['version']['major'] != 25:
    raise SystemExit('Unexpected JDK major version')
package = release['binary']['package']
if not package['link'].startswith('https://github.com/adoptium/temurin25-binaries/'):
    raise SystemExit('Unexpected JDK download source')
destination = Path(sys.argv[2])
(destination / 'url').write_text(package['link'])
(destination / 'sha256').write_text(package['checksum'])
print('Installing Temurin ' + release['version']['openjdk_version'])
PY
    curl --fail --location --retry 2 --connect-timeout 20 --max-time 900 \
        "$(cat "$setup_tmp/url")" --output "$setup_tmp/jdk.tar.gz"
    python3 - "$setup_tmp" <<'PY'
import hashlib, sys
from pathlib import Path
root = Path(sys.argv[1])
with (root / 'jdk.tar.gz').open('rb') as stream:
    actual = hashlib.file_digest(stream, 'sha256').hexdigest()
if actual != (root / 'sha256').read_text().strip():
    raise SystemExit('JDK checksum mismatch')
print('JDK SHA-256 verified')
PY
    mkdir "$setup_tmp/jdk"
    tar -xzf "$setup_tmp/jdk.tar.gz" --strip-components=1 -C "$setup_tmp/jdk"
    "$setup_tmp/jdk/bin/java" -version
    "$setup_tmp/jdk/bin/javac" -version
    mv -- "$setup_tmp/jdk" .toolchains/jdk-25
fi

echo "In this shell, run: source tools/dev/env.sh"
echo "Then run: ./gradlew --version && ./gradlew build && ./gradlew genSources"
echo "IDE: choose JDK 25 for both Project SDK and Gradle JVM; use the Gradle wrapper."
