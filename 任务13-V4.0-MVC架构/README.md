# 任务13 V4.0 MVC架构

状态：自测通过，待用户验收。用户已验收任务 12，继承源码提交 `4aa55d6`；本任务尚未提交、尚未获用户验收。

前置版本：[任务12 V3.3](../任务12-V3.3-工程优化/README.md)。本任务继承已验收源码及独立数据副本，在当前目录增量重构，可独立编译、运行。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。以下目标与验收摘自原文。

## 迭代目标

任务 10 完成了三层架构（Controller-Service-Dao），任务 12 引入单例 + 简单工厂管理实例。 本次迭代：基于现有三层架构，重构为 MVC 架构，清晰划分 Model、View、Controller 职责；同时更新 UML 类图，体现 MVC 的组件关系。

核心思想：分离视图展示、业务控制、数据模型，消除控制台输出散落在 Service/Dao 中的问题；业务逻辑、持久层代码不改动，只做职责与包结构调整。

## 验收标准（原文）

- 包结构严格按照 MVC 划分，View、Controller、Service、Dao 分层清晰；
- 控制台输入输出全部放在 View，Service/Dao 中不存在任何打印语句；
- 调用链路正确：View → Controller → Service → Dao；
- 原有全部业务功能正常运行，无 BUG；
- 提交更新后的 MVC 版本 UML 类图，依赖关系绘制正确；
- 代码没有跨层直接调用（View 禁止直接调用 Service/Dao）。

## 实现与测试记录

上一版本 `ConsoleUI` 同时承担登录、角色菜单和服务调用。本次将交互拆到 `view`，由 `controller` 转发到原服务；Service、DAO、实体、枚举、Result、异常及工具保留。任务书中的 `ResourceStatus` 在基线不存在，不为示意目录新增未使用类型；模型继续位于现有 `entity`、`exception` 包，预约服务沿用 `ReserveService` 名称。

用户于 2026-11-11 确认保留具体 DAO，不提前抽取接口；图按实际实现绘制。用户同时要求交付可在本机 Visual Paradigm 16.2 编辑的 `.vpp`，保留任务 10 原工程。

| 必做要求 | 实现位置 | 验证与结果 |
| --- | --- | --- |
| MainView / AdminView / StudentView 与纯视图 IO | `view` | 分层检查及控制台回归通过，所有标准输入输出均在 View |
| 三个控制器，View → Controller → Service → DAO | `controller` | `MvcTest` 26 项、分层检查通过；View 不引用 Service / DAO |
| 业务、持久化、单例、工厂保持 | 继承 `service` / `dao` 等包 | 五个保留包与任务 12 逐文件哈希一致；单例、工厂及业务回归通过 |
| 原有菜单、权限、状态机和错误恢复 | 当前任务 `tests` | 业务 103 项、状态机 108 项、控制台 30 项及 14 组前后版本会话通过 |
| 独立持久化数据迁移 | 本目录三个业务文件 | 2 个账号、4 条资源、1 条预约完整迁入；四次独立 JVM 校验通过 |
| MVC UML、可见性、依赖、单例和工厂 | `docs/uml` 中图源、预览、VPP | 四张图已渲染并目视核对；40 个类型、195 个核心成员与编译后的源码一致；VP 16.2 重开与无构建插件导出通过 |
| 三层 → MVC 演进说明与复测步骤 | 本 README | 已记录，下述手工步骤交用户验收 |

## 目录与职责

```text
src/com/campus/reservation/
├── SystemMain.java       启动加载、控制器和主视图装配
├── view/                 MainView、AdminView、StudentView、ConsoleView
├── controller/           UserController、ResourceController、ReserveController、ControllerSupport
├── service/              原有 Operations 接口、服务单例及 BusinessRules
├── dao/                  原有具体 DAO 单例及 DaoFactory
├── entity/               User、CampusResource、ReserveRecord、ReserveStatus、Result
├── exception/            原有业务异常
└── utils/                FileUtil
```

`ConsoleView` 复用输入流、菜单循环和统一结果展示；主视图处理登录注册及角色路由，角色视图只采集输入和展示结果。控制器接收参数、调用服务，并通过 `ControllerSupport` 将业务异常转换为 `Result.fail`。权限、资源状态、预约冲突及状态流转仍由原服务判断。`SystemMain` 仅在启动时经 `DaoFactory` 加载三个文件，启动提示也委托 View 输出。

本阶段继续使用 JDK 和脚本，不引入 Maven、Spring Boot 或额外实现层。源码新增和调整处均保留精简中文注释。未开始任务 14，也未实施选做视图。

## 三层架构到 MVC 的演进

