#!/usr/bin/env bash
# Build DSHA Island (LSPosed module) without Gradle.
# The Java toolchain cannot open non-ASCII paths on this host, so everything is
# copied to an ASCII build dir first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="${BUILD_DIR:-/tmp/dsha-island-build}"
SDK="${ANDROID_SDK:-/opt/android-sdk}"
PLATFORM="$SDK/platforms/android-37.0/android.jar"
BUILD_TOOLS="$SDK/build-tools/android-37.0"
AAPT2="${AAPT2:-/opt/termux-tools/usr/bin/aapt2}"
TERMUX_LIB="${TERMUX_LIB:-/opt/termux-tools/usr/lib}"
VERSION="0.1.4"

rm -rf "$BUILD"
mkdir -p "$BUILD/src" "$BUILD/libs" "$BUILD/classes" "$BUILD/dex" "$BUILD/apk"

cp -r "$ROOT/src/." "$BUILD/src/"
cp "$ROOT/AndroidManifest.xml" "$BUILD/AndroidManifest.xml"
cp "$ROOT/libs/libxposed-api-102.jar" "$BUILD/libs/"
cp -r "$ROOT/module/META-INF" "$BUILD/META-INF"

env LD_LIBRARY_PATH="$TERMUX_LIB" "$AAPT2" link \
    -o "$BUILD/apk/base.apk" \
    -I "$PLATFORM" \
    --manifest "$BUILD/AndroidManifest.xml" \
    --min-sdk-version 33 \
    --target-sdk-version 37

find "$BUILD/src" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options \
    -classpath "$PLATFORM:$BUILD/libs/libxposed-api-102.jar" \
    -d "$BUILD/classes" \
    @"$BUILD/sources.txt"

"$BUILD_TOOLS/d8" \
    --lib "$PLATFORM" \
    --lib "$BUILD/libs/libxposed-api-102.jar" \
    --min-api 33 \
    --output "$BUILD/dex" \
    $(find "$BUILD/classes" -name '*.class')

python3 - "$BUILD" <<'PY'
import os, sys, zipfile
build = sys.argv[1]
apk = os.path.join(build, "apk", "base.apk")
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(build, "dex", "classes.dex"), "classes.dex")
    for dirpath, _, files in os.walk(os.path.join(build, "META-INF")):
        for name in files:
            path = os.path.join(dirpath, name)
            z.write(path, os.path.relpath(path, build))
PY

KEYSTORE="$ROOT/keys/dsha-island.jks"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$BUILD/dsha-island.jks" \
        -alias dsha \
        -storepass android -keypass android \
        -dname "CN=DSHA Island" \
        -keyalg RSA -keysize 2048 -validity 10000
    mkdir -p "$ROOT/keys"
    cp "$BUILD/dsha-island.jks" "$KEYSTORE"
else
    cp "$KEYSTORE" "$BUILD/dsha-island.jks"
fi

(
    cd "$BUILD"
    "$BUILD_TOOLS/apksigner" sign \
        --ks dsha-island.jks \
        --ks-pass pass:android \
        --key-pass pass:android \
        --out dsha-island.apk \
        apk/base.apk
)

mkdir -p "$ROOT/out"
cp "$BUILD/dsha-island.apk" "$ROOT/out/dsha-island-$VERSION.apk"
echo "built: $ROOT/out/dsha-island-$VERSION.apk"
sha256sum "$ROOT/out/dsha-island-$VERSION.apk" || true
ls -lh "$ROOT/out/dsha-island-$VERSION.apk"
