# Quieta（息匣）

给 Android 通知渠道降噪的系统工具。帮用户看清、管住「谁在乱发通知」：批量静音营销渠道、按规则拦截新建渠道，并保持默认无常驻、低耗电。

- 显示名：息匣 / Quieta
- 一句话：Notification channels, quieted. / 给通知渠道降噪
- 仓库名：`quieta`
- `applicationId`：`app.quieta`
- 作者：`1290000`（GitHub）
- 远程仓库：`https://github.com/1290000/Quieta.git`
- 协议：GPL-3.0-only（第三方 Apache-2.0 库可并入；液态玻璃参考 Kyant0/AndroidLiquidGlass）

---

## 1. 产品边界

**做：**

- 盘点各 App 的 NotificationChannel（名称、ID、重要级别、渠道数量排行）
- 按 App / 渠道名规则批量静音、降级 importance
- 规则支持包名/包前缀/渠道 ID 精确与前缀/关键词（可区分名称与 id）；**包名与渠道 ID 精确字段支持逗号/换行分隔多个值（OR）**；白名单 KEEP 优先，其余更精确者优先；配置页分组「永不静音 / 静音降级」
- 批量静音前计划预览（二级页 dry-run）；筛选/排序收成入口 + 勾选弹层（miuix Card）；渠道行可手动静音/降级/恢复；整应用可一键静音/恢复；支持撤销最近静音批次
- 新建渠道自动按规则处理（可选开启）
- 通知时间线（摘要级，默认弱采集；记包名/应用名/渠道名/id/时间范围/条数/importance，**不记正文**；1 分钟合并窗，7 天 / 200 条；记录页按日期与 App 分组 + 今日摘要 + 静音联动）
- 规则本地存储，可导入/导出 JSON（根字段 `schemaVersion` + `kind: "rules"`；配置页勾选子集或全部导出；导入预览后可选合并/替换）；**渠道设置快照**可导出/导入 JSON（`kind: "quieta-channel-snapshot"`，包名+渠道 id 关联；导入 dry-run 后按 importance 写入并回读校验，系统应用默认跳过）
- 提权可用性检测与引导（对齐 InstallerX「可用特权」心智）

**不做：**

- 不做垃圾清理、加速、弹窗广告、电脑管家式全家桶
- 不上传通知正文；默认无遥测
- 不当「应用商店」或通用包安装器

**MVP 金路径：**

1. 渠道盘点  
2. 按 App / 关键词批量静音  
3. 新渠道自动静音（用户可选）

**一期明确不做：** 快捷设置 Tile、桌面 Widget、平板/折叠优先布局、应用内静默强更。

**已交付（摘要）：** MVP 盘点/批量静音/可选自动静音；Shizuku 盘点与 setImportance（列表路径 + create/update，K40s 25/25）；本地规则 JSON；弱采集通知时间线（包名/渠道/时间/数量，默认 7 天 / 200 条保留）；四栏 HorizontalPager 壳；关于/许可/检测更新；HyperOS 风格 UI；底栏 InstallerX 移植 + 液态玻璃（K40s/K90 观感已验收）；主页渠道列表搜索 / 筛选（含 HIGH、含 NONE、将静音）/ 排序（渠道数、名称、包名、最高级）/ App 卡折叠与展开收起（派生投影，不触发重扫）；二级页预测性返回动画严格对齐 InstallerX Revived（AOSP / Miuix / 缩放 / 经典 / 无；退出方向仅「缩放」生效，跟随手势按 swipeEdge）。**未创建 GitHub Release / 未打 tag。**

**已约定、尚未完成 / 待验收（后续代理优先做）：**

1. ~~**液态玻璃观感验收**~~ — 已验收（K90 HyperOS）。  
2. ~~**批量静音真机验收**~~ — K40s / MIUI 14：25/25 成功；其它 ROM（ColorOS / OriginOS / MagicOS / One UI）仍待矩阵覆盖。  
3. ~~**Root / Dhizuku 后端**~~ — 已完成：`DhizukuBackend` 使用 Binder；`RootBackend` 使用 libsu RootService 独立读写并回读校验，不依赖 Shizuku/Dhizuku。自动选择 Root → Shizuku → Dhizuku；不同管理器与 ROM 的真机覆盖仍需扩展。
4. ~~**通知时间线页**~~ — 已实现弱采集时间线与本地记录 UI；默认仅保存包名、渠道、时间、数量，支持 7 天 / 200 条滚动保留及设置页关闭。
5. ~~**二级页 Navigation**~~ — 已完成：已增加独立二级路由栈；主题设置、开放源代码许可、可用特权页均支持页面返回和系统返回，主栏状态保持不变。
6. ~~**AboutLibraries 自动收集**~~ — 已完成：Gradle 插件生成 `aboutlibraries.json`，许可页读取自动依赖元数据；移植代码与架构参考使用补充条目披露。  
7. **release 签名与首个 Release**：`signing.properties` 流程已约定，未生成正式包、未发 GitHub Release。  
8. ~~**记录页筛选**~~ 以外的备份边界：**规则导出**配置页支持多选/全部分享 JSON，文件导入可**合并**或**替换**；**渠道设置快照**导出（主页多选 / 设置全量）与**导入**（设置→导入渠道设置：对照预览 → 写入 importance → 回读报告；系统应用默认不改；关联键包名+渠道 id）已实现；声音/震动等完整字段写入与 ROM 矩阵导入验收仍待覆盖。

