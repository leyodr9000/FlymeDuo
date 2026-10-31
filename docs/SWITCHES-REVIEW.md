# 样式开关梳理与优化方案

本文只做两件事：把当前「哪些开关控制什么、谁又被谁门控」讲清楚，然后给出一个可以分批落地的优化方案。所有结论都标了源码位置，可以直接跳过去核对。

> **阅读提示**：一、二两节是**改造前**的快照，其中的行号与代码引文都指向改造前的版本，保留下来是为了让「为什么值得改」有据可查；方案落地后的实际结果、以及三处与本文提案的偏离，记录在第六节。想直接看最终状态就跳到第六节。

---

## 一、当前的开关全景

### 1.1 三层结构

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 存储 / 默认 / 界限 | `app/src/main/java/io/github/yixing233/hyperduo/Prefs.java` | 39 个在用键（另有 2 个只读 legacy 键 `show_mobile_type` 与 `out_type_size`，共 41 个 `KEY_*` 常量）、全部 `DEF_*` 默认与上下限，两侧共用的唯一真源 |
| 快照 | `app/src/main/java/io/github/yixing233/hyperduo/TrioSettings.java` | 39 个 public 字段、`from`/`copy`/`applyKeyFrom`/`fromBundle`/`toBundle`，clamp 在读侧发生 |
| 写入漏斗 | `app/src/main/java/io/github/yixing233/hyperduo/ui/SettingsRepository.kt` | 39 个 setter，每个都「本地 commit → push 到远程 preferences → 广播」 |
| 渲染 | `app/src/main/java/io/github/yixing233/hyperduo/TrioRenderer.java` | 只读 `TrioSettings`，画圆环或矩形 |
| Hook | `app/src/main/java/io/github/yixing233/hyperduo/TrioHooks.java` | 开关折叠槽位、交还原生图标、挂环外标签 |
| 界面 | `app/src/main/java/io/github/yixing233/hyperduo/ui/SettingsScreen.kt` | 4 个分页、39 个可写项、57 处 `enabled =`（第十七轮后实测计数；原为 20 / 33） |

链路是单向的：`SettingsScreen`（唯一写入口 `update`，`SettingsScreen.kt:287-290`）→ `SettingsRepository` → SharedPreferences + 远程 preferences + 广播 → `TrioConfig` 快照 → `TrioRenderer` / `TrioHooks`。**渲染侧从不回写**，这条边界是干净的，问题全部出在「开关之间的语义」上。

### 1.2 开关清单与真实归宿

| 开关 | 键 | 设置页行 | 渲染侧读取点 | Hook 侧读取点 |
| --- | --- | --- | --- | --- |
| 总开关 | `enabled` | `:626-632` | 从不读 | **11 处**（`TrioHooks.java:149/333/477/493/721/793/794/841/849/1050/1723`）|
| 三合一样式 | `trio_style` | `:642-654` | `TrioRenderer.java:115` 唯一 | 不读 |
| 显示 Wi-Fi 弧 | `show_wifi` | `:655-662` | `:214`、`:534` | `:153` |
| 显示移动信号点 | `show_mobile` | `:663-670` | `:217`、`:251` | `:156` |
| 显示电量数字 | `show_value` | `:671-678` | `:178`、`:189`、`:260`、`:271` | `:477`、`:793`、`:841` |
| 充电时显示闪电 | `show_bolt` | `:686-695` | `:178`、`:260` | `:477`、`:793`、`:841`、`:880` |
| 网络类型位置 | `mobile_type_mode` | `:709-717` | `:182`、`:261` | `:1723`、`:1830` |
| 电量数字居中 | `swap_wifi_value` | `:723-735` | `:172` **唯一，且只在圆环分支** | 不读 |
| 状态颜色 | `role_colors` | `:881-888` | `:389` 唯一 | 不读 |
| 三色 ×2（深/浅） | 六个 `color_*` | `:906-922` | `:394/397/400` | 不读 |
| 低电量阈值 | `low_threshold` | `:896-905` | `:396` 唯一 | 不读 |
| 环线粗细 | `ring_stroke` | `:748-756` | `:307`(矩形条)、`:409`、`:562/584/600` | 仅日志 `:346` |
| 弧线粗细 | `arc_stroke` | `:757-765` | `:337`(矩形位移)、`:482`、`:497` | 仅日志 `:346` |
| 数字字号 / 字重 | `value_size` / `value_weight` | `:766-774` / `:779-793` | `:371/561`、`:372/566` | 不读 |
| 圆环内类型字号 | `type_size` | `:805-815` | `:360`、`:583`、`:599` | 不读 |
| 环外字号 | `out_type_size_dp` | 第十二轮（1.6.2 起；取代裸像素键 `out_type_size`） | 不读 | `outTypeSizePx`（环外标签用它） |
| 5GA 中 A 的比例 | `type_suffix_scale` | 第十一轮（见 6.9） | `:368`（矩形）、`:783`、`:799`（环内分段绘制） | `:2031`（环外 `RelativeSizeSpan`）|
| 环外类型左边距 | `out_type_margin_left_dp` | 第十一轮 | 不读 | `placeOutTypeLabel` / `reserveOutRingStrip` |
| 环外类型右边距 | `out_type_margin_right_dp` | 第十一轮 | 不读 | 同上 |
| 环外信号间距 | `out_signal_margin_dp` | 第十一轮（1.6.1 起） | `TrioPreviewView.drawOutSignal` | `placeOutTypeLabel` / `reserveOutRingStrip` |
| 网络类型字重 | `type_weight` | `:842-853` | `:361`、`:585`、`:601` | `:1946`（环外标签用它）|
| 底纹浓度 | `track_alpha` | `:854-862` | `:285`、`:311`、`:411`、`:518` | 不读 |
| 调试日志 | `debug_log` | `:950-958` | 不读 | `:80-82` |
| 充电功率 | `perf_power` | `:861-868` | 不读 | `TrioHooks.java:3696`（`perfText`；是否挂载由 `TrioAppearance.java:340` 的 `perfAnywhere()` 决定；`Float.isNaN` 那一项挡住「答不出功率」） |
| 电池温度 | `perf_temp` | `:869-876` | 不读 | `TrioHooks.java:3715`（同上，两个读数共用一个视图） |
| 图标整体尺寸 | `glyph_scale_pct` | `:897-914` | `TrioRenderer.java:136`（`inkScale(w, h, rect) * appearance.glyphFit()`，只影响适配系数） | 不读（但 `TrioAppearance.glyphFit()` 是两侧共用的那个乘数定义；第十四轮起 `perfTextSize` 也把它折进 `icon`） |
| 读数整体尺寸 | `perf_scale_pct` | `:927-938` | 不读 | `TrioHooks.java:3797`（`perfTextSize` 里取 `a.perfFit()`，最后乘在每一条返回值上） |
| 读数字重 | `perf_weight` | `:939-950` | 不读 | `TrioHooks.java:3840`（`perfWidth`）与 `:4052`（`updatePerfLabel` 的 `setTypeface`）；`a.perfTypeWeight()`（`TrioAppearance.java:371`）既进 `perfLineHeight`，也进预留宽度的比较，还是第五路重同步分句 |

**第一个整体印象**（下面的分类是**第十轮之前的快照**，那之后 `signal_mode`、`stacked_signal`、`data_sim_only`、`out_signal_size` 等键加入，第十一轮又加了 5 个；这里保留原文的分类结构，只在末尾补上本轮新键的归属）：

- **两侧都读**：`show_wifi`、`show_mobile`、`show_value`、`show_bolt`、`mobile_type_mode`、`ring_stroke`、`arc_stroke`、`value_size`、`type_weight`，加上本轮加入的 `type_suffix_scale`（环内分段绘制、环外 `RelativeSizeSpan`）与 `out_signal_margin_dp`（`TrioHooks` 定位/占位 + `TrioPreviewView` 预览）。
- **只有渲染侧读**：`trio_style`、`swap_wifi_value`、`track_alpha`、`value_weight`、`type_size`、`role_colors`、六个 `color_*`、`low_threshold`（第十轮后的 `out_signal_size` 也属此类），以及第十四轮的 `glyph_scale_pct`（`TrioRenderer.drawInto` 把 `TrioAppearance.glyphFit()` 乘进适配系数；Hook 侧不读这个字段，`GlyphDrawable` 只是把同一个 `TrioRenderer.drawState` 交给 MIUI 的绘制路径，所以缩放仍只在这里应用一次）。
- **只有 Hook 侧读**：`enabled`、`out_type_size`、`debug_log`（`TrioConfig.debugLog()`，`TrioHooks.java:80-81`），加上本轮的两个环外标签边距 `out_type_margin_left_dp` / `out_type_margin_right_dp`，第十三轮的两个性能读数 `perf_power` / `perf_temp`（两个键共用同一个 `PerfLabel` 视图，渲染器与预览都不看它们），以及第十四轮的 `perf_scale_pct`（只在 `perfTextSize` 里当乘数用），与第十五轮的 `perf_weight`（`a.perfTypeWeight()` 进 `perfLineHeight`，另有一个第五路重同步分句看它；渲染器与预览都不读）。

`ring_stroke`/`arc_stroke`/`value_size` 在 Hook 侧只出现在 `TrioHooks.java:346-347` 的 debug 日志字符串里、不参与任何逻辑。**真正需要两个进程就同一份规则达成一致的** = 6 个结构开关（`enabled` 由 Hook 侧独占但语义上凌驾全部）加 `type_weight`（环外标签按它取字重，见 2.6）与 `type_suffix_scale`（两处必须给出同一个比例）。恰恰就是这几个，把复杂度撑起来了。

---

## 二、六个具体的「乱」及其证据

### 2.1 同一条规则被逐字写了 5 遍，加界面第 6 遍

「模块画闪电时，原生闪电必须让路」这条规则写成 `showBolt && showValue`，然后在两处根据所在侧补上各自的前缀（Hook 侧补 `enabled`，渲染侧补 `charging`）：

| 位置 | 原文 |
| --- | --- |
| `TrioRenderer.java:178`（圆环分支）| `final boolean bolt = charging && cfg.showBolt && cfg.showValue;` |
| `TrioRenderer.java:260`（矩形分支）| 同一行，逐字复制 |
| `TrioHooks.java:477`（`applyMeterText`）| `final boolean glyphBolt = cfg.enabled && cfg.showBolt && cfg.showValue;` |
| `TrioHooks.java:793`（`onBatteryStyleChanged`）| 同一行，逐字复制 |
| `TrioHooks.java:841`（`updateChargeAndText`）| `if (cfg.enabled && cfg.showBolt && cfg.showValue) {` |

第 6 处是设置页 `SettingsScreen.kt:693` 的门禁 `gated && settings.showValue`——它写的不是同一个表达式，但表达的正是同一个约定的另一半（这个开关只在数字开着时才有意义）。

五处代码里任何一处改了、别处没跟上，就会出现「设置页说能开、渲染不画」或者「渲染画了、原生也没让路，两个闪电叠在一起」。`TrioHooks.java:790-792` 的注释甚至反过来引用 `TrioRenderer` 来说明自己为什么要这么写（`// which per TrioRenderer is showBolt && showValue`）——这本身就是耦合信号：一处规则要靠注释去提醒另一处同步。

顺带指出 `TrioRenderer.java:178` 与 `:260` 是同一表达式的**两次复制**（圆环与矩形各一份），而不是抽成一个函数再调用两次。

### 2.2 「数字居中」有两个来源，只有一个开关

`TrioRenderer.java:190`：

```java
final boolean valueInCentre = hasValue && (centred || (!wifi && !typeInRing));
```

`centred` 是用户的 `valueCentred` 开关，而 `(!wifi && !typeInRing)` 意味着**没有 Wi-Fi 弧、也没有环内网络类型时，数字自己就跑到圆心了——和「电量数字居中」这个开关毫无关系**。

结果就是设置页 `:733` 的文案（`value_centre_summary`「电量数字放大到圆环中间并始终优先占据圆心」）对用户描述的是一件由开关控制的事，用户实际看到的却是「关掉 Wi-Fi 后数字也会跳中间」。默认外观（圆环、无网络类型）本身就长期处于这个隐式居中状态，反而让这个开关看起来「开了没变化」。

### 2.3 「三合一样式」选了矩形之后，一批设置默默换了含义或直接失效

`TrioRenderer.java:243-246` 自己写得很清楚：

> the bar takes its thickness from `ringStroke`, the arcs still take their own, and `valueCentred` is ignored outright

对照 `TrioSettings.java:40-47` 关于 `trioStyle` 的文档断言——两种排布共享其余全部设置，只有几何与绘制顺序不同——**这两段话互相矛盾**。实际情况是：

| 设置 | 圆环下 | 矩形下 |
| --- | --- | --- |
| `valueCentred` | 完整语义（数字进圆心、弧进缺口） | **完全不读**（`drawRectLayout` 248-274 无任何查询）|
| `ring_stroke`（文案「环线粗细 / 电池圆环的描边宽度」）| 环描边宽度 `:409` | 变成电量条厚度 + 上半圆角半径 `:307` |
| `arc_stroke`（文案「Wi-Fi 弧线的描边宽度」）| 弧线描边 `:497` | 弧线描边**加上**整组 Wi-Fi 的 y 位移 `:337` |
| `type_size` | 环内 120×120 设计空间（`RECT_TYPE_SCALE` 不参与）| 乘 `RECT_TYPE_SCALE = 1.26` `:360` |
| `value_size` | 原值 `:561` | 乘 `RECT_VALUE_SCALE = 1.38` `:371` |
| 顶端槽 | 数字/类型/弧可并存 | 三选一 else-if，bolts > Wi-Fi > 类型 `:263-269` |

设置页 `:733` 的 `value_centred` 行**没有 trioStyle 门禁**（只查 `gated && showWifi && showValue`），而 `geometryTab`（`:741-865`）在注释 `:794-799` 里明确声明「不做 trioStyle 分支」。于是**矩形样式下，设置页会出现一个完全没有任何效果的开关，和两个文案与行为不符的滑块**。

### 2.4 门禁的表达方式不统一，而且原因看不见

33 处 `enabled =` 里，20 个可写项（**改造前的数字**，第十一轮后为 52 / 25）的依赖关系有 5 种：仅总开关（14 个）、`showValue`（2 个）、`mobileTypeMode`（3 个）、`roleColors`（4 个）、`showWifi+showValue`（1 个），另有 1 个无门禁（调试日志）。

