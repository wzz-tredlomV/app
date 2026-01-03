#!/bin/bash

echo "开始构建APK..."

# 清理项目
./gradlew clean

# 构建调试版APK
echo "构建调试版APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "调试版APK构建成功！"
    echo "APK位置: app/build/outputs/apk/debug/"
    ls -la app/build/outputs/apk/debug/*.apk
else
    echo "构建失败！"
    exit 1
fi

# 可选：构建发布版
# echo "构建发布版APK..."
# ./gradlew assembleRelease
