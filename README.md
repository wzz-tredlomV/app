Unarchiver Android 项目骨架 (Kotlin) - RAR 集成、并行大文件、详细通知、测试

主要特性:
- compileSdk: 34, minSdk: 26, targetSdk: 31
- 支持解压: zip, 7z, rar (junrar), tar, tar.gz, tar.bz2, tar.xz, gz, bz2, xz
- 支持压缩: zip, 7z, tar, tar.gz, tar.bz2, tar.xz; RAR 压缩通过尝试系统 'rar' 二进制（若无则不可用）
- ArchiveManager 支持 ProgressCallback、逐文件进度、总体进度；遇到大文件（>5MB）自动使用线程池并行处理
- NotificationHelper 支持更详细的通知（单文件开始/进度/完成、总体进度、速率估算）
- 包含单元测试和简单 UI (Espresso) 测试
- 脚本会在结束时把整个项目打包为 ${ZIP_NAME}

说明:
- RAR 压缩受限：应用中不能直接用开源库安全地创建 RAR 文件（RAR 是专有格式）。脚本中实现的方案是把文件复制到临时目录并尝试调用系统 `rar` 二进制（需要在 PATH 中）。在 Android 设备上一般不可用；建议使用其它格式（zip/7z/tar.*）。
- 运行:
  1. 需要：bash, python3, pillow (pip install pillow), zip (系统工具)
  2. 运行：chmod +x setup_project.sh && ./setup_project.sh
  3. 运行脚本后会在本目录生成 ${ROOT} 以及压缩文件 ${ZIP_NAME}
  4. 打开 Android Studio 导入 ${ROOT}，同步 Gradle（需要 SDK 34）