**一期关于页必做：** 展示应用名与作者；「查看源代码」跳转本应用仓库；「检测更新」手动检查 GitHub Release（可打开最新页，不做静默下载/强制安装）。

**二期：** 营销 vs 重要启发式归类、通知摘要、更多 ROM quirk、宽屏布局。

### 配套测试工程：Notiflab（强制知晓）

真机验收（盘点 / 静音 / 降级 / 新建拦截）依赖可控噪音源，由独立工程 **Notiflab（息匣通知实验室）** 提供，**不得**把测试噪音逻辑塞进本仓库。

| 项 | 约定 |
|----|------|
| 仓库 | `https://github.com/1290000/NotifLab` |
| 本地路径 | 与本仓库同级：`C:\Users\i1290\Documents\ChatGPT\Notiflab`（机器相关，可覆盖） |
| 包名 | 正式 `app.quieta.notiflab`；**联调默认 debug** `app.quieta.notiflab.debug` |
| 产物 | `Notiflab/app/build/outputs/apk/debug/app-debug.apk` |

**Notiflab 为息匣测试提供：**

| 能力 | 用途 |
|------|------|
| 固定 8 个 `lab.*` 渠道（HIGH/DEFAULT/LOW/MIN） | 盘点数量、名称、importance 分布对照 |
| 单渠道「发送」/「连发全部」 | 验证静音后是否投递、降级后是否仍进通知栏 |
| 「重置渠道」 | 删光 `lab.*` 后按目录重建，用于「新建渠道自动静音」回归 |
| 「清空通知」 | 清场，避免旧通知干扰观察 |

**息匣在测试中的职能：** 治理端——盘点、按规则静音/降级、可选自动拦截、回读校验 importance。

**代理测试义务（本仓库任何真机验收）：**

1. **会自行调用 Notiflab**：安装/更新其 debug APK，授予 `POST_NOTIFICATIONS`，用 UI 或 `am start -n app.quieta.notiflab.debug/app.quieta.notiflab.MainActivity` 拉起，再点发送/重置；不要假设用户手工操作噪音源。  
2. **会自行调用息匣**：安装/更新本仓库 debug APK，拉起 `app.quieta.debug/app.quieta.MainActivity`，执行刷新 / 按规则静音 / 查看 importance。  
3. 验收以 **系统侧 dumpsys / 回读 importance** 为准，不以 UI 文案「成功」为唯一证据。  
4. Notiflab **禁止**在发送路径重建已存在渠道（会冲掉静音）；详见 Notiflab 仓库 `AGENTS.md` §5。  

---

## 2. 技术栈与 SDK

| 项 | 约定 |
|----|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + miuix |
| 构建 | Gradle Kotlin DSL + Version Catalog；AGP **9.4.0** · Gradle **9.6.0** · Kotlin **2.4.10**；miuix **本地 includeBuild** `D:/Tools/miuix`（或 `-PmiuixDir=`） |
| minSdk | 26（液态玻璃仅 API ≥ 33；miuix-blur 用 `tools:overrideLibrary`） |
| targetSdk / compileSdk | 37（`android.suppressUnsupportedCompileSdk=37,37.0`） |
| 架构 | 多模块单向依赖；见下文 |
| DI | 手工构造或 Koin；一期不用 Hilt |
| 页面导航 | 四栏 `HorizontalPager` + `MainPagerState`（InstallerX 式 EaseInOut）；二级页再引入 Navigation/miuix-nav |
| Shizuku | `dev.rikka.shizuku`（api + provider）；不自造绑定协议 |
| 数据 | 全本地（Room + DataStore），不联网收集用户通知 |
| 主导航 | 主页 / 配置 / 记录 / 设置（四栏） |
| 关于 | 版本信息、作者、**查看源代码**（跳本应用仓库）、**检测更新**（查 GitHub Release）、**完整开源依赖列表**（每项可跳对应仓库） |

---

## 3. 仓库与模块结构

```text
quieta/
├── app/                 # Application 壳：组装、导航、权限引导、关于
├── core/                # 纯业务：模型、规则引擎、提权抽象、ROM 探测、持久化
├── ui/                  # miuix 主题、液态玻璃底栏、通用组件
├── gradle/              # libs.versions.toml 等
├── LICENSE              # GPL-3.0
├── README.md
├── AGENTS.md            # 本文件
└── settings.gradle.kts
```

MVP 使用三模块 `:app` / `:core` / `:ui`。页面显著增多后再拆 `feature/*`。

### 包名

```text
app.quieta              # MainActivity, App, Nav
app.quieta.core.*       # model / privilege / engine / repo / rom
app.quieta.ui.*         # theme / glass / component
app.quieta.feature.*    # 仅在拆 feature 后使用
```

### 依赖方向（必须单向）

```text
app → core
app → ui
ui  ↛ core          # UI 不碰提权 / IO
core 尽量少依赖 Compose；Android 系统 API 收在 backend 实现里
```

---

## 4. 提权（Privilege）

多后端检测可用性，用户选一种作全局授权（对齐 InstallerX Revived 的「可用特权」页）。自动选择顺序固定为 Root → Shizuku → Dhizuku；手动指定不可用时不偷偷改选。主页与可用特权页使用同一探测状态，盘点结果不得覆盖授权名称。Root 管理器在授权后的 su 环境中探测；无法识别时明确显示未知，不凭管理器安装与否推断。

