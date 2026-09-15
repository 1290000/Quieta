# 启动与后台盘点

## 来源与适配

参考 LibChecker（Apache-2.0），源码版本 `54961716cdaec13cad4b73eebf3ad8fd3b229a6b`：

- `app/src/main/kotlin/com/absinthe/libchecker/data/app/LocalAppListRepository.kt`：Room DAO 的 Flow / suspend 读取接口。
- `domain/home/presentation/HomeViewModel.kt`：初始化与增量同步在 `Dispatchers.IO` 执行。
- `domain/app/list/usecase/InitializeAppListUseCase.kt`：每 50 项入库，延后初始化重型特征。
- `domain/app/list/usecase/GetAppListContentUseCase.kt`：先读本地内容，首批只构建 32 项重型展示状态。

仓库：https://github.com/LibChecker/LibChecker

Quieta 保留现有 JSON 存储，没有照搬 Room 或 LibChecker 的 APK 特征扫描。借鉴的是异步数据源、缓存与扫描解耦、分批发布的职责划分。应用内许可页保留 LibChecker 的架构参考说明。

InstallerX Revived（Apache-2.0/GPL-3.0 代码来源按应用内许可页列出，源码版本 `f6ffcd8ea84e629bc14160414e7343f07a4d3d76`）的相关做法也已对照：能力状态由多个 Flow 合并，刷新在 IO 线程执行；启动页面先等待必要主题状态，而不是让每次状态探测把已显示内容清空；状态卡使用固定的三段文本结构。Quieta 因为需要显示真实授权结果，启动时只复用上一次成功结果作为临时展示，写操作仍在本次探测完成前禁用，失败后立即显示不可用状态。

## 实现约束

- `AsyncLocalState` 在 IO 线程加载一次；构造器不读盘。界面订阅共享 StateFlow，业务通过 `current()` 等待加载完成。
- 初始化先于任何写入；写入通过 Mutex 串行执行，先发 Flow，再落盘，失败回滚。避免冷启动期间编辑规则或追加日志被加载结果覆盖。
- 主页独立加载渠道缓存，无须等待 Root / Shizuku / Dhizuku。授权仍重新探测；缓存可浏览但不代表写权限。
- 上一次成功的特权名称持久化到 DataStore，仅用于避免已知状态在首帧变成灰色；不把缓存当作本次授权凭据。
- 特权探测放 IO 线程并发执行；规则计算在 Default 线程执行，过期的计算结果不更新界面。
- 扫描并发上限 6，每 20 个应用检查是否发布，间隔至少 200ms，完成时立即发布。
- 导航容器不订阅主页全部进度；状态卡、设备卡只接收自身显示所需字段，避免每批盘点重组底栏及无关内容。
- 不增加周期扫描、延迟定时启动或常驻 Root 服务；现有服务空闲释放策略不变。
- 状态卡按 InstallerX 的实际结构使用 16dp 内边距、20sp 标题、14sp 摘要、36dp 中间间距和自然高度；不额外用双行文本或固定大高度撑开布局。

## 验证方法

1. `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest check`；`AsyncLocalStateTest` 覆盖异步加载、初始化期间修改、并发修改及写失败回滚。
2. 在同一设备、同一 APK 类型下，`am force-stop` 后运行 `am start -W`，重复多次；首次安装启动与后续冷启动分开统计。
3. 同时检查 `dumpsys gfxinfo`、Choreographer 跳帧及缓存出现后的点击/滚动响应。少量启动帧的卡顿比例不能代表日常滚动帧率。
4. 方法采样会显著增加 debug 构建耗时，仅用于定位，不能与未采样启动时间直接比较。
5. Root 回归测试仍使用测试 APK 的临时渠道，不改用户应用渠道。确认完成后 Root 子进程退出。

仍需持续评估首帧 Compose 测量、文字绘制以及 miuix 圆角着色器首次初始化成本；缓存等待改善不能等同于首帧零卡顿。采样文件、截图和完整测试日志存仓库外，不提交到 `docs/`。

K40S / Android 14 的 debug 小样本中，排除安装后的首次启动，修改前两次冷启动为 1691 / 1031ms，修改后为 1655 / 1051ms，首帧没有显著改善，且仍有 Choreographer 跳帧。不能将此次异步数据路径修复描述为启动卡顿已彻底解决。24 项 core 单测、`check`、debug 构建及独立 Root 临时渠道的读写回读测试通过。