| 关注点 | 任务 10 的三层架构 | 任务 13 的 MVC |
| --- | --- | --- |
| 交互与调度 | `ui.console.ConsoleUI` 同时处理输入、菜单、结果展示和服务调用 | `view` 负责交互，`controller` 负责请求转发与结果边界 |
| 业务调用方向 | UI → Service → DAO | View → Controller → Service → DAO |
| 模型 | 实体、统一返回、异常及业务数据 | 沿用现有实体、状态枚举和 Result，Service / DAO 支撑模型业务与持久化 |
| 实例管理 | 按任务 10 当期方式创建对象 | 继承任务 12 的 Service / DAO 饿汉单例和 DaoFactory；控制器取得服务单例，服务经工厂取得 DAO |
| 视图替换 | 控制台交互和服务调用集中于 ConsoleUI | 新视图可复用控制器；业务服务和文件格式无需随界面调整 |

此次拆分用于落实单一职责，控制器通过已有 Operations 接口持有服务，保留原来的面向接口调用。没有把任务书示例中的具体类名或尚未落地的 DAO 接口写成实际实现。任务 10 的 UML 原文件保留，任务 13 另建 MVC 工程。以上真实演进记录供任务 20 的实践报告复用，本阶段不另交 Word / PDF 报告。

## 运行与用户验收

本机使用 Oracle JDK `1.8.0_341`，路径 `D:\jdk8`。在本任务目录运行：

```powershell
.\run.ps1 -JdkHome 'D:\jdk8'
```

