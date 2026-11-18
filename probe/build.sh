#!/bin/bash
# 构建最小探针 APK（不含任何资源与第三方库）
set -e

ROOT="E:/AI-WORKBUDDY/2026-11-15-17-36-04"
ENV="$ROOT/build-env"
BT="$ENV/sdk/build-tools/34.0.0"
PLATFORM="$ENV/sdk/platforms/android-34/android.jar"
JAVA_BIN="$ENV/jdk/jdk-17.0.21+9/bin"

export JAVA_HOME="$ENV/jdk/jdk-17.0.21+9"
cd "$ROOT/caller-sentry/probe"
OUT="build"
rm -f "$OUT"/*.apk
mkdir -p "$OUT/classes" "$OUT/dex"

"$BT/aapt2.exe" link -o "$OUT/base.apk" \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 26 \
    --target-sdk-version 34

"$JAVA_BIN/javac.exe" -nowarn -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$PLATFORM" -d "$OUT/classes" \
    src/cn/fzj/probe/ProbeActivity.java > "$OUT/javac.log" 2>&1 || true
if [ ! -f "$OUT/classes/cn/fzj/probe/ProbeActivity.class" ]; then
    echo "!! javac 失败"; cat "$OUT/javac.log"; exit 1
fi

find "$OUT/classes" -name "*.class" > "$OUT/classes.txt"
"$JAVA_BIN/java.exe" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --release --min-api 26 --lib "$PLATFORM" \
    --output "$OUT/dex" "@$OUT/classes.txt"

python - "$OUT" <<'PY'
import shutil, sys, zipfile, os
out = sys.argv[1]
shutil.copy(os.path.join(out, "base.apk"), os.path.join(out, "unsigned.apk"))
with zipfile.ZipFile(os.path.join(out, "unsigned.apk"), "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(out, "dex", "classes.dex"), "classes.dex")
PY

# 口令同样不写死：环境变量 SENTRY_KS_PASS 或 keystore/pass.txt
if [ -n "$SENTRY_KS_PASS" ]; then
    KS_PASS="$SENTRY_KS_PASS"
elif [ -f "$ROOT/caller-sentry/keystore/pass.txt" ]; then
    KS_PASS="$(tr -d '\r\n' < "$ROOT/caller-sentry/keystore/pass.txt")"
else
    echo "!! 缺少签名口令：SENTRY_KS_PASS 或 keystore/pass.txt"; exit 1
fi

"$BT/zipalign.exe" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner.bat" sign --ks "$ROOT/caller-sentry/keystore/sentry.jks" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$ROOT/caller-sentry/Probe.apk" "$OUT/aligned.apk"
ls -lh "$ROOT/caller-sentry/Probe.apk"
