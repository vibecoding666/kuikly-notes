# 轻记 · Kuikly Notes

一个用 **腾讯 Kuikly**（Kotlin Multiplatform + 原生渲染）写的便签 App，只用 GitHub Actions 编译，产物为 release APK。

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| UI 框架 | 腾讯 Kuikly `2.4.2-2.0.21`（制品来自 Maven Central） |
| 语言 | Kotlin `2.0.21` |
| 构建 | AGP `7.4.2` / Gradle `7.6.3` / KSP `2.0.21-1.0.27` |
| 渲染 | 原生控件渲染，无自绘引擎、无 WebView、无 JS Bridge |
| 数据 | 纯 Kotlin 业务逻辑 + 自写极简 JSON 编解码，SharedPreferences 持久化 |

工程结构：

```
shared/                      KMP 业务模块
  commonMain/                 Note 模型、JSON、NoteRepository（全部纯 Kotlin）
  androidMain/                expect/actual 平台实现（时钟、存储）
androidApp/                  安卓宿主壳（Activity + Kuikly 适配器）
.github/workflows/build.yml  CI：编译 release APK、校验 <= 5MB、上传 artifact
```

## 功能

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

## 编译

推送到 `main` 或在 Actions 里手动触发 `Build Release APK` 即可。CI 会：

1. 装 JDK 17 + Android SDK 34
2. 用 `keytool` 生成 release keystore 并注入环境变量
3. `./gradlew :androidApp:assembleRelease`
4. 校验 APK **不大于 5MB**（超出直接失败）
5. 上传 artifact `kuikly-notes-release-apk`

拉取产物：

```bash
gh run list --workflow "Build Release APK"
gh run download <run-id> -n kuikly-notes-release-apk
```

> release 签名在 CI 中临时生成（每次构建一把新钥匙），仅用于演示与验收；
> 若要上架，请把自己的 keystore 放到 Secrets 并替换 workflow 里的生成步骤。

## 关于体积

Kuikly 的核心卖点就是极小的 SDK 增量（AOT 模式下 Android 约 300 KB）。本工程不引入
appcompat / material / constraintlayout 等大型依赖，宿主直接用 `android.app.Activity`，
因此整包远小于 5MB。
