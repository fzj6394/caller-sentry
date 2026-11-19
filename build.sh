#!/bin/bash
# 手工编译（不用 Gradle）：aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
set -e

# 路径自动推导：脚本所在的上一级就是工程根目录（Windows 下 pwd -W 给出 E:/... 形式）
APP="$(cd "$(dirname "$0")" && { pwd -W 2>/dev/null || pwd; })"
ROOT="$(dirname "$APP")"

# 工具链路径可以覆盖：适合工具链装在别处的情况
ENV="${SENTRY_ENV:-$ROOT/build-env}"
BT="${ANDROID_BT:-$ENV/sdk/build-tools/34.0.0}"
PLATFORM="${ANDROID_PLATFORM:-$ENV/sdk/platforms/android-34/android.jar}"
JAVA_BIN="${SENTRY_JDK:-$ENV/jdk/jdk-17.0.21+9}/bin"

# apksigner.bat 依赖 JAVA_HOME；PATH 里要放 unix 风格的 bin
JAVA_HOME_WIN="$(dirname "$JAVA_BIN")"
export JAVA_HOME="$JAVA_HOME_WIN"
JAVA_UNIX="$(cd "$JAVA_BIN" && pwd)"
BT_UNIX="$(cd "$BT" && pwd)"
export PATH="$JAVA_UNIX:$BT_UNIX:$PATH"

cd "$APP"
OUT="build"
rm -f "$OUT"/*.apk "$OUT"/*.zip "$OUT"/*.txt "$OUT"/*.log
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
rm -f "$OUT/gen"/cn/fzj/callersentry/R.java "$OUT/classes"/cn/fzj/callersentry/*.class 2>/dev/null || true
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/test"

echo "== 1/7 aapt2 compile =="
"$BT/aapt2.exe" compile --dir res -o "$OUT/res.zip"

echo "== 2/7 aapt2 link =="
"$BT/aapt2.exe" link -o "$OUT/base.apk" \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    -A assets \
    --auto-add-overlay

echo "== 3/7 javac =="
find src "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
set +e
"$JAVA_BIN/javac.exe" -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM" \
    -d "$OUT/classes" "@$OUT/sources.txt" > "$OUT/javac.log" 2>&1
JRC=$?
set -e
if [ $JRC -ne 0 ]; then
    echo "!! javac 失败（退出码 $JRC）："
    tr -d '\000' < "$OUT/javac.log" | grep -E "error|错误" | head -10
    exit 1
fi

# 逐个点名核对，宁可构建失败也不要出个缺类的包
CNT=0
CNT=0
for cls in App MainActivity ScreeningService PhoneLookup Update MarkCache OnlineLookup \
           Yellow YellowStore Ui UiTheme BasePageActivity RulesActivity SourcesActivity \
           AppearanceActivity RecordsActivity CallerIdProvider Notify Blacklist LogStore Timing Overlay NumUtil Prefs \
           Trace Watcher Boot; do
    CNT=$((CNT + 1))
    if [ ! -f "$OUT/classes/cn/fzj/callersentry/$cls.class" ]; then
        echo "!! 缺少编译产物：$cls.class"
        exit 1
    fi
done
echo "   $CNT 个类全部生成"

echo "== 4/7 d8 =="
find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
"$JAVA_BIN/java.exe" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --release --min-api 26 --lib "$PLATFORM" \
    --output "$OUT/dex" "@$OUT/classes.txt"

echo "== 5/7 打包 dex =="
python - "$OUT" <<'PY'
import shutil, sys, zipfile, os
out = sys.argv[1]
src = os.path.join(out, "base.apk")
dst = os.path.join(out, "unsigned.apk")
shutil.copy(src, dst)
with zipfile.ZipFile(dst, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(out, "dex", "classes.dex"), "classes.dex")
print("  已写入 classes.dex")
PY

echo "== 6/7 zipalign =="
"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== 7/7 签名 =="
KS="$APP/keystore/sentry.jks"
mkdir -p "$APP/keystore"

# 签名口令不写进代码：优先环境变量 SENTRY_KS_PASS，其次 keystore/pass.txt（已在 .gitignore 里）
if [ -n "$SENTRY_KS_PASS" ]; then
    KS_PASS="$SENTRY_KS_PASS"
elif [ -f "$APP/keystore/pass.txt" ]; then
    KS_PASS="$(tr -d '\r\n' < "$APP/keystore/pass.txt")"
else
    echo "!! 缺少签名口令：设置 SENTRY_KS_PASS 环境变量，或写入 keystore/pass.txt"
    exit 1
fi
KS_ALIAS="${SENTRY_KS_ALIAS:-sentry}"

if [ ! -f "$KS" ]; then
    "$JAVA_BIN/keytool.exe" -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -keyalg RSA -keysize 2048 -validity 12000 \
        -dname "CN=CallerSentry, O=Personal, C=CN" >/dev/null 2>&1
    echo "  已创建签名密钥"
fi
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$APP/CallerSentry.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" verify --print-certs "$APP/CallerSentry.apk" | head -3

ls -lh "$APP/CallerSentry.apk"
echo "== 完成 =="
