# 0.6.0 后台与多服务商版

## 新增

- Android 控制中心“余额监控”磁贴，一键启动/停止状态栏文字条。
- 可选自动恢复：前台服务 `START_STICKY`、任务划除后延迟恢复、开机完成及应用升级恢复。
- OpenRouter Management Key 余额查询。
- SiliconFlow `/v1/user/info` 总余额查询。
- Kimi/Moonshot、Anthropic、Google Gemini、xAI/Grok 普通 Key 验证，并支持手动余额与原有告警。
- 8 家服务商图标、账户添加入口和固定显示选项。

## 行为边界

- Android“强行停止”会禁止后台恢复，应用不会绕过系统限制。
- ColorOS 仍可能要求用户手动允许自启动、后台活动并关闭电池优化。
- OpenRouter 余额接口要求 Management Key；普通推理 Key 可能没有权限。
- Anthropic、Gemini、xAI 和 Kimi/Moonshot 普通 Key 没有统一可读的剩余余额时，应用只验证 Key，不伪造余额；可使用手动余额。

## 构建状态

源码与 Android 资源 XML 已完成静态检查。当前生成环境无法解析 Android Gradle Plugin `8.9.2`，因此本次没有声称已产出或真机验证 APK；请在 Android Studio（JDK 17、Android SDK 35）完成 Gradle Sync 后运行 `assembleDebug`。
