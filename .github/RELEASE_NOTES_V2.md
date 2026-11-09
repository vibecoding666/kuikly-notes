## 轻记 · Kuikly Notes v2.0.0（全新 UI + 大文本架构）

按草图重做界面与交互，正文改用原生编辑器承载，支持单篇 50 万+ 汉字。

### 界面（按草图）

| 页面 | 内容 |
|---|---|
| 笔记 | 2 列便签卡片网格（左侧色带、标题、摘要、标签、日期、字数） |
| 待办 | 长条列表 +「已完成 (N)」分组，勾选即在两组间移动 |
| 顶栏 | 标题 + 右上搜索 + 三点菜单（视图 / 排序 / 主题 / 置顶 / 收藏 / 归档 / 删除） |
| 编辑页 | **全屏无边框**：左上返回键 + 标题 + 右上三点；元信息行显示创建时间、最后编辑时间、**当前字数**；正文区占满 |
| 底部 | 「笔记 / 待办」segmented 切换；右下角悬浮 ＋ |

去掉了旧版两排 10 个小按钮、emoji 图标、色块上的文字标签、「留空保留原正文」文案和硬盖全屏的编辑面板。

### 50 万字架构（方案 A）

- **Kuikly 扩展原生 View**：`DeclarativeBaseView` + `IKuiklyRenderViewExport`，把平台 `EditText` 暴露给 Kuikly（`renderViewExport("HRNotesEditor", ...)`）。
- **全文不跨层**：Kotlin common 层只存元数据（摘要 / 字数 / 时间），正文永不进入响应式状态；`TextArea.textDidChange` 那种「每敲一字回传全文」的瓶颈被彻底消除。原生侧只回传 `{length, editedAt}` 事件。
- **分块文件存储**：正文按 32K 字符切片写 `files/bodies/<id>_N.txt`，预览只读第一块；载入时分块 append 到 EditText。
- **800ms debounce 后台写**：输入不阻塞 UI，磁盘 IO 在单线程 executor 上。
- **裸 `android.widget.EditText`**：不用 AppCompatEditText（对大文本明显更慢），固定内边距。

> 说明：`PrecomputedText` 对 EditText 无效（`BufferType.EDITABLE` 会跳过预计算），所以没有依赖它；性能靠「不跨层传全文 + 分块 + 后台 IO」保证。

### 验证（GitHub Actions 模拟器，API 33 / 34 双矩阵）

- `FATAL EXCEPTION` / ANR / `handleException` 计数 **0**
- 首页网格、编辑页、返回键、元信息行、原生编辑器均正常渲染
- 输入 `HelloKuiklyNativeEditor` → 顶部字数 **0 字 → 23 字**（事件链路通）
- monkey 200 事件注入完成，进程存活

### 产物

- `kuikly-notes-release.apk` — **1,933,994 字节（约 1.84 MiB）**，≤ 5 MB 校验通过
- 由 `:androidApp:assembleRelease` 产出，CI 生成的 release keystore 签名

### 诚实说明

- 真机上「单篇 50 万字符连续编辑」的帧率**未做压测**（浏览器预览里 50 万字赋值 25ms，但那是 Chrome textarea，不代表 Android）。架构上已消除最大瓶颈，但实际流畅度建议装上长文实测。
- release 签名证书在 CI 临时生成，仅用于验收；上架请换成自己的 keystore。
