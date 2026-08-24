# 状态栏余额（Balance Island）MVP

一个原生 Android 应用，用状态栏顶部的透明文字条显示多家 AI API 的余额、预算或 Key 状态。面向 Android 8.0 及以上的大多数手机和平板，不绑定特定品牌或型号。

> 当前仍处于测试阶段，但 GitHub 的正式版本已统一发布 Release 签名 APK。Debug APK 仅用于开发测试，不保证与正式版本相互覆盖升级。

> **签名冲突已解决**：从 `0.7.5` 起，正式 Release 固定使用同一张发布证书，CI 会在发布前拒绝 Debug 证书，后续正式版本可直接覆盖升级。若手机里安装的是早期 `0.7.2-debug`、`0.7.3-debug`、旧 `0.7.4-debug` 或其他 Debug 签名包，仍需先卸载一次（会清除应用内配置），再安装最新正式 APK；此后无需重复卸载。

> GitHub Actions 会在推送 `main`、提交 PR、手动运行或推送 `v*` 标签时使用 JDK 17 与 Android SDK 35 构建 APK；安全上下文中还会使用仓库 Secrets 生成正式签名 Release APK。后续测试步骤见 `CODEX_TASK_SPEC.md`。

完整版本变化见 [`CHANGELOG.md`](CHANGELOG.md)。

## 已实现

- Kotlin + Jetpack Compose 设置页，所有功能按可伸缩分类卡片组织。
- `TYPE_APPLICATION_OVERLAY` 状态栏单行文字条，不依赖厂商专有卡片或私有 SystemUI 接口。
- 仅显示已配置的 API 账户；支持 DeepSeek、OpenAI、OpenRouter、SiliconFlow、Kimi/Moonshot、Xiaomi MiMo、Anthropic、Google Gemini、xAI/Grok。
- 同一服务商可保存多个 Key，并分别缓存、刷新和轮播。
- 展示格式可选：仅余额、`今日已用 / 余额`，或每 5 秒在两者间轮播。
- 同服务商只有一个 Key 且未写备注时，自动隐藏方括号部分，节省状态栏宽度。
- 自动轮播全部已配置账户，或固定任一已配置服务商；每 5 秒切换。
- 点按文字条立即切换账户，长按返回设置。
- 左上/右上圆角安全区、左上/右上贴边四种预设。
- 圆角安全预设读取 Android DisplayCutout、RoundedCorner 和状态栏 Insets，并保留通用最小侧边空间。
- 透明文字、半透明底、自动对比描边和自适应对比底色四种样式，内置五种文字颜色；亮色文字自动配深色对比效果，暗色文字自动配浅色对比效果。
- 文字条运行时默认每 1 分钟查询，可自定义 `1–1440` 分钟。
- OpenRouter 与 OpenAI 使用官方日用量；DeepSeek、Moonshot、SiliconFlow 通过持久化余额差显示“今日约用”。当天被杀后台后，恢复时会补算离线期间的下降。
- 每账户独立额度警告：余额小于等于警告线 `×1.5` 时橙色，越过警告线时红色并推送。
- 每账户独立下降步进通知，默认每下降 `5` 个货币单位提醒一次。
- 每账户可选异常变动报警：支持绝对值、百分比或任一阈值，带独立提醒冷却时间。
- 每账户可填写手动显示余额；留空继续使用 API 值。
- 切换待添加的服务商时会清空备注、API Key 和显示状态，避免误把上一家的凭据保存到下一家。
- 可选余额长期不变后自动隐藏文字条；余额变化时自动恢复，并发送无横幅、无声音的静默通知。
- DeepSeek：调用 `GET https://api.deepseek.com/user/balance`。
- OpenAI：`sk-admin-` 调用 `GET /v1/organization/costs`，并在可用时读取 `GET /v1/organization/spend_limit`；`sk-proj-` 与普通 Key 仅通过 `GET /v1/models` 校验，余额使用手动设置。
- Xiaomi MiMo：普通按量 `sk-` Key 通过官方 `GET /v1/models` 和 `api-key` 请求头校验；官方未公开余额查询接口，余额使用手动设置。Token Plan 的 `tp-` Key 不在本应用中调用。
- Android Keystore + AES-GCM 分账户加密保存 API Key，设置页只显示备注/后四位。
- 粘贴 Key 时会自动提取 `sk-`、`AIza` 或 `xai-` 凭据，去除前置 `Bearer`、说明文字、引号和空白；验证仍失败时弹窗显示接口错误并提示检查 Key。
- 自动迁移上一版保存的单个 DeepSeek/OpenAI Key。
- 文字条前台服务负责分钟级刷新；服务未运行时由 WorkManager 以系统允许的 15 分钟周期兜底。
- 控制中心“余额监控”磁贴可一键启动/停止文字条；Android 13+ 可在应用内请求添加。
- 可选“被回收后自动恢复”：结合 `START_STICKY`、任务移除延迟恢复、开机完成和应用升级广播恢复服务。
- 内置简体中文、繁體中文（台灣）、English、日本語和 한국어，可在应用内选择或跟随系统；界面、文字条与系统通知同步切换。

