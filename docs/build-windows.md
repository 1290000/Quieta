# Quieta 本机构建环境（Windows）

工具链已从 C 盘迁至 D 盘，避免占满系统盘。

| 组件 | 路径 |
|------|------|
| JDK 17 (Temurin) | `D:\Tools\jdks\jdk-17.0.20.1+1` |
| Gradle 9.5.0 | `D:\Tools\gradle-dist\gradle-9.5.0` |
| GRADLE_USER_HOME | `D:\Tools\gradle-home\.gradle` |
| Android SDK | `C:\Users\i1290\AppData\Local\Android\Sdk` |

## 构建前设置（PowerShell）

```powershell
$env:JAVA_HOME = "D:\Tools\jdks\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "C:\Users\i1290\AppData\Local\Android\Sdk"
$env:GRADLE_USER_HOME = "D:\Tools\gradle-home\.gradle"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 构建 Debug

```powershell
D:\Tools\gradle-dist\gradle-9.5.0\bin\gradle.bat :app:assembleDebug -p C:\Users\i1290\Documents\ChatGPT\Quieta
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

不要把上述本机绝对路径写进 Gradle 脚本；`local.properties` 里的 `sdk.dir` 仍指向本机 SDK。