| 后端 | 阶段 | 能力 |
|------|------|------|
| 无特权 | 一期 | NotificationListener 统计 + 引导跳系统设置；不能改他人渠道 |
| Shizuku | 一期主路径 | 枚举 / 修改 importance / 静音 / 删除渠道；拦截新建渠道 |
| Root（KernelSU/Magisk/APatch） | 独立实现 | libsu RootService + AIDL 直接读写通知 Binder；不借道 Shizuku/Dhizuku；写后回读确认 |
| Dhizuku | 二期 | 单独验证渠道 API 能力表；不足则降级提示 |

### 接口约定

```kotlin
interface PrivilegeBackend {
    val id: PrivilegeId
    suspend fun isAvailable(): Boolean
    suspend fun listChannels(pkg: String): List<Channel>
    suspend fun setImportance(pkg: String, channelId: String, importance: Int)
    // 删除渠道等按需扩展
}
```

- UI 只依赖接口与能力探测结果，不直接绑 Shizuku。  
- 功能菜单按探测结果显隐；失败降级：自动改 → 跳系统设置 → 仅统计。
- Root 服务按需绑定、空闲后释放，不使用 daemon 模式；区分授权、读取能力与写入验证状态。探测不修改用户渠道，写入以目标渠道回读结果为准，真机未验证的 ROM 不宣称兼容。
- Root 读写不得依赖管理器包名；覆盖 Magisk、KernelSU、APatch 及分支的版本标识，未知实现仍按实际授权与能力使用。只返回家族名的分支以“家族 + 已安装管理器”分别展示；隐藏/重命名管理器不影响提权。设备测试只写测试 APK 的临时渠道并清理。

### 应用自身权限

- `POST_NOTIFICATIONS`（前台服务 / 结果通知，按需）
- 通知使用权（NotificationListener，统计与时间线）
- `QUERY_ALL_PACKAGES`（列出已装应用；侧载场景）
- 前台服务类型仅在用户开启「持续监控」时使用
- 电池优化白名单：仅开启监控时引导

---

## 5. ROM 适配

原则：先保证 AOSP/Shizuku 标准路径，再用探测 + quirk 补差异；禁止大段按品牌写死业务。

```text
core/rom/
  RomId.kt              # HyperOS / ColorOS / OriginOS / MagicOS / OneUI / Aosp / ...
  RomDetector.kt
  CapabilityProbe.kt    # 读渠道 / 写 importance / 删渠道 / Listener
  quirks/
```

| ROM | 注意点 |
|-----|--------|
| 小米 HyperOS / MIUI | 纯净模式、未知来源；引导安装与风险拦截 |
| OPPO / 一加 ColorOS | 自启动、关联启动；系统级通知类别可能覆盖渠道设置 |
| vivo OriginOS | 后台限制严；采集降频、前台服务约束 |
| 荣耀 MagicOS | 权限偏紧；不可用则明确仅引导模式 |
| 三星 One UI | 相对标准，可作参考机 |

启动做 **CapabilityProbe**，结果落本地；UI 按能力表显隐，不假装能改。

---

## 6. UI / 视觉

真相源：`ui/theme/QuietaTheme.kt`、`QuietaTypography.kt`、`PresetColors.kt`、`ui/component/*`、`ui/glass/*`；默认值以代码为准，本节约定须与实现一致。

### 视觉锚点与色板

- 对齐 InstallerX Revived / 澎湃：浅色浅灰底、白卡片、大标题；一期手机竖屏优先。
- **出厂默认**（自定义色关闭）：primary `#3482FF`（`HyperBlue`）；浅色 `background #F5F5F6` / `surface` 白；深色 `background #242424` / `surface #2C2C2C` / primary `#7EB0FF`。深浅色均须可读。
- 主题默认**跟随系统**；开启「自定义颜色」后 primary、surface 等角色跟种子方案（Monet / materialkolor + miuix `ThemeController`），**不再锁死系统蓝**。自定义色默认种子为 Material 紫 `#6750A4`，可换 `PresetColors`。
- 实现为 `MaterialTheme(QuietaTypography)` + `MiuixTheme(ThemeController)` 双轨；业务色走 `MaterialTheme.colorScheme` / `MiuixTheme.colorScheme`，禁止散落硬编码（语义色例外见下表）。

### 语义色（强制）

| 用途 | 约定 |
|------|------|
| 特权状态卡·可用 | 浅色容器 `#DFFAE4`、图标 `#34C759`；深色 `QuietaColors.StatusGreenDark` `#163D25` |
| 特权状态卡·检测中 | 浅色 `#F0F0F1`、图标 `#8E8E93`；深色用 `surface` |
| 特权状态卡·未就绪 | 浅色 `#FAEEEE`、图标 `#FF3B30`；深色用 `errorContainer` |
| 说明 / 提示卡 | `primary.copy(alpha = 0.2f)`，文字 `primary`（跟主题，不写死蓝） |
| 规则过宽等警告文案 | 琥珀字 `#B45309` 等，不占用状态绿 |
| 多选圆点 | 当前实现固定 `#3482FF`（选中）/ 同色 35%（半选）——品牌固定色，不声称已跟主题 primary |
| 关于页标题/版本 | 浅 `#7A4A6E` / 深 `#E8C4DC`；版本浅 `#6B5A72` 等，属 aurora 页特例 |
| 应用图标绿色 | 品牌资产，**不是** UI 强调色；绿色语义仅限特权「可用」状态卡 |

