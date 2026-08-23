# 状态栏余额（Balance Island）MVP

一个原生 Android 应用，用状态栏顶部的透明文字条显示多家 AI API 的余额、预算或 Key 状态。面向 Android 8.0 及以上的大多数手机和平板，不绑定特定品牌或型号。

> 当前交付为可构建源码。生成环境没有 Android SDK，因此未在此处产出 APK；请在 Android Studio 中执行 Gradle Sync 和真机构建。后续测试步骤见 `CODEX_TASK_SPEC.md`。

## 已实现

- Kotlin + Jetpack Compose 设置页，所有功能按可伸缩分类卡片组织。
- `TYPE_APPLICATION_OVERLAY` 状态栏单行文字条，不依赖厂商专有卡片或私有 SystemUI 接口。
- 仅显示已配置的 API 账户；支持 DeepSeek、OpenAI、OpenRouter、SiliconFlow、Kimi/Moonshot、Anthropic、Google Gemini、xAI/Grok。
- 同一服务商可保存多个 Key，并分别缓存、刷新和轮播。
- 展示格式：`服务商真实图标［备注或 Key 后四位］余额`，不再显示外侧界定符。
- 同服务商只有一个 Key 且未写备注时，自动隐藏方括号部分，节省状态栏宽度。
- 自动轮播全部已配置账户，或固定任一已配置服务商；每 5 秒切换。
- 点按文字条立即切换账户，长按返回设置。
- 左上/右上圆角安全区、左上/右上贴边四种预设。
- 圆角安全预设读取 Android DisplayCutout、RoundedCorner 和状态栏 Insets，并保留通用最小侧边空间。
- 透明文字与半透明底两种样式，内置五种文字颜色。
- 文字条运行时默认每 1 分钟查询，可自定义 `1–1440` 分钟。
- 每账户独立额度警告：余额小于等于警告线 `×1.5` 时橙色，越过警告线时红色并推送。
- 每账户独立下降步进通知，默认每下降 `5` 个货币单位提醒一次。
- 每账户可填写手动显示余额；留空继续使用 API 值。
- DeepSeek：调用 `GET https://api.deepseek.com/user/balance`。
- OpenAI：调用 `GET /v1/organization/costs`；如果可用，再读取 `GET /v1/organization/spend_limit`。
- Android Keystore + AES-GCM 分账户加密保存 API Key，设置页只显示备注/后四位。
- 自动迁移上一版保存的单个 DeepSeek/OpenAI Key。
- 文字条前台服务负责分钟级刷新；服务未运行时由 WorkManager 以系统允许的 15 分钟周期兜底。
- 控制中心“余额监控”磁贴可一键启动/停止文字条；Android 13+ 可在应用内请求添加。
- 可选“被回收后自动恢复”：结合 `START_STICKY`、任务移除延迟恢复、开机完成和应用升级广播恢复服务。
- 内置简体中文和英文，可在应用内选择或跟随系统；界面、文字条与系统通知同步切换。

## 状态栏示例

```text
[DeepSeek 图标]［主账号］ ¥37.28
[OpenAI 图标]［9K2M］ $18.42 可用
```

图标使用各服务商的真实品牌轮廓；来源与商标说明见 `NOTICE_BRAND_ICONS.md`。备注为空且存在多个同服务商账户时，方括号内自动使用 Key 后四位。

## 余额定义

DeepSeek、OpenRouter 和 SiliconFlow 显示官方接口返回/计算的账户余额。OpenAI 没有公开的通用预付余额查询接口，因此应用显示：

```text
月度预算剩余 = 组织硬消费上限 - 本月 Costs
```

若组织没有可读取的硬消费上限，只显示本月累计消费。OpenAI 查询需要组织 Owner 创建的 Admin API Key，普通项目 Key 通常没有组织成本权限。

Kimi/Moonshot、Anthropic、Google Gemini、xAI/Grok 的普通 Key 当前只做官方模型接口验证；这些平台没有向普通 Key 提供统一剩余余额时，需在“额度警告与手动余额”填写余额。应用不会把 token 用量伪装成余额。

## 构建

推荐环境：Android Studio、JDK 17、Android SDK 35。

1. 用 Android Studio 打开本目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 和真实安卓设备。
4. 点击 Run，或执行：

```bash
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

APK 默认输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 在“API 账户”选择服务商。
2. 可选填写备注，再填入 Key，点击“添加并测试”。可重复添加多个账户。
3. 展开“额度警告与手动余额”，逐账户设置警告线、下降提醒步长和可选手动余额。
4. 展开“刷新频率与通知”，设置 1–1440 分钟查询间隔。
5. 在“显示账户”选择自动轮播或固定某个已配置服务商。
6. 在“后台运行与控制中心”可打开自动恢复，并添加“余额监控”磁贴。
7. 在“位置预设”选择左/右圆角安全区，点击“启动文字条”并授予权限。
8. 若位置与系统图标重叠，用水平/垂直滑块微调；点按切换账户，长按打开设置。

## 通用设备适配

文字条使用系统公开的 WindowInsets 自动读取状态栏高度、显示缺口和顶部圆角半径。安全区预设会自动避让屏幕边缘，贴边预设保留少量通用边距；不同系统仍可使用 X/Y 滑块即时微调。工程不包含任何品牌、型号或厂商卡片 SDK 特判。

## 多语言

- 默认跟随系统语言。
- 设置页可切换简体中文或 English。
- Android 13+ 同时声明系统“应用语言”支持；旧版 Android 使用应用内语言配置。
- 新语言只需在 `app/src/main/res/values-语言代码/strings.xml` 增加同名资源。

## 安全说明

- API Key 不写入源码、不打印日志、不加入 Android 备份。
- 每个 Key 都使用 Android Keystore 中的不可导出 AES 密钥加密。
- OpenAI Admin Key 权限很高。本 MVP 适合个人侧载；公众发布前应改为自建只读后端和短期令牌。

## 已知限制

- 不同品牌的状态栏图标布局和后台省电策略不同，首次使用可能需要微调 X/Y，并手动允许自启动或后台活动。
- 系统设置中的“强行停止”会阻止广播和后台恢复；应用不会也不能绕过 Android 的强停语义。
- WorkManager 周期任务最短为 15 分钟，执行时间可能被省电策略推迟。
- 1 分钟刷新依赖文字条前台服务持续运行；过高查询频率可能触发供应商限流或增加电耗。
- OpenAI Costs 数据可能存在账单处理延迟；当前还未处理 Costs 分页。
- 品牌图标仅用于识别 API 服务商，不代表厂商背书；正式公开发布前应再次审阅商标条款。

## 官方接口

- DeepSeek Balance：<https://api-docs.deepseek.com/api/get-user-balance/>
- OpenAI Costs：<https://developers.openai.com/api/reference/resources/admin/subresources/organization/subresources/usage/methods/costs/>
- OpenAI Spend Limit：<https://developers.openai.com/api/reference/resources/admin/subresources/organization/subresources/spend_limit/methods/retrieve/>
- OpenRouter Credits：<https://openrouter.ai/docs/api/api-reference/credits/get-credits>
- SiliconFlow `/user/info` 变更说明：<https://docs.siliconflow.cn/cn/release-notes/overview>
- Anthropic Models：<https://docs.anthropic.com/en/api/models-list>
- Gemini Models：<https://ai.google.dev/api/models>
- xAI API：<https://docs.x.ai/docs/overview>
