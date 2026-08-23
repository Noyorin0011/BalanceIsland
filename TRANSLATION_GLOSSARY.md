# Balance Island 翻译术语表

| 原词 | 产品含义 | 翻译要求 |
|---|---|---|
| Balance Island | 应用名 | 可保留英文；若本地化，整套界面保持一致 |
| balance | 可用余额或手动余额 | 不要误译成“消费额” |
| costs / spend | 已发生的 API 消费 | 不要误译成“剩余余额” |
| spending limit | 组织消费硬上限 | 不等同于充值余额或信用额度 |
| available | 消费上限减去当月 Costs 后的估算剩余 | 避免暗示为官方预付余额 |
| alert line | 用户设置的低余额警告阈值 | 全应用统一术语 |
| drop notification step | 余额每下降指定货币单位提醒一次 | 强调“下降量”而非时间间隔 |
| manual balance | 用户手动输入、用于显示和告警的数值 | 不能描述成 API 官方返回值 |
| API account | 一组服务商、API Key 与备注 | 不等同于厂商网页登录账号 |
| API Key | API 凭据 | 可保留英文；不要显示或扩写真实 Key |
| project key | OpenAI 普通项目 Key，常见前缀 `sk-proj-` | 可验证模型访问，但不要声称可读取组织账单 |
| admin key | OpenAI 组织管理 Key，常见前缀 `sk-admin-` | 明确高权限；用于组织 Usage/Costs 类管理接口 |
| status bar overlay | 使用 Android 公开悬浮窗能力显示的文字条 | 不要翻译或宣传成系统原生灵动岛/流体卡片 |
| safe area | 根据显示缺口、圆角和状态栏 Insets 预留的区域 | 不要写成特定手机型号适配 |
| background recovery | 服务被系统回收后按用户设置尝试恢复 | 不保证绕过系统“强行停止” |
| Quick Settings tile | Android 控制中心/快捷设置磁贴 | 使用目标系统的惯用称呼 |
| follow system | 跟随系统或系统默认语言 | 表示不强制指定应用语言 |

## 固定品牌写法

`DeepSeek`、`OpenAI`、`OpenRouter`、`SiliconFlow`、`Kimi`、`Moonshot`、`Anthropic`、`Google Gemini`、`xAI`、`Grok`。

## 不应翻译的示例和代码

`sk-...`、`sk-proj-...`、`sk-admin-...`、`sk-or-...`、`sk-ant-...`、`AIza...`、`xai-...`、`/v1/models`、`/v1/organization/costs`。