## 状态栏示例

```text
[DeepSeek 图标]［主账号］ ¥37.28
[OpenAI 图标]［9K2M］ $18.42 可用
```

图标使用各服务商的真实品牌轮廓；来源与商标说明见 `NOTICE_BRAND_ICONS.md`。备注为空且存在多个同服务商账户时，方括号内自动使用 Key 后四位。

## 余额定义

DeepSeek、OpenRouter、SiliconFlow 和 Kimi/Moonshot 显示官方接口返回/计算的账户余额。Moonshot 会自动尝试国内 `api.moonshot.cn`（CNY）与国际 `api.moonshot.ai`（USD）余额接口。OpenAI 没有公开的通用预付余额查询接口，因此应用显示：

```text
月度预算剩余 = 组织硬消费上限 - 本月 Costs
```

若组织没有可读取的硬消费上限，只显示本月累计消费。OpenAI 组织消费查询需要 Organization Owner 创建的 Admin API Key。配置页会常驻提示这一权限差异；检测到 `sk-proj-` 时只校验模型访问，不再错误调用组织账单接口，并提示填写手动余额。Admin Key 同时会读取当天组织 Cost。

OpenAI Project Key、Xiaomi MiMo、Anthropic、Google Gemini、xAI/Grok 的普通 Key 当前只做官方模型接口验证；这些平台没有向普通 Key 提供统一剩余余额时，需在“额度警告与手动余额”填写余额。应用不会把 token 用量伪装成余额。

对于仅提供余额、不提供日账单接口的服务商，应用在本机保存当天最早余额、最后余额与识别到的充值增长，以余额下降估算今日消费。该数值从首次启用统计开始，跨零点恢复或长时间停机时仍属于近似值，因此界面明确标为“今日约用”。

## 构建

推荐环境：Android Studio、JDK 17、Android SDK 35。完整的首次配置、命令行构建、APK 输出位置、Release 签名和 GitHub Actions 说明见 [`BUILDING.md`](BUILDING.md)。

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
3. 展开“额度警告与手动余额”，逐账户设置警告线、下降提醒步长、异常变动阈值、冷却时间和可选手动余额。
4. 展开“刷新频率与通知”，设置 1–1440 分钟查询间隔，并可开启 5–1440 分钟无变化自动隐藏。
5. 在“显示账户”选择自动轮播或固定某个已配置服务商。
6. 在“显示内容”选择仅余额、今日用量/余额或自动轮播。
7. 在“后台运行与控制中心”可打开自动恢复，并添加“余额监控”磁贴。
8. 在“文字样式”中选择透明文字、半透明底、自动对比描边或自适应对比底色。
9. 在“位置预设”选择左/右圆角安全区，点击“启动文字条”并授予权限。
10. 若位置与系统图标重叠，用水平/垂直滑块微调；点按切换账户，长按打开设置。

