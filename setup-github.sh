#!/bin/bash

# GitHub项目设置脚本

# 检查是否提供了仓库URL
if [ -z "$1" ]; then
    echo "使用方法: ./setup-github.sh <github-repo-url>"
    echo "示例: ./setup-github.sh https://github.com/username/MySimpleApp.git"
    exit 1
fi

REPO_URL="$1"

echo "设置Git仓库..."

# 初始化Git仓库
git init

# 添加所有文件
git add .

# 提交初始版本
git commit -m "Initial commit: MySimpleApp Android app"

# 添加远程仓库
git remote add origin "$REPO_URL"

# 推送到GitHub
git branch -M main
git push -u origin main

echo "项目已成功推送到GitHub！"
echo "仓库地址: $REPO_URL"