禁止把状态绿挪作通用成功色，或把状态红/灰挪作普通卡片底。

### 页面骨架

- 主导航四栏 `HorizontalPager` + 底栏浮层；二级页独立栈，底色 `MaterialTheme.colorScheme.surfaceContainer`。
- 通用列表页使用 `ui/component/QuietaPage`：miuix `TopAppBar` + `MiuixScrollBehavior`（大标题收拢为居中小标题）；`overScrollVertical`、关系统 overscroll；状态栏 inset 仅由顶栏处理。
- `QuietaPage` 默认：水平 **12dp**、顶 12dp、底 **110dp**（给浮层让位）、条目间距 12dp；设置/主题/规则编辑常设 `itemSpacing = 0` 自管分区。
- **间距（强制）**：已在 `QuietaPage` 内的分组卡**不得再叠** `padding(horizontal = 12.dp)`。**特权页**为范本：`QuietaPage` 水平/顶部 padding 置 0，卡片自行 `horizontal = 12.dp`。配置/静音预览等 Tip 若仍叠边距，属待收敛例外，**新代码一律贴页边距**。
- 预测性返回默认动画 **Miuix**、退出方向默认 **始终向右**；仅「缩放」消费退出方向；卡片式返回可出现 **32dp** 圆角裁切（`QuietaRoot`）。
- 独立可点卡：`PressableCard`（miuix `addSquircleRect` + Tilt + 按压着色，避免首帧 SDF 解码）；分组内选项整行反馈，不缩放单行文字；**展开后的信息卡用静态 Card，不要整卡 Tilt**。
- 状态卡刷新保留上次结果，检测期间禁写，检测结果一次提交；状态文案区和操作区预留稳定尺寸。
- 配置页规则卡提供编辑入口；修改保留规则 ID、顺序、启用状态及未编辑字段，通过共享仓库实时通知主页。

### 控件体系（目标态 + 现状）

- **目标（长期）**：控件优先 miuix（`top.yukonga.miuix.kmp.basic.*`：Card / Switch / Text / TextField / Button / SearchBar / BasicComponent / FAB 等）；新代码能 miuix 不扩 M3。miuix 无对应物时才用 M3（系统分享、部分 Dialog、既有列表壳等），并在改动说明原因。
- **现状（允许保留，勿倒退）**：设置/主题分组壳、主页权限动作条、列表静态卡、搜索 `OutlinedTextField`、部分 `IconButton`/`Text` 仍为 Material3 + 主题 token。收敛时优先改交互控件，不把已稳定列表整页重写成半成品。
- 公共组件（强制复用，业务页禁止另写一套）：

| 组件 | 职责 |
|------|------|
| `QuietaPage` | 二级/主栏列表页骨架 |
| `PressableCard` | 可点独立卡（squircle + Tilt） |
| `QuietaSwitch` | miuix Switch 包装（设置/配置/主页开关） |
| `HyperOsPopup` | 轻量弹出菜单 |
| `FloatingBottomBar` / `FloatingSelectionBar` | 底栏与多选操作条 |

- 字号/字色只走主题 token；硬编码仅限上表语义色。

### 字体（以 token 为准）

真相源：`QuietaTypography` + `QuietaTextStyles`。不得给所有标题统一加粗或添加负字距。

| 角色 | 样式 |
|------|------|
| 页头 / TopAppBar 大标题体系 | `displaySmall` 32sp Normal |
| 状态卡主标题 | `headlineSmall` 20sp SemiBold |
| 状态卡明细 | `QuietaTextStyles.statusDetail` 14sp Medium |
| 统计卡标签 / 数值 | `statLabel` 15sp Medium / `headlineMedium` 26sp SemiBold |
| 列表与设置行标题 | `titleLarge` 17sp Medium |
| 分区标题 | `titleSmall` / miuix `SmallTitle`，灰 `#8E8E93` |
| 描述 / 设置摘要 | `bodyMedium`·`bodySmall` 14sp Normal |
| 记录副文案 | `bodyLarge` 16sp Normal |
| 规则标题 | `QuietaTextStyles.ruleTitle` 18sp Medium |
| 弹层菜单行 | `bodyLarge` 16sp |
| 底栏 tab | `labelSmall` 11sp Medium |
| 关于 hero / 版本 | 35sp Bold / 14sp（页内特例） |

### 底栏与浮层

- 实现集中在 `ui/glass`（含 `liquid/*`，参考 Kyant0/AndroidLiquidGlass），**不散落在业务页**。
- 档位由 `resolveBottomBarMode(blurEnabled, liquidGlassSupported)` 决定，**无**单独 `allowShader` 用户开关：

| 档位 | 条件 | 观感 |
|------|------|------|
| LiquidGlass | `blurEnabled` 且 API≥33 且 `isLiquidGlassSafe()`（非模拟器 + RuntimeShader 可用） | 悬浮胶囊 + lens/vibrancy；容器 `surfaceContainer` 约 40% 透明 |
| Blur | `blurEnabled` 但玻璃不安全/不支持 | 半透明模糊胶囊，容器约 65% 透明 |
| None | 用户关闭「使用模糊」 | 实色胶囊 |

