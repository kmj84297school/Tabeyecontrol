#!/bin/bash
set -e

ANDROID_JAR="/tmp/android-all-34.jar"
KOTLINC="/opt/kotlinc/bin/kotlinc"
STDLIB="/opt/kotlinc/lib/kotlin-stdlib.jar"
DX="/usr/lib/android-sdk/build-tools/debian/dx"
AAPT2="/usr/lib/android-sdk/build-tools/29.0.3/aapt2"
APKSIGNER="/usr/lib/android-sdk/build-tools/29.0.3/apksigner"

MANIFEST="AndroidManifest.xml"
RES="res"
OUT="build"

rm -rf $OUT
mkdir -p $OUT/res_compiled $OUT/dex $OUT/apk $OUT/gen $OUT/r_classes

echo "=== [1/7] Compiling Resources ==="
find $RES \( -name "*.xml" -o -name "*.png" \) | while read f; do
  echo "  $f"
  $AAPT2 compile "$f" -o $OUT/res_compiled/ 2>&1
done
echo "Flat files: $(ls $OUT/res_compiled/ | wc -l)"

echo "=== [2/7] Linking Resources + R.java ==="
FLAT_FILES=$(find $OUT/res_compiled -name "*.flat" | tr '\n' ' ')
$AAPT2 link \
  -o $OUT/resources.apk \
  -I "$ANDROID_JAR" \
  --manifest $MANIFEST \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --java $OUT/gen \
  $FLAT_FILES 2>&1 | head -10

echo "=== [3/7] Compiling R.java ==="
R_JAVA=$(find $OUT/gen -name "R.java" 2>/dev/null | head -1)
if [ -n "$R_JAVA" ]; then
  javac -source 8 -target 8 -classpath "$ANDROID_JAR" -d $OUT/r_classes/ "$R_JAVA" 2>&1 \
    | grep -v "^\(Note\|warning\)" || true
  jar cf $OUT/r_classes.jar -C $OUT/r_classes .
else
  touch $OUT/r_classes.jar
fi

echo "=== [4/7] Compiling MainActivity.kt only (no reflect) ==="
$KOTLINC \
  src/com/example/eyetab/MainActivity.kt \
  -classpath "$ANDROID_JAR:$OUT/r_classes.jar" \
  -jvm-target 1.8 \
  -d $OUT/classes.jar 2>&1 | grep -v "^w:" | head -20

echo "=== [5/7] Stripping multi-release sections ==="
# Strip from app jar
zip -d $OUT/classes.jar "META-INF/versions/*" > /dev/null 2>&1 || true
# Strip module-info from stdlib (only META-INF/versions/9/module-info.class)
cp "$STDLIB" $OUT/stdlib-stripped.jar
zip -d $OUT/stdlib-stripped.jar "META-INF/versions/*" > /dev/null 2>&1 || true

echo "=== [6/7] Converting to DEX ==="
$DX --dex \
  --min-sdk-version=26 \
  --output=$OUT/dex/classes.dex \
  $OUT/classes.jar \
  $OUT/stdlib-stripped.jar \
  $OUT/r_classes.jar 2>&1 | head -20

echo "=== [7/7] Packaging + Signing ==="
cp $OUT/resources.apk $OUT/apk/eyetab-unsigned.apk
cd $OUT/dex && zip -u ../apk/eyetab-unsigned.apk classes.dex > /dev/null && cd -

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
  $OUT/apk/eyetab-unsigned.apk 2>&1 | head -5

ls -lh $OUT/apk/eyetab-debug.apk
echo ""
echo "=== BUILD SUCCESS ==="