被禁用的原因**只能长按 Tooltip 看到**（`gateHint` `:1668-1672` → `TooltipBox`），行本身是灰的、没有任何文字说明。而且 `gateHint` 只报第一个未满足的依赖，`show_mobile_type_title`（「网络类型」）这个标题被三处不同含义的缺依赖提示反复借用（`:803`、`:821`、`:840`）。

`enabledCount`（`:1779-1787`）统计的 5 项里不含 `enabled`、`roleColors`、`valueCentred`、`trioStyle`，所以总开关关掉之后，关于页仍然会显示「已开启显示项 5」——**这个数字和「实际画了几个东西」不是一回事**。

### 2.5 尺寸页把 8 个互不相干的滑块平铺在一张卡里

`geometryTab`（`:741-865`）没有 `SectionTitle`（全仓库唯一的组标题是常规页的 `group_appearance`，`SettingsScreen.kt:623`），8 个滑块一个 Card：

1. 环线粗细 ← 只在圆环样式有意义
2. 弧线粗细 ← 两种样式都有意义但语义不同
3. 数字字号 ← 数字关掉就没用
4. 数字字重 ← 数字关掉就没用
5. 圆环内类型字号 ← 只在 `mobileTypeMode == IN_RING` 有意义
6. 环外字号 ← 只在 `mobileTypeMode == OUT_RING` 有意义
7. 网络类型字重 ← 只在 `mobileTypeMode != 0` 有意义
8. 底纹浓度 ← 总是有意义

也就是说 8 行里只有 1 行是「无条件有效」的，其余 7 行各自挂在一个用户可能还没做的选择上，但它们在视觉上是平级的、顺序也不反映任何分组。

### 2.6 预览盖不住所有东西，写入路径有三条

预览（`PreviewCard` `:1163-1196`）走 `TrioPreviewView` → `TrioRenderer.drawInto`，和状态栏共用同一份渲染代码，这点是好的。但它有盲区：**环外网络类型不是 `TrioRenderer` 画的**，而是 `TrioHooks` 挂的一个 `OutTypeLabel` TextView（`TrioHooks.java:1697-1704`）。所以 `out_type_size` 这个滑块在设置页里**永远没有任何可见反馈**，用户只能切回状态栏看。（**这条盲区已在第 6.4 / 6.9 节补上**：预览末两格分别画环外标签与环外读数，第 7 格在第十一轮起用 `"5GA"` 示例，于是 `out_type_size`、`out_type_margin_*`、`type_suffix_scale`、`out_signal_size`、`out_signal_margin_dp` 都有可见反馈。）

写入路径有三条而不是一条：标准 `writeBoolean`/`writeInt`（`SettingsRepository.kt:147-157`）、手写的 `resetRoleColors`（`:66-84`，6 个键 commit 一遍再统一 push + 广播）、以及整表推送的 `syncAllToFramework`（`:111-145`，服务绑定后把本地全部值推给框架）。三条都必须记得「push → notifyModule」，而 `notifyModule`（`:173-180`）的载荷恒为全量按键打包。

顺带两个更小的坑：`strings.xml:55` 的 `color_preview`（「预览」）零引用，是死资源；`Prefs.java:85` 的 `KEY_VALUE_CENTRED = "swap_wifi_value"` 键名与含义永久不符（这个**必须保持不动**，注释 `:78-84` 已经说明改名会静默重置老用户的选择）。

---

## 三、优化方案

总的方向：**把「开关之间的规则」从散落在 6 个文件里的 if 组合，收敛成一个可以被两侧共用的、有名字的纯函数；再让界面从同一份声明驱动。**

分三级落地，P0 是不改行为、只收敛和修正的；P1 会动到键和界面结构；P2 是补齐性工作。

### P0-1（核心）：抽出「有效外观」纯函数 `TrioAppearance`

在 `TrioSettings` 旁边加一个不可变的值对象和唯一一个构造函数：

```java
final class TrioAppearance {
    final boolean drawGlyph;        // enabled
    final boolean wifi, mobile;     // showWifi / showMobile
    final boolean bolt;             // enabled && showBolt && showValue
    final boolean value;            // showValue
    final boolean valueCentred;     // enabled && showValue && valueCentred && style == RING
    final boolean typeInRing;       // enabled && mode == IN_RING
    final boolean typeOutOfRing;    // enabled && mode == OUT_RING
    final boolean roleColors;
    final int     ringStroke, barStroke, arcStroke, trackAlpha, lowThreshold;
    final float   valueSize, valueWeight, typeSize, typeWeight, outTypeSize;
    final int     trioStyle;
}

static TrioAppearance appearance(TrioSettings cfg) { /* 规则只在这里写一次 */ }
```

收益：

- 5 处逐字相同的 `enabled && showBolt && showValue` → 1 处。
- **`valueCentred` 在矩形下自动为 `false`**（把 `trioStyle != RING` 写进规则），渲染侧不必再「记得忽略」，`TrioSettings.java:40-47` 与 `TrioRenderer.java:243-246` 的那处文档矛盾也随之消失。
- `barStroke` 与 `ringStroke` 在同一步拆成两个字段（默认相等），为 P1 的独立键留好位置。
- `TrioHooks.java:1050`（settle 短路）、`:721`（字形绘制）、`:1723`（环外标签）统一读 `a.drawGlyph` / `a.typeOutOfRing`。
- `TrioRenderer.drawRingLayout` / `drawRectLayout` 里那些逐字重复的谓词消失，两条分支的公共前缀收敛到一处。

关键点：**这个函数是纯函数、不依赖 Android，因此 `TrioHooks`（SystemUI 进程）和设置页都能调**——设置页就能用同一份规则算「这一行现在到底有没有效果」。

### P0-2：设置页按规则驱动 `enabled`，不再手写条件

有了 `appearance()`，设置页每一行的门禁不再是散落的表达式，而是查表：

- 有 `appearance` 就直接用（`show_bolt` ← `a.bolt` 的依赖项、`value_centred` ← `a.valueCentred` 是否可能为真）。
- 「这一行在当前样式/模式下有没有效果」用一个显式枚举表达：`Scope.ALWAYS / RING_ONLY / RECT_ONLY / TYPE_IN_RING / TYPE_OUT_RING / TYPE_ANY`。

顺带修掉 `enabledCount`（`:1779-1787`）——改成统计 `appearance()` 里为真的绘制项，或者干脆把它挪到常规页顶部、只统计「画了什么」。

### P0-3：几何页加分组标题（不改任何行为）

`geometryTab`（`:741-865`）拆成同页多张 Card，每张一个 `SectionTitle`：

- **圆环**（`Scope.RING_ONLY`）：环线粗细、弧线粗细
- **矩形**（`Scope.RECT_ONLY`）：电量条粗细、Wi-Fi 弧粗细
- **文字**：数字字号、数字字重、网络类型字重
- **网络类型**：圆环内字号（`TYPE_IN_RING`）、环外字号（`TYPE_OUT_RING`）
- **底纹**：底纹浓度

这一步是纯界面结构改动，风险最低，收益最直观（用户能看出「这些只对圆环生效」）。

### P0-4：三个文案/死资源修正

- `strings.xml:61` `stroke_summary`「电池圆环的描边宽度」→ 按样式取两条：圆环下「电池圆环的描边宽度」，矩形下「底部电量条的粗细」。
- `strings.xml:64` `arc_stroke_summary`「Wi-Fi 弧线的描边宽度」→ 补上矩形下同时决定 Wi-Fi 弧纵向位置（或随 P1 拆键后自然消失）。
- `strings.xml:75` `out_type_size_title`「环外字号」→「环外网络类型字号」。
- 删 `strings.xml:55` `color_preview`（零引用）。

英文侧对应行号相同：`values-en/strings.xml:61`（`Stroke width of the battery ring.`）、`:64`（`Stroke width of the Wi-Fi arcs.`）、`:75`（`Out-of-ring size`）、`:55`（`Preview`），需要同步改。

### P1-1：键元数据表（唯一声明，四处派生）

在 `Prefs.java` 里加一张声明表，把现在散在四个地方的信息合并：

```java
record KeyDef(String key, Kind kind, int def, int min, int max,
              Gate gate, Scope scope, int titleRes) {}
```

由这张表派生：

1. `TrioSettings.from(SharedPreferences)` / `fromBundle` 的 clamp（今天 `TrioSettings.java:118-168` 手写 19 次）。
2. `applyKeyFrom`（`TrioSettings.java:260-292`）的 25 分支 switch → 表驱动（键名 → 字段的映射仍要显式写，但类型/范围不再重复）。
3. 设置页的行、门禁与分组（`Gate`/`Scope` 直接变成 Compose 的 `enabled`）。
4. 文档里的配置表可以写一个小校验脚本对着表核。

`Gate` 用枚举而不是自由表达式，例如 `Gate.NONE / SHOW_VALUE / SHOW_WIFI_AND_VALUE / TYPE_IN_RING / TYPE_OUT_RING / TYPE_ANY / ROLE_COLORS` —— 这样 `gateHint` 要报的那一行标题也就有出处了（消除 `show_mobile_type_title` 三处借用）。`Scope` 是另一个正交维度（`ALWAYS / RING_ONLY / RECT_ONLY`），它不决定行能不能点，只决定「点了在当前样式下有没有效果」。

### P1-2：把「为什么点不动」搬到行内

现在只有长按 Tooltip。建议在 `summary` 下面加一行小字或一个依赖徽标：

```
数字字号
电量百分比文字的尺寸。
需要先打开「显示电量数字」        ← 灰色小字，与 summary 同色阶但更淡
```

实现上把 `gateHint` 的结果从 `TooltipBox(text=...)` 改成同时作为一段行内文本传入，Tooltip 保留（两条路并存不冲突）。这一改动不涉及任何渲染逻辑。

### P1-3：矩形电量条粗细拆出独立键

按 2.3 的表，`ring_stroke` 在矩形下已经承担了另一件事。新增 `bar_stroke`（`Scope.RECT_ONLY`），**缺键时回落 `ring_stroke`**，这样老安装升级后矩形外观不变；用户一旦动过这个新滑块，两条样式就各自独立了。**必须遵守仓库既有的迁移规矩**：`Prefs.java:8-13` 说明框架不做类型转换、每个键终生一种类型；`KEY_MOBILE_TYPE_MODE` 的迁移就是先例（`TrioSettings.java:180-208` 读新 int 键、缺失则回落旧 boolean 键）。

同一批可以视情况给矩形拆 `rect_arc_offset`，但收益不如 `bar_stroke` 直接，可以推迟。

### P2-1：预览补齐环外类型（**已落地**，见 6.4 / 6.9）

`out_type_size` 在今天没有任何可见反馈（见 2.6）。最小做法：在预览末尾加第 7 格，画「电池环 + 右侧一个 5G 文本」，字号取 `outTypeSize`、字重取 `typeWeight`。**关键约束**：它必须在预览里用与 `TrioHooks.updateOutTypeLabel`（`:1936-1948`）相同的取值口径（字号 `outTypeSize`、字重 `typeWeight`、PX 而非 SP），否则预览又开始骗人。实际落地时第 7 格**不画电池环**、只画那个环外标签，并在第十一轮把示例类型改为 `"5GA"`。

### P2-2：`resetAllDefaults` 与写入路径收敛

- 加一个与 `resetRoleColors`（`SettingsRepository.kt:66-84`）对称的「恢复全部默认」。
- `resetRoleColors` 改走统一写路径。
- 中期把 20 个 setter 收敛成 `fun writeInt/writeBoolean/writeString(key, value)` + 由 P1-1 的表生成的薄封装，消除「新增一个设置要改 10 处」的成本：`Prefs`（键、默认、上下限）、`TrioSettings`（字段、`defaults`、`from`、`fromBundle`、`toBundle`、`copy`、`applyKeyFrom`）、`SettingsRepository`（setter）、`SettingsScreen`（行）。

### P2-3：文档同步

`README.md:82-141` 的设置表、`docs/DEVELOPMENT.md:659-684` 的配置项参考、`:686-703` 的已知限制都要跟着改；`TrioSettings.java:40-47` 关于「两种排布共享其余全部设置」的断言必须重写（它和 `TrioRenderer.java:243-246` 直接冲突）。

---

## 四、建议的落地顺序与验收点

| 批次 | 内容 | 风险 | 验收 |
| --- | --- | --- | --- |
| 1 | P0-1 抽 `appearance()`，替换 5 处重复谓词 | 低（纯重构） | 圆环/矩形 × 闪电开/关 四种组合的绘制结果与改前逐像素一致 |
| 2 | P0-4 文案与死资源 | 极低 | 编译通过、资源无未引用 |
| 3 | P0-3 几何页分组 + P0-2 门禁改由 `appearance` 驱动 | 低 | 每行的 enabled 与改前逐一对照，只允许「矩形下 value_centred 变不可用」这一处有意的行为变化 |
| 4 | P1-2 行内原因 | 低 | 灰显行能看到原因 |
| 5 | P1-1 键元数据表 | 中 | clamp 边界逐个复核（尤其 `out_type_size` 16–64）|
| 6 | P1-3 `bar_stroke` + 迁移 | 中 | 老安装升级后矩形外观不变 |
| 7 | P2 预览/写入/文档 | 中 | 预览与状态栏对照；全量设置改动后状态栏一致 |

**注意**：仓库没有 Android 侧自动化测试（`app/src` 下只有 `main`；`tests/` 下后来补了五个纯 JVM 断言脚本，见 `docs/DEVELOPMENT.md` 「测试」一节）。P0-1 这种「行为必须完全不变」的重构，最划算的投入是先补一个纯 JVM 单测（`app/src/test`）来锁 `appearance()` 的真值表——它不依赖 Android，写起来最便宜，而且正好是这个重构的核心。

---

## 五、一句话总结

乱的根源不是开关太多——39 个键里有 24 个只归一侧所有（`glyph_scale_pct` 只有渲染侧读、`perf_scale_pct` 与 `perf_weight` 只有 Hook 侧读），归属本身是清楚的；乱的是**那几个跨进程的结构开关：它们的组合规则被逐字复制在 5 个地方，而且其中一条规则（矩形样式）与文档声明相矛盾、另一条规则（无 Wi-Fi 时数字自动居中）根本没有开关**。先把规则收敛成一个 `appearance()` 纯函数，界面分组与文案修正就没有阻力了；这块收敛同时也让「加一个设置要改 10 处」变成「改 4 处」。

