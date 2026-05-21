#!/bin/bash
# Pure Java Android build — zero Kotlin, zero kotlin-stdlib, zero kotlin-reflect.
set -e

ANDROID_JAR="/tmp/android-all-34.jar"
DX="/usr/lib/android-sdk/build-tools/debian/dx"
AAPT2="/usr/lib/android-sdk/build-tools/29.0.3/aapt2"
APKSIGNER="/usr/lib/android-sdk/build-tools/29.0.3/apksigner"

SRC="app/src/main/java"
MANIFEST="app/src/main/AndroidManifest.xml"
OUT="build-java"

rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/apk

echo "=== [1/4] Linking Manifest (no custom resources) ==="
$AAPT2 link \
  -o $OUT/resources.apk \
  -I "$ANDROID_JAR" \
  --manifest $MANIFEST \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  2>&1 | head -10

echo "=== [2/4] Compiling Java ==="
javac \
  -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d $OUT/classes \
  $SRC/com/example/eyetab/MainActivity.java 2>&1 | grep -v "^\(Note\|warning\)" || true
echo "  Classes: $(find $OUT/classes -name '*.class' | wc -l)"

echo "=== [3/4] Converting to DEX ==="
$DX --dex \
  --min-sdk-version=26 \
  --output=$OUT/dex/classes.dex \
  $OUT/classes 2>&1 | head -10

echo "=== [4/4] Packaging + Signing ==="
cp $OUT/resources.apk $OUT/apk/eyetab-java-unsigned.apk
cd $OUT/dex && zip -u ../apk/eyetab-java-unsigned.apk classes.dex > /dev/null && cd -

if [ ! -f /tmp/debug.keystore ]; then
  keytool -genkeypair \
    -keystore /tmp/debug.keystore \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -3
fi

$APKSIGNER sign \
  --ks /tmp/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out $OUT/apk/eyetab-java-debug.apk \
  $OUT/apk/eyetab-java-unsigned.apk 2>&1 | head -5

ls -lh $OUT/apk/eyetab-java-debug.apk
echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $(pwd)/$OUT/apk/eyetab-java-debug.apk"
