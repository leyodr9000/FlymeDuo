# FlymeDuo

把 Flyme 状态栏里的 **Wi-Fi、移动信号、电量** 合并成一个仿 iPhone「Duo」风格的三合一图标。

FlymeDuo 是 [HyperDuo](https://github.com/yixing233/HyperDuo) 的 **Flyme 适配版**：渲染内核
（几何、配色、组件编排）与上游一致，系统界面侧的注入点全部换成了 Flyme 的实现——
`FlymeBatteryMeterView`、`QSStatusBar`、`KeyguardStatusBarView`、`NotificationPanelView`。

## 适配了什么

### 三个位置，一颗环

- **普通状态栏**：环画在 `system_icons` 的电池槽里；
- **控制中心**：`QSStatusBar` 顶栏（可在生效范围里排除）；
- **通知中心 / 锁屏头部**：`keyguard_status_bar`（Flyme 里它被 include 进通知面板，
  是同一颗视图，不会被误判成状态栏）。

三处的电池、Wi-Fi、信号槽都由模块接管：画环时原厂图标让位，排除时整体交还原厂。

### 整体缩放：以状态栏高度为基准

Flyme 的测量把电池视图锁死在电池图标的本征盒里（约 10dp），环的 120 单位设计空间被压扁。
适配版改写了 `onMeasure`：**三个位置统一以状态栏高度（140px ≈ 43dp）为 100%**，普通状态栏
那一行只有 65px，装不下就对父链放开裁剪、溢出绘制进窗口余量。设置里的「图标整体尺寸」
滑条（60–100%）三处一致生效。

### 生效范围

**全部 / 排除控制中心 / 排除通知中心 / 排除两者**。被排除的位置保留 Flyme 原厂电池与图标，
显示方式由原厂决定。区域按真实父链归类、挂载后重判——Flyme 在挂载前就测量视图，
过早归类会把所有面误判成状态栏（.33 之前排除失效的根因）。

### 其余能力

与上游一致：环内电量数字 / Wi-Fi 弧 / 信号点 / 网络类型 / 充电闪电可逐项开关或交还系统，
胶囊布局，环外信号卡、5G 标签（按所在格宽度自适应字号）、充电功率、电池温度。架构与实现
细节见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)（上游文档，Flyme 注入点在
`FlymeHooks.java`）。

## 安装

| 项目 | 要求 |
| --- | --- |
| 系统 | Flyme AIOS 2（Android 16，API 36）；同代 Flyme 理论可用 |
| 框架 | LSPosed（libxposed API 102+），已在 2.2.1 上验证 |

1. 从 [Releases](https://github.com/leyodr9000/FlymeDuo/releases) 下载最新的
   `FlymeDuo-*.apk` 并安装。
2. LSPosed → 模块 → 启用 **FlymeDuo**，作用域勾选 **系统界面（`com.android.systemui`）**。
3. 重启系统界面：

```sh
adb shell "su -c 'pkill -f com.android.systemui'"
```

装完 pkill 后如果状态栏变回原厂：LSPosed 把**每一次系统界面进程重启**都计为一次「崩溃」，
累计 4 次会进入模块安全模式——去 LSPosed 重新勾选模块即可，不是模块崩了。

## 排查

```powershell
adb logcat -v time -s LSPosedFramework:* | Select-String HyperDuo
```

模块在系统界面里的输出都由框架代打（tag `LSPosedFramework`）。启动行
`flyme adapter build x.y.z, hooks=N` 确认适配层已加载；每个电池宿主会打
`battery region=…`（归到哪个面）和 `battery box …`（行高、边长、排除判定），
这两行能直接定位「不生效 / 大小不对」属于哪一类问题。

## 开发

```sh
gradle assembleRelease -PhyperduoVersionName=1.7.5-flyme.38 -PhyperduoVersionCode=10742
```

分支 `flyme-adapter`，上游同步采用 rebase。渲染内核（`TrioRenderer` / `TrioGeometry` /
`TrioConfig`）尽量不动，Flyme 差异全部收敛在 `FlymeHooks.java`。

## 致谢

- **[HyperDuo](https://github.com/yixing233/HyperDuo)** —— 本 fork 的一切基础
- **FlymeBarSizer 群友 知秋** —— Flyme 适配参考
- 设置界面基于 Compose + [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)

## 许可证

**GNU General Public License v3.0（GPL-3.0）**，全文见 [LICENSE](LICENSE)。

Copyright (C) 2026 yixing233 与 HyperDuo 贡献者；FlymeDuo 修改部分同样以 GPL-3.0 发布。
分发编译好的 APK 时需要一并给出对应源码，指向本仓库该版本的 tag 即可。没有担保。
