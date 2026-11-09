## 轻记 · Kuikly Notes v1.0.0

用腾讯 **Kuikly**（Kotlin Multiplatform + 原生渲染）写的便签 App，全程由 GitHub Actions 编译，产物为 release APK。

### 产物

- `kuikly-notes-release.apk` — **1,306,727 字节（约 1.25 MiB）**，远低于 5 MB 上限
- 由 `:androidApp:assembleRelease` 产出，已用 CI 生成的 release keystore 签名（证书 `CN=KuiklyNotes`）
- APK 结构完整：`classes.dex`、`AndroidManifest.xml`、`resources.arsc`、`META-INF/CERT.RSA`

### 功能

- 便签新建 / 编辑 / 保存 / 删除 / 复制
- 标题、正文、标签、8 种颜色、置顶、收藏
- 待办清单（勾选、添加）
- 搜索（标题 / 正文 / 标签）
- 五个视图：全部、收藏、置顶、归档、回收站
- 回收站：恢复、彻底删除、清空
- 五种排序：最近修改、创建时间、标题、置顶优先、颜色
- 标签筛选、统计面板
- 导出 JSON / 导入 JSON
- 本机持久化，首次启动带示例数据

### 技术栈

| 项 | 版本 |
|---|---|
| UI 框架 | 腾讯 Kuikly 2.4.2-2.0.21 |
| 语言 | Kotlin 2.0.21 |
| 构建 | AGP 8.5.0 / Gradle 8.7 / KSP 2.0.21-1.0.27 |
| 渲染 | 原生控件渲染，无自绘引擎、无 WebView、无 JS Bridge |

业务逻辑全部在 `shared/commonMain` 用纯 Kotlin 实现（自写极简 JSON 编解码，零额外依赖），宿主壳不引入 appcompat / material 等大型库 —— 这是整包能压到 1.25 MiB 的原因。

### 说明

- release 签名证书在 CI 中临时生成，仅用于演示与验收；若要上架，请把自己的 keystore 放到 Secrets 并替换 workflow 中的生成步骤。
- 本版本已验证**编译、签名、体积**，未在真机或模拟器上实跑 UI 交互。
