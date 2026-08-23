# Balance Island 多语言翻译与接入任务书

## 1. 目标

为 Balance Island `0.7.0-universal-i18n` 增加一种或多种目标语言。当前基准语言：

- 简体中文：`app/src/main/res/values/strings.xml`
- English：`app/src/main/res/values-en/strings.xml`

优先以英文文件为翻译源，中文文件用于确认产品含义。必须翻译全部资源，不能只翻译主页面。

## 2. 最低交付物

如果只负责翻译，请为每种语言返回：

1. `values-<Android qualifier>/strings.xml`
2. `TRANSLATION_REPORT.md`，写明：
   - 语言名称
   - BCP 47 标签，例如 `ja`、`ko`、`fr`、`zh-Hant-TW`
   - Android 资源目录名
   - 有意保留英文的品牌名和技术词
   - 存在歧义、需要产品确认的资源键

不要返回散乱的“键=译文”列表；必须返回合法 Android XML。

## 3. Android 资源目录示例

| 语言 | BCP 47 | 推荐目录 |
|---|---|---|
| 日本語 | `ja` | `values-ja` |
| 한국어 | `ko` | `values-ko` |
| Français | `fr` | `values-fr` |
| Deutsch | `de` | `values-de` |
| Español | `es` | `values-es` |
| 繁體中文（台灣） | `zh-Hant-TW` | `values-b+zh+Hant+TW` |

语言和地区组合优先使用 Android BCP 47 目录形式：`values-b+语言+脚本+地区`。

## 4. 严格翻译规则

1. 不得修改 `<string name="...">` 的 `name`。
2. 目标文件必须与英文基准包含完全相同的资源键；接入语言名称时新增的键除外。
3. 保留格式占位符及其顺序和类型：`%1$s`、`%2$s`、`%1$d`。
4. `%%` 表示格式化后的单个百分号，不得擅自改成单个 `%`。
5. 保留必要的 XML 转义；不得输出未转义的 `&`、`<`。
6. `configure_api` 的开头空格用于紧邻图标显示，必须保留。
7. 不翻译 API Key 示例：`sk-...`、`sk-admin-...`、`sk-proj-...`、`AIza...`、`xai-...`。
8. DeepSeek、OpenAI、OpenRouter、SiliconFlow、Kimi、Moonshot、Anthropic、Gemini、xAI、Grok 是品牌名，原则上保持官方写法。
9. `API`、`Key`、Android、Keystore、AES-GCM、WorkManager 可按目标语言的开发者惯例决定是否本地化。
10. 不加入营销内容，不改变警告线、查询频率或安全说明的实际含义。

## 5. 长度和语气

- 状态栏、控制中心磁贴和按钮优先简短，避免逐字直译造成截断。
- `quick_settings_tile_label` 建议不超过约 18 个拉丁字符或等效宽度。
- `configure_api`、`snapshot_waiting`、`snapshot_key_valid` 应尽量短。
- 设置说明可以自然、明确，不必逐字对应中文语序。
- 警告和错误文案必须直接说明问题，不使用模糊或戏谑表达。

## 6. 术语要求

先阅读 `TRANSLATION_GLOSSARY.md`。余额相关语义必须区分：

- balance：可用余额或用户手动余额
- costs / spend：已发生消费
- spending limit：消费上限，不等同于预付余额
- alert line：用户设置的余额警告线
- project key：普通项目 API Key，不能自动表述为组织账单读取 Key
- admin key：组织管理 Key，权限高于项目 Key

## 7. 自动校验

在翻译包根目录执行：

```bash
python3 tools/validate_translations.py app/src/main/res/values-en/strings.xml target/strings.xml
```

校验必须输出 `PASS`。脚本会检查：

- XML 是否可解析
- 是否存在重复键
- 是否缺少或多出资源键
- 格式占位符是否一致
- `configure_api` 的开头空格是否保留

脚本不会判断译文质量，仍需人工或另一模型复核。

## 8. 可选：直接接入工程

若任务要求直接接入，而不是只返回译文：

1. 把目标文件放入正确的 `app/src/main/res/values-.../strings.xml`。
2. 在 `app/src/main/res/xml/locales_config.xml` 添加对应 BCP 47 `<locale>`。
3. 在 `AppLanguage.kt` 增加语言枚举和标签识别。
4. 为所有现有语言文件增加该语言的显示名称资源，例如 `language_japanese`。
5. 在 `MainActivity.kt` 的 `AppLanguage.localizedLabel()` 映射新语言资源。
6. 运行校验脚本、Android Lint 和 `assembleDebug`。

不要修改 API 查询、加密存储、后台服务或告警业务逻辑。

## 9. 完成标准

- 所有资源键完整。
- XML、占位符和前导空格校验通过。
- 品牌名与金额含义准确。
- 设置页、悬浮文字条、前台通知、额度通知和控制中心磁贴均有目标语言文案。
- 若直接接入，语言可在应用内选择，重启后仍保持，切回“跟随系统”正常。

## 可直接复制给本地 AI 的提示词

```text
你正在为 Android 应用 Balance Island 翻译界面。完整阅读 TRANSLATION_TASK.md 和 TRANSLATION_GLOSSARY.md，以 app/src/main/res/values-en/strings.xml 为主源、app/src/main/res/values/strings.xml 为中文语义参考。为我指定的目标语言生成合法 Android strings.xml。不得修改资源键，必须保留所有 %1$s、%2$s、%1$d、%%、XML 转义和 configure_api 的开头空格。品牌名使用官方写法。完成后运行 tools/validate_translations.py，修复到输出 PASS，并附 TRANSLATION_REPORT.md。除非我明确要求直接接入工程，否则不要修改 Kotlin 代码。
```
