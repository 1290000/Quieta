# Quieta 本机构建环境（Windows）

使用仓库自带的 Gradle Wrapper，不调用本机另装的旧版 Gradle。版本以 `gradle/wrapper/gradle-wrapper.properties` 和 `gradle/libs.versions.toml` 为准。

## 构建前设置（PowerShell）

以下是本机已验证的路径；其他机器需按实际安装位置调整：

```powershell
$env:JAVA_HOME = 'D:/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/i1290/AppData/Local/Android/Sdk'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
```

Gradle 缓存默认位于用户目录 `.gradle`。已有 `GRADLE_USER_HOME` 时沿用现有缓存，不必为构建迁移目录。本机 SDK 和默认缓存仍在 C 盘。

miuix 默认使用 `D:/Tools/miuix` 的本地源码。可通过 `-PmiuixDir=<实际路径>` 覆盖。显式设置 `ANDROID_HOME`，确保 miuix 的 included build 也能找到 SDK；不要将本机绝对路径写入项目构建脚本。

## 构建与检查

在仓库根目录运行：

```powershell
./gradlew.bat :app:assembleDebug check --console=plain
```

Debug 产物位于 `app/build/outputs/apk/debug/`。Release 必须遵循根目录 `AGENTS.md` 的统一签名流程，不使用临时密钥或 debug 签名代替。

提交文档或调试相关改动前，暂存目标文件并运行 `python scripts/check_repo_hygiene.py`。临时截图、录屏帧和界面 XML 的存放规则见 `docs/README.md`。
