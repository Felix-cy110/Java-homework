# 任务10 V3.1 三层架构重构

状态：自测通过，待用户验收。用户于 2026-11-10 明确验收任务 9 并授权开始任务 10；本轮未暂存、提交、推送或创建 PR。

前置版本：[任务09 V3.0](../任务09-V3.0-资源预约核心业务开发/README.md)。基线为 `fad293c`，其中任务 9 源码提交为 `b01dfe7`。本项目独立编译、运行，使用自己的数据副本。

需求依据：[项目任务列表.docx](../项目任务列表.docx) 任务 10 完整章节、[评分标准.docx](../评分标准.docx)；说明与待澄清项见[需求核对](../docs/需求核对.md)。

## 原有问题与本次方案

任务 9 已具备完整预约闭环，但 `SystemMain` 同时处理输入、权限、校验、集合变更和文件调用，修改一项业务需要阅读整个主类。本次从已验收源码迁出原有方法，按职责分层，保留菜单编号、提示、文件格式及业务规则；启动标题升级为 V3.1。

- `SystemMain`：仅装配 DAO、Service、UI，处理启动失败。资源服务与预约服务注入同一组 DAO，避免内存状态分叉。
- `ui.console.ConsoleUI`：输入读取、格式转换、菜单路由及 Result 展示；不读写文件、不修改业务数据。角色判断只控制菜单展示，权限由服务独立校验。
- `service`：登录注册、资源和预约规则，Stream 查询、Result 返回以及异常转换。原有共享校验迁入包内的 `BusinessRules`。
- `dao`：三个具体 DAO 分别维护用户、资源和预约集合，执行主键 CRUD、文件加载与保存。成功落盘后才更新缓存。
- `utils.FileUtil`：从旧 `util` 包迁入任务书指定的 `utils`，只保留创建文件、替换文件、BOM 处理及文本转义。
- `entity`、`exception`：17 个文件与任务 9 保持一致，继续使用原有实体、枚举和异常体系。

调用方向为 **ConsoleUI → 三个业务接口及其 Service 实现 → DAO → FileUtil**。实体与异常为共享基础类型，没有反向调用。装配入口创建各层依赖不属于业务跨层调用。

## 原文要求与验收清单

| 必做要求 | 实现位置 | 验证方法与结果 |
| --- | --- | --- |
| 固定 ui.console / service / dao / entity / exception / utils 包 | `src/com/campus/reservation/` | 30 个源码文件的包依赖检查通过 |
| UI 只做交互，业务和数据存取分离 | `ConsoleUI`、三个 Service、三个 DAO | 检查 UI 无 DAO/文件访问，Service 无控制台和直接文件操作，通过 |
| 业务功能完全不变 | 三个 Service、`BusinessRules` | 24 组任务 9/10 CLI 场景的输出、退出码及落盘数据一致；仅归一化版本号、随机编号和生成时间 |
| 保留 Result、自定义异常、Stream/Optional | service、DAO 主键查找 | 103 项原生 Java 断言通过，失败数据为空并有中文提示 |
| 接口隔离、依赖倒置 | `UserOperations`、`ResourceOperations`、`ReserveOperations` | UI 依赖三个按模块拆分的业务契约，构造器注入实现 |
| 三层 UML 核心包、类、字段、方法、可见性和依赖 | `docs/uml/` | PlantUML 图源及 SVG/PNG 已生成，125 个图示成员名与源码匹配，已目视检查文字和箭头 |
| 持久化数据独立继承 | 三个数据文件与本地迁移备份 | 2 个账号、4 条资源、1 条预约全部迁移；登录、查询、重启和关联验证通过 |
| 原生 Java，JDK 8+，独立运行 | `run.ps1`、OpenIDEA 配置 | Oracle JDK 1.8.0_341 编译及运行通过 |

任务书的单独包结构图是可选项，本次未增加。没有引入 Maven、Gradle、Spring Boot 或第三方运行框架，也未提前实现任务 11 的状态机、任务 12 的单例、任务 13 的 MVC。

DAO 在本阶段仍为具体类；接口隔离和依赖倒置具体应用在 UI 与业务契约之间，**不将当前实现描述为 Service 已依赖 DAO 接口**。后续 DAO 接口演进按对应阶段要求处理。

## 目录与 UML

