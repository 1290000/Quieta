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
- 新建渠道自动按规则处理（可选开启）
- 通知时间线（摘要级，默认弱采集）
- 规则本地存储，可导入/导出 JSON（根字段 `schemaVersion`，便于迁移）
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

**已交付（摘要）：** MVP 盘点/批量静音/可选自动静音；Shizuku 盘点与 setImportance（列表路径 + create/update，K40s 25/25）；本地规则 JSON；弱采集通知时间线（包名/渠道/时间/数量，默认 7 天 / 200 条保留）；四栏 HorizontalPager 壳；关于/许可/检测更新；HyperOS 风格 UI；底栏 InstallerX 移植 + 液态玻璃（K40s/K90 观感已验收）。**未创建 GitHub Release / 未打 tag。**

**已约定、尚未完成 / 待验收（后续代理优先做）：**

1. ~~**液态玻璃观感验收**~~ — 已验收（K90 HyperOS）。  
2. ~~**批量静音真机验收**~~ — K40s / MIUI 14：25/25 成功；其它 ROM（ColorOS / OriginOS / MagicOS / One UI）仍待矩阵覆盖。  
3. **Root / Dhizuku 后端** — `DhizukuBackend` 使用 Binder；`RootBackend` 使用 libsu RootService 独立读写并回读校验，不依赖 Shizuku/Dhizuku。自动选择 Root → Shizuku → Dhizuku；不同管理器与 ROM 的真机覆盖仍需扩展。
4. ~~**通知时间线页**~~ — 已实现弱采集时间线与本地记录 UI；默认仅保存包名、渠道、时间、数量，支持 7 天 / 200 条滚动保留及设置页关闭。
5. ~~**二级页 Navigation**~~ — 已增加独立二级路由栈；主题设置、开放源代码许可、可用特权页均支持页面返回和系统返回，主栏状态保持不变。
6. **AboutLibraries 自动收集**：现为手写许可列表；插件生成 aboutlibraries.json 未接通。  
7. **release 签名与首个 Release**：`signing.properties` 流程已约定，未生成正式包、未发 GitHub Release。  
8. **记录页筛选**；**导出规则**仅配置页分享 JSON。

**一期关于页必做：** 展示应用名与作者；「查看源代码」跳转本应用仓库；「检测更新」手动检查 GitHub Release（可打开最新页，不做静默下载/强制安装）。

**二期：** 营销 vs 重要启发式归类、通知摘要、更多 ROM quirk、宽屏布局。

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

- 整体风格对齐 InstallerX Revived / 澎湃：浅灰底、白卡片、系统蓝 `#3482FF`、大标题；**绿色仅用于「运行中」状态卡**。
- 底栏：白色悬浮胶囊 + 弹簧动画选中胶囊；三档：

| 档位 | 条件 | 含义 |
|------|------|------|
| LiquidGlass | 代码已接入 lens；**当前默认关闭**（HyperOS RenderThread SIGSEGV） | 仅 `allowShader=true` 且 API≥33 非模拟器时启用 |
| Blur / None | 默认 | 实色/半透明胶囊 |

- 主色为系统蓝；绿色仅状态卡。  
- 主题默认**跟随系统**；深浅色均须可读。  
- 字体沿用系统默认及 miuix 文字样式：普通条目标题 17sp Medium、摘要 14sp Normal，状态标题 20sp SemiBold；规则标题 18sp Medium，记录摘要 16sp Normal。不得给所有标题统一加粗或添加负字距。
- 配置页规则卡提供编辑入口；修改保留规则 ID、顺序、启用状态及未编辑字段，通过共享仓库实时通知主页。
- 液态玻璃实现放在 `ui/glass`，不散落在业务页。  
- 底栏默认：支持则 LiquidGlass，否则 Blur；用户可在设置改为 None。  
- 一期手机竖屏优先。
- 顶栏统一使用 miuix `TopAppBar` + `MiuixScrollBehavior`（大标题收拢为居中小标题）；滚动容器使用 `overScrollVertical`，关闭系统 overscroll，触顶/触底按 InstallerX Revived 回弹。状态栏 inset 仅由顶栏处理。
- 可点击的独立卡片使用 miuix `PressFeedbackType.Tilt` + 按压着色；分组内选项保持整行反馈，不缩放单行文字。miuix 与 Material 共用息匣深浅色主题。
- 首屏独立卡片使用 miuix `addSquircleRect` 路径轮廓，保留 Tilt 与着色，避免首帧 SDF 贴图解码；状态卡刷新保留上次结果，检测期间禁写，检测结果一次提交；状态文案区和操作区预留稳定尺寸。
- 可用特权页采用两块蓝色说明区 + 单组圆形勾选项；保留无特权 / ROOT / Shizuku / Dhizuku / 自动选择，不显示未实现的自定义提权命令。

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
