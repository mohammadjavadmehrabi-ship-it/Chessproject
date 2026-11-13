#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/app/src/main"
TEST_DIR="$SCRIPT_DIR/tests"
OUT_APK="${1:-$SCRIPT_DIR/../SnakeGame.apk}"
OUT_AAB="${2:-$SCRIPT_DIR/../SnakeGame.aab}"

TOOLS_ROOT="${ANDROID_TOOLS_ROOT:-/tmp/android-tools}"
JAVA_HOME="$TOOLS_ROOT/venv/lib/python3.11/site-packages/jdk4py/java-runtime"
JAVA="$JAVA_HOME/bin/java"
KEYTOOL="$JAVA_HOME/bin/keytool"
AAPT2="$TOOLS_ROOT/node_modules/aaptjs3/bin/x64/linux/aapt2"
ECJ="$TOOLS_ROOT/sdk/ecj-repo/lib/ecj-4.4.jar"
D8="$TOOLS_ROOT/sdk/d8-repo/d8.jar"
ANDROID_JAR="$TOOLS_ROOT/sdk/android-platforms/android-35/android.jar"
APKSIGNER="$TOOLS_ROOT/node_modules/@postar/apktool-node/lib/apksigner.jar"
BUNDLETOOL="$TOOLS_ROOT/sdk/bundletool-repo/bundletool.jar"
KEYSTORE="$TOOLS_ROOT/snake-release.jks"

BUILD_DIR="/tmp/snake-build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/test_obj" "$BUILD_DIR/dex" "$BUILD_DIR/res_compiled"

echo "==> [1/8] Running JVM unit tests (6 modes, 3 power-ups, engine invariants)..."
"$JAVA" -jar "$ECJ" \
    -encoding UTF-8 \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD_DIR/test_obj" \
    "$APP_DIR/java/com/snakestudio/snake/GameEngine.java" \
    "$APP_DIR/java/com/snakestudio/snake/Strings.java" \
    "$APP_DIR/java/com/snakestudio/snake/ThemePalette.java" \
    "$TEST_DIR/com/snakestudio/snake/GameEngineTest.java"

"$JAVA" -cp "$BUILD_DIR/test_obj" com.snakestudio.snake.GameEngineTest

echo "==> [2/8] Compiling Android resources (API 35) with aapt2..."
"$AAPT2" compile --dir "$APP_DIR/res" -o "$BUILD_DIR/res_compiled/resources.zip"

"$AAPT2" link \
    -o "$BUILD_DIR/res_only.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    "$BUILD_DIR/res_compiled/resources.zip"

"$AAPT2" link \
    --proto-format \
    -o "$BUILD_DIR/res_proto.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --min-sdk-version 21 \
    --target-sdk-version 35 \
    "$BUILD_DIR/res_compiled/resources.zip"

echo "==> [3/8] Compiling Java sources..."
JAVA_SOURCES=$(find "$APP_DIR/java" "$BUILD_DIR/gen" -name '*.java')
"$JAVA" -jar "$ECJ" \
    -encoding UTF-8 \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD_DIR/obj" \
    $JAVA_SOURCES

echo "==> [4/8] Compiling DEX bytecode with D8 (min-api 21, release)..."
CLASS_FILES=$(find "$BUILD_DIR/obj" -name '*.class')
"$JAVA" -cp "$D8" com.android.tools.r8.D8 \
    --release \
    --min-api 21 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex" \
    $CLASS_FILES

echo "==> [5/8] Packaging & 4-byte aligning APK..."
python3 - "$BUILD_DIR/res_only.apk" "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/aligned.apk" <<'PYEOF'
import sys, zipfile

res_apk, dex_path, out_apk = sys.argv[1], sys.argv[2], sys.argv[3]

entries = []
with zipfile.ZipFile(res_apk, 'r') as zin:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename == "resources.arsc" or info.filename.endswith(".png"):
            comp = zipfile.ZIP_STORED
        else:
            comp = info.compress_type
        entries.append((info.filename, data, comp))

with open(dex_path, 'rb') as f:
    entries.append(("classes.dex", f.read(), zipfile.ZIP_DEFLATED))

with zipfile.ZipFile(out_apk, 'w') as zout:
    for name, data, comp in entries:
        zi = zipfile.ZipInfo(name, date_time=(2026, 11, 13, 12, 0, 0))
        zi.compress_type = comp
        if comp == zipfile.ZIP_STORED:
            cur = zout.fp.tell()
            data_offset = cur + 30 + len(name.encode('utf-8'))
            pad = (4 - (data_offset % 4)) % 4
            if pad:
                zi.extra = b'\x00' * pad
        zout.writestr(zi, data, compress_type=comp)
PYEOF

echo "==> [6/8] Signing APK (v1 + v2 + v3) with apksigner..."
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL" -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass snake2026pass \
        -keypass snake2026pass \
        -alias snakestudio \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Snake Studio, OU=Games, O=SnakeStudio, L=Tehran, C=IR" >/dev/null 2>&1
fi

"$JAVA" -jar "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:snake2026pass \
    --key-pass pass:snake2026pass \
    --ks-key-alias snakestudio \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    --out "$OUT_APK" \
    "$BUILD_DIR/aligned.apk"

echo "==> [7/8] Building & signing Google Play Android App Bundle (.aab)..."
python3 - "$BUILD_DIR/res_proto.apk" "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/base.zip" <<'PYEOF'
import sys, zipfile

proto_apk, dex_path, base_zip = sys.argv[1], sys.argv[2], sys.argv[3]

with zipfile.ZipFile(base_zip, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    with zipfile.ZipFile(proto_apk, 'r') as zin:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename == "AndroidManifest.xml":
                zout.writestr("manifest/AndroidManifest.xml", data)
            else:
                zout.writestr(info.filename, data)
    with open(dex_path, 'rb') as f:
        zout.writestr("dex/classes.dex", f.read())
PYEOF

"$JAVA" -jar "$BUNDLETOOL" build-bundle \
    --modules="$BUILD_DIR/base.zip" \
    --output="$BUILD_DIR/unsigned.aab"

"$JAVA" -jar "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:snake2026pass \
    --key-pass pass:snake2026pass \
    --ks-key-alias snakestudio \
    --min-sdk-version 21 \
    --v1-signing-enabled true \
    --v2-signing-enabled false \
    --v3-signing-enabled false \
    --v4-signing-enabled false \
    --out "$OUT_AAB" \
    "$BUILD_DIR/unsigned.aab"

echo "==> [8/8] Verifying final APK and Google Play AAB..."
"$JAVA" -jar "$APKSIGNER" verify --verbose --print-certs "$OUT_APK"
"$AAPT2" dump badging "$OUT_APK"
echo "--- bundletool validate ($OUT_AAB) ---"
"$JAVA" -jar "$BUNDLETOOL" validate --bundle="$OUT_AAB"
echo "--- verifying AAB JAR signature ---"
"$JAVA" -jar "$APKSIGNER" verify --verbose --min-sdk-version 21 --max-sdk-version 23 "$OUT_AAB"
echo "--- bundletool build-apks smoke test ---"
"$JAVA" -jar "$BUNDLETOOL" build-apks --bundle="$OUT_AAB" --output="$BUILD_DIR/test.apks" --mode=universal --overwrite >/dev/null
ls -lh "$OUT_APK" "$OUT_AAB" "$BUILD_DIR/test.apks"
