# HyperDuo 开发文档

面向二次开发与问题定位。用户向的安装、设置说明见 [README](../README.md)。

2026-10-18 上中下改为「多选 + 优先级排序」：每处不再是一个槽位，而是一份**有序候选列表**。
每次只画列表中第一个"本帧能画"的组件，因此列表是回退链（Wi-Fi 断线时电量数字顶上来），
而不是叠放。存储从打包 int 改为字符串 `ring_layout`，形如 `"3,1|2|7,8"`（`|` 分三处，`,` 分优先级，
空段表示该处为空）。新装默认**三处全空**，用户自行添加；旧配置经 `Prefs.migrateLayout` 自动转成
每处一项的列表，外观不变。新增 `TrioZones.withPiece`（添加到队首/提升/跨处移动）、
`withoutAt`（移除单项，露出后一位）、`moveWithin`（上移下移）。绘制页每处展开优先级列表，
用 ↑/↓/× 调整。`ring_slots_v2` / `ring_slots` 降级为只读迁移来源。

2026-10-18 修正环形墨迹盒：`INK_*` 改为由弧推导，且**按滑杆最粗描边**（16）而非参考描边（8）
计算端帽——两者不同（默认描边是 14），按 8 算会让真实绘制溢出自己的适配框。同时把信号点珠子
排除在墨迹盒之外：墨迹盒决定缩放，把随画面增减的珠子算进去会让整个字形随编辑跳动。

2026-10-18 删除自动布局：圆环现在**只有**三槽一种布局。`Prefs.RING_AUTO`（`-1`）已删除，
旧值经 `migrateRingSlots` 转为 `Prefs.RING_DEFAULT_SLOTS()`（上=电量、中=Wi-Fi、下=移动信号）。
`TrioAppearance.Ring` 不再有开关推导的仲裁路径，`TrioZones.occupant(ring)` 只读槽位，
`TrioRenderer.drawRingLayout` 直接转 `drawFreeRing`；随之删除 `drawWifi`/`drawLevelDots`/
`drawValue`/`drawCentreType`/`drawGapType` 五个死方法。`Part.DUAL` 与「双卡信号」开关一并移除：
一槽只放一件，双卡改用「卡一信号点」「卡二信号点」两个独立部件。
同时上下缺口改为等宽（见 `GAP_WIDTH_DEG`）、信号点改为穿在环上的珠子、环形墨迹盒改为由弧与珠子
推导（`checkInkBox` 在类加载时校验字面量未漂移）。`TrioZones.assign` 对圆环改为直接写槽。

2026-10-17 自由圆环收尾：新增 `ring_slots` 独立配置，三位一组存储上/中/下的部件 ID，`-1` 保留自动布局。
2026-10-17 环内分卡信号点：新增独立组件「卡一 / 卡二」（`PART_SIM_1` / `PART_SIM_2`），各画一行四点，可放进圆环任意槽位，开关各自独立、尺寸共用一份。九件部件装不进每槽 3 bit 的旧编码，因此布局改存
新键 `ring_slots_v2`（4 bit/槽），`ring_slots` 降级为只读迁移来源（见「卡一 / 卡二」一节）。
首次绘制编辑通过 `TrioZones.editableRingSettings` 保存当前预览的占用关系；临时缺信号/未充电不重排槽位。
原生图标交接由 `TrioAppearance` 的 ownership 规则统一决定。验证入口、当前构建与真机检查见
[自由三槽验证记录](validation/ring-free-2026-10-17.md)。下文早期固定区域与部件优先级描述仅适用于自动布局。

## 项目构成

| 项 | 值 |
| --- | --- |
| `namespace` / `applicationId` | `io.github.yixing233.hyperduo` |
| `versionCode` / `versionName` | 源码里 2 / 1.1；发布时由 `release.ps1 -Version` 注入（见「发布流水线」） |
| `minSdk` / `targetSdk` / `compileSdk` | 29 / 36 / 37 |
| Java / Kotlin JVM target | 21（不是选择，见「构建」） |
| 框架接口 | libxposed **API 102**（`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`） |
| 作用域 | `com.android.systemui`（`app/src/main/resources/META-INF/xposed/scope.list`） |
| 入口 | `io.github.yixing233.hyperduo.HyperDuoModule`（`java_init.list`） |

应用侧有两个入口：`.ui.MainActivity` 带 `de.robv.android.xposed.category.MODULE_SETTINGS`
分类（LSPosed 管理器里点击模块进入），`.ui.MainActivityAlias` 是桌面 LAUNCHER 图标。

依赖：`compileOnly io.github.libxposed:api:102.0.0`、`implementation io.github.libxposed:service:102.0.0`、
`androidx.core:core-ktx:1.19.0`、`androidx.activity:activity-compose:1.13.0`、
`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`（更新器要在主线程外做网络、
回到主线程报状态，Compose 本身没有调度原语）、`kotlinx-serialization-json:1.11.0`（miuix-nav 的
路由要 `@Serializable`）、Miuix **0.9.4**（`miuix-ui-android` / `miuix-preference-android` /
`miuix-icons-android` / `miuix-blur-android` / `miuix-shader-android` / `miuix-nav-android`）。
`miuix-blur` 声明 minSdk 33，本模块用 `<uses-sdk tools:overrideLibrary>` 把它压回 29 ——
它内部每个效果都由 `isRuntimeShaderSupported()` 门控，33 以下是退化成普通绘制而不是崩溃。

## 源码结构

```
io.github.yixing233.hyperduo
├─ HyperDuoModule.java   入口（extends XposedModule，按 META-INF/xposed/java_init.list 实例化）
├─ HyperDuoApp.kt        Application：注册 XposedServiceHelper 监听，持有 service
├─ Prefs.java            键名 / 默认值 / 上下界（两端唯一真源）
├─ TrioSettings.java     跨进程配置快照（值对象，刻意不依赖 Xposed API）
├─ TrioConfig.java       hook 侧：绑定 remote preferences 并热更新快照
├─ TrioHooks.java        全部 hook 安装与容器折叠逻辑
├─ GlyphDrawable.java    把三合一画进 MIUI 电池图标自己的 drawable 槽
│                      （不建窗口：画布 / bounds / 裁剪都归系统）
├─ TrioState.java        电量 / 信号等级 / 前景色等运行期状态
├─ BatteryPresence.java  粘性电池广播采样：是否接着充电器 / 充电功率 / 电池温度
├─ TrioGeometry.java     120×120 设计空间的几何常量
├─ TrioAppearance.java   外观规则：每个元素该不该画、画在哪（渲染器与预览共用）
├─ TrioZones.java        三分区热区规则：上/中/下各是谁、点在哪儿（纯 Java，无 Android 类型）
├─ TrioRenderer.java     纯 Canvas 描边绘制（状态栏与预览共用）
├─ TrioPreviewView.java  设置界面里的预览 View（也负责把触点解析成分区）
├─ Refl.java             反射小工具
└─ ui\
   ├─ MainActivity.kt         ComponentActivity + MiuixTheme
   ├─ SettingsScreen.kt       全部设置项 + 预览卡片 + 颜色弹窗 + 更新卡片
   ├─ DrawingScreen.kt        「绘制」分页：大预览 + 三块热区 + 预览状态 + 部件设置弹窗
   ├─ PartControls.kt         尺寸行的唯一声明表（尺寸页与部件弹窗共用）
   ├─ SettingsRepository.kt   读写本地 prefs 并写穿到 remote preferences
   └─ UpdateController.kt     检查 / 下载 / 调起安装器（GitHub Releases）
                              三个 URL 常量同处一处：API、RELEASES_PAGE、REPO_HOME
```

## 构建

需要 JDK 21、Android SDK（platform 37 + build-tools 37.0.0）与 Gradle 9.8。本仓库把整套工具链
与依赖都放在 `.tools\`：

```powershell
. .\scripts\env.ps1
& $env:GRADLE_HOME\bin\gradle.bat --offline :app:assembleDebug
```

`scripts\env.ps1` 只是把下面这几个变量设好，等价于手动导出：

```powershell
$T='C:\code\HyperDuo\.tools'
$env:JAVA_HOME="$T\jdk\jdk-21.0.12.1+1"
$env:GRADLE_USER_HOME="$T\gradle-home"
$env:ANDROID_HOME="$T\sdk"
& "$T\gradle\gradle-9.8.0\bin\gradle.bat" --project-dir C:\code\HyperDuo :app:assembleDebug --no-daemon --console=plain
```

产物：`app\build\outputs\apk\debug\app-debug.apk`。

**JDK 21 是硬性要求，不是偏好。** Miuix 0.9.4 的全部产物都是 Java 21 字节码（class 文件
major 65），而 `miuix-nav` 的入口 `rememberNavController` / `entry` 是 inline composable：
Kotlin 拒绝把 21 的目标 inline 进 17 的输出，报

```
Cannot inline bytecode built with JVM target 21 into bytecode that is being
built with JVM target 17. Specify proper '-jvm-target' option.
```

而且 AGP 会硬性校验两侧一致（`Inconsistent JVM targets between Java and Kotlin
compile tasks: 17 and 21.`），所以 `compileOptions` 与 `kotlin.compilerOptions.jvmTarget`
必须同时是 21，也就必须有一个真能产出 21 class 的 `javac`——只把 Kotlin 单独升到 21
在 JDK 17 上编不过（`无效的源发行版：21`）。`build.ps1` 的离线管线仍然用
`-source 8 -target 8`，JDK 21 接受这两个值（有 deprecation 警告），只是共用同一个 JDK。

**构建需要联网。** `io.github.libxposed` 已 vendored 到 `.tools\maven`，但 miuix 0.9.4
与 miuix-nav 不在本地 Gradle 缓存里，`--offline` 会解析失败。

要点：

- `settings.gradle.kts` 把 `.tools\maven` 作为**第一个**仓库（承载 `io.github.libxposed:api/service`
  的本地副本），排在 `google()` / `mavenCentral()` 之前，所以 libxposed 部分不依赖网络
  （miuix 部分仍然依赖，见上）。
  `dependencyResolutionManagement` 用的是 `FAIL_ON_PROJECT_REPOS`，因此**不要在模块里写
  `repositories {}`**。
- `android.useAndroidX=true`（Compose 必需）。Compose 编译器是
  `org.jetbrains.kotlin.plugin.compose` 2.3.21，与 Kotlin 2.3.21 同版本。
- `gradle.properties` 中 `android.suppressUnsupportedCompileSdk=37`、
  `org.gradle.configuration-cache=false`、`org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8`。
- 手写的 `build.ps1`（javac/d8/aapt2 离线管线）**已弃用**，保留仅供查阅：它无法编译
  Kotlin/Compose 与 `res/`，这正是迁移到 Gradle 的原因。
- 本仓库**没有 Gradle wrapper**（工具链是 vendored 的），本地用 `.tools\gradle\gradle-9.8.0`。

### 发布构建

```powershell
& "$T\gradle\gradle-9.8.0\bin\gradle.bat" --project-dir C:\code\HyperDuo :app:assembleRelease `
  -PhyperduoVersionName=1.2 -PhyperduoVersionCode=10200 --no-daemon --console=plain
```

`app\build.gradle.kts` 顶部读取 `hyperduoVersionName` / `hyperduoVersionCode` 两个 project
property，缺省时回落到 `1.1` / `2`。**发布构建不能加 `--offline`**：R8 需要一个未 vendored 的
`org.jetbrains.kotlin:compose-group-mapping`，离线会以
`Execution failed for task ':app:produceReleaseComposeMapping'` 失败。

release 开启 `isMinifyEnabled` + `isShrinkResources`，但 `proguard-rules.pro` 里
`-keep class io.github.yixing233.hyperduo.** { *; }` 保证了 hook 侧与更新器都不被剥离 —— 这是必须的，
因为 hook 类由框架按 `java_init.list` 反射实例化，R8 看不到这条引用。

### 签名

`signingConfigs.create("hyperduo")` 使用 `rootProject.file(".tools/debug.keystore")`，
`storePassword` / `keyPassword` 均为 `android`，`keyAlias` 为 `hyperduo`。debug 与 release
共用同一个签名，目的是让就地升级覆盖已安装的模块时能继续保持模块授权。

`.tools/debug.keystore` **是入库的**（`.gitignore` 显式放行），因此发布不需要任何 secret 就能
产出与本地 debug 包、与应用内更新互相覆盖的 APK。

### 发布流水线

**写说明之前先读 [RELEASE-NOTES-STYLE.md](RELEASE-NOTES-STYLE.md)。** 那份规则规定了固定的三节
结构（功能添加 / 功能修复 / 功能变更）、每一条的写法，以及五类绝对不能出现的内容 ——
「未验证」「未包含」、issue 对照表、代码标识符、内部取舍的来龙去脉。这些不是风格偏好，
都是实际写错过的地方。

发布是**一条本地命令**，没有 CI。`release.ps1` 发完主仓后**自动**调用 `release-module.ps1`，
把同一个 APK、同一份说明发到 LSPosed 模块仓（模块页的下载链接走这里），两个仓库永远同版本：

```powershell
.\scripts\release.ps1 -Version 1.0 -NotesFile .\docs\release-notes\release-notes-1.0.md
.\scripts\release.ps1 -Version 1.0 -DryRun             # 只构建，不打 tag、不建 Release、不发模块仓
.\scripts\release.ps1 -Version 1.0 -SkipModuleRepo     # 只发主仓，跳过模块仓

# 模块仓那步失败（主仓已发出）时单独补齐；或手动重发/覆盖模块仓的某个版本
.\scripts\release-module.ps1 -Version 1.0 -VersionCode 10000 `
    -Apk .\dist\HyperDuo-1.0.apk -NotesFile .\docs\release-notes\release-notes-1.0.md
