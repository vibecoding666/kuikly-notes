## 轻记 · Kuikly Notes v2.2.0

按真机反馈修掉了 4 个硬伤，其中「滚动只能滚一行」是架构级问题。

### 1. 滚动只能滚一行 —— 已根治

**根因**（用 CI 埋点实测出来的，不是猜）：

| | 修复前 | 修复后 |
|---|---|---|
| 编辑器收到的 MOVE 事件 | **0** | **56** |
| 3 次滑动累计滚动距离 | **0 px** | **475 px** |

Kuikly 的渲染层会用自己的手势探测器在整个页面上 `requestDisallowInterceptTouchEvent(true)`，**把原生 `EditText` 的 MOVE 事件全部吃掉**，导致正文一次只能挪一点点。这个行为在 2.4.2 上没有开关能关掉。

**解药**：编辑页整体搬到**独立原生 Activity**（`EditorActivity`）。列表/待办仍是 Kuikly，编辑正文是标准 `android.widget.EditText` —— 滚动、惯性、光标、输入法全部走 Android 原生路径，性能与系统记事本一致。

### 2. 删除按钮被遮盖 + 误触 —— 改成长按多选

- **去掉卡片上的 × 按钮**（第一排被徽标遮挡的问题一并消失）
- **长按卡片进入选择模式**：卡片显示勾选圈，点按切换选中
- 顶部显示「已选 N 项」+「完成」，底部出现「取消 / 删除」操作条
- 支持**批量删除**，删完同样有 5 秒撤销（一次恢复全部）
- 待办列表同样支持长按多选

### 3. 左下角「› 编辑器就绪」文字 —— 已删除

### 4. 顺带修掉的真 bug

- **删光所有笔记后重启会重新生成示例笔记**：`load()` 现在只在真正首次安装（存储为空）时 seed，`[]` 不再被当成空库
- **每敲一个字都写一次 SharedPreferences**：改为关闭编辑器时统一落盘
- **每次按键创建一个 setTimeout**：长文输入时会堆积上千个定时器，已移除
- **待办列表每次刷新整表重建**：改为只在切 tab 时重建，滚动位置不再丢

### 验证（GitHub Actions 模拟器，API 33 / 34）

12 项断言全过、`FATAL EXCEPTION` / ANR / `handleException` 均为 **0**：

```
OK: editor requested                OK: gestures reach the native editor (n=56)
OK: native EditorActivity running    OK: long document scrolls far (y=475)
OK: back to list after editor        OK: long-press enters selection mode
OK: first tap only arms delete       OK: bulk delete removed the selected notes
OK: second tap performs the delete   OK: todo tab selected
```

50 万字样张：分 16 块载入、可正常滚动与输入，顶部字数实时更新（500000 → 500001）。

### 产物

- `kuikly-notes-release.apk` — 约 1.9 MiB，≤ 5 MB 校验通过
- CI 生成的 release keystore 签名

### 说明

- 编辑器现在是一个原生页面（Kuikly 外壳 + 原生正文的方案在 2.4.2 上无法解决手势冲突）。列表、待办、搜索、筛选、多选、撤销仍是 Kuikly 实现。
- release 签名证书在 CI 临时生成，仅用于验收；上架请换成自己的 keystore。
