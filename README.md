# MySimpleApp

一个简单的Android应用程序，演示基本功能。

## 功能特性

- 用户输入姓名
- 显示个性化欢迎消息
- 现代化的Material Design界面
- 响应式布局

## 项目结构

```
MySimpleApp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/mysimpleapp/
│   │   │   └── MainActivity.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       ├── colors.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
├── gradle/wrapper/
├── .gitignore
└── README.md
```

## 构建说明

### 前提条件
- JDK 11或更高版本
- Android SDK

### 构建APK

#### 方法1: 使用Gradle包装器
```bash
# 生成调试版APK
./gradlew assembleDebug

# 生成发布版APK
./gradlew assembleRelease
```

#### 方法2: 使用Android Studio
1. 使用Android Studio打开项目
2. 选择 Build → Generate Signed Bundle / APK
3. 按照向导创建签名APK

### 运行应用
```bash
# 连接到设备或模拟器
adb devices

# 安装调试版APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

本项目配置了GitHub Actions自动化工作流：
- 在每次推送时自动构建APK
- 生成APK文件作为构建产物
- 可在Actions页面下载构建的APK

## 贡献指南

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 许可证

本项目采用MIT许可证。详见 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题或建议，请通过GitHub Issues提交。