- `blurEnabled` 默认 **true**。HyperOS/模拟器上 RuntimeShader 或 LayerBackdrop 可能 HWUI SIGSEGV——按 `isLiquidGlassSafe()` **回退到 Blur**，不是把产品默认改成「玻璃关闭」。About 页等内容层 LayerBackdrop 另有规避，勿随意包全页。
- 视觉：选中 pill 弹簧/阻尼滑动 + InstallerX 高光（可随设备倾角旋转）；tab 单元 `minWidth 76.dp`；底栏距导航条约 14dp；选中色 `primary`。
- **多选操作条**与主底栏同构：`FloatingSelectionBar`，共用 `FloatingBottomBarMode` + 页面 `Backdrop`；高度约 64dp，图标+文字，可用 `emphasized`；多选时隐藏主 `FloatingBottomBar`。禁止业务页另写普通 Card 操作条。

### 圆角刻度

| 场景 | 圆角 |
|------|------|
| 应用图标 / 小图 | 8dp |
| 搜索框 | 14dp |
| `PressableCard` 默认、多数信息卡 / M3 Card | 16dp |
| 状态大卡、App/记录折叠卡、设置/主题分组壳 | 20dp（调用方指定或 SettingsGroup） |
| `HyperOsPopup` | 22dp |
| 关于玻璃卡 | 16dp |
| 预测返回卡片式裁切 | 32dp（导航层，非列表卡） |

「二级页分组 20dp」指设置/主题等分组壳；独立可点卡默认 16dp，列表/状态卡可显式 20dp。

### 按钮与列表动作（强制）

| 层级 | 用途 | 实现 |
|------|------|------|
| 主操作 | 确认静音、完成、整应用静音、保存 | miuix `Button` 实心，一屏优先一个主钮 |
| 主页门禁动作 | 请求授权、按规则静音、刷新、撤销 | 主页动作卡内图标 +（当前）M3 Button；刷新/撤销用图标，静音为主按钮 |
| 次要文字 | 整应用恢复等 | 灰字次级按钮；关闭/重置优先图标 |
| 行内信息/可点计数 | 命中 N、匹配摘要 | pill 或副文案；**不要**做成 TextButton |
| 行内多动作 | 单渠道静音/降级/恢复 | **收进弹层或底部浮层**，列表行只留状态 + 可点 |
| 图标操作 | 编辑、删除、筛选、更多、关闭 | 行内 24–28dp 图标；顶栏动作最多 1–2 个 |

- 分组规则/选项：一张 Card 多行 + `HorizontalDivider`（特权/配置/规则编辑同构），不要一行一张孤立卡。
- 弹层/表单关闭：右上角 `Close` 或 `Check`（保存）；**不要**底部「取消+确定」双文字钮并排。

### 二级页 / 弹层形态（InstallerX，强制）

| 场景 | 约定 |
|------|------|
| 可发现操作入口 | 图标：筛选 `Tune`、更多 `MoreVert`、刷新 `Refresh`、撤销 `Undo`、多选 `Checklist`、关闭 `Close` |
| 轻量选择（筛选/排序/显示/渠道动作） | `HyperOsPopup`：`IntrinsicSize.Max` + `widthIn(max=280)`，**禁止**写死满宽；圆角 22dp；行 bodyLarge 16sp、垂直 12dp；Check 右缘对齐；点外/返回关闭；弹层内不放 Switch、不放底部主按钮 |
| 全屏/长表单（规则新增·编辑） | `QuietaPage` + 大标题；导航 `Close`、动作 `Check`（可用时 primary）；字段独立 miuix `TextField`（垂直 6dp，**不要**再套 Card）；`SmallTitle` 分区 + 一张 Card 多行；单选动作用 Check，不用 Switch |
| 确认 / 选项 / 样本列表 | miuix `WindowDialog`（`ui/component/QuietaWindowDialog`，InstallerX `MiuixDialog`）：标题 + summary + 内容 Card/列表 + 底部 `TextButton`；**禁止**裸 `androidx.compose.ui.window.Dialog` + 自绘 Close 标题栏 |
| 静音预览等确认页 | 顶部说明卡 + 写入范围单选组（`BasicComponent` + 圆形 `Checkbox`）+ 底部全宽 miuix `Button` |
| 对话框（规则包导入等） | 右上 `Close`；选项用 `BasicComponent`；危险操作二次点击确认，不用双 TextButton |

禁止：列表头并排「展开/收起」文字钮；HyperOsPopup 内放 Switch；表单底部「取消/确定」文字钮。

### 设置 / 特权 / 关于 / 主题页

**设置列表：** `QuietaPage` + 分区标题灰 `#8E8E93` `titleSmall` + 分组卡 **20dp**、`surface`（当前为 M3 Card）；行标题 `titleLarge`、摘要 `bodySmall`、尾部 `ChevronRight` 或 `QuietaSwitch`；分组卡贴页边距，不再叠 12dp。

**特权页：** 两块 **primary 半透明**说明卡（非写死纯蓝）+ 单组圆形勾选（`BasicComponent` + miuix `Checkbox` + `selectableGroup`）；保留无特权 / ROOT / Shizuku / Dhizuku / 自动选择，不显示未实现的自定义提权命令。

**关于页（InstallerX，特例，不走 QuietaPage）：** `SmallTopAppBar`（首屏仅返回，标题随滚动）；AGSL `ui/effect/bg/BgEffectBackground` + 卡片 `textureBlur`（16dp，水平 12dp 页边距，卡片不叠边距）；hero 应用名 35sp Bold、版本 14sp；调试区 `SmallTitle` + 同构玻璃卡 + miuix Switch / `ArrowPreference`；hero 标志可点切换 aurora 填充 ↔ 品牌原色。**许可页等列表二级页不用 aurora**，仅标准 `QuietaPage` 表面。图标须透明底品牌标（`ic_about_logo`），禁止 adaptive 白底方块。