---

## 六、落地结果（改造已完成，本节为事后回填）

改造按第三、四节的方案执行完毕，批次 1–3 与 P2-1 已落地，P1-1/P1-2/P1-3、P2-2 未做（理由见下）。这一节记录**实际做成了什么**，上面几节的旧行号随之作废。

### 6.1 新增的文件与核心类型

`app/src/main/java/io/github/yixing233/hyperduo/TrioAppearance.java`（新文件，296 行）是全场唯一的外观规则源。它刻意**不含任何 Android 类型、不含静态状态**，所以设置页进程与 SystemUI 进程都能构造同一份。

- 公开 final 字段（`:31-81`，后又加 `signalMode`/`signalInRing`/`signalOutOfRing`/`stackedSignal`/`dataSimOnly`）：`style`、`rect`、`glyph`（= `enabled`）、`wifi`、`mobile`、`dualSim`、`value`、`boltWanted`、`centreValue`、`typeMode`、`typeInRing`、`typeOutOfRing`、`roleColors`、六个颜色、`lowThreshold`、`ringStroke`、`arcStroke`、`trackAlpha`、`valueSize`、`valueWeight`、`typeSize`、`outTypeSize`、`typeWeight`。
- 入口 `TrioAppearance.of(TrioSettings)`（`:116-119`，`null` → 默认值）；`TrioConfig.appearance()` 把它包了一层，避免调用点各自手抄规则。
- 两个派生谓词：`drawsBolt() = boltWanted && value`、`hidesNativeBolt() = glyph && drawsBolt()`（`:132-142`）——前者**不含** `glyph`，因为设置页预览在总开关关闭时仍要渲染所选样式；后者才是 Hook 侧「原生闪电该不该让路」的判据。后续新增的三个谓词沿用同一套写法：`signalDots() = mobile && signalInRing`、`stackedOut() = glyph && mobile && signalOutOfRing && stackedSignal`、`foldsMobile() = signalDots() || stackedOut()`（`:201-231`）。
- `typeAnywhere()`（`:172-174`）、`wifiInk(int)`（`:145-147`）、`dualSimRows(boolean wifiInk, boolean charging, int sims)`（`:167-169`）。
- 两个内嵌布局决策对象：`Ring`（`:199-250`）与 `Rect`（`:266-295`），把「哪个元素占圆心、哪个进缺口」这类槽位仲裁从渲染器里搬了出来。渲染器现在只读它们的最终布尔值。

### 6.2 与本文提案的三处偏离（重要）

1. **命名**：提案里叫 `appearance()`/`drawGlyph`/`bolt`，落地为 `TrioAppearance.of(...)`/`glyph`/`boltWanted` + `drawsBolt()`。语义相同，`boltWanted` 特意与 `drawsBolt()` 区分开：前者是「用户想不想在充电时看闪电」，后者是「这一帧到底画不画」。
2. **P1-3 的 `bar_stroke` 新键被弃用**。提案想给矩形电量条拆一个独立厚度键；实际做法是**让它继续读 `ringStroke`**，改由设置页在矩形样式下把这一行改称「电量条粗细」（`stroke_title_rect` / `stroke_summary_rect`）。理由是加第二个键只会多出一个在另一种样式下完全惰性的滑块，而用户真正需要的是「知道这一行在这里管什么」，不是再多一个正交旋钮。这条决定写在 `TrioRenderer.drawRectBatteryBar` 的 javadoc（`TrioRenderer.java:300-303`）里。
3. **P0-2 的 `Scope` 枚举没有引入**。提案打算用 `Scope.ALWAYS / RING_ONLY / ...` 显式表达「这一行在当前样式下有没有效果」；实际是让每个 tab 在 `Card` 开头取 `val a = TrioAppearance.of(settings)`，然后**直接写 `a.*` 表达式**当 `enabled`。少一层间接，规则仍然只有一处真源；代价是个别行的条件看起来长一些（例如 value_centred 那行）。

### 6.3 设置页改造结果（`app/src/main/java/io/github/yixing233/hyperduo/ui/SettingsScreen.kt`）

- **签名变化**：`generalTab` / `geometryTab` / `colorsTab` 三个 tab 的函数都**去掉了 `gated` 参数**，改为自己从 `settings` 推出 `val a = TrioAppearance.of(settings)`。这正是「规则只有一处真源」的直接体现：不再把总开关当作一个横传的布尔值往下发。四个 `entry<Route.*>` 调用点同步不再传 `gated`。
- **三个 tab 的全部门禁改为 `a.*`**，文件里 `gated` 一词只剩五处英文注释里的普通用词（`SettingsScreen.kt:375`、`:642`、`:676`、`:726`、`:1746`），**零处代码**——`gated` 这个参数名随之从这个文件里消失。
- `generalTab`：master Switch 恒可点（不加 `enabled`）；trio_style 下拉 `enabled = a.glyph` / `selectedIndex = a.style`；showWifi、showMobile、showValue、typeModes 下拉、signal_mode 下拉均只门禁 `a.glyph`；双卡行 `enabled = a.glyph && a.mobile`；stacked_signal 行 `enabled = a.glyph && a.signalOutOfRing`；data_sim_only 行 `enabled = a.glyph && a.signalOutOfRing && a.stackedSignal`；showBolt 行 `enabled = a.glyph && a.value`，其提示链用 `a.value to show_value_title`。（**此处曾在落地时写成 `a.glyph && a.drawsBolt()` 并造成自锁，见 6.7。**）
- `geometryTab`：拆成四组、同页多张 Card，每组一个 `SectionTitle`——`group_stroke`（环线/弧线粗细）、`group_text`（数字字号/字重）、`group_type`（环内类型字号/环外字号/环外信号大小/类型字重）、`group_track`（底纹浓度）。落地的分组与提案 P0-3 的**五项并不同**：提案里的「圆环」「矩形」两组没有采用，因为两种样式共用同一批滑块、并不存在只对矩形生效的滑块（正是 6.2 第 2 条把 `bar_stroke` 砍掉的结果），把它们拆成两张卡只会让人以为有两套尺寸。改为按**量纲**分组（描边 / 文字 / 网络类型 / 底纹）。**所有尺寸项归同一张卡**，所以「环外信号大小」也落在 `group_type`（第十轮补，见 6.8），而不是与 `stacked_signal` 两个开关同卡。
- 矩形样式下的文案切换：`stroke_title` → `stroke_title_rect`「电量条粗细」、`stroke_summary` → `stroke_summary_rect`、`arc_stroke_summary` → `arc_stroke_summary_rect`（补上「同时决定这组弧线在顶部纵向位置」），全部由 `if (a.rect)` 选择。这兑现了 `TrioRenderer.drawRectBatteryBar` javadoc 里「设置页会把它改称 bar thickness」的承诺。
- `colorsTab`：roleColors 开关 `enabled = a.glyph`；低电量阈值、六行颜色、颜色页「恢复默认」全部 `enabled = a.glyph && a.roleColors`。
- **`enabledCount` 重写**（关于页「已开启显示项」）：旧实现统计 6 个原始布尔、不含总开关，于是总开关关掉后它仍然报出「5」，与「实际画了几个东西」不是一回事。新实现从规则派生：`if (!a.glyph) return 0; listOf(a.wifi, a.mobile, a.dualSim && a.signalDots(), a.value, a.drawsBolt(), a.typeAnywhere()).count { it }`。第二轮改动把第二排那一项由 `a.dualSim && a.mobile` 改成 `a.dualSim && a.signalDots()` —— 双卡两排**只在环内存在**，环外底部什么都不画，所以环外时它不该算第二个东西；`a.mobile` 本身仍计入，因为环外堆叠时读数由 `OutSignalView` 画出来，只是换了位置。
- 门禁提示（`gateHint`）机制不变，但**仅被总开关拦下的行刻意不给提示**——总开关就在同一屏上，提示是噪音。这条规则与 `docs/DEVELOPMENT.md:295` 一致。

### 6.4 预览的两格「补上可见反馈」

2.6 指出的盲区（`out_type_size` 在设置页永远没有反馈）已按 P2-1 补齐，但落地的形态与提案的「电池环 + 右侧 5G 文本」不同：**新格单独占一格，并且不画电池环，只画那个环外标签**。

- `TrioPreviewView`（`app/src/main/java/io/github/yixing233/hyperduo/TrioPreviewView.java`）新增 `outTypeOnly` 开关与 `drawOutTypeLabel(...)`；`HOST_ICON_HEIGHT_DP = 20f` 对应宿主 `status_bar_icon_height`，因为环外标签的尺寸只有相对这个 20dp 图标盒才有意义（宿主画布详见 `docs/DEVELOPMENT.md:502-503`）。
- **口径必须与 `TrioHooks.updateOutTypeLabel` 完全一致**：字号 `outTypeSize`、字重 `typeWeight`、**px 而非 sp**；再把宿主 20dp 盒按预览自身高度等比换算。提案里写的「在预览里画电池环再配文本」会引入渲染器与 Hook 两套口径混用的风险，这是刻意避开的。
- `TrioRenderer` 为此抽出了 `inkScale(int w, int h, boolean rect)`（转发到私有四参重载），让预览拿到与 `drawInto` 逐位相同的缩放系数；这个系数随样式而变，正是预览不能自己算的原因。
- `PREVIEW_SIZE` 由 `52.dp` 缩到 `46.dp` 以容纳 7 格；文案 `preview_out_type`「环外类型」/「Out type」两语言各一条。
- 收尾时补了一处宽字串处理：状态栏里标签是 wrap-content、宽了向右伸不会被裁，预览格却是正方形，`"5GA"` 会顶边。`drawOutTypeLabel` 因此只在**文本宽度超出格宽**时用 `setTextScaleX` 做水平压缩，不动字号 —— `outTypeSize` 设定的是高度，那一维必须精确。
- **第二轮再加第 8 格**「环外信号」（`preview_out_signal`）：环外时 `glyph` 里没有任何移动信号墨迹（这正是环外 + 堆叠关要交还系统的那一格），若没有这一格，`signal_mode`、`stacked_signal`、`data_sim_only` 三个开关在设置页就没有任何可见反馈。模式与第 7 格同构：`setOutSignalOnly(true)` → `onDraw` 里 `drawOutSignal(...)`，按 `HOST_ICON_HEIGHT_DP × density` 算出宿主高度、再按预览格缩放，所以两个环外格与状态栏保持同一比例。`dataSlot` 故意传 `-1`：哪个 SIM 是上网卡是设置页从未采样过的运行时状态，猜一个会把开关效果画在错卡上；传 `-1` 时 `dataSimOnly` 退回第一张有读数的卡。
- 八格一行需要 `PREVIEW_SIZE` 从 `46.dp` 再降到 `39.dp`（8×39 + 7×4 = 340dp < 344dp 内容宽）。降得动是因为不是 glyph 的那两格没有需要保住的固定纵横比。

### 6.5 未做的部分及理由

- **P1-1 键元数据表**：收益是真金白银（clamp 等 19 处手写、`applyKeyFrom` 的 25 分支 switch），但它会把 `Prefs`、`TrioSettings`、`SettingsRepository`、`SettingsScreen` 四层同时翻掉，且 Android 侧没有自动化测试兜底。改造当时的目标是「让用户更快调到想要的样式」，这一步属于开发者侧收益，风险与收益不成比例，缓做。
- **P1-2 行内原因**：与 `gateHint` 的 Tooltip 机制不冲突，是一段独立的展示改动；当前 Tooltip 已能表达原因，行内小字属于体验加分项而非功能缺失。
- **P2-2 写入路径收敛**：同上，属重构而非本次目标。

### 6.6 验证方式

仓库**没有 Android 侧自动化测试**（`app/src` 下只有 `main`），但 `tests/` 下已有四个纯 JVM 断言脚本
（`wifi-presence.ps1`、`battery-presence.ps1`、`perf-layout.ps1`、`glyph-scale.ps1`，见 `docs/DEVELOPMENT.md` 「测试」一节第 3 条），所以验证分两层：

