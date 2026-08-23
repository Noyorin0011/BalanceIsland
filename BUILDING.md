# 自行构建 Balance Island

本文说明如何从源码构建 Debug APK，以及如何使用自己的 JKS 构建正式签名 APK。普通测试建议先构建 Debug 版。

## 环境要求

- 64 位 Windows、macOS 或 Linux。
- JDK 17。
- Android Studio（推荐），或已配置好的 Android SDK 命令行工具。
- Android SDK Platform 35 与 Build Tools 35.0.0。
- 能访问 Google Maven 和 Maven Central 的网络。

## 获取源码

```bash
git clone https://github.com/Noyorin0011/BalanceIsland.git
cd BalanceIsland
```

也可以在 GitHub 的 `Code → Download ZIP` 下载源码并解压。

## 使用 Android Studio 构建 Debug APK

1. 启动 Android Studio，选择 `Open`，打开项目根目录。
2. 如果提示选择 JDK，将 Gradle JDK 设为 JDK 17。
3. 等待 Gradle Sync 完成；缺少 Android SDK 35 时按提示安装。
4. 选择 `Build → Build App Bundle(s) / APK(s) → Build APK(s)`。
5. 构建完成后，在通知中点击 `locate`，或打开：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用命令行构建 Debug APK

macOS / Linux：

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

如需先检查多语言资源：

```bash
python3 tools/validate_translations.py app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
python3 tools/validate_translations.py app/src/main/res/values-en/strings.xml app/src/main/res/values-ja/strings.xml
python3 tools/validate_translations.py app/src/main/res/values-en/strings.xml app/src/main/res/values-b+zh+Hant+TW/strings.xml
python3 tools/validate_translations.py app/src/main/res/values-en/strings.xml app/src/main/res/values-ko/strings.xml
```

## 安装 Debug APK

在手机上允许从当前文件管理器安装未知来源应用，然后打开 APK；或在已启用 USB 调试的设备上执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK 使用 Android 调试证书签名，只适合开发测试。以后改用正式 JKS 签名时，通常需要先卸载 Debug 版；卸载会清除应用数据。

## 构建自己的正式签名 APK

不要把 JKS、密码、Base64 文本或 `local.properties` 提交到 GitHub。首次正式发布后，后续版本必须继续使用同一签名密钥，Android 才允许覆盖安装。

先在本地生成 JKS：

```bash
keytool -genkeypair -v -keystore balanceisland-release.jks -alias balanceisland -keyalg RSA -keysize 4096 -validity 10000
```

设置以下环境变量后构建：

macOS / Linux：

```bash
export BALANCE_ISLAND_STORE_FILE="/absolute/path/balanceisland-release.jks"
export BALANCE_ISLAND_STORE_PASSWORD="your-store-password"
export BALANCE_ISLAND_KEY_ALIAS="balanceisland"
export BALANCE_ISLAND_KEY_PASSWORD="your-key-password"
./gradlew :app:assembleRelease
```

Windows PowerShell：

```powershell
$env:BALANCE_ISLAND_STORE_FILE = "C:\absolute\path\balanceisland-release.jks"
$env:BALANCE_ISLAND_STORE_PASSWORD = "your-store-password"
$env:BALANCE_ISLAND_KEY_ALIAS = "balanceisland"
$env:BALANCE_ISLAND_KEY_PASSWORD = "your-key-password"
.\gradlew.bat :app:assembleRelease
```

输出位置：

```text
app/build/outputs/apk/release/app-release.apk
```

完成后建议清除当前终端中的密码环境变量，并对 JKS 与密码做至少两份离线加密备份。

## 使用 GitHub Actions 构建

仓库的 `Android APK` 工作流会在推送 `main`、提交 PR、手动运行或推送 `v*` 标签时构建 Debug APK。进入仓库的 `Actions → Android APK`，打开对应运行，在页面底部下载 `BalanceIsland-debug-*` Artifact。

如需让非 PR 构建同时生成正式签名版，请在仓库 `Settings → Secrets and variables → Actions` 配置：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

其中 `ANDROID_KEYSTORE_BASE64` 是 JKS 文件的 Base64 内容。四项均不可写入工作流、README、Issue 或日志。

## 常见问题

- `Plugin ... was not found`：通常是无法访问 Google Maven，检查网络和 Gradle 代理。
- `SDK location not found`：用 Android Studio 安装 SDK，或在本机创建 `local.properties` 并设置 `sdk.dir`；不要提交该文件。
- `validateSigningRelease` 失败：检查 JKS 路径、仓库密码、Alias 和私钥密码是否与创建时一致。
- 已安装 Debug 版但正式版提示签名不一致：备份应用内需要的数据后卸载 Debug 版，再安装正式版。
