#!/usr/bin/env bash
# 手动构建脚本：不依赖 Gradle / Android Studio
set -e
SDK=/home/user/.doubao/agent_mode/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
ANDROID_JAR=$SDK/platforms/android-13/android.jar
PROJ="$(cd "$(dirname "$0")" && pwd)"
OUT=$PROJ/build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/obj" "$OUT/dex"

echo "== 1/7 aapt2 编译资源 =="
"$BT/aapt2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "== 2/7 aapt2 链接 =="
"$BT/aapt2" link \
  -o "$OUT/app.unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

echo "== 3/7 javac =="
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 1.8 -target 1.8 -nowarn \
  -cp "$ANDROID_JAR" -d "$OUT/obj" @"$OUT/sources.txt"

echo "== 4/7 d8 转 dex =="
find "$OUT/obj" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt"

echo "== 5/7 打包 =="
cp "$OUT/app.unsigned.apk" "$OUT/app.apk"
(cd "$OUT/dex" && zip -j -q -u "$OUT/app.apk" classes.dex)

echo "== 6/7 对齐 =="
"$BT/zipalign" -p -f 4 "$OUT/app.apk" "$OUT/app.aligned.apk"

echo "== 7/7 签名 =="
KS=$OUT/debug.keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -alias dbgkey \
    -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Debug,O=BtCodecViewer,C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$PROJ/BtCodecViewer.apk" "$OUT/app.aligned.apk"

"$BT/apksigner" verify --print-certs "$PROJ/BtCodecViewer.apk" | head -2
ls -lh "$PROJ/BtCodecViewer.apk"
echo "BUILD OK"