1. **构建**：`gradle -p C:\code\HyperDuo :app:assembleDebug --offline -q` 通过。
2. **五条离线工装**（不入库，`work\` 下）：`gapcheck`（电量弧算术）、`slotcheck`（居中槽位优先级，用「非居中布局逐像素不变」当不变量）、`dualsimcheck`（双卡判定规则，故意把 `charging` 换回 `bolt` 造一份反例构建）、`simcheck`（订阅号↔槽位映射、单排兜底、**以及读的是 MIUI 电平而不是 AOSP 电平**；三构建对照）、`outringcheck`（环内/环外与两个新开关的真值表 + 环内逐像素不变）。五条**全部 `exit 0`**，即固定版通过、反例版按预期失败。改动渲染器或 `TrioAppearance` 后这些都要重跑，机制记录在 `docs\DEVELOPMENT.md:805-862` 与「测试」一节。
3. **与参考图的数值化比对**（`work\outringcheck\OutSignalShot` + `compare.py`，同样不入库）：`OutRingProbe` 只能证明源码自洽（两边引用同一批常量，同时错也通过），所以另有出图工装把环外读数按**参考图 1 单位 = 1 像素**画出来，再由 `compare.py` 用**同一套量法分别量参考图与渲染结果**，逐项比列数、列距、柱宽、四根柱高、点行直径与柱底到点行的空带。它当场抓到 `STACK_DOT_GAP` 原写 10、实为 **9**（参考图无抗锯齿：柱底末行墨 237 即下边缘 238，点行首行墨 247，空带 `238..246`），`STACK_INK_H` 因此由 210 修为 209。

**唯一一处有意的行为变化**：矩形样式下「电量数字居中」由「静默无效」改为**不可用并给出提示**（`enabled = a.glyph && !a.rect && a.wifi && a.value`，提示链含 `!a.rect to trio_style_ring`）。这是 2.3 那条矛盾的正解：以前矩形下这个开关点得动但什么也不发生，现在它明说自己是圆环专属。

### 6.7 上机后由用户发现的四个问题

落地版装到真机（houji / 小米 14，Android 17）之后，用户报了四个问题，都是这次改造自己引入的，记录如下。

**一、「三合一样式」摘要里出现了「或参考图那样的矩形」（用户原话：怎么现在会写一个"或参考图那样的矩形"？？？？？能不能好好写了）**

- 根因：**「参考图」是设计对齐术语**，只在 `docs/DEVELOPMENT.md` 里跟 MIUI 参考截图逐项比对时才有意义（`docs/DEVELOPMENT.md:388-389`、`:477-478`、`:685`、`:928`、`:1139`、`:1202`）。它被写进了用户可见的摘要，而设置页里根本没有那张图，读起来就像一个没写完的占位符。
- 全仓库扫描后确认**只有这一处用户可见字符串**犯这个毛病（`app/src/main/res/values/strings.xml:21`）。
- 改为照实描述实码行为（依据 `app/src/main/java/io/github/yixing233/hyperduo/TrioRenderer.java:218-224` 的 javadoc、`:232-257 drawRectLayout`、`:303 drawRectBatteryBar`）：
  - `values/strings.xml:21`：「选择三合一的排布方式。圆环是默认样式；矩形改用左右两列信号点夹住中间内容，底部横贯一条电量条。」
  - `values-en/strings.xml:21`：`Which arrangement to draw. The ring is the default; the rectangular one puts a column of signal dots on each side of the centre and a battery bar across the bottom.`
- **教训**：文档里的设计对齐词汇（参考图、提案编号、一期/二期）不得进入 `strings.xml`。

**二、「充电时显示闪电」关掉后无法再打开（用户原话：为何现在的充电时显示闪电的条目无法开启了）**

- 根因：`SettingsScreen.kt` 里该行落成了 `enabled = a.glyph && a.drawsBolt()`，而 `TrioAppearance.drawsBolt()` 是 `boltWanted && value` —— **它折入了这一行自己的开关值**。于是关掉该行 → `drawsBolt()` 为 false → 该行被禁用 → 永远回不来。改造前它挂的是外部开关 `showValue`，本来是对的；落地时还配了一段注释把错误合理化（「行的门禁应跟随规则而非复述规则」），这段注释也一并删掉。
- 修复：提示链 `gateHint(a.glyph to master_title, a.value to show_value_title)`、`enabled = a.glyph && a.value`。
- **规则（写进注释）**：一行可以被**别的**开关门禁，但**永远不能被自己门禁**。推导门禁时若用的是把本行开关值折进去的判定函数（如 `drawsBolt()`），就会自锁；应当回溯到它所依赖的**上游**开关（这里是「显示电量数字」）。
- 全行审计：闪电行是**唯一**的自锁。其余门禁都挂在别的开关上——showWifi/showMobile/roleColors = `a.glyph`；双卡 = `a.glyph && a.mobile`（本行是 `dualSim`）；居中 = `a.glyph && !a.rect && a.wifi && a.value`（本行是 `valueCentred`）；字重 = `a.glyph && a.value`；typeSize/outTypeSize/typeWeight = `a.typeInRing`/`a.typeOutOfRing`/`a.typeAnywhere()`（本行分别是这三个网络的类型项，`typeAnywhere` 是它们的或，故不会自锁：关掉全部三个才会禁用本行，而那正是「三个都关」的状态，不是自锁）。
- **上机证据**（`<DEVICE_LAN_IP>:<ADB_PORT>`）：`uiautomator dump` 取到该行开关 `bounds="[963,1756][1110,1900]"`，节点 `enabled="true" clickable="true"`。`adb shell su -c "input tap 1036 1828"` 之后：
  - `show_bolt` 由 `true` 变 `false`；
  - 该节点变为 `checked="false"` 而**仍是 `clickable="true" enabled="true"`** —— 关闭后这一行依然可用，自锁消除。旧门禁下这里会是 `clickable="false" enabled="false"`。
- **取证陷阱**：不能用「预置 `show_bolt=false` 再启动 app」来验证——`SettingsRepository` 会在启动时全量同步并把 pref 回写成 `true`。必须走 UI 点击。本条与本改造同属「验证方法」教训。

**三、双卡时上下两排各少一格（用户原话：为啥 我单卡的时候底部那个信号是满格的,但是双卡显示的时候上下两个都是少一格信号啊?啥bug）**

- 根因：双排的每卡电平取自 `TrioState.levelOf` 的 `SignalStrength.getLevel()`（**AOSP 口径**），而 MIUI 状态栏选 `stat_sys_signal_N` 用的是 `getMiuiLevel()`（**MIUI 扩展**）。实机 `dumpsys telephony.registry` 一行里两个字段并存且不等：Xiaomi 14 / 5G NR 下两张卡都是 **`miuiLevel = 4`、`level = 3`**，系统画四格满格，双排照 AOSP 取就矮一格。单卡时那一排走的是图标链兜底（直接拿系统图标已解析的档位），所以看着是满格——两条路径口径不一致才是这个 bug 的形状。
- jadx 佐证 MIUI 自己的取法：`work\jadx-out\sources\com\android\systemui\statusbar\connectivity\MobileSignalController.java:495` = `miuiLevel = signalStrength2.getMiuiLevel();`（`:504` `mobileState2.level = miuiLevel;`）。
- 修复：`getMiuiLevel()` 不在公开 SDK（SDK 37 的 `android.jar` 里 `android.telephony.SignalStrength` 只有 `public int getLevel();`），所以新增 `TrioState.miuiLevel(SignalStrength)` 用 `Refl.callByName(strength, "getMiuiLevel")` 反射取，非 `Number` 才退回 `strength.getLevel()`；0..4 之外的拒绝规则不变。
- 防回归：`work\simcheck\verify.ps1` 升为**三构建**，第三个反例把 `final int level = miuiLevel(strength);` 换回 `strength.getLevel();`。固定版 `exit 0`（47 条），该反例 `exit 1` 且**恰好 4 条**不符（正是那 4 条 MIUI 断言）。
- **教训**：凡是「跟着系统状态栏读数」的地方，必须抄**系统实际用的那套口径**，不能照公开 SDK 的等价方法想当然——MIUI 有大量 `getMiuiXxx()` 扩展，公开 API 只是它的子集。

**四、环外信号类型的字体颜色跟随深浅色不及时（用户原话：目前我们自行绘制的环外信号类型，像 5G 的字体颜色，似乎有跟随状态栏文本变色的逻辑……但是它的更新不是很及时）**

- 根因：环外两个自建视图（`OutTypeLabel` / `OutSignalView`）的前景色都读 `TrioState.foreground()`，但**没有任何东西会在深浅色变化时重绘它们**。MIUI 的深浅色流程必然 invalidate 电池图标视图（`MiuiBatteryMeterView.updateLightDarkTint` → `:1189 onDarkChangeInternal()` → `MiuiBatteryMeterIconView.java:504-505`），所以 `hyperduo-draw` 必然重跑；但那条链只给自己重新着色，两个环外视图是 glyph host 的**兄弟**，`MiuiStatusBatteryContainer` 又完全没有 tint 处理，`settle()` 里的 `refreshOutTypeLabel` 还被 `onLayout` 门禁卡住。**更隐蔽的是代码里原本的注释把这个错误信念写成了事实**：`OutSignalView.onDraw` 写着「the row is invalidated when the tint changes」——它被 invalidate 的场景只有「尺寸/读数变化」那一条（`updateOutSignal` 尾部），颜色变化从来不在其中。
- 修法：`TrioHooks.recolourOutRing(TrioState state, int ink)`，由 `hyperduo-draw` 钩子在 `state.refresh()` 之后比较 `state.foreground()` 与 `TrioState.outRingInk`，**只在不等的帧**记账并 post 一次重着色（标签 `setTextColor`、读数 `invalidate()`）。
- 两个设计决定：**账记在 `TrioState` 实例字段而非静态字段**（状态栏与控制中心各有一个电池容器，前景色相同，共享静态格会让先画的把变化吞掉），**先记账再 post**（帧抖动不能每帧排 runnable，且记下的正是本帧画出去的值）。
- **判据只有真机能给**（改深浅色的那一下是否立刻跟上），设备离线时缺此证据；离线侧只保证编译与五条工装不回归，外加产物核对：`app-release.apk` 的 `classes.dex` 同时含 `recolourOutRing` 与 `outRingInk`（debug 包在 `classes3.dex`），排除「改动没进产物」。
- **离线钉子**：`TrioHooks` 需要 Xposed API、桌面上编不了，所以这条规则和该文件里其它几条一样，
  在 `work\outringcheck\verify.ps1`（不入库）里钉源码：四条成对断言（只在不等的帧记账 / 先记账再
  post / 标签拿到新墨色 / 读数被要求重绘）加两条形状断言（错误注释不得复活、`outRingInk` 必须是
  实例字段且 `foreground()` 仍把 0 折成 `DEFAULT_FOREGROUND`）。七种回归形态各自实测都能把钉子碰响。
- **教训**：自绘视图挂进系统视图树后，**「谁会在什么时候 invalidate 我」必须自己举证**，不能沿用宿主视图的刷新假设——宿主被 invalidate 不等于兄弟也被 invalidate。注释里写下的这类假设，正是后来找 bug 时最该先怀疑的一句。

### 6.8 第十轮：环外信号尺寸异常与「环外信号大小」

用户报告两件事：**「环外信号尺寸异常」**与**「增加环外信号尺寸的调节功能」**，并要求参考 `https://github.com/ColdP/HyperChanger`。两者同源，一起做。

**一、尺寸异常的真身**

`TrioRenderer.outSignalInkH(boolean dots)` 原本随有无点行返回**两个不同的墨高**：有点行 `STACK_INK_H`（209），无点行 `STACK_BAR_H[STACK_COLUMNS-1]`（150）。而它同时被两处当分母用：

```java
// TrioRenderer.outSignalWidth(int height, boolean dots)  —— 旧
return Math.max(1, Math.round(height * TrioGeometry.STACK_INK_W / outSignalInkH(dots)));
// TrioRenderer.drawOutSignal(...)  —— 旧
final float scale = Math.min(width / inkW, height / outSignalInkH(dotRow));
```

于是**同一宿主高度**下，无点行的读数按 150 单位撑满整盒、有点行按 209，柱子被放大 `209/150 ≈ 1.39` 倍——单卡（无点行）比双卡明显粗大一截，这就是用户看到的「尺寸异常」。

- 修法：**参照框永远取 `STACK_INK_H`**。`outSignalWidth` 收为单参 `static int outSignalWidth(int height)`，分母写死 `TrioGeometry.STACK_INK_H`；`drawOutSignal` 内 `scale = Math.min(width / inkW, height / TrioGeometry.STACK_INK_H)`。`outSignalInkH(dots)` 保留但**语义降级**为「居中用的墨高」（`x0` 与 `baseline` 仍按它算），不再是 scale 的分母——所以无点行时读数在同一个 209 框里居中，上下各留一点空，不再被放大。
- **必须同时改两行才能复现**：只改 `drawOutSignal` 的 scale 或只改 `outSignalWidth`，两者会互相抵消（宽度按 150 算出来、scale 又按 209 缩回去，实测只差 3px）。`work\outringcheck\verify.ps1` 的 `squat` 反例因此用**两段** `[regex]::Replace`（`$boxRule` 与 `$widthRule`），各须恰好命中 1 处否则 throw。

**二、新设置项**

- 键值 `out_signal_size_dp`（**常量名仍是 `Prefs.KEY_OUT_SIGNAL_SIZE`**，只改了它的字符串字面量），**int dp**，默认 `15`（= 与状态栏图标同高），范围 `6..20`（`MIN_OUT_SIGNAL_SIZE` / `MAX_OUT_SIGNAL_SIZE`）。**不欠迁移**：旧值 `out_signal_size` 从未随任何一次发布出货，框架也不做类型转换、每个键终身只有一种类型，所以没有历史键要读。
- 换算入口：`TrioRenderer.outSignalHeight(int sizeDp, float density)`（纯算术，`sizeDp <= 0 || density <= 0f` → 0，否则 `Math.max(1, Math.round(sizeDp * density))`）；`TrioHooks.outSignalHeight(View host)` 是唯一包 `TrioConfig.appearance().outSignalSize` 的地方，它**只从视图取密度**（`host.getResources().getDisplayMetrics().density`），视图的测量高度**故意不取**，**measure 与变更检查都走它**（`view.getMeasuredHeight() != outSignalHeight(host)`），所以滑杆与重新测量不可能对目标高度各执一词。
- **为什么是 dp（bug (g)）**：用户报「而且下拉到控制中心后这个信号还会莫名其妙的放大,需要修复」。旧语义是电池容器**活高度**的百分比，而 MIUI 把那行在收起时常驻 88px、拉开控制中心后变成 134px（就是系统 `statusBars` inset 的高度，`dumpsys window displays` 实测），读数于是跟着放大 `134/88 ≈ 1.52` 倍。dp × 密度与那一行多高毫无关系，物理大小不随下拉改变。15dp 也不是随手取的：测试机（小米 houji / Redmi K70，1200×2670，`Physical density: 480` 即 density 3）上 MIUI 自己那四条信号柱墨高 44px，`15 × 3 = 45` 正好同高；`6..20dp` 在 density 3 下是 18..60px，下界是四根柱仍分得清的最小值，上界是状态栏那一行在开始挤动邻居图标之前能容下的最大值。
- UI：滑杆落在**尺寸卡** `group_type`（紧跟 `out_type_size` 之后），门禁 `enabled = a.stackedOut()`——直接复用谓词而不是重抄三个条件；提示链点名的四条上游开关（`master` / `show_mobile` / `signal_mode` / `stacked_signal`）**都不含本行自身**，符合 6.7 二的自锁规则。
- 预览第 8 格（`preview_out_signal`）同步走 `TrioRenderer.outSignalHeight(a.outSignalSize, density)`（`TrioPreviewView` 自己从 display 取 `density`），所以滑杆在设置页有可见反馈。

**三、HyperChanger 对比**

参考项目 `ColdP/HyperChanger` 有同类实现：键 `stacked_mobile_signal_scale` / `_vertical_offset` / `_left_margin` / `_right_margin` **全为 float**，夹取 `scale.coerceIn(0.1f, 3f)`、其余 `coerceIn(-8f, 8f)`，应用方式是给容器打 `scaleX`（还额外乘 1.06）/ `scaleY` / `translationY` + 改 margin，**不重算几何**。本项目改取 **int dp + 重算几何**，理由与它同源（本模块所有既有设置项都是 int、UI 用 `IntSlider`），但单位是 **dp 而不是百分比**：dp 在任何密度上都能自己换算成正确的像素，百分比则必须依附某个参照高度，而这里可依附的那一行恰恰会变——bug (g) 就是这么来的。dp 也胜过 px：px 换一块密度不同的屏就偏大或偏小了。且环外读数是自绘的、本来就有几何可算；抄它的容器缩放会和本模块「按参考图单位逐项算出墨迹」的路线打架。