**主题设置页：** InstallerX 主题页子集；默认模式跟随系统。分组卡同设置页（20dp + 贴边距）；项用 miuix `WindowSpinnerPreference` / `BasicComponent` + `QuietaSwitch`：主题模式、使用模糊、自定义颜色、动态取色、调色板、Color Spec、种子色板（`PresetColors`）、预测性返回动画与退出方向。自定义色关闭时产品视觉保持出厂 HyperBlue 色板。

### 应用图标（已选定，强制）

| 项 | 约定 |
|----|------|
| 选定概念图 | `docs/icon-concepts/SELECTED-app-icon.png`（绿匣 + 三条蓝色通知卡；仓库内唯一正式源图） |
| 语义 | 通知条被收入「息匣」；勿再改回其它方向 |
| 打包 | **所有** debug/release 的 launcher 图标必须由该图（或由其导出的自适应图标分层）生成；禁止使用 Android 模板默认图标、禁止误用其他草稿（`v3-*`、`v4-b/c` 等） |
| 资源路径 | 步骤 1 起：`app/src/main/res/mipmap-*` / `ic_launcher*.xml` 自适应图标以本文件指定源图切图；切图脚本或说明放在 `docs/icon-concepts/` |
| 验收 | `assembleDebug` / `assembleRelease` 安装后桌面图标与 `SELECTED-app-icon.png` 一致（或为其清晰的自适应裁切） |

---

## 7. 性能与耗电（验收约束）

| 策略 | 要求 |
|------|------|
| 默认无常驻 | 安装后盘点/规则在打开应用时执行；不默认挂前台服务 |
| 事件驱动 | 用广播/Listener 回调增量更新；禁止定时全机扫描渠道 |
| 盘点缓存 | 渠道列表落盘（`ChannelInventoryStore`）；缓存异步显示，不等待特权检测；再后台限流刷新（仿 LibChecker），缓存不能作为授权凭据 |
| 启动线程 | Repository 构造器不得读写磁盘；初始化与写入串行，业务读取必须等待加载完成；特权探测、应用信息读取及规则计算不得阻塞主线程 |
| Binder 限流 | 盘点并发 ≤ 6；每 20 个包渐进刷新 UI，禁止数百路并行 Shizuku 调用 |
| 自动拦截可选 | 「新渠道自动静音」默认关；开启后才加强监听逻辑 |
| 时间线弱采集 | 默认只记包名/渠道/时间/条数，不记正文；滚动保留（如 7 天或上限 N 条） |
| Shizuku 生命周期 | 用时绑定，不用可断开；不在 Application 常连接 |
| 批量操作 | 协程限流分批调 Binder；可取消；失败记日志不无限重试 |
| UI | 列表稳定 key、Paging、避免 item 内 IO；玻璃 shader 尺寸不变不重编译 |
| 目标 | 冷启动快、空闲耗电≈0（无监控时）、200+ App 盘点可滚动不卡顿 |

### 日志、崩溃与备份

- 日志：默认仅本地/Debug；不记录通知正文与隐私内容。  
- 崩溃：不接第三方崩溃 SDK；可选「导出日志文件」供用户自提 issue。  
- 备份：`android:allowBackup` 默认 false；若开启，仅允许规则类配置，渠道缓存与日志不备份。

### 跨页状态与列表（强制，防复发）

违反下列任一条视为未完成改动：

| 规则 | 原因 |
|------|------|
| **共享数据源必须进程单例** | `RuleRepository` / `MuteLogStore` 等用 `getInstance(context)`；禁止各 ViewModel `new` 一份，否则配置页开关主页收不到 |
| **Lazy list `key` 必须是稳定唯一 id** | 禁止用展示文案 / 时间拼 key；同分钟静音日志会 `Key already used` 闪退 |
| **跨页 UI 只读共享 StateFlow** | 静音按钮等状态从 ViewModel StateFlow 派生；禁止缓存一次性 `first()` 结果当实时值 |
| **写路径要发 Flow** | `replaceAll` / `append` 先改共享 StateFlow 再落盘，保证订阅方立刻刷新 |

---

## 8. 工程约定

- 分支：优先 `main`（若本地仍为 `master` 则与远程默认分支保持一致）；功能 `feat/*`、修复 `fix/*`。  
- **远程**：`origin` = `https://github.com/1290000/Quieta.git`。  
- **自动提交（代理职责）**：在本仓库完成一步实质工作（阶段交付、约定变更、可编译骨架、功能）后，应在同一回合 `git add` 相关文件并 commit，消息用 `feat:` / `fix:` / `docs:` / `chore:` 前缀；在远程可达时 `git push origin <branch>`。禁止 force-push `main`/`master`；禁止提交密钥。用户明确要求「自动提交到仓库」时，以 push 到 origin 为完成标准之一。
- 版本：显示版本 `yy.MM.x`（如 `26.09.1`）+ 递增 `versionCode`。
- 分发：GitHub Release 侧载 APK；不以应用商店为首发。
- 测试：`core` 单测优先（规则引擎、探测降级）；UI 仪器测试只盖导航与金路径。
- 真机矩阵最小集：小米 / OPPO 或一加 / vivo / 荣耀或三星，各验证盘点、静音、拦截。
- 混淆：release 开启 R8；Shizuku / AIDL / 反射相关 keep 规则进 `proguard-rules.pro`。  
- CI：GitHub Actions 至少 `assembleDebug` + `check`（骨架可编译后再加 `assembleRelease` 若密钥已配好）。  
- 禁止：提交密钥；默认路径联网上报通知内容；未约定的大规模重构。

