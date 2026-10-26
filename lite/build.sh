#!/usr/bin/env bash
# Build DSHA Island Lite (no root, no LSPosed) without Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT="$(cd "$ROOT/.." && pwd)"
BUILD="${BUILD_DIR:-/tmp/dsha-island-lite-build}"
SDK="${ANDROID_SDK:-/opt/android-sdk}"
PLATFORM="$SDK/platforms/android-37.0/android.jar"
BUILD_TOOLS="$SDK/build-tools/android-37.0"
AAPT2="${AAPT2:-/opt/termux-tools/usr/bin/aapt2}"
TERMUX_LIB="${TERMUX_LIB:-/opt/termux-tools/usr/lib}"
VERSION="0.1.2"

rm -rf "$BUILD"
mkdir -p "$BUILD/src" "$BUILD/res" "$BUILD/classes" "$BUILD/dex" "$BUILD/apk" "$BUILD/gen"

cp -r "$ROOT/src/." "$BUILD/src/"
cp -r "$ROOT/res/." "$BUILD/res/"
cp "$ROOT/AndroidManifest.xml" "$BUILD/AndroidManifest.xml"

env LD_LIBRARY_PATH="$TERMUX_LIB" "$AAPT2" compile --dir "$BUILD/res" -o "$BUILD/res.zip"
env LD_LIBRARY_PATH="$TERMUX_LIB" "$AAPT2" link \
    -o "$BUILD/apk/base.apk" \
    -I "$PLATFORM" \
    --manifest "$BUILD/AndroidManifest.xml" \
    -R "$BUILD/res.zip" \
    --java "$BUILD/gen" \
    --min-sdk-version 33 \
    --target-sdk-version 37

find "$BUILD/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options \
    -classpath "$PLATFORM" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

"$BUILD_TOOLS/d8" \
    --lib "$PLATFORM" \
    --min-api 33 \
    --output "$BUILD/dex" \
    $(find "$BUILD/classes" -name '*.class')

python3 - "$BUILD" <<'PY'
import os, sys, zipfile
build = sys.argv[1]
apk = os.path.join(build, "apk", "base.apk")
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(build, "dex", "classes.dex"), "classes.dex")
PY

SHARED_KEY="$PROJECT/keys/dsha-island.jks"
if [ -f "$SHARED_KEY" ]; then
    cp "$SHARED_KEY" "$BUILD/lite.jks"
else
    keytool -genkeypair \
        -keystore "$BUILD/lite.jks" \
        -alias lite \
        -storepass android -keypass android \
        -dname "CN=DSHA Island Lite" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

(
    cd "$BUILD"
    "$BUILD_TOOLS/apksigner" sign \
        --ks lite.jks \
        --ks-pass pass:android \
        --key-pass pass:android \
        --out dsha-island-lite.apk \
        apk/base.apk
)

mkdir -p "$PROJECT/out"
cp "$BUILD/dsha-island-lite.apk" "$PROJECT/out/dsha-island-lite-$VERSION.apk"
echo "built: $PROJECT/out/dsha-island-lite-$VERSION.apk"
sha256sum "$PROJECT/out/dsha-island-lite-$VERSION.apk" || true
ls -lh "$PROJECT/out/dsha-island-lite-$VERSION.apk"