**四、验证**

- `work\outringcheck\verify.ps1` 升为**四构建**：`fixed` 期望 0，`refold` / `inert` / `squat` 期望非 0，实测 3 / 14 / 3 条不符，脚本 `exit 0`。`squat` 的尺寸断言实测 `the tallest bar is the same size with and without a dot row (51 vs 72 px)`——51/72 就是 1.39× 等比缩放后的同一件事。
- **断言教训一（取样点必须与几何无关）**：第一版按**正确公式**算最高柱横坐标再取样，而 `squat` 构建的几何恰是错的 ⇒ 取样列落在柱外、量到空画布，实测 `51 vs 0 px`，反例照样「失败」但失败的理由是错的。改为 `tallestRun(img)`（全画布扫最长连续亮段）+ `inkSpan(img)`（最左/最右亮列打包成一个 long），不依赖任何几何假设。
- **断言教训二（出图工装写死尺寸就看不见尺寸 bug）**：`OutSignalShot` 原来把三个单元格的宽高写死成参考框 `251×209`，于是 `squat` 也报 `PASS`；改成**向生产同一对函数要盒子**（取一个合成密度 `STACK_INK_H / shipped`，于是 `outSignalHeight(shipped, STACK_INK_H / shipped)` 恰好让交付的 dp 设置渲染成参考框 `251×209` 的 1:1 结果，再 `outSignalWidth(...)`）之后，`squat` 立刻报 `FAIL: 6 measurement(s) differ from the reference`（含 `MISMATCH bar_w: reference 50 vs rendered 49` 与 `one SIM (bars only) bar heights: [104, 139, 174, 209] vs the two-SIM reading's [75, 100, 125, 150]`），`fixed` 仍 `PASS`。
- `compare.py` 判据同步调整：参考图逐项比对只跑**两卡格**（其墨恰好填满 209 参考框、零偏移）；单卡/上网卡改为与两卡格逐根比柱高（容差 1px）——它们在 209 框里居中会少 1px 抗锯齿边缘，拿它们去比参考图量到的是**居中**而非几何。
- Gradle `:app:assembleDebug --offline` `BUILD SUCCESSFUL`。

### 6.9 第十一轮：环外边距、环外信号位置与「5GA」的 A

用户在同一轮提了三件事，都落在环外这条线上，一次做完、一个版本号（`1.6` / `versionCode 10600`）。

**一、环外网络类型标签的两个左右边距**

- 键 `out_type_margin_left_dp` / `out_type_margin_right_dp`，int dp，默认都 `2`（= 原硬编码常量 `OUT_LABEL_GAP_DP`，所以老安装外观不变），范围 `0..16`。
- **语义按物理左右、不按阅读顺序**：条带在 LTR 下是 `[标签] --右缝-- [读数] --右缝-- [电池]`，标签的**外侧**对着原生图标行、**内侧**对着读数（或电池）。RTL 下整行镜像，两条缝互换物理方向，所以 `placeOutTypeLabel` LTR 用右缝（`anchor.getLeft() - right - width`）、RTL 用左缝（`anchor.getRight() + left`）。
- **必须在一个地方合成**：strip 是**一个** padding 数字（`reserveOutRingStrip`），两条缝在这里按 RTL 取「朝锚点」的那条算出 total；只有标签独占（没有读数）时把外侧那条缝也算进去。分别 reserve 会让后更新的那个把先更新的挤掉。
- 原来的单值 `outLabelGap(container)` 拆成 `outLabelMargins(container)`（返回 dp→px 的 `{左, 右}`）与 `outLabelAnchorGap(container)`（取朝锚点的那条）；dp→px 与 `outSignalHeight` 同法，取显示器密度而非状态栏行高。

**二、环外信号读数与电池的间距（1.6.1 修正，取代 1.6 的两个位置偏移）**

- **1.6 的原案（已废弃）**：`out_signal_offset_x_dp` / `out_signal_offset_y_dp`，走布局层把像素偏移加在 `placeOutTypeLabel` 的 clamp 之后。
- **上线后用户报告**：「环外信号现在存在占位问题，现在不占位了，会悬浮在其他图标上方」——位移**不参与 strip 计算**（`reserveOutRingStrip` 只按 `getMeasuredWidth()` 求和），所以调大后读数滑出为自己预留的空间、直接画在邻居图标上层。
- **修正**：键改为 `out_signal_margin_dp`，int dp，默认 `2`（= 原来写死的 `OUT_LABEL_GAP_DP`，升级不变样），范围 `0..16`。它是**会占位**的边距，和标签边距走同一条路径：`reserveOutRingStrip` 把它连同读数宽度一起让出来，`placeOutTypeLabel` 用它定位。
- **垂直方向不再提供**：状态栏那一行没有可预留的纵向空间，任何纵向位移都只能是「压到别的东西上」，所以读数一律在行内垂直居中。
- `placeOutTypeLabel` 第三参从 `(offsetX, offsetY)` 改成**单个 `gap`**——「这个视图与锚点之间的缝」；三参重载保留给标签（内部取 `outLabelAnchorGap`），读数用四参传 `outSignalMargin`。工作台的文本 pin 锚的正是三参那两处标签调用，因此不受影响。
- **通用教训**：这条线上的任何调节都必须是**边距**，不能是自由位移——strip 是唯一能让原生图标跟着让位的机制。

**三、「5GA」里的 A 相对主字号缩小**

- 键 `type_suffix_scale`（**不是** `out_type_suffix_scale`：这是标签自身的属性，与画在环内还是环外无关，两处共用一个键），int 百分比，默认 `65`，范围 `50..100`。
- 目标值来自用户参考图 `docs/ref-5ga.png` 的量测：三段字形末段（A）高 56、主段 86 ⇒ **≈0.65**，且 A 底边 115 与主字底边 117 近似齐平（差 2px）。`work/outringcheck/SuffixShot` 把这段量测固化成了脚本：按若干比例渲染 `"5GA"`、列游程量高，实测 50/65/80/100 → `0.481 / 0.625 / 0.779 / 0.971`（65 时读数 0.625，与参考的 0.65 同档；100 与旧版单字号一致）。
- **两条独立路径**（同一比例、两套机制）：
  - **环内（Canvas）**：`TrioRenderer.drawType` 分两段绘制。`Canvas.drawText(CharSequence,...)` 不应用 Span，只有 `TextPaint` + `StaticLayout` 才会，所以必须自己量宽、自己排。`TEXT` 默认 `Align.CENTER`，绘制期间切成 `Align.LEFT`、画完还原（居中会让两段叠在同一处）；两段共用同一条 baseline 保证底边对齐。`fitSize` 走同一套量测（`measureType`），否则用整串宽度判断「装不下」会把本来放得下的标签缩小。
  - **环外（TextView）**：`OutTypeLabel` 是真 `TextView`，直接 `SpannableStringBuilder` + `RelativeSizeSpan` 套在末字，平台自己排版对齐。`label.suffixScale` 记住当前比例——比例变了但**字符没变**（还是「5GA」），只比 `getText()` 会跳过重建，滑杆看起来是死的。
- **后缀判定**：`TrioGeometry.hasShrunkSuffix(text, scale)` = 比例 < 100 且长度 > 1 且末字为 `A`。单独的 `"A"` 不是后缀（缩小它等于整体变小，那是字号滑杆的事）。

**四、门禁与预览**

- 新滑杆的 `enabled` 都复用既有谓词，且**提示链不含自身**（6.7 二的规则）：类型边距与 A 比例用 `a.typeOutOfRing` / `a.typeAnywhere()`，信号间距用 `a.stackedOut()`（与 `out_signal_size` 同一条提示链）。
- 预览第 7 格的示例类型由 `"5G"` 改为 **`"5GA"`**，正是为了让 A 比例有可见反馈；`TrioPreviewView.drawOutTypeLabel` 同步走两段绘制。第 8 格把 `out_signal_margin_dp` 换算成读数到格子右边缘的距离（右边缘即电池在真实那一行的位置，窗口宽 = 读数 + 滑块上限），所以拉大间距时读数确实向左离开边缘，整个量程都留在格内。
- 顺手清掉了 `TrioPreviewView` 里声明未用的陈旧常量 `OUT_LABEL_GAP_DP`（它的 javadoc 还自称是 `TrioHooks.OUT_LABEL_GAP_DP` 的镜像，而后者已经不存在）。

**五、验证**

- 五个工装（`gapcheck` / `slotcheck` / `dualsimcheck` / `simcheck` / `outringcheck`）全部 `exit 0`（`outringcheck` 的四个构建：fixed=0、refold/inert/squat=1）。
- `work/outringcheck/SuffixShot` 新增，量出 A/主字高度比随比例单调，65 → 0.625。
- Gradle `:app:assembleDebug --offline` `BUILD SUCCESSFUL`。
- **未做**：设备端两条实证（环外读数尺寸不随下拉变大、仅显示上网卡实时生效）——写出本轮时 `adb devices -l` 仍是 `<DEVICE_LAN_IP>:<ADB_PORT> offline`。

### 6.10 1.6.1：环外信号间距取代位置偏移

1.6 发布后用户立即报告：**「环外信号现在存在占位问题，现在不占位了，会悬浮在其他图标上方，似乎是调节的参数导致的，应该不用位置来做调节了，而是边距」**——诊断完全正确。改动见上方 6.9 二；这里记结论与教训：

- **根因**：位置偏移不参与 `reserveOutRingStrip` 的占位计算，所以它能把读数移出预留区、叠到邻居图标上。边距之所以没这个问题，正因为它是被预留的那个数字。
- **键的迁移**：`out_signal_offset_x_dp` / `out_signal_offset_y_dp` **不迁移**到新键。理由有二：偏移的语义（自由移动）与边距（占位间隙）不同，无法一一映射；且这两个键只在 1.6 存在了一天、默认值为 `0`，绝大多数安装从未写过它们。装了 1.6 又恰好调过偏移的用户，升级后回到默认间距 `2dp`（= 1.6 之前的出厂外观），不会更差。
- **默认值取 2dp 而非 0**：老 `OUT_LABEL_GAP_DP` 常量、以及 1.6 之前所有版本的读数间距都是 2dp，取 0 会让升级后读数贴到电池上，是外观回退。

### 6.11 第十二轮：尺寸调节的复盘（1.6.2）

对全部 13 条尺寸滑杆做了一次盘点后落地四项。总体判断：1.6.1 之后这套设计的**骨架是对的**（环外一切横向调节都走占位边距、门禁全部派生自 `TrioAppearance`、新键默认等于旧硬编码值），残留的问题集中在一个单位例外、两个预览盲区、一处卡片混杂。

- **P1 环外字号转 dp（`out_type_size` → `out_type_size_dp`）**：旧键是全表**唯一**的非 dp 长度（裸 px，`COMPLEX_UNIT_PX` 原样应用），同一个滑杆值在不同密度设备上物理大小不同——与第十轮 `out_signal_size` 改 dp 的理由完全同源，是那条修复漏掉的一处。新键默认 **11dp**（= 老默认 32px ÷ 编写它的 density-3 设备的密度，物理外观不变），范围 **6–22dp**（覆盖旧范围在同密度上的物理跨度）。**迁移**走 `readMobileTypeMode` 的既有模式（新键在则用之；仅旧键在则 `Math.round(px ÷ density)` 一次并 clamp；`from(SharedPreferences)` 为此增开带 `density` 的重载，无显示环境的工装走 `Prefs.AUTHORED_DENSITY = 3f` 委托旧签名）；**九项离线断言**（`.tmp/migtest/MigTest`）覆盖全部路径。消费端换算收口在 `TrioHooks.outTypeSizePx`（posted 更新与变更比较共用，同 `outSignalHeight` 模式），`TrioPreviewView` 同口径乘密度。
- **P2 预览补类型边距的可见反馈**：`drawOutTypeLabel` 在格子两侧按边距宽度画细刻度线（前景 56/255，与底纹同级的结构感），**文字块在两线之间居中**——加宽某一边时文字块被挤向另一侧，展示的是「占位」而非「位移」，与状态栏的真实语义一致。两边相等时与旧版渲染逐像素一致。至此 13 条滑杆全部有可见反馈。
- **P3 卡片拆分**：`group_type` 原来一张卡 8 条滑杆，混了「网络类型」与「环外信号」两个对象、两套门禁。拆成两张卡（新增 `group_out_signal` 小节标题）：信号大小与间距门禁是 `a.stackedOut()`，类型组门禁是 `typeOutOfRing`/`typeAnywhere()`，混排时用户找「信号间距」要在类型堆里翻。
- **P5/P6 文案与单位**：`out_type_size` 的 summary 补上单位与范围（此前全表唯一不提单位的长度滑杆）；`value_size` 的 summary 说明「居中模式下数字最大放大 1.4 倍、38 及以上不再变大」（`centreSize = min(size×1.4, 52)` 的既有死区，此前无解释）；`IntSlider` 增 `valueTextSuffix`，五条 dp 滑杆的值显示从裸数字变为「12dp」。
- **未动**：`out_type_margin_left/right` 的「左/右」命名（首轮用户在选择题里定过「左右分开、两个独立滑杆」的硬约束，RTL 镜像已在 summary 说明）；strip 预留模型；`type_weight`/`value_weight` 分立。

### 6.12 第十三轮：状态栏性能读数

新增两个布尔键 `perf_power`（充电功率）与 `perf_temp`（电池温度），默认都关。两者都是**只有 Hook 侧读**的键，渲染器与预览都不看它们——设置页两行开关的门禁也因此只跟总开关走。