```text
src/com/campus/reservation/
├── SystemMain.java
├── ui/console/ConsoleUI.java
├── service/
│   ├── UserOperations.java / UserService.java
│   ├── ResourceOperations.java / ResourceService.java
│   ├── ReserveOperations.java / ReserveService.java
│   └── BusinessRules.java
├── dao/
│   ├── UserDao.java
│   ├── ResourceDao.java
│   └── ReserveRecordDao.java
├── entity/       # User、CampusResource、ReserveRecord、ReserveStatus、Result
├── exception/    # 原有业务异常
└── utils/FileUtil.java
```

UML 分为主图和两张明细图，避免把所有成员挤在一张图中：

**Visual Paradigm 16.2 原生工程：[任务10-三层架构.vpp](docs/uml/任务10-三层架构.vpp)。** 2026-11-10 按用户指定的软件版本补充交付。原有 PlantUML 图源与 SVG/PNG 供文本维护和直接查看；新增工程将包、类、接口、枚举、核心成员与关系保存为可编辑的原生 UML 模型。

先启动用户指定目录中的 **Visual Paradigm 16.2**，通过 **Project → Open** 选择上述 `.vpp`，然后在图表导航器（Diagram Navigator）中打开以下三张图；主图较大，可放大查看字段和方法。交付此工程只需要该 `.vpp` 文件，无需导入 PlantUML、安装生成插件或依赖本机源码路径。

本机同时安装了 Visual Paradigm CE 18.0，Windows 的 `.vpp` 默认打开命令指向 18.0；直接双击文件或通过系统文件链接打开会调用该版本。2026-11-10 的实际反馈中，18.0 先自动升级了工程，导致随后使用 16.2 时提示版本过新。已从与原始交付哈希一致的自动备份恢复，并在用户正在运行的 16.2 桌面程序中打开主图、保存、关闭工程后重新打开验证通过。最终文件保留 16.2 格式；请从 16.2 内部打开，避免再次自动升级。未修改系统默认程序设置。

- `01 三层架构与调用关系`：入口装配、UI → Service → DAO、工具类及共享类型。
- `02 实体与返回结构`：实体核心字段和方法、Result 泛型返回、预约状态及逻辑外键。
- `03 业务异常继承`：BusinessException、11 个业务子异常及 JDK 父类。

原生工程基于本任务 30 个 Java 类型及现有三份图源建立，另含 `java.lang.RuntimeException`；共 31 个类型、127 个核心成员、40 条模型关系，三张图分别展示 26、3、12 条连接（有一条跨图复用）。同一类型在多张图中引用同一模型；这里只建模图示核心成员，不宣称包含源码全部成员。原有图源和预览继续保留，后续改动需同步维护两种可编辑格式。

| 内容 | 可编辑图源 | 矢量图 | 图片 |
| --- | --- | --- | --- |
| 三层架构、接口实现、DAO 依赖与集合关联 | [architecture.puml](docs/uml/architecture.puml) | [SVG](docs/uml/architecture.svg) | [PNG](docs/uml/architecture.png) |
| 实体字段、Result、预约状态及逻辑外键 | [models.puml](docs/uml/models.puml) | [SVG](docs/uml/models.svg) | [PNG](docs/uml/models.png) |
| BusinessException 与全部子异常 | [exceptions.puml](docs/uml/exceptions.puml) | [SVG](docs/uml/exceptions.svg) | [PNG](docs/uml/exceptions.png) |

`+` 为 public、`-` 为 private、`~` 为包内可见。实体完整 getter/setter 见源码；图中显示核心成员，接口上的业务方法由对应 Service 实现。

渲染环境：PlantUML 1.2025.0、其可用的 Graphviz 2.44.1、Microsoft YaHei 字体。绘图工具仅用于生成文档，不是项目运行依赖；JAR 位于本地忽略目录。可在本任务目录重新渲染：

```powershell
& 'D:\jdk8\bin\java.exe' -jar data/tools/plantuml-1.2025.0.jar -charset UTF-8 -tsvg 'docs/uml/*.puml'
& 'D:\jdk8\bin\java.exe' -jar data/tools/plantuml-1.2025.0.jar -charset UTF-8 -tpng 'docs/uml/*.puml'
```

重新克隆时需自行准备 PlantUML JAR 并调整路径；查看已交付 SVG/PNG 不需要安装绘图工具。

