# Balance Island 0.7.1

- OpenAI 账户配置页新增 Project Key / Admin Key 权限提示。
- 输入 `sk-proj-` 时即时提醒：仅校验 Key，余额需要手动设置。
- `sk-proj-` 与普通 OpenAI Key 改为通过 `/v1/models` 校验，不再错误调用组织级账单接口。
- `sk-admin-` 继续用于查询组织消费与消费上限。
- 保存前自动清理 Key 前的 `Bearer`、说明文字、引号和多余空白，不修改 Key 本体。
- 清洗并测试仍失败时弹窗显示验证失败原因，提示检查 Key、有效期和接口权限。
- 新增日本語、繁體中文（台灣）与 한국어，连同简体中文和 English 共支持五种语言。
- Android 13+ 系统应用语言与应用内语言切换均声明 `ja`、`zh-Hant-TW`、`ko`。
- 修复 Compose `weight` 错误导入和旧版系统快捷设置 Intent 常量的编译问题。
- GitHub Actions 在 `main`、PR、手动运行和 `v*` 标签触发时使用 JDK 17 与 Android SDK 35 构建 Debug APK。
- CI Artifact 为调试签名，只适合测试；正式发布仍需单独配置 Release Keystore。
