
# My2048App (Compose)



脚本生成的 Compose 2048 示例项目。已包含 GitHub Actions workflow 文件（.github/workflows/android.yml），用于在 CI 中通过 Gradle Wrapper 构建 APK。



注意：

- Actions 工作流 使用 Gradle Wrapper (./gradlew)。请确保仓库中包含 gradle wrapper 文件：

  - gradlew

  - gradlew.bat

  - gradle/wrapper/gradle-wrapper.properties

  - gradle/wrapper/gradle-wrapper.jar

- 脚本会在本地尝试自动生成 Gradle Wrapper（如果本机安装了 gradle）。如果脚本没能生成 wrapper，请在本地运行：

  - gradle wrapper --gradle-version 7.5.1

  然后把生成的 wrapper 文件提交到仓库。



如何在本机上使用：

1. 运行脚本：

   - chmod +x create_project.sh

   - ./create_project.sh

2. 如果脚本提示你生成 wrapper，请在有 gradle 的机器运行：

   - ./gradlew wrapper --gradle-version 7.5.1

3. 初始化 git，提交并推送到 GitHub：

   - git init

   - git add .

   - git commit -m "Initial commit"

   - git remote add origin <your-repo-url>

   - git push -u origin main



CI（GitHub Actions）会在 push 后自动触发构建。



