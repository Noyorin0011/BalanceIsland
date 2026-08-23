# OPPO/OnePlus 原生流体云接入骨架

这里不是可由 Android Studio 直接编译的普通 App 模块，而是交给 OPPO Pantanal DevStudio 的 UPK 界面骨架，以及宿主 App 的 SeedlingSupportSDK 参考实现。

官方接入链路：

1. 在 OPPO 开放平台申请泛在卡片服务，并确认“API 余额监控”场景是否准入。
2. 获得 `serviceId`、`eventCode`、`event` 等服务参数。
3. 在 Pantanal DevStudio 中创建 API 2.0+ 流体云工程，用 `upk/src/pages/index.oml` 作为模板基础。
4. 真机调试并把 UPK 发布到 OPPO 服务库。未发布的 UPK 不会由系统云端下发，普通侧载 APK 无法绕过。
5. 在宿主 App 中添加官方依赖：

   ```kotlin
   implementation("com.oplus.pantanal.card:seedling-support-lite:3.0.7")
   ```

6. 将 `android/BalanceFluidCloudProvider.kt.example` 移入 App 源码，按真实 SDK 版本修正签名，并在 Manifest 注册 `com.oplus.seedling.action.SEEDLING_CARD` Provider。
7. 使用 OPPO 分配的事件参数调用 `SeedlingTool.updateIntelligentData(...)` 创建或销毁卡片。不能自行编造事件码。

`index.oml` 已按官方组合模板约束提供：胶囊左/右各一个元素，文本不超过 5 个字符，并提供必需的 `A1*` 兜底服务图标。发布前仍需替换图标资源并通过 OPPO 设计与场景审核。

官方文档：

- 流体云模板：<https://open.oppomobile.com/documentation/page/info?id=12658>
- 流体云卡片：<https://open.oppomobile.com/documentation/page/info?id=12965>
- SeedlingSupportSDK 接入：<https://open.oppomobile.com/documentation/page/info?id=12719>
- SeedlingTool：<https://open.oppomobile.com/documentation/page/info?id=12696>