```

`release.ps1` 按顺序做六件事，任何一步失败都立刻停下：

1. 校验版本号（`major` / `major.minor` / `major.minor.patch`，缺段按 0 计），算出
   `versionCode = major*10000 + minor*100 + patch`。
2. 不带动 `--offline` 地跑 `:app:assembleRelease`，把版本号用 `-PhyperduoVersionName`
   / `-PhyperduoVersionCode` 注入。
3. 复制成 `dist\HyperDuo-<version>.apk`，再用 `aapt2 dump badging` **回读 APK 里的版本**，
   与预期不符就中止 —— 版本注入写错时这一步会立刻暴露，而不是等用户装上才发现。
4. 打并推送 `v<version>` tag，创建 GitHub Release（`git credential fill` 取凭据，不存明文
   token），最后上传 APK asset。
5. **清掉主仓里其它所有 Release**，只留刚发的这一版（见下）。
6. 调 `release-module.ps1` 发模块仓：同 APK、同说明，`-Force` / `-KeepOldReleases`
   原样传递。模块仓自己校验 badging、上传 asset、同步 `module-README.md`、清理旧
   Release。说明为空时模块仓会拒绝发布 —— LSPosed 规范要求 Release 正文就是
   changelog，所以发版必须带 `-Notes` 或 `-NotesFile`。**这一步失败不会撤回主仓的
   Release**，直接重跑 `release-module.ps1` 补齐即可。

#### 只保留最新版 Release

两个仓库都遵循同一条策略：**Releases 页永远只有刚发布的这一版**。这是有意为之，不是清理
遗留 —— 用户要的是「点进 Releases 就是最新版」，旧版本留在列表里只会让人下错。

- **tag 一律保留**。删的只是 GitHub 上的 Release 记录（标题、说明、asset）；`v1.0`…
  `v1.4` 这些 git tag 原样不动，历史源码快照仍然能 `git checkout v1.3` 检出。
- 两个脚本都在**最后一步**做清理（asset 已上传、发布已经成功之后），所以清理失败绝不会
  影响这次发布；失败时脚本会以非零退出告诉你，但版本已经发出去了。
- **draft 不删**：`Remove-StaleReleases` 的过滤条件是
  `$_.id -ne $KeepId -and -not $_.draft`，没发布的草稿留着。
- 需要留旧版时加 `-KeepOldReleases`（两个脚本都有这个开关）。
- **后果：旧版本的 changelog 也随之消失**。Release body 不是版本库里的文件，删了 Release
  就没有第二份副本。所以 `-NotesFile` 指向的笔记**是入库的**：`release-notes-<version>.md`
  放在 `docs\release-notes\`，与文档收在一起（根目录只留两份 README）。现存
  `release-notes-1.1.md` / `release-notes-1.3.md` / `release-notes-1.4.md` 等 17 份
  （1.1 与 1.3 的 Release 已被清理删除，正文只剩这里这一份；它们原先只在被忽略的
  `work\notes-*.md` 里）。**发新版时把笔记写成 `release-notes-<version>.md` 放进
  `docs\release-notes\` 再入库**，否则「只留最新版」等于把历史更新说明一起丢掉 ——
  而 tag 只记录源码，不记录说明。

**容易误判的一点**：删掉 Release 之后，`/releases/tag/v1.3` 这类地址**仍然返回 HTTP 200**
（它退化成纯 tag 页）。判据不是状态码，而是：页面 `<title>` 从 `Release HyperDuo 1.3`
变成 `Release v1.3`，且**页面上不再有 `HyperDuo-*.apk` asset 链接**；`/releases` 列表页只剩
一条 `/releases/tag/` 链接。

`.tools\debug.keystore` **是入库的**（`.gitignore` 显式放行），所以发布不需要任何 secret，
产出的 APK 与本地 debug 包、与应用内更新互相覆盖。

**文件名是契约的一部分**：更新器取 Release 的第一个 `.apk` asset，这个名字就是设备上的下载
文件名。

**为什么一个版本必须带 APK**：应用内更新器读的是 `/releases/latest`。如果一个 Release 存在却
没有 APK asset，用户会看到一个装不上的「新版本」。所以宁可失败也不要发空 Release。

**为什么不用 GitHub Actions**：最初的 `release.yml` 在 `android-actions/setup-android@v3` 这
一步就失败了（`platforms;android-37.0` 是 `compileSdk 37` 这种很新的版本化平台，托管 runner 上
的 setup-android 拿不到），后续步骤全部跳过。修这个要有权限调试别人的 action，而本地产出 APK
只需要几十秒且工具链已经 vendored，所以改成 `release.ps1`。发布脚本与本地构建共用同一套命令，
不会出现「CI 能过、本地过不了」的分叉。

#### 改 `release.ps1` 时的两个坑

**必须保留 UTF-8 BOM**（`release.ps1` 是 UTF-8 **with BOM**）。这个 shell 是 Windows
PowerShell **5.1**，它读无 BOM 的文件时用 ANSI 代码页（本机是 GBK/936）。中文注释被解成乱码后，
某个多字节序列会**吞掉后面的引号**，于是报出一堆语法错误（`Missing expression after ','`、
`The string is missing the terminator`），而那些行本身完全正确。用 `read` 工具或编辑器看到的
内容是对的，所以这类报错极易被误诊。给文件加回 BOM 即可：

```powershell
$p='C:\code\HyperDuo\release.ps1'
$t=[System.IO.File]::ReadAllText($p,[System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText($p,$t,(New-Object System.Text.UTF8Encoding($true)))
```

**不要用 `& git … 2>&1`**。`$ErrorActionPreference='Stop'` 会把原生程序写到 stderr 的正常输出
（git 的 `Everything up-to-date`、gradle 的弃用提示）升级成**终止错误**，一个成功的步骤就会中止
整个发布。脚本里的 `Invoke-Native` 临时把 `$ErrorActionPreference` 放宽到 `Continue`，只用 exit
code 判成败 —— 调外部程序一律走它。


### 发版前必查的三件事（2026-10-23，1.7.3 踩过）

**1. 确认在 `main` 上。** 那次整个会话都在 `pr42-rebased`（一个等待评审的 PR 分支）上干活，
所有 UI 改动都叠在 PR 的两个提交之上。直接在它上面发版，就等于把未评审的 PR 一起发了。

```powershell
git branch --show-current                  # 必须是 main
git log --oneline origin/main..HEAD        # 有输出就先想清楚那些提交该不该发
```

**2. 跑一次 `assembleRelease`，不要只看 debug。** `release.ps1 -DryRun` 就是干这个的。
那次 debug 一直能过、APK 也一直装得上，因为设备上装的是**分支**构建的包；
切回 main 后 release 构建立刻报了 20 多个 `Unresolved reference` —— 字体功能的
`fontPath` / `FontInstaller` / `font_*` 全在未合入的 PR 上。
debug 通过**不是** main 可用的证据。

**3. 脚本报「发布失败」不等于什么都没发生。** 模块仓那步因 SSL 中断，报错信息是
「主仓已发布成功；直接重跑 release-module.ps1 … 补齐」—— 但实际状态是
**Release 已建、APK 没传上去**。重跑会撞上
`release … already exists. Re-run with -Force to overwrite`。

排查顺序：

```powershell
# 先看两边 Release 的附件在不在，别只看脚本的退出码
gh release view v1.7.4      --repo yixing233/HyperDuo --json assets
gh release view 10704-1.7.4 --repo Xposed-Modules-Repo/io.github.yixing233.hyperduo --json assets

# 附件为空就补传，要带 -Force
.\scripts\release-module.ps1 -Version 1.7.4 -VersionCode 10704 `
    -Apk .\dist\HyperDuo-1.7.4.apk `
    -NotesFile .\docs\release-notes\release-notes-1.7.4.md -Force
```

**网络。** `gh` 和 `git push` 直连 `api.github.com` 通常可以，但模块仓那步偶尔报
`The SSL connection could not be established`。给那条命令加系统代理即可：

```powershell
$env:HTTPS_PROXY='http://127.0.0.1:7890'; $env:HTTP_PROXY=$env:HTTPS_PROXY
```

`git push` 本身不走系统代理，需要时用 `git -c http.proxy=… push`。

**分支卫生。** 合入 PR 后删掉它的本地分支（内容已在 main）。保留作者原始的提交
（如 `pr42-review`）便于日后追原始 diff，但不要留 rebase 出来的中间分支 ——
它们与 main 的 commit id 不同，`git branch --merged` 看不出已合并，
容易让人以为还有东西没进。

### 安装与观测

```powershell
& C:\code\HyperDuo\scripts\install.ps1              # 安装 + 打开设置界面 + 提示重启 SystemUI
& C:\code\HyperDuo\scripts\install.ps1 -InstallOnly
& C:\code\HyperDuo\scripts\install.ps1 -LogOnly     # 只跟日志
```

脚本内硬编码了 `$Root='C:\code\HyperDuo'`、`$Adb='C:\Program Files\UotanToolbox\Bin\platform-tools\adb.exe'`、
`$Package='io.github.yixing233.hyperduo'`；日志用 `adb logcat -v time -s LSPosedFramework:* AndroidRuntime:E *:S`。

手动启动设置界面：

```powershell
adb shell am start -n io.github.yixing233.hyperduo/.ui.MainActivity
```

日志 tag 是 **`LSPosedFramework`**（框架代打的），**不是** `HyperDuo`；行形如
`(com.android.systemui)[io.github.yixing233.hyperduo,HyperDuo,<id>,0,1] <msg>`。启动时会打印一行
`HyperDuo installed, hooks=9`；固件改了方法名时会打 `skip <id>: method not found`。

需要重新定位某个容器时，把 `TrioHooks.DEBUG_DUMP` 改成 `true` 再构建，即可打印容器的屏幕
坐标、尺寸与全部子视图（含 slot / 宽高 / alpha / visibility）。

## 配置通道

设置走 libxposed 的 remote preferences，组名 / 文件名同为 `Prefs.NAME = "hyperduo_settings"`；
键与默认值集中在 `Prefs.java`，上下界也在那里（第十七轮后共 41 个 `KEY_*` 常量，其中
`KEY_SHOW_MOBILE_TYPE`、`KEY_OUT_TYPE_SIZE` 与 `KEY_RING_SLOTS` 是只读的迁移遗留键；第四十轮加
`KEY_SHOW_SIM_1` / `KEY_SHOW_SIM_2` 与 `KEY_RING_SLOTS_V2` 三个，实际读写的 41 个键见配置项参考）。

- **写侧**（设置 App）：`HyperDuoApp.onServiceBind` 拿到 `XposedService`，之后每次改动都
  `getRemotePreferences(NAME).edit().putX(...).commit()`，并把同一份写进本应用的
  `SharedPreferences`（即 `shared_prefs\hyperduo_settings.xml`）。服务是异步绑定、也可能随时
  死亡，所以 `SettingsRepository` 每次写入都实时取 `HyperDuoApp.xposedService`，**不缓存
  service**；绑定成功时还会 `syncAllToFramework()` 全量重放这 39 个键，补齐框架缺席期间的改动。
  **每新增一个键都必须同时加到 `syncAllToFramework()`**，否则该键在框架重连后不会下发。
- **读侧**（hook 进程）：remote `SharedPreferences` 是**只读**的，但支持
  `registerOnSharedPreferenceChangeListener`，框架会实时投递变更。`TrioConfig` 注册一个监听器，
  每次回调重新读成一份 `TrioSettings` 快照并 `invalidateHosts()` 重绘宿主 ⇒ **改设置不需要
  重启 SystemUI，也不需要热重载**。
- 用 `commit()` 而非 `apply()`：值必须在对下一帧绘制可见。

### Compose 注意事项

任何 `OverlayDialog`（本项目的颜色选择弹窗）都必须写在 `Scaffold` 的 content lambda **内部**。
Miuix 的 `MiuixPopupHost` 从 `LocalRootDialogStates` / `LocalDialogStates` 读取待渲染的弹窗列表，
而这两个 CompositionLocal 只由 `Scaffold` 提供；写在外部会注册到一个没人渲染的孤儿列表，
弹窗**静默不出现且不报错**。

这条约束对重启确认弹窗同样成立：它用的就是 `OverlayDialog`，所以也必须留在 `Scaffold` 的 content
lambda 内。`window.*` 下的组件（`WindowDialog`、`WindowBottomSheet` 等）**不受这条约束**：它们自己开一个
真正的 `androidx.compose.ui.window.Dialog`，与 `Scaffold` 的弹窗宿主无关，因此不依赖任何父级。本项目
没有用它们：多引入一种弹窗形态不如复用颜色选择弹窗已有的那种。

### 重启系统界面

顶栏右侧的「重启系统界面」按钮需要 root。它执行的是 `su -c "killall com.android.systemui"`，与
README 里给的手工命令一致——用 `su -c` 而不是往裸 `su` 里喂命令，是为了让退出码属于真正要执行的那条
命令：授权被拒绝与 `killall` 本身失败都表现为非 0，不必从输出里去猜。stdout 与 stderr 合并后**先读完
再 `waitFor`**，否则 shell 写满无人读的管道会一直阻塞，`waitFor` 永不返回。

状态机在 `ui/RestartController.kt`（`Idle` / `Running` / `Done` / `Failed`）。它和 `UpdateController`
一样被提到 screen 级持有：确认弹窗被关掉后 kill 可能仍在进行，root 授权弹窗也不一定随着弹窗消失。

确认弹窗复用颜色选择弹窗那套 `OverlayDialog`，因此和它一样必须写在 `Scaffold` 的 content lambda 内
（见上一节）。kill 进行中把 `enabled` 置为 `false` 并让 `onDismissRequest` 直接返回，此时取消/确认按钮
都不可点、点击外部与返回键也都不关闭——用户可能正对着 root 授权弹窗，此时关掉确认弹窗会让结果无处可报。
失败文案沿用更新卡片「本地化标题 · 原始诊断文本」的拼接方式，诊断文本不翻译。**这条通路不需要新增
`Prefs` 键**，也不经过配置通道。

### 颜色恢复默认的确认

颜色页的「恢复默认」同样先弹确认。它和重启确认共用同一套 `OverlayDialog` 外壳（`ResetColorsDialog`），
所以同样必须在 `Scaffold` 的 content lambda 内。理由是这条操作会一次性丢掉六种自定义颜色（三种状态 ×
深/浅背景）且没有撤销，误触的代价与重启同一个量级。

弹窗打开期间只翻转一个 `resettingColors` 布尔值，**不在打开时就重置**：确认与取消走的是同一条 `update`
漏斗，写操作只发生在确认回调里。该行此前没有 `enabled` 守卫，顺带补上 `a.glyph && a.roleColors`——
在状态颜色总开关关闭时它重置的是一组不生效的值，和上面几行保持一致的灰显更合理。

### 顶栏模糊

顶栏用 `miuix-blur` 的 `Modifier.textureBlur` 采样页面内容做磨砂玻璃，而不是画一层不透明底色。
两个修饰符缺一不可：页面 `LazyColumn` 挂 `Modifier.layerBackdrop(backdrop)` 把自己录进一个
graphics layer，顶栏再通过同一个 `backdrop` 采样它——捕获源与采样面是 `Scaffold` 里两个不同的兄弟槽位，
库内部靠 `layerCoordinates.localPositionOf()` 对齐全局坐标，不需要嵌套。

- **必须自己门控，不能只依赖库**：库在 `isRuntimeShaderSupported()` 为假时会跳过特效本身
  （`DrawBackdropNode.draw()` 首行 `if (!enabled) { drawContent(); return }`），但这对本项目不够——
  模糊顶替的是顶栏自己的背景，而 `TopAppBar` 把传入的 `modifier` 应用在自身 `background(color)`
  **之前**，所以未门控的老设备上会得到一个完全没有背景的顶栏，列表会直接从标题上滚过去。
  因此调用点用 `if (barBlurSupported)` 同时切换 `modifier` 与 `color`（支持时 `Color.Transparent`，
  否则 `surfaceColor`）。
- **捕获层必须先铺不透明底**：页面留白与卡片间隙是透明的，直接模糊会把邻格颜色横着拖开一道。
  `rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }` 先铺一层 `surface`。
- `rememberLayerBackdrop` 的 `onDraw` 经 `rememberUpdatedState` 读取，所以每帧新建的 lambda 不会重建
  backdrop、不会重置坐标；但 `remember` 本身必须**无条件**调用，条件化会破坏 slot table。
- `BarBlurRadius = 40f`，远高于库默认的 20dp：顶栏有状态栏 + 大标题那么高，半径太小时底下文字仍可辨，
  观感只是半透明蒙层而非磨砂。
- 依赖侧：`miuix-blur-android` 的 aar 自带 `minSdkVersion="33"`，而本项目 `minSdk = 29`；靠
  `app/src/main/AndroidManifest.xml` 里的 `<uses-sdk tools:overrideLibrary="top.yukonga.miuix.kmp.blur" />`
  保住 29（已验证 merge 后仍是 `minSdkVersion="29"`）。`miuix-shader-android` 虽由 blur 传递引入，
  但代码直接 import 了它的 `isRuntimeShaderSupported()`，所以也显式声明。

### 提示文案：Snackbar 与 Tooltip

- 反馈改用 Miuix `SnackbarHost`（`Scaffold(snackbarHost = ...)`）。用它是因为它与页面同一个
  surface、跟随主题、且随页面销毁而消失。时长选 `SnackbarDuration.Short`（4000ms）而不是
  `Long`（10000ms）——两个数字取自反编译的 `SnackbarKt.toMillis`，不是猜的：这几条消息都是一句
  话，十秒太长，而提示条本身还可以划走。所有弹出都收敛到一个 `showMessage: (String) -> Unit`，
  并用 `remember(scope, snackbarHostState)` 包住，避免每个接收它的行都被不稳定 lambda 拖着重绘。
  （1.0 的两条反馈文案是常驻行内文字，不是 Toast；git 历史里从未出现过 `android.widget.Toast`。）
- 被复合门控灰显的行（例如需要先开总开关再开子开关）加 `TooltipBox` 长按提示，文案来自
  单条格式串 `R.string.gate_hint`（`需要先打开「%1$s」` / `Turn on "%1$s" first`），
  由 `gateHint(vararg gates: Pair<Boolean, Int>)` 取第一个未满足的开关名。提示通过
  `enabled = hint != null` 关闭——`Tooltip.kt` 里 `tooltipGestures` 在 `enabled = false` 时退化成
  普通 Modifier，所以灰显行只有在确实存在未打开的前置开关时才响应长按。
- **仅被总开关拦下的行刻意不加提示**：总开关就在同一屏上，提示是噪音。
- **一行可以被别的开关门禁，但永远不能被自己门禁。** 推导 `enabled` 时不要用把本行开关值折进去的
  派生谓词，而要回溯到它所依赖的**上游**开关。1.4.1 落地时「充电时显示闪电」写成了
  `enabled = a.glyph && a.drawsBolt()`，而 `TrioAppearance.drawsBolt()` 是 `boltWanted && value`
  ——**含这一行自己的值**，于是关掉它以后该行永久变灰、再也打不开（用户上机报「无法开启」）。
  正确写法是 `enabled = a.glyph && a.value`（挂「显示电量数字」）。上机核验方法：`uiautomator dump`
  取该开关节点，点掉之后它必须是 `checked="false"` 但仍 `clickable="true" enabled="true"`；旧写法
  会是 `clickable="false" enabled="false"`。**不要**用「预置 `show_bolt=false` 再启动 app」验证——
  `SettingsRepository` 启动时会全量同步并把 pref 回写成 `true`，必须走 UI 点击。

### 导航：整页标签切换（2026-10-19）

当前只有「首页」和「关于」两个标签。`rememberNavController<Route>` 保留页面栈与返回逻辑，
页面渲染使用与 HyperChanger `mainTabNavigation` 相同的 `AnimatedContent`。
外层 `Scaffold` 只承载固定底栏；参与动画的 `SettingsPage` 包含各自的顶栏、标题、操作按钮及列表。
首页标题为 `HyperDuo`，关于页标题为「关于」。

进入动画为方向位移 `width / 7`（280ms）、淡入（210ms）、缩放 `.985 → 1`（280ms）；
退出为反方向位移 `width / 3`（230ms）、淡出（180ms）、缩放 `1 → .975`（230ms）。
方向取目标与起始标签的 ordinal，滑动采用 `FastOutSlowInEasing`，页面另有 `12dp ↔ 0dp` 的
280ms 模糊轨道。各页独立维护顶栏滚动行为和正文 backdrop，整页动画的输出再供底栏采样，
避免把消费同一 backdrop 的底栏录入自身。每次进入页面重置列表与顶栏到顶部。

真机回归：`python tests/navigation-device.py --serial SERIAL --root-input --transitions-only`。
覆盖双向切换、连续反向点击、退出内容清理、标题与系统返回；该模式保持当前底栏偏好。

以下为旧版四分区实现记录，其中 `NavDisplay`、`SectionTransition` 和零位移淡入已被上述实现替换。

### 导航：旧版 miuix-nav 实现记录

设置页的四个分区由 `miuix-nav` 的 `NavDisplay` 承载，四个标签就是栈上的四个目的地。

- 路由是 `@Serializable sealed interface Route : NavKey` 下的四个 **data object**。必须可序列化
  是因为 nav 把 back stack 放在 `rememberSaveable` 里、用 serializer 重建；必须是 object 是因为
  nav 用路由自己的 `toString()` 给每个条目的 saved state 做命名空间，object 的 `toString()`
  是类名（跨进程稳定），而普通类的默认实现会打印 identity hash（进程重启后复位）。
- `rememberNavController<Route>(Route.General)` 的**超类型必须显式写出来**：reified 参数否则会
  从实参推断成 `Route.General`，之后 push 其它三个子类型时保存/恢复会序列化失败。
- 切分区走 `selectTab`：落在已经在栈上的路由就 `popUntil` 回退过去，不在栈上才 `push`。
  这样栈是一条路径而不是点击流水账，也顺手满足 nav 文档要求的 push 幂等（重复 key 会被拒绝，
  而标签条允许点得比转场更快）。
- **页面级状态必须传对象而不是值。** `NavDisplay` 内部是
  `val provider = remember(content) { entryProvider(content) }`——DSL lambda 被记忆，provider
  只在 lambda 实例变化时重建。所以 entry 里若捕获 `settings` 的**值**，之后设置变化时它会一直
  渲染那个快照。因此 `SettingsScreen` 保留 `settingsState`/`serviceState` 两个 `MutableState`
  对象并把它俩传给 entry，让每次读取都发生在目的地自己的组合里（真实 snapshot read）。
- **`preview` + 标签条留在每个目的地的 `LazyColumn` 里，没有提到 `NavDisplay` 之上。**
  它是页面的一部分，要跟着内容滚走：顶栏的收起依赖这个滚动，预览卡也正因如此才能在不滚回
  顶部的情况下与设置项对照。提到上层会把它钉在屏幕顶部，两件事同时坏掉。
  四个目的地各带一份（`sectionHeader()`，`SettingsScreen.kt:314-330`），所以它随各自的列表
  滚动。看起来是四份重复，实际不会看出差别：两份是同样的像素、同一个位置，
  转场时互相淡入淡出落在完全相同的内容上。
  位置也验过是等价的——把 header 放进列表只改了它的归属，没改它的坐标：改动前后两次
  `uiautomator dump` 里预览卡与四个标签的 `bounds` 逐字相同。
- 起先放在那条唯一 `LazyColumn` 上的 `.layerBackdrop(barBackdrop)` 与
  `.nestedScroll(scrollBehavior.nestedScrollConnection)` 上移到 `NavDisplay`：模糊要采样整页，
  滚动行为要听到现在发生在目的地内部的滚动。
- **切标签一律回到列表顶部，不恢复上次的滚动位置。** 这里换过两次方向，最终以用户的
  「不要记录页面位置」为准。中间那版曾把「每个目的地各留各的偏移」当成返回语义的正确形态：
  四个分区各有一个 `listState`，走回去就回到原处。问题在于目的地被覆盖时**仍然组合着**
  （见下文交叉淡入的可见窗口），所以「回到原处」不是恢复一个冻结的快照，而是读者刚在标签条上
  选了一个分区、却被丢进它的中段。滚动偏移属于这一次访问，不属于这个分区，因此不跨访问携带。
  实现：`SectionList` 多收一个 `isTop`（`SettingsScreen.kt:572-587`），
  `LaunchedEffect(isTop) { if (isTop) listState.scrollToItem(0) }`；四个 entry 各自传
  `isTop = nav.backStack.lastOrNull() == Route.Xxx`（`SettingsScreen.kt:467/475/483/497`）。**键取 `isTop` 而不是
  `LaunchedEffect(Unit)`**：同一个已组合的列表会被反复覆盖又揭开，重置必须发生在每次「成为栈顶」
  时，而不是当初创建它的那次组合里。
- 对话框（取色、重启、重置确认）**刻意不做成路由**：它们是浮在页面上的提示，推成路由会让
  返回手势的含义从「回上一个分区」变成「取消」。
- **转场不能用库的预设，必须换成一个零位移的交叉淡入。** `NavTransitions.MiuixDefault` 的动作是
  「到达一个新页面」：进入的层从右缘整幅推入（`NavTransitions.kt:43-57` 里 `d <= 0f` 分支的
  `translationX` 最大等于页宽），被它覆盖的层视差左移 1/4 页宽并降到 0.9 alpha，外层再由
  `NavDisplayEffects.dimAmount = 0.5f` 压一层灰。四个标签是同一页的四个视图，横推过去看着像
  把页面撕成两半。`SectionTransition`（`SettingsScreen.kt:195-228`）因此
  只动 alpha：`relativeDepth` 为 0 是停在顶层、-1 是完全退出到上层之上、+1 是被上层盖住，所以
  `alpha = if (d <= 0f) (1f + d) else 1f` 一个式子就同时管往前往后两个方向，被盖住的那层保持
  全不透明直到真的被盖住。
  **挑 alpha 而不是位移动画，也是因为 header 就在被动画的层里**：两份 header 是同一批像素，
  淡入淡出看不出来；一旦改成横推或缩放，屏幕上就会出现两套错开的预览卡与标签条。
- **被覆盖分支也必须显式写 `alpha`。** 这个 block 跑在 `Modifier.graphicsLayer { }` 里，而
  graphicsLayer 是跨帧保留的：某个属性这一帧不再被赋值，它就沿用上一帧的值。只在 `d <= 0f`
  时赋值会让层冻结在它上一次进入动画的 alpha 上，而不是正常显示为不透明。
- 时长取 220 ms 而非预设的 500 ms，`NavDriverSpec.PROGRAMMATIC_DURATION_MILLIS` 是为整页推入调
  的曲线。另外只有**静止起步的整步**才会走 `programmatic` 曲线
  （`runtime\NavDriver.kt` 的 `usesProgrammaticCurve = velocity == 0f && abs(distance) >= 0.999f`），
  所以这个 Tween 实际只作用于点标签；返回手势中途松手带着速度，仍会落到 `commit` 的 spring 上。
- `SectionEffects`（`SettingsScreen.kt:235-238`）关掉 `enableCornerClip`、把 `dimAmount` 设为 0：
  这里从没有层叠在另一层之上，裁角和压暗都没有对象。
- **每个目的地的 `LazyColumn` 必须自己铺底色（`Modifier.background(MiuixTheme.colorScheme.surface)`，
  `SettingsScreen.kt:606`）。** 交叉淡入要求被覆盖的那层在转场期间继续组合、继续绘制——
  visibility window 是 `-1 < d <= opaqueDepth`（`runtime\NavPresentation.kt:104`），
  `opaqueDepth = 1f` 正是为了留住这一层。问题是两个列表尺寸位置完全相同，被覆盖层**只能被上层
  实际画出的像素遮住**：卡片之间有 12dp 间隙、较短的分区结束后还有大片空白，透明列表在这些地方
  什么都盖不住，于是上一个分区的行直接透出来，看起来像旧标签页垫在新标签页底下（用户报的原话是
  「不同标签页会覆盖上一个标签页的内容在底层」）。`Scaffold` 只画一层底色（`containerColor =
  MiuixTheme.colorScheme.surface`，`.tools\tmp-src\miuix-sources\…\basic\Scaffold.kt:88`），
  在只有一个列表的年代够用，现在得由列表自己负责。用同一个 surface 色是刻意的：静止态因此
  与改动前逐像素一致。

## 实现要点

### 注入点

`MiuiBatteryMeterIconView.onDraw(Canvas)` 之后。该视图本身就被测量为 **28dp × 20dp**
（`battery_meter_width` × `status_bar_icon_height`），画布够用，无需改动视图树。注意固件的
**旧式分支不清画布**（`onDraw` L536 在 `super.onDraw` 后直接 `return`），所以我们在
`proceed()` 之后无条件 `drawColor(0, CLEAR)` 再绘制字形，避免原生 drawable 透出来。

### 几何

120×120 设计空间，常量集中在 `TrioGeometry.java`。电池环圆心 `(59.5, 61.487)`、半径 51.5、
描边 8、起始角 148.69°、扫过 242.62°（缺口在**底部**）；顶部缺口留给数字（进度 0.325–0.675）
或闪电（0.345–0.655，比数字缺口**更窄**）。Wi-Fi 半径 31 / 18.5，描边 7，等级 0–3；点阵 4 颗
半径 5.5，等级 0–4。环线粗细 / 弧线粗细 / 数字字号 / 轨道透明度由 `TrioSettings` 覆盖，未改动
时可复现上面这些原始值。

`DOTS` 只有一排；双卡读数的第二排（`DOTS_DUAL`）由它关于**环心** `B_CY = 61.48715261785473f`
镜像得到，而不是关于设计盒中心（120 的一半 = 60）——镜像轴取错会让上排整体偏 1.49 设计单位，
在 28dp 的宿主里约合 0.76px，肉眼几乎看不出，但对齐参考图时对不上。双排点半径
`DUAL_DOT_R = 7.0f` 比单排的 `DOT_R = 5.5f` 大：参考图的点明显更大，且 7.0 是最大环线粗细
（16）下仍留在墨迹盒内的上限。

缺口是**有内容才开**的：`gapStart == gapEnd == 0`（`GAP_NONE`）时 `batteryRing` 的两段会合并成
一整圈。判断顶部缺口是否被占用，以及谁占圆心，都在 `TrioRenderer.drawInto` 的决策块里
（`TrioRenderer.java:118` 起）；圆心与缺口的字号、基线、清空宽度分别由
`TrioGeometry.centreSize/centerBaseline/centreClearWidth` 与 `gapBaseline/gapClearWidth` 给出。

`batteryRing(Canvas, from, to, gapStart, gapEnd)` 的 `from` / `to` 是**可见弧**上的进度，不是整条
路径的进度：缺口本身不出墨，也就不该占用电量。设缺口为 `[m0, m1]`，可见弧长
`drawn = m0 + (1 - m1)`，那么电量 `f` 到达的可见位置是 `u = f * drawn`，左段画
`[0, min(u, m0)]`、右段画 `[m1, m1 + max(0, u - m0)]`。于是 `drawn = 0.65` 时 50% 恰好铺满左半环
（`u = 0.325 = m0`）、右半环为空，50%–100% 全部落在右半环上。

早期实现把 `level/100` 当作整条路径的进度直接用（`from`/`to` 不乘 `drawn`），缺口宽度没有补偿，
症状是电量在缺口整段宽度内**原地不动**：45% 就已填满左半环，而右半环要等电量爬过 67% 才起弧。
端帽（`STROKE` 是 `Paint.Cap.ROUND`，半宽 `stroke/2`）会让每段末端向外多渗一点墨：`stroke = 14`
时约 `7 / 218.078 ≈ 0.032` 路径进度（7.79°），**校验像素时必须先扣掉这个渗出量**，否则正确渲染
也会被判成越界。

### 电量数字居中

设置里这一项叫「电量数字居中」，存储键却是 `swap_wifi_value` —— **键名冻结，不要改**。它是个布尔，
已装用户可能已经打开过；换新键名会让那部分人的选择在升级后静默丢失（框架不做类型/名称迁移）。
只有**标签**改过名：旧文案「Wi-Fi 与数字换位」描述的是机制，而用户要的是「数字居中」。

打开后，`drawInto` 不再做「两个槽位互换」，而是一套**优先级**：电量数字永远第一顺位占圆心，
其余符号依次让位。

| | 关（默认，行为未变） | 开 |
| --- | --- | --- |
| 圆心 | Wi-Fi 弧；无 Wi-Fi 且未选环内类型时是电量数字 | 电量数字（放大 `CENTRE_SIZE_RATIO` 倍）——只要 `show_value` 且电量已知就必定是它 |
| 顶部缺口 | 电量数字 / 闪电 / 双卡两排的上排 | 按顺序取第一个可用者：闪电 → 双卡 → Wi-Fi 弧（缩到 `GAP_WIFI_SCALE`）→ 环内网络类型，都没有则圆环闭合成整圆 |

**缺口是单槽位，按优先级仲裁**（顺序同上）。四条让位规则：

1. **闪电 vs 双卡**：只有 `bolt` 能压过双卡，`charging` 不能——见「双卡信号」一节的解释。
2. **双卡 vs Wi-Fi 弧**：`wifiInGap` 带 `&& !dual` 给双卡让位。这是有意取舍（双卡无处可去，
   弧线有无可从缺失去读）。
3. **闪电恒落缺口**。`boltInCentre` / `boltInGap` 这对变量已删除，`drawBolt(Canvas c, int color)`
   只剩一个落点，固定用 `boltOffsetX/Y` + `BOLT_SCALE`（原居中几何 `BOLT_CENTRE_SCALE`、
   `boltCentreOffsetX/Y` 已从 `TrioGeometry` 移除）。闪电占缺口时**整组 Wi-Fi 弧不画**，
   门是 `cfg.showWifi && (!valueInCentre || wifiInGap)` —— 不能只把 `wifiInGap` 置假，否则
   `drawWifi(..., false)` 会把弧画回圆心压在数字上。**门取 `valueInCentre` 而非 `centred`**：
   双卡也会把数字强制送进圆心，那个组合下按开关判断同样会叠画（见「双卡信号」一节）。
4. **缺口归闪电时用窄嘴**。`gapUsed` 的门与 `GAP_START/END_CHARGE` 的选择只看 `bolt`，
   不再看闪电落在哪个槽位。
5. **环内网络类型让位**。`valueInCentre` 生效时数字占圆心，类型改由新增的
   `drawGapType(Canvas c, String type, int fg, TrioSettings cfg)` 画进缺口（`gapClearWidth` +
   `gapBaseline`，用 `cfg.typeSize` / `cfg.typeWeight`）；圆心空出来时才回到 `drawCentreType`。

Wi-Fi 搬进缺口仍是 canvas 变换（`translate` + `scale`）完成的，几何常量仍是原始那套绝对值，
在 `save()`/`restoreToCount()` 里做。

`valueInCentre == false` 且双卡关时这套式子化简回改动前的判定（`wifiInGap` 与 `typeInGap` 恒为假），
`work\slotcheck\verify.ps1` 用 12 个非居中状态在新旧两版之间逐像素对照，专门锁住这一点。
**注意这条绕过双卡**：双卡开着时它会把数字强制送进圆心（`valueInCentre = … || dual || …`），
于是那个「非居中」的帧其实也走居中分支——这正是 `drawWifi` 的门必须按 `valueInCentre` 而不是
按开关判断的原因。

代码侧的符号（`Prefs.KEY_VALUE_CENTRED`、`TrioSettings.valueCentred`、`TrioRenderer` 里的 `centred`）
都跟着标签改成了「居中」语义，只有那个字符串字面量保持 `"swap_wifi_value"`。

### 三合一样式：圆环与矩形

`trio_style` 两档：`0` 圆环（默认）、`1` 矩形。**选项顺序即存储值**，未识别的值在
`TrioSettings` 里被 `Prefs.clamp` 收敛回圆环，所以升级不会画出一个不存在的排布。

切换发生在 `TrioRenderer.drawInto` 的入口：`drawInto` 只负责 `clearWhenDone` 清屏、算一次
`roleColor`，再按样式选**各自的墨迹盒**做一次 `translate` + `scale`，随后的绘制分成两支：

| | 圆环（`STYLE_RING`） | 矩形（`STYLE_RECT`） |
| --- | --- | --- |
| 墨迹盒 | `INK_X/Y/W/H`（`1.0, 0.5, 117.0, 117.5`） | `RECT_INK_X/Y/W/H`（`1.0, 1.0, 118.0, 114.4`） |
| 落点 | `drawRingLayout` | `drawRectLayout` |

两套墨迹盒是必要的：矩形排布的纵向范围比圆环小、横向范围比圆环大，共用一盒会让其中一种在
宿主 28dp×20dp 的画布里被裁掉或缩得偏小。选盒由包级重载
`inkScale(int w, int h, boolean rect)` 决定，它再转调私有的
`inkScale(int w, int h, float inkW, float inkH)`；`drawInto` 与设置页预览都走这个重载，
**预览因此拿到与状态栏逐位相同的缩放**。公开的 `inkScale(int, int)` 语义不变
——`TrioHooks` 用它做「宿主尚未测量」的守卫（`TrioHooks.java:1752`），改它会把守卫一起改坏。
环外标签的字号**已经不再取自 inkScale**，见「网络类型：环内与环外」。

**用户的整体尺寸乘在这个适配上**：`drawInto` 里那句是
`final float scale = inkScale(w, h, rect) * appearance.glyphFit();`（`TrioRenderer.java:136`），
`glyphFit()` 来自 `TrioAppearance.java`，把 `glyph_scale_pct` clamp 后除以 100。乘**适配**而不是乘
**设计单位**是刻意的：整块字形几何是同一个 120×120 的盒子，缩适配会让每一笔描边、每条弧、每个
数字按同一比例变化，而后面那次 `translate` 仍然居中原点；若改成对设计单位另做一次
`canvas.scale`，线宽（`Paint.setStrokeWidth` 给的是设计单位下的常量）与半径的缩放口径就会分家。

**缩放只在适配这一处应用一次**：槽里的字形是 `drawState` 画的，而 `drawState` 走的正是上面那条
已经乘过 `glyphFit()` 的路径。任何「再按同一个百分比缩一次交给 drawable 的 bounds」的做法，等于
把设置乘两遍。`GlyphDrawable.draw` 因此直接把 `getBounds()` 的宽高交给 `drawState`，不做第二次
换算：bounds 就是 MIUI 给电池图标的那个框，「框不变、画小一点」正是缩放语义。

矩形排布（设计空间仍是 120×120，`TrioGeometry` 的 `rectangular arrangement` 一节）：

- **两列信号点**，`RECT_DOT_LX = 9.6` / `RECT_DOT_RX = 110.4`，行心
  `RECT_DOT_TOP + i * RECT_DOT_STEP`（`15.6 + i * 22.5`，4 行），半径 `RECT_DOT_R = 8.6`。
  两列是**同一个电平的镜像**：点亮数 `min(RECT_DOT_ROWS, level)`，自顶向下点亮，未点亮用
  `withAlpha(fg, cfg.trackAlpha)`。随 `show_mobile` 开关。
- **顶部槽位**三选一，优先级 **闪电 > Wi-Fi > 网络类型**，与圆环排布一致：闪电复用
  `drawBolt` 的路径，只换成 `rectBoltOffsetX/Y`（把缩放后的墨迹中心摆到 `x = 60`）；
  Wi-Fi 复用**同一组** `W1/W2/WIFI_DOT`，用 `RECT_WIFI_SCALE = 0.95` 缩放后由
  `rectWifiOffsetX/Y` 锚定到外弧中心线顶点 `RECT_WIFI_TOP = 8.5`；网络类型用
  `RECT_TYPE_BASELINE = 42.3` 与 `typeSize * RECT_TYPE_SCALE(= 1.26)`。
  弧的缩放是量出来的：参考图外弧的墨迹宽 3.05 倍点径，未缩放的组是 3.20 倍，说明这个
  排布下弧几乎就是全尺寸，取 `0.95` 而不是 `1.0` 是因为参考图自己的弧略扁（墨迹更宽、
  描边更细）——**这个差异刻意不复刻，弧必须沿用现有图标，只做重定位**。
- **电量数字**居中于 `RECT_VALUE_X = 60`，基线 `RECT_VALUE_BASELINE = 92.7`，字号
  `valueSize * RECT_VALUE_SCALE(= 1.38)`，颜色是 `fg`（与圆环排布的 `drawValue` 一致，
  `role` 只喂给电量条）。两种文字都先过 `fitSize(text, requested, RECT_TEXT_CLEAR)`
  （`RECT_TEXT_CLEAR = 83.6`，即两列点之间的净宽）。
- **底部电量条**：`RECT_BAR_LEFT = 1.0` / `RECT_BAR_RIGHT = 119.0`（与两列点的外缘齐平）、
  中心线 `RECT_BAR_CY = 107.3`，**粗细取 `cfg.ringStroke`**——矩形没有圆环，让这个滑块继续有
  归宿；设置页在矩形下把这一行改称「电量条粗细」（`stroke_title_rect`），滑块本身不换。
  轨道是整条胶囊（`drawRoundRect`，`withAlpha(fg, cfg.trackAlpha)`），点亮段用
  `Path.addRoundRect` 配 `RECT_RADII = {r,r, 0,0, 0,0, r,r}`（左端圆角、右端垂直切边），
  右端 `cut = left + (right - left) * clamp01(level / 100f)`。
- **`valueCentred` 对矩形完全无效**：`drawRectLayout` 里没有它的分支。规则收在
  `TrioAppearance`（构造时 `centreValue = c.valueCentred && !rect`），设置页据此把这一行置灰
  并提示需要先打开圆环；这是本次门禁改造**唯一有意的行为变化**。

矩形排布把 Wi-Fi 弧的画弧主体从 `drawWifi(c, level, fg, a, gap)` 抽成了
`drawWifiArcs(c, level, fg, a)`（`TrioRenderer.java:479` / `:496`）：两个排布各自只做一次
`translate` + `scale` 再调用它，**弧的形状与描边完全同源**，这也是「Wi-Fi 图标沿用现有实现」
的落地方式。取 `TrioAppearance` 而不是 `TrioSettings` 是同一件事的一部分：弧的粗细现在问
`a.arcStroke`，不再是 `cfg.arcStroke`。

### 网络类型：环内与环外

`mobile_type_mode` 三档：`0` 关闭、`1` 环内、`2` 环外。**选项顺序即存储值**。

- **环内**（`1`）走渲染器：规则在 `TrioAppearance.Ring` 的决策块里
  （`TrioAppearance.java:222-250`）。`type = a.typeInRing && mobileType != null && !mobileType.isEmpty()`，
  再按圆心是否已被电量数字占用分流：`typeInCentre = type && !wifi && !valueInCentre`，
  `typeInGap = type && valueInCentre && !wifi && !bolt && !dual`。即**数字优先**，类型退到顶部缺口；
  没有数字可展示时才回到圆心。它**不查 `show_value`** —— 与闪电（`drawsBolt()` = `showBolt && showValue`）不同。
- **环外**（`2`）不是 canvas 绘制。宿主画布只有 `battery_meter_width = 28dp` ×
  `status_bar_icon_height = 20dp`，装不下环外的字，所以模块**自己往电池容器里新增一个
  `TextView`**：`TrioHooks.OutTypeLabel`（`TrioHooks.java` 的 `out-of-ring type label` 区块）。
  挂点是 `batteryContainerOf(host)` 返回的 `MiuiStatusBatteryContainer` —— 选它是因为
  `MiuiStatusBatteryContainer.onMeasure/onLayout` 只量/摆自己那几个具名字段、
  **从不遍历 `childAt`**，因此第 4 个子视图不会被量也不会被摆，手工 `layout()` 的位置能保住；
  容器还带 `clipChildren="false"`，越界也画得出来。

**绝不能挂到 `MiuiStatusIconContainer`**：它的 `onLayout` 第一遍把所有 child 的 x 归零，第二遍
又把每个 child 强转 `StatusIconDisplayable`（外来视图直接 `ClassCastException`）。同理也不能用
`WeakHashMap<View, TextView>` 缓存 label —— value 里的 `View.getParent()` 强引用回 key，条目永远
回收不掉；这里改用「子视图 `instanceof OutTypeLabel`」当标记。

挂载/摘除只发生在 posted 路径（`applyConfigChange`、`registerHost`、`invalidateHosts`），因为
`settle()` 是从 `onLayout` 里调的，在那儿 `addView` 会触发
`requestLayout() improperly called during layout`。`settle` 里只调 `refreshOutTypeLabel`，它发现
文本/字号需要变时**只排队一次 posted sync**（`setText`/`setTextSize` 会 re-measure → 调度布局，
同样不能在 `onLayout` 内做），否则只更新颜色与位置。

字号用 `setTextSize(TypedValue.COMPLEX_UNIT_PX, outTypeSizePx(container))`：**必须显式
`COMPLEX_UNIT_PX`**，单参 `setTextSize(float)` 默认按 SP 解释，而这里传的已经是换算好的 px。
**环外字号不再乘 `inkScale`**：它是一个独立键 `out_type_size_dp`（默认 14dp，见
`Prefs.KEY_OUT_TYPE_SIZE_DP`），含义是「宿主 20dp 图标盒子里的大小」，乘上画布缩放反而会
把两种排布的字号绑在一起；dp→px 的换算走 `outTypeSizePx`（posted 更新与 layout-pass 的变更
比较都过它，滑杆改值与重测不可能各执一词，与 `outSignalHeight` 同一模式）。
字重取 `a.typeWeight`（`TrioRenderer.typefaceFor`），与环内共用。
宿主尚未测量（`inkScale <= 0`）时跳过挂载，下一次 posted sync 自愈。文本为空时置 `GONE`
而非移除 —— 网络类型随 modem 来去，每次布局 add/remove 太吵。

`type_size` 只作用环内，`out_type_size_dp` 只作用环外，二者**不再共用**；`type_weight` 是两者
共用的。**但它只管网络类型这一处文字**：第十五轮起环外的性能读数（充电功率 / 电池温度）有自己
的 `perf_weight`，与环内、环外标签都无关（见「环外性能读数」）。设置页的门禁因此是
`a.typeInRing` / `a.typeOutOfRing` / `a.typeAnywhere()`，不再是
裸的 `mobileTypeMode == X` 比较。`show_mobile_type` 是废弃的旧布尔键，`out_type_size` 是
废弃的旧像素键，均仅用于迁移读取（见配置项参考）。

**`out_type_size` → `out_type_size_dp`（第十二轮）**：旧键存的是**裸像素**，同一个滑杆值在
不同密度设备上物理大小不同——这正是第十轮把 `out_signal_size` 从百分比改成 dp 时修掉的同一个
缺陷，环外字号漏掉了。新键默认 14dp（原为 11dp；范围也从 6–22dp 调为 8–26dp，见 §23），
迁移走
`readMobileTypeMode` 的既有模式：新键在则用新键；仅旧键在则 `Math.round(px ÷ density)` 一次
并 clamp 进新范围；都没有则取新默认。旧键从此不再写入。九项离线断言
（`.tmp/migtest/MigTest`，不含库）逐项覆盖了这几条路径。

**结尾 A 的缩小（`type_suffix_scale`）**：参考图 `docs/ref-5ga.png` 量出 A 高 / 主字高 ≈
56/86 ≈ 0.65，底边与主字底边近似齐平（差 2px），所以默认 65。这个比例是**标签本身的属性**，
与它画在环内还是环外无关，因此环内、环外共用一个键，两处各用自己的机制实现：

- **环内（Canvas）**：`Canvas.drawText(CharSequence,...)` **不应用 Span**（只有 `TextPaint` 经
  `StaticLayout` 才会），所以 `TrioRenderer.drawType` 分两段绘制：先量 `"5G"` 与 `"A"` 的宽度，
  按总宽算出左起点（`TEXT` 默认 `Align.CENTER`，居中会让两段叠在同一处，所以绘制期间切成
  `Align.LEFT`、画完还原），两段共用同一条 baseline 以保证底边对齐。`fitSize` 也走同一套
  量测（`measureType`），否则用整串宽度判断「装不下」会把本来放得下的标签缩小。
- **环外（TextView）**：`OutTypeLabel` 是真 `TextView`，直接用
  `SpannableStringBuilder` + `RelativeSizeSpan` 套在末字上，平台自动排版并对齐 baseline。
- **判断后缀**：MIUI 报的是整串（`"5GA"`），所以规则是「末字为 `A` 且长度 > 1 且比例 < 100」
  （`TrioGeometry.hasShrunkSuffix`）。比例 100 即关闭缩小；单独的 `"A"` 不是后缀，缩小它等于整体
  变小，那是字号滑杆的事。

**环外标签的两个边距（`out_type_margin_left_dp` / `_right_dp`）**：标签的两个邻居不同——外侧
对着原生图标行，内侧对着环外读数（没有读数时对着电池），两条缝分别调。语义按**物理左右**定义
（不是阅读顺序的 start/end），RTL 下整行镜像，所以 `placeOutTypeLabel` 里 LTR 用右缝、RTL 用左缝
（`anchor.getLeft() - right - width` 对 `anchor.getRight() + left`）。`reserveOutRingStrip` 是唯一
把两条缝合成为**一个** strip 数字的地方（见其注释），同样按 RTL 取「朝锚点」的那条缝；只有标签
独占时才会把外侧那条缝也算进去。

**环外读数与电池的间距（`out_signal_margin_dp`）**：和标签边距一样是**会占位**的边距，不是位置。
`reserveOutRingStrip` 把它（连同读数宽度）从原生图标行里让出来，`placeOutTypeLabel` 用它把读数
摆在电池左侧。**这里踩过一次坑**：1.6 最初把它做成 `out_signal_offset_x_dp` / `_y_dp` 两个自由
**位移**，加在 clamp 之后——位移不参与 strip 计算，所以调大后读数滑出为自己预留的空间，直接画在
旁边图标上层（用户报告「不占位了，会悬浮在其他图标上方」）。结论：**这条线上的任何调节都必须是
边距**，因为 strip 是唯一能让原生图标跟着让位的机制；自由位移没有对应的让位动作。
**垂直方向不再提供调节**：状态栏那一行没有可预留的纵向空间，任何纵向位移只能是「压到别的东西
上」，所以读数一律在自己那一行里垂直居中。

**读数的验证**：`work/outringcheck` 的 `SuffixShot` 是后缀缩放的量测脚本——它把
`"5GA"` 按若干比例渲染进环心、用列游程量高度比，默认 65 应落在 0.65 附近、比例 100 应与旧版
单字号完全一致。间距本身没有独立工装：它是 `reserveOutRingStrip` 的一个加数，与标签边距走同一
条断言路径。

**环形与胶囊的组件设置相互隔离（1.7）**。在这之前，两种形态共用同一份三槽布局和同一批组件开关
（`show_wifi` / `show_mobile` / `show_value` / `show_bolt` / `show_sim_*` / `mobile_type_mode` /
`signal_mode` / `dual_sim` / `value_centred` / `ring_layout`）。后果是**切换形态会改写对方**：在圆环里
把电量数字放进中部，切到胶囊再摆一个读数，圆环那个位置就被清掉了——因为两者写的是同一个 `ring_layout`。

隔离只做在**「画什么、放哪里」**这一层，存储上靠**键名后缀**区分：

| 形态 | 键 |
| --- | --- |
| 圆环 | `show_wifi`（沿用历史名，现有安装的值原地不动，**无需迁移**） |
| 胶囊 | `show_wifi@capsule` |

`Prefs.styledKey(base, style)` 是唯一映射；`Prefs.STYLED_KEYS` 列出受影响的 12 个键。
**尺寸/粗细/颜色一律不隔离**——40pt 的数字在两种形态里都该是 40pt，复制这些意味着以后每加一条滑杆
都要写两遍。

**读取时按当前形态选键，胶囊缺省时回退到圆环的值**。回退就是全部迁移逻辑：升级上来的安装没有
`xxx@capsule`，于是胶囊先继承它原来看到的样子，直到用户在胶囊模式下改了某样东西才开始分叉。
`TrioSettings.from(prefs, density, styleOverride)` 多了一个可选形态参数，供样式卡片预览
**另一种**形态（`SettingsRepository.readForStyle`）——否则卡片会用当前形态的组件去画另一个形态，
点下去才发现跟预览不一样。写入侧 `SettingsRepository` 全部走 `writeStyled`，键由**当前**形态决定；
`toBundle` / `applyKeyFrom` 也认后缀，否则胶囊的改动到得了存储却到不了 SystemUI 进程。

验收：`-Suite isolation`（键映射 / 升级继承 / 双向隔离 / 远端键与广播键）。其中「双向」是重点：
改胶囊不影响圆环、改圆环也不影响胶囊，两个方向都断言。

**环外顺序可调（`out_ring_order`，1.7）**。三个环外组件（信号读数 / 读数对 / 网络类型）的**摆放
顺序**原本写死为 signal → readouts → type，而且**同一个顺序也是优先级**——`OutRingLayout.solve`
放不下时从数组末尾往前丢，所以「排在最后」同时意味着「最先被丢」。这是两个不同的问题被一份列表
回答了：用户想要的「哪个读数挨着电池」和「窄行里哪个读数该牺牲」并不相关。于是 1.7 把它们拆开：

| 问题 | 真源 | 可调 |
| --- | --- | --- |
| 谁挨着电池 | `out_ring_order`（存 `"3,1,2"` 这样的槽位 id 串） | 是，绘制页「环外组件」的 ←/→ |
| 谁先被丢 | `Prefs.OUT_PRIORITY`（固定：类型最先丢） | 否，这是判断不是偏好 |

**存储顺序是「贴电池优先」，屏幕顺序是它的反向**。`OutRingLayout` 的 `near` 从行尾量起，
下标 0 的坐标最大——也就是 LTR 下**最右**、最贴电池。所以 `out_ring_order` 的第 0 项是
**最右**那张卡片，不是最左。绘制页最初按存储顺序从左往右排，方向正好相反：按的是「往电池移」，
屏幕上却往左跳了一格。现在 `DrawingOutsideParts` 渲染 `order.reversedArray()`，箭头也改用 ←/→
（右＝贴电池），并在行下角标出「← 往外 / 贴着电池 →」。

**只翻转渲染，不翻转存储**：`TrioHooks` 和 `OutRingLayout` 都按「贴电池优先」消费这个数组，把屏幕顺序
写进存储会让每个读取方都要镜像一次。验收里有两条直接量这个映射的断言（`near[0] > near[1] > near[2]`，
以及首/末卡片对应的存储下标）。

**顺序只决定摆放**。`OutRingLayout.solve` 原来把数组下标同时当位置和优先级用，现在多一个
`dropOrder` 参数：位置仍是数组下标（`outRingViews` 按存好的顺序取视图），牺牲顺序单独传
（`outRingDropOrder` 按 `OUT_PRIORITY` 经当前顺序翻译成下标）。旧的四参数重载保留，传 `null`
就退回「从末尾丢」，所以没调用它的地方行为不变。

**每个槽位带着自己的间距走**。`gaps[i]` 现在按 `outGapFor(order[i], ...)` 逐槽计算，而不是
`{signalMargin, inward, inward}` 那样按下标写死：信号读数带 `out_signal_margin_dp`，两个文本件
带标签内侧边距。共用一份间距的话，一交换位置就会把用户调好的边距悄悄换成另一件的。同理
`outward` 属于**实际最外**的那一件（`order[order.length-1]`），而不是下标 2。

**编解码容忍损坏**：`Prefs.decodeOutOrder` 丢掉未知 id、合并重复、按默认顺序补齐缺项，**永远**返回
三个槽位。所以坏掉一个 id 不会把整个顺序重置，下游也不用处理短数组。

验收：`tests/issue-regressions.ps1 -Suite outorder`（编解码 / 传输 / 摆放确实改变坐标 /
优先级独立于顺序 / 部分 dropOrder 仍能收敛）。`tests/os4-layout.ps1` 拼接了 `outRingPlan` 与
新增的五个顺序方法，并注入了 `Prefs`/`TrioConfig`/`TrioAppearance` 的桩，所以它测的仍是**生产**
代码的摆放。

**原生类型文字的压制（1.6.3，取代已关闭的「已知风险」）**：原生类型文字有两个宿主视图——经典的
`mobile_type`（ImageView + `MobileTypeDrawable`，在 `mobile_signal_container` 内）与 HyperOS 的
`mobile_type_single`（TextView，在 `mobile_group` 内），两者都在 slot=`mobile` 的
`ModernStatusBarMobileView` **内部**。折叠整个 mobile 槽本应带走它们，但 MIUI 的 binder
（`MiuiMobileIconBinder`）独立 collect 一个 `mobileTypeSingleVisible` flow，经
`MobileSignalAnimatorContainer.setChildVisible(view, true)` **直接把孩子置 VISIBLE 并播出现动画**；
disappear 路径还会 clone 出副本 `addTransientView` 到容器根，画在 GONE 祖先之外。再叠加
`MiuiStatusIconContainer.onLayout` 第一趟把每个孩子停在容器左端（状态栏即屏幕中线），就是用户截图里
「游离在状态栏中部的 4G」。

两层修复，互补：

1. **源头**：hook `MobileSignalAnimatorContainer.setChildVisible`（hook 组 8）。环外模式下，凡是
   「显示类型」的调用（`visible=true` 且目标视图 id 的资源名是 `mobile_type` / `mobile_type_single`，
   经 `isNativeTypeView` 逐 id 记忆化）把布尔**翻转成 false** 再 proceed——出现动画根本不启动，
   连一帧的闪现都没有；volte / vowifi / roaming 的调用原样放行。环内或关闭时不干预。
2. **兜底**：`settle()` 末尾对 owned 容器 `findViewById` 这两个 id，把 VISIBLE 的强制 GONE
   （`suppressNativeTypeViews`，走与槽位相同的 `markCollapsed` 一次性记账）。它同时承担**交还**：
   模式切离环外（或总开关关闭，`restoreNative` 末尾也调它）时按记账把 GONE 的孩子还原成 VISIBLE，
   MIUI 的 flow 下一次 emit 自然重新接管。

已知风险一节就此关闭：环外模式下原生与自建标签不再同屏。设备离线期间此修复无法上机确认，恢复后
需验证三点：环外时中部无游离 4G/5G；切回环内/关闭后原生类型文字回到信号图标旁；volte/vowifi 等
兄弟图标不受影响。

### 配色

角色优先序在 `TrioRenderer.roleColor(TrioSettings cfg, int level, boolean charging, boolean powerSave, boolean low, int fg)`
（`TrioRenderer.java:169`）：

1. `critical` —— `level >= 0 && level < TrioGeometry.CRITICAL_LEVEL`（`CRITICAL_LEVEL = 20`，
   `TrioGeometry.java:208`），`TrioRenderer.java:175`；
2. `lowPower` —— `powerSave || low || (level >= 0 && level <= cfg.lowThreshold)`，`TrioRenderer.java:178`；
3. `charging` —— `TrioRenderer.java:181-182`；
4. 否则回落到前景 tint。

轨道与未激活点用前景色（默认 22%，可调）透明度；数字与闪电始终用前景 tint。深/浅底由前景色的
sRGB 亮度判断，三组角色色各有深/浅两套。前景 tint 自身仍取自图标视图的
`mUseTint / mTintColor / mLightColor / mDarkColor`。

快充由 `TrioState` 反射读 `mQuickCharging`（与 `mCharging`）得到，**只改闪电颜色**（琥珀色
`TrioGeometry.QUICK_CHARGE`），不改形状或大小 —— 见 `TrioRenderer.drawBolt(Canvas c, int color)`
（`TrioRenderer.java:426`）的注释：*bolt fill; amber while the battery reports quick charge*。

### 预览与状态栏共用几何

`TrioPreviewView`（设置界面顶部一行 39dp 的方块）直接调用
`TrioRenderer.drawInto(..., clearWhenDone=false)`，与状态栏走同一份代码与同一个 `TrioSettings`
快照。`clearWhenDone=false` 是必须的 —— UI 预览若清画布会把 Activity 背景擦成透明黑。

预览卡共 8 格：充电 / 快充 / 正常 / 无 Wi-Fi（带双卡电平与环内类型）/ 省电 / 危险 /
**环外类型** / **环外信号**。后两格是例外：环外标签与环外读数都不由渲染器绘制
（见「网络类型：环内与环外」与「环外信号」），所以走
`TrioPreviewView.setOutTypeOnly(true)` / `setOutSignalOnly(true)` 自己画。标签那格必须与
`TrioHooks.outTypeSizePx` 同口径——字号取 `out_type_size_dp` 乘密度、字重取 `typeWeight`、按 px 而非 sp，
再把宿主 20dp 图标盒按预览自身的高度等比换算（`HOST_ICON_HEIGHT_DP = 20f`，与 `docs` 里
`status_bar_icon_height` 一致），结尾 A 的缩放取 `type_suffix_scale`。（这里的 `typeWeight` 说的是
**网络类型标签**；环外的性能读数有自己的 `perf_weight`，且**没有预览格**——见「环外性能读数」。）
`out_type_size` 在设置页
别处**没有任何可见反馈**，这一格就是它的唯一所见即所得参照；口径一旦和钩子侧不一致，预览就
开始骗人，比没有预览更糟。信号那格同理：按 `out_signal_size_dp × density` 与参照纵横比定出
读数尺寸，再把 `out_signal_margin_dp` 换算成读数到格子右边缘的距离（右边缘即电池在真实那一行里
的位置；窗口宽 = 读数 + 滑块能给出的最大间距），所以拉大间距时读数确实向左离开边缘，整个量程都
留在格内。

**类型边距的可见反馈（第十二轮补）**：两条边距在这格里的画法是「文字块在两条细刻度线**之间**
居中」——刻度线各距格子边缘一条边距的宽度（按 20dp 图标盒等比），前景色 56/255 的透明度，与底纹
同级的「结构感」。状态栏里边距的语义就是「原生图标让出来的空隙」，在这格里邻居就是格子边缘，
所以加宽某一条边距时，刻度线外移、文字块整体被挤向另一侧——所见即所得地展示了「占位」而非
「位移」。两边相等时文字块恰好在格子正中，与旧版渲染逐像素一致。

`PREVIEW_SIZE = 39.dp` 是为了让八格一行放得下（8×39 + 7×4 = 340dp < 344dp）。第 7 格用了
`"5GA"` 作为示例类型——正是为了让 `type_suffix_scale` 有可见反馈。

第十三轮加的两个性能读数（充电功率 / 电池温度）**没有预览格**：它们只在状态栏里出现，而这一行
八格已经贴着宽度预算（340dp 对内容宽 344dp），再塞第九格只能靠继续缩小每一格。两个开关在设置页
各有一行，但它们画出的东西在预览卡上无处可见。

唯一的额外处理：状态栏里这个标签是 wrap-content、排在图标盒旁边，宽了就往右伸不会被裁；
而预览格是个正方形，`"5GA"` 这种宽字串会顶到边界。所以 `TrioPreviewView.drawOutTypeLabel`
在量得文本宽度超出格宽时，只用 `setTextScaleX` 做**水平压缩**，不动字号 —— `outTypeSize`
真正设定的是**高度**，这一维必须保持精确。

### 绘制分页：把三块热区当作命中判定来测（第三十一轮）

「尺寸」页和「常规」页都是**用名字**描述部件；这一页反过来，把三合一大画一遍，让用户直接点在
想改的那一笔上：**点一下**弹出这个分区能放哪些组件、挑一个放上去（第一项是「不显示」，用来清空），
**长按**图标、区域行或菜单里的部件，打开其尺寸与颜色；长按空白区域打开组件列表。它需要一整页是因为热区就是字形的三等分，字形必须大到手指能
**故意**落在某一格里，而「常规」页顶部那条预览每格只有 39dp，是给人**比对**用的，不是输入面。

**这一页的交互模型改过一次，方向很重要。** 第一版是「点一下 = 开关这一处的组件」，上机之后被否掉：
用户要的不是**开关**，而是**替换** —— 点开一个列表，挑一个组件换上去。差别不只是文案。渲染器里
**没有**「把 X 放到缺口」这个概念：每一处画什么是由开关**推导**出来的，槽位内部还有固定优先级
（闪电 > 双卡排 > 弧 > 类型）。所以「把 X 放到第 Z 区」是一个**要求解**的问题，见下面「指派求解器」
一节 —— 那是这次改动真正的技术内容。

#### 三块分区不是「方块的上中下」，是各自布局自己的三个槽位

两种排布的分区内容并不一样，而**最值得写下来的是圆环的默认样子**：电量数字平时住在 12 点
**缺口**里，Wi-Fi 弧平时住在**环心**。

| 分区 | 圆环样式 | 矩形样式 |
| --- | --- | --- |
| 上部 | 12 点缺口：数字、闪电、双卡上排点，或「数字居中」后缩进缺口的弧 | 顶部槽位：闪电 / 弧 / 类型 |
| 中部 | 环内：弧、居中放大的数字，或类型 | 放大的数字 |
| 下部 | 底部四个电平点 | 左右两列点，以及两列之间的电量条 |

所以 `occupant(Zone, ...)` **必须按排布分开作答**，也因此每个分区**能放的组件集合**是**算出来
的**，不是写死的表：矩形的中部只能放数字，圆环的下部只有电平点。

#### 指派求解器：把「换个组件」变成「改哪几个开关」

`TrioZones.assign(zone, rect, want, current, sims)` 回答的是「要把 `want` 放进 `zone`，该把开关
改成什么样」，做不到就返回 `null`。做法是**枚举相关的开关组合，逐个问 `TrioAppearance` 它把东西
放到哪儿**，而不是在代码里写一张「想得到 X 就设这些开关」的表。这个区别就是要害：手写的表是**仲裁
规则的第二个副本**，而本仓最近一个缺陷（矩形中段接缝，见 §6.16.1）正是「两个常量描述同一个边界」
造成的。枚举不会漂移，因为它问的就是渲染器遵守的同一条规则。

几处必须写下来的细节，都是**第一版做错、被离线校验抓出来**的：

- **`sims` 必须由调用方传入，不能从 `current.dualSim` 推**。双卡画几排是**设备事实**，`dualSim`
  是**开关**。用开关推事实是循环的：`dualSim = false` 的帧里根本没有第二张卡，于是双卡永远
  「不可达」。第一版就这么错的，预览里「双卡两排点」被报成不可放置。
- **闪电要在充电帧上判定，其余在空闲帧上判定**。闪电只在充电时存在，拿空闲帧去问「闪电能不能放
  在这儿」等于问一个不可能出现的部件 —— 第一版因此把闪电也报成不可达。
- **清空一个分区要在四种状态上都成立**：有/无 Wi-Fi × 充电/未充电。只检查充电两帧仍会漏掉
  Wi-Fi 断开后进入中部的网络类型。这里的「空」指可选组件为空，圆环和矩形电量条始终保留。
- **同一件事的多个解里取改动最小的那个**，否则把顶部从弧换成数字会**连带**把移动信号表挪走；
  并列时再取**隐藏部件更少**的（挪走比关掉轻），最后按枚举顺序定胜负，保证结果是确定的而不是
  取决于迭代运气。
- **`choices()` 由 `assign()` 反推**，所以「菜单里能点」和「求解器能解」不可能不一致：菜单里点了
  没反应的项、或能解但没列出来的能力，两种都不可能出现。
- **放不下的组件仍然列出来，灰掉并写明原因**（「中部放不下这个组件」），而不是隐藏：少一项会让
  用户怀疑自己是不是点错了列表，灰掉则回答了「为什么不给我」。

`occupant` 必须表示当前帧真正的内容：圆环中部的 Wi-Fi 要同时满足 `wifi`（运行时有读数）和
`drawWifi`（布局允许绘制），开关开启但没有读数时返回空。普通放置仍按组件的适用状态求解；
`DrawingScreen.PlacementFeedback` 用写入后的配置与当前预览比较，说明充电覆盖、待充电或无 Wi-Fi
导致的暂时差异，不静默切换用户选定的预览状态。大图长按经独立 `onEditZone` 回调读取当前帧组件，
已有组件进入设置，空白区域进入选择菜单；点击仍选择替换组件。

离线校验 `tests/drawing-zones.ps1` 对求解器有四条性质检查：给出的答案**真的**把部件放到了那里
（用真仲裁重新推导）；答案是**最小改动**（穷举全部 128×3 组合，不存在更小的解）；每一条**拒绝都是
事实**（同样穷举全空间，任何组合都做不到才算数）；以及**是否可放取决于运行时状态**（闪电要充电帧、
双卡要两张卡）。因为拒绝是穷举验证的，所以求解器为了速度只搜一个子集这件事**不会**让它误报
「做不到」。

#### 一份变换，且只折一次 inset

命中判定和绘制必须落在同一处，否则热区就会偏到它所命名的东西旁边 —— 这一页最怕的就是这个。
做法是把「字形落在哪儿」收敛成**一个定义**：

```
TrioRenderer.drawInto
   └─ TrioRenderer.designTransform(w, h, appearance, out)   ← 唯一的 fit（scale + translate）
        └─ TrioZones.viewTransform(w, h, inset, appearance) ← 只加 inset（pad 折进 translate）
             ├─ TrioPreviewView.zoneTransform(...)          ← 转发，供 View 与设置页共用
             └─ TrioPreviewView.onDraw 也用同一份算术画（canvas.translate(pad, pad)）
TrioZones.zoneAt(transform, rect, viewX, viewY)             ← 把上面那份反着读回来
```

`drawInto` 现在**问** `designTransform` 要变换，而不是自己再算一遍；`TrioZones.zoneAt` 反解的是
同一个 `{scale, translateX, translateY}` 三元组。

**为什么 `viewTransform` 在 `TrioZones` 而不在 View 里**：`View` 需要一个 Android `Context` 才能
存在，于是留在 View 里的算术**离线工装根本够不到**。第一版就是把它写在 `TrioPreviewView` 里，
`tests\drawing-zones.ps1` 因此只能测到渲染器那一半，而**手指真正走的那一半没有测试**。挪进
`TrioZones` 之后，同一个函数既能被 `View` 调用，也能被桌面垫片编译，工装这才测到了真实路径。

工装为此在**源码层面**钉了三条形状：`drawInto` 里不得再出现 `TrioGeometry.INK_W * scale`
（否则重复的 fit 又长回来了）、`viewTransform` 必须存在于 `TrioZones`、`TrioPreviewView.zoneTransform`
必须**转发**给它而不是自己算。最后一条用负对照验过：把 inset 折两次或完全不折，工装分别以
`painted ink at 537,69 resolves to no zone` 与 `... at 858,311 ...` 退出。

#### 分界线是按量出来的墨迹定的，不是拍的

分界线写在 `TrioZones` 里，每条都注明了它是对着哪一块墨迹量的；工装则**单独**渲染每个部件，
墨迹跑出它声称的那一格就失败。最窄的一处窗口是矩形样式上/中的接缝：

| 量的是什么 | 值（设计单位） |
| --- | --- |
| 矩形：最粗弧线（`arcStroke = 16`）的下沿 | y ≈ 48.06 |
| 矩形：最大数字（`valueSize = 44`）的上沿 | y ≈ 49.04 |
| ⇒ 接缝取 | **y = 48.5** |

其余三条：圆环上/中 `y = 38`、圆环中/下 `y = 92`、矩形中/下 `y = 96`。

两处形状上的细节也写在这里而不是留给读者猜：

- **矩形下部分区是三个矩形，不是一个**：左右两列点（它们**纵贯整个字形高度**）加上底部电量条。
  两列点是**同一个部件**（移动信号表），所以在任何高度按下去都必须和按电量条一样。
- **矩形的竖直接缝不是调出来的，是导出出来的**：它就是渲染器给中段文字划定的那个盒子
  （`TrioGeometry.RECT_TEXT_LEFT/RIGHT`，即两列点的内边缘），`TrioZones` 直接引用同一对常量。
  曾经这里有一个手挑的「往里挪 3 个单位」的 `RECT_SEAM_MARGIN`，理由是「挪进点与文字之间的空
  带、代价为零」——**那个理由和那个常量都是错的**，代价是 `100%` 加大字号时点数字右边缘会关掉移动
  信号表。详见 `docs\SWITCHES-REVIEW.md` §6.16.1。

#### 工装断言的是「包含」而不是「独占」，这是刻意的

第一版断言写的是「关掉一个部件，它只该从表里点名给它的那几格里掉墨」。这条在**正确代码上**就
失败了，因为这个字形的部件真的会互相让位：打开数字会把弧从环心挪进缺口；关掉数字则圆环的 12 点
开口**闭合**，弧线直接画过原来数字所在的位置。同一格里「数字没了、弧线来了」，按像素计数分不出来。

修法是**把断言改弱成真的那一句**（关掉一个部件，不得从表里**没有**点名给它的格里掉墨），而**不是**
把检查放宽到能过。两处混淆在代码里明说，不含糊：

- **开口开合**：只有两帧的 `TrioAppearance.Ring.gapUsed` 一致时才做严格比较 —— 读的是**渲染器
  自己遵守的那条规则**，因为这个变化恰恰是「像素不可信」的地方；不一致时只断言这一下确实改变了
  画面。
- **前置条件**：`drawsBolt()` 是 `boltWanted && value`，所以关掉数字**允许**连带带走闪电的墨迹。
  这是全字形里唯一一处「一个开关是另一个开关的墨迹前提」，写成 `gates(Part, Part)`。

矩形样式没有开口，所以它在**所有**配置下都受严格形式约束。

#### 尺寸行收敛成一张表

`geometryTab` 原先手写 16 个滑杆；现在它只从 `PartControls.geometrySliders()` 分组、排序并渲染。
原因是长按弹窗要显示**同一个部件自己的**那几行，两份清单一定会漂，而漂的方式是其中一份留着另一份
已经改掉的量程。改写前后逐项核对过：16 个 setter、48 处字符串引用一个不少，启用条件逐条照抄。

行门禁保留了设置页的既有规则：**一行可以被别的开关门禁，但永远不能被自己的值门禁**（否则关掉它
就再也打不开）。

#### 手势

热区命中由 View 解析（它才知道变换），但手势用的是 Compose 的 `detectTapGestures`，不是
`setOnTouchListener`：后者在 `ACTION_DOWN` 返回 true 会把事件吃掉，于是一个占了大半屏的字形会让
整页**滚不动**；`detectTapGestures` 一旦越过 touch slop 就放开，拖动于是变成滚动、原地按住才是点按。

#### 验证

无设备，所以只有两条：`:app:assembleDebug --offline` `BUILD SUCCESSFUL`；`tests\` 六个脚本全
`exit 0`（其中 `drawing-zones` 打印 `PASS: 566 cases over 24 configurations and 539 renders`）。
**上机验证仍缺** —— 真正的手指落点、三格在真机上的触感、以及长按弹窗的观感都还没有在设备上看过。

2026-10-16 修复复核：将 Wi-Fi 读数独立于开关纳入像素扫描，`drawing-zones.ps1` 通过 **1104** 条
检查、24 种配置、**979** 次渲染；`issue-regressions.ps1` 通过 **85** 条执行断言，包括四帧清空与
清空最少修改的独立穷举，并检查大图长按和状态提示的源码接线。修改前的快照分别触发无 Wi-Fi
虚假占位、无 Wi-Fi 下清空失败的断言。最终 Debug/Release 离线构建通过，包含 Kotlin 编译和 Release R8；
Compose 手势与弹窗的实机操作尚未执行。

### 原生图标抑制

两条通道叠加，两者都**按子开关**决定折叠哪些槽位（`TrioHooks.foldedSlots()`：`show_wifi` → `wifi`；
`show_mobile` → `mobile` + `stacked_mobile`；总开关关闭时为空集）。

1. `MiuiStatusIconContainer` 的 `ignoredSlots` 列表 —— 让容器在 `onMeasure` 时把它们排除出
   `measureViews`。`syncSlots()` 让该列表与子开关**双向**对齐：补上缺失的，并**移除**不再需要的。
   移除才是「交还」的关键：MIUI 会为所有未被忽略的槽位重新布局并定位。
2. 仅靠 (1) 不够：`MiuiStatusIconContainer.onLayout` 第一趟会把**每个**孩子放在容器局部 `x=0`，
   后续趟只重定位「可见且未 blocked 且不在 `ignoredSlots`」的孩子，被忽略的孩子永远停在容器
   左边缘 —— 而状态栏容器因为 `MiuiNotificationStatusContainer.onMeasure` 的半屏测量，左边缘
   正好在**屏幕中线**，于是留下游离的「5G」。所以在 `onLayout` 之后遍历孩子，凡 `getSlot()`
   命中折叠集合的，就 `layout(0,0,0,0)`（每趟重施，`layout()` 不调度新布局故不成环）并
   `setVisibility(GONE)`（只做一次，`COLLAPSED` 去重）。

`ModernStatusBarView.isIconVisible()` 只由 binding 与动画标志决定，与 `getVisibility()` 无关，
所以 GONE 单独用是无效的 —— 必须配合 `ignoredSlots`，反之亦然。

**交还**（子开关关闭时）必须把上面两步都撤销：(1) 从 `ignoredSlots` 移除该槽，
(2) 把 `COLLAPSED` 里登记过的孩子恢复 `VISIBLE`，然后 `requestLayout()` 让 MIUI 重排。
`settle()` 与 `restoreNative()` 都只对**自己登记过**（`COLLAPSED`）的孩子恢复可见性：MIUI 自己
隐藏的图标（无 SIM、关 Wi-Fi、飞行模式）从不登记，绝不能被复活。至于折叠期留下的 `0×0` 布局，
不必手工纠正 —— 槽位一旦不在 `ignoredSlots` 里，MIUI 就会重新测量并布局它。

`system_icons.xml` 被 7 个布局 include（状态栏 / 锁屏 / 控制中心 / 两个 QS 头部 / CC fake），
每个都膨胀出独立实例，所以这条规则挂在 `MiuiStatusIconContainer` **类**上而不是某个实例上，
一次覆盖全部宿主；`insets` 变化时 `MiuiPhoneStatusBarView.updateCutoutLocation` 会用
`setIgnoredSlots(RIGHT_BLOCK_LIST)` 清空列表，故在 `onLayout` 每趟用 `ensureFolded` 补齐。
`ensureFolded` 同样是双向的，并且**只在确有差异时才调用 `syncSlots`**：`addIgnoredSlots` 结尾
无条件 `requestLayout()`，每趟盲目追加会造成无限布局循环。

### 充电闪电的交还

闪电不是状态栏槽位，而是 `MiuiBatteryMeterView` 的孩子，所以它走另一条路。

模块把电池样式钉成 0（见 `hyperduo-style`），而 MIUI 的原生闪电**只在 style 1/2 下被测量**：
`onMeasure` 里那句 `measureChildWithMargins(mBatteryChargingView, ...)` 带 style 条件，所以
style 0 时该视图宽高恒为 0 —— 单纯把它设成 `VISIBLE` 也不会显示。`updateChargeAndText` 同样
按 style 决定闪电可见性，`onLayout` 也按 style 决定是否把它排在电池视图之后。

因此交还的做法是**用反射直接写 `mBatteryStyle = BOLT_STYLE(1)`**，而不是调用
`onBatteryStyleChanged(1)`。原因是后者 style-1 分支会 `mBatteryIconView.setVisibility(8)` ——
而 `mBatteryIconView` 正是字形绘制的宿主，走那条路等于用「交还闪电」换掉字形。直接写字段则同时
点亮 `onMeasure`（测量闪电）、`updateChargeAndText`（按电量状态显示闪电）与 `onLayout`（排到
电池视图右侧，即系统默认位置）三条路径，而完全不碰宿主可见性。全固件只有构造函数与
`onBatteryStyleChanged` 会给 `mBatteryStyle` 赋值，所以这次覆写不会被别处悄悄改回去。

谓词是 `enabled && showBolt && showValue`（`TrioRenderer` 画闪电的条件），即**当且仅当字形真的
画出闪电时才压制原生闪电**。实现分两处，都必须用同一规则，否则会把刚交还的闪电又藏回去：
`hyperduo-style`（style 变更时）与 `hyperduo-charge-text`（`updateChargeAndText` 每次重跑时）。
设置变更时由 `applyMeterText()` 补齐，并按 `showValue`/总开关分别处理闪电与百分比容器。

**总开关关闭时必须先把 `mBatteryStyle` 清成 `-1`**（构造函数自己的初值），再透传真实样式。
否则会卡住 MIUI 自己的还原：`onBatteryStyleChanged` 的主体在 `if (mBatteryStyle != i3)` 门内，
而 `handBackBolt` 之前已把字段写成 1；若真实样式恰好也是 1，这道判断为假，主体被跳过，
`:641/:642`（普通图标 VISIBLE、hollow GONE）不执行 —— 可 `handBackBolt` 留下的正是这个组合，
本该是 style 1 的「hollow 开、普通图标关」，于是 hollow 电池轮廓永远回不来。清成 `-1` 是唯一能
让那道门通过的状态。

百分比容器（`mBatteryPercentContainer`）只在 style 3 下被测量和显示，而模块从不请求 style 3，
所以模块开着时它只能是隐藏的。

### 只压制「自己的」容器

`isOwned()` 从绘制宿主沿视图树向上找到它所属的 `MiuiStatusBatteryContainer`（按类名匹配，
不走 classloader —— 宿主被 Compose/Factory 包装后 classloader 拿不到 SystemUI 类），只有真正
在画字形的容器才隐藏原生图标；电池视图本身被隐藏的容器（island / 极简模式 / 控制中心折叠态）
保留自己的信号图标。

**锁屏那一行是例外，且必须折叠（1.6.9，PR #11 采纳）**：`keyguard_status_bar.xml` 同样 include
`system_icons`，所以锁屏带着**第二个电池 meter**与它自己的一份原生信号图标；而那个 meter 没有
live host（宿主匹配只认状态栏那一行），于是它的容器什么都不折叠、原生图标与三合一同屏
（issue #7；报告者在 1.6.7 主线上复现过，见 issue #3）。`isOwned` 因此对**祖先链上有
`Keyguard` 的容器**放行：这些容器照常折叠自己的图标。只放开折叠这一项——采样仍只认权威状态栏
容器（`isStatusBarContainer`），绘制路径也不变。

### 字形落在哪里：MIUI 电池图标自己的 drawable 槽（1.6.13 起）

三合一不在另一个窗口里，而是写进 MIUI 电池图标**自己**的 drawable 槽
（`MiuiBatteryMeterIconView.mTintBgDrawable`，由 `GlyphDrawable` 安装）。画布、bounds、裁剪与硬件
层全部由平台提供，模块只是往槽里放一个自己画的 `Drawable`；`GlyphDrawable.draw(Canvas)` 把
`getBounds()` 的宽高交给 `TrioRenderer.drawState`，字号与缩放路径与宿主内绘制完全一致。

**为什么不再另开窗口**：`TrioOverlay` 用的是系统级 overlay 类型，因此会被平台的触摸遮挡判定盯上。
`InputMonitor.isTrustedOverlay(int type)` 认的类型里**有 `TYPE_STATUS_BAR_ADDITIONAL`（2041）、没有
`TYPE_STATUS_BAR_SUB_PANEL`（2017）**（AOSP `android-14.0.0_r1`，`InputMonitor.java:740-753`）；而
`WindowState.getTouchOcclusionMode()` 对状态栏类型走 `BLOCK_UNTRUSTED`（它们不是 system-alert 类型，
拿不到 `USE_OPACITY` 这条捷径）。合起来的结果是：窗口虽然不可触摸、只占一个图标的宽、位于状态栏、
且属于 SystemUI，仍会被读作「可能劫持触摸的覆盖层」，落在它下面的触摸被标记为 obscured —— 带防护的
应用（被报告的就是 LSPosed 的权限请求界面，issue #28 / #33）看到这个标记就拒绝响应。当时的补丁是给
窗口打 `PRIVATE_FLAG_TRUSTED_OVERLAY`（`1 << 29`，反射读写 `privateFlags`），但**只要还在建窗口，
就永远要跟这份白名单和这套遮挡判定打交道**；改用槽之后模块不创建任何窗口，这条判定从根上消失。
完整历史（窗口类型怎么选、私有位为什么必须走反射、`tests\overlay-window.ps1` 的三形状验证）留在
`docs\SWITCHES-REVIEW.md` §6.15。

**槽不是永久归模块的，必须每帧重新确认**：MIUI 会从 `clearTint()`、暗色通道、资源重建里改写它，
并在中间整个丢掉；每一次后面都跟一次绘制，而 `GlyphDrawable.install(...)` 是幂等的，所以下一帧会
抢回来。`install` 返回「这一帧是否需要直接绘制」：刚接管槽、缺槽、写入失败或 legacy 路径都返回
true；正常槽已由原生调用时返回 false，避免同帧重复绘制。

**legacy 分支使用持续兜底**：`res\values\bools.xml` 的 `battery_meter_use_legacy_drawable` 为 `true` 时，
`MiuiBatteryMeterIconView.onDraw` 走 legacy 分支（`super.onDraw` 后直接 return），tint 槽根本不会被
画到。因此 hook 在每一帧原生绘制结束后直接绘制字形；缺少可写槽时采用相同路径。可选字段的查找
包括负结果均按宿主类缓存，失败提示按类去重。该兜底已有模拟宿主连续帧覆盖，尚待对应设备验收。

**master 开关关闭时要在原生 onDraw 之前把槽交回**：`GlyphDrawable.uninstall(View)` 成对调用
`clearTint()`、`onDarkChangeInternal()`，让 MIUI 按电池当前状态及颜色重建槽；若该 build 缺少这些入口，退化为
把槽置 null。槽的生命周期比任何一次重绘都长，不交回就会留下一个「模块已关闭、却仍盖着 MIUI 电池」
的 drawable。

2026-10-17 的 OS4 定向修复另见 [代码修复与验证](validation/hyperos4-code-2026-10-17.md)：整条外置读数带
统一排布、缩放和预留，岛宽作为遮挡边界；按电池所在 tint area 取色。以下真机记录属于此前重构版，
不作为这轮修改的设备验收结论。

**真机确认（1.6.13 重构版，小米 14 / houji / HyperOS 4）**：桌面、应用内、锁屏、控制中心四态字形与
读数都正常；桌面连续 5 帧里充电绿弧的像素包围盒**逐帧完全相同**（`1052,44..1111,86`，60×43，
668 px）；`dumpsys window windows | grep hyperduo` **四态计数全 0**，重启 SystemUI 后复拍仍为 0 而
字形照常（666 px）。日志里 `has no mTintBgDrawable` 与 `legacy drawable` 两条失败路径都没触发 ——
本机 `battery_meter_use_legacy_drawable` 确为 `false`。控制中心里也**没有** §6.15 记的那个方块背景
残影（窗口不在了，残影的载体就没有了）。

### 实时刷新

等级来自 `transformResId`，但**指示器消失时那个方法不再被调用** —— MIUI 关掉 Wi-Fi 后不再重绑
该视图，`sWifiLevel` 会永久停在最后一次的值，弧线就一直在屏幕上（这正是「关了 Wi-Fi 弧还在」
的成因）。所以 Wi-Fi 的**有无**改由实时视图树采样：在 `MiuiStatusIconContainer.onLayout` 之后
遍历孩子，对 `getSlot()` 为 `"wifi"` 的读 `ModernStatusBarView.isIconVisible()`（读 binding，
与 `getVisibility()` 无关），结果喂给 `TrioState.setWifiPresent()`，只在**边沿**变化时重绘宿主。

注意不能靠「子视图是否存在」判断：Wi-Fi 关闭时 MIUI **不移除** `slot=wifi` 的孩子，只是停止
绑定它，孩子数量恒为 1。移动信号不做此反馈 —— `isIconVisible()` 在已淡出的移动视图上仍为 true，
无法区分开关，所以点阵仍由最后一次 resId 驱动。

采样只认权威状态栏容器（`isStatusBarContainer`：等于捕获到的 `mStatusBarStatusIcons`，或祖先
类名为 `MiuiPhoneStatusBarView`），否则控制中心/QS 头部的容器会污染全局状态。

**在场判定改为两路取「或」（1.6.5，issue #5 的修复）**：单靠 `isIconVisible()` 会在一部分固件上
失真——小米 17 Ultra / HyperOS 4.0.0.28 beta 上 Wi-Fi 已连接但该方法答 false，弧线被永远关掉
（用户的两张对照图是决定性证据：关弧时原生图标正常回来 = 折叠没问题；开弧时不画 = 判定假阴性）。
修复：`isIconVisible()` 答 true **或** `sWifiLevel >= 0`（`transformResId` 只有在系统真的绑定
Wi-Fi 图标时才会被调到，采到过等级 = 无线至少答过一次）即视为在场。等级不会自行回落，所以它不是
可靠的「离开」证人——但 Wi-Fi 真正关闭时走的是 C_CLEAR 路径（等级本身被清），两路合用恰好互补。
方法缺失时的「退化为 mere presence」兜底保留不变。

#### 环外视图的前景色（深浅色跟随）

环外两个自建视图（`OutTypeLabel`、`OutSignalView`）的前景色都取自 `TrioState.foreground()`，
但**没有任何东西会在深浅色变化时重绘它们** —— 这是「字体颜色跟随状态栏变色的逻辑更新不及时」
的成因。机制（jadx 取证）：MIUI 的深浅色流程是
`MiuiBatteryMeterView.updateLightDarkTint(...)`（`MiuiBatteryMeterView.java:1176-1204`）→
`:1189 miuiBatteryMeterIconView.onDarkChangeInternal()` → 非 legacy 的两条路径都汇到
`MiuiBatteryMeterIconView.java:504 updateProgressBackgroundBitmap(); L505 invalidate();`。
即**深浅色变化必然 invalidate 电池图标视图**（也就必然重跑 `hyperduo-draw`），但它只给自己的
子树重新着色。两个环外视图挂在 `MiuiStatusBatteryContainer` 里、是 glyph host 的**兄弟**，
不在那条链上：`MiuiStatusBatteryContainer` 完全没有 tint 处理，而 `settle()` 里的
`refreshOutTypeLabel` 又被 `onLayout` 门禁卡住（文本/字号/内边距都已相符时不动作）。

修法在 `TrioHooks.recolourOutRing(TrioState state, int ink)`：`hyperduo-draw` 钩子在
`state.refresh()` 之后把本帧的 `state.foreground()` 与 `TrioState.outRingInk` 比较，
**只在不等的那一帧**记账并 post 一次重着色 —— 给标签 `setTextColor(ink)`，给读数
`invalidate()`（它的 `onDraw` 本就每帧现读颜色，缺的只是「被要求画」）。两条细节：

- **账记在 `TrioState` 的实例字段上，不是静态字段**：状态栏与控制中心各有一个电池容器，
  前景色对两者相同，共享一个静态格会让先画的那个把变化吞掉，后画的永远停在旧色。
- **先记账再 post**：颜色抖动时不能每帧排一个 runnable；而且记下的正是本帧画出去的值，
  两边不会对不上。`foreground()` 在 `0` 时替换成默认值，所以初值 `0` 必然触发第一次比较。

`OutSignalView.onDraw` 里原先的注释「the row is invalidated when the tint changes」正是这个
bug 的信念来源，已改正。

`TrioHooks` 需要 Xposed API，桌面上编不了，所以这条规则和该文件里其它几条一样，靠
`work\outringcheck\verify.ps1` 的**源码钉子**守着：只在不等的帧记账、先记账再 post、标签拿到新墨色、
读数被要求重绘，四条成对断言，外加「错误注释不得复活」与「`outRingInk` 必须是实例字段且
`foreground()` 仍把 0 折成 `DEFAULT_FOREGROUND`」两条形状断言。**唯一无法离线替代的是真机那一下**
—— 改深浅色时环外文字是否立刻跟上。

**同拍反色（1.6.4，issue #2 的修复）**：上面的 draw-pass 比较仍是安全网，但它有一个观感缺陷——
MIUI 的深浅色是**逐帧动画**（`updateLightDarkTint` 每帧被值动画调一次，`onLightDarkTintChanged` 是
免动画直调），原生图标跟着动画渐变；环外视图却要等字形**下一帧** onDraw 才比较出墨色变了、然后
一步翻过去——用户看到的就是「别人在过渡，它迟一秒突变」。修法：hook 组 9 拦
`MiuiBatteryMeterView.updateLightDarkTint`，proceed 后**当场**用本帧参数算出墨色并经
`applyOutRingInk`（与 post 路径共用的一行上色体）直接给两个视图上色——无 post、无等待，环外视图
从此与原生图标同速渐变。墨色解析复刻 `TrioState.foreground()` 的三分支（`useTint ? tintColor :
(intensity > 0 ? darkColor : lightColor)`，0 折白），保证两条路径算出的是**同一个数**；钩子体每帧
只有几次字段读和一次颜色比较，与 MIUI 自己每帧的付出同级。

**第三条路径：直接听图标自己的上色调用（1.6.9，PR #11 采纳）**。上面两条都还是在**读**描述栏状态的
字段（meter 的 tint 字段），写进去的时机是栏搭起来的时候、换背景时会滞后。PR #11 改成**听**：
hook `StatusBarIconView.onDarkChanged(ArrayList,float,int)`（AOSP 拼法）与
`StatusBarIconView.setIconColor(int,boolean)`（MIUI 在外面加的那层），取第一个非 0 的颜色参数记进
`sBarInk`（组 16，两个签名都 hook、任一装上即可）。`TrioState.foreground()` 现在**优先** `sBarInk`，
字段退化为兜底——这是「颜色跟着变化一起到」的源头。已知边界：`onDarkChanged`/`setIconColor` 是
**每个图标各自**收到的调用，若某个固件对个别图标传不同颜色（如只给电池的琥珀色），`sBarInk` 会被
最后一次调用覆盖；目前观察到的固件所有图标同一 tint，风险可接受，真机若发现颜色错乱再收紧采样面。

### 双卡信号

「双卡信号」（`dual_sim_signal`）把一排点换成上下两排：**上排 = 卡一（slot 0）、下排 = 卡二
（slot 1）**。渲染端只问「画几排」，判定集中在这里。

**两条规则并存，别把它们合成一条**——两种排布的「第二排」位置不同，所以让位条件也不同：

| 排布 | 谓词 | 第二排是 | 让位给 |
|---|---|---|---|
| 矩形 | `dualSimRows(wifiInk, charging, sims)` | 旁边**另一列** | Wi-Fi 墨迹 **和** 插着充电器 |
| 圆环 | `dualClaimsNotch(bolt, sims)` | 顶部**同一个缺口**的上排 | 只有**闪电** |

**圆环那条是缺口仲裁，不是"条件都满足"**。顶部缺口只有一个位置，能占它的东西按顺序排：
**闪电 > 双卡 > Wi-Fi 弧 > 环内网络类型 > 无（闭合）**。所以：

- `dualClaimsNotch` 只问 `bolt`，**故意不问 charging**。闪电开关化之后这两件事分家了：
  「充电时显示闪电」关掉时模块不画闪电、把闪电交还系统（`hidesNativeBolt()` 为假），缺口就
  重新空闲，两排点可以占据。若按 charging 判定，就会出现「充电中、却什么都没画在缺口里」——
  用户报的正是这个。
- `wifiInGap` 因此多了 `&& !dual`：弧线给双卡让位。**这是有意的取舍**：双卡被顶掉后无处可去
  （数字占环心），而 Wi-Fi 有无可以从弧线的缺失去读，两害相权取双卡。
- `drawWifi` 改判 `!valueInCentre`（而非 `!a.centreValue`）。原因是上面的让位让一个**此前不可达**
  的组合变得可达：**双卡 + Wi-Fi 有墨迹 + 未开「电量数字居中」**。此时 `dual` 把 `valueInCentre`
  强制为真（数字让位到环心），若 `drawWifi` 仍按开关判断就会把弧线也送去环心、和放大数字叠画。
  渲染器里对应那条注释（`TrioRenderer` 的 "a bolt that has taken the notch suppresses them
  outright"）说的是同一件事，只是当时只有闪电能造成这个局面。

`sims < 2` 时两条规则都回退 —— 只有一张卡有读数时，画两排会多出一排空格，不如照旧画当前上网卡。
工作台 `work\dualsimcheck` 同时钉住两条规则，并各有一个反例（`bolt` 变体把缺口规则喂成充电器、
`narrow` 变体把双卡缺口压回百分比缺口）。

几何在 `TrioGeometry`：`DOTS_DUAL[8][2]` 的 `0..3` 是 SIM 1 上排（`DOTS` 关于**环心** `B_CY`
镜像，不是关于设计盒中心），`4..7` 是 SIM 2 下排（`DOTS` 原值）；下排落在 `B_START`/`B_SWEEP`
没盖住的那段自然开口里，所以双排时 `gapUsed` 强制为真，否则电量弧会横穿上排点。点半径用
`DUAL_DOT_R = 7.0f`（单排仍用 `DOT_R = 5.5f`）—— 参考图的点明显更大，且 `7.0` 是在最大环线
粗细下仍能留在墨迹盒内的上限。

**12 点钟缺口在双排时要张到与底部开口等宽**（`GAP_START_DUAL = 1f - 180f / B_SWEEP ≈ 0.2581`、
`GAP_END_DUAL = 180f / B_SWEEP ≈ 0.7419`，`TrioRenderer.drawRingLayout` 里 `r.dual` 优先取这对值）。
上排是 `DOTS` 绕环心镜像，外点几乎正落在弧中线 `B_R = 51.5` 上：外点圆心到弧端点圆心只有约
10.4 个设计单位，而弧是圆帽收尾（`STROKE.setStrokeCap(ROUND)`）、帽半径 `ringStroke/2 = 7`，再加
点半径 7 共 14 ⇒ **帽与点重叠，上排看着糊成一团、且左右不对称**。原先共用的
`GAP_START_IDLE`/`GAP_END_IDLE` 只张 `0.35 * B_SWEEP = 84.9°`，而弧底部自然开口是
`360 - B_SWEEP = 117.4°`，窄了 32.5°。改成等宽后上下互为镜像，余量回到约 10 单位，与下排一致。
放大缺口不影响缺口里的文字：双排时百分比已改去环心（见下），`gapClearWidth()` 仍按 idle 缺口算。
顺带一处叠加缺陷：`valueInCentre` 原先只看 `centreValue`/`(!wifi && !type)`，双排的上排占了缺口而
`valueInGap` 会把数字送进同一位置，两排点与数字叠画；现在 `valueInCentre = hasValue &&
(a.centreValue || dual || (!wifi && !type))`，数字让位到它本来该去的环心。

### 卡一 / 卡二：两张卡各自的独立组件

上一节那张电平表是**合并的**：一个部件，双卡开关决定它画一排还是两排。`PART_SIM_1` 与
`PART_SIM_2` 是它的反面——**每张卡各自一个部件**，各画一行四点，可以放进圆环的上、中、下任意
槽位。需求是「环内信号点要能分别控制卡一卡二」，所以两者并存而不是替换：合并表回答「同一处
画几排」，独立行回答「这张卡自己那一行摆在哪」。

**三个键，互不牵连**：

| 键 | 默认 | 作用 |
| --- | --- | --- |
| `show_sim_1` | `false` | 卡一那一行要不要出墨 |
| `show_sim_2` | `false` | 卡二那一行要不要出墨 |
| `dual_sim_signal` | `false` | 合并表画一排还是两排（不变） |

「开关独立、尺寸共用一条」是按需求定的取舍：两条尺寸行会让同一套点径出现两个真源，所以
`slidersForEditor` 把 `Sim1` / `Sim2` 都映射到 `TrioZones.Part.MOBILE` 那组滑杆，点径与底纹
浓度只有一份。

**信号点沿圆环弯曲，不是直排**。内置的 `DOTS` 本来就是弧形（104.2 / 111.2 / 111.7 / 105.8，
中间最低），所以独立行也画在一段与圆环同心的弧上：弧高由半径导出——弦半宽 `w`、半径 `R` 时
矢高为 `R - √(R²-w²)`——而不是写死的常数，于是行越宽弯得越多。圆心决定朝向：底部槽向下弯
（与 `DOTS` 同向），顶部与中部朝环心弯。实现是 `TrioRenderer.drawArcDotRow`，半径取
`B_R * ARC_ROW_RADIUS_RATIO`（0.82），留出端点不压环线。

**信号点不能放进中部**（2026-10-20）。`PART_MOBILE` / `PART_SIM_1` / `PART_SIM_2` 三行电平点
只允许进上、下两处，中部一律拒绝。理由不是偏好而是几何：圆环在 12 点与 6 点本来就有缺口，
一排点穿在缺口上读起来就是「环被切成几段」；中部没有缺口，电量弧从两侧直接过去，`drawArcDotRow`
的 `zone == 1` 分支只能画成一条**水平**短线，而它的点径取自读数盒 `DOT_BOX_CENTRE / 3`，不是
珠子的尺度。结果是闭合圆环中间一道横杠，任何字号下都不成立，所以这是拒绝而不是又一条滑杆。

规则只有一处真源：`Prefs.zoneAccepts(zone, id)`（挨着部件 ID 与分区编号写，因为解码器也要用它）。
四条路径都过它，一条都不能绕过：

| 路径 | 行为 |
| --- | --- |
| `Prefs.decodeLayout` | 旧布局里中部若存着信号点，**移到下部**（追加在队尾，保留下部原有队首），不丢弃 |
| `TrioZones.withPiece` | 拒绝时把 `current` 原样返回，不写入 |
| `TrioZones.assign` | 返回 `null`（「放不下」），于是绘制页那一行是灰的 |
| `TrioZones.placementChoices` | 直接把该件从该处的候选里滤掉，不留死菜单项 |
| `TrioAppearance` 的槽位解析 | 照样跳过它，让后面的候选顶上（广播来的快照可能没经过解码） |

只对圆环生效：胶囊的两排点画在两个**缺口**上、中部不画点，矩形的中部本来就只有数字，两者都没有
能被放错的「中部」。组合框里卡一/卡二的「中部」选项因此恒为禁用（`ContentControls.kt` 的
`middleBlocked`），绘制页的挑选框则用 `drawing_pick_signal_in_centre` 说明原因——不是「放不下」，
那条会让人去找一个根本存在的设置。验收在 `tests/issue-regressions.ps1 -Suite centre`。

**胶囊的两排点现在是可编辑的摆放**（2026-10-20，修「默认就显示信号点但用户无法编辑」）。
缺陷的形状：胶囊上下的点由 `cap.dots`（= `signalDots()`，只看开关）**无条件**画出，与三格布局
完全无关；而 `Capsule` 的解析里 `BOTTOM` 被硬写成 `PART_NONE`、`TOP` 只可能放下读数。于是上下
两个缺口**看得到点、点开挑选框却只有读数**，没有任何设置能移动或去掉那两排点——`hasInk` 里
`PART_SIM_*` 还带着 `a.ring` 条件，胶囊根本进不去候选。

修法是把两排点变成和圆环一样的**缺口占用者**：

| 环节 | 现在 |
| --- | --- |
| `TrioAppearance.hasInk` | `PART_SIM_*` 的条件从 `a.ring` 改为 `!a.rect`——圆环和胶囊都有缺口可穿，矩形才没有 |
| `Capsule` 解析 | 三个缺口都跑 `firstInk`；某缺口命中信号点则记进 `dotBands`（`dotsIn(band)`），同一缺口不再同时画读数 |
| `drawCapsuleDots` | 按 `dotsIn` **逐缺口**决定画不画那排，所以可以只画上面 |
| `TrioZones.occupant(capsule)` | 缺口有点就报 `Part.MOBILE`，编辑页因此点得到、拿得走 |
| `TrioZones.hasSlots` | 圆环**和**胶囊都是槽位驱动（`STYLE != RECT`），于是 `assign`/`placementChoices`/`applyZoneAssignment` 都走布局写入 |
| `TrioAppearance.slotted` | 新增字段，替代散落的 `a.ring`；卡片的位置行、`signalInsideRing` 都改问它 |

**兼容性是硬要求，且已逐像素验证**：没摆过任何东西的胶囊（`ringZones` 为空）仍走原来的迁移
分支，`dotBands` 上下都取 `signalDots()`——即旧图。我把「空布局 / 充电 / 有 Wi-Fi / 双卡」四种
胶囊画面在 `HEAD` 与本次改动下各渲染一次做 SHA256 对比，**四张全部 identical**。

`dots` 仍是「上下任意一排存在」，因为轮廓在两个缺口都要断开（只断一侧看起来像损坏），而
`dotsIn(band)` 才回答具体某一排。验收 `-Suite capsule`（默认外观 / 两缺口独立 / 编辑页能命名 /
可放置可清空 / 时钟仍不可摆放 / 矩形无缺口）。

**信号点大小可调**（2026-10-20）。`dot_scale` 一条滑杆，**上下两排共用**，默认 100% 即
「点径 = 环的粗细」——这是 `arcRowDotR` 一直以来的推导，所以不碰这条键的安装外观完全不变。
上下不是一个设置而是同一个，因为它们是同一种墨：两排点要一起读成「环被切成几段」，允许上粗下细
等于在一个图标里画两条不同的环。

放大会先撞两个上限里的第一个，两个都得挡：

| 上限 | 来源 | 撞不上会怎样 |
| --- | --- | --- |
| `arcRowBeadRoomR` | 缺口长度减去弧的端帽 | 四颗珠子压到弧的圆头上，珠子不再是描边的延续 |
| `maxBeadR() = INK_W/2 - B_R = 8` | **墨迹盒**：珠子穿在半径 `B_R` 的弧上，外缘到 `B_R + r` | 墨迹出框被宿主裁掉；而且墨迹盒决定缩放，动它会让整个字形随滑杆一起变大变小 |

`maxBeadR()` 恰好等于 `MAX_RING_STROKE/2`，不是巧合：墨迹盒当初就是按「最粗描边时那排点正好装下」
定的尺寸。所以这不是余量而是硬顶，也正因如此放大**不能**靠加大墨迹盒来解决——那会让每个从没碰过
这条滑杆的安装都缩小整个字形。处理方式沿用 `bolt_scale` 的先例：先乘再夹，大数值到顶就不再长。
`TrioGeometry.checkInkBox()` 在类加载时按 `MIN/DEF/MAX_DOT_SCALE` 三档重算一遍，越界直接抛。

滑杆按**样式**门控（只有圆环有缺口可穿珠子），不按开关门控——刚把信号点关掉的人仍要先调好大小
再打开，和其余尺寸行一致。验收在 `tests/issue-regressions.ps1 -Suite dots`。

**文字尺寸按槽位分两套规则**（用户定义，2026-10-17）：

| 槽位 | 上限依据 |
| --- | --- |
| 顶部 / 底部 | **信号点设计盒** `28` / `20`（`TrioGeometry.signalDotBoxH`） |
| 中部 | **圆环内可用空间** `2*(B_R - ringStroke)` |

理由是这两槽本身就是环上开口里那排点的位置，读数做了那里的"客人"就按点的尺度；中部才是
读数的正位，给足环内空间。`drawFreeRing` 的盒子高度也改成读同一组常量
（`DOT_BOX_TOP/CENTRE/BOTTOM`），两处不再各写一份 `28/44/20`。

宽度则一律按**弦宽**：环是圆的，某一高度可用宽度就是那条弦，越靠上下越窄。文字最宽处在墨迹
中部、而那里弦最宽，所以危险的是上下两缘——`zoneTextRoomW(z, stroke, inkH)` 取文字上下缘中
离环心更远的那一行的弦。宽度与高度互相依赖（墨高由字号定、字号的宽度上限又由墨高定），所以
渲染器里做了不动点迭代（4 轮，收敛阈值 0.05），一次猜一个必然过冲。

**两个真 bug 记在这里，别再犯**：
1. `drawFreeRing` 已经把画布平移到槽心，文字基线必须按**相对量**算；早期版本又加了一次绝对
   槽心，导致文字下移整整一个槽心距（顶部读数画到了 y 47..57，而那是 0..40 的槽）。
2. 墨迹居中修正式的方向：`getTextBounds` 与字体度量都相对基线且上方为负，两者相减即可；
   早期版本混用了 `(ascent-descent)` 与 `(ascent+descent)` 两种参照，把每个字符串推下约
   35 个单位。
3. 高度约束当初拿**字体行高** `ascent+descent` 当除数，而落笔只有墨迹——36pt 时行高 45、
   墨迹仅 26.3，等于把每一行字按 58% 判小，底部槽只画到约 11 单位。改用 `textInkHeight`
   后同一设置画到 26.4（满额）。

**只在自由圆环里成立，这不是省事而是必要条件**。独立行是**槽位占用者**，而只有自由圆环有槽位。
`drawsSim1()` / `drawsSim2()` 因此都带 `freeRing && hasRingPart(...)`：

```java
public boolean drawsSim1() {
    return showSim1 && mobile && signalInRing
            && freeRing && hasRingPart(Prefs.PART_SIM_1);
}
```

自动布局若也答「是」，模块就会折叠 `mobile` 槽（`foldsMobile()` 含 `drawsAnySimRow()`）却一行都
画不出来——屏幕上直接少一个信号图标。**这个条件没有放宽的余地**：`work` 里没有对应工装，
但 `tests/RingSlotsTest` 的 ownership 段有一条断言专门盯它（"automatic layouts keep the aggregate
meter"）。

**槽位保持，开关只管出墨**。`TrioAppearance.Ring` 的 `parts` 过滤里，这两项与 Wi-Fi、电量数字
同构：开关关掉只是 `part = PART_NONE`（本帧不画），`ring_slots` 里那个位置照旧保留。所以关掉
卡一不会把卡二顶到它的位置，重新打开也回到原处。

**单卡设备不假装满格**：一行的电平取自 `levels[slot]`，`-1`（没读到）时 `active = 0`，整行落在
底纹色里。这与合并表的单排处理一致，也是为什么这两行**不需要**双卡才成立的条件——卡二那一行
在单卡机器上就是一行不亮的点，而不是一行满格。

**`ring_slots` 的位宽**：九件部件（`PART_NONE` … `PART_SIM_2`）装不进原来每槽 3 bit 的编码
（上限 `PART_CLOCK = 6`），所以新键 `ring_slots_v2` 用 **4 bit/槽**，旧键 `ring_slots` 降级为
只读迁移来源。两种打包方式**不兼容**：`1 | 3 << 3 | 2 << 6` 是一个合法的三比特布局，按四比特
读却是另一堆槽位关系，所以 `Prefs.migrateRingSlots(int)` 手写解码旧值再重新打包，而不是复用
`ringPart`。旧编码产出不了 `7`，所以迁移时遇到 `7` 按损坏处理（回到自动布局），不会把它冒充
成卡一。写入侧只有 `Prefs.packRingParts(...)` 一处拼装——原先 `parts[0] | parts[1] << 3 |
parts[2] << 6` 散落在三个文件里，位宽一改必漏一处（这一轮**确实漏了** `TrioZones.
editableRingSettings` 里的 `* 3`，被 `ring-slots` 的"first edit preserves the preview's visible
occupants"抓出来）。

**求解器**：`TrioZones.candidates()` 把两个开关**在每个槽位都变一变**（不像 `mobiles` 只在底部
变），因为这两行本来就能放三个槽中的任意一个；同时 `distance()` / `hidden()` 都把两个开关计入，
否则"把卡一关掉"会被当成零成本，最小改动的裁决就会偏向它而不是挪走一个还开着的部件。

**电平来源**：`MiuiStatusBarIconViewHelper.transformResId(int, boolean, boolean)` 是静态方法，**签名里没有 subId 也没有 slot**，而且实测渲染走的是 Compose 的 `stacked_mobile` 堆叠图标
（经典 `slot=mobile` 的 `ModernStatusBarMobileView` 是 alpha 0 的死实例），所以那一层拿不到分卡
读数。模块直接问 `android.telephony`：`SubscriptionManager.getActiveSubscriptionInfoList()` 拿
每个 `getSubscriptionId()` / `getSimSlotIndex()`（subId↔slot 的对应关系运行时解析，**不硬编码**），
`getDefaultDataSubscriptionId()` 认当前上网卡，`TelephonyManager.createForSubscriptionId(subId)
.getSignalStrength()` 再取电平得到 0..4。

**取哪个电平：MIUI 的，不是 AOSP 的**（`TrioState.miuiLevel(SignalStrength)`）。MIUI 用
`SignalStrength.getMiuiLevel()` 选 `stat_sys_signal_N`，该方法**不在公开 SDK 里**（连 SDK 37 的
`android.jar` 也只有 `getLevel()`），所以用 `Refl.callByName(strength, "getMiuiLevel")` 反射取，
取不到（`NoSuchMethodError`/非 Number）才退回 `strength.getLevel()`。实机证据：Xiaomi 14 在 5G NR 下
`dumpsys telephony.registry` 两张卡都是 **`miuiLevel = 4` 而 `level = 3`**，而系统状态栏画的是
四格满格。`MobileSignalController.updateTelephony()`（jadx
`work\jadx-out\sources\com\android\systemui\statusbar\connectivity\MobileSignalController.java:495`）
走的正是 `miuiLevel = signalStrength2.getMiuiLevel();` ⇒ 如果双排照 `getLevel()` 取，就会**比系统
单排读数矮一格**，这正是用户报的「单卡满格、双卡上下都少一格」。`work\simcheck\verify.ps1` 现在
多建一份「把 `miuiLevel(strength)` 换回 `strength.getLevel()`」的负例，钉住这条。

`Context` 由绘制路径首次经过时经
`TrioState.attachContext(...)` 缓存（hook 安装时可能根本没有 context）。整个采样在
try/catch Throwable 里，失败时**保留上一轮的值**而不是清空。subId→slot 与各卡电平在
`sampleSims` 末尾一次性发布到 `sSlotLevels`/`sDataSlot`，避免跨轮询拼帧。

采样时机有两处：`refresh()` 里按 `SIMS_INTERVAL_MS = 2000L` 的帧率守卫（`sampleSimsIfDue`，仅在
`dualSim` 打开或单排链从没答过时轮询），以及 `hyperduo-signal` 拦截里的 `pollSimsNow()` —— MIUI
只在读数变化时才重绑图标，所以那次调用本身就是「per-SIM 电平已过期」的事件；它绕过帧率守卫，
但只在**确实变化**时返回 true，避免 MIUI 自己的图标抖动变成重绘循环。

**「单排 = 当前上网卡」**：`refresh()` 读电平之后，若 `sMobileLevel < 0`（图标链从没答过）而
`sSlotLevels[sDataSlot]` 有读数，则用后者兜底。这样开关关掉、单卡、或充电中时那一排仍然是
当前上网卡，而不是最后一张碰巧刷新过的卡。subId↔slot 的映射与这条兜底由
`work\simcheck\verify.ps1` 离线守着（见「测试」一节）——参考机上 `subId` 恰好等于槽位，
所以「把订阅号当槽位」这种写法在那里看起来是对的，那条工装用不相等的 id 把它逼出来。

### 信号：环内与环外堆叠

`signal_mode` 两档：`0` 环内（默认）、`1` 环外。**选项顺序即存储值**，未识别的值由
`TrioSettings` 的 `Prefs.clamp` 收敛回环内。环外模式配套两个布尔键 `stacked_signal`（默认关）
与 `data_sim_only`（默认关）。

行为的唯一判据收在 `TrioAppearance` 的两个方法上，别处一律不问裸键：

- `signalDots()` = `mobile && signalInRing` —— **环内是否画那排点**。它同时是 `foldedSlots()`
  折叠 `mobile` 的条件、`drawRingLayout` 画点阵的条件、以及 `TrioAppearance.Rect.dots` 的来源。
  原先这三处各写一次 `a.mobile`，环外一旦出现就会三处不一致（环里不画、槽却折叠了）。
- `stackedOut()` = `glyph && mobile && signalOutOfRing && stackedSignal` —— **模块是否接管环外读数**，
  也是唯一允许折叠 `mobile` 槽的环外条件。带 `mobile` 是因为「显示移动信号点」的语义是
  「哪儿都不画移动读数」，环内它已经通过 `signalDots()` 这样回答；漏掉它就会出现
  「点阵开关关着、环外堆叠却照画」——用户关掉的那一项被另一个开关复活。
- `foldsMobile()` = `signalDots() || stackedOut()` —— **Hook 侧唯一该问的问题**。它写在这里而不是
  `TrioHooks.foldedSlots()` 里，是为了让规则不依赖 Xposed API 就能被工装驱动（见「测试」）。

由此得到四条组合，**只有第一条与改动前逐字节相同**：

| `signal_mode` | `stacked_signal` | `mobile` 槽 | 移动信号由谁画 |
| --- | --- | --- | --- |
| 环内 | 任意（不起作用） | 折叠 | 模块（环内点阵，含双卡两排） |
| 环外 | 关 | **不折叠** | **系统原生信号格** |
| 环外 | 开 | 折叠 | 模块（`OutSignalView`） |
| 环外 | 开 + `data_sim_only` | 折叠 | 模块，且只画上网卡 |

环外 + 堆叠关**必须不折叠**：这条路径下模块什么都不画，折叠了就是屏幕上少一个信号格。
`work\outringcheck\verify.ps1` 用真值表把 `foldedSlots()` 在这四种组合下的输出逐一对照。
另外「显示移动信号点」（`show_mobile`）关掉时，上表落空：`mobile` 为假使 `signalDots()` 与
`stackedOut()` 同时为假，于是两个位置都既不折叠也不自绘，移动读数整项交还系统——这才是那个
开关承诺的「不画移动读数」，而不是只对环内生效。

**绘制**：`TrioRenderer.drawOutSignal(Canvas, int width, int height, int bars, int dots, int fg,
TrioAppearance a)`。它**不使用 120×120 设计空间，也不乘 `inkScale`**：环外读数不在环里，
用一个描述电池的数字去标定它，等于把状态栏高度绑到电池几何上。所以它直接用参考图自己的单位
（`TrioGeometry` 的 `stacked out-of-ring signal` 一节）：四列、列距 67、柱宽 50、柱高
`{75, 100, 125, 150}` 等差递增、柱底共线、圆头半径 = 柱宽/2；点行直径 50，与柱底留 9；
`STACK_INK_W = 251`、`STACK_INK_H = 209`。整套单位两轴同尺度，所以只算一个 `scale` 就能保住
柱阶、列距与圆头的比例关系。未点亮的柱/点用 `withAlpha(fg, a.trackAlpha)` —— 与环内点阵同一个
底纹浓度键。**读数不足 4 时不画残缺的行**：几列亮就是几格，剩下的列留在底纹色里。

**注意那个 9 是怎么来的**：参考图没有抗锯齿，边缘落在整像素上，所以柱底边缘是 238、点行上缘是
247，空带是 `238..246` = 9。若按「两段墨迹首行下标相减」去量同一张图会得到 10，整套读数就高了
一个单位。`work/outringcheck/compare.py` 存在的意义正是这一条差值 —— 它把参考图与渲染结果**都**
量一遍再比，而不是拿源码里的数字去核对源码。

**两行 vs 一行**：`outSignalLevels(boolean dataSimOnly, int mobileLevel, int[] slotLevels,
int dataSlot, int[] out)` 是唯一的读数解析处。两卡都有读数 ⇒ `out = {卡1, 卡2}`（柱 + 点）；
只有一张卡有读数 ⇒ `{那, -1}`（只有柱，无点行）；都没有时退回 `mobileLevel` 兜底。**只画一张卡
时统一画柱、不画点**，哪怕那张卡恰好是卡二 —— 一张卡不该因为它是第二张就用另一种符号。
`data_sim_only` 打开时只取上网卡：`dataSlot` 命中哪张就取哪张，`dataSlot` 未知（`-1`）或那张卡
没有读数时退回第一张有读数的卡，绝不退化成两张。

**高度与尺寸**：视图高度是 `TrioRenderer.outSignalHeight(sizeDp, density)` —— 用户设置的 dp
（`out_signal_size_dp`，默认 15）乘上显示器密度，**与那一行多高没有任何关系**，再把结果交给
`outSignalWidth(height)` 按参考图纵横比求宽。**两个函数的参照框永远是 `STACK_INK_H`（209）**，
与有没有点行无关。

**旧形式为什么被换掉（bug (g)）**：高度原先是 `outSignalHeight(anchor.getHeight(), outSignalSize)`，
即电池图标盒**活高度**的百分比。MIUI 把这一行在收起时排成 88px、拉开控制中心后变成 134px
（就是系统 `statusBars` inset 的高度，`dumpsys window displays` 实测），于是读数跟着放大
`134/88 ≈ 1.52` 倍 —— 用户报的「下拉到控制中心后这个信号还会莫名其妙的放大」正是它。dp 免疫：
密度不变，下拉多少都画一样大。

**这里修掉过一个尺寸异常**：原先 `outSignalWidth(height, dots)` 的分母随 `dots` 在
`STACK_INK_H`(209) 与 `STACK_BAR_H[3]`(150) 之间切换，而 `drawOutSignal` 里的
`scale = Math.min(width/inkW, height/inkH)` 又用同一个较矮的框 —— 两处互相印证，谁都看不出错，
结果**同一宿主高度下「无点行」的柱被放大 `209/150 ≈ 1.39` 倍**：单卡读数比双卡读数高出一大截，
切到「仅显示上网卡」时视图还会跟着跳一下。现在参照框只有 209 一个：点行只是**内容**，不是**画框**，
`outSignalInkH(dots)` 降级为「居中用的墨高」，不再参与比例。`work/outringcheck` 的 `squat` 反例
把这两行同时改回旧写法，探针必须挂掉 —— 单独改一处是**看不见的**，这正是这个 bug 能活下来的原因。

**改尺寸要不要重测**：`TrioHooks.outSignalHeight(View host)` 是唯一的换算入口，
`updateOutSignal` 的 `measure(...)` 与 `refreshOutSignal` 的变更比较都用它，滑杆改值与重新测量
不可能对目标高度各执一词；比较式因此是 `view.getMeasuredHeight() != outSignalHeight(host)`。
它**只从视图取密度**（`host.getResources().getDisplayMetrics().density`），视图的测量高度**故意
不取** —— 取了就等于把 bug (g) 请回来。

宿主尚未测量（`inkScale <= 0`）时跳过挂载，下一次 posted sync 自愈。

**挂载位置与生命周期**完全照抄环外类型标签那套（见「网络类型：环内与环外」）：挂在
`batteryContainerOf(host)` 返回的 `MiuiStatusBatteryContainer` 上，同样的 posted-only
挂载/摘除、同样的 `instanceof OutSignalView` 当标记（不用 `WeakHashMap`）、`onDetachedFromWindow`
时一并摘掉。`requestOutSignalSync` 的第一行也是 `outSignalWanted()`，与 `requestOutTypeSync`
对称（后者写的是 `if (!TrioConfig.appearance().typeOutOfRing) return;`，同样在**不**该挂载时
提前退出）。

**两个视图共用一条 strip**（第十三轮后是三个）：信号与类型标签都排在电池图标左边，都以
`reserveOutTypeSpace(container, total)` 往容器左侧撑 padding。它们各自更新时如果都按自己的宽度
去撑，后更新的那个就会把先更新的挤掉，所以统一走 `reserveOutRingStrip(container)` —— 它读每个
子视图的 `getMeasuredWidth()` 求和，并配上各自的缝：读数是
`out_signal_margin_dp`，标签是 `out_type_margin_left_dp` + `out_type_margin_right_dp`
**两条都算**（朝锚点的内侧缝 + 背对锚点的外侧缝，后者是它与原生图标之间的空隙，两种情况都在
画）。性能读数（`PerfLabel`，见「状态栏性能读数」）加入的是同一条求和，而不是给自己另外预留——
它只在自己开着时才有一份宽度，所以不会在关掉时留下空洞。

**这条求和里的每一项都必须是「这个视图是什么」的函数，不能是「它此刻写着什么」的函数**：
`getMeasuredWidth()` 看似顺手，但它跟着文本内容变，而性能读数的文本大约每秒换一次
（`0.0W` ↔ `6.1W`）。预留值一跳，`setPadding` 就重排一次，左侧的原生图标整体被推走。读数那一项
因此改走 `perfWidth(...)`：按最宽的可能形态量、逐行量、向上取整（详见「环外性能读数」）。其余几项
的宽度不随内容变，仍可直接读实测宽。

**但「图标右跳」还有第二个、更凶的来源：teardown 路径的抢先释放。** 上机仪器化把每个分量打出来
才发现，`perfWidth` 在 28 帧里恒定 `101`，而图标行的 `paddingEnd` 在 `107 ↔ 0` 之间以功率更新的
节奏（约 1.4 s）乒乓——`removeOutTypeLabel` / `removePerfLabel` 原先的第一步都是
`releaseOutTypeSpace(group)`，**排在判空之前**；视图为 null 时直接 `return`，不再重新 reserve。
这两个方法在每次 sync 都被调用（环外类型默认关闭，「根本没有 label」是常态），紧接着
`reserveOutRingStrip` 又把 total 重新 reserve，于是整排原生图标每轮横移 107px 再弹回。
**⇒ `reserveOutRingStrip` 是 strip 的唯一权威**：它按实际 VISIBLE 的视图求和，`total <= 0` 时自己
会调 `releaseOutTypeSpace`。两个 teardown 方法现在都只做「判空 → 非 null 时 `removeView` →
无条件 `reserveOutRingStrip(group)`」，`tests\perf-layout.ps1` 对这两个方法体各钉一条源码契约
（体内禁止 `releaseOutTypeSpace(`、必须有 `reserveOutRingStrip(`）。

**两条边距都必须计入，这一条修过一次**：原先只在**没有**读数时才把外侧缝算进去，理由是
「有读数时两视图之间的缝已经由内侧那条覆盖了」。但外侧缝画的是标签另一侧的空隙，有读数时
照样存在，于是预留宽度比实际占的窄 —— 把边距调大时图标不动，滑杆看起来失灵。默认值下
（内外各 2dp）两种算法的 total 相同，所以出厂外观没变，只是滑杆终于真的起作用。

**定位**用 `placeOutTypeLabel(container, view, anchor, gap)`，第三参永远是「这个视图与锚点
之间的缝」；标签自己用三参重载（内部取 `outLabelAnchorGap`），读数用四参传
`outSignalMargin`。标签的锚点是 `labelAnchorIn(container, meter)`：有信号就贴在信号外侧，
否则直接贴电池盒 —— 阅读顺序是网络类型 → 信号 → 电池，标签永远在最外。

**摆放参照按「实际靠着谁」分派，这一条也修过一次（§23，用户报告「网络类型和移动信号重叠
了」）**：`placeOutTypeLabel` 的水位参照不能一律取图标行边缘。读数永远靠着电池，取图标行边缘
是对的（1.6.6 的充电岛修复）；但标签有读数时靠着的是**读数**，必须取 `reading.getLeft()`。
早期版本把两种情况都写成图标行边缘、把传进来的锚点丢掉，于是标签和读数的右边缘落在同一个
像素上、字直接画在柱子上 —— 而这个状态在实机上是**每帧**都会出现的，因为图标行永远已测量。
链读数时另外要求 `reading.getWidth() > 0`：刚测量还没 layout 的读数帧是空的，照它链会把标签
甩到容器最左边一帧，此时退回图标行边缘，下一帧再链上。

**采样时机**：环外堆叠也要分卡读数，所以 `TrioState.refresh()` 的轮询门与
`hyperduo-signal` 里的 `pollSimsNow` 条件都从裸的 `dualSim` 放宽成
`dualSim || appearance().stackedOut()`。

### 状态栏性能读数（充电功率 / 电池温度）

第十三轮新增的两个只读显示项，各由一个布尔键控制（`perf_power` / `perf_temp`，默认都关）。
它们**共用同一个视图**：`TrioHooks.PerfLabel`（`TrioHooks.java` 的 `performance readout` 区块），
一个挂在 `batteryContainerOf(host)` 上的 `TextView`，与环外类型标签、环外读数同一处邻居位置。
共用一个视图的理由是两项的可见性可以独立：只开功率就只显示功率，只开温度就只显示温度，两个都
关整块视图才摘掉（`TrioAppearance.perfAnywhere()` = `perfPower || perfTemp` 就是挂载门）。
文本由 `perfText(a)` 按段拼：功率段只在 `TrioState.sChargingAttached` 为真时出现，温度段只要
`TEMP_UNKNOWN` 以外的读数就出现，两段之间用一个换行符（`PERF_BREAK`）相接——**一项一行**。
竖排而不是并排是有意的：两项横着排会把视图撑成两个读数的宽度，挤掉旁边原生图标的预留；
两行只让它变高。只开一项时视图里没有换行符，仍然是一行高。

**两行要压进行高才能放进状态栏**：这里的容器只有 88px，而共享字号是 42px，两行按正常行高
放不下。先由 `perfTextSize(text, container, host)` 把字号缩下来：它取
`TrioRenderer.inkScale(host.getWidth(), host.getHeight(), a.rect)`——**与 `drawInto` 给画布用的
完全同一个适配系数**——乘 `INK_H`（矩形样式用 `RECT_INK_H`）得到图标真实绘制高度，再除以行数
换回字号（`base * (icon / lines) / lineHeight`），最后用 `PERF_MIN_SIZE_RATIO = 0.6f` 和 `base`
两头收口。只开一项时不缩：一行本来就放得下，缩了反而像另一个设置。行高由探针
`PERF_PROBE` 量，**不能在 view 上量**——要决定的正是这个字号本身，从 view 上读会让结果依赖
view 是否已经被赋过值。

**行距是第二道保险**：字号缩完通常两行合起来已经是一个图标高，`perfLineSpacing(view, text, container)`
便返回 `1f`。它真正起作用是在「行被摆得比字形还矮」时（容器高度不足以装下这两行）：数出换行符
算行数，用 `view.getPaint().getFontMetrics()` 取该字体该字号的真实 `descent - ascent`，只压缩
**第一行之下**的那些行——`setLineSpacing` 缩放的是第一行之后的每一行，第一行保留全高，所以要把
空间除以「第一行之外」的行数，直接除以总行数会把这份全高摊到所有行上，结果比容器还高
（实测 100px > 88px）。倍数下限是
`PERF_MIN_LINE_SPACING = 0.72f`，再低两行就要贴上了；数字、度号、C 都没有下伸部，返回这点
行距是安全的。`createPerfLabel` 用 `setMaxLines(2)` 而不是 `setSingleLine(true)`，真正决定高度的
是 `updatePerfLabel` 里那次 `UNSPECIFIED` 测量。

**行距也算进重同步条件**：容器高度变化时只有它的高度变了（那一行收起时 88px、拉开控制中心后
134px），文本与字号都比得相等，所以
`refreshPerfLabel` 里除了
`perf.getTextSize() != perfTextSize(text, container, host)`，还必须有
`perf.getLineSpacingMultiplier() != perfLineSpacing(perf, text, container)`——少了这一项，
上一帧按另一个行高算出的行距会被留下，两行在新行高里就会溢出。同族的 `refreshOutTypeLabel` 是比了
自己的缩放项的，这里照做。`perfLineSpacing` 只读 paint 度量与容器高度/内边距，不写不 post，
因此在 `onLayout` 回调里读它是安全的。

**字号变了必须能触发重同步**：`refreshPerfLabel` 的重同步条件里比对的是
`perf.getTextSize() != perfTextSize(text, container, host)`，不是 `outTypeSizePx(container)`。
该项原本写的是后者，改成自适应字号后必须同步改，否则两项读数一起打开时字号变了也不会重新
同步。

**功率是电池自己的电流 × 电压**（`BatteryPresence.wattsOf`），不是充电器协商出来的最大档位：
协商值在电量爬升时几乎不变，而用户想看的正是「现在还充进去多少瓦」，所以它随电量升高逐渐变小。
满电时电池的电流是**一个小负数**（向外漏），因此算出的瓦数 clamp 在 0，绝不显示负功率。
显示格式由 `powerText(watts)` 决定：不足 10W 保留一位小数（`6.4W`），10W 及以上取整（`18W`）——
小数位在两位数的瓦数上没有信息量。温度取整到整摄氏度，degree 符号在源码里写成 `"\u00B0C"`
以保持该文件全 ASCII。

**没接充电器时不显示功率段，而不是显示 `0W`**：`0W` 挨在温度旁边会被读成「读数坏了」，
而它实际表达的只是「没在充电」，所以干脆整段消失。温度与充电器无关，照常显示——这条不对称正是
两项要分成两个开关的原因。

**「问不出来」是第三种答案**：`getIntProperty` 对不支持的属性返回 `Integer.MIN_VALUE`、
`EXTRA_VOLTAGE` 可能缺失，这两条既不是「没在充电」也不是任何瓦数，所以 `wattsOf` 返回哨兵
`TrioState.POWER_UNKNOWN`（`Float.NaN`），`perfText` 多判一项 `!Float.isNaN(sChargePowerW)`
才拼功率段。早先版本把它们折成 `0f`，会在温度旁边画出一个很自信的 `0.0W`——正是上面那条注释
要避免的假读数。**真正的 0（满电、充电器停止推流）仍然显示 `0.0W`**，「满电时不闪断」的行为不变。
哨兵是 `NaN`，所以判它**只能**用 `Float.isNaN(...)`，**绝不能写 `x != POWER_UNKNOWN`**：`NaN != NaN`
恒为真，不等号会把这枚哨兵本身放行，`powerText` 的 `Math.round(NaN * 10f)` 得 `0`，画出来恰好又是
那个要避免的 `0.0W`。同样的道理，`publish` 的去重比较必须写成 `Float.compare(beforeWatts, …) != 0`，
用 `!=` 会让答不出功率的设备每 3 秒无谓重绘状态栏一次。这两处都有 `tests\battery-presence.ps1` 兜底。

**字号以环外网络类型字号**（`out_type_size_dp`）**为基准**：只开一项时与邻居
环外标签一样大，摆在一起才对得齐；两项都开时在这个基准上按图标高度自适应缩小（`perfTextSize`，
见上），让两行合起来不高于一个图标，所以组合态比单项小一档；**缩放下限
`PERF_MIN_SIZE_RATIO = 0.6f` 触底时两行会略微高过图标一点**——宁可略高也不缩到看不清。
换言之，**两项各自的字号相同，
但它们与组合态的字号不同**——早期文档写的「两个读数永远一样大」描述的是两项单项之间，不是组合态。
调基准键会同时影响两者。

**「读数整体尺寸」是这条规则之上的最后一次缩放**：`perf_scale_pct` 由
`TrioAppearance.perfFit()` clamp 后除以 100，在 `perfTextSize` 的最后一句乘上去——
`return Math.max(base * PERF_MIN_SIZE_RATIO, Math.min(base, size)) * factor;`——并且**三条提前返回
的兜底分支也各乘一次**（`lines < 2 || host == null`、`fit <= 0f`、`lineHeight <= 0f` 都返回
`base * factor`）。之所以要覆盖到每一条答案，是因为只有一项读数打开时走的就是 `lines < 2` 那条
分支：如果它只缩放组合态，那么单项时滑杆没有任何反应，等用户打开第二个开关时才突然跳一下。
它乘的是**自适应结果**而不是替掉它，所以两项读数仍然跟着图标高度与 `out_type_size_dp` 走；
范围 60 – 150 也因此比字形的 60 – 100 宽——它缩放的是一个字号，不是一块被宿主框限死的绘制。

**字重是这两项读数自己的键（第十五轮，`perf_weight`）**：`TrioAppearance.perfTypeWeight()` 把
`perfWeight` 经 `Prefs.clamp(perfWeight, Prefs.MIN_PERF_WEIGHT, Prefs.MAX_PERF_WEIGHT)` 后交给
`perfLineHeight(base, a.perfTypeWeight())`。它**刻意不复用 `type_weight`**：那个键同时驱动网络类型
标签，而这两项是状态栏上的读数，两者没有任何理由被绑在一起——尤其用户想调粗读数时，不应该顺手
把环外的 5G 标签也改粗。

**它必然成为 `refreshPerfLabel` 重同步条件的第五个分句**。字重改变画面的路径有两条：typeface
本身、以及它带来的行高。当字号被钉在 `PERF_MIN_SIZE_RATIO` 下限时（本机默认就落在这个档位），
改字重不会改变字号，于是既有的「文本变了没」与「尺寸变了没」两个分句都看不见它，滑杆会像死了一样。
条件因此是五路：`perf.getText() != text`、`perf.getTextSize() != perfTextSize(...)`、
`perf.getLineSpacingMultiplier() != perfLineSpacing(...)`，加上 `perf.weight != weight`
（`weight = TrioConfig.appearance().perfTypeWeight()`）——`PerfLabel` 里的 `int weight = -1`
记的正是当前这支 typeface 是照哪个字重建的。

**预留宽度必须是「读数是什么」的函数，而不是「此刻写着什么」的函数**：
`reserveOutRingStrip` 原先拿 `perf.getMeasuredWidth()`（**当前文本的实测宽**）求和。而
`BatteryPresence` 大约每秒发布一次新瓦数，`0.0W`（102px）与 `6.1W`（92px）之间来回时，每一次变化都会
经 `setPadding` 重排 MIUI 的图标行，把左边的原生图标整体推一下——设备日志里同一个文件内就出现
预留值在 `102` / `108` 之间交替。现在换成 `perfWidth(perf, perf.getText().toString())`：借视图自己的
`Paint` 量出这段文字**可能**变成的最宽形态——把每一位数字都换成该字体里最宽的那个数字
（把十个数字各量一遍问出来，而不是假定 `'0'` 或 `'8'`），并且**逐行量**（`\n` 对 paint 只是一个字符，
整串量会把两行加在一起、预留出两倍的实际宽度），最后 `(int) Math.ceil(width)`。走 paint 而不是真的
layout 一段探针字符串，是为了**不踏上布局这条路**；代价是没算进字距
（`VALUE_LETTER_SPACING = -0.04em`），所以答案比真正画出来的宽几个像素——**这是安全的方向**：
预留宁可略宽，绝不能短。此后只有**数字位数**还能改变预留，那是阶跃、不是闪烁。这条规则与
`reserveOutRingStrip` 自身一起由 `tests\perf-layout.ps1` 钉住。

**这条是必要条件，不是充分条件**：上机仪器化（`reserveOutRingStrip` 打印每个分量）显示
`perfWidth` 在 28 帧里恒为 `101`，而图标行的 paddingEnd 仍在 `107 ↔ 0` 之间以功率更新的节奏乒乓。
真正的凶手是 `removeOutTypeLabel` / `removePerfLabel` 里**抢先的 `releaseOutTypeSpace`**（排在判空
之前，视图为 null 时直接 return、不再重新 reserve），修法与后果见上文「两个视图共用一条 strip」
一段。**排查这类「谁在动」的问题时，先把分量打出来，不要从症状反推原因**——这一次从像素反推出的
`3–8px` 位移，实际是 `107px` 的整行横移被状态栏边界裁掉之后的残影。

**功率的符号不可移植**：`wattsOf` 取 `Math.abs(currentMicroAmps)` 而不是保留正负。本平台（小米 14 /
HyperOS）在 `USB powered: true`、`status: charging`、`charge_counter` 上升的情况下，`current_now`
连采 8 次**全部为负**（`-115000` … `-204000` µA），与 AOSP 的正号约定相反；按符号判断「在放电」会把
每一个真实充电电流都折成 0，状态栏一直停在 `0.0W`，只有采样抖动恰好落在正值时才闪出一次真数。
幅值才是真正在流动的电流，调用方本来就只在接充电器时绘制，真零仍是真零。

**采样**在 `BatteryPresence.java`（形状照抄 `WifiPresence.java`）：注册一个粘性的
`ACTION_BATTERY_CHANGED` 接收器，再在 SystemUI 里自续一个 3 秒的看门狗（模块跑在
`android.uid.systemui`）。之所以只用广播与 `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)`，
是因为 sysfs 与 health HAL 对 SystemUI uid 都已关闭。读数发布到 `TrioState.sChargingAttached` /
`sChargePowerW` / `sBatteryTempC`，`BatteryPresence.start(sContext)` 由 `TrioState.attachContext`
挨着 `WifiPresence.start(sContext)` 调用。

**采样是无条件启动的，即使两个开关都没开**：`attachContext` 不知道也不需要知道当前配置，而两个
开关随时可能被打开（广播、或设置页里的下一次点击），那时若还没有读数，状态栏会先空一拍再出现。
代价是常驻一个接收器与一条 3 秒的主线程重读（`registerReceiver(null, …)` 是粘性查询，不是新注册），
与相邻的 `WifiPresence` 完全同形。真正决定「画不画」的是渲染侧的 `perfAnywhere()`，开关的关闭
状态不会让采样器少跑，但也不会让它多画一笔。

**挂载与链**：本视图与另两个环外视图同生命周期——posted 路径挂/摘、`instanceof PerfLabel` 当标记、
`restoreNative` 时摘掉、前景色走 `applyOutRingInk`，同步点仍是同样四个 posted 调用处，外加
`settle` 里那次 layout 安全的 `refreshPerfLabel`（`settle` 是从 `onLayout` 里调的，那里不能
`addView`）。它加入的是 `reserveOutRingStrip` 里的**同一条求和**，不给自己单独预留；锚链上是
`labelAnchorIn` 先看它、再退回 `readingAnchorIn`（见「网络类型：环内与环外」）。

## Hook 清单

| id | 目标 | 作用 |
| --- | --- | --- |
| `hyperduo-container` | `MiuiPhoneStatusBarView.onFinishInflate` | 捕获权威的状态栏图标容器 |
| `hyperduo-icon-layout` | `MiuiStatusIconContainer.onLayout` | 按子开关同步 slot + 压制原生信号视图（覆盖全部宿主）+ 采样 Wi-Fi 有无 |
| `hyperduo-draw` | `MiuiBatteryMeterIconView.onDraw` | 清画布 + 绘制字形 |
| `hyperduo-detach` | `MiuiBatteryMeterIconView.onDetachedFromWindow` | 注销宿主 |
| `hyperduo-style` | `MiuiBatteryMeterView.onBatteryStyleChanged` | 强制样式 0，还原 `mStoreRealStyle`；需要时交还原生闪电 |
| `hyperduo-charge-text` | `MiuiBatteryMeterView.updateChargeAndText` | 按 `show_bolt`/`show_value` 隐藏原生充电/百分比视图 |
| `hyperduo-cutout` | `MiuiPhoneStatusBarView.updateCutoutLocation` | 重新追加被 `setIgnoredSlots` 清掉的 slot |
| `hyperduo-signal` | `MiuiStatusBarIconViewHelper.transformResId` | 读取 Wi-Fi / 移动信号等级并触发重绘 |
| `hyperduo-mobile-type` | `MobileTypeDrawable.measure` + `MobileTypeDrawable.draw` | 读 `mMobileType` 并触发重绘；环外时直接吞掉原生类型 ink |
| `hyperduo-meter-tint` | `MiuiBatteryMeterView.updateLightDarkTint` | 深浅色同拍反色（环外视图跟随动画逐帧上色） |
| `hyperduo-mobile-type-visible` | `MobileSignalAnimatorContainer.setChildVisible` | 环外时把「显示原生类型」翻转为隐藏（双向，hide 先 INVISIBLE 防消失副本） |
| `hyperduo-island-hide` | `MiuiBatteryMeterView.updateIslandChanged` + `MiuiStatusBatteryContainer.setIsHideBattery` | 岛期间拒绝电池替换（保留三合一），setter 侧作兜底 |
| `hyperduo-keep-island-battery` | 同上（meter 侧） | 见「充电超级岛」一节 |
| `hyperduo-tint-dark` / `hyperduo-tint-set` | `StatusBarIconView.onDarkChanged` / `.setIconColor` | 听图标自己的上色调用，`sBarInk` 供 `foreground()` 优先取用 |

共 13 组 hook（第十七轮随独立窗口路线删掉了 11–15 那五组：下拉展开高度与最近任务 / 启动动画的
脉冲信号只服务于窗口的显隐，槽路线不再需要——槽里的 drawable 由 MIUI 每帧自己调用）。每个 hook
组独立容错：固件重命名某个方法只会让该组打日志跳过，不影响其余。
设置通道不占 hook —— `TrioConfig` 是注册在 remote `SharedPreferences` 上的变更监听器。

> **新增 hook 组必须同时改两处**：`group()` 的 `switch` 与 `install()` 里的
> `hooked += group(module, cl, N)`。1.6.3 曾经只加了 case、漏了注册，导致该版修复完全没生效
> （见 issue #3/#9 的回帖）。五套工装不编译 `TrioHooks`，抓不到这类遗漏——发布前对着
> `hooked += group` 清点一遍是最省事的保险。

`hyperduo-mobile-type` 必须在 `chain.proceed()` **之后**再读字段：`measure()` 会把 `"5G++"`
就地改写成 `"5G"` 并另置一个 double-plus 标志（`MobileTypeDrawable.java:69`），提前读会拿到
未规范化的原值。网络类型绝不自行推断 —— `5GA` 是 MIUI 按运营商配置
（`OperatorConfig.support5GADisplay`）决定的，模块只如实显示系统给的字符串。

### 充电超级岛：电池被隐藏时的接管

充电时 HyperOS 弹出超级岛，`MiuiBatteryControllerImpl` 置 `mIsAddBatteryIsland` →
`MiuiBatteryMeterView.updateIslandChanged(true)` → `MiuiStatusBatteryContainer.setIsHideBattery(true)`
+ `requestLayout`。容器对 `mBattery` **跳过测量与占位**（图标行的 layout 右边界不再减电池宽度，
原生图标整体右移让位），但 `mBattery` 视图本身仍 VISIBLE、仍在绘制——这是两条坑的源头：

1. **三合一字形悬空**：字形画在 `mBattery` 里，电池没占位但视图还在画，字形叠在岛的下放空间里。
2. **环外视图锚点失效**：环外标签/读数锚在电池 meter 的坐标上，电池被跳过后锚点不再可靠。

hook 组 10 拦 `setIsHideBattery`，把值记进 `TrioState.sIslandHideBattery`（全局 volatile：MIUI
同时隐藏所有容器的电池），值翻转时走一遍与 `applyConfigChange` 同形的反应（refold + 重挂环外两
视图 + 重绘）。岛在场时三条规则同时生效：

- **字形停画**（`hyperduo-draw` 的门加上 `!islandHideBattery()`）——环内没有可安放的环。
- **Wi-Fi 槽交还**（`foldedSlots()` 里 `a.wifi && !islandHideBattery()` 才折 wifi）——原生
  Wi-Fi 图标显示，用户此前从模块里关掉它只是因为弧画在环上，环没了就该回来。
- **移动信号按环外读数显示**（`foldedSlots()` 无条件折 mobile 槽；`syncOutSignal`/
  `requestOutSignalSync` 的门放宽为 `stackedOut() || islandHideBattery()`）——环内读不了信号，
  环外读数顶上，与用户是否开过「环外 + 堆叠」无关。

环外标签（用户开了环外类型时）继续显示：锚链 `labelAnchorIn` 优先取环外读数——岛下读数必在。
第十三轮后这条链还多一环：`labelAnchorIn` 先看有没有可见的性能读数（`PerfLabel`），有就贴它，
否则退回 `readingAnchorIn`（可见的环外读数，再退回电池盒）。
岛收回（`setIsHideBattery(false)`）时同一套反应把三件事还原：字形恢复、Wi-Fi 槽重新折叠（若用户
开着弧）、环外读数按用户原设置决定去留。

**接管条件的精确边界（1.6.7）**：岛只改变信号**画在哪里**，不改变用户**是否要它**。所以
`foldedSlots()` 的 mobile 折叠条件**保持 `a.foldsMobile()` 不变**（原来是
`a.foldsMobile() || islandHideBattery()`，那会在用户关掉移动信号时凭空折掉原生图标），改由
`outSignalWanted()` 表达真正的规则：`stackedOut() || (islandHideBattery() && foldsMobile())`
——用户要信号且岛夺走了字形，读数才顶上；用户本就不要信号的机器上，原生图标留在原地。

**横向参考系改到图标行边缘（1.6.7，修 1.6.6 的坐标错误）**：`placeOutTypeLabel` 原先锚
`meter.getLeft()`。正常态它等于图标行的右界（容器把行右界算作「容器右 − paddingEnd − 电池宽」，
电池正被布局在那里），所以一直是对的；**岛上这个等价被破坏**——`MiuiStatusBatteryContainer`
的 `onMeasure` 不再把电池宽度累加进 measuredWidth、`onLayout` 里行右界 `i7` 也不再减电池宽
（行因此右移让位），但电池视图本身**仍按原位布局、只被 `updateVisibility$6()` 设为
`INVISIBLE(4)`**（不可见但仍占位，不是 GONE）。于是锚在 meter 上的读数落在比行末**左移一个电池
宽**处，正好压在被右移过来的原生图标（网速、信号）上——用户截图里的重叠。
修法：横向基准取 `iconContainerIn(container)` 的 `getRight()`（RTL 取 `getLeft()`），即图标行
真正的末端，也正是 `reserveOutRingStrip` 预留 padding 收尾的地方，读数因此落在自己预留的条带
里；`icons` 取不到或未测量时退回原来的 anchor 边。垂直基准仍取 anchor（它的 frame 无条件布局，
两态一致）。两态下该边缘与 meter 左缘相等或正好差一个电池宽，所以正常外观逐像素不变。

## 配置项参考

键名、默认值与上下界的唯一真源是 `Prefs.java`（`app/src/main/java/io/github/yixing233/hyperduo/Prefs.java`）。

| 键 | 默认 | 范围 |
| --- | --- | --- |
| `enabled` | `true` | — |
| `show_wifi` / `show_mobile` / `show_value` / `show_bolt` | `true` | — |
| `dual_sim_signal` | `false` | —（无 Wi-Fi 且未充电时改画上下两排点，上排卡一、下排卡二；见「双卡信号」） |
| `show_sim_1` | `false` | —（卡一自己的那一行四点，作为独立组件放进圆环**上/下两处**——中部不放信号点，见 `Prefs.zoneAccepts`；只在自由圆环生效，见「卡一 / 卡二」） |
| `show_sim_2` | `false` | —（卡二自己的那一行四点，同上；与卡一互不影响） |
| `ring_slots_v2` | `-1` | `-1` 或 `0`–`4095`（三槽 × 4 bit 的部件 ID；**取代**每槽 3 bit 的旧键 `ring_slots`，见「卡一 / 卡二」） |
| `signal_mode` | `0`（环内） | 0 – 1（环内 / 环外）；**选项顺序即存储值**；见「信号：环内与环外堆叠」 |
| `stacked_signal` | `false` | —（环外时自绘柱+点；关闭则把 mobile 槽交还系统） |
| `data_sim_only` | `false` | —（环外堆叠时只画上网卡；只在 `stacked_signal` 打开时有意义） |
| `mobile_type_mode` | `0`（关闭） | 0 – 2（关闭 / 环内 / 环外） |
| `show_mobile_type` | `false` | 已废弃，只读用于迁移 |
| `swap_wifi_value` | `false` | 「电量数字居中」的存储键；名字是历史遗留，**冻结不改**（只对圆环样式有效） |
| `trio_style` | `0`（圆环） | 0 – 1（圆环 / 矩形）；**选项顺序即存储值** |
| `ring_slots` | `-1` | 已废弃，只读用于迁移（每槽 3 bit 的旧编码，经 `Prefs.migrateRingSlots` 重打包） |
| `role_colors` | `true` | — |
| `color_critical_on_dark` / `color_critical_on_light` | `0xFFFF3B30` | — |
| `color_charging_on_dark` / `color_charging_on_light` | `0xFF34C759` / `0xFF1F8F3D` | — |
| `color_low_on_dark` / `color_low_on_light` | `0xFFF2B900` / `0xFFC99700` | — |
| `low_threshold` | `20` | 5 – 50 |
| `ring_stroke` | `14` | 4 – 16 |
| `arc_stroke` | `12` | 3 – 16 |
| `dot_scale` | `100` | 50 – 160（环内上/下两排信号点的点径，相对「直径=环粗细」的百分比；**一个键管上下两处**，只对圆环生效。放大会在墨迹盒与缺口两个上限处停住，见「信号点大小」） |
| `value_size` | `36` | 16 – 44 |
| `value_weight` | `700` | 100 – 900 |
| `type_size` | `32` | 16 – 44（只用于环内） |
| `out_type_size_dp` | `14` | 8 – 26（只用于环外；dp，消费端乘密度成像素。取代裸像素键 `out_type_size`（默认 32、范围 16–64），旧值在读取时按 `px ÷ density` 迁移一次，旧键从此不再写入。默认 14dp，可调 8–26dp） |
| `type_suffix_scale` | `65` | 50 – 100（结尾为 A 的类型如 5GA，末尾 A 相对主字号的百分比；环内分段绘制、环外用 `RelativeSizeSpan`，两处共用同一个键） |
| `out_type_margin_left_dp` | `2` | 0 – 16（环外标签与其**外侧**的空隙，dp；RTL 下随整行镜像） |
| `out_type_margin_right_dp` | `2` | 0 – 16（环外标签与其**内侧**的空隙，dp；RTL 下同样镜像） |
| `out_signal_size_dp` | `15` | 6 – 20（环外信号读数的 dp 高度；乘显示器密度成像素，**不跟随电池容器高度**，只用于环外 + 堆叠信号） |
| `out_signal_margin_dp` | `2` | 0 – 16（环外读数与电池之间的**会占位**边距，dp；调大时原生图标同步让位。只用于环外 + 堆叠信号） |
| `out_ring_order` | `"1,2,3"` | 三个环外组件的摆放顺序，**贴电池的一侧在前**（即 LTR 下最右）；id 见 `Prefs.OUT_SLOT_*`（1=堆叠信号/读数对、2=功率温度、3=网络类型）。**只决定摆放，不决定溢出时谁先被丢**（那是固定的 `Prefs.OUT_PRIORITY`）；解码时永远补齐成三项。绘制页渲染时反向，见「环外顺序可调」 |
| `type_weight` | `700` | 100 – 900（环内/环外共用） |
| `track_alpha` | `56` | 0 – 255 |
| `debug_log` | `false` | — |
| `perf_power` | `false` | —（在电池图标旁追加充电功率读数，与 `perf_temp` 共用同一个环外 `TextView`；功率是电池自身电流 × 电压，没接充电器时不显示这一段） |
| `perf_temp` | `false` | —（在同一处追加电池温度读数，单位摄氏度、取整；不接充电器同样显示） |
| `glyph_scale_pct` | `100` | 60 – 100（三合一字形的整体缩放百分比。乘在 `TrioRenderer.drawInto` 那个适配系数上——`inkScale(...) * appearance.glyphFit()`——所以整块 120×120 几何里每一笔描边、弧线与数字按同一比例缩放，`translate` 的居中不变。上界只有 100 是因为宿主给电池图标的框只有 28dp × 20dp，超过 100 只会画到视野外、裁不裁由 MIUI 说了算；下界 60 避免缩到看不见。缩放**只在适配系数这一处应用一次**——`GlyphDrawable.draw` 把 `getBounds()` 的宽高原样交给 `drawState`，不做第二次换算） |
| `perf_scale_pct` | `100` | 60 – 150（两项性能读数的整体缩放百分比，**最后**一步乘在 `TrioHooks.perfTextSize` 的答案上——`Math.max(base * PERF_MIN_SIZE_RATIO, Math.min(base, size)) * factor`——并且同样乘上三条兜底分支（`base * factor`），所以单项读数、测不出宿主、测不出行高这三种答案都会跟着动。它是「自适应结果之上的倍数」而不是第二个绝对字号，因此两项读数仍然跟着图标与 `out_type_size_dp` 走。范围比字形那条宽、且允许超过 100，是因为它乘的是字号而不是一个被框限死的绘制） |
| `perf_weight` | `700` | 100 – 900（两项性能读数文字的笔画粗细，`TrioAppearance.perfTypeWeight()` clamp 后交给 `perfLineHeight`。**独立于 `type_weight`**：后者驱动的是网络类型标签字重，与状态栏上这两项读数无关。改粗会同时改变行高，所以 `refreshPerfLabel` 的重同步条件里有它的第五个分句；预留宽度另由 `perfWidth` 按最宽形态量出，见「环外性能读数」） |

## 已知限制

- 仅在 HyperOS 4 / 小米 14（`CP2A.260605.016`，SDK 37，`OS4.0.0.27.XNCCNXM`）上验证过；其他
  固件可能类名或字段名不同，届时会退化为对应 hook 组打日志跳过。
- 折叠集合按类名/slot 字符串硬编码（`wifi` / `mobile` / `stacked_mobile`）。若后续固件引入新的
  移动槽位名，需要在 `TrioHooks.MANAGED_SLOTS` 里补一项，并在 `foldedSlots()` 里归属到对应的
  子开关；`DEBUG_DUMP = true` 可以打印出实际 slot 名。
- 子开关的「交还」依赖 `MiuiStatusIconContainer.ignoredSlots` 可读可写（反射）：读不到该字段时
  `syncSlots` 退化为只追加，于是子开关只能压制、不能交还（等同旧行为）。
- 原生充电闪电的交还依赖 `mBatteryStyle` 被直接改写：这在 MIUI 自身不重新派发 style 变更时成立。
  若用户在系统设置里改了电池样式，`hyperduo-style` 会重跑并重新按子开关决定是否交还。
- Wi-Fi 与移动信号等级来自 `MiuiStatusBarIconViewHelper.transformResId` 的原始 resId
  （`getResourceEntryName` 解析尾位数字）；若固件改走别的绑定路径，环内会退化为不显示对应
  弧/点（不会崩溃）。Wi-Fi 的**有无**另有实时采样兜底，不受此限制。
- 移动信号开关无法实时反映：`isIconVisible()` 在淡出的移动视图上仍为 true，故移动点只在
  resId 变化时更新。
- 「双卡信号」的分卡电平来自 `android.telephony`（`SubscriptionManager` / `TelephonyManager`），
  而**图标那条链给不出 slot**：`transformResId(int, boolean, boolean)` 是静态方法，签名里既没有
  subId 也没有 slot；Compose 的 `stacked_mobile` 堆叠图标与 Dagger 里的
  `MiuiMobileIconInteractorImpl`（它有 `subId` 与 `phoneId`）模块都够不着。因此该开关依赖
  SystemUI 进程本身持有 `READ_PHONE_STATE` / `READ_PRIVILEGED_PHONE_STATE`（实测该进程
  `sharedUserId="android.uid.systemui"`，模块不额外声明权限）。取数失败时只在日志里留痕，
  字形退回单排，不会崩溃。
- 修改环线粗细 / 数字字号等几何参数会立即生效，但**不会重新测量**原生电池视图的
  28dp×20dp 尺寸；极端值下字形可能被裁切。

## 目录

```
LICENSE                                 GNU GPL v3.0 全文（本仓库协议，源码的 SPDX 头指向它）
app\build.gradle.kts                   Gradle 模块配置（namespace io.github.yixing233.hyperduo）
app\src\main\java\io\github\yixing233\hyperduo\   hook 源码（Java）
app\src\main\java\io\github\yixing233\hyperduo\ui\ 设置界面（Kotlin + Compose + Miuix）
app\src\main\res\                      strings / themes / 图标
app\src\main\res\xml\file_paths.xml    FileProvider 路径（更新器下载目录）
app\src\main\resources\META-INF\xposed\  java_init.list / scope.list / module.prop
scripts\env.ps1                        工具链环境变量（source 后得到 JAVA_HOME / ANDROID_HOME / GRADLE_HOME）
scripts\release.ps1                    构建 release APK 并上传到 GitHub Release（末尾自动调 release-module.ps1）
scripts\release-module.ps1             把同一 APK 发到 LSPosed 模块仓
scripts\install.ps1                    安装到设备 + 打开设置 + 跟进日志
scripts\legacy\build.ps1               旧的手写离线构建（已弃用，保留查阅）
docs\                                  开发文档与 README 配图
docs\release-notes\                    历次发版的 changelog（Release 正文的唯一存档）
build.gradle.kts / settings.gradle.kts / gradle.properties   构建配置
work\jadx-out\                         MiuiSystemUI 反编译源（分析用，不入库）
work\unpacked\                         MiuiSystemUI 解包资源（分析用，不入库）
work\geocheck\                         离线段渲染与解析校验（不入库）
work\gapcheck\                         电量环缺口像素校验与前后对照图（不入库）
work\outringcheck\                     环内/环外与两个信号开关的真值表校验、环外读数与参考图的数值比对（不入库）
work\preview\                          离线 JVM 预览工装（不入库）
work\overview\                         全部支持样式的状态总览图（不入库）
.tools\                                JDK / Gradle / SDK / 本地 Maven 仓库
.ref\                                  参考模块与 libxposed 源码（分析用，不入库）
```

### 协议

本仓库以 **GNU GPL v3.0**（`GPL-3.0-only`）发布，全文在根目录的 `LICENSE`。

- 每个源码文件（`app/src` 下的 `.java` 与 `.kt`、`tests/` 里的 `.java`）头部都有一段
  `SPDX-License-Identifier: GPL-3.0-only` 注释。新增文件照抄同一段头。
- 改协议不是改一个文件：`LICENSE`、`README.md`、`module-README.md`（发布到 LSPosed 模块仓的
  首页）三处写的都是它，必须一起动。
- GPL 的传染性对**分发**生效，不分发就不触发。自己构建、自己装在手机上，不需要公开任何东西。
- 分发 APK 时（第 6 条）要同时给出对应源码。release 的 tag 就是这个对应关系，别删 tag。

## 测试

仓库**没有 Android 侧自动化测试**（`app\src` 下只有 `main`，没有 `test` / `androidTest` source set），
但 `tests\` 下有六个**纯 JVM 断言脚本**（见下面第 3 条）。回归验证靠三条通道：

1. **离线 JVM 预览**（`work\preview\`，不入库）：一套只服务工装的 `android.*` 桌面垫片 + 入口
   `PreviewMain`，直接编译 `Prefs.java` / `TrioSettings.java` / `TrioGeometry.java` /
   `TrioAppearance.java` / `TrioRenderer.java` 出图，不需要设备就能看几何。工装内嵌的
   `SharedPreferences` 替身**必须叫 `FakePrefs`** —— 叫 `Prefs` 会遮蔽真正的
   `io.github.yixing233.hyperduo.Prefs`，导致常量解析失败。渲染器依赖的外观规则全部在 `TrioAppearance` 里，
   所以它必须和 `TrioRenderer` 一起编进去，否则 `work\overview\run.ps1` 会以
   `cannot find symbol` 失败。垫片出图与真机不符时，**先怀疑垫片**（历史上
   `Canvas.restoreToCount` 的语义错实现过一次，症状是画布变换泄漏到后续所有绘制；矩形样式的
   `drawRoundRect` / `Path.addRoundRect` 也一度只有真机 API、垫片缺失）。
2. **上机验证**：`install.ps1` 装机后重启 SystemUI，看日志与状态栏实拍。
3. **纯 JVM 断言**（`tests\`，入库）：不依赖设备，把生产代码里的关键方法**原样抽出来**编译执行，
   方法改名或改签名就报错退出，所以它们锁的是真源码而不是副本。`tests\wifi-presence.ps1` 锁
   `WifiPresence.resolve/publish` 的 18 组真值表；`tests\battery-presence.ps1` 锁 `BatteryPresence`
   的瓦数换算与哨兵、`TrioHooks.perfText` 的分段与两项一行规则、`perfLines`，以及 `publish` 的
   去重（含 `NaN` 陷阱）；`tests\perf-layout.ps1` 锁自适应字号 `perfTextSize`、行距 `perfLineSpacing`
   与第十五轮的 `perfWidth` / `perfTypeWeight`
   —— 后者就是「两行合起来是一个图标高」那条身份断言，并且**把被否决的算法也钉住**
   （`room / (lines - 1)` 与 `room / lines` 两种分摊都被显式排除）。这条身份最初两边都用生产的
   `perfLineHeight`，因此只是**自洽检查**：若该函数整体带上一个会被字号公式约掉的因子，它照样成立；
   现在同一句话再用一支**从没经过 `perfLineHeight`** 的 paint 量一遍（`2 * (descent - ascent)` 对
   图标高），并断言两者互相吻合，这才让它成为独立事实。第十四轮给它补了 10 条断言覆盖
   `perf_scale_pct` 这个乘数（单项读数也缩放、三条兜底分支也缩放、clamp 到 60 与 150 两端）；
   第十五轮再补 11 条，覆盖 `perfWeight` 的两端 clamp 与默认值；随后又补 12 条**执行级**用例，把
   `perfWidth` 从「只有一条源码契约钉」抬到「真的跑过」——那条钉原本断言生产里写的是
   `perfWidth(perf,` 而不是 `perf.getMeasuredWidth()`，钉的是字面而不是答案，实现返回什么都算过。
   这 12 条覆盖：替换最宽数字确实让预留变宽、每个数字都按最宽的那个量、两个不同瓦数预留**同宽**
   （抗抖动核心）、答案是加宽后的 `(int) Math.ceil` 且该值非整数（取整方向可观测）、位数变化仍是台阶、
   **两行的预留等于较宽的那一行而不是两行之和**、最宽数字是问字体而不是假定 `'0'`（换一张字宽表答案
   跟着变）、空文本回落到视图已有的 frame、等宽字体下不偏短。为了让「替换」可观测，探针对垫片 `Paint`
   注入了 `setDigitWidths(float[])`：桌面 SansSerif 的十个数字都是 556/1000（实测），替换在这张脸上是
   恒等操作；真实状态栏字体是比例宽度（设备日志 `0.0W`=102px 对 `6.1W`=92px），那张表就是它的替身。
   现在共 **76** 条，打印
   `PASS: 76 cases over perfTextSize, perfLineSpacing, perfWidth and inkScale`；同一轮还修掉了复核
   指出的两处重复覆盖（近边界用例改用 `(int) Math.ceil(lh * 2f) - 1` 算确切行数）与一条自指的
   下限断言（改用 `eq("the legibility floor is 0.6 of the type size", PERF_MIN_SIZE_RATIO, 0.6f)`
   把下限当成外部事实来陈述）。此外它还补了三条**纯函数够不到的接线**，用源码契约钉而不是调用来锁：
   `perfLineHeight` 里 `PERF_PROBE.setTypeface(TrioRenderer.typefaceFor(weight))` 必须还在（否则
   字重分支消失，而桌面 AWT 对 PLAIN/BOLD 报告完全相同的度量，任何基于度量的断言都测不到它）、
   `refreshPerfLabel` 的**五路**重同步条件里必须还有 `perf.getLineSpacingMultiplier()` 与
   `perfLineSpacing(perf, text, container)` 的比较，以及第十五轮加的 `perf.weight != weight`、
   `updatePerfLabel` 里必须还是「先比再写」
   （`if (perf.getLineSpacingMultiplier() != spacing) { perf.setLineSpacing(0f, spacing); }`）。
   第十五轮又补了两条同类的钉，锁的是**图标右跳那个缺陷的两个 teardown 方法**：
   `removeOutTypeLabel` 与 `removePerfLabel` 的方法体里**禁止**出现 `releaseOutTypeSpace(`、
   **必须**出现 `reserveOutRingStrip(`。它们同样够不到——两个方法都要真的挂载视图才会跑——但正是
   这两句让「判空之前先把 strip 交还」这个写法无论怎么改回来都会被拦下；负对照插入抢先释放后探针以
   `removeOutTypeLabel still releases the strip before it knows what is mounted` 退出。
   另有一条**终点用例**盖住 M6 担心的隐患：把算出的行距写回视图后，`getLineSpacingMultiplier()`
   必须原样读回、与重新计算的值相等 —— 若这个比较永不收敛，每个 layout pass 都会再 post 一次同步，
   状态栏将无限重排；垫片按 Android 的方式逐位存回，四舍五入的垫片会正好掩盖它。
   这 12 条 `perfWidth` 用例做过**负对照**：把实现分别改坏成「不替换数字」「两行相加」「恒不更新最宽
   数字」「去掉向上取整」，四条各自死在不同断言上（第一条死在源码钉 `perfWidth does not widen the digits`，
   其余三条死在 `the pair's strip is not the sum of its lines`、`the reserve is wider than the literal`、
   `the answer is the ceiling of that widened width: got <126> want <127>`），所以这一组不是重复覆盖。
   **做负对照时必须用 `git checkout --` 还原源码，不要用 `Get-Content -Raw` 读回再写**：PS 5.1 按 ANSI
   代码页解码，会把 Java 文件里仅有的几个非 ASCII 注释字符（`—`、`→`）写成乱码，文件仍能编译、探针仍
   通过，但字节已经变了。
   `tests\glyph-scale.ps1` 锁**整体尺寸**这条端到端的线：它用 `tests\shims` 的桌面垫片把真
   `TrioRenderer` 画进 Java2D 位图，然后数**真正落笔的像素**，所以它不是公式层面的自洽检查，而是
   「这个设置确实到达了画布」。17 条断言里：相邻档位（100/90/80/70/60）墨迹必须逐档递减；墨迹**面积**
   随系数平方下降、**包围盒宽高**随系数线性下降（后两条才是「等比缩小」而不是「细节熔掉」的证据）；
   满档要铺满它被测量的宿主、最低档要离开四边并保持居中（这条抓的是 translate 没跟着缩放、图形缩在
   角落）；clamp 两端各渲染一次并与 floor/ceiling 的像素**逐位相同**；矩形样式单测一遍，免得乘数只接
   进了一种布局。另有四条**源码契约钉**：`final float scale = inkScale(w, h, rect) * appearance.glyphFit();`
   必须原样存在、`glyphFit()` 必须仍走 `Prefs.clamp(glyphScale, …) / 100f`、`TrioAppearance` 的构造
   必须仍从 `c.glyphScale` 取值 —— 像素能看出「缩小了」，但看不出「是在 fit 上缩的而不是在别处」。
   **负控实测**：把乘数整条删掉、把 clamp 去掉、把 `canvas.scale(scale, scale)` 改成
   `canvas.scale(scale * glyphFit(), scale)`（非等比）、去掉 `/ 100f`、把构造改成常量 100 —— 五条全部
   `EXIT=1`。其中最要紧的一条是**保留契约钉那一行字面量、但把它的值丢弃**（另起一个未缩放的局部变量
   交给 `canvas.scale`）：源码钉照样通过，只有像素断言 `the painted area falls with the square of the
   factor: got <0.6736883167543373> want <0.36000001430511475>` 抓到它 —— 这证明像素那部分独立承重，
   不是给契约钉当装饰。
   `tests\overlay-window.ps1` 锁**独立窗口那个私有标记**的整条接线（第三十轮，issue #28 / #33）。
   这条修复在桌面上特别别扭：平台的 `WindowManager.LayoutParams` 可能**两个隐藏名字都有、只有
   `privateFlags` 字段、或两个都没有**，而「有没有」是编译期事实 —— 一个测试类编不了三种平台。
   所以脚本的做法是**整棵重建 `work\overlaywindow\{src,classes}` 三次**，每次换成一种形状的
   `android.view.WindowManager` 桩，把同一份 `OverlayTest.java` 编一遍再跑：形状「字段 + 常量」
   9 条、形状「只有字段」3 条、形状「都没有」4 条。断言里 `owned=0x08` 代表「平台自己拥有的位」，
   用来证明写回的是**或**而不是赋值；形状「只有字段」那组则证明没有常量时**窗口一个位都不动**。
   另有八条**源码契约钉**补在形状断言够不到的地方：两个隐藏名字必须仍按字符串反射读、**禁止**
   `params.privateFlags`（这是编译不过的写法，也正是 PR #29 第一版被作者自己换掉的原因）、必须是
   `field.getInt(params) | trusted`、必须有 `if (trusted == 0) {`、`FLAG_NOT_TOUCHABLE` 必须还在、
   `markTrustedOverlay(params);` 必须出现在设置窗口 flags 之后、`TYPE_STATUS_BAR_SUB_PANEL` 仍必须
   优先尝试。**负对照实测五条全部 `EXIT=1`**：改成赋值死在第 2 条、删掉零判死在第 5 条、写成直写
   死在「不可直写」那条、把常量 `| 1` **只改位而保留全部字面量** 则由**执行级**用例抓到
   （`the flag reads back what the platform declares: got <536870913> want <536870912>`），
   换回 HEAD 版死于抽取那一句。最后一条是要紧的：它证明形状断言独立承重，不是给源码钉当装饰。

   `tests\drawing-zones.ps1` 锁**「绘制」分页那三块热区**（第三十一轮，详见下面「绘制分页」一节）。
   它是这批工装里唯一一个把**命中判定**当被测对象的：把真 `TrioRenderer` + `TrioZones` 画进
   960×960 的 Java2D 位图（8 像素/设计单位，于是接缝两侧各约 1 像素的抗锯齿边远在最小余量之内），
   然后断言 (a) 每一个落笔的像素都能反查到一个分区、且三个分区能**铺满**整个 120×120 设计空间；
   (b) 表里写的「这一格是谁」确实是画在那里的那个部件——用**逐个关掉**该部件、看这一格**少了**
   哪些墨迹来隔离，而不是「拨动 X 只影响 Y 格」。共 **566** 条断言、24 种配置 × 3 档电量、539 次渲染，打印
   `PASS: 566 cases over 24 configurations and 539 renders`。

   **这一轮的第二个教训：单一维度的扫描会漏掉最宽的那一种输入。** 第一版 24 种配置**全部**只在一个
   电量（两位数 `79`）上渲染，而电量数字是**文字**——三位数 `100` 比两位数宽得多，于是「最宽的那一
   种」从来没被测量过。这个盲区正好盖住了一个真缺陷：矩形样式的中段接缝比渲染器允许文字画到的范围
   窄 3 个设计单位，导致 `100%` + 大字号时点在数字**右边缘**会**把移动信号表关掉**（静默写错一个
   持久化设置）。修法是两件事一起做：把 level 变成**要扫的维度**（`LEVELS = {79, 5, 100}`），并加一
   条直接针对墨迹的正面断言 `rectCentreHoldsItsText()`——对**每一个**字号渲染三位数 `100`，断言它
   的**每一个像素**都反查到中段，外加一条镜像断言（点列墨迹必须反查到下部）防止「把中段撑大到吞掉
   点列」这种假修法。负对照：把接缝改回旧的 `+3f`，工装报
   `.../L100: VALUE loses ink only in the thirds that name it: got <BOTTOM> want <>` 并 `exit 1`。
   **探测下限**：逐档试 `0.001 / 0.01 / 0.5 / 1.0` 个设计单位，前三个 `exit 0`、1.0 才失败——小于约
   1 个设计单位的偏移在 960² 下测不出来（亚像素）。修好后接缝由 `TrioGeometry` **导出**、不再可调，
   所以这个下限是记录而非遗留风险，但**如果将来有人重新引入可调的接缝，0.5 个单位的错误会悄悄通过**。
   完整复盘见 `docs\SWITCHES-REVIEW.md` §6.16.1。

   同一轮的负对照（inset 断言）：把 `TrioZones.viewTransform` 里的 `t[1] += pad` 改成
   `pad * 2f`（inset 折两次）→ `painted ink at 537,69 resolves to no zone`；整句删掉（inset 完全不折）
   → `painted ink at 858,311 resolves to no zone`；两条都 `exit 1`。

   **为什么是「包含」而不是「独占」——这是这一轮的教训。** 第一版断言写的是「关掉一个部件，
   它只应该从表里点名的那几格里掉墨」。这条在**正确的代码上**就失败了，因为这个字形的部件真的会
   互相让位：打开电量数字会把 Wi-Fi 弧从圆心挪进缺口，关掉它则圆环的 12 点开口**闭合**、弧线直接
   画过原来数字所在的位置。于是同一格里「数字没了、弧线来了」，按像素计数根本分不出来。修法是把
   **断言本身**改弱成真的那一句（关掉一个部件，不得从表里**没有**点名给它的格里掉墨），而**不是**
   把检查放宽到能过。两处混淆是在代码里明说的，不是含糊过去的：
   - **开口开合**：只有两帧的 `TrioAppearance.Ring.gapUsed` 一致时才做严格比较（读**渲染器自己
     遵守的那条规则**，因为这个变化恰恰是像素不可信的地方）；不一致时只断言「这一下确实改变了画面」。
   - **前置条件**：`drawsBolt()` 是 `boltWanted && value`，所以关掉电量数字**允许**连带带走闪电的
     墨迹。这就是全字形里唯一一处「一个开关是另一个开关的墨迹前提」，写成了 `gates(Part, Part)`。
   矩形样式没有开口，所以它在**所有**配置下都受严格形式约束。

   同一轮的负对照（inset 断言）：把 `TrioZones.viewTransform` 里的 `t[1] += pad` 改成
   `pad * 2f`（inset 折两次）→ `painted ink at 537,69 resolves to no zone`；整句删掉（inset 完全不折）
   → `painted ink at 858,311 resolves to no zone`；两条都 `exit 1`。

   六个脚本都只读源码、写到 `work\` 下编译，不碰 `app\src`。

   `tests\overlay-window.ps1` 曾经锁**独立窗口那个私有标记**的整条接线（第三十轮，issue #28 / #33）：
   平台的 `WindowManager.LayoutParams` 可能**两个隐藏名字都有、只有 `privateFlags` 字段、或两个都
   没有**，而「有没有」是编译期事实——一个测试类编不了三种平台，所以那个脚本整棵重建
   `work\overlaywindow\{src,classes}` 三次、每次换一种形状的桩。**第十七轮随窗口一起删除了**：
   字形改由 `GlyphDrawable` 走 MIUI 自己的 drawable 槽，模块不再创建窗口，私有标记与遮挡判定都不
   再适用（历史见 `docs\SWITCHES-REVIEW.md` §6.15）。
   它的接班人是 `tests\glyph-drawable.ps1`：窗口路线换成槽路线，**被锁的机件换了，但「平台可能长
   什么样」这件事没变**——这个脚本把真 `GlyphDrawable.java` 与真 `Refl.java` 编进**五种宿主形状**
   （字段 + public `clearTint` 的正常 build、`mUseLegacyDrawable=true` 的 legacy build、没有
   `clearTint`、`clearTint` 是**包级私有**、以及连 `mTintBgDrawable` 字段都没有），共 105 条断言。
   包级私有那一形状不是凑数：`Refl.callByName` 走 `getMethod`，看不到非 public 成员，所以「交不回槽
   就置 null」这条兜底必须有；去掉这条兜底，只有那个形状会红。断言分两类：**源码契约钉**锁桌面看不见
   的（槽名与 legacy 名仍是那两个、`GlyphDrawable` 不再乘一次 `glyphFit`、类里不出现任何
   `WindowManager`/`addView` 窗口痕迹、`install` 写完必须读回校验、`uninstall` 是「先判归属 → 再求
   MIUI 重建 → 最后才置 null」的次序、`TrioHooks` 里重绘必须由 `install` 的返回值 gate、总开关关闭
   时必须 `uninstall`）；**执行级用例**驱动真类跑（首次抢槽、第二帧不重复抢且换的是同一个 drawable、
   重新 `install` 会把旧 drawable 重新指向新 state、`draw` 交出去的宽高就是 `getBounds()` 的宽高、
   零面积/零高/倒置的盒子不画也不刷新 state、`uninstall` 后槽归 MIUI、对持有 MIUI 自己 artwork 的
   宿主**完全不打扰**、重复 `uninstall` 无害）。**负控实测七条**：`install` 去掉写回校验、反转载
   bounds 守卫、`uninstall` 不再判归属、把「置 null」挪到 `clearTint` 之前、`drawState` 的宽高对调、
   重新 `install` 不再重新指向 state、以及把 `getBounds()` 换成常量。其中**后三条只被执行级断言抓到**
   （源码钉全部原样通过），证明这个探针的运行时那半独立承重，不是给契约钉当装饰。
   五个脚本都只读源码、写到 `work\` 下编译，不碰 `app\src`。
   **垫片放在 `tests\shims`（入库）而不是 `work\preview\src`（`work\` 被 gitignore）**：`tests\glyph-scale.ps1`
   编译的是**真 `TrioRenderer`**，需要 `android/graphics/{Canvas,Paint,Path,RectF,Color,PorterDuff,
   Typeface}`、`android/view/View`、`android/{content/SharedPreferences,os/Bundle}` 与两个同名桌面替身
   （`TrioConfig`/`TrioState`）。这些垫片原先只在 `work\preview\src` 里有一份，而那棵树是**不入库**的，
   于是「把这条证明入库」的目的会被自己拆掉——工作区一清理，脚本就跑不起来。
   `tests\perf-layout.ps1` 也曾经从 `work/preview/src` 拷 `Typeface.java`，同样已改指 `tests\shims`。
   （`tests\overlay-window.ps1` 不在此列：它自己生成 `android.view.WindowManager` 的三种桩，
   与 `tests\shims` 无关。）
   判断方法很简单：把 `work\preview\src` 改名后再跑，六个脚本都应仍 `exit 0`。

   （已删除的 `tests\overlay-window.ps1` 曾不在此列：它自己生成 `android.view.WindowManager`
   的三种桩，与 `tests\shims` 无关。）
   判断方法很简单：把 `work\preview\src` 改名后再跑，五个脚本都应仍 `exit 0`。
   **注意它们不会被自动发现**，要手动跑：
   `powershell -NoProfile -ExecutionPolicy Bypass -File tests\battery-presence.ps1`。
   PS 5.1 下**必须**这样经由 `cmd`（或让脚本自己吞掉 javac 的 stderr）运行：javac 会往 stderr 写
   一条既有的 deprecation 注记，而工装都设了 `$ErrorActionPreference='Stop'`，直接调用会把这条
   注记当成致命错误（`NativeCommandError`）。
   `tests\perf-layout.ps1` 另有一条**垫片陷阱**值得记住：它自带的桌面 `android.graphics.Paint`
   必须把 `getFontMetrics().ascent` 写成**负值**（Android 约定），而不是照抄 `work\preview` 垫片里
   AWT 的正值 —— 正值会让 `descent - ascent` 变成负数，于是 `perfTextSize` / `perfLineSpacing` 全部
   走「量不出来」的兜底分支，探针会在什么都没量的情况下「通过」。同理，字体度量要按参考字号量一次
   再线性缩放：AWT 会把字号取整到整点，而 Android 的度量对字号严格线性，否则那条身份断言会以
   半个像素的误差假失败。
   写这类脚本还有个通用要求：**生成目录必须每次整棵重建**。编译步骤收的是 `$src` 下所有 `.java`，
   若只清 `classes` 而不清 `src`，上一版遗留、这一版不再生成的文件会继续参与编译，一个陈旧定义
   足以遮蔽同名的新定义，让探针对着已经不存在的代码通过。（`tests\perf-layout.ps1` 已按此修正。）

另有 `work\gapcheck\verify.ps1`（不入库）专门盯**电量环缺口**：它把同一份 `GapProbe` 跑两遍 —— 一遍
编工作树的 `TrioRenderer`，一遍把 `batteryRing` 单独换回旧算法（其余代码不动，免得测到另一个程序）。
`GapProbe` 用反射直接驱动真渲染器并沿环心线回读像素，按「可见弧」区间模型逐档断言 0–100% 的左右末端、
缺口内是否无墨，再走 `drawInto` 的槽位决策做端到端确认。固定版必须 `exit 0`、旧算法必须
`exit 1`（旧算法在 45/55/60/70/75% 上共 42 项不符）——**两边都跑才说明探针真的在测这个 bug**；
只跑通过的那一边等于没有反例。探针同时产出 `work\gapcheck\out\mouth-{old,new}.png` 对照图。

另有 `work\slotcheck\verify.ps1`（不入库）盯**居中模式的槽位优先级**。它把同一份 `SlotProbe` 跑两遍
（工作树一份、`git show 312ae00:` 重建的旧渲染器一份，旧源必须先断言含 `boltCentreOffsetX`、不含
`drawGapType`）。**基线必须是写死的修订号，不能是 `HEAD`**：修复未提交时 `HEAD` 正好是旧版，
提交之后 `HEAD` 就是修复本身，探针会拿自己和自己比、然后「证明」一件它没测过的事——本脚本在
提交后确实这样假绿过一次，是被那句 `boltCentreOffsetX` 断言拦下的，所以那道断言不是多余的。
判定用**无色不变量**：圆心圆盘（`CENTER_CY` 处半径 18 设计单位）内的像素在「有闪电」与「无闪电」
两种渲染下必须逐像素相同——低电量角色色 `0xFFF2B900` 与快充琥珀色 `0xFFFFBA28` 太近，
按颜色计数不可靠，所以只比像素。渲染时置 `cfg.roleColors = false`，否则数字颜色会随充电标志变化而
掩盖结论。另有对照组防空洞：未居中 + Wi-Fi 3 与居中 + Wi-Fi 3 的圆心盘哈希必须不同，否则「有/无闪电
相同」什么都没测。新版必须 `exit 0`、旧版必须 `exit 1`（旧版 6 项不符：闪电落在圆心、缺口无琥珀、
Wi-Fi 0..3 四个哈希各不同、环内类型扰动圆心、缺口以下也被改动），同时要求 12 个非居中状态在新旧
两版之间哈希逐行相等，锁住「本改动只影响居中模式」。探针产出 `work\slotcheck\out\slots-{old,new}.png`
六格对照图，格下说明走 `caption` 逐字换行并对超宽/超高一并 `throw`（同样是「宁可失败也别裁字」）。

另有 `work\dualsimcheck\verify.ps1`（不入库）盯**双卡开关的判定规则本身**。`gapcheck` 与 `slotcheck`
测的是电量弧算术与居中优先级，都不覆盖「什么时候才该画两排」；`overview` 只画出一个双卡格，证明
的是**两排画得出来**，不是**只在该画的时候画**。它把同一份 `DualSimProbe` 跑**三遍**，每次只换一个
文件：`fixed` 编工作树；`bolt` 把两处 `dualSimRows` 调用点从 `charging` 换回 `bolt`；`narrow` 把
`GAP_START_DUAL`/`GAP_END_DUAL` 退回 `GAP_START_IDLE`/`GAP_END_IDLE`（即用户投诉的上排拥挤状态）。
探针分五组断言：`rule()` 逐条查 `dualSimRows` 的真值（无 Wi-Fi 无充电双卡为真；有
Wi-Fi 墨迹 / 充电 / 只读到一张卡 / 一张都没读到 / 开关关 / 移动信号表关，六种情形全假）；`layoutFlag()`
直击那个真缺陷——`showValue=false`（于是 `drawsBolt()` 为假）时插着充电器，`Ring`/`Rect` 的 `dual`
标志必须仍为假；`pixels()` 用 MD5 逐像素哈希要求「被抑制的帧」与「开关关掉」的帧**完全相同**（这才
叫回退成单排），并要求该画时两者必须不同；`rows()` 按 `DOTS_DUAL` 与 `inkScale` 复算 8 个圆心、读
圆心核心区 alpha，锁住**上排 = slot 0**；`separation()` 把上排拥挤变成可断言的量——对每个点从其
**中心像素**做迭代式 4-连通 flood fill，要求连通域面积约等于单个点（`area / (π·DUAL_DOT_R²·scale²)
< 2.5`），因为一旦与弧的圆帽连成一体，面积会涨到数倍（`narrow` 版实测外侧两点为 **8.22 / 5.58** 个
点面积，`fixed` 版 8 个点全是 **0.97..0.99**）。`fixed` 必须 `exit 0`，`bolt` 与 `narrow` 必须
`exit 1`（实测分别 3 条、2 条不符）。

要给用户看这次修复的前后差别，用 `work\dualsimcheck\before-after.ps1`：它编两遍（工作树 vs 把两个
`GAP_*_DUAL` 换回 idle 值），再用 `TopZoom.java`（12 点钟区域放大 3 倍）与 `DualSimShot.java`
（四态总览）各出一张，最后由 `Panels.java` 拼成上下两栏的 `out\top-before-after.png` 与
`out\dualsim-before-after.png`。**一次编译只能表现一个几何**，所以对照必须两次编译 + 外部拼图。

**采样坑（踩过一次）**：`rows()` 最初取 26px 窗口内的**最大** alpha，结果 `{2,4}` 时最外侧的上排点
被判成亮——它在 720px 图上是 `(522,102)`，离圆环弧只有约 25px，窗口把弧吃了进来。正确做法是读圆心
**核心区**取**最小** alpha：亮点为 255、轨道点恰为 `trackAlpha`，两者判然可分。只看窗口内亮点计数或
最大值都会把「紧挨弧的点」读反。

另有 `work\outringcheck\verify.ps1`（不入库）盯**环内/环外与两个新开关的真值表**。它编
`work\outringcheck\src\io\github\yixing233\hyperduo\OutRingProbe.java`，分五组：

- `foldRule()` 逐条查 `TrioAppearance` 的表面：环内 `signalDots()`/`foldsMobile()` 为真而
  `stackedOut()` 为假；环外 + 堆叠关三者**全假**（并显式断言「mobile 槽原样留着，MIUI 才有图标可画」）；
  环外 + 堆叠开为 假/真/真；再叠 `data_sim_only` 仍为 真/真；总开关关、`show_mobile` 关各自
  「不折叠也不自绘」；最后两条查 `Rect.dots` 与 `Ring.dual` 在环外都为假。
- `inRingUntouched()` 是**环内逐字节不变**这条承诺的可执行版本：3 组槽位读数 × 2 种双卡开关 ×
  2 种样式共 12 组，要求 `signal_mode=环内` 时「两个新开关都关」与「都开」的 720×720 渲染哈希**相等**。
- `reading()` 查 `outSignalLevels` 的读数解析（两卡取槽序、单卡只画柱、`dataSimOnly` 命中上网卡、
  上网卡沉默时退回另一张已应答的卡等 11 组）。
- `geometry()` 查参考图比例式（列数、柱高严格递增、`STACK_INK_W/H` 与 `STACK_BAR_H[3]+gap+D` 的关系），
  并查**盒子始终按 `STACK_INK_H` 定比例**：`outSignalWidth(h)` 与 `h*STACK_INK_W/STACK_INK_H` 相等、
  高度为 0 时宽度为 0，以及 `outSignalHeight` 的 dp 语义（`15dp × density` 就是像素高、密度越大
  像素越多、非正 dp 或非正密度得 0、下限仍留 1 px）。
- `pixels()` 在真实画布上验柱底共线、最高柱在最右、未点亮列既不透明也不与点亮列同色、点行在柱底下方，
  以及**同一行上有无点行的最高柱一样高**（尺寸异常的回归断言）。

三条反例构建都由 `[regex]::Replace` 从工作树文本生成：`refold` 把 `foldsMobile()` 尾上 `|| signalOutOfRing`
（环外一律折叠，模拟「交还系统却把系统图标摘了」），`inert` 把 `signalDots()` 尾上 `&& !stackedSignal`
（环内也被堆叠开关压住），`squat` 同时改回 `drawOutSignal` 的 `scale` 与 `outSignalWidth` 两行的旧分母
（只改一处是看不见的 —— 两处互相印证，正是尺寸异常能长期存活的原因）。`fixed` 必须 `exit 0`，
三条反例必须 `exit 1`（实测分别 3 条、14 条、3 条不符；`squat` 实测最高柱 51 vs 72 px，即 1.39 倍本身）。
脚本开头有一道**源码钉**：`TrioHooks.java` 里若已不存在 `if (a.foldsMobile())` 就直接 `throw`——
否则探针会变成在测一条没人调用的规则。同一段还有**环外前景色跟随深浅色**那一批钉子（`TrioHooks`
桌面上编不了，只能钉源码）：只在不等的帧记账、先记账再 post、标签 `setTextColor(ink)`、读数
`signal.invalidate()`，四条成对断言，外加两条形状断言 —— `OutSignalView.onDraw` 上那句
「the row is invalidated when the tint changes」的错误注释不得复活，`TrioState.outRingInk` 必须是
实例字段（`static` 即报错）而 `foreground()` 必须仍把 `0` 折成 `DEFAULT_FOREGROUND`（否则初值
`0` 会与首帧读数相等，第一次着色被当成「没变」而跳过）。七种回归形态都实测能把钉子碰响。

**为什么尺寸断言不能自己算取样列**：`squat` 第一次跑出来是「51 vs 0 px」，因为探针用**正确**的公式
去算最高柱的横坐标，而那个构建的几何恰恰是错的，取样列整个落在柱外，量到的是空画布。断言「两边一样大」
时，取样点必须与几何无关（取全画布最高的亮柱，`tallestRun`），否则一条本意是抓尺寸错的断言会退化成
在抓「那里没东西」。

**为什么断言「未点亮」要挑对列**：`pixels()` 最初在第一列取点验底纹，而三点读数下第一列的点是亮着的，
于是那条断言恒假，看起来像产品缺陷。改成在**第四列**取样（三点的读数下第四列必暗）并补一条
「未点亮的点仍在屏幕上（alpha > 0）」才通过。断言「没有」必须取**确定没有**的位置。

**与参考图的数值化比对**（`work\outringcheck\OutSignalShot` + `compare.py`，不入库、不参与 `verify.ps1`）：
`OutRingProbe` 只能证明源码自洽 —— 它拿 `TrioGeometry` 的常量去核对 `TrioRenderer` 用它们画出来的像素，
两边同时错也照样通过。要抓这类错只能出图：`OutSignalShot` 把三种读数按**参考图 1 单位 = 1 像素**画在
恰好等于各自 ink box 的画布上（`scale` 恰为 1，没有任何取整问题），产出 `work\outringcheck\out\out-signal.png`；
`compare.py` 再用**同一套量法**分别量参考图与渲染结果，比列数、列距、柱宽、四根柱高、点行直径、
柱底到点行的空带。**两边都量**是关键：不拿源码里的数字去核对源码。

这条比对当场抓到一个真错误：空带本是 **9** 而不是 10。参考图无抗锯齿、边缘落在整像素上，柱底最后一行
墨是 237（下边缘 238）、点行第一行墨是 247，空带 `238..246` = 9；按「两段墨迹首行下标相减」量会得到 10。
原先的 `STACK_DOT_GAP = 10f` 因此让整个读数高一个单位（`STACK_INK_H` 210 → 应为 209）。改成 9 后
`compare.py` `exit 0`，三格（两卡 4 柱+4 点 / 单卡 4 柱 / 仅上网卡 2 柱）的量测与参考图逐项相等。

另有 `work\simcheck\verify.ps1`（不入库）盯**订阅号到槽位的映射**与**单排兜底**——这是双卡信号里唯一
无人覆盖的一段。上一条 `dualsimcheck` 验的是「什么时候画两排」，`overview` 验的是「两排画得出来」，
而**哪一排是卡一**、以及**只画一排时画哪张卡**，都发生在 `TrioState.sampleSims` 里，只有真机
SystemUI 进程会跑到它。它把真实的 `app\src\main\java\io\github\yixing233\hyperduo\TrioState.java`（不是
`work\preview` 里那个同名桌面替身）编进一套 `android.telephony.*` 垫片里跑两遍：一遍编工作树，一遍把
`final int slot = info.getSimSlotIndex();` 换成 `info.getSubscriptionId()`（**只动这一行**）。探针分七组：
`plumbing()` 查无 context 时惰性、attach 后才通；`mapping()` 是核心——**故意用与槽位不相等的订阅号**
（`subId 9 → 槽 0`、`subId 5 → 槽 1`）断言电平落在**槽**而非订阅号上，另加反向槽序与一个槽位越界的
订阅（不贡献任何一排、但 `sDataSlot` 仍解析出来）；`fallback()` 断言图标链静默时取**当前上网卡**而非
槽 0，且上网卡自己没读数时**不从槽 0 借**；`gating()` 断言开关关且图标链已答时**根本不去问 telephony**；
`degrade()` 逐项拔掉订阅服务 / telephony 服务 / 订阅列表 / `createForSubscriptionId` / `getSignalStrength`，
要求**要么保留上一轮读数、要么降级为 -1，绝不误报**，并锁住「电平 0 是真实读数（有卡无服务）」与
「0..4 之外一律拒绝、不钳制」；`pollNow()` 要求事件驱动刷新**只在真变化时**报变化；`miuiLevel()`
（第五组加的那批）钉住**读的是 MIUI 的电平而不是 AOSP 的**——见「双卡信号」一节的说明。
现在共 **47 条断言**，且是**三构建**对照：固定版必须 `exit 0`（47 条全 `[ok]`）、
「订阅号当槽位」版必须 `exit 1`（实测 27 条不符）、「把 `miuiLevel(strength)` 换回 `strength.getLevel()`」
版必须 `exit 1`（实测**恰好 4 条**不符，且正是那 4 条 MIUI 断言 ⇒ 分离度精确到条）。

**门控判定的坑（踩过一次）**：`gating()` 一开始对每次 `refresh()` 都新建 host，结果它是被 2 秒限流
而不是被开关拦下的——断言会**因为错误的原因通过**。必须复用同一个 host、并在两次 `refresh()` 之间把
`SystemClock.now` 步进 2 秒以上，让限流不再起作用，测的才是门控本身。

前三条工装都直接驱动渲染器，所以**渲染器的私有签名一改就会连带弄坏它们**，而它们又不在
`:app:compileDebugJavaWithJavac` 里，改完主代码不跑一遍是发现不了的。已知会咬人的三处：`drawBattery`
在 `TrioAppearance` 抽取后从收 `TrioSettings` 改成了收已解析的 `TrioAppearance`；`drawInto` 在双卡信号
里于 `mobileType` 之前插入了 `int[] slotLevels`；三个工装的编译源清单里还必须带上
`TrioAppearance.java`（渲染器把外观规则全问它），但**不能**带 `PreviewMain` / `OverviewMain`
——`slotcheck` 的旧版构建会拿它们去调旧渲染器，编译期就失败。因此 `GapProbe` 按新签名反射，
`SlotProbe` 则在运行期先找带 `int[].class` 的 `drawInto`、找不到再退回旧签名并补 `null` 占位，
这样同一份探针源码能同时链上新旧两版渲染器。`DualSimProbe` 走的是当前签名，因此它只编当前
`TrioRenderer`，靠换 `TrioAppearance` 造反例。

`simcheck` 是这里面唯一**不碰渲染器**的：它编的是 `TrioState.java`，因此受渲染器签名变化影响的是
反过来的方向——它不能带上 `work\preview\src` 里的 `TrioState.java` / `TrioConfig.java`（同名桌面替身，
会撞类），只能借那里的 `SharedPreferences.java` 与 `Bundle.java` 两个纯垫片；`TrioConfig` 则由
`simcheck\src` 自带一份**可设置的**替身（开关要在运行中途翻），`Refl.java` 是纯 JDK 所以直接编真源。
**替身要跟住真 `TrioConfig` 的公开面**：加 `appearance()` 那次（`refresh()` 改读
`appearance().stackedOut()` 来决定要不要为环外读数采分卡电平）替身没跟上，`simcheck` 立刻以
`TrioState.java:199: 错误: 找不到符号` 失败——所以替身补了 `appearance()`（`TrioAppearance.of(snapshot)`，
该类是纯 Java、无 android 端口，能直接编），并把 `TrioAppearance.java` 加进编译源清单。
`TrioConfig` 或 `TrioState.refresh()` 的采样门控一改，就要先跑这条工装再谈别的。
改完渲染器请把五条 `verify.ps1` 都跑一遍；动过 `TrioState` 的采样逻辑则补跑 `work\simcheck\verify.ps1`；
动过 `TrioAppearance` 的规则谓词则补跑 `work\outringcheck\verify.ps1` 与 `work\dualsimcheck\verify.ps1`；
**动过环外读数的几何常量**（`TrioGeometry` 的 `stacked out-of-ring signal` 一节）还要重跑出图工装
`work\outringcheck\OutSignalShot` + `compare.py` —— 那批常量只有它与参考图逐项比对过，五条
`verify.ps1` 里没有一条能发现「常量整体偏一个单位」这类错。

另有 `work\overview\run.ps1`（不入库）：把渲染器**实际支持的每一个样式**画成一张状态总览图
`work\overview\out\overview.png`（1788×2248，3 组共 26 格）。它复用 `work\preview` 的桌面垫片，
每格都以 `TrioSettings.defaults()` 为底再叠加该格的单项 tweak，因此代表的始终是出厂外观。用途有二：
一是改渲染器后一眼看全所有样式的回归（新样式没进这张图就等于没被清点），二是给用户/文档出图。
出图脚本会打印被编译的 `TrioRenderer.java` 的 SHA256，便于确认图对应哪份代码。

**当前支持样式的清单**（即总览图的三组，也是渲染器能力的边界）：

- 电池（顶部）10 格：充电中 / 快速充电 / 用电中（数字）/ 低电量 / 低电量模式 / 危险电量 /
  已充满 / 只显示圆环 / 无 Wi-Fi 时数字居中 / 无 Wi-Fi 时充电且数字居中。
- Wi-Fi（中部）7 格：已连接 3 格 / 2 格 / 1 格 / Wi-Fi 开启未关联 / Wi-Fi 关闭或不可用 /
  电量数字居中 / 居中后充电（闪电在缺口）。

注意两个「居中」不是一回事，别混：电池组里的「**无 Wi-Fi 时**数字居中」是**自动**行为
（`wifiInk` 为假时数字自己掉进圆心，没有开关），Wi-Fi 组里的「电量数字居中」才是那个**设置项**
（`swap_wifi_value`，用户手动打开）。
- 移动信号与网络类型 9 格：信号 4 格 / 2 格 / 无信号 / 双卡信号（4 / 2 格）/ 网络类型环内 4G /
  环内 5G / 环内 5GA / 类型关闭 / 类型环外。**环外类型不由本 Canvas 绘制** —— 它由 `TrioHooks`
  另建的 `OutTypeLabel extends TextView` 画在电池表左侧，所以那一格是手绘标签示意，不是渲染器输出。
  双卡格故意取两卡电平不同（4 / 2），这样它若悄悄退化成单排会「看起来就不对」，而不是看起来像
  一个合理的单卡读数。双卡格在图上仍走圆环样式（矩形样式的两列本来就各认一张卡）。

另有一条只在双卡手机上会露面的形状：「双卡信号」（`dual_sim_signal`，见「双卡信号」一节）。
它在**无 Wi-Fi 且未充电**时把一排点换成上下两排（上排卡一、下排卡二），圆环样式下两排落在
圆环的两个缺口里（上排那个缺口会被张到与底部开口等宽，否则点会与环的圆帽糊在一起），
矩形样式下两列各认一张卡。判定收在 `TrioAppearance.dualSimRows` 一处，渲染端只问「画几排」。

参考的 macOS 版总览图里还有两组本模块**没有实现**，不要误画：**蓝牙音频**（4 格）与**音量**（7 格）
在 `app\src\main\java\io\github\yixing233\hyperduo` 下搜 `volume|bluetooth|audio|headset|earbud` 无任何匹配。
此外参考图的「已连接电源，未充电」也无对应状态 —— 渲染器只有充电 / 未充电两态，不区分插电未充。

## 验证记录

- 几何离线校验（渲染）：`work\geocheck\geocheck.png` 通过。
- 信号名解析校验：112 个资源名逐条判定正确。
- 上机（小米 14 / HyperOS 4）：9 个 hook 全部安装（日志 `HyperDuo installed, hooks=9`），无
  `AndroidRuntime:E`；状态栏 / 锁屏 / 控制中心的原生 Wi-Fi、移动（含 `stacked_mobile`）、电池
  图标均被抑制；屏幕中央不再残留游离的「5G」；Wi-Fi 关闭时不画弧；深色背景下前景色取样正确；
  切换设置不重启 SystemUI 即生效。
- 居中开关（存储键 `swap_wifi_value`）上机双向验证（小米 14 / houji / Android 17 / HyperOS 4 / KernelSU，
  `versionCode=10300` / `versionName=1.3`，`adb install -r` Success、SystemUI 重启后
  `HyperDuo installed, hooks=9 enabled=true`、无 `FATAL EXCEPTION`）——三态各取一帧，8x 放大目视：
  - `work\ondevice\v13-ring-8x.png`（充电 + Wi-Fi 关闭，真实 100%）：缺口里是**小闪电**，
    圆心是 **100**——即「数字优先级最高」，不是旧版的大闪电占圆心。
  - `work\ondevice\v13-charge-wifi-8x.png`（充电 + **Wi-Fi 已关联** `OpenWrt` / RSSI `-38`）：
    缺口只有小闪电，**两条 Wi-Fi 弧整组不画**（验证 `cfg.showWifi && (!centred || wifiInGap)` 这道门）。
  - `work\ondevice\v13-wifi-8x.png`（`dumpsys battery unplug` + Wi-Fi 已关联）：**弧缩进顶部缺口**、
    数字仍在圆心。
  截图 `v13-charging.png` / `v13-charge-wifi.png` / `v13-wifi.png`，裁图用
  `& "$env:JAVA_HOME\bin\java.exe" -cp work\overview\classes Crop <src> <dst> 1055 20 90 85 8`。
  取完把 Wi-Fi 关回 `off`、`dumpsys battery reset` 已确认（`USB powered: true` / `level: 100` 为真实状态）。
  按字节复核「设备上跑的就是这一份」：`adb pull` 设备 `base.apk` 与
  `app\build\outputs\apk\debug\app-debug.apk` SHA256 逐字相同
  （`8F951FD65CC7B3625AA881FE021C96D3E701A7D64162BFB5829BA953FDA177C8`，33019398 B）。
  **修正记录**：早期实现让「居中 + 充电」时大闪电占圆心、数字缩到缺口，与「数字在居中模式下优先级最高」
  的语义相反；1.2 上机截图正是这个错误形态。`work\slotcheck\verify.ps1` 用同一探针在新旧两版之间对照
  （新版 exit 0 / 旧版 exit 1，6 项不符），并把 12 个非居中状态锁成逐像素不变。
  **探针基线必须写死修订号**：它原先按 `HEAD` 重建旧版，修复提交后 `HEAD` 就是修复本身，
  会自己跟自己比却照样打印 `VERIFIED`——现已固定为 `$BaseRev = '312ae00'`。
- 更新器上机验证：无 Release 时点「检查更新」显示「作者尚未发布任何正式版本」（404 视为正常
  空答案而非失败），界面不卡死、不误报。
- 更新器端到端验证（真机）：把 debug 包故意装成 0.9(900)，走完「检查更新 → 下载更新 → 安装」，
  版本变为 1.0(10000)。设备到 github.com 时快时慢（同一 asset 在 1.5s 与 15s 超时之间摇摆），
  故下载先打 `api.github.com/repos/…/releases/assets/<id>`（配 `Accept: application/octet-stream`，
  实测 10/10 成功），失败再退到 `browser_download_url`，每个 URL 重试 3 次。
- 安装被拒的两条路径分开处理：`InstallResult.NeedsPermission`（缺「安装未知应用」授权）弹出提示
  并跳到该设置页；`InstallResult.NoInstaller`（设备上没有能处理安装 intent 的应用）只弹提示，
  不跳设置页 —— 授权已经给出时把用户送去设置页会让他面对一个无处可改的界面。
- 「查看仓库」：更新卡片最后一行，**是卡片里唯一无条件存在的行**；卡片其余各行都遵守「只有动作
  当前可行时才出现」的规则，这一行故意破例——用户最需要仓库的时刻恰恰是卡片答不上来的时候
  （作者尚未发布 Release、检查失败、changelog 说得不够）。放在最后因此不打断
  「检查更新 → 下载更新 → 安装」的主线。地址取自与 `API_LATEST` / `RELEASES_PAGE` 同一个
  `REPO` 常量（`REPO_HOME`），三者不可能漂移；与「打开发布页面」互补，一个看源码一个看 Release。
  `openReleasePage(url)` 因该行为而更名为 `openInBrowser(url)`（它本来就是 URL 泛型的，
  旧名字对第二个 URL 是谎话），没有留下别名。`assembleDebug` 通过，`aapt2 dump resources`
  确认 `update_repo` / `update_repo_summary` 在中英两个 locale 都已打包。**未上机验证**（当时无
  adb 设备）：行的渲染、点击后浏览器打开仓库主页这两条仍需真机确认。
- release 构建注入验证：`-PhyperduoVersionName=1.2 -PhyperduoVersionCode=10200` 产出的 APK
  经 `aapt2 dump badging` 确认 `versionCode='10200' versionName='1.2'`；解包后 `dexdump` 确认
  hook 侧与更新器全部类均未被 R8 剥离。
- 重启按钮：`assembleDebug` 通过，`aapt2 dump resources` 确认 `restart_title` / `restart_summary` /
  `restart_confirm` / `restart_running` / `restart_done` / `restart_failed` / `cancel` 七个串在中英
  两个 locale 都已打包（`values` 与 `values-en` 均命中）。上机（小米 14 / HyperOS 4 / Android 17 /
  KernelSU）确认：顶栏右侧 `Refresh` 图标按钮渲染正常、与状态栏图标不冲突；点开后确认弹窗按
  `OverlayDialog` 形态渲染（标题、正文、灰「取消」+ 蓝「重启」双按钮）；`am force-stop` 后冷启动
  不误触，`restarting` 初值正确。root 通路单独验证通过：`su -c 'killall com.android.systemui'`
  退出码 0，SystemUI 随后重新起来。**弹窗落在屏幕底部而非居中**——这是 Miuix 的限制不是本模块的
  bug：`DialogContentLayout` 只在窗口宽 ≥ 840dp 时才用 `Alignment.Center`，手机上恒为
  `BottomCenter`，且 `OverlayDialog` / `WindowDialog` 都没有暴露 alignment 参数。**仍待补**：
  「拒绝授权 → 弹窗留在原位并显示失败」「kill 进行中取消/确认按钮均不可点，点击外部与返回键都不
  关闭弹窗」两条交互路径。
- 颜色恢复默认确认：`assembleDebug` 通过，`aapt2 dump resources` 确认 `color_reset_dialog_summary` /
  `color_reset_confirm` 两个新串在中英两个 locale 都已打包。上机（小米 14 / Android 17 / KernelSU）
  确认颜色页「恢复默认」行渲染正常；**弹窗本身的上机截图未取到**——验证过程中设备 USB 掉线
  （`adb devices` 变空），点击「恢复默认」后的那一步没能截到图。同样的 `OverlayDialog` + 取消/确认
  双按钮形态已在重启弹窗上截到过（见上一条），但这两条确认通路各自仍需一次真机点击确认。
- 顶栏模糊：`assembleDebug` 通过；merge 后的 manifest（`processDebugMainManifest` 与
  `processDebugManifestForPackage` 两处）确认 `<uses-sdk android:minSdkVersion="29"
  android:targetSdkVersion="36" />`，即 `tools:overrideLibrary` 生效、minSdk 未被 blur 的 33 顶上去。
  上机（小米 14 / Android 17 / API 37）静置与滚动两态截图确认：顶栏后是页面内容被磨砂糊开
  （预览卡片图标透出为色块），不再是纯色底；滚动时卡片内容从标题下穿过并保持模糊。
  **未在 API 29–32 设备上验证**——该分支只能确认编译与 manifest，实际分支是否退化成不透明底依赖
  真机或模拟器。
- 门控提示（Tooltip）：语义层用 `uiautomator dump` 验证——总开关关闭时恰好 3 个
  `long-clickable="true"` 节点（充电时显示闪电 / 显示网络类型 / 电量数字居中），打开后为 0，
  与「只有确实存在未打开前置开关的行才响应长按」的预期一致。**提示气泡本身未截到图**：
  取图前设备掉线，长按路径未走通；`gateHint` 的文案拼接由代码路径保证。
- 应用内提示条：`assembleDebug` / `compileDebugKotlin --rerun-tasks` 均通过且无警告。
  **提示条本身未截到图**——它只在更新安装失败或重启完成时弹出，前者需要远端存在新 Release，
  后者会真的重启 SystemUI，都不适合为取图而触发。时长常量由反编译 Miuix 确认
  （`SnackbarDuration.Short` = 4000ms、`Long` = 10000ms，见 `SnackbarKt.toMillis`）。
- miuix-nav 接入：`assembleDebug` 通过（JDK 21 + JVM target 21）。解包 APK 后按 dex 字符串确认
  三件事都进了包：`miuix/kmp/nav/core/NavDisplayKt` 与 `NavController`、`NavBackStackKt`
  （`classes6.dex`），以及本模块的 `Route$General`、`SettingsPage`（`classes4.dex`）。
  已 `adb install -r` 装机成功（小米 14，设备在线）。
  **导航的运行时行为未做视觉验证**——按用户要求本轮不截图、不重启 SystemUI。因此以下各条
  只有编译期与打包期的证据，实际观感待补：分区切换的转场动画、系统/预测返回手势的分区回退、
  顶栏模糊在 `NavDisplay` 改为内容宿主后是否仍正确采样、
  以及标签条高亮与栈顶路由是否始终一致。（转场后来返工成零位移交叉淡入，见下一条；
  「切标签后落在哪里」后来由用户定成一律回到顶部，见第四次返工。）
- 导航转场（第一次上机后返工）：首版直接用了库预设 `NavTransitions.MiuixDefault`，用户截图
  判定「问题太大了」——整页被横推、被覆盖分区残留在左边缘。改为 `SectionTransition`
  原地交叉淡入 + `SectionEffects`（无裁角、无 scrim），`compileDebugKotlin` / `assembleDebug`
  均通过且零警告，已 `adb install -r` 装机。**这次改动同样只有编译期证据**：`adb shell input
  tap`/`keyevent` 与 `adb shell monkey -f` 在本机都被 MIUI 在 OS 层拦下，逐字报错
  `java.lang.SecurityException: Injecting input events requires the caller (or the source of the
  instrumentation, if any) to have the INJECT_EVENTS permission`（`monkey` 另外还要求 COUNT 位置
  参数，缺了报 `** Error: Count not specified`），所以「点一下标签看转场」这一步无法由我完成，
  最终观感由用户目视确认。
- 导航布局（第二次返工）：用户指出「做简单的导航动画即可,不要影响界面布局了」，即转场不得
  以改变页面结构为代价。于是把上一轮为了「只动标签页以下部分」而钉在 `NavDisplay` 之外的
  `preview` + 标签条收回每个目的地自己的 `LazyColumn`（`sectionHeader()`），页面恢复成
  「一条列表，前两行是预览卡与标签条」，与引入导航之前同形。`NavDisplay` 因此直接
  `fillMaxSize()` 并承接 `.layerBackdrop(barBackdrop)` / `.nestedScroll(...)`——原来挂在
  中间那层 `Column` 上的两个修饰符随该 `Column` 一并删除。`sectionPadding.top` 从 `12.dp`
  改为 `padding.calculateTopPadding() + 12.dp`：状态栏内边距原先由 Scaffold 施加在外层
  `Column` 上，现在必须由列表自己让开。
  验证：`compileDebugKotlin` / `assembleDebug` 通过且零警告；APK SHA256
  `EB5552BF…8CE63B`（上一版 `8A0CE291…E4E00B6`），`adb install -r` Success；冷启动
  `mCurrentFocus` 落在 `MainActivityAlias`、`pidof` 有进程、logcat 无 `FATAL EXCEPTION` /
  `No entry` / `Duplicate contentKey`。**静止态布局与改动前逐像素一致**（见上文两次 dump 的
  `bounds` 对比），这一条是本轮唯一可自证的验收点。转场观感同前，仍待用户目视。
- 导航覆盖穿透（第三次返工）：用户报告「不同标签页会覆盖上一个标签页的内容在底层」。
  根因是**交叉淡入与透明列表不相容**：`opaqueDepth = 1f` 刻意让被覆盖层继续组合并绘制
  （窗口 `-1 < d <= opaqueDepth`），而两个 `LazyColumn` 尺寸位置完全相同，被覆盖层只能被上层
  **真正画出的像素**遮住——卡片间 12dp 间隙与较短分区尾部的大片空白什么都盖不住，
  上一分区于是从那里透出。`Scaffold` 只提供一层底色，在只有一个列表的年代够用。
  修复：给 `SectionList` 的 `LazyColumn` 加 `.background(MiuixTheme.colorScheme.surface)`
  （`SettingsScreen.kt:606`），与 `Scaffold` 同色，静止态逐像素不变。
  验证：`assembleDebug` 通过且零警告；APK SHA256 `3CFDDE1E…0605`（上一版 `EB5552BF…8CE63B`），
  `adb install -r` Success；`javap -c` 确认 `BackgroundKt` / `background` / `getSurface` 已进字节码
  （排除「没重新编译」）；冷启动 `mCurrentFocus` 落在 `MainActivityAlias`、`pidof` 有进程、
  logcat 无崩溃；dump `v1.xml` 31613 B 与修复前 `ui4.xml` 完全同尺寸（General 静止态节点未变）。
  **装机版本已核对**：`adb pull` 出设备上的 `/data/app/…/base.apk`，SHA256 与本地 APK 逐字相同
  （都是 `3CFDDE1E…0605`），即设备上跑的确实是含本次修复的构建。
  注意：事后一次「最终重编」失败，报 `SettingsRepository.kt:91/133 Unresolved reference 'KEY_TYPE_SIZE'`
  与 `SettingsScreen.kt:796 Unresolved reference 'setOutTypeSize'`——那是**并发的另一个 DSH session**
  正在改 `Prefs.java` / `TrioSettings.java` / `SettingsRepository.kt` 中途留下的未完成状态，
  与本轮修复无关（我的 `.background(...)` 在 `SettingsScreen.kt:606` 仍在）。已安装的 APK 是
  那次失败构建**之前**的产物，因此设备上的版本不受影响；在此仓库与他人并发写文件时，
  任何时刻的 `assembleDebug` 结果都要先确认不是别人改到一半的状态。
  **判据是库契约而非截图**：`isVisibleAt(1f, 1f)` 为 true，所以被覆盖层在**静止态也一直组合、
  一直绘制**（不只是转场期间）——这正是「点完另一个标签后旧内容长驻底层」这种报告的原因。
  转场之外我无法自证：切标签需要注入输入，而本机 `input` 只能在当前前台窗口生效（见下一条更正），
  不能在用户可能正用手机时调用。
- **更正上文关于输入注入的结论。** 之前记录「`adb shell input tap` / `keyevent` / `monkey` 在本机
  全被 MIUI 拦下」只对**非 root** 成立：本机 `su` 可用（`/system/bin/su`，`uid=0(root) … context=u:r:ksu:s0`），
  `adb shell "su -c 'input tap X Y'"` 能成功注入。但它**无法指定目标窗口**，只会打到当时的
  前台窗口——我试过一次，当时前台是用户的 QQ（`com.tencent.mobileqq/…SplashActivity`，
  稍后变成 `…av.ui.AVActivity`），那一击落在了聊天界面上。**因此它不能用于本 app 的定向验证，
  除非先确认焦点在自己 app 上；不应在用户可能正在使用手机时调用。** 已验证过、也仍然安全的
  观察手段是：`dumpsys window | Select-String mCurrentFocus`、`pidof`、`logcat -d`、
  `uiautomator dump` + `adb pull`、`screencap -p` 到文件再 `pull`（`exec-out screencap` 会损坏 PNG）。
- 导航滚动位置（第四次返工）：用户一句「不要记录页面位置」。上一轮刚把「每个目的地各留各的偏移」
  写成了正确语义（见上文条目），这一轮按用户口径推翻：切标签一律回到列表顶部。
  改动只在 `SectionList`（`SettingsScreen.kt:572-587`）：多收一个 `isTop: Boolean`，
  加 `LaunchedEffect(isTop) { if (isTop) listState.scrollToItem(0) }`；四个 entry
  （`SettingsScreen.kt:467/475/483/497`）各自传 `isTop = nav.backStack.lastOrNull() == Route.Xxx`。
  **键必须是 `isTop` 而不是 `Unit`**：条目被覆盖时仍在组合，`LaunchedEffect(Unit)` 只在创建它的
  那次组合里跑一次，之后成为栈顶不会再触发。
  验证：`assembleDebug` BUILD SUCCESSFUL 且零警告；`javap -c` 里出现
  `LazyListStateKt.rememberLazyListState` 与 `SettingsScreenKt$SectionList$1$1.<init>:(Z…Continuation;)V`
  （那个 `Z` 就是 `isTop`，证明 lambda 确实捕获了它而不是常量）；`SectionList` 签名变为
  `(PaddingValues, boolean, Function1<LazyListScope, Unit>, Composer, int)`。
  APK 33359674 B，SHA256 `BD2C2E96E7DBB196D6E4F004C9F05E100A0471C8E52445F9DD9FF1A605DD933C`
  （上一版 `3CFDDE1E…0605`；体积变大是因为**并发的另一个 DSH session** 的「外环字号」功能
  同时进了这一版，其 `Prefs.java` / `TrioSettings.java` / `SettingsRepository.kt` 已改完并通过编译）。
  `adb install -r` Success；冷启动 `mCurrentFocus` 落在 `MainActivityAlias`、`pidof` = 2675、
  logcat 干净。**装机版本已核对**：`adb pull` 设备 base.apk 到 `.tmp\installed2.apk`，
  SHA256 与本地同为 `BD2C2E96…933C`。
  「回到顶部」这一步的实际手感仍需用户目视——切换标签要注入输入，无法自证。
- **构建环境变更**：`.tools\jdk\jdk-21.0.12.1+1`（Temurin 21.0.12.1+1，从 Adoptium 下载，195.6 MB）
  是新增的，`env.ps1` / `build.ps1` / `release.ps1` / 本文档的 `JAVA_HOME` 都已从 JDK 17 改为它。
  JDK 17 目录**保留未删**，但已不被任何脚本引用。
- 电量环缺口（本轮修复）：用户报告「电量环在中间断开后，应该是左右两半各 50%，现在的逻辑不对，
  会把中间显示为其他图标的位置也算作电量区域了」。根因是 `TrioRenderer.batteryRing` 里
  `level/100` 被当成**整条路径**的进度直接用，缺口宽度没有从 `to` 里补偿回来。
  改为按可见弧长换算（见「几何」一节）。验证走 `work\gapcheck\verify.ps1`：
  同一份 `GapProbe` 跑两遍，固定版 `exit 0`（63 档扫描 + 12 项端到端全绿），
  只把 `batteryRing` 换回旧算法的对照版 `exit 1`（42 项不符，45% 就已填满左半环、
  55/60/65% 右半环完全不起弧）——**反例成立**才说明探针在测这个 bug 而不是恒真。
  `javac -source 8 -target 8 -bootclasspath android.jar` 编 10 个 hook 源 `exit 0`；
  `gradle --offline assembleDebug` `exit 0`。**探针第一版曾因端帽误判**（见下条教训）。
- 教训（端帽）：探针第一版按手工取点分类左右段，在 45%/50% 上报了大量假失败，原因是
  `STROKE` 是 `Paint.Cap.ROUND`，每段末端向外多渗半个笔画宽（`stroke = 14` 时约 0.032 路径进度、
  7.79°）。凡是「看着渲染器明明正确、断言却失败」的像素校验，**先怀疑端帽**，改成区间模型并
  从点亮末端扣掉 `cap` 再比。这条同样适用于 `centreClearWidth` / `gapClearWidth` 之外的任何
  像素级判定。
- 电量环缺口**上机验证**（小米 14 / houji / Android 17 / HyperOS 4 / KernelSU）：`install.ps1
  -InstallOnly` Success，`adb pull` 设备 `base.apk` 与本地 SHA256 逐字相同
  （`E9FB1EDF5918048C974988DA254A675CDF798FBFDE905D81A23C3EE97AF814B0`，33359674 B），
  且 APK (18:25:42) 晚于 `TrioRenderer.java` (17:19:26)——设备上跑的确含本次修复。
  SystemUI 重启后 `HyperDuo installed, hooks=9 enabled=true`，无 `FATAL EXCEPTION`。
  两帧定量测量（`work\ondevice\measure.ps1` / `m60b.ps1`：按颜色分类像素 → 代数拟合圆心半径 →
  映射到路径进度 `t` → 输出连续区间；`TrioGeometry.B_START=148.69008689281117`、
  `B_SWEEP=242.6198262143777`）：
  - **52%**（真实电量，充电中）：绿 `0.002..0.398` 与 `0.666..0.744`，灰含 `0.445..0.999`。
    `u = 0.52 × 0.65 = 0.338`，左段应到 `min(u, m0=0.325)`、右段应从 `0.675` 起画
    `0.013` 残段——两段都出现，缺口内部无绿。
  - **60%**（`dumpsys battery set level 60` 强制值，判别性帧）：绿 `0.001..0.325`（左，止于
    `m0`）+ **右半环绿 0.675..0.783 共 96 个采样**，灰 `0.384..0.623` 等。固定版预测
    `u = 0.39` → 右段 `0.675..0.740`（含端帽到 0.772），实测吻合。**这一帧是判别性的**：
    旧算法 `to = 0.60 < m1 = 0.675` 时右半环一个像素都不画，与实测的 96 个绿采样互斥。
    目视见 `work\ondevice\lv60-fixed-8x.png`（8x）：左半环绿满、右半环从缺口右缘起一小截绿、
    其余为灰轨道。缺口内部仅 3 个采样落在 `0.357..0.643`（495 个中的抗锯齿边缘像素）。
  **强制电量必须还原**：`set level` 是全局持久状态，要写成
  `dumpsys battery set level 60; sleep 2; screencap -p /sdcard/x.png; dumpsys battery reset`
  **同一条 `adb shell`**，否则中途掉线/中断会把手机卡在假电量上（本轮真发生过一次，
  重插 USB 后 `reset` 才清掉，前后读回都是 `level: 47`）。
  拟合圆心时另有两条坑：只用绿像素拟合会退化（绿只有两段短弧），而把「中性灰」阈值放宽到
  `mx >= 90` 会把背景 `#656563` 一起算成轨道（13144 px，圆心被拖到背景里）。正确做法是先按
  目视取粗略圆心做环形预筛（`18 < d < 34`）再拟合，且背景灰度必须排除。
- **全量样式总览图**（对照 macOS 版 `Status Trio` 总览图出的图）：`work\overview\run.ps1`
  `exit 0`，`work\overview\out\overview.png`，`1788×2248`，3 组共 **25 格**，
  `renderer = 92f6733e0b52a58409b17d26270b6a6fc0fa3152978c2cd62d2c5262bb9bb420`
  （即出图时 `TrioRenderer.java` 的 SHA256，用来确认图对应哪份代码）。放大目视核对
  `crop-battery.png`(2x) / `crop-wifi.png`(2x) / `crop-mobile.png`(2x) / `crop-footer.png`(2x) 逐格正确；
  Wi-Fi 组末格「居中后充电」现在画的是**缺口里的小闪电 + 圆心数字**，与改动前的「闪电居中」相反。
  **页脚长这样是有守卫的**：`footerLine` 用 `FontMetrics.stringWidth` 量宽并 `throw`，宁可出图失败
  也不让文字被裁掉——本页新增的居中说明行第一次就超宽 76px 被它拦下，改为更短的句子才通过。
  **清点结论**：参考图的「蓝牙音频」4 格与「音量」7 格在本模块无任何实现
  （`volume|bluetooth|audio|headset|earbud` 零匹配），「已连接电源，未充电」也没有对应状态
  （渲染器只有充电 / 未充电两态），这三类都不应画进图里 —— 详见「测试」一节的样式清单。
  原图曾另有一组「外观与几何」12 格，后按要求删除：那是外观旋钮而非图标状态。
- 教训（出图也会溢出）：总览图页脚前两版都**超出右边界被静默裁掉**，肉眼在图缩略图上根本看不出。
  改成 `footerLine(...)` 先 `getFontMetrics().stringWidth` 量宽、超宽直接 `throw`，
  立刻报出 `footer line overflows the page by 49px`。凡是定宽排版出图，都要让"画不下"变成
  异常而不是裁剪。
- 教训（PowerShell 5.1 与 BOM）：工作树里的 `release.ps1` 丢过 UTF-8 BOM，PS 5.1 于是按 GBK
  解码中文注释，多字节序列吞掉后面的引号，报出 8 个**指向完全正确行**的 parse error。凡在
  PowerShell 5.1 下跑、且含中文的 `.ps1`，都要确认首字节是 `239,187,191`。已逐个按「解码后
  是否出现乱码」判定：`release.ps1`（517 个汉字，**必须**带 BOM）、`install.ps1`（`install.ps1:66`
  的 `Duo 三合一状态栏` 会被解成 `Duo 涓夊悎涓€鐘舵€佹爮`，已补 BOM，len 3140→3143）；
  `env.ps1` / `build.ps1` 一个汉字都没有，无 BOM 也无所谓，保持原样。
  BOM 有无只看 **parse error 是不够的**——`install.ps1` 在无 BOM 时 parse-errors 仍然是 0，
  错的是运行时打印出来的字。
- **1.1 发布**（`release.ps1 -Version 1.1 -NotesFile work\notes-1.1.md`，`exit 0`）：tag `v1.1`
  → `03b36b5f5a0f95709255dbf85705da724da4cfb3`（与 `main` 同一提交，即构建的确实是已提交的代码）；
  Release `HyperDuo 1.1` 非 draft / 非 prerelease，body 808 字符；asset `HyperDuo-1.1.apk`
  3038076 B。**发布产物按字节复核**：从 `api.github.com/repos/…/releases/assets/605602727`
  下载回来 SHA256 `9E024FA1A8988947DA9D6E694AC83B9A737FE7C2EC85C32CDB406C8414FBA6DC`
  与本地 `dist\HyperDuo-1.1.apk` 逐字相同。签名 `CN=HyperDuo` 与设备上原装 APK 同一证书
  （SHA-256 `b4e3a12d…8c41f`），所以能 `-r` 覆盖安装而不冲突。
- **1.1 release 包上机验证**（这一步不可省：R8 会剥离反射用到的类）。`adb install -r` Success，
  `versionCode=10100` / `versionName=1.1`、`pkgFlags` 里 **没有 `DEBUGGABLE`**（release 构建）。
  类名在 dex 里是**斜杠描述符**（`Lio/github/yixing233/hyperduo/TrioHooks;`），用点号搜会全部假阴性 ——
  `TrioHooks` / `TrioRenderer` / `TrioConfig` / `Prefs` / `TrioSettings` / `HyperDuoModule` /
  `ui/RestartController` 全部在 `classes.dex` 里，未被剥离。SystemUI 重启后
  `HyperDuo installed, hooks=9 enabled=true`、`reload receiver registered`，无 `FATAL EXCEPTION`。
  截图 `work\ondevice\release-1.1-statusbar.png` 目视确认：三合一图标正常（绿色充电弧 + `49` +
  闪电 + 底部信号点），环外网络类型 `5G` 也画出来了。日志中对应
  `out type: "5G" size=45.0 label=60x60 at 341,14 anchor=407..491 container=491x88`。
- **1.3 发布**（`release.ps1 -Version 1.3 -NotesFile work\notes-1.3.md`，`exit 0`，构建耗时
  1m19s）：tag `v1.3` → `e97f73d6347f842a21511db03f37cde299732c48`（与 `HEAD`/`main`
  同一提交——`release.ps1` 构建工作树但给 `HEAD` 打 tag，所以**必须先提交干净再跑**）；
  Release `HyperDuo 1.3` 非 draft / 非 prerelease（`draft=False` / `prerelease=False`），
  body 496 字符，asset `HyperDuo-1.3.apk` 3038276 B。**发布产物按字节复核**：从
  `https://github.com/yixing233/HyperDuo/releases/download/v1.3/HyperDuo-1.3.apk` 下载回来
  SHA256 `2AAD1D6AA5920FBA4EA3F1E44B1349364752C81A9BA63675DA1D365EB2916EA1`
  与本地 `dist\HyperDuo-1.3.apk` 逐字相同（R8 产物体积不固定，每次构建字节都不同，
  所以「下载回来对哈希」这一步不能省）。`git ls-remote --tags` 确认远端有 `refs/tags/v1.3`。
  **`v1.2` 从未打过 tag 也从未发布**——`dist\HyperDuo-1.2.apk` 只是本地产物（它含改名后的串
  「电量数字居中」，但还不含居中的优先级修复）。所以 1.1 → 1.3 的用户一次性拿到改名 + 修复，
  1.3 的 release notes 只写这两件事，不提 1.2。
- **1.3 release 包上机验证**（这一步不可省：R8 会剥离反射用到的类，而 debug 包不会暴露这个
  风险）。三个构建类型共用同一个签名配置（`app\build.gradle.kts:26-49`：`debug` 与 `release`
  都取 `signingConfigs.getByName("hyperduo")`，keystore `.tools/debug.keystore`、alias
  `hyperduo`），所以**正式包能直接 `-r` 覆盖 debug 包**——但两者证书必须一致，实测
  `keytool` 的 `SHA256: B4:E3:A1:2D:…:C4:1F` 与 `apksigner verify --print-certs` 打印的
  `b4e3a12d03957e441c3ccf2e4e2be55de0db35fc5eb9aa8ac49a0a9d7298c41f` 相同（也就是设备上
  原装 APK 的同一证书）。
  `adb install -r dist\HyperDuo-1.3.apk` Success；`versionCode=10300` / `versionName=1.3`。
  **按字节证明装进去的就是发布包**：`pm path` 取 codePath 后 `adb pull` 出 `base.apk`，
  SHA256 `2AAD1D6AA5920FBA4EA3F1E44B1349364752C81A9BA63675DA1D365EB2916EA1`（3038276 B）
  与 `dist\HyperDuo-1.3.apk` 以及从 GitHub 下载回来的那份**三者逐字相同**。SystemUI 重启后
  `HyperDuo installed, hooks=9 enabled=true`、`reload receiver registered`，无 `FATAL EXCEPTION`
  ——反射入口在 R8 下存活。截图 `work\ondevice\release-1.3-statusbar.png`（裁切放大
  `release-1.3-icon-4x.png`）目视确认居中优先级修复在正式包里生效：**缺口里是小闪电、
  圆心是 `100`**（若拿 debug 包验证，就等于没验证 R8 这一层）。
- **双卡信号（`dual_sim_signal`）离线验证**：`work\overview\run.ps1` `exit 0`，
  `work\overview\out\overview.png` `1788×2248`、3 组 **26 格**（移动信号组 8 → 9 格），
  `renderer = 38011f32635075984f70fca4c17ab519a71a70f4329dd12e3e543d6eb8b8892f`；
  新增格「双卡信号（4 / 2 格）」目视确认：两排点分别落在圆环的上下两个缺口里、两排都用
  `DUAL_DOT_R = 7.0f`、数字 `79` 居中，与参考图一致。`work\preview\out\04-dual-sim.png`
  （`slotLevels = {4, 2}`、无 Wi-Fi、电量 79）单张复核同形。双卡格**故意取两卡电平不同**
  （4 / 2）：若哪天它悄悄退化成单排，出图会「看起来就不对」，而不是看起来像个合理的单卡读数。
  上下排与 slot 的对应关系**不靠肉眼**（288×288 缩略图上看不出每排几点，容易把两排读反）：
  `work\dotprobe\DotProbe.java` 反射取 `TrioGeometry.DOTS_DUAL` 与 `TrioRenderer.inkScale`，
  按渲染器同一套平移/缩放算出 8 个圆心，再在每点邻域统计亮点。实测（`inkScale = 2.15319`、
  `tx = ty = 15`）：`DOTS_DUAL[0..3]`（设计 y < `B_CY`，上排 = slot 0 = 卡一）**4 点全亮**；
  `DOTS_DUAL[4..7]`（下排 = slot 1 = 卡二）**前 2 点亮、后 2 点灭**（第 7 点 `maxv = 78`，
  即只有 `trackAlpha` 的灰）。与 `slotLevels = {4, 2}` 逐点吻合，确认上排=卡一、下排=卡二。
  测量时要注意相邻点窗口重叠：灭点若紧挨亮点，窗口里会漏进几个亮像素（第 6 点测得 31 px、
  质心偏 29.5 px 靠左），判断某点是亮是灭要看该点圆心附近的 `maxv`，不能只看窗口内亮点计数。
  `:app:assembleDebug` `BUILD SUCCESSFUL`（`37 actionable tasks: 13 executed`）。
  `work\gapcheck\verify.ps1` 与 `work\slotcheck\verify.ps1` 均 `exit 0`：两次`drawInto` 加参、
  `drawBattery` 改收 `TrioAppearance` 之后，两条工装都已改到能同时链上新旧两版渲染器（见「测试」
  一节的三处坑），固定版/新版必须 `exit 0`、旧算法/旧版必须 `exit 1` 各自成立。
- **双卡开关判定规则离线验证（第二轮补）**：`work\dualsimcheck\verify.ps1` `exit 0` —— 固定版
  `exit 0`（21 条断言全 `[ok]`）、把两处 `dualSimRows` 调用点换回 `bolt` 的反例版 `exit 1`
  （恰好那 3 条「充电中且 `show_value=false`」断言不符）。这条工装是为了补上一个**此前没被测过**
  的洞：`overview` 只证明双卡格画得出来，`gapcheck`/`slotcheck` 测的是电量弧与居中优先级，**没有
  任何一条覆盖「什么时候才该画两排」**。而补测的过程本身查出一个真缺陷：`dualSimRows` 第 2 参在
  javadoc 里是「设备是否插着充电器」，两个调用点（`Ring`/`Rect`）却都传了 `bolt`，而
  `bolt = charging && drawsBolt()`、`drawsBolt() = showBolt && showValue`，于是**关掉「显示电量
  数字」后插着充电器也会画出两排**，既违反 javadoc 也违反用户原话的「没充电」。修复即把两处改传
  `charging`（`TrioAppearance.java`，附注释说明「是插头不是闪电」），`TrioSettings.dualSim` 的
  javadoc 与 `docs` 里「没有闪电（`charging` 为假）」这句同样含混的措辞也一并订正为「没插充电器」。
  顺带记一条采样坑：`rows()` 最初取窗口内**最大** alpha，`{2,4}` 时最外侧上排点被圆环弧（相距
  约 25px）带亮而误判；改读圆心**核心区取最小** alpha 后，亮点 255 与轨道点 `trackAlpha=56`
  判然可分（实测 `core alpha 255 255 255 255 / 255 255 56 56`）。
- **双卡上排拥挤的修复与离线验证（第四轮补）**：症状是用户指出「上排显示不完整、空间太少、应该
  上下对称」——上排外侧两点与圆环 12 点钟缺口的两个**圆帽**糊在一起，参考图里那种点与弧分明的样子
  出不来。根因是量化的：上排是 `DOTS` 绕环心 `B_CY` 的镜像，外侧点几乎正落在弧中线 `B_R = 51.5` 上，
  外点圆心到弧端点圆心只有约 **10.4** 个设计单位，而弧用 `STROKE.setStrokeCap(ROUND)` 收尾、帽半径
  `ringStroke / 2 = 7`，加点半径 `DUAL_DOT_R = 7` 共 **14** ⇒ 必然重叠；下排落在弧**没盖住**的那段
  自然开口里（`360 - B_SWEEP = 117.4°`），所以一直舒展。而两排共用的
  `GAP_START_IDLE`/`GAP_END_IDLE` 只张 `0.35 * B_SWEEP = 84.9°`，比底部开口**窄 32.5°** —— 这正是
  「不对称」的可量化来源。修法：新增 `GAP_START_DUAL = 1f - 180f / B_SWEEP ≈ 0.2581`、
  `GAP_END_DUAL = 180f / B_SWEEP ≈ 0.7419`，在 `TrioRenderer.drawRingLayout` 里让 `r.dual` 优先取
  这对值，使 12 点钟缺口与底部开口**等宽**，余量回到约 10 单位。顺带修掉一处叠加缺陷：`valueInCentre`
  原先只看 `centreValue`/`(!wifi && !type)`，双排的上排占了缺口而 `valueInGap` 会把百分比数字送进
  同一位置造成叠画，现在 `hasValue && (a.centreValue || dual || (!wifi && !type))` 让数字回到环心。
  验证：`work\dualsimcheck\verify.ps1` `exit 0`，且升级为**三构建**对照——`fixed` `exit 0`（25 条断言
  全 `[ok]`）、`bolt` 版 `exit 1`（3 条不符，仍是上一轮那条充电语义）、**新增 `narrow` 版**（把两个
  `GAP_*_DUAL` 退回 idle 值，即用户投诉时的状态）`exit 1`（2 条不符）。新增的 `separation()` 组用
  **连通域面积**把「挤不挤」变成断言：从每个点的中心像素做迭代式 4-连通 flood fill，要求
  `area / (π·DUAL_DOT_R²·scale²) < 2.5`，实测 `fixed` 版 8 个点全是 **0.97..0.99** 个点面积，而
  `narrow` 版上排外侧两点涨到 **8.22 / 5.58**（与弧合并成一体），下排 4 点作为对照始终 0.97..0.99。
  同样一条断言在 `bolt` 版下**不**失败，说明它专盯几何、不与其他断言重复。
  可视化复核：`work\dualsimcheck\out\dualsim.png`（2296×650，sha256
  `824BFD162B5630D0EDCAA26A33585C41CFC3976B5A8CFAC0B9DB5D5006A418A4`）四格中第 ② 格已可见上排
  四点完全脱离环的两个圆帽、与下排对称；关闭时只有下排、充电时只有一排且闪电在缺口、单卡时只有一排。
  **改前/改后对照图**由 `work\dualsimcheck\before-after.ps1` 生成：它把同一份绘图代码分别与工作树的
  `TrioGeometry`（双排缺口 = 底部开口宽度）和「两个 `GAP_*_DUAL` 退回 idle 值」的那份各编一遍，再由
  `Panels.java` 拼成上下两栏，产出 `work\dualsimcheck\out\top-before-after.png`（1310×1592，12 点钟
  区域放大 3 倍，由 `TopZoom.java` 裁切）与 `...\out\dualsim-before-after.png`（2316×1422，四态总览，
  由 `DualSimShot.java` 出图）。**没有任何单次编译能同时画出两个几何**，所以必须两次编译各出一张再合并。
  放大图里 `BEFORE` 栏上排外侧两点与弧的圆帽连成一体（`separation()` 实测 8.22 / 5.58 个点面积），
  `AFTER` 栏四点是四个独立正圆、与弧留出明显空隙（0.97..0.99）。
  同轮其余门禁复跑全绿：`gapcheck` `exit 0`（反例 42 条不符）、`slotcheck` `exit 0`（反例 6 条不符）、
  `simcheck` `exit 0`（反例 23 条不符）、`overview` `exit 0`（1788×2248、三组 10/7/9 格、`total
  cells = 26`、`renderer = eb3d8cbbd28e8a662df2759911e55620df672c47a09944c6a259178d19883817`），
  `:app:assembleDebug` `BUILD SUCCESSFUL`（`app-debug.apk` 33426038 B、sha256
  `C7F58E09590C6A875B8226C0A99D68254AAD25C6F3664EA879A9740D1054085C`）；dex 里查得
  `dual_sim_signal` / `dual_sim_title` / `dual_sim_summary` / `DOTS_DUAL` / `DUAL_DOT_R` /
  `dualSimRows` / `GAP_START_DUAL` / `slotLevels` / `pollSimsNow` / `attachContext` / `getSimSlotIndex` /
  `getDefaultDataSubscriptionId` / `appearance` / `stackedOut` 全部命中（6 个 dex，`com/hyperduo`
  零命中），确认本轮几何改动与新开关都进了交付物。这组哈希是**最后一次全绿时**的值，
  同轮之后并发写者又动过 `TrioGeometry` / `TrioHooks` / `TrioRenderer` / `TrioState`，重跑会变；
  **后被第七轮那次构建修复取代**（见「第七轮补」条：`app-debug.apk` 33434570 B、`AAEA3228…`）。
  另记一条工装迁移
  事实：本轮之前 app 整包改名为 `io.github.yixing233.hyperduo`，`work\` 下 25 个工装文件随之迁移
  （`work\migrate-rename.ps1`），其中 `work\slotcheck\verify.ps1` 需要把「工作树路径」与「`git show`
  用的旧提交路径」拆成两个变量（旧提交树里仍是旧路径），并把取出的旧源**重写包声明**后才编得过。
  上机验证当时仍待补（设备未上线），第六轮补上了双排那条。
- **双卡订阅号↔槽位映射与单排兜底离线验证（第三轮补）**：`work\simcheck\verify.ps1` `exit 0` —— 固定版
  `exit 0`（42 条断言全 `[ok]`）、把 `final int slot = info.getSimSlotIndex();` 换成
  `info.getSubscriptionId()` 的反例版 `exit 1`（实测 23 条不符）。补它的原因：双卡信号里画得对不对
  已有三条工装，而**哪一排属于哪张卡**、**只画一排时画哪张卡**只在 `TrioState.sampleSims` 里决定，
  且只在真机 SystemUI 进程会跑到；参考机上 `subId` 恰好等于槽位（subId 1 在槽 0、subId 2 在槽 1），
  所以「把订阅号当槽位」这种写法在那里**看起来是对的**，本工装因此故意用不相等的 `subId 9 → 槽 0`、
  `subId 5 → 槽 1` 来暴露它。实现手法：真实 `TrioState.java` 只依赖 `Refl`（纯 JDK）与 `TrioConfig`，
  于是自带一套 `android.telephony.*` 桌面垫片就能跑，不需要 Xposed、也不需要设备。另记两条坑：
  一是 `gating()` 对每次 `refresh()` 新建 host 时，拦下采样的其实是 2 秒限流而不是开关，断言会
  「因为错误的原因」通过，必须复用同一 host 并在两次 `refresh()` 之间步进 `SystemClock.now`；
  二是编译源**不能**带 `work\preview\src` 里的 `TrioState.java` / `TrioConfig.java`（同名桌面替身撞类），
  只能借它的 `SharedPreferences.java` 与 `Bundle.java`。
  **上机验证（当时）仍待补**：设备 `<DEVICE_LAN_IP>:<ADB_PORT>` 已下线（`adb connect` 失败、
  `Test-Connection -Quiet` 为 `False`、`adb devices` 只剩一条 `offline`）；第六轮补到了
  「真双卡手机在无 Wi-Fi 未充电时画出两排点」这条。但**「只插一张卡时退化成单排、
  且那排是当前上网卡」至今仍只有离线证据**（`work\simcheck\verify.ps1` 的 counterexample
  用 subId 9 → 槽 0、subId 5 → 槽 1 的错位映射来暴露「把订阅号当槽位」的写法），
  真机没拔过卡，这条不要当成已验收。
- **改名后的真机部分验收（第五轮补，双排本身未拍到、由第六轮补上）**：设备经无线 adb 短暂上线（`<DEVICE_LAN_IP>:<ADB_PORT>`，
  houji / model `23127PN0CC` / Android 17 / SDK 37 / `wm size` 1200x2670 / density 480），
  本轮取到三条真机事实：
  1. **改名后的包已自动进入 LSPosed 且已加载**。`modules_config.db` 里新包
     `enabled = 1`、`scope` 含 `com.android.systemui`；旧包 `com.hyperduo.trio` 已从设备卸载
     （`pm list packages -f hyperduo` 只剩新包）。**不需要手工往作用域里拖**：LSPosed 按
     `applicationId` 建行，改名后第一次启动模块就自己登记了。截图
     `work\device\out\bar-wifi-on.png`（1200x2670）右侧可见 `13.4 KB/s` + `5G` + 三合一轮
     （环内 `85`、环下四点）——**证明改名 + `TrioAppearance` 重构后的代码在真机 SystemUI 里
     真的跑了**，这比任何离线渲染都强。
  2. **真机是双卡，且未充电**：`dumpsys isub` 里 `id=1 … simSlotIndex=0 … 中国移动`、
     `id=2 … simSlotIndex=1 … 中国广电`（`getAvailableSubscriptionInfoList: [1, 2]`，
     恰好 `subId = slot + 1`，这是参考机的巧合，不是契约）；`dumpsys battery` 三项
     `powered` 全 `false`、`level: 85`、`status: 3`。⇒ 双排的两个前提（未充电、双卡）已满足。
  3. **卡在「无 Wi-Fi」这一步**：`dumpsys wifi` 为 `Wi-Fi is enabled`，而无线调试本身走
     Wi-Fi，`adb shell "svc wifi disable"` 一执行就 `error: closed` / `device offline`，
     之后十余次 `adb connect` 全部失败。**教训：这台设备上不能用 `svc wifi disable` 制造
     「无 Wi-Fi」状态，那等于自断控制通道。**
  因此 `work\device\acceptance.ps1` 改用**语义等价的替代触发**：发模块自己的 reload 广播把
  `show_wifi` 置假（`am broadcast -a io.github.yixing233.hyperduo.action.RELOAD -p
  com.android.systemui --ez enabled true --ez show_wifi false --ez dual_sim_signal true`）。
  这之所以合法，是因为判定规则读的是 `wifiInk`（屏幕上有没有 Wi-Fi 墨迹），不是物理 Wi-Fi
  开关；而 `TrioConfig.onReloadBroadcast` 对不含 `KEY_ENABLED` 的 extras 直接忽略、对缺失的键
  一律沿用**上一次快照**（`TrioSettings.fromBundle(bundle, base)`），所以只带这三个键就只改这
  三个键，其余设置不会被清掉。脚本另含：设备事实、包与旧包检查、LSPosed 配置库判读
  （Java 序列化 boolean 只看 `data` 列**最后一个字节**）、充电/双卡读数、SystemUI 日志里新包
  是否出现、以及关机屏截图。
  **仍未完成的验收，以及为什么主机侧帮不上忙**：截图里还没看到两排点。后来设备整个从局域网
  消失——`arp -a` 里没有该设备的 MAC，`ping -S <HOST_LAN_IP> <DEVICE_LAN_IP>` 得到
  `Destination host unreachable`（同法 ping 网关 `<GATEWAY_IP>` 正常，`time<1ms TTL=64`），
  异步扫 `<LAN_SUBNET>` 只有 `<GATEWAY_IP>` 与 `<HOST_LAN_IP>` 活着。**恢复只能靠用户在手机上
  手动重开 Wi-Fi 与无线调试。** 另记一条主机侧陷阱：本机 FlClash 是 TUN 模式
  （`198.18.0.1/30`，默认路由 `0.0.0.0/0 → 198.18.0.2 metric 0`），
  `Find-NetRoute -RemoteIPAddress <DEVICE_LAN_IP>` 判给 `FlClash` 而不是`以太网`，于是
  **任何 TCP 连接都会「成功」**——早先那次「1024..65535 几乎全开」的端口扫描是假象，
  不能用 `TcpClient.ConnectAsync` 判断端口是否开放，要看 `Find-NetRoute` 或 `adb devices`。
  当前 shell 非管理员，加不了静态路由绕开它。
- **真机双排验收完成（第六轮补）**：设备重开 Wi-Fi 后回到局域网，**端口又变了**
  （用户给的 `<DEVICE_LAN_IP>:<ADB_PORT>` 变 `10061` 拒绝）。这次先跑 `adb mdns services`，
  直接问出真正在听的端口 `_adb-tls-connect._tcp <DEVICE_LAN_IP>:<ADB_PORT>`，连上即 `device`。
  **教训：无线调试端口每次重开都变，别再猜或扫端口，直接 `adb mdns services`。**
  设备事实与上轮一致（houji / `23127PN0CC` / release 17 / SDK 37 / `wm size` 1200x2670 /
  density 480）；模块本机 prefs 里 `dual_sim_signal=true`、`mobile_type_mode=2`、
  `swap_wifi_value=true`；`dumpsys battery` 三项 `powered` 全 `false`、`level: 75`；
  `dumpsys isub` 仍是 slot 0 = 中国移动（subId 1）/ slot 1 = 中国广电（subId 2），
  `defaultDataSubId=2`、`activeDataSubId=2`。
  验收手法与结果：先 `screencap` 拍下 Wi-Fi 在屏的基线（`work\device\out\bar-wifi-on.png`，
  裁剪 `tall-wifi-on.png`：环内 `75`、上排位置是 Wi-Fi 弧、**只有一排点**），再发那条 reload 广播
  把 `show_wifi` 置假，同一位置再拍（`bar-dual.png` / `tall-dual.png`）——**真的出现两排点，
  上下对称**，Wi-Fi 弧消失、百分比仍居中 `74`。随后再发一条 `show_wifi true` 把设置还原
  （`bar-restored.png` / `tall-restored.png`：Wi-Fi 弧回来了、又只剩一排），确认这条触发路径
  可逆、没有留下副作用。三态拼图 `work\device\out\device-dual.png`（1020x2278，
  sha256 `53ABB0A709E4E21BFC7B8E781C9BF82A8CC142ECF15977A3CC0920FD8DD8ABB5`，由
  `Panels` 生成）。
  **对称性是量出来的、不是看出来的**：对 `tall-dual.png`（`Crop` 放大 5 倍，1100/5 比例）跑
  `work\refimg\Analyze.java`，六颗点的质心给出环心 `y = 365.18`，于是
  上排三点在上方 `108.19 / 126.94 / 128.19`，对应的下排三点在下方
  `109.12 / 126.82 / 128.31` ⇒ **三对偏差 `0.93 / -0.12 / 0.12` 裁剪像素，
  即最大 `0.19` 设备像素**（内两对在 `0.03` 设备像素内）。这正是 m02630 要的「上下对称」，
  也是 `GAP_*_DUAL` 那两行几何改动的真机证据。
  同轮 `Analyze` 还确认六颗点的外接框都是 `35x35`（`area=900/925`），即**同样大的正圆**：
  上排没有被环的圆帽吃掉，也没和弧粘成一个 blob（离线侧同一结论的数字是分离度 `0.97..0.99`）。
  真机取证的工装细节：`Crop` 与 `Panels` 都在
  `work\dualsimcheck\after-classes`（`io.github.yixing233.hyperduo.Crop/Panels`），
  `java -cp <那个目录> io.github.yixing233.hyperduo.Panels …`；`Panels` 不传全限定名会
  `ClassNotFoundException: Panels`。`Analyze` 在 `work\refimg\classes`（**默认包**，
  `java -cp … Analyze`）。`screencap` 写进 `/sdcard` 再 `adb pull` 仍比 `exec-out` 稳。
- **未转义撇号把构建弄坏、aapt2 才是可信报错源（第七轮补）**：这一轮 `:app:assembleDebug`
  突然失败在**并发写者新加的英文串**上（不是双卡那部分改动）：
  `app\src\main\res\values-en\strings.xml:50:4: Failed to flatten XML for resource
  'stacked_signal_summary' with error: Invalid unicode escape sequence in string` +
  `…:50:4: string/stacked_signal_summary does not contain a valid string resource.`
  + `> Task :app:mergeDebugResources FAILED`。**这条信息严重误导**：按字节扫两个
  `strings.xml`，`0x5C`（反斜杠）计数为 **0**，严格 UTF-8 校验通过、无 BOM；合并产物
  `app\build\intermediates\incremental\debug\mergeDebugResources\merged.dir\values-en\values-en.xml`
  也干净；**清掉 `…\incremental\debug\mergeDebugResources` 再建，报错一字不变**（所以不是增量缓存陈旧）。
  **定位手法（值得复用）**：绕过 Gradle，用 aapt2 直接编一份资源副本 ——
  `.tools\sdk\build-tools\37.0.0\aapt2.exe compile --dir C:\code\HyperDuo\work\resprobe -o out.zip`
  （目录里放 `values\strings.xml` 与 `values-en\strings.xml` 两份），aapt2 才说出真话：
  `strings.xml:52: error: unescaped apostrophe in string`，直指 `… the system's own mobile signal
  icon is kept.`。⇒ **aapt2 报「Invalid unicode escape sequence in string」时，多半其实是
  「字符串里有未转义的撇号」（`'` 必须写成 `\'`，或把整串用 `"…"` 包起来）；Gradle 那条信息
  不可信，遇到就先 aapt2 直编定位，别去清缓存。** 中文版 `values\strings.xml:52` 无此问题
  （撇号本来就不出现在中文串里），所以只改了英文那份。修复后 aapt2 `exit 0`，
  `:app:assembleDebug` `BUILD SUCCESSFUL in 17s`，交付物刷新为
  `app-debug.apk` **33434570 B**、sha256
  **`AAEA3228AE3A9CAD848771ECFE5D0C580D28257F3817D41368DF31A75ED23736`**；
  解包仍是 **6 个 dex**，`dual_sim_signal` / `dual_sim_title` / `dual_sim_summary` / `DOTS_DUAL` /
  `DUAL_DOT_R` / `dualSimRows` / `GAP_START_DUAL` / `slotLevels` / `pollSimsNow` / `attachContext` /
  `getSimSlotIndex` / `getDefaultDataSubscriptionId` **12 个串全命中**、`com/hyperduo` 零命中。


- **包名更换为 `io.github.yixing233.hyperduo`（为进 LSPosed 仓库）**：起因是 LSPosed 的
  反域名归属校验——`com.hyperduo.*` 要求根域 `hyperduo.com` 配一条
  `lsposed-modules-repo-verification=<github 用户名>` 的 TXT 记录，而该域名虽在 Cloudflare
  之下（RDAP：2004-02-04 注册、2027-02-04 到期、registrar Cloudflare），**TXT 记录为空**，
  所以那条路走不通；提交 README 另有免域名通道——用 `io.github.{username}` 前缀即可，
  不需要域名也不需要 TXT。于是 `com.hyperduo.trio` → `io.github.yixing233.hyperduo`。
  改动面：17 个源文件从 `app\src\main\java\com\hyperduo\trio\` 迁到
  `app\src\main\java\io\github\yixing233\hyperduo\`（16 个走 `git mv` 保留历史，1 个未跟踪文件直接移动），
  包声明与 `import` 同步改写；`app\build.gradle.kts` 的 `namespace` / `applicationId`、
  `app\proguard-rules.pro` 的两条 `-keep`、`META-INF\xposed\java_init.list` 的入口类、
  `install.ps1` 与 `release.ps1` 的 `$Package` 一并更新。改写用 `UTF8Encoding($false)` +
  `ReadAllText/WriteAllText`，**原有 LF/CRLF 与无 BOM 全部保持**（仅 `TrioConfig.java` 原本是 CRLF）。
  有意**不动**的东西：`Prefs.NAME`（远程 preferences 组名，也是设置界面文件名，
  改它等于丢用户设置）、签名配置名 / `keyAlias` / 以 `hyperduo` 开头的 Gradle property 名 /
  keystore 与证书。`Prefs.ACTION_RELOAD` **跟着包名一起改了**（`…action.RELOAD`）——
  它不是对外契约，收发的两端（`SettingsRepository.kt:177` 发送、`TrioConfig.java:157` 注册）
  都用同一个常量，只要同一版本内自洽即可。`AndroidManifest.xml` 一个字节都没改——
  组件名全是相对名（`.HyperDuoApp`、`.ui.MainActivity`、`.ui.MainActivityAlias`）自动跟随 `namespace`，
  FileProvider authority 是 `${applicationId}.fileprovider` 也自动跟随。
  离线验证：`:app:assembleDebug` `BUILD SUCCESSFUL in 37s`（`37 actionable tasks: 19 executed`，
  日志里出现 `compileDebugKotlin` / `compileDebugJavaWithJavac` / `dexBuilderDebug`，
  不是「全部 up-to-date」的假绿）；`aapt2 dump badging` → `package: name='io.github.yixing233.hyperduo'`；
  `aapt2 dump xmltree` → `application` / `activity` / `activity-alias` / FileProvider authority
  全部落到新包名，`de.robv.android.xposed.category.MODULE_SETTINGS` 仍在；APK 内
  `META-INF/xposed/java_init.list` 的内容是 `io.github.yixing233.hyperduo.HyperDuoModule`；
  按**斜杠描述符**扫 6 个 dex，`io/github/yixing233/hyperduo/…` 六个关键类全部命中，
  `com/hyperduo/trio` 与 `com/hyperduo/yixing` 零命中。
  **用户影响（必须写进 release notes）**：`applicationId` 变了就是另一个 app，
  旧的 `com.hyperduo.trio` **无法原地覆盖升级**，设置与 LSPosed 作用域都不迁移，需要先卸载再装。
  本文件上方的 v1.1 / v1.3 发布记录（字节核对、证书指纹、旧包名）是**历史事实，按当时原样保留**，
  未随本次改名回填。
- **卡信号环外样式与两个开关（第八轮补）**：新增 `signal_mode`（环内/环外）、`stacked_signal`、
  `data_sim_only` 三个键，环外读数按 350×350 参考图自绘（四条递增圆头柱 + 下方一排圆点，见
  「信号：环内与环外堆叠」一节）。本轮的判据收在 `TrioAppearance` 的 `signalDots()` /
  `stackedOut()` / `foldsMobile()` 三个谓词上，`TrioRenderer.drawOutSignal` 与
  `TrioHooks.OutSignalView` 各只有一处调用它们。
  离线验证：新建的 `work\outringcheck\verify.ps1` `exit 0`（`fixed` 通过；`refold` 与 `inert`
  两个反例构建分别 **3 条 / 14 条**不符而 `exit 1`）；既有四条 `gapcheck` / `slotcheck` /
  `dualsimcheck` / `simcheck` 复跑全部 `exit 0`；`:app:assembleDebug`（`--offline`）
  `BUILD SUCCESSFUL`，`compileDebugKotlin` 与 `compileDebugJavaWithJavac` 都真跑过，
  不是「全部 up-to-date」的假绿。
  **本轮由探针与出图抓出的三个缺陷**：
  1. `stackedOut()` 漏了 `mobile`（「显示移动信号点」关掉后环外堆叠仍会自绘，把用户关掉的
     那一项复活）。探针的断言原文是 `mobile meter off, out of ring stacked: no folding and no
     reading`。补上 `mobile` 后 `show_mobile` 关即「两个位置都交还系统」。
  2. 探针自身在**第一列**取样验底纹，而三点读数下第一列是亮的，断言恒假，看着像产品缺陷；
     改到第四列并补一条「未点亮的点仍在屏幕上」才通过（见「测试」一节的同名段落）。
  3. `STACK_DOT_GAP` 原是 10，实为 **9**（参考图无抗锯齿：柱底最后一行墨 237 即下边缘 238，
     点行首行墨 247，空带 `238..246` = 9）。这个错 `OutRingProbe` 抓不到 —— 它两边都引用同一批
     常量，自洽即通过；是 `OutSignalShot` 出图 + `compare.py` 拿**同一套量法分别量参考图与渲染
     结果**才暴露的（`STACK_INK_H` 210 → 209）。教训：证明「源码自洽」的工装不能证明「符合外部
     基准」，凡有外部参考目标的几何，都要有一件把目标本身也量一遍的工装。
- **双卡两排「各少一格」：读 MIUI 电平而不是 AOSP 电平（第九轮补）**：用户报「单卡时底部满格，
  双卡时上下两排都少一格」。根因在 `TrioState.levelOf` 原来取 `SignalStrength.getLevel()`
  （AOSP 口径），而 MIUI 状态栏用的是 `getMiuiLevel()`。实机 `dumpsys telephony.registry` 直接给出
  反证：Xiaomi 14 / 5G NR 下两张卡都是 **`miuiLevel = 4`、`level = 3`**（同一行里两个字段并存），
  系统画四格满格，双排照 AOSP 口径取就矮一格。jadx 侧佐证 MIUI 自己的取法：
  `work\jadx-out\sources\com\android\systemui\statusbar\connectivity\MobileSignalController.java:495`
  = `miuiLevel = signalStrength2.getMiuiLevel();`。`getMiuiLevel()` 不在公开 SDK 里
  （`javap` 查 SDK 37 的 `android.jar`，`android.telephony.SignalStrength` 只有 `public int getLevel();`），
  所以新增 `TrioState.miuiLevel(SignalStrength)`：`Refl.callByName(strength, "getMiuiLevel")`，
  非 Number 才退回 `strength.getLevel()`；0..4 之外的拒绝规则不变（MIUI 报 9 仍判 -1）。
  离线验证：`work\simcheck\verify.ps1` 从两构建升为**三构建**，第三个反例把
  `final int level = miuiLevel(strength);` 换回 `final int level = strength.getLevel();`（该正则必须
  恰好命中 1 处，否则脚本自己 `throw`）—— 固定版 `exit 0`（**47 条**断言全 `[ok]`）、
  「订阅号当槽位」版 `exit 1`（27 条不符）、「用 AOSP 电平」版 `exit 1`（**恰好 4 条**不符，且正是
  那 4 条 MIUI 断言，分离度精确到条）。`gapcheck` / `slotcheck` / `dualsimcheck` / `overview` 复跑
  全部 `exit 0`（`renderer = e96fd812ba2a…`）；`:app:assembleDebug` `BUILD SUCCESSFUL in 19s`，
  `app-debug.apk` **33434594 B**、sha256 **`4A0738A03E92D0828B8257C6FC76652D1FA86D22589D6ED3BD405F58EBA4032C`**，
  6 个 dex 里 `getMiuiLevel` / `miuiLevel` / `getLevel` 全部命中、`com/hyperduo` 零命中。
  **尚未在真机上复验「双排两排都满格」**（当时设备不在线；`adb mdns services` 返回空）——
  实机证据目前只有 `dumpsys` 那两个字段，没有装新 APK 后的截图。

- **环外信号尺寸异常与「环外信号大小」（第十轮补）**：用户报「环外信号尺寸异常」并要求「增加环外
  信号尺寸的调节功能」，并指出参考 `https://github.com/ColdP/HyperChanger`。新增 `out_signal_size`
  （int 百分比，默认 100 = 与状态栏图标同高，范围 50 – 200），UI 是尺寸卡里的 `IntSlider`，
  门禁 `a.stackedOut()`、提示指向 `master` / `show_mobile` / `signal_mode` / `stacked_signal`
  四条上游开关（**不是**本行自己的结果，否则关掉就再也推不回来）。
  **尺寸异常的真身**：`outSignalWidth(height, dots)` 的分母随点行在 209 / 150 之间切换，
  `drawOutSignal` 的 `scale = min(width/inkW, height/inkH)` 又用同一个较矮的框 —— **两处互相印证**，
  于是同一宿主高度下「无点行」（单卡、或开了「仅显示上网卡」）的柱被放大 `209/150 ≈ 1.39` 倍，
  单卡读数比双卡高出一大截。修法是**参照框只留 209**：新增
  `outSignalHeight(int anchorHeight, int percent)`（纯算术，`Math.max(1, round(h * percent/100f))`）、
  `outSignalWidth` 收成单参、`drawOutSignal` 的 scale 固定除以 `STACK_INK_H`，`outSignalInkH(dots)`
  降级为**居中用的墨高**。`TrioHooks.outSignalHeight(View anchor)` 是唯一的换算入口，
  `measure` 与变更比较都用它。
  借鉴来源：HyperChanger 的 `stacked_mobile_signal_scale` 是 **float** 且夹取 `0.1..3`，
  应用方式是给容器打 `scaleX/scaleY`；本项目取 **int 百分比**以贴合「所有既有设置项都是 int +
  `IntSlider`」的现状，且不缩放视图而是重算几何。
  离线验证：`work\outringcheck\verify.ps1` 从三构建升为**四构建**，第三个反例 `squat` 把
  `scale` 与 `outSignalWidth` **两行同时**改回旧分母（**只改一处看不见** —— 这正是该 bug 能长期
  存活的原因），固定版 `exit 0`、三条反例分别 3 / 14 / 3 条不符而 `exit 1`；`squat` 实测最高柱
  `51 vs 72 px`（约 1.39 倍本身）。既有四条工装复跑全部 `exit 0`。`:app:assembleDebug`
  （`--offline`）`BUILD SUCCESSFUL in 28s`。
  **探针自身的一条教训**：尺寸断言最初用**正确公式**去算取样列，而 `squat` 构建的几何恰恰是错的，
  取样点整个落在柱外，量到空画布（`51 vs 0 px`）—— 断言「两边一样大」时取样点必须与几何无关
  （改取全画布最长的亮柱 `tallestRun`），否则一条本意抓尺寸错的断言退化成在抓「那里没东西」。
  **其中「int 百分比」这一段已被后一轮取代**（见下条「第十一轮补」：单位改为 dp，键值也随
  字符串字面量改为 `out_signal_size_dp`）；本条其余事实（尺寸异常的真身、参照框只留 209、
  行内四构建与探针教训）仍然成立，未受那一次改动影响。

- **「环外信号大小」由百分比改为 dp（第十一轮补）**：用户报「而且下拉到控制中心后这个信号还会
  莫名其妙的放大,需要修复」。**机制**：旧值 `out_signal_size` 是电池容器**活高度**的百分比，
  而 MIUI 把那行在收起时常驻 88px、拉开控制中心后变成 134px（就是系统 `statusBars` inset 的
  高度，`dumpsys window displays` 实测），读数于是按 `134/88 ≈ 1.52` 倍跟着长 —— 这不是绘制
  错，是参照系本身会变。**修法**：单位换成 dp，`TrioRenderer.outSignalHeight` 的签名由
  `(int anchorHeight, int percent)` 改为 `(int sizeDp, float density)`，体是
  `if (sizeDp <= 0 || density <= 0f) return 0; return Math.max(1, Math.round(sizeDp * density));`
  —— 与那一行多高彻底无关。`TrioHooks.outSignalHeight(View host)` 只从视图取
  `getResources().getDisplayMetrics().density`，视图的测量高度**故意不取**（取了就等于把这个
  bug 请回来），两个调用点（变更检查 `view.getMeasuredHeight() != outSignalHeight(host)` 与
  `updateOutSignal` 里的 `final int height = outSignalHeight(host);`）都传 `host`；
  `TrioPreviewView` 自己从 display 取密度再调 `outSignalHeight(a.outSignalSize, density)`。
  **键与取值**：`Prefs.KEY_OUT_SIGNAL_SIZE` 这个常量名不变，只有它的字符串字面量从
  `out_signal_size` 改成 `out_signal_size_dp`；`DEF_OUT_SIGNAL_SIZE` `100 → 15`、
  `MIN_OUT_SIGNAL_SIZE` `50 → 6`、`MAX_OUT_SIGNAL_SIZE` `200 → 20`（`IntSlider` 读同一对常量，
  所以 `TrioSettings` / `TrioAppearance` / `SettingsRepository.kt` / `SettingsScreen.kt` 一行没动）。
  **不欠迁移**：那个旧键值从未随任何一次发布出货，框架也不做类型转换。`outSignalWidth(int height)`
  与 `outSignalInkH(boolean dots)`、`drawOutSignal` 里 `scale = Math.min(width / inkW, height /
  TrioGeometry.STACK_INK_H)` 都**未改**，`TrioPreviewView.HOST_ICON_HEIGHT_DP = 20f` 仍由
  `drawOutTypeLabel` 用着、没有删。**15dp 是实测来的**：测试机（小米 houji / Redmi K70，
  1200×2670，`Physical density: 480` 即 density 3）上 MIUI 自己那四条信号柱墨高 44px，
  `15 × 3 = 45` 正好同高；`6..20dp` 在同密度下是 18..60px，下界是四根柱仍分得清的最小值、
  上界是状态栏那一行在开始挤动邻居图标之前能容下的最大值。文案 `out_signal_size_summary`
  （`values` / `values-en` 两份）同步改写为 dp 说明。

- **1.4 发布（含包名更换 + 双卡/环外/按 MIUI 电平取格）**：第一次用「先提交干净、再跑 release.ps1」的流程走通（`release.ps1` 构建工作树但给 `HEAD` 打 tag，所以必须工作树干净）。提交 `a98053f` `release: 1.4, rename the package to io.github.yixing233.hyperduo`（30 files、4464 insertions / 1103 deletions）先 `git push origin main`（`release.ps1` 只推 tag、**不推分支**，必须单独推一次），再 `release.ps1 -Version 1.4 -NotesFile .tmp\modrepo\notes-1.4.md` → `exit 0`、`BUILD SUCCESSFUL in 14s`。tag `v1.4` 是指向提交 `a98053f68ffe076574053ae17ffdfda0fbe30cf9` 的 **annotated tag**（tag 对象 `058f6f54229c9807004cd7fecf9555dcdccee61a`，`git rev-parse "v1.4^{commit}"` 与 `HEAD` 一致；注意在 PowerShell 里 `^{commit}` 必须加引号，否则 `^{…}` 被当转义吃掉）。Release `HyperDuo 1.4` 非 draft / 非 prerelease（`published=2026-10-03T05:40:25Z`），asset `HyperDuo-1.4.apk` **3067544 B**。**发布产物按字节复核**：从 `https://github.com/yixing233/HyperDuo/releases/download/v1.4/HyperDuo-1.4.apk` 下载回来 SHA256 **`0B95AF03D704C3F52C7B015023F67EA3217C22299CD1AECDBB6ABC07D65BE527`** 与本地 `dist\HyperDuo-1.4.apk` 逐字相同。
- **1.4 发布产物的离线核对**（无设备，上机验证仍缺）：`aapt2 dump badging` → `package: name='io.github.yixing233.hyperduo' versionCode='10400' versionName='1.4'`、targetSdk 36、ABI 四套；`apksigner verify --print-certs` → v2 方案 `true`、证书 `CN=HyperDuo, O=HyperDuo, C=CN`、SHA-256 `b4e3a12d…8c41f`（与设备上原装 APK 同一证书，所以除改名那一次外，同包名的后续版本仍可 `-r` 覆盖）。
- **R8 每次构建字节都不同，但 1.4 这次只差一处**：用 `release.ps1 -DryRun` 重建一份后与已上传的那份对比，**85 个 zip 条目、CRC、时间戳全部相同、体积相同**，却有 339 字节不同、哈希不同。逐字节定位（`.tmp\apkcmp.py`）后锁定唯一差异在 `META-INF/version-control-info.textproto` —— AGP 把构建时的 git 版本写进了 APK：旧的写 `revision: "3743845…"`（改名前的提交），重建的写 `revision: "a98053f…"`（本次发布提交）。另外 4 段差异全在签名块与中央目录（签名覆盖了那个文件，所以连签名一起变）。**结论：重建的那份才是与 tag 一致的正确产物**，已用它覆盖模块仓 asset（`release-module.ps1 -Force` 重传；主仓是首发，无需 `-Force`）。两处 asset 与本地 `dist\HyperDuo-1.4.apk` 现在**三者同一哈希** `0B95AF03…BE527`。
- **PS 5.1 读 BOM-less UTF-8 脚本会按 ANSI（本机 `gb2312`）解码，直接 `& .\release.ps1` 必然解析失败**（报 `Missing expression after ','`、`The string is missing the terminator` 等一串错，且错误里中文全是乱码 —— 这是判据）。`release.ps1` / `install.ps1` / `.tmp\modrepo\release-module.ps1` 都是 BOM-less UTF-8 且含中文，所以在这台机器的 Windows PowerShell 5.1 下只有两条路：装 pwsh 7，或 `[scriptblock]::Create([System.IO.File]::ReadAllText($p,[Text.Encoding]::UTF8))` 后调用（脚本内硬编码 `$Root`、不用 `$PSScriptRoot`/`$script:`，所以这样调用安全）。本次用后者，两个脚本都以 `exit 0` 跑完。**要长期可用，应给这三个脚本加 UTF-8 BOM**（未做，属本次范围外）。
  > **后续（1.7.0 发布时）：** 机器已装 PowerShell 7.6.7，这条路不再需要——pwsh 7 默认按 UTF-8 读脚本，BOM 可有可无。脚本仍保持 BOM-less，因为加 BOM 会让 git 把整个文件显示为首行变更。发版请用 `pwsh` 执行，不要用 `powershell.exe`（5.1）。
  >
  > **路径也已变：** 上面那条记录写的是当初的位置（`.\release.ps1`，脚本当时在仓库根）。五个脚本后来移入 `scripts\`，所以现在是 `.\scripts\release.ps1`、`.\scripts\env.ps1`，`build.ps1` 归入 `scripts\legacy\`。本文其余处已按新路径书写，只有这类历史记录保留原样。
- **LSPosed 模块仓首发 release**：模块仓 `Xposed-Modules-Repo/io.github.yixing233.hyperduo` 按仓库规格发 tag `10400-1.4`、title `1.4`、body 取 `.tmp\modrepo\notes-1.4.md`（首行是「安装前必读」的包名更换说明），asset `HyperDuo-1.4.apk` 3067544 B；`README.md` 同步更新到 1.4（`PUT contents` 带旧 sha → 新 sha `fab6b22eb153025b808a3ead27db6a4a5abae4fc`、commit `cb10135`），`SUMMARY` 未动。驱动脚本 `.tmp\modrepo\release-module.ps1`（只走 Releases API，模块仓不克隆到本地；`maintain` 角色不能改仓库设置但**能发 release**，已实测 `POST /releases` → 201）。
- **模块仓「已上线但未收录」的唯一门禁 = 仓库 description 为空**：抽样 `Xposed-Modules-Repo` 24 个仓库逐个探 `https://modules.lsposed.org/module/<pkg>/`，结果是「描述空 + 404」4 个（含本项目）、「描述空 + 已收录」**0** 个、「描述非空 + 已收录」20 个。所以 release 发了、README 有了也仍 404（`io.github.yixing233.hyperduo` → 404，对照 `io.github.kvmy666.duostatusbar` → 200）。而 **description 目前无法由 API 设置**：`PATCH /repos/Xposed-Modules-Repo/…` → 404 Not Found；GraphQL `updateRepository` → `FORBIDDEN: yixing233 does not have the correct permissions to execute 'UpdateRepository'`（尽管 GitHub 角色表上 maintain 本应有 `Edit a repository's description`，且同 token 的 `PUT …/topics` → 200，证明 token 与路由都正常）。⇒ **只能在 GitHub 网页的 About 面板手填**，文案已备：description `HyperDuo · 仿 iPhone Duo 三合一状态栏图标`（`.tmp\modrepo\desc.txt`）、homepage `https://github.com/yixing233/HyperDuo`（`.tmp\modrepo\homepage.txt`）。**（这一项后来已由用户手填完成并成功收录，见本节末尾。）**

- **模块仓 README 补预览图（1.4 之后）**：模块仓 `Xposed-Modules-Repo/io.github.yixing233.hyperduo` 的 `README.md` 原先只有纯文字，已在三处插入 `<img>`（用 HTML 标签而非 Markdown 语法，就是为了显式控制 `width`）：第 5 行标题下 `statusbar.png`（`width="900"`，真实状态栏字形）、第 48 行新增 `## 效果预览` 一节插 `states.png`（`width="820"`，六种状态）、第 70 行 `## 设置` 一节插 `settings.png`（`width="420"`，设置界面整屏）。图片**不落在模块仓里**，而是引用主仓的图：`https://cdn.jsdelivr.net/gh/yixing233/HyperDuo@main/docs/images/<name>.png` —— 模块仓按规格只放 `README.md` + `SUMMARY` 两个文件（`git clone --depth 1` 复核确认仍只有这两个），所以图片只能外链；选 jsDelivr 而非 `raw.githubusercontent.com` 是因为对照仓 `io.github.kvmy666.duostatusbar` 的已上线 README 用的就是 jsDelivr，且 jsDelivr 自带 CDN 与 `@main` 缓存语义。
- **外链可达性与字节一致性已实测**：`raw.githubusercontent.com` 与 `cdn.jsdelivr.net` 两条路对 `docs/images/` 下三张图都是 HTTP 200 且 **SHA256 与工作树逐字相同**（`statusbar.png` 155272 B / 4800×384、`states.png` 37408 B / 1124×405、`settings.png` 131178 B / 1200×1780；三张图 git 内已跟踪且工作树无改动）。GitHub 侧的渲染也用 `Accept: application/vnd.github.html+json` 拉 `readme` 端点验证过：三个 `<img>` 都正常生成（GitHub 把它们经 camo 代理，`data-canonical-src` 仍是我们的 jsDelivr URL），说明语法与地址都没问题。写入用 `PUT /repos/Xposed-Modules-Repo/io.github.yixing233.hyperduo/contents/README.md`（带旧 sha `fab6b22eb153025b808a3ead27db6a4a5abae4fc`）→ 新 sha `00ccd134494a60e6ba89a1aa4f022e98cb92fbc9`、commit `1a43a3f`；拉回来与本地暂存件逐字比对为 `True`（11004 B）。`SUMMARY` 未动（模块仓首页摘要仍是不含图片的一小段）。
- **description / homepage 已由用户手填完成**（这是上一节留下的唯一阻塞项）：`GET /repos/Xposed-Modules-Repo/io.github.yixing233.hyperduo` 现在返回 `description = 'HyperDuo · 仿 iPhone Duo 三合一状态栏图标'`、`homepage = 'https://github.com/yixing233/HyperDuo'`、`topics = lsposed, statusbar, xposed`。**但模块页此时仍是 404**（`https://modules.lsposed.org/module/io.github.yixing233.hyperduo/` → 404，对照 `io.github.kvmy666.duostatusbar` → 200）——按仓库说明，收录要等上游 Cloudflare Pages 重新构建（说明写的是 5–15 分钟），**所以「填了 description 就立刻 200」并不成立，需要按时间复验**。
- **模块页已收录（description 手填后约 23 分钟）**：`https://modules.lsposed.org/module/io.github.yixing233.hyperduo/` 在 **14:14:43** 由 404 转为 **200**（后台轮询 60 s 一次，`?cb=`/`Cache-Control: no-cache` 都不能提前穿透缓存，只有上游重新构建才生效；description 手填时刻约 05:51:27Z，即上游站点约 23 分钟后才重建）。**判据不能只看详情页 404**：同一时刻站点首页 `https://modules.lsposed.org/` 的 `astro-island` props 里已经出现本模块（`"name":"io.github.yixing233.hyperduo"`、`description` 为手填的那串、`url` 指模块仓，列表行 `<h2>` 显示手填 description、`Website` 指主仓、`Source` 指模块仓、`<time dateTime="2026-10-03T05:58:49Z">`），所以**「首页已列出、详情页还 404」是重建过程中的中间态**，等待期间不要据此判定失败。
- 收录后的详情页（36628 B）核验：`<title>` = `HyperDuo · 仿 iPhone Duo 三合一状态栏图标 · Xposed Module Repository`；README 的三个 `<img>` 全部渲染出来，`data-canonical-src` 仍是我们的 jsDelivr 地址（`statusbar.png` width 900 / `states.png` width 820 / `settings.png` width 420）；下载链接是 `download/10400-1.4/HyperDuo-1.4.apk?sign=…`，即模块仓那个 release。
- 工具坑：详情页直连偶发 `000`（curl 超时/连接失败），**必须 `curl.exe -sL` 跟随重定向并加重试**，否则会把网络抖动误判成 404（无斜杠的 `.../hyperduo` 返回 `308`，`-L` 才能跟到 200）。
- **「只保留最新版 Release」落地（1.4 之后）**：两个脚本各加一个 `Remove-StaleReleases`
  函数（`GET releases?per_page=100` → 过滤 `$_.id -ne $KeepId -and -not $_.draft` → 逐个
  `DELETE /releases/$id`）和 `-KeepOldReleases` 开关，都在**上传 asset 之后**才调用。
  主仓提交 `f90046e`（`release.ps1`，`1 file changed, 39 insertions(+), 1 deletion(-)`）；
  模块仓脚本见下条。**执行前先验证可恢复性**：把 4 个远端 asset 全部下载回来与 `dist\` 同名
  文件逐个比 SHA256，得到 `v1.4 3067544 B` / `v1.3 3038276 B` / `v1.1 3038076 B` /
  `v1.0 2560743 B` **四个 `identical=True`**，确认字节完全一致后才 `DELETE` 掉
  `v1.3`/`v1.1`/`v1.0`（保留 `v1.4`）。清理后 `git ls-remote --tags origin` 仍是八个 ref
  （四个 tag 各有 annotated + peeled 两条），`GET /repos/yixing233/HyperDuo/releases/latest`
  → `tag=v1.4`、asset `HyperDuo-1.4.apk` 3067544 B，**应用内更新器不受影响**。
- **模块仓发布脚本已从 `.tmp\modrepo\release-module.ps1` 移到仓库根 `release-module.ps1`**：
  它原先落在 `.gitignore` 的 `/.tmp/` 里，而 `docs/DEVELOPMENT.md` 却在引用它 ——「每次发布
  都这样」的策略放在被忽略的目录里等于不可复现。现在它与 `release.ps1` 并排入库。**该文件
  刻意保持 ASCII-only 且无 BOM**（`nonASCII=0`、7702 B），所以 PS 5.1 按 ANSI 读也无害；
  文件头注释已写明这条约束，**不要往里面加中文**。验证：`& .\scripts\release-module.ps1 -Version 1.4
  -VersionCode 10400 -Apk .\dist\HyperDuo-1.4.apk -DryRun` → 版本三者全对、`exit 0`。
- **包名更换（`a98053f`）曾把两个脚本的 UTF-8 BOM 弄丢，造成真实回归**：`release.ps1` 在
  `bcb7f9b`/`0cc4d5b`/`03b36b5` 三个历史版本里都是 `BOM=True (239,187,191)`，到 `a98053f`
  变成 `BOM=False (35,32,230)`；`install.ps1` 在 `41ef355` 是 `BOM=True`、`a98053f` 也丢了。
  后果：本机 `[Parser]::ParseFile` 读 `release.ps1` 报 **9 个 parse error**（`line 11: Missing
  expression after ','`、`line 44/55: Unexpected token …`），即**文档里的调用方式
  `.\release.ps1` 当时根本跑不起来**；`install.ps1` 则 parse errors 为 0 但运行时打印乱码
  （正是 §「教训（PowerShell 5.1 与 BOM）」记过的那个陷阱）。修复见提交 `39c4b45`
  （`2 files changed, 2 insertions(+), 2 deletions(-)`，每文件仅第 1 行 1 增 1 删）。
  **回归测法**：`ParseFile` 数 errors + 用 `[System.IO.File]::ReadAllText($p,[Text.Encoding]::UTF8)`
  核对中文是否正常，两条都要做。**教训：凡是"改包名/批量替换"式的全局改写，收尾必须复验
  BOM 与行尾**，因为这类改写常顺手重写整个文件。
- **`.gitattributes` 已经规定 `*.ps1 text eol=crlf`，且仓库 `core.autocrlf=false`**，所以
  `git add` 时出现的 `warning: in the working copy of '...', LF will be replaced by CRLF`
  是**无害的**（索引里存的仍是 LF，工作树保持 LF）。不要为了消掉这个警告去改脚本行尾。
- **环外视图前景色跟随深浅色（第十二轮补）**：用户报「自行绘制的环外信号类型，像 5G 的字体颜色，
  似乎有跟随状态栏文本变色的逻辑……但是它的更新不是很及时」。根因与修法见「实时刷新」下的
  「环外视图的前景色（深浅色跟随）」一节：`hyperduo-draw` 钩子比较
  `state.foreground()` 与 `TrioState.outRingInk`，只在不等的帧记账并 post 一次
  `recolourOutRing`。**这一条只有真机能验**（改深浅色的那一下是否立刻跟上），当前设备离线，
  上机验证仍缺；离线侧只保证编译不回归、五条工装（`gapcheck` / `slotcheck` / `dualsimcheck` /
  `simcheck` / `outringcheck`）全部维持原判，外加出图工装 26 格照常渲染。
  `TrioHooks` 需要 Xposed API、桌面上编不了，所以这条规则像该文件里其它几条一样，改成在
  `work\outringcheck\verify.ps1` 里加**源码钉子**：四条成对断言（只在不等的帧记账 / 先记账再 post /
  标签拿到新墨色 / 读数被要求重绘），外加两条形状断言（`OutSignalView.onDraw` 上那句「tint 变化会
  让它失效」的错误注释不得复活；`outRingInk` 必须是实例字段，而 `foreground()` 必须仍把 0 折成
  `DEFAULT_FOREGROUND`，否则首帧会被当成「没变」而跳过）。七种回归形态各自实测都能把钉子碰响。
  另一条能离线做的判据是**产物核对**：`app\build\outputs\apk\release\app-release.apk` 的
  `classes.dex` 同时含 `recolourOutRing` 与 `outRingInk`（debug 包在 `classes3.dex`），
  证明改动确实进了产物而不是被增量构建漏掉。