## 持久化与迁移记录

数据文件仍位于任务 10 根目录，均为 UTF-8、制表符分隔并支持原有转义：

| 文件 | 格式 | 迁移数量 |
| --- | --- | --- |
| `user.txt` | 账号、密码、角色、注册时间四列 | 2 |
| `resource.txt` | ID、名称、类型、状态、位置五列 | 4 |
| `reserve.txt` | ID、资源 ID、申请人、申请时间、起止时间、状态、备注八列 | 1 |

本次格式未改变，迁移直接保留源文件全部字节，不重建账号、ID、时间或关联。确认未有业务程序运行后，先备份源和空目标，再在隔离副本验证加载、所有账号登录、两种角色查询、重新启动及预约关联，最后写入目标并校验哈希。迁移无冲突、无遗漏；源文件及目标迁移后的文件在收尾核对时均未变化。

备份：`data/migration-20261110-task09-to-task10-verified/`，包含 `source/`、`target-before/`、`validation/` 和只记录数量/哈希的 `report.json`。真实业务数据与备份均被 Git 忽略，阶段间没有共享文件路径。

第一次回归脚本因 Windows PowerShell 5.1 解析无 BOM 的中文脚本发生异常，把演示数据写进了当时尚未迁移、没有业务数据的任务 10 根目录。迁移检查及时阻止覆盖，已将这些测试文件完整归档到首次测试目录。脚本改为 UTF-8 BOM，并显式设置 Java 子进程工作目录；重新执行 103 项断言通过，确认测试文件只在新隔离目录后才执行正式迁移。任务 9 数据未受影响。

`samples/` 沿用任务 9 的演示文件，仅供空白副本使用；不要用演示文件覆盖已迁移数据。

## 环境与运行

实际环境：Windows x64，Oracle JDK `1.8.0_341`（`D:\jdk8`）。本轮没有验证其他 JDK 版本；不将字节码兼容性等同于其他环境已测试。

在 OpenIDEA 选择“任务10：编译并运行”。本机 IntelliJ IDEA 已补充“任务10-V3.1-三层架构重构：运行”配置，使用已有 task10 模块；该本机配置被 Git 忽略。图形界面按钮操作尚待用户复测。

