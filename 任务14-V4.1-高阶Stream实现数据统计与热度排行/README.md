# 任务14 V4.1 高阶Stream实现数据统计与热度排行

状态：自测通过，待用户验收。2026-11-13 已补充全局 back 返回；原阶段验收历史保留，本轮需用户复验，尚未提交。

前置版本：[任务13 V4.0](../任务13-V4.0-MVC架构/README.md)。初版继承用户验收的任务 13（源码 `028a216`、README `ba1a743`）；用户随后要求两阶段同时按原文重做，本轮以修正后的任务 13 工作区为基线同步调整。两阶段修正版均须重新验收，无新的提交号。本项目可独立编译、运行。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。以下目标与验收摘自原文。

## 迭代目标

基于已完成的 MVC 架构、预约业务、状态机，不新增业务功能，利用 Stream 高阶 API（分组、聚合、count、sorted、limit、收集器）完成业务数据统计、资源热度排行；把多轮循环汇总统计的代码重构为流式聚合，学习函数式编程的集合统计能力。 解决传统写法：循环遍历、手动维护 Map 计数器、代码冗长易错的问题。统计结果封装为 Result 返回，在管理员控制台菜单展示统计报表。

## 验收标准（原文）

- 存在ResourceStatDTO数据传输对象；
- 4 项统计功能全部使用 Stream 高阶 API 实现，不使用 for 循环 + HashMap 手动计数；
- 资源热度 TOP 排行可按通过预约数量降序展示，支持 limit 限制条数；
- 通过率计算正确，无除零异常；资源被删除时，报表显示 “资源已删除”；
- 管理员可以查看全部统计报表；学生菜单不开放统计报表入口；
- 所有统计方法返回 Result，异常友好提示，不打印堆栈；原有预约业务不受影响。

## 实现与测试记录

任务 13 已分离控制台交互和业务，但还没有统计报表。任务 14 初版沿用了任务 13 遗漏专门包和服务类名的结构；本轮同步修正为原文要求的 `enums`、`common` 和 `ReserveRecordService`，更新源码、测试和文档引用。在正确的 MVC 结构中保留四类流式统计，经 `ReserveController` 返回管理员视图。

统计实现使用 JDK 8 的 `Collectors.groupingBy()`、`counting()`、`sorted()` 和 `limit()`；不使用原文中的 `summarizingCount` 示意写法。DAO、单例工厂、原预约方法和文件格式继承本轮任务 13；删除新增统计块后，预约服务与任务 13 一致。

| 必做要求 | 实现位置 | 验证方法与当前结果 |
| --- | --- | --- |
| 继承修正后的完整 MVC 包结构 | `enums`、`common`、`service/ReserveRecordService` 等 | 必需类、包声明、旧路径清除、枚举实际使用及 MVC 边界检查通过 |
| 资源统计 DTO，含全部字段、构造器、getter/setter/toString | `entity/ResourceStatDTO` | 已实现；字段、默认比率、DTO 与业务数据隔离通过 |
| 按资源分组、全部状态计入总数、正确通过率 | `service/ReserveRecordService` | 多资源、多状态、零通过、空数据、删除资源通过 |
| PASS 降序 TopN，使用 sorted 和 limit | `service/ReserveRecordService` | 同热度、超过总数、非法条数通过 |
| 按申请人分组，统计总提交、通过、驳回 | `service/ReserveRecordService` | 多用户混合记录及各报表总数核对通过 |
| 四种状态全局汇总，缺失状态显示零 | `service/ReserveRecordService` | 混合与空数据通过，状态顺序为 PENDING / PASS / REJECT / CANCEL |
| 管理员四个入口、学生不可访问、Result 友好提示 | `controller`、`view/AdminView` | 菜单、服务与控制器越权、非法输入及 EOF 通过 |
| 实时计算、无报表持久化、原业务不变 | 原 MVC 包和原 DAO | 提交、审核、取消、删除后重查通过；旧业务回归及 14 组前后版本会话一致 |
| 继承业务数据并独立运行 | 本任务自己的三个业务文件 | 已备份并迁移 2 个账号、4 条资源、1 条预约；四次独立 JVM 校验通过 |

