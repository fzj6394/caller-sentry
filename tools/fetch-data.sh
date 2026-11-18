#!/bin/bash
# 下载号段库（xluohome/phonedata，GPL-3.0）到 assets/。
# 仓库里不放这个 4.3MB 的二进制，构建前跑一次即可。
set -e
cd "$(dirname "$0")/.."
mkdir -p assets
URL="https://cdn.jsdelivr.net/gh/xluohome/phonedata@master/phone.dat"
if [ -f assets/phone.dat ]; then
    echo "assets/phone.dat 已存在（$(stat -c%s assets/phone.dat) 字节），跳过"
    exit 0
fi
echo "下载号段库…"
curl -sL -m 600 -o assets/phone.dat "$URL"
SIZE=$(stat -c%s assets/phone.dat)
echo "完成：$SIZE 字节"
if [ "$SIZE" -lt 500000 ]; then
    echo "!! 文件太小，可能下载失败"
    rm -f assets/phone.dat
    exit 1
fi
