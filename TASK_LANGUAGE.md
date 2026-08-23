# BalanceIsland 补语言任务书（v0.7.0-universal-i18n 扩展）

> 给 Codex / dsh 的执行说明。目标：在现有「简体中文（默认）+ English」基础上，**新增 日本語 / 繁體中文 / 한국어 三套语言**，不改动架构、不动现有中英翻译。

## 一、基线（先读 README.md 和本任务书）

- 工程：Kotlin + Jetpack Compose，包名 `com.noyorin.balanceisland`，当前 `0.7.0-universal-i18n`。
- 现有资源：
  - `app/src/main/res/values/strings.xml` —— **默认简体中文**（注意：中文是默认值，**不要**建 `values-zh`）
  - `app/src/main/res/values-en/strings.xml` —— English
  - `app/src/main/res/xml/locales_config.xml` —— 应用语言声明
  - `app/src/main/java/com/noyorin/balanceisland/localization/AppLanguage.kt` —— 语言枚举与切换
- 现有 language 枚举：`SYSTEM("")`、`SIMPLIFIED_CHINESE("zh-Hans-CN")`、`ENGLISH("en")`。
- 现状：中英 **126 个 string key 完全对齐**，无缺漏、无多余。新增语言必须保持这个对齐。

## 二、目标语言与资源目录约定

| 语言 | AppLanguage 枚举 tag | 资源目录 |
|---|---|---|
| 日本語 | `JAPANESE("ja")` | `values-ja/strings.xml` |
| 繁體中文 | `TRADITIONAL_CHINESE("zh-Hant-TW")` | `values-zh-rTW/strings.xml` |
| 한국어 | `KOREAN("ko")` | `values-ko/strings.xml` |

> 繁体说明：`values-zh-rTW`（值为 zh-TW，隐含繁体）是 Android 旧式区域限定符，lint 友好、兼容性稳；若想只区分繁简不区分地区，可改用 BCP47 目录 `values-b+zh+Hant`，但需同步把 tag 改为 `zh-Hant`。**二选一，全工程一致即可**，本任务书默认走 `values-zh-rTW` + `zh-Hant-TW`。

## 三、实施步骤

1. **复制基线**：把 `values-en/strings.xml` 复制到 `values-ja`、`values-zh-rTW`、`values-ko`，作为翻译起点。
2. **逐条翻译**，**key 名与数量严格一致**（126 个），不得增删、改名。
3. **翻译硬性要求**：
   - 保留所有占位符：`%1$d`、`%1$s`、`%2$s`、`%3$s` 等，**位置与含义对应**。
   - 字面 `%` 写 `%%`。
   - 保留 `\n` 换行。
   - `app_name` 用对应语言名（如「バランス島」「餘額浮島」「밸런스 아일랜드」），厂商名、货币符号、数字格式保持原样。
   - 术语统一：provider 名（DeepSeek/OpenAI…）、余额、警告线、手动余额、下降提醒步长等。
4. **更新 `locales_config.xml`**：追加
   ```xml
   <locale android:name="ja" />
   <locale android:name="zh-Hant-TW" />
   <locale android:name="ko" />
   ```
5. **更新 `AppLanguage.kt` 枚举**：新增 `JAPANESE("ja")`、`TRADITIONAL_CHINESE("zh-Hant-TW")`、`KOREAN("ko")`，`current()/set()/wrap()` 逻辑无需改（通用）。
6. **更新语言选择 UI**：`values/strings.xml` 与三个新语言里各加一个语言项名（**用母语显示**）：
   - `language_japanese` = **日本語**
   - `language_traditional_chinese` = **繁體中文**
   - `language_korean` = **한국어**
   并在设置页语言分组（`section_language`）把这三项加进可选择列表。
7. **更新 README「多语言」段落**，列出现有语言。
8. **构建**：JDK 17 + Android SDK 35，`./gradlew assembleDebug` 修复编译错误。
9. **跑 Lint**：`./gradlew lintDebug`，确保**没有** `MissingTranslation`、`ExtraTranslation`、`Untranslatable` 等问题。出现缺失时，是因为翻译不完整必须补全；出现多余时，是 key 没对齐。
10. **验收**：应用中切换「日语 / 繁體 / 한국어 / 简体 / English / 跟随系统」，确认设置页、状态栏文字条、前台服务通知、额度告警通知、控制中心磁贴**全部**同步切换，无英文残留、无混排、无乱码。

## 四、质量要求

- 译文自然、符合当地用语（繁体用台湾习惯，如「餘額」「帳戶」），避免机翻生硬。
- 占位符位置与语序正确（日/韩语序与英语不同，注意 `%1$s` 单独存在或与其他占位符顺序一致即可）。
- 语言列表项名一律用母语显示（不要翻译成别的语言）。
- 禁止把真实 API Key 写入代码、日志或测试夹具。

## 五、已知坑（务必注意）

- **中文是默认 `values/`**，不要新建 `values-zh`；新增是 `values-ja` / `values-zh-rTW` / `values-ko`。
- `locales_config.xml` 不更新的话，Android 13+「应用语言」设置不会识别新语言。
- Lint 会用默认 `values`（简体）作 fallback——若某 key 在 ja 缺失会报 MissingTranslation，必须补全而非忽略。
- 语言项名称（日本語/繁體中文/한국어）要放进**每个**语言文件（包括中文和英文文件），否则切换后该列表项会回落英文。

## 六、可直接交给 Codex / dsh 的提示词

```text
请为 BalanceIsland Android 工程（Kotlin + Compose，v0.7.0-universal-i18n）新增 日本語(ja)、繁體中文(zh-Hant)、한국어(ko) 三套语言。先读 README.md 与 TASK_LANGUAGE.md。保持现有架构。步骤：1) 复制 values-en/strings.xml 到 values-ja、values-zh-rTW、values-ko；2) 完整翻译全部 126 个 key，key 名与数量严格一致，保留 %1$d/%1$s/%2$s 占位符与 %% 转义；3) 更新 locales_config.xml 追加 ja/zh-Hant-TW/ko；4) 更新 AppLanguage.kt 新增 JAPANESE("ja")/TRADITIONAL_CHINESE("zh-Hant-TW")/KOREAN("ko")；5) 语言选择 UI 增加三项（母语显示 日本語/繁體中文/한국어），并把这些语言项名补进每个语言文件；6) 更新 README；7) 用 JDK17+SDK35 跑 assembleDebug 及 lintDebug，确保无 MissingTranslation/ExtraTranslation；8) 验证切换语言后设置页/文字条/通知/控制中心磁贴全部同步。不要改动中英现有翻译，不要加厂商特判，不要把真实 API Key 写入代码或日志。
```
