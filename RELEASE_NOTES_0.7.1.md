# Balance Island 0.7.1

- OpenAI 账户配置页新增 Project Key / Admin Key 权限提示。
- 输入 `sk-proj-` 时即时提醒：仅校验 Key，余额需要手动设置。
- `sk-proj-` 与普通 OpenAI Key 改为通过 `/v1/models` 校验，不再错误调用组织级账单接口。
- `sk-admin-` 继续用于查询组织消费与消费上限。
- 保存前自动清理 Key 前的 `Bearer`、说明文字、引号和多余空白，不修改 Key 本体。
- 清洗并测试仍失败时弹窗显示验证失败原因，提示检查 Key、有效期和接口权限。
- 同步更新简体中文、英文资源和翻译任务包。
