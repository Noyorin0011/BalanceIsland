# Balance Island 0.7.2

> 当前以 Debug Preview / Pre-release 形式提供，用于设备兼容性与功能验证，不作为稳定正式版。

- 状态栏文字条的垂直位置恢复为从屏幕顶边计算：`0 dp` 即屏幕顶边，不再额外叠加状态栏居中偏移。
- 位置设置文案明确显示为“距屏幕顶部”，并同步更新简体中文、English、日本語、繁體中文（台灣）和 한국어。
- GitHub Actions 接入仓库内的 Android 签名 Secrets；主分支、标签和手动构建同时产出 Debug APK 与正式签名 Release APK，PR 不接触签名材料。
- 修复控制中心 / 快速设置磁贴在服务停止或进程被系统回收后仍卡在“运行中”的问题；磁贴现在显示服务的真实运行状态，而不是仅显示期望状态。
- 磁贴的启动、停止状态现在即时刷新，并在停止时清除残留的服务状态。
- 新增 `BUILDING.md`，说明 Android Studio、命令行、GitHub Actions 与自有 JKS 的构建方式。
- README 增加测试阶段提示及 AI 辅助开发声明。