统计以当前内存预约记录为依据，资源报表保留已删除资源的历史分组并显示“资源已删除”；无预约的资源不产生分组，空集合返回空列表。TopN 必须为正整数，相同通过数按资源 ID 排序以便复核。用户和状态报表使用各自 DTO 表达，不将申请人或状态混填为资源字段。通过后取消的预约按当前 CANCEL 状态计入，不累计历史 PASS 次数。

本阶段未要求新增 UML，[任务 13 的原生 VPP 和四张图](../任务13-V4.0-MVC架构/README.md#uml-交付物)保留在原目录，作为上一阶段架构记录；本次不把旧图冒充新增 DTO 后的完整类图。

## 实现与架构

继续使用 `View → Controller → Service → DAO`，不引入框架、Maven 或额外架构层。完整继承任务 13 原文要求的包结构，统计 DTO 加入 `entity`：

```text
src/com/campus/reservation/
├── SystemMain.java       启动装配
├── view/                 MainView、AdminView、StudentView、ConsoleView
├── controller/           三个控制器及 ControllerSupport
├── service/              UserService、ResourceService、ReserveRecordService、Operations 接口及 BusinessRules
├── dao/                  UserDao、ResourceDao、ReserveRecordDao、DaoFactory
├── entity/               三个业务实体及 ResourceStatDTO、UserStatDTO、StatusStatDTO
├── enums/                ReserveStatus、ResourceStatus
├── common/               Result、BusinessException
├── exception/            原有业务异常子类
└── utils/                FileUtil
```

资源实体的状态继续使用 String，Service / DAO 用 `ResourceStatus.name()` 对接，文件数据无需格式转换。统计实现位于 `ReserveRecordService`；`ReserveOperations` 和 `ReserveController` 增加四个同名入口：

| 方法 | 返回数据 | 管理员菜单 |
| --- | --- | --- |
| `queryResourceStatistics(user)` | `Result<List<ResourceStatDTO>>` | 9.资源预约统计 |
| `queryHotResources(user, topN)` | `Result<List<ResourceStatDTO>>` | 10.热门资源TOP排行 |
| `queryUserStatistics(user)` | `Result<List<UserStatDTO>>` | 11.用户预约统计 |
| `queryStatusStatistics(user)` | `Result<List<StatusStatDTO>>` | 12.全局状态汇总 |

`groupingBy` 按资源、申请人或状态分组，`counting` 聚合数量，`map` 映射 DTO，`sorted` 和 `limit` 输出 TopN。资源名称使用 Stream 和 Optional 查找，缺失显示“资源已删除”。通过率保存为 0–1 的 double，View 展示两位小数百分比；资源统计按 ID 排序，热度按通过数量降序、ID 升序排序。统计只读 DAO 当前快照，每次调用重新计算；DTO 没有对应持久化文件。

统计服务独立校验管理员身份，学生、空身份和无效角色返回失败 Result；控制器只转发，View 只解析条数并展示，不聚合业务数量。统计变化处已补充精简中文注释。原 DAO、工厂、状态机、工具和已有业务实体保留，原预约业务方法与单例初始化通过差异检查。

## 编译运行

实际验证环境：Oracle JDK `1.8.0_341`，本机路径 `D:\jdk8`；Windows PowerShell 5.1。程序只依赖 JDK，Python 仅用于隔离测试脚本。

在本任务目录运行：

```powershell
.\run.ps1 -JdkHome 'D:\jdk8'
```

从仓库根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File '.\任务14-V4.1-高阶Stream实现数据统计与热度排行\run.ps1' -JdkHome 'D:\jdk8'
```

已配置 `JAVA_HOME` 时可直接运行 `.\run.ps1`。入口为 `com.campus.reservation.SystemMain`，正常标题为 V4.1。OpenIDEA 打开仓库或本任务目录后选择 **任务14：编译并运行**；本机 IntelliJ IDEA 已添加 **任务14-V4.1-高阶Stream实现数据统计与热度排行：运行**，使用 `task14` 模块与本任务工作目录。IDE 图形按钮待用户复测；本机 `.idea` 配置继续被 Git 忽略。

正式数据位于本目录 `user.txt`、`resource.txt`、`reserve.txt`。使用迁入账号及原密码；默认管理员规则沿用上一阶段，普通注册为 student。`samples/` 仅供演示和隔离自测，不要覆盖正式数据。

## 数据迁移

2026-11-11，从任务 13 迁入 **2 个账号、4 条资源、1 条预约**。DAO 与文件格式保持一致，源文件编码、列数、ID、时间、状态和关联按原值完整复制。目标原先没有业务文件，无冲突、无未迁移项。

迁移前检查应用已退出，只有可识别的 IDE 构建进程；备份在本机忽略目录 `data/migration-task13-e8eb6322f77c4917ad5391405039c280/`，包含 `source/`、`target/`（原先为空）和 `validation/`。先在隔离副本中执行两次独立 JVM 加载、登录、查询、关联及报表核对，写入目标后再启动两次；每次原业务 8 项、报表 6 项检查通过。源文件未修改，迁入文件与备份字节一致。

本地辅助脚本 `data/migrate-task13.ps1` 对相同文件重跑不重复导入，目标有不同内容时拒绝覆盖并保留备份供核对；迁移不是应用启动步骤。正式数据与备份均被 Git 忽略，验收继续使用当前独立数据副本。

本轮重做保留当前任务自己的数据，未用任务 13 再次覆盖。修正前备份在 `data/package-rework-c8905800d4564bc1ba79fec474c6fa2d/`；使用修正后的编译产物在隔离副本中两次启动独立 JVM，每次原业务 8 项、报表 6 项均通过。仍有 2 个账号、4 条资源、1 条预约，从根目录和任务目录分别启动退出后，三个正式数据文件哈希与修正前一致。无冲突或未迁移项。

## 实际自测

在本任务目录执行：

```powershell
.\test.ps1 -JdkHome 'D:\jdk8'
```

| 测试 | 2026-11-11 实际结果 |
| --- | --- |
| JDK 8 编译 | `javac -encoding UTF-8 -Xlint:all` 通过 |
| `SingletonTest` | 15 项通过；单例身份、私有构造、只加载一次 |
| 三种文件损坏后的启动 | 均给出对应文件提示，退出码为 1 |
| `RegressionTest` | 103 项通过；登录注册、权限、资源、预约和 IO 回滚 |
| `StateMachineTest` | 108 项通过；审核、取消、越权、状态矩阵和恢复 |
| `MvcTest` | 26 项通过；原控制器业务和异常边界 |
| `StatisticsTest` | 72 项通过；分组、比率、排序截断、权限、只读性、实时状态和删除资源 |
| `verify-cli.py` | 7 次会话、30 项通过；原有控制台业务 |
| `verify-mvc.py` | 151 项通过；完整目录契约、源码保留、MVC 边界、Stream 约束及 14 组任务 13/14 配对会话 |
| `verify-statistics.py` | 11 次会话、53 项通过；四报表、百分比、非法 TopN、EOF、学生菜单和空数据 |
| 现有数据兼容验证 | 本轮隔离副本两次独立 JVM，每次 14 项通过；原始迁移记录保留 |
| 正式启动脚本 | 从根目录和任务目录分别启动 Windows PowerShell 脚本，显示 V4.1，输入 0 正常退出 |
| 历史数据保护 | 本轮 181 个受保护文件哈希一致，含任务 01–12 源码、历史 UML 及任务 13 / 14 正式数据 |
| 文档与启动配置 | 本轮 112 个本地链接、3 份 OpenIDEA TOML 及本机 IDEA XML 解析检查通过；IDE 图形按钮待用户复测 |

前后版本比较只归一版本号、动态 UUID、时间和新增菜单项；比较原业务输出、退出码及三个数据文件。所有业务测试使用 `data/` 中的隔离样本。本轮改名时，旧路径断言误指向新类名，已修正为检查 `service/ReserveService.java` 不存在；随后完整 `test.ps1` 成功退出。

## 用户验收步骤

1. 启动 V4.1，用原管理员登录，查询原资源及预约，确认迁入数据可见。
2. 依次选择 9、10、11、12，TopN 输入 `10`；预期四类报表正常显示，热度按通过数降序，状态汇总始终列出四种状态。
3. 在 TopN 输入 `0`、`-1`、`abc` 或 `2147483648`；预期友好提示，仍能继续选择报表。
4. 用学生登录，预期没有统计入口；直接输入 9–12 也只提示无效选项。
5. 管理员新增测试资源，学生申请预约（例如 `2030-01-02T10:00` 至 `2030-01-02T11:00`），管理员审核 PASS，学生取消；每次重新查报表，预期总申请、通过、取消和状态数量随当前记录即时变化。
6. 取消后管理员删除该测试资源，资源报表应保留该 ID 并显示“资源已删除”；退出到首页输入 0，重新运行后再次查询，记录及统计仍可复现。

上述手工操作会写入任务 14 自己的数据。用户验收：待反馈。IDE 图形运行按钮尚未代为点击；本次未提交、未推送，未开始任务 15。

## 全局返回补充与复验（2026-11-13）

本轮基线为当前提交 `8f07a0c`。原交互在填写表单时缺少取消入口，只能继续输入或结束程序；现按用户明确要求跨阶段补充 `back`，实现位于 `view/ConsoleView.java`、`MainView.java` 和同包的 `BackNavigation.java`，保留本任务原有结构与业务规则，并补充精简中文注释。这是用户追加的交互完善，两份 DOCX 未禁止返回菜单，不将它写成教师新增要求；上文验收与自测属于历史，本轮状态以本节及页首为准。

注册、登录及当期已有表单的任意输入处输入 `back`，取消本次尚未提交的内容并返回所属菜单；首页输入 `back` 保持首页，原数字菜单和 `0` 保留。角色菜单输入 `back` 退出登录并回到首页。列表和统计展示后自动回到角色菜单。 命令仅识别小写，忽略前后空白；`BACK`、`back123` 仍为普通文本。输入 `\back` 表示字面文本 `back`，连续前导反斜杠仅去掉一层，其他输入保持原样。提示及转义说明仅在启动时显示一次。

| 本轮检查 | 实际结果 |
| --- | --- |
| 独立编译与运行 | Oracle JDK `1.8.0_341`，`D:\jdk8`，`javac -encoding UTF-8 -Xlint:all` 通过 |
| 导航与取消 | 47 次独立控制台会话、274 项断言通过，覆盖 27 个表单输入点 |
| 提示、转义及原行为 | 单次启动提示、大小写/转义、取消后继续操作、输入结束等通过；有持久化的阶段同时验证取消不写文件及重启可读 |
| 隔离测试记录 | `data/back-test-b0d9cad955374f9aac1ed7338aa6c209/result.json`；同目录保留编译与会话日志，不纳入 Git |
| 数据与业务保护 | 全仓库 273 个受保护业务源码、正式数据和脚本哈希一致；任务 15–20 未改动 |

在仓库根目录可复跑本阶段导航测试（Python 只用于测试，应用运行只需要 JDK）：

```powershell
python .\tests\verify-back.py --jdk D:/jdk8 --tasks 14
```

删除 `--tasks 14` 可测试全部任务 1–14；合计已通过 449 次控制台会话、2482 项断言，覆盖 216 个表单输入点。测试只使用新建的隔离演示数据。

另在本任务目录执行 `.\test.ps1 -JdkHome 'D:\jdk8'`，退出码为 0；单例 15 项、损坏文件启动、业务 103 项、状态机 108 项、MVC 控制器 26 项、统计 72 项、控制台 30 项（7 会话）、MVC 对照 154 项（14 组配对会话）、统计控制台 53 项（11 会话）通过。日志为 `../任务14-V4.1-高阶Stream实现数据统计与热度排行/data/back-navigation-e3904a3a2c9b4ba6b6b7c128f17eb8c6/regression-14.log`（本机忽略）。

修改前源码、说明、UML 及已有正式数据备份于 `data/back-navigation-e3904a3a2c9b4ba6b6b7c128f17eb8c6/`，该目录被 Git 忽略。各任务原有账号、资源及预约文件保持原样；本轮不改变持久化格式，也未开始新阶段，因此不重新覆盖或迁移数据，无冲突及未迁移项。

用户复验：启动后在首页输入 `back`，应仍显示首页；进入注册，在密码处输入 `back`，应回首页且不新增账号；使用原账号登录。在新增资源或预约等当前已有表单中途输入 `back`，应回角色菜单且没有写入；角色菜单再输入 `back`，应回首页。最后输入 `0` 退出，全程启动提示只出现一次。 如需验证字面值，在文本字段输入 `\back` 应作为普通 `back` 处理；格式受限字段仍按原规则校验。沿用上文原运行方式，IDE 图形运行按钮本轮未点击，留用户复测。当前为“自测通过，待用户验收”，未暂存、提交或推送。

[返回任务索引](../README.md)
