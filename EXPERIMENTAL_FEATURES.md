# 实验性功能与安全说明

## ChatGPT/Codex 套餐用量

BalanceIsland v0.9.0 首次加入可选的 ChatGPT/Codex 套餐用量读取。该功能适合不想频繁切换到其他应用查看 5 小时与周额度的用户，但它不是 OpenAI 提供的公开集成能力。

OpenAI 官方 API 文档目前公开的是 API 组织的模型调用用量与成本端点，例如 `GET /organization/usage/completions` 和 `GET /organization/costs`，并未提供 ChatGPT/Codex 订阅套餐剩余百分比 API：

- https://developers.openai.com/api/reference/python/resources/admin/subresources/organization/subresources/usage

本功能因此只能在用户主动登录 `https://chatgpt.com/` 后，由同一个 WebView 页面读取未公开的 `/backend-api/wham/usage`。这个路径、响应结构和可用性都不受保证，OpenAI 可以随时修改或关闭它。

## 启用方式

1. 从设置页左侧边缘滑动，或点击顶部标题打开侧边栏。
2. 进入带警示色的“实验性功能”区域。
3. 阅读风险说明并主动勾选确认。
4. 在应用内 WebView 登录 ChatGPT。
5. 回到 HTTPS `chatgpt.com` 页面，点击“读取用量”。

该功能不会要求用户粘贴 Session ID、Cookie 或 Authorization 值。

## 本机保存的数据

应用自己的 SharedPreferences 只保存：

- 风险确认版本；
- 套餐类型；
- 5 小时与周额度的剩余百分比；
- 对应重置时间；
- 上次成功读取时间。

登录 Cookie 和站点存储由 Android System WebView 管理，不会被复制到 BalanceIsland 的普通配置、日志、通知或浮岛文本中。应用全局关闭 Android 备份，避免配置和 WebView 数据进入系统云备份。

## 已采取的保护

- 实验功能默认关闭，并需要显式风险确认。
- 实验 Activity 不可被其他应用直接启动，也不会出现在最近任务中。
- 登录页启用 `FLAG_SECURE`，系统截图与最近任务缩略图会被阻止。
- WebView 禁止文件访问、content 访问和 HTTP 混合内容，并启用 Safe Browsing。
- 第三方 Cookie 默认关闭。
- 用量读取只允许在 HTTPS 且主机严格等于 `chatgpt.com` 时执行。
- 没有使用 `addJavascriptInterface`，网页无法直接调用应用对象。
- 错误提示不会显示接口响应正文、Cookie 或其他会话数据。
- “断开并清除”会删除 Cookie、WebStorage、缓存、历史、表单、SSL 状态和本地用量记录，同时撤销风险确认。

## 仍然存在的风险

- 登录会话本质上等同密码。设备被控制、系统 WebView 存在漏洞或站点本身被篡改时，仍可能造成信息泄露或账号暴露。
- 嵌入式登录可能触发验证码、SSO 兼容问题、安全提醒或重新登录。
- 未公开接口可能返回不同字段、被限流、拒绝 WebView 请求或彻底移除。
- 套餐窗口名称与时长可能变化，当前界面的“5 小时”和“周额度”只是对已知响应结构的解释。
- BalanceIsland 无法保证第三方网页加载的脚本、身份提供商或网络服务的行为。

如果不能接受这些风险，请不要确认启用。查看失败时不要将 Cookie、Session ID、网页调试内容或完整响应发送给任何人。

## 清除与恢复

点击“断开并清除”后，实验功能会回到未确认状态；下次使用需要重新确认风险并登录。卸载应用也会删除本机保存的数据，但为了清除实验会话，不需要卸载正式版本。
