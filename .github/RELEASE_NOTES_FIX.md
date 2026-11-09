## 轻记 · Kuikly Notes v1.0.1（修复版）

修复 v1.0.0 的**启动闪退**与**桌面图标缺失**。这两个问题由新增的模拟器诊断 workflow 在 GitHub Actions 中复现并定位。

### 修复内容

- **闪退根因**：`core-render-android` 运行时反射加载 `androidx.recyclerview.widget.RecyclerView`，但该 AAR 的 POM 只声明了 `kotlin-stdlib`，androidx 依赖需宿主自行提供。为压体积砍掉全部 androidx 依赖后，出现
  `java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/recyclerview/widget/RecyclerView;`。
  补上 `androidx.recyclerview:recyclerview:1.3.2` 后修复。
- **无图标根因**：`<application>` 未配置 `android:icon`。补上矢量图标 `res/drawable/ic_launcher.xml`。

### 模拟器验证结果（API 33 / 34 两个级别）

| 检查 | 结果 |
|---|---|
| `FATAL EXCEPTION` | 0 |
| Kuikly 初始化 | `initRenderView ... pageName: NotesPage` |
| 首帧渲染 | `NotesPage: LaunchData(firstPaintCost=676, initViewCost=62, initRenderCoreCost=316, ...)` |
| 应用进程 | 持续存活 |
| 图标 | `application-icon-160/240/320` 均存在 |

### 产物

- `kuikly-notes-release.apk` — **1,932,657 字节（约 1.84 MiB）**，仍远低于 5 MB 上限
- 由 `:androidApp:assembleRelease` 产出，CI 生成的 release keystore 签名

### 诊断 workflow

`.github/workflows/diagnose.yml`（手动触发 / 诊断文件变更时自动触发）：在 GitHub-hosted Linux runner 上启用 KVM，矩阵跑 API 33、34 两个模拟器，安装 APK → 启动 MainActivity → 抓取完整 logcat、dumpsys、截图并上传为 artifact。

### 说明

- release 签名证书在 CI 中临时生成，仅用于演示与验收；上架请换成自己的 keystore。
- 已通过模拟器验证启动与首帧渲染，未做完整 UI 交互遍历。