## 通用设备适配

文字条使用系统公开的 WindowInsets 自动读取状态栏高度、显示缺口和顶部圆角半径。安全区预设会自动避让屏幕边缘，贴边预设保留少量通用边距；垂直偏移始终从屏幕顶边起算，`0 dp` 即屏幕顶边。不同系统仍可使用 X/Y 滑块即时微调。工程不包含任何品牌、型号或厂商卡片 SDK 特判。

## 多语言

- 默认跟随系统语言。
- 设置页可切换简体中文、繁體中文、English、日本語或 한국어。
- Android 资源目录分别为默认 `values`、`values-b+zh+Hant+TW`、`values-en`、`values-ja` 与 `values-ko`。
- Android 13+ 同时声明系统“应用语言”支持；旧版 Android 使用应用内语言配置。
- 新语言只需在 `app/src/main/res/values-语言代码/strings.xml` 增加同名资源。

## GitHub Actions

- 推送到 `main`、提交 PR、手动运行工作流或推送 `v*` 标签都会触发 Debug APK 构建。
- 构建使用 JDK 17、Gradle 8.11.1、Android SDK 35，并先校验全部五套语言资源。
- 非 PR 构建会读取 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，额外生成正式签名 Release APK；PR 不接触这些 Secrets。
- APK 在对应 Actions 运行页的 `Artifacts` 区域下载：Debug 默认保留 14 天，签名 Release 默认保留 30 天。
- 非 PR 构建成功后会创建或更新与当前 `versionName` 对应的正式 Release（例如 `v0.7.5`），从 `CHANGELOG.md` 自动截取该版本说明，并上传通过证书检查的 `BalanceIsland-v<version>.apk`。
- Debug APK 只适合测试；对外发布和覆盖升级应始终使用同一套 Release Keystore 签名的 APK。
- 正式 Release 的签名冲突问题已解决：工作流会检查证书并拒绝 `CN=Android Debug`。早期 Debug 签名安装只需卸载迁移一次，之后正式版本可连续覆盖升级。

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
- OpenRouter Credits：<https://openrouter.ai/docs/api/api-reference/credits/get-remaining-credits>
- OpenRouter Key 日用量：<https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key>
- Moonshot 国内余额：<https://platform.moonshot.cn/docs/api/balance>
- Moonshot 国际余额：<https://platform.moonshot.ai/docs/api/balance>
- SiliconFlow `/user/info` 变更说明：<https://docs.siliconflow.cn/cn/release-notes/overview>
- Anthropic Models：<https://docs.anthropic.com/en/api/models-list>
- Gemini Models：<https://ai.google.dev/api/models>
- xAI API：<https://docs.x.ai/docs/overview>
- Xiaomi MiMo List Models：<https://mimo.mi.com/docs/zh-CN/api/model/list-models>
- Xiaomi MiMo API Key 类型：<https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration>

## 后续规划（TODO）

- [x] **自动隐藏与静默提醒（0.8.0）**：长时间无余额变动时自动隐藏状态栏文字条；发生变动时自动重新显示，并仅在通知栏以图标静默提示（无横幅弹窗）。
- [x] **API Key 异常变动报警（0.8.0）**：监测各 API Key 余额/用量的异常变动，支持绝对值、百分比和提醒冷却。
- [ ] **接口可靠性**：为可重试错误增加指数退避，细分网络、超时、权限、限流与服务端错误，并补齐 OpenAI Costs 分页。
- [ ] **账户管理与测试**：增加“删除全部凭据”，并为告警状态机、SecureKeyStore 迁移和删除补单元测试。
- [ ] **显示模式**：增加固定指定账户、同服务商合计、字体大小、背景透明度和最大宽度设置。

## AI 辅助开发声明

项目构想、需求定义与验收由 Noyorin 主导；代码由 GPT-5.6-sol 与 DeepSeek V4-Flash（DSV4F）联合辅助完成。AI 生成或修改的代码仍由项目维护者负责审阅、测试与发布。