- **两个键共用同一个视图**：`TrioHooks.PerfLabel`，挂在电池容器上、位于环外类型标签与环外读数旁边。之所以不做成两个视图，是因为两项的可见性可以独立：只开一项就只有那一项，两个都关整块才摘掉（挂载门是 `TrioAppearance.perfAnywhere()` = `perfPower || perfTemp`，另加总开关 `a.glyph`）。
- **两行开关都只用 `enabled = a.glyph` 门禁，刻意不写 `a.perfAnywhere()`**：两行共用同一个视图，用「两项的或」当门禁会让任一行在关掉自己的瞬间变灰、再也开不回来。这条与 §6.3 里「一个开关不能门控它自己」是同一条规则，落地时按它执行。
- **采样走广播**：`BatteryPresence.java`（形状照抄 `WifiPresence.java`）注册粘性 `ACTION_BATTERY_CHANGED`，电流取 `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)`，再在 SystemUI 里自续一个 3 秒看门狗。不用 sysfs / health HAL，是因为模块跑在 `android.uid.systemui`，那两条路对这个 uid 都是关着的。
- **功率是电池自身电流 × 电压**，不是充电器协商的最大档位（协商值在电量爬升时几乎不变，用户要看的却是真实速率）；满电时电池电流是**一个小负数**，所以瓦数 clamp 在 0，永不显示负功率。不足 10W 保留一位小数、10W 及以上取整，整数拼串避免 `Locale` 差异。
- **没接充电器时功率段整段消失**，而不是显示 `0W`——`0W` 挨着温度会被读成「读数坏了」，而它只表达「没在充电」。温度与充电器无关、照常显示，这条不对称正是两项要分成两个开关的原因。
- **字号复用环外网络类型字号**（`out_type_size_dp`）作为基准，不给自己加滑杆：单项与邻居环外标签一样大，两项都开时按图标高度自适应缩小（`perfTextSize`）。（**这句话在第十四轮作废**：仍然以环外字号为基准，但在它之上新增了 `perf_scale_pct` 这条整体缩放滑杆，见 §6.13。）
- **加入 `reserveOutRingStrip` 的同一条求和**，而不是另撑一次 padding——strip 是唯一能让原生图标让位的机制（§6.10 的教训：各视图各撑一次会互相挤掉）。锚链上 `labelAnchorIn` 先取可见的 `PerfLabel`，没有才退回 `readingAnchorIn`。
- **未动 / 盲区**：预览卡仍是 8 格（八格一行已到宽度预算 340dp / 344dp），两个读数**没有预览格**，开关效果在预览里无反馈。

### 6.13 第十四轮：两处整体尺寸滑杆

用户要求给「整个字形」与「两项读数」各加一条整体缩放的滑杆。新增两个 int 键：`glyph_scale_pct`（默认 100，**60 – 100**，只有渲染侧读）与 `perf_scale_pct`（默认 100，**60 – 150**，只有 Hook 侧读）。两者都是**百分比乘数**，不是新的绝对尺寸。

- **字形那条只能往小缩（上界 100），是被宿主的框决定的**：MIUI 给电池图标预留的区域只有 **28dp × 20dp**（模块无法改动它，那个框是系统画的）。100% 已经等于系统允许的最大值，再往上只会画到视野外，裁不裁由 MIUI 说了算——「要求得比盒子能装的更多」并不能让盒子变大。做 dp 滑杆也要先把它换算回「这个不归模块所有的盒子」的占比，所以直接做成百分比更诚实。
- **乘在适配系数上，而不是乘在设计单位上**：`TrioRenderer.drawInto` 现在是
  `final float scale = inkScale(w, h, rect) * appearance.glyphFit();`（`TrioRenderer.java:136`）。整块字形几何是同一个 120×120 的盒子，缩适配会让每一笔描边、每条弧、每个数字按同一比例变化，后面那次 `translate` 仍然把它居中；如果改成对设计单位另做一次 `canvas.scale`，`Paint.setStrokeWidth` 给的是设计单位下的常量，线宽与半径的缩放口径就会分家。
