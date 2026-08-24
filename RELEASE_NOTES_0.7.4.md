# Balance Island 0.7.4

> Debug Preview / Pre-release，用于真机验证余额接口与后台恢复行为。

- 修复 Kimi/Moonshot 被错误归类为“仅验证 Key”的问题，普通 Key 现在直接查询可用、现金与代金券余额。
- 自动兼容 Moonshot 国内人民币端点与国际美元端点。
- OpenRouter Management Key 读取各 Key 的官方 `usage_daily` 并与账户余额组合显示。
- OpenAI Admin Key 增加当天组织 Cost。
- DeepSeek、Moonshot、SiliconFlow 增加持久化“今日约用”统计。
- 保存当天最早余额、最后余额和已观察充值；进程当天被杀后，恢复时补算离线期间的余额下降。
- 新增“仅余额”“今日已用/余额”“自动轮播”三种显示内容模式。
- 同步更新简体中文、繁體中文（台灣）、English、日本語和 한국어资源。