### 打包与签名（强制统一）

任何代理/人在本仓库打 **release APK** 必须走同一套流程，禁止临时自签、禁止换 applicationId、禁止私自改 versionCode 规则。

| 项 | 约定 |
|----|------|
| 产物 | `app/build/outputs/apk/release/Quieta-<versionName>-release.apk`（命名在 `app/build.gradle.kts` 统一） |
| 命令 | `./gradlew :app:assembleRelease`（Windows：`gradlew.bat :app:assembleRelease`） |
| 配置文件 | 仓库根 `signing.properties`（**不提交**）；模板见 `signing.properties.example` |
| 密钥库 | 路径写在 `signing.properties` 的 `storeFile`；**同一台机器/同一密钥文件**；禁止每次生成新 keystore |
| 读取方式 | `app/build.gradle.kts` 只读 `signing.properties` 或环境变量 `QUIETA_*`；缺省则 skip release 签名并给出明确错误信息，不静默用 debug 签名冒充 release |
| CI | 使用 GitHub Secrets（`QUIETA_STORE_FILE_BASE64`、`QUIETA_STORE_PASSWORD`、`QUIETA_KEY_ALIAS`、`QUIETA_KEY_PASSWORD`）；与本地同一密钥 |
| 校验 | 打完后 `apksigner verify --print-certs`，确认与约定证书指纹一致（指纹可写在本节 `releaseCertSha256`） |
| 禁止 | 把 `.jks`/`.keystore`/含密码文件提交进 git；用系统默认 debug key 发「正式包」 |

首次正式签名后，将 `releaseCertSha256` 填入本节；后续所有 release 必须一致，否则视为未按约定打包。

用户侧密钥保管：仓库外安全位置存放 keystore；`signing.properties` 仅本机/CI 使用。

---

## 9. 代码规范

### 分层

```text
UI (Compose) → UseCase / Engine → PrivilegeBackend / Repo
```

- Compose 不直接调 Shizuku、不写 Room SQL  
- Engine / core 不依赖 Compose  
- Backend 只实现系统能力，不写业务规则  

### 命名

| 类型 | 约定 | 例 |
|------|------|-----|
| Composable | 名词/动词 + Screen/Item | `HomeScreen`、`ChannelRow` |
| UseCase | 动词 + 名词 + UseCase | `BatchMuteUseCase` |
| Backend | 技术名 + Backend | `ShizukuBackend` |
| 文件 | 一文件一主类型 | `RulesEngine.kt` |
| 禁止 | 无意义缩写、`Util2`、`ManagerAll` | — |

### core

- 公共 API 以接口收口（如 `PrivilegeBackend`、`RuleRepository`）  
- 优先纯函数与 `suspend`，便于单测  
- 错误显式（`Result` 或明确异常），不静默吞掉  
- 避免可变全局单例滥用；状态放 ViewModel / Repo  

### app / UI

- Activity 只负责装配 `setContent { QuietaTheme { AppNav() } }`  
- 页面用单一 `UiState` + ViewModel，避免多个零散 StateFlow 拼界面  
- 通用组件放 `ui/component`；液态玻璃只出现在 `ui/glass`  
- 颜色/字号走主题 token，禁止散落硬编码颜色  
- 字符串资源化；列表 item 无业务、只展示与回调  

### 卫生

- `docs/` 只保留长期有效的 Markdown 文档及明确批准的资源/生成脚本，目录说明见 `docs/README.md`；非 Markdown 文件须在 `scripts/check_repo_hygiene.py` 白名单中登记用途。
- 临时截图、录屏帧、UI 层级 XML、图标草稿和上游源码检出放仓库外或根目录被忽略的 `artifacts/`，禁止堆入 `docs/`；移植完成保留来源与许可说明，不保留无用源码副本。
- 提交前暂存相关改动，再运行 `python scripts/check_repo_hygiene.py`；CI 对 Git 索引内路径执行相同检查，禁止以强制添加绕过目录规则。
- `!!` 仅在局部可证明非空时使用  
- 禁用 `GlobalScope`；用 `viewModelScope` / 明确 Job  
- 公共类型写一句 KDoc；私有实现少写过程注释  
- 本地跑通 `./gradlew check`（含 ktlint/detekt，若已接入）后再认为可提交  

### 先立样板再铺功能

1. `PrivilegeBackend` + `FakeBackend`（单测）  
2. 一处 `Screen` + `ViewModel` 串通依赖  
3. `FloatingBottomBar` 三档空实现  

新页面复制同一模式，避免各写一套架构。

---

## 10. 文案与命名

- 应用名：息匣；英文：Quieta。  
- **作者**：`1290000`；关于页必须展示。  
- **本应用仓库 URL**：`https://github.com/1290000/Quieta`；「查看源代码」「检测更新」均基于该 URL（Release 页 / API）。  
- 避免「管家」「大师」「清理加速」等词；定位为「通知策略 / 降噪工具」。  
- 界面语言：优先 `zh-CN`；`values/`（英文）作兜底字符串。  
- 设置与引导文案按 ROM 与能力探测动态展示。

关于页更新行为约定：

