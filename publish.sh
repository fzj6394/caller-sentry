#!/bin/bash
# 一键发布到 GitHub：建仓库 + 推送 + 打 tag + 上传 APK 到 Release
#
# 前置：
#   1. 装 GitHub CLI（winget install --id GitHub.cli -e）
#   2. 能访问 github.com（需要代理/VPN，本机直连不通）
#   3. 先构建出 APK：bash build.sh
#
# 用法：
#   bash publish.sh                 # 建公开仓库 caller-sentry
#   bash publish.sh private         # 建私有仓库
#   REPO_NAME=my-name bash publish.sh
set -e

NAME="${REPO_NAME:-caller-sentry}"
VIS="${1:-public}"
TAG="v2.5"

cd "$(dirname "$0")"

if ! command -v gh >/dev/null 2>&1; then
    echo "没有装 GitHub CLI。装一下：winget install --id GitHub.cli -e"
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "还没登录 GitHub，下面会给你一个一次性验证码，去 https://github.com/login/device 输入。"
    gh auth login --hostname github.com --git-protocol https --web
fi

if [ ! -f CallerSentry.apk ]; then
    echo "还没构建 APK，先跑 bash build.sh"
    exit 1
fi

if git remote | grep -q origin; then
    echo "已存在 origin，直接推送"
    git push -u origin main
else
    echo "创建仓库 $NAME（$VIS）并推送…"
    gh repo create "$NAME" --"$VIS" --source=. --remote=origin --push
fi

echo "打 tag $TAG"
git tag -f "$TAG" >/dev/null 2>&1 || true
git push -f origin "$TAG"

echo "上传 APK 到 Release"
if [ -f RELEASE_NOTES.md ]; then
    gh release create "$TAG" CallerSentry.apk --title "$TAG" --notes-file RELEASE_NOTES.md
else
    gh release create "$TAG" CallerSentry.apk --title "$TAG" --generate-notes
fi

echo
echo "完成："
gh repo view --json url -q .url
