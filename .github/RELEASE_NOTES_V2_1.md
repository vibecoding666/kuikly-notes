## 轻记 · Kuikly Notes v2.1.0（修复真机反馈的三个问题）

主人真机安装后发现并修复的问题。

### 1. 正文「跑中间了」

- **根因**：`HRNotesEditorView` 里调用了 `setTextIsSelectable(true)`。这个 API 会把 EditText 切换成 TextView 行为，导致**文本垂直居中**且内部滚动被禁用 —— 表现就是正文浮在屏幕中间。
- **修复**：去掉 `setTextIsSelectable(true)`；显式设置 `gravity = TOP or START`、`setHorizontallyScrolling(false)`、`includeFontPadding = false`；并覆写 `onMeasure` 强制填满 Kuikly 分配的尺寸，不再 wrap-content。
- 验证：模拟器截图确认正文回到左上角。

### 2. 编辑页右上角三点菜单弹不出

- **根因**：`syncSlots()` 里写的是 `if (menuOpen && screen == "list")`，编辑页 `screen == "editor"`，菜单槽**永远不会被渲染**。
- **修复**：菜单在列表页和编辑页都能弹出，且内容按页面区分：
  - 列表页：视图筛选（5 种）/ 排序（5 种）/ 深色模式
  - 编辑页：置顶、收藏、归档、复制、深色模式、删除、压测样张（50 万字）

### 3. 删除便签容易误触

- **修复一 · 两段式确认**：第一次点删除按钮只「上膛」（按钮变成红色的「确定删除」，提示「再点一次确认删除」，3 秒自动取消），第二次才真正删除。
- **修复二 · 撤销条**：删除后底部浮出「已移入回收站 · 撤销」，5 秒内可一键还原。撤销条浮在悬浮按钮左上方，不会被遮挡。
- **修复三 · 事件抑制**：点删除按钮后 600ms 内屏蔽卡片的点击，避免「删除」和「打开编辑器」同时触发。
- **修复四 · 回收站可彻底删除**：回收站里的卡片同时提供「↺ 恢复」和「✕ 彻底删除」。

### 顺带修的

- **输入不再每键写盘**：正文字数/时间的元数据落盘加了 800ms 防抖，之前每敲一个字都要写一次 SharedPreferences。
- **待办列表不再整表重建**：切到待办页才渲染分组，刷新不会重置滚动位置。
- **关闭编辑器时强制落盘**：待写入的元数据在关闭时立即刷盘，不丢最后一次编辑。

### 验证（GitHub Actions 模拟器，API 33 / 34 双矩阵）

smoke 脚本现在用**确定性 intent 钩子**（`--ei menu 1` / `--es delete_test 1` / `--ei tab 1` / `--ei stress 500000`）驱动并断言，9 项全部通过：

```
OK: editor opened
OK: native editor reported meta
OK: editor three-dot menu opened
OK: editor closed
OK: first tap only arms the delete
OK: second tap performs the delete
OK: todo tab selected
OK: stress request received
```

`FATAL EXCEPTION` / ANR / `handleException` 计数 **0**。

### 产物

- `kuikly-notes-release.apk` — 约 1.9 MiB，≤ 5 MB 校验通过
- 由 `:androidApp:assembleRelease` 产出，CI 生成的 release keystore 签名

### 诚实说明

- 修复是依据模拟器截图与 logcat 断言验证的；**真机手感（尤其是软键盘弹出时的布局）仍建议主人再过一遍**。
- release 签名证书在 CI 临时生成，仅用于验收；上架请换成自己的 keystore。