- 「检测更新」为**用户手动触发**；联网仅用于拉取公开 Release 元数据，不上传设备标识。  
- 有新版本时提示版本号并提供「打开 Release 页」；**不做**后台轮询、静默下载、强制安装。  
- 无网络或仓库 URL 未配置时优雅降级（提示打开浏览器或配置未完成）。

---

## 10.1 开源依赖披露（硬性）

引入或移除任何第三方开源库/资源时，必须同步维护应用内「开放源代码许可」页：

1. **覆盖范围**：Gradle 依赖中会打进包或运行时使用的库；复制进源码的片段须在页内注明上游项目与许可证。  
2. **每项至少包含**：名称、版本（能取到则显示）、作者/组织、许可证、**可点击的仓库或官方主页 URL**。  
3. **跳转**：点击条目用系统浏览器 / Custom Tab 打开对应仓库（如 GitHub），不得只显示纯文本无法跳转。  
4. **实现**：优先 AboutLibraries 等自动收集；不足时用自维护 JSON 补 URL，并与 `libs.versions.toml` 同步。  
5. **验收**：新增依赖的 PR/改动若未出现在该页，视为未完成。  
6. **移植代码的递归标注（强制，不限 InstallerX）**：从**任意**应用/项目搬来片段时，除标注该来源本身外，还必须把该片段内部引用的其它开源项目**单独**列入许可页（名称 + 许可证 + URL）。例：经 InstallerX 移植的动画若源自 KernelSU，则须单独标注 KernelSU，不能只写 InstallerX。液态玻璃同理须标注 Kyant0/AndroidLiquidGlass 等。

---

## 11. 本文件（AGENTS.md）的维护规则

本文件是**长期有效的工程与产品约定**，不是会话记录、不是变更日志、不是灵感草稿。任何人类或 AI 修改时必须遵守：

### 只保留什么

1. **仍有效的约定**：产品边界、技术栈、模块/依赖、接口形态、验收约束、规范。  
2. **将做或正在做的决策**（已拍板、会影响后续实现的）。  
3. **与代码必须一致的结构**（路径、包名、模块名）——代码改了这里要同步，而不是另起一段历史。

### 禁止写入什么

- 会话过程、讨论经过、选项对比、「当时我们聊了…」  
- 已废弃方案的长篇保留（废弃只需一行「曾考虑 X，已否决，改用 Y」）  
- 与约定无关的教程、API 摘抄、大段示例代码（示例仅保留接口级、极短）  
- 堆砌日期的「进度日志」「完成清单」  
- 重复表述同一规则（合并到唯一章节）

### 修改方式

| 动作 | 做法 |
|------|------|
| 新增约定 | 插入所属章节；没有合适章节再新建，不优先新建 |
| 变更约定 | **原地改写**该条，不追加「更新：…」 |
| 废弃约定 | 删除或一行说明「已由 X 替代」；不保留完整旧文 |
| 冲突 | 以更具体、更新的**产品决策**为准，并改到全文一致 |

### 体量与语气

- 以表格、短列表、一句话规则为主；单章节能说清就不要拆成附录。  
- 全文应能被一次上下文读完；目标是「可执行的规范」，不是「说明书小说」。  
- 若某节超过约一屏且开始写「注意事项的注意事项」，应拆到 `docs/` 或实现里，本文件只留指针。  
- 变更产品/架构约定时：**先改本文件，再改代码**；纯实现细节不必写入。

### 违规即视为无效改动

若一次修改使本文件变成日志、对话摘要或互相矛盾的多版本并存，应拒绝该修改并按上表收敛为单一真相源（single source of truth）。

### 何时必须自动改本文件（代理职责）

在本仓库工作的任何代理（含 AI）在完成实质工作后，**应主动检查并必要时更新本文件**，不必等用户点名「更新 AGENTS.md」。触发条件：

| 触发 | 动作 |
|------|------|
| 实现导致**产品范围**变化（新做/不做/砍掉 MVP 项） | 原地改 §1，保持与代码一致 |
| **模块、包名、applicationId、SDK、依赖方向**变了 | 原地改 §2–§3 |
| **提权后端、权限、ROM quirk、能力探测**行为变了 | 原地改 §4–§5 |
| **底栏/主题/导航**等 UI 约定变了 | 原地改 §6 |
| 性能策略变化（例如开始默认常驻） | 原地改 §7，并与产品决策一致 |
| 分支、版本、测试、发布方式变化 | 原地改 §8 |
| 新增/废弃编码约定，或样板模式变了 | 原地改 §9 |
| 新增/移除开源依赖 | 更新 §10.1 所述应用内许可页数据源，保证可跳转 |
| 作者名或本应用仓库 URL 确定/变更 | 原地改 §10 待填项，并同步关于页字符串/常量 |
| 发现**文档与代码不一致** | 以已合入的代码与已拍板决策为准，改文档或改代码，禁止只改一边留隐患 |
| 仅 bugfix、重构且约定未变 | **不要**为了「刷存在感」改本文件 |

改完应做一次全文自检：无重复章节、无过期路径、无「更新日志」语气。单次变更尽量小步、可审阅。

### 与实现的关系

- 约定变更：**先改本文件，再改代码**（紧急修复可先改码，同一回合内必须回写本文件）。  
- 纯实现细节、未定稿讨论、实验分支：不写入本文件。  
- 本文件与代码冲突时，视为流程失败：以产品意图 + 已验证可运行的代码为准，立刻收敛文档。
