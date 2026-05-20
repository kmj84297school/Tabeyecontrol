#!/bin/bash
set -e

ANDROID_JAR="/tmp/android-all-34.jar"
KOTLINC="/opt/kotlinc/bin/kotlinc"
DX="/usr/lib/android-sdk/build-tools/debian/dx"
AAPT2="/usr/lib/android-sdk/build-tools/29.0.3/aapt2"
APKSIGNER="/usr/lib/android-sdk/build-tools/29.0.3/apksigner"
ZIPALIGN="/usr/lib/android-sdk/build-tools/29.0.3/zipalign"

SRC="src"
RES="res"
MANIFEST="AndroidManifest.xml"
OUT="build"
PKG="com.example.eyetab"

rm -rf $OUT
mkdir -p $OUT/classes $OUT/res_compiled $OUT/dex $OUT/apk

echo "=== [1/6] Compiling Kotlin ==="
$KOTLINC \
  $SRC/com/example/eyetab/*.kt \
  -classpath "$ANDROID_JAR" \
  -jvm-target 1.8 \
  -include-runtime \
  -d $OUT/classes.jar 2>&1 | grep -v "^w:" | head -30

echo "=== [2/6] Compiling Resources with aapt2 ==="
find $RES -name "*.xml" -o -name "*.png" | while read f; do
  echo "  Compiling: $f"
  $AAPT2 compile "$f" -o $OUT/res_compiled/ 2>&1
done
echo "Flat files: $(ls $OUT/res_compiled/ | wc -l)"

echo "=== [3/6] Linking Resources ==="
FLAT_FILES=$(find $OUT/res_compiled -name "*.flat" | tr '\n' ' ')
$AAPT2 link \
  -o $OUT/resources.apk \
  -I "$ANDROID_JAR" \
  --manifest $MANIFEST \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  $FLAT_FILES 2>&1 | head -20

echo "=== [3.5/6] Stripping multi-release sections ==="
zip -d $OUT/classes.jar "META-INF/versions/*" > /dev/null 2>&1 || true

echo "=== [4/6] Converting to DEX ==="
$DX --dex \
  --min-sdk-version=26 \
  --output=$OUT/dex/classes.dex \
  $OUT/classes.jar 2>&1 | head -20

echo "=== [5/6] Packaging APK ==="
cp $OUT/resources.apk $OUT/apk/eyetab-unsigned.apk
cd $OUT/dex
zip -u ../apk/eyetab-unsigned.apk classes.dex > /dev/null
cd -

echo "=== [6/6] Signing APK ==="
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
  --out $OUT/apk/eyetab-debug.apk \
  $OUT/apk/eyetab-unsigned.apk 2>&1 | head -10

ls -lh $OUT/apk/eyetab-debug.apk
echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $(pwd)/$OUT/apk/eyetab-debug.apk"