可将路径换成本机实际 JDK；已配置 `JAVA_HOME` 时可直接运行 `.\run.ps1`。从仓库根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File '.\任务13-V4.0-MVC架构\run.ps1' -JdkHome 'D:\jdk8'
```

OpenIDEA 打开仓库或本任务目录，选择 **任务13：编译并运行**；本机 IntelliJ IDEA 可选择 **任务13-V4.0-MVC架构：运行**，入口为 `com.campus.reservation.SystemMain`，模块为 `task13`，工作目录为本任务目录。本机 `.idea` 配置被 Git 忽略；两种 IDE 的图形运行按钮未代为点击，需用户复测。编译运行脚本已通过 Windows PowerShell 5.1 验证。

正式数据为本目录 `user.txt`、`resource.txt`、`reserve.txt`。现有账号继续使用原密码；首次缺少管理员时沿用 `admin / 123456`，普通注册仍为 `student`。既有管理员不会被重置。

建议按以下顺序复测，操作会写入任务 13 自己的数据：

1. 启动后确认标题为 V4.0；先输入无效选项，再输入错误密码，预期提示失败且返回菜单。
2. 用原管理员登录，查询迁入资源及全部预约；用原学生登录，查询可用资源和自己的预约。预期原数据可见，两个角色菜单不同。
3. 管理员新增一条测试资源，记下 ID；学生提交预约，时间示例 `2030-01-02T10:00` 至 `2030-01-02T11:00`。预期状态为 PENDING。
4. 管理员审核 PASS，预期资源为 OCCUPIED；学生取消自己的该预约，预期状态为 CANCEL，并按原业务规则释放资源。REJECT / CANCEL 不能再次取消，已处理记录不能重复审核。
5. 退出到首页再输入 `0`，重新运行后查询，预期本次记录仍在；任务 12 数据不随任务 13 改动。
6. 在指定 Visual Paradigm 16.2 中使用“打开工程”打开下方 VPP，检查四张图及 View → Controller → Service → DAO 的关系，类和方法可以直接编辑。

`samples/` 是演示数据，供隔离自测使用；不要覆盖迁入的正式数据。独立编译运行只需要本任务源码和 JDK；测试脚本额外使用 Python，前后版本差异比较还会只读编译仓库中的任务 12 源码。

## 数据迁移记录

2026-11-11，从任务 12 的三个业务文件迁入 2 个账号、4 条资源、1 条预约。源目标编码、字段、ID 和格式一致，采用完整复制；目标原先无业务文件，无冲突、无未迁移项。账号、资源 ID、预约时间、状态及关联保持原值；终态预约允许保留已删除资源的历史 ID，不据此丢弃记录。

迁移前确认应用已退出，源文件及目标原状备份在本机忽略目录 `data/migration-task12-761cc7d8b0db4d358acaf7da2a470ab1/`（`source/`、`target/`、`validation/`）。先在隔离副本进行两次独立 JVM 登录、查询及关联校验，写入目标后再独立启动两次；每次 8 项检查均通过。迁移后启动正式运行脚本并退出成功，源文件保持不变。备份和运行数据不纳入 Git。

本地迁移脚本 `data/migrate-task12.ps1` 重跑相同数据不会重复导入；目标内容不同时拒绝覆盖，避免丢失新记录。本次交付后用户使用迁入的独立数据验收，不再自动用旧任务文件覆盖。

## UML 交付物

原生工程：[任务13-MVC架构.vpp](docs/uml/任务13-MVC架构.vpp)。请使用用户指定的 `D:\Visual Paradigm 16.2\Visual Paradigm 16.2\Visual Paradigm 16.2\bin` 中的 Visual Paradigm 16.2 打开，避免默认文件关联调用其他版本。

| 图 | 可编辑图源 | SVG | PNG | 原生 VPP 导出预览 |
| --- | --- | --- | --- | --- |
| View 与 Controller | [architecture.puml](docs/uml/architecture.puml) | [查看](docs/uml/architecture.svg) | [查看](docs/uml/architecture.png) | [查看](docs/uml/vpp-preview/architecture.png) |
| Service、DAO、单例与工厂 | [services.puml](docs/uml/services.puml) | [查看](docs/uml/services.svg) | [查看](docs/uml/services.png) | [查看](docs/uml/vpp-preview/services.png) |
| 实体、状态与统一返回 | [models.puml](docs/uml/models.puml) | [查看](docs/uml/models.svg) | [查看](docs/uml/models.png) | [查看](docs/uml/vpp-preview/models.png) |
| 业务异常继承 | [exceptions.puml](docs/uml/exceptions.puml) | [查看](docs/uml/exceptions.svg) | [查看](docs/uml/exceptions.png) | [查看](docs/uml/vpp-preview/exceptions.png) |

图中展示核心属性和方法、参数及返回类型、可见性、静态成员、抽象成员、依赖与关联、包边界和调用方向。`Result<T>` 保留泛型模型；Service 和 DAO 标注单例，DaoFactory 标注工厂。主图较宽，建议用 SVG 或在 VPP 中缩放查看。任务 10 的原图未被覆盖。

实际核对：40 个类型（39 个项目类型及 JDK `RuntimeException`）、195 个核心成员的签名和修饰符经编译产物反射核对；VPP 使用原生类、属性、方法、包、关系和注释模型，保存后在独立的 VP 16.2 进程中重开，检查四张图、成员、包归属、图形边界和 65 个去重关系。PlantUML 与 VPP 的四张预览均已目视检查。为避免 VP 16.2 包背景遮挡类框，原生图的包使用透明填充，仍保留包边界。移除本次临时构建插件后再次打开工程并导出四图，结果与已核对预览逐像素一致；交付 VPP 不依赖该插件。

## 实际自测记录

2026-11-11，在本任务目录执行：

```powershell
.\test.ps1 -JdkHome 'D:\jdk8'
```

| 检查 | 实际结果 |
| --- | --- |
| JDK 8 独立编译运行 | 通过；`javac -encoding UTF-8 -Xlint:all` |
| `SingletonTest` | 15 项通过，包含私有构造、实例身份及加载一次 |
| 三个文件分别损坏后的启动 | 均提示对应文件并返回退出码 1 |
| `RegressionTest` | 103 项通过，含注册登录、权限、资源、预约、写入失败与恢复 |
| `StateMachineTest` | 108 项通过，含状态矩阵、审核、取消、越权和回滚 |
| `MvcTest` | 26 项通过，含控制器转发、统一结果、角色权限和异常边界 |
| `verify-cli.py` | 7 个控制台会话、30 项检查通过 |
| `verify-mvc.py` | 96 项通过，含保留包哈希、层次边界、14 组任务 12 / 13 配对会话；比较退出码、归一后的输出及三个持久化文件 |
| 迁移数据验证 | 隔离副本及安装后各两次独立 JVM，四次均 8 项通过 |
| Windows PowerShell 5.1 正式脚本 | 从仓库根目录启动，输入 `0` 正常退出，数据落在本任务目录 |
| 历史成果与数据保护 | 45 个受保护文件哈希一致，包含任务 12 源码及数据、任务 10 原 UML；目标三个业务文件与迁移备份一致 |
| 启动配置 | 根目录及本任务 OpenIDEA TOML、本机 IDEA XML 均可解析；IDE 图形按钮待用户复测 |
| UML | 编译产物反射核对、VP 16.2 原生重开、四图目视及移除构建插件后的独立导出均通过 |

测试使用 `data/` 下的隔离目录，未覆盖正式业务数据。动态 UUID、运行时间及版本号在前后版本比较时归一，其他提示和状态均参与比较。新增源码、运行配置、测试与 UML 均留在当前工作区供验收，本轮未暂存、提交或推送。

用户验收：待反馈。IDE 图形运行按钮待用户按上述步骤复测；命令行编译、业务、启动配置文件和持久化自测的结论不代替用户验收。

[返回任务索引](../README.md)