从任意目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\MyProject\Java-homework\任务10-V3.1-三层架构重构\run.ps1" -JdkHome "D:\jdk8"
```

脚本固定任务目录并递归编译。手动执行时：

```powershell
Set-Location 'D:\MyProject\Java-homework\任务10-V3.1-三层架构重构'
New-Item -ItemType Directory -Force -Path out | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
& 'D:\jdk8\bin\javac.exe' -encoding UTF-8 -Xlint:all -classpath out -d out @taskSources
& 'D:\jdk8\bin\java.exe' '-Dfile.encoding=UTF-8' -cp out com.campus.reservation.SystemMain
```

入口仍为 `com.campus.reservation.SystemMain`。保留默认演示管理员 `admin / 123456`，已有管理员不会被覆盖；学生继续使用任务 9 的账号或正常注册。

菜单与任务 9 相同：学生 2 查询资源、5 提交、6 我的预约、7 取消；管理员 1–4 资源 CRUD、5 全部预约、6 审核、7 筛选。预约时间例：`2099-01-01T10:00` 至 `2099-01-01T11:00`。

## 实际测试记录（2026-11-10）

| 检查 | 实际结果 |
| --- | --- |
| 独立编译 | `javac -encoding UTF-8 -Xlint:all -classpath out -d out ...` 退出码 0，30 个生产类，无编译警告 |
| 业务与失败恢复回归 | `test.ps1 -JdkHome D:\jdk8` 输出 `PASS: 103 checks`；包括注册/登录、CRUD、查询排序、角色边界、状态联动、时间冲突、删除保护、特殊字符、重载持久化、审核/取消写入失败补偿与重试 |
| 与已验收任务 9 对照 | `python data/compare_cli.py` 执行 24 组场景、两版本共 48 次独立 Java 启动；退出码、stdout/stderr、三个文件内容归一化后逐项一致 |
| CLI 异常路径 | 覆盖无效菜单、日期格式、空输入、EOF、文件损坏、重复预约 ID、管理员同名学生冲突、BOM 文件；保留原有友好错误 |
| 包依赖 | 检查 30 个源码文件，无 UI → DAO、Service → UI、DAO → Service 反向依赖；UI/Service 无直接文件操作 |
| 实体和异常继承 | 17 个源码文件与任务 9 内容一致 |
| 启动脚本 | 从仓库根目录和任务 10 目录启动并输入 0 均退出码 0；根目录、任务 9、任务 10 数据哈希保持不变 |
| 数据迁移 | 迁移副本两次独立启动与目标只读检查均为 2/4/1；全部账号可登录，资源/预约可查询，关联及文件哈希通过 |
| UML | 三份图源生成 SVG/PNG；125 个成员名核对通过，已查看三张 PNG，文字、包边界、箭头方向无缺失 |
| Visual Paradigm 原生工程补充（2026-11-10） | 使用本机 Visual Paradigm 16.2 原生接口生成 `.vpp`，在新的 Java 进程中重新打开，31 个类型、127 个核心成员的包名、可见性、参数、返回类型、静态标记及各图形坐标、关系类型和端点核对通过；原生导出三张 PNG 后目视核对类名、成员、包边界、继承/实现方向及聚合端。移除临时生成插件后，再次独立重开并导出三张 SVG，所有类名可见；30 个源码及 3 个业务数据文件的哈希与操作前一致。测试日志和预览位于本地忽略目录 `data/vpp-build-20261110-172130/`。本轮仅补充 UML 及说明，未重新执行 Java 业务回归 |
| 默认打开版本冲突修复（2026-11-10） | 软件日志确认 17:52 被新版自动升级；注册表默认打开命令指向 `D:\Visual Paradigm CE 18.0\bin\Visual Paradigm.exe`。保留升级文件副本，从哈希核对一致的原版恢复后，在实际 16.2 桌面进程完成打开、主图显示、保存、关闭与重新打开；保存后的格式仍为 `20200610 / patch 89 / build 20200904`。31 个类的完整原生定义与原版逐字一致，包和关系模型的标识、类型、名称及所属包未变化。修复备份、桌面截图和检查记录位于本地忽略目录 `data/vpp-compatibility-20261110/`；用户对图内内容的编辑验收仍待反馈 |

可重复执行的测试只依赖 JDK，自动创建新的隔离数据目录：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\MyProject\Java-homework\任务10-V3.1-三层架构重构\test.ps1" -JdkHome "D:\jdk8"
```

[RegressionTest.java](tests/RegressionTest.java) 为原生 Java 断言入口，不使用第三方测试框架；若测试工作目录已有数据，会拒绝运行。最终通过的隔离目录为 `data/self-test-62fa913080b840d0a86a65aba1675624/`；CLI 对照记录为 `data/cli-comparison/`。一次性对照脚本、日志、迁移验证类和数据不提交 Git；正式回归入口随本任务交付。

未执行：IDE 图形界面的实际点击、其他操作系统/JDK、真实磁盘耗尽/断电、多进程并发写入。两个文件的联动仍采用任务 9 的“先写资源、再写预约、失败补偿”，不是跨文件原子事务；补偿再次遭遇 IO 故障或进程中断仍可能不一致，未宣称这些场景已解决。当前任务不增加并发或高级持久化机制。

## 用户验收步骤

1. 运行任务 10，确认标题为 V3.1；使用原账号登录，管理员应看到迁移的 4 条资源和 1 条预约。
2. 学生对 AVAILABLE 资源提交未来预约，管理员按 ID 审核 PASS，应变为 OCCUPIED；学生取消后按原规则恢复 AVAILABLE。
3. 管理员驳回另一申请，确认资源状态不变；学生不能取消他人的记录或再次取消 REJECT/CANCEL，存在 PASS 时不能删除资源。
4. 退出程序并重新启动，确认账号、资源和预约仍存在；任务 9 的原文件保持原状。
5. 查看 `ui.console`、`service`、`dao` 源码和 UML，确认主类只装配依赖、UI 无数据存取、权限和冲突判断在 Service。
6. 可运行 `test.ps1`，预期输出 `PASS: 103 checks`，测试不改动上述业务数据。

本轮交付止于“自测通过，待用户验收”。收到反馈后在任务 10 内修复，不自动进入任务 11。

[返回任务索引](../README.md)