- **缩放只在适配系数上应用一次**（这是本轮避开的那个陷阱）：槽里的字形由 `drawState` 画，而 `drawState` 走的就是上面那条已经乘过 `glyphFit()` 的路径。任何「再按同一百分比缩一次交给 drawable 的 bounds」的做法都会让设置被应用两遍。正确形态是**bounds 就是电池图标自己的框 + 居中的小字形**；`GlyphDrawable.draw` 直接把 `getBounds()` 的宽高交给 `drawState`，不做第二次换算。
- **读数那条乘在自适应结果之上，而不是替掉它**：`perf_scale_pct` 经 `TrioAppearance.perfFit()` 后**最后**乘在 `TrioHooks.perfTextSize` 的返回值上——`Math.max(base * PERF_MIN_SIZE_RATIO, Math.min(base, size)) * factor`——并且三条提前返回的兜底分支也各乘一次（`lines < 2 || host == null`、`fit <= 0f`、`lineHeight <= 0f` 都返回 `base * factor`）。**必须覆盖每一条答案**：只开一项读数时走的正是 `lines < 2` 那条分支，若只缩放组合态，单项时滑杆毫无反应，等用户打开第二个开关时读数会突然跳一下。乘在自适应之上而不是设成绝对 dp，则保证两项读数仍然跟着图标高度与 `out_type_size_dp` 走。
- **两条滑杆的位置与门禁**：字形那条在「尺寸」页新增的**整体尺寸**小节里（`R.string.group_scale`），门禁只跟总开关（`enabled = a.glyph`）——它缩的就是字形本身，字形关掉就没有作用于其上的东西；它不门控任何行、也不被任何行门控。读数那条最初放在「常规」页性能读数卡片的末尾，**第十五轮已挪到同一个小节里、紧挨字形那条**（见 §6.14），门禁是 `a.glyph && a.perfAnywhere()`，并带 `gateHint(a.glyph to R.string.master_title, a.perfAnywhere() to R.string.perf_pair_title)` 的 tooltip——「两项读数都关着所以变灰」这件事离屏幕上的现象远了一步，需要说明。两条都用 `valueTextSuffix = "%"`。
- **两条都是全新的键名，没有复用旧键**：框架不做类型转换，一个键一辈子只有一个类型（§6.2 第 3 条与 §P1-1 的教训）。`glyphScale`/`perfScale` 走全链：`Prefs` 常量与上下限 → `TrioSettings` 字段与 `defaults()`/`from()`/`copy()`/`applyKeyFrom`/`fromBundle`/`toBundle`/`equals`/`hashCode` → `TrioAppearance` 字段与 `glyphFit()`/`perfFit()` → `SettingsRepository.setGlyphScale`/`setPerfScale` 与 `syncAllToFramework()` 的两行 `putInt` → `SettingsScreen` 的两条 `IntSlider`。
- **本轮计数**：`Prefs` 的 `KEY_*` 常量 39 → 41，在用的键 37 → 39，`TrioSettings` public 字段 37 → 39，`SettingsRepository` setter 37 → 39，设置页可写项 37 → 39；`SettingsScreen.kt` 的 `enabled =` 实测 51 → **56**（逐行数出来的，本轮新增 5 处：两个性能读数开关的 `enabled = a.glyph`、性能读数滑杆的 `enabled = a.glyph && a.perfAnywhere()`、字形滑杆的 `enabled = a.glyph`、以及 `TooltipBox` 的 `enabled = scaleHint != null`）。**顺带订正**：本表上方原先写的「第十三轮后 53 处」与树里对不上——按 `git show HEAD:…SettingsScreen.kt` 逐行数，第十三轮后其实只有 51 处，所以这一格改为按实测值书写。
- **断言脚本跟着扩**：`tests\perf-layout.ps1` 从 38 条加到 **48** 条（`PASS: 48 cases over perfTextSize, perfLineSpacing and inkScale`），新增的 10 条专打这个乘数——单项读数也缩放、三条兜底分支也缩放、clamp 到 60 与 150 两端、以及 100% 回到未缩放的答案。抽取面同时收紧：垫片不再自己写 `perfScale / 100f`，而是把生产的 `TrioAppearance.perfFit()` 与 `Prefs.clamp(int,int,int)` **正则抽出来**，两个界限也从 `Prefs.java` 读出——原先的写法把生产的 clamp 删掉探针照样绿，而 clamp 正是广播路径唯一的防线。复核指出的两处覆盖缺陷同轮修掉：一个与既有用例重复的房间用例（近边界现在用 `(int) Math.ceil(lh * 2f) - 1` 取确切行数），以及一条**自指**的下限断言（原先是拿 `PERF_MIN_SIZE_RATIO` 算出期望值再比回去，改成 `eq("the legibility floor is 0.6 of the type size", PERF_MIN_SIZE_RATIO, 0.6f)`，把 0.6 当成外部事实陈述）。复核另留的两处盲区（M5「身份断言只是自洽检查、且 AWT 看不见字重」与 M6「布局期接线零覆盖」）随后补齐，脚本最终 **53** 条：身份断言改用一支**没经过 `perfLineHeight`** 的 paint 独立量一遍（否则把 `perfLineHeight` 乘上任意常数它都照样通过），字重分支改由源码契约钉锁住（AWT 对 PLAIN/BOLD 度量完全相同，度量断言无从下手），`refreshPerfLabel` 的四路条件与 `updatePerfLabel` 的「先比再写」同样按形状钉住，并用一条**终点用例**证明写回视图的行距能原样读回、重同步必然收敛（否则每个 layout pass 都会再 post 同步、状态栏无限重排）。`tests\battery-presence.ps1` 同轮把硬编码的分隔符也改成从源码读出，由 41 条变 42 条。
- **新增第四个断言脚本 `tests\glyph-scale.ps1`（17 条）专门锁字形这条乘数**：`perf-layout.ps1` 只覆盖 `perfFit()`，而 `glyphFit()` 此前只有 `work\_scaleprobe\` 里一个**不入库**的临时探针证明过，`work\` 被 `.gitignore` 排除，随时可能被当垃圾清掉。新脚本用 `tests\shims`（**入库**的桌面垫片，本次新建；原先只在 `work\preview\src` 里有一份，那等于把证据放回会被清掉的地方）把真 `TrioRenderer.drawInto` 画进 Java2D 位图后**数真正落笔的像素**——这是端到端断言，与公式自洽无关：一个从不落笔的实现也能满足任何公式层面的等式。断言含「相邻档位逐档递减」「墨迹面积随系数平方下降、包围盒宽高随系数线性下降」（后两条才区分「等比缩小」与「细节熔掉」）、「满档铺满宿主」「最低档离边且仍居中」（抓 `translate` 没跟着缩放）、「clamp 两端与 floor/ceiling 逐位相同」「矩形样式单测」。另有四条**源码契约钉**（`inkScale(w, h, rect) * appearance.glyphFit()` 原样存在、`glyphFit()` 仍走 `Prefs.clamp(...) / 100f`、构造仍读 `c.glyphScale`）——像素能看出「变小了」，看不出「是在 fit 上缩的而不是在别处」。七条负控全部 `EXIT=1`，其中最要紧的一条是**保留契约钉那行字面量、但把它的值丢弃**（另起未缩放的局部变量交给 `canvas.scale`）：源码钉照样通过，只有像素断言 `got <0.6736883167543373> want <0.36000001430511475>` 抓到它，证明像素那部分独立承重。**独立性实测**：把 `work\preview\src` 改名后四个脚本仍全部 `exit 0`，确认入库的测试不再依赖任何 gitignore 目录。
- **预览的可见性两条不一样，要分开说**：**字形那条在预览里看得见**——`PreviewCard` 的每一格都是 `TrioPreviewView` → `TrioRenderer.drawInto(..., settings, ...)`，`settings` 就是当前快照，所以拖动滑杆时八个格子里的字形一起变小（已用桌面探针实测：`glyphScale` 100 → 60 时墨迹像素 5127 → 1948，`drawInto` 确实拿到了这个乘数）。**读数那条在预览里看不见**——预览的八格是七个字形态加「环外类型」「环外信号」两格，没有任何一格画性能读数。
- **未动 / 盲区**：字形滑杆虽然预览可见、且已有入库的像素级探针 `tests\glyph-scale.ps1` 证明这个乘数确实到达了画布，但**状态栏里的实际效果只有上机才能确认**；读数滑杆连预览都没有。本轮设备中途脱离 USB，两条都没在真机上过。

### 6.14 第十五轮：读数字重与图标右跳

三项用户要求：给两项读数加一条**字重**滑杆、把「读数整体尺寸」挪到它该在的页、修掉「读数开着时左边的原生图标一直往右跳」这个缺陷。前两项是新增，第三项是修复。

- **新增键 `perf_weight`（默认 700，范围 100 – 900，只有 Hook 侧读），又是一条全新的键名**：理由与上一轮相同——框架不做类型转换，一个键一辈子只有一个类型。它走全链：`Prefs.KEY_PERF_WEIGHT`/`DEF_PERF_WEIGHT`/`MIN_PERF_WEIGHT`/`MAX_PERF_WEIGHT` → `TrioSettings.perfWeight` 与 `defaults()`/`from()`/`copy()`/`applyKeyFrom`/`fromBundle()`/`toBundle()`/`equals()`/`hashCode()` → `TrioAppearance.perfWeight` 与 `perfTypeWeight()` → `SettingsRepository.setPerfWeight` 与 `syncAllToFramework()` 的 `putInt` → `SettingsScreen` 的第三条 `IntSlider`（`step = WEIGHT_STEP`）。
- **它是一条独立的键，不复用 `type_weight`**：`type_weight` 驱动的是**网络类型标签**（环内分段绘制与环外 `RelativeSizeSpan` 都按它取字重），而这两项是状态栏上的读数。把两者绑在一起意味着「想把读数调粗」会顺手把环外的 5G 标签也改粗，反之亦然——它们之间没有任何语义联系，只是碰巧都叫「字重」。
- **字重必须自己成为重同步的第五个分句**：`PerfLabel` 新增 `int weight = -1`，记住当前这支 typeface 是照哪个字重建的；`refreshPerfLabel` 的条件因此从四路变五路，多出 `|| perf.weight != weight`（`weight = TrioConfig.appearance().perfTypeWeight()`）。原因是字重改变画面有两条路径——typeface 本身、以及它带来的行高——而**当字号被钉在 `PERF_MIN_SIZE_RATIO` 下限时（本机默认就在这个档位）改字重不改变字号**，既有的「文本变了没」与「尺寸变了没」两个分句都看不见它，滑杆会像死了一样。`perfTextSize` 同时改用读数自己的字重量行高：`perfLineHeight(base, a.perfTypeWeight())`，不再借用类型标签的 `typeWeight`。
- **缺陷本身：`reserveOutRingStrip` 原先拿 `perf.getMeasuredWidth()` 当读数的预留宽**。那是**当前文本的实测宽**，而 `BatteryPresence` 大约每秒发布一次新瓦数，`0.0W`（102px）与 `6.1W`（92px）之间来回时预留值就跟着跳；每次跳动都经 `setPadding` 重排 MIUI 的图标行，左侧的原生图标被整体推一下——表现就是「图标一直往右跑，回不去」。设备日志里同一个文件内预留值在 `102` 与 `108` 之间交替，是这条判断的现场证据。
- **修法是让预留宽度成为「读数是什么」的函数，而不是「它此刻写着什么」的函数**：新增 `private static int perfWidth(PerfLabel perf, String text)`（`TrioHooks.java:3840`），借视图自己的 `Paint` 量出这段文字**可能**变成的最宽形态。三处细节各自针对一个坑：①把每一位数字换成**该字体里最宽的那个数字**——把十个数字各量一遍问出来，而不是假定 `'0'` 或 `'8'`（不同字体里最宽的数字不同）；②**逐行量**——`\n` 对 paint 只是一个字符，整串量会把两行加在一起、预留出两倍的实际宽度；③`(int) Math.ceil(width)` 向上取整。调用点因此是 `total += perfWidth(perf, perf.getText().toString()) + labelInward;`（`TrioHooks.java:4788`）。
- **为什么是 paint 而不是真的 layout 一段探针字符串**：探针方案会**踏上布局这条路**，而 `reserveOutRingStrip` 就在布局/同步的链上；paint 度量是纯计算。代价是没算进字距（`VALUE_LETTER_SPACING = -0.04em`），所以答案比真正画出来的宽几个像素——**这是安全的方向**：预留宁可略宽，绝不能短（短了图标会被压在读数上）。此后只有**数字位数**还能改变预留，那是阶跃、不是闪烁。
- **顺带把滑杆挪到该在的页**：「读数整体尺寸」原在「常规」页性能读数卡片末尾，现在与新增的「读数字重」一起放进「尺寸」页的**整体尺寸**小节（`R.string.group_scale`），紧跟字形那条之后。理由写在源码注释里：它们和描边粗细、字号是一类东西（都是**数字**），而一页开关不是滑杆该待的地方。两条读数滑杆共用同一个 `perfHint` tooltip。`generalTab` 末尾只留下一条注释说明它们搬去了哪里。
- **本轮计数**：`Prefs` 的 `KEY_*` 常量 41 → **42**，在用的键 39 → **40**，`TrioSettings` public 字段 39 → **40**，`SettingsRepository` setter 39 → **40**，设置页可写项 39 → **40**；`SettingsScreen.kt` 的 `enabled =` 实测 56 → **58**（逐行数出来的，本轮新增 2 处：字重滑杆的 `enabled = a.glyph && a.perfAnywhere()` 与它那个 `TooltipBox` 的 `enabled = perfHint != null`——挪动的那条滑杆把两处 `enabled =` 原样带走，不改变计数）。
- **断言脚本跟着扩**：`tests\perf-layout.ps1` 从 53 条加到 **64** 条，随后又加到 **76** 条（`PASS: 76 cases over perfTextSize, perfLineSpacing, perfWidth and inkScale`）。前 11 条覆盖 `perfTypeWeight` 的两端 clamp 与默认值；后 12 条**执行** `perfWidth` 本身，而在此之前这条修复只有一条源码契约钉（断言生产里写的是 `perfWidth(perf,` 而不是 `perf.getMeasuredWidth()`）——钉的是字面而不是答案，实现返回什么都算过。12 条覆盖最宽数字替换确实变宽、每个数字按最宽量、两个不同瓦数预留**同宽**（抗抖动核心）、`(int) Math.ceil` 的取整方向且该值非整数、位数变化仍是台阶、**两行的预留等于较宽的那一行而不是两行之和**、最宽数字是问字体不是假定 `'0'`（换一张字宽表答案跟着变）、空文本回落到视图已有 frame、等宽字体下不偏短。为了让「替换」可观测，探针给垫片 `Paint` 注入了 `setDigitWidths(float[])`：桌面 SansSerif 十个数字都是 556/1000（实测），替换在这张脸上是恒等操作，无论实现对不对都通过；真实状态栏字体是比例宽度（设备日志 `0.0W`=102px 对 `6.1W`=92px），那张表就是它的替身。五路重同步条件里的 `perf.weight != weight` 也一并按形状钉住。
- **未动 / 盲区**：字重滑杆与抗抖动修复都已在真机上确认过。字重滑杆在尺寸页逐档可见；**但 `perfWidth` 单独并不足以止住跳动**——上机仪器化（在 `reserveOutRingStrip` 里打印每个分量）显示 `perfWidth` 在 28 帧里恒为 `101`，而图标行的 `paddingEnd` 仍在 `107 ↔ 0` 之间以功率更新的节奏乒乓。真正的成因是 `removeOutTypeLabel` / `removePerfLabel` 里**抢先的 `releaseOutTypeSpace`**（排在判空之前，视图为 null 时直接 return、不再重新 reserve），改由 `reserveOutRingStrip` 单独当权威之后 `strip release` 计数归 0、14 张连续截帧里左侧原生图标钉死在 `832-844|846-878|890-927`。详见 `history/1003-todo.md` §30.6。**教训：从症状反推病因会走错——这一次从像素反推出的 3–8px 位移，实际是 107px 的整行横移被状态栏边界裁掉之后的残影。**

### 6.15 第十六轮：#28 / #33 的触摸遮挡（半途接手一个社区 PR）——第十七轮已整体退场

用户要求「#28/#33 遮挡问题先修吧」。**这一轮没有新增任何键**（`Prefs` 的 `KEY_*`、在用键数、`TrioSettings` 字段、`SettingsRepository` setter、设置页可写项与 `enabled =` 计数全部不变），改的是既有开关 `glyph_window` 那条渲染路径上的一个窗口属性。

> **本节是历史记录：第十七轮把整条独立窗口路线删掉了**（`TrioOverlay.java`、`tests\overlay-window.ps1` 与 `glyph_window` 设置链一并退场，字形改由 `GlyphDrawable` 塞进 MIUI 电池图标自己的 drawable 槽，见 §6.16）。因此下面提到的 `TrioOverlay.windowType()`、`markTrustedOverlay`、`PRIVATE_FLAG_TRUSTED_OVERLAY` 在树里都已不存在——留在这里是因为它记录的判断仍然成立：那个窗口之所以被平台当成可疑覆盖层，根因是**窗口类型不在白名单里**，而不是窗口太大。

- **症状与归属**：开着本模块时 LSPosed 的权限请求界面点不动——系统判定有别的窗口盖在上面，落在其下的触摸被打上 obscured，带防护的应用检测到该标记就拒绝响应（`history/1003-todo.md` §32.1）。
- **根因不在「窗口太大」而在「窗口类型不被白名单认」**：`TrioOverlay.windowType()` 优先取 `TYPE_STATUS_BAR_SUB_PANEL`（2017，理由是 MIUI 的 `StatusBar1` 会在配置变更/SystemUI 重启时盖住 2041），而 AOSP 的 `InputMonitor.isTrustedOverlay(int)` 白名单里**有 `TYPE_STATUS_BAR_ADDITIONAL`（2041）、没有 2017**；配合 `WindowState.getTouchOcclusionMode()` 对状态栏类型走 `BLOCK_UNTRUSTED`，主线挑中的恰好是那个必须靠私有位自救的类型（§32.2 有逐行 AOSP 出处）。**这也是第十七轮选择「不建窗口」的直接理由：只要还在建窗口，就永远要跟这份白名单和这套遮挡判定打交道。**
- **当时的修法**：按 PR #29 的第二个提交（`b809317`）反射读写 `privateFlags`，把 `PRIVATE_FLAG_TRUSTED_OVERLAY`（`1 << 29`）**或**上去；**必须是「或」**，否则会抹掉平台自己拥有的位。第一版 `fb2fa61` 的 `params.privateFlags |= trusted;` 直写在本机的 `android.jar` 上根本编译不过——`javap -p` 里连 `TYPE_STATUS_BAR_SUB_PANEL` 都找不到。
- **当时的验证方式**：平台的 `WindowManager.LayoutParams` 可能「两个隐藏名字都有 / 只有字段 / 都没有」，而这是**编译期**事实，一个测试类编不了三种平台。所以探针 `tests\overlay-window.ps1` **整棵重建生成树三次**，每次换一种桩形状编一遍：9 / 3 / 4 条断言，另加八条源码契约钉补在形状断言够不到处（禁止直写、禁止用赋值顶替「或」、没有该位的平台必须原样放过、`FLAG_NOT_TOUCHABLE` 必须还在、标记必须与窗口 flags 同处）。**五条负对照全部 `EXIT=1`**，其中最要紧的一条是**把常量 `| 1` 只改位而保留全部字面量**：源码钉照样通过，只有**执行级**用例抓到（`got <536870913> want <536870912>`），证明形状断言独立承重。该探针随窗口一起删除。
- **当时未做、后来也不再需要**：PR #29 的另外两块（`redrawAll()` 换色重绘字形、`fileInk()` 配色归档）在「不建窗口」之后失去意义——槽里的 drawable 由 MIUI 每帧自己调，换色重绘不再需要手工触发。

### 6.16 第十七轮：删掉独立窗口路线，字形改走 MIUI 自己的 drawable 槽

用户先选定「学 HyperChanger，自定义 `Drawable` 塞进系统电池 `ImageView`」，再指示「可以的，开一条重构的工作树吧」。改动在独立 worktree（`work\drawable`，分支 `refactor/drawable-glyph`，基线 `68a8dcc`）里完成。

**这一轮是减法：删掉一个键（`glyph_window`）而不是新增。** 因此本表上方所有计数**净减 1**：`Prefs` 的 `KEY_*` 常量 42 → **41**，在用的键 40 → **39**，`TrioSettings` public 字段 40 → **39**，`SettingsRepository` setter 40 → **39**，设置页可写项 40 → **39**，`SettingsScreen.kt` 的 `enabled =` 实测 58 → **57**（删掉「独立窗口绘制」那一行的 `enabled = a.glyph`）。

- **为什么整条路线删掉而不是留成兜底**：三条 A/B 实测（桌面 / 锁屏 / 下拉，各拍窗口开与关两臂）像素差**全部为 0**。写「状态栏窗口只有 `status_bar_height` 高、会把超出部分裁掉」时依据的前提，在本机 ROM 的普通状态栏上并不成立——宿主内的 `onDraw` 本来就能画出完整三合一。既然窗口在几何上是冗余的，而它又是 §6.15 那个遮挡判定的唯一来源，就没有理由留着。
- **新的落点**：`GlyphDrawable`（新增）把三合一装进 `MiuiBatteryMeterIconView.mTintBgDrawable` 槽。**槽买到的是画布、bounds、裁剪与硬件层都留在平台手里**——独立窗口这些都没有：得自己从宿主推尺寸、得与别人画的栏同步显隐、而且它在屏时平台会当成可能的 tap-jacking overlay（LSPosed 拒绝触摸就是这个代价）。
- **槽不是永久归模块的**：MIUI 会从 `clearTint()`、暗色通道、资源重建里改写它，并在中间整个丢掉；每一次后面都跟一次绘制，而 `install` 幂等，所以下一帧抢回来。`install` 的返回值只区分「刚抢到槽」——那一帧 MIUI 自己的电池已经画进同一个 canvas，调用方必须再 `TrioRenderer.draw(...)` 覆盖一次。
- **一个 build 够不着**：`res\values\bools.xml` 的 `battery_meter_use_legacy_drawable` 为 `true` 时，`MiuiBatteryMeterIconView.onDraw` 走 legacy 分支（`super.onDraw` 后直接 return），tint 槽根本不会被画到，这个 drawable 永不被调用——那种 ROM 上字形**静默不出现**而不是画错。本机该 bool 为 `false`。
- **master 开关关闭时要交回槽**：`GlyphDrawable.uninstall` 调 `clearTint()` 让 MIUI 按电池此刻的状态重建（放回记住的 drawable 会把早已离开的状态画回来）；没有 `clearTint` 的 build 退化为把槽置 null。槽的生命周期比任何一次重绘都长。
- **一并退场的东西**：`TrioOverlay.java`（818 行）、`tests\overlay-window.ps1`、`PorterDuff` import、`isStatusBarHost`、`barCovered` 三件套、`statusIconContainer()`/`statusBarView()` 两个访问器，以及 **5 组只为窗口显隐而存在的 hook**（下拉展开高度、最近任务手势、跨进程脉冲、启动动画）。`group()` 的 switch 从 18 个 case 收敛到 **13**。`AndroidManifest.xml` 本就只有 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES`，槽路线不需要 `SYSTEM_ALERT_WINDOW`。
- **验证**：构建通过（debug APK 33,175,455 B、release APK 3,128,880 B，R8 后 `GlyphDrawable` 的 `install`/`uninstall`/`draw` 保留原名，APK 字节里 `GlyphDrawable`/`mTintBgDrawable`/`mUseLegacyDrawable` 在、`TrioOverlay`/`glyph_window`/`PRIVATE_FLAG_TRUSTED_OVERLAY`/`TYPE_STATUS_BAR_SUB_PANEL` 全部不在）；**五个**探针全绿（`wifi-presence` 18 / `battery-presence` 44 / `perf-layout` 76 / `glyph-scale` 17 / **`glyph-drawable` 105 条、五种宿主形状**）；五台工作台全部 `VERIFIED`。
- **真机四态复核（1.6.13 重构版，设备 `<DEVICE_SERIAL>` / houji / HyperOS 4）**：桌面 / 应用内 / 锁屏 / 控制中心，字形与读数均正常；桌面连续 5 帧充电绿弧包围盒**逐帧完全相同**（`1052,44..1111,86`，60×43，668 px）；`dumpsys window windows | grep hyperduo` **四态全 0**，重启 SystemUI 后复拍仍为 0 而字形照常。控制中心里**没有 §31 那个方块背景残影**。日志中 `has no mTintBgDrawable` 与 `legacy drawable` 两条失败路径都未触发。`hooks=22 → 19` 与 `group()` 18 → 13 组（删 5 组窗口专用 hook）量级自洽。
- **`tests\glyph-drawable.ps1` 是补上的那一环**：删掉 `overlay-window.ps1` 时，被它锁住的机件（窗口私有标记）确实消失了，但**没有任何探针接管 `GlyphDrawable`** —— 而它正是替掉窗口的那个东西。新脚本把真 `GlyphDrawable.java` + 真 `Refl.java` 编进五种宿主形状（正常 / legacy / 无 `clearTint` / `clearTint` 包级私有 / 连字段都没有），105 条断言分成源码契约钉与执行级用例。**七条负控里后三条（宽高对调、重新 `install` 不重指向、`getBounds()` 换常量）源码钉全部原样通过，只有执行级断言报错**，证明这半边独立承重。
- **教训（写进 `docs\DEVELOPMENT.md`）**：① 做窗口 A/B 前必须先确认当前行是 `MiuiPhoneStatusBarView` 而不是 Keyguard——早前一次「两臂完全相同」的错误结论就是因为设备停在锁屏，而 `isStatusBarHost()` 遇到名字含 `Keyguard` 就返回 false，两臂都没有资格开窗口；② 从像素反推病因会走错（见 §6.14 的 107px 残影），先把分量打出来；③ `[System.IO.File]` 的相对路径按**进程 CWD** 解析而非 PowerShell 的 `Set-Location`，对 worktree 做脚本手术必须传绝对路径；④ 用「读全部行 → 按下标删除 → 写回」做行号手术不安全，逐条用锚定字符串替换。


### 6.16 第三十一轮：「绘制」分页——把三块热区当作命中判定来测

用户要求「设计一个可视化调节的功能，把这个三合一直接在软件中渲染出来，用户能够通过可视化直接切换需要显示的组件，显示的位置主要就是中间的上中下三部分」，并在三个追问里选定：新开一整页；上中下就是**可视化区域的分区**（不是让用户把任意组件指派到任意槽位）；**点按＝开关，长按＝打开该部件的尺寸/颜色设置**。页名用户定为**「绘制」**（因为「常规」页里已有一个同名小标题「外观」`group_appearance`）。

**这一轮没有新增任何键**：`Prefs` 的 `KEY_*`、在用键数、`TrioSettings` 字段、`SettingsRepository` setter 全部不变。它给**已有的**开关加的是**第二条写入路径**，不是新的开关。

- **它是第三个改动同一批开关的入口，走的是同一条漏斗**：新页的每次点按都调用 `SettingsRepository` 的既有 setter（`setShowWifi` / `setShowMobile` / `setShowValue` / `setShowBolt` / `setMobileTypeMode` / `setDualSim`），于是「本地 commit → push 到 remote preferences → 广播」三步与「常规」页逐字相同，两个进程的同步语义不必再想一遍。页里**没有任何回写或缓存**：它读的是父级传下来的实时快照，写完由父级重读，所以不会出现「页面上开着、状态栏没开」。
- **`TrioZones` 与 `TrioAppearance` 的分工，是这一轮最要紧的一条边界**：`TrioAppearance` 已经决定**每个槽位归谁**（`Ring` / `Rect` 的 `bolt` / `valueInGap` / `wifiInGap` / `typeInCentre` / `dual` …）。新页**不再自己推导任何一个开关**，`TrioZones.occupant(...)` 只做一件事：把 `TrioAppearance` **已经算好**的那个结果**放进**三格中的一格。理由是：如果这一页自己重推一遍「现在画着什么」，两边就可能对不上，而它偏偏是个**提议你去关掉某个东西**的界面——表要是错了，页面会提议关掉字形根本没画的东西。所以表里每一行都只读 `TrioAppearance` 的字段，不出现第二份 `showXxx && showYyy` 的组合。
- **`resident()` 必须按排布分开作答**（这是最容易搞错的一处）：圆环的默认样子是**数字住缺口、弧住环心**，所以「空缺口」属于**数字**；矩形的空顶槽属于**弧**。单个常量表会把其中一种排布答错，而错法是让空分区去开一个住在别处的开关。
- **点按规则**：点一格开关你看得见的那样东西，点空格开关本该在那里的那样东西。两个方向共用一条代码路径，因为调用方只是**翻转**返回部件的开关。**闪电刻意不是缺口的 `resident`**：它在缺口里排名第一，但只在充电帧存在，把它当空缺口的答案等于提议打开一个不会出现的东西。闪电打开时会**连带打开数字**（`drawsBolt()` 是 `boltWanted && value`，否则那一下看起来毫无反应），关掉时**不动数字**（用户要的是闪电走，不是数字走）。类型是三档模式，这一页只在「环内 ↔ 关闭」之间切；环外那档留「常规」页，因为环外标签由状态栏画在图标旁边，没有自己的热区。
- **命中判定与绘制共用一份变换**：`TrioRenderer` 抽出 `designTransform(w, h, appearance, out)`，`drawInto` 改成**问**它要变换而不是自己再算；`TrioZones.zoneAt` 反解同一个三元组。**`viewTransform`（加 inset 的那一步）放在 `TrioZones` 而不是 `TrioPreviewView`**：`View` 需要 Android `Context` 才能存在，留在 View 里的算术离线工装够不到——第一版就是这样，于是**手指真正走的那一半没有测试**。挪进 `TrioZones` 之后工装才测到真实路径。
- **工装断言的是「包含」而非「独占」，这是本轮的方法论教训**。第一版写的是「关掉一个部件，它只该从表里点名的那几格里掉墨」——这条**在正确代码上就失败了**：开关数字会让弧在环心与缺口之间**搬家**，关掉它还会让圆环 12 点开口**闭合**、弧线直接画过原来数字的位置。同一格里「数字没了、弧线来了」，按像素计数分不出来。修法是把**断言本身**改弱成真的那一句（关掉一个部件，不得从表里**没有**点名给它的格里掉墨），而**不是**把检查放宽到能过；两处混淆在代码里明说（`gapUsed` 一致时才严格比较；`gates(VALUE, BOLT)` 是唯一的前置条件）。**「测不过就改测试」和「测不过就把断言改成真的那一句」是两件不同的事，区别在于改完之后它还能不能抓到错。**
- **分界线是量出来的**：工装单独渲染每个部件、墨迹跑出它声称的那一格就失败。最窄的窗口是矩形上/中接缝——最粗弧线下沿 y≈48.06、最大数字上沿 y≈49.04，所以取 **48.5**；其余为圆环 38 / 92、矩形 96。矩形那条**竖直接缝**最初是一个手挑的常量（「从点列边缘往里挪 3 个单位」），**那次是错的**——正确做法是让它**就是**渲染器给中段文字划定的盒子（`TrioGeometry.RECT_TEXT_LEFT/RIGHT`）。详见 §6.16.1。
- **`geometryTab` 收敛成一张表**（`PartControls.geometrySliders()`）：长按弹窗要显示同一个部件自己的那几行，两份清单一定会漂。改写前后逐项核对：**16 个 setter 一个不少；旧 `geometryTab` 用到的 48 个不同字符串资源（`R.string.*` 去重后）在 `PartControls.kt` 里全部仍在**（`Compare-Object` 差集为空）。行门禁保留既有规则——**一行可以被别的开关门禁，但永远不能被自己的值门禁**。
- **手势用 Compose 的 `detectTapGestures` 而不是 `setOnTouchListener`**：后者在 `ACTION_DOWN` 返回 true 会吃掉事件，一个占大半屏的字形会让整页滚不动。
- **本轮计数**：`Prefs` 的 `KEY_*` 常量（**42**，含 2 个只读 legacy 键）、在用键（40）、`TrioSettings` 字段（40）、`SettingsRepository` setter（40）、设置页可写项（40）**全部不变** —— 新增的是一个**入口**，不是键。`enabled =` 的**字面**计数变了、语义没变：`SettingsScreen.kt` 从 **58 降到 33**，新增的 `PartControls.kt` 有 **19**（其中 **16** 条是 `SliderSpec` 表里的门禁 lambda，另 3 条在部件弹窗里：一个 `TooltipBox(enabled = true)`、一条颜色行的门禁、一处 `enabled = spec.enabled(a)`），合计 **52**。**这个数字不能跨文件比大小、也不能当门禁数量的证据**：16 条尺寸行的门禁只是从 `SettingsScreen.kt` 的 `enabled = a.xxx` 改写成了 `PartControls.kt` 里同一个表达式的 lambda，一处没多、一处没少（改写前后逐行核对过：16 个 setter 一个不少，旧 `geometryTab` 的 48 个不同字符串资源全部仍在）。要数门禁就**按文件分别数**，或者干脆数 `SliderSpec` 的条数（`write = { r, v ->` 出现 16 次）。
- **验证**：`:app:assembleDebug --offline` `BUILD SUCCESSFUL`；`tests\` 六个脚本全 `exit 0`，其中新增的 `tests\drawing-zones.ps1` 在补上 §6.16.1 的两条断言后打印 `PASS: 566 cases over 24 configurations and 539 renders`（修缺陷前是 `221 / 24 / 177`）。它还带两条**负对照**：把 `viewTransform` 的 `t[1] += pad` 改成 `pad * 2f`（inset 折两次）与整句删掉（完全不折），分别以 `painted ink at 537,69 resolves to no zone` 与 `... at 858,311 ...` `exit 1`。**上机验证仍缺**——真实手指落点、三格触感、长按弹窗观感都还没在设备上看过；设备当时离线。
- **未做**：上机验证（真实手指落点、三格触感、长按弹窗观感）；`drawing-zones.ps1` 是唯一编译 `TrioZones` 的工装（grep 过 `tests\*.ps1`），而它**没有一条断言覆盖「点按之后写的是哪个 setter」**——`DrawingScreen.tapZone` 的分支只在真机上按过才算数；离线能约束的只有 `TrioZones.tapTarget` 的真值表，也就是「该点哪个部件」，而不是「点了之后写了哪个键」。

#### 6.16.1 独立复核抓到的缺陷：矩形中段的接缝与「100」（已修，本轮同一批）

上面那一节是我自己写的，写完时 221 条全过。**但那份代码有一个我自己没测出来的真缺陷**，是独立复核（另一名 agent，按「不许改文件、只报告」的规则跑）找出来的，我复现并修掉了。记在这里，因为它的形状比缺陷本身更值得记。

- **缺陷**：矩形样式的**可点中段**是一个手挑的常量 `RECT_SEAM_LEFT/RIGHT = 18.2+3 .. 101.8-3`（宽 **77.6**），而渲染器允许文字画到的范围是 `RECT_TEXT_CLEAR`（宽 **83.6**，即 x `18.2..101.8`）。**同一个边界有两个独立常量**，而它们不相等。于是任何「落在 77.6 与 83.6 之间」的电量数字都**不会被缩**、却**落在中段之外**——它右边缘的笔画被判定为下一格。
- **后果**：`100%` + 大字号时，点在数字**右边缘**上会**把移动信号表关掉**（`setShowMobile(false)`），而不是翻转电量数字。这是**静默写错一个持久化设置**，不是显示问题。实测（960²）：`valueSize=38/42`、电量 `100` 时，数字墨迹右沿到 **98.90 / 101.30**，越界；`valueSize=36..44 × 5 档电量`扫下来共 22 组越界，**每一组都是三位数的 `100`**。
- **修法**：接缝不再手挑，改成**就是渲染器给文字划定的那个盒子**——在 `TrioGeometry` 里新增 `RECT_TEXT_LEFT/RIGHT`（两列点的内边缘），`RECT_TEXT_CLEAR` 由两者相减导出，`TrioZones` 的接缝直接引用同一对常量。**一处定义，两处引用**。
- **为什么原来那个「往里挪 3 个单位」的直觉是错的**：我当时的注释写「挪进点列与文字之间的空带，代价为零，因为文字居中且比 clear width 窄」。**这句话是错的**，而且是它让那个常量通过了复核——`clear width` 恰恰**就是**两列点之间的空隙，文字被允许一直用到它的边界，所以**根本不存在那条空带**。实测把 margin 从 0 调到 1/2/3/6，越界像素数是 **0 / 334 / 1568 / 3324 / 27542**：**margin 只能取 0**。这条注释按 `docs/SWITCHES-REVIEW.md` §6.7 的规矩就是缺陷本身（注释为一个错误决定提供了理由），已改写成事实。
- **工装为什么没抓到**：`drawing-zones.ps1` 原先所有 24 种配置都在**同一个电量 `79`** 上渲染，而矩形/圆环的电量数字是**文字**——「79」比「100」窄。**单一 level 的扫描从不测量最宽的那一种**，这就是盲区的形状。修法是两件事一起做：(a) 把 level 变成**要扫的维度**（新增 `LEVELS = {79, 5, 100}`，24 种配置 × 3 个电量，断言数 221 → **566**）；(b) 加一条**直接针对墨迹**的断言 `rectCentreHoldsItsText()`：对 `MIN_VALUE_SIZE..MAX_VALUE_SIZE` **每一个**字号，渲染三位数 `100`，断言它的**每一个像素**都反查到 `CENTRE`；并加一条镜像断言（点列墨迹必须反查到 `BOTTOM`），防止「把中段撑大到吞掉点列」这种假修法。
- **负对照（本轮新增）**：把接缝改回旧的 `+3f` → 工装报 `rect/w1v1m1/c0d0t1g0s00/v44y44/a16r16/L100: VALUE loses ink only in the thirds that name it: got <BOTTOM> want <>`，`exit 1`。逐档试 `0.001 / 0.01 / 0.5 / 1.0` 个设计单位：**前三个 `exit 0`、1.0 才 `exit 1`** —— 也就是说**小于约 1 个设计单位的偏移，这套工装在 960² 分辨率下测不出来**（亚像素）。这一点必须写明：修好之后接缝是**导出**的、不再可调，所以这个探测下限只是记录，不是遗留风险；但如果将来有人重新引入一个可调的接缝，**0.5 个单位的错误会悄悄通过**。
- **教训（比缺陷本身更值得记）**：①**两个常量描述同一个边界，就一定会漂**——即使写下它们的是同一个人、在同一天、还配了注释。②**注释里的「代价为零」是一个待验证的断言，不是理由**；这一条注释的代价正好是让错误常量活下来。③**「测不过就改测试」和「测不过就把断言改成真的那一句」的区别，在于改完之后它还能不能抓到错**——上一节我为了「关掉数字会连带搬走弧线」把断言改弱成包含，这一节我为了同一个功能把接缝改成导出并补上正面断言；两次都改了测试，但只有一次是让步。④**单一维度的扫描会漏掉最宽的那一种输入**（这里是三位数 `100`）。


