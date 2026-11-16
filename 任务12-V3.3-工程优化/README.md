# 任务12 V3.3 工程优化

状态：本轮 Service 命名调整自测通过，待用户验收。 2026-11-16 基于 `280a60a` 修改，未提交；此前验收记录保留，不代表本轮已验收。

前置版本：[任务11 V3.2](../任务11-V3.2-预约审核流程与状态机流转开发/README.md)。本任务继承已验收源码和独立数据副本，可独立编译运行。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。以下目标与验收摘自原文。

## 迭代目标

任务 10 已完成 UI / Service / DAO 三层分包。本任务对核心业务服务、DAO 持久化对象使用设计模式管理实例，解决多处 new 对象造成实例重复创建、资源重复加载、实例不统一的问题；

单例模式：保证业务服务、DAO 全局唯一实例，避免重复读取文件、重复加载数据

简单工厂模式：统一封装对象创建逻辑，将对象创建与业务使用分离

不新增业务功能，属于架构重构迭代；原有所有业务功能（登录、资源 CRUD、预约状态机）保持不变。

## 验收标准（原文）

- Service、Dao 类实现饿汉单例，构造方法私有，外部无法 new；
- 存在 DaoFactory 工厂类，统一生产 DAO 实例；
- 项目所有地方不再直接 new Service、Dao；全部通过getInstance()和 DaoFactory 获取；
- 多次获取实例，引用地址相同，保证全局单例；
- 原有全部业务功能正常运行，没有功能退化；
- 文件只会在程序启动加载一次，不会多次重复读取。

## 实现与测试记录

- `UserServiceImpl`、`ResourceServiceImpl`、`ReserveServiceImpl` 与三个 DAO 均采用私有构造器、静态唯一实例和 `getInstance()`。任务书称预约服务为 `ReserveRecordService`，本阶段沿用已验收的 `ReserveServiceImpl` 类名。
- `dao/DaoFactory.java` 统一返回 DAO 单例，服务通过工厂获取，入口按文件顺序初始化并获取服务。文件读写、实体、业务规则与接口保持任务 11 的实现；未引入 Maven、框架、DAO 接口或 MVC。
- DAO 在类初始化时只尝试加载一次；加载错误被保留并由入口提示对应文件。修复损坏文件后需要重新启动程序，不能在同一 JVM 中重新加载。
- 可选 UML 未选择；任务 10 原图不变。

## 运行与验收

本机使用 Oracle JDK `1.8.0_341` 编译、运行。OpenIDEA 打开仓库或本任务目录，选择 **任务12：编译并运行**；本机 IntelliJ IDEA 可选择 **任务12-V3.3-工程优化：运行**。两种 IDE 的图形启动按钮未代为操作，待用户复测。

也可在本任务目录的 PowerShell 中执行：

```powershell
.\run.ps1 -JdkHome 'D:\jdk8'
```

将 JDK 路径换成本机实际路径；也可配置 `JAVA_HOME` 后运行 `.\run.ps1`。程序使用本目录的 `user.txt`、`resource.txt`、`reserve.txt`，不会共用任务 11 的运行文件。默认管理员为 `admin` / `123456`，现有账号仍可使用原密码。

建议验收：登录管理员和原有学生，查询迁入的资源及预约；新增一条资源，学生提交预约，管理员审核通过后检查资源变为占用，再由学生取消并检查资源状态；退出重启后确认结果仍在。上述操作会修改任务 12 的数据。

## 数据迁移

来源为任务 11 的三个业务文件，格式和编码未改变。目标原先不存在业务文件，无冲突；完整复制 2 个账号、4 条资源、1 条预约，无未迁移记录。源文件备份及验证副本位于本机 Git 忽略目录 `data/migration-task11-d16848302fe44b9a83e14825ee4ac0ae/`。迁移前检查无 Java 进程，备份后在隔离副本中两次独立启动，验证账号登录、资源和预约查询及关联；核对源文件哈希未变化后复制，安装时核对目标与备份哈希，安装后又独立启动验证两次。旧任务数据未修改，备份不纳入 Git。

## 自测记录

- 在本目录运行 `.\test.ps1 -JdkHome 'D:\jdk8'`：单例身份、私有构造及只加载一次 15 项通过；三个数据文件分别损坏时均提示正确文件；注册、登录、权限、资源及预约流程 103 项通过；状态流转及权限 108 项通过。
- `tests/verify-cli.py` 对演示数据在隔离目录执行 7 个控制台会话、30 项检查，覆盖驳回、通过、取消、重启与损坏数据提示，均通过。运行时需 Python，并提供测试脚本输出的 classes 路径与 Java 可执行文件路径。
- 正式运行脚本使用迁入数据启动并退出成功；安装后的两次迁移核对均通过。
- 任务 11 的继承式 DAO 故障注入不适用于私有构造器；本次在隔离目录用目标路径被目录占用，验证资源写入失败不修改缓存，以及审核、取消时预约文件写入失败的资源文件和缓存回滚、状态保持及恢复后重试。GUI 按钮尚未验证。

## 全局返回补充与复验（2026-11-13）

本轮基线为当前提交 `8f07a0c`。原交互在填写表单时缺少取消入口，只能继续输入或结束程序；现按用户明确要求跨阶段补充 `back`，实现位于 `ui/console/ConsoleUI.java` 和同包的 `BackNavigation.java`，保留本任务原有结构与业务规则，并补充精简中文注释。这是用户追加的交互完善，两份 DOCX 未禁止返回菜单，不将它写成教师新增要求；上文验收与自测属于历史，本轮状态以本节及页首为准。

注册、登录及当期已有表单的任意输入处输入 `back`，取消本次尚未提交的内容并返回所属菜单；首页输入 `back` 保持首页，原数字菜单和 `0` 保留。角色菜单输入 `back` 退出登录并回到首页。列表和统计展示后自动回到角色菜单。 命令仅识别小写，忽略前后空白；`BACK`、`back123` 仍为普通文本。输入 `\back` 表示字面文本 `back`，连续前导反斜杠仅去掉一层，其他输入保持原样。提示及转义说明仅在启动时显示一次。

| 本轮检查 | 实际结果 |
| --- | --- |
| 独立编译与运行 | Oracle JDK `1.8.0_341`，`D:\jdk8`，`javac -encoding UTF-8 -Xlint:all` 通过 |
| 导航与取消 | 46 次独立控制台会话、267 项断言通过，覆盖 26 个表单输入点 |
| 提示、转义及原行为 | 单次启动提示、大小写/转义、取消后继续操作、输入结束等通过；有持久化的阶段同时验证取消不写文件及重启可读 |
| 隔离测试记录 | `data/back-test-cca756c391554e7e9fd9f2a92dbc40b7/result.json`；同目录保留编译与会话日志，不纳入 Git |
| 数据与业务保护 | 全仓库 273 个受保护业务源码、正式数据和脚本哈希一致；任务 15–20 未改动 |

在仓库根目录可复跑本阶段导航测试（Python 只用于测试，应用运行只需要 JDK）：

```powershell
python .\tests\verify-back.py --jdk D:/jdk8 --tasks 12
```

删除 `--tasks 12` 可测试全部任务 1–14；合计已通过 449 次控制台会话、2482 项断言，覆盖 216 个表单输入点。测试只使用新建的隔离演示数据。

另在本任务目录执行 `.\test.ps1 -JdkHome 'D:\jdk8'`，退出码为 0；单例 15 项、损坏文件启动、业务回归 103 项、状态机 108 项通过。日志为 `../任务14-V4.1-高阶Stream实现数据统计与热度排行/data/back-navigation-e3904a3a2c9b4ba6b6b7c128f17eb8c6/regression-12.log`（本机忽略）。

修改前源码、说明、UML 及已有正式数据备份于 `data/back-navigation-e3904a3a2c9b4ba6b6b7c128f17eb8c6/`，该目录被 Git 忽略。各任务原有账号、资源及预约文件保持原样；本轮不改变持久化格式，也未开始新阶段，因此不重新覆盖或迁移数据，无冲突及未迁移项。

用户复验：启动后在首页输入 `back`，应仍显示首页；进入注册，在密码处输入 `back`，应回首页且不新增账号；使用原账号登录。在新增资源或预约等当前已有表单中途输入 `back`，应回角色菜单且没有写入；角色菜单再输入 `back`，应回首页。最后输入 `0` 退出，全程启动提示只出现一次。 如需验证字面值，在文本字段输入 `\back` 应作为普通 `back` 处理；格式受限字段仍按原规则校验。沿用上文原运行方式，IDE 图形运行按钮本轮未点击，留用户复测。当前为“自测通过，待用户验收”，未暂存、提交或推送。

[返回任务索引](../README.md)

## Service 命名调整（2026-11-16）

用户明确授权同时调整任务 10–14；以当前提交 `280a60a` 的各阶段独立源码为基线，不重新复制上一任务、不推进任务 15。原 `*Operations` 接口与 `*Service` 实现的名称不够直观，本轮统一为以下配对，仍放在原 `service` 包，不增加 `impl` 子包或新架构层。

| 接口 | 实现类 | 验证 |
| --- | --- | --- |
| `UserService.java` | `UserServiceImpl.java` | 声明、implements、构造器和调用引用核对通过 |
| `ResourceService.java` | `ResourceServiceImpl.java` | 声明、implements、构造器和调用引用核对通过 |
| `ReserveService.java` | `ReserveServiceImpl.java` | 声明、implements、构造器和调用引用核对通过 |

`BusinessRules.java` 是包内规则工具，不是服务接口或实现，名称不变。任务 10–11 保留构造注入；任务 12–14 的饿汉单例保留在 `*ServiceImpl`，UI/Controller 的业务字段继续依赖 `*Service` 接口。调用端、Java 测试、结构回归检查、当前实现说明已同步；业务方法体、DAO、实体、状态机和持久化格式不变。

### 本轮实际自测

初次运行旧 `verify-cli.py` 时因本工作区尚无 `data/` 父目录而中止；创建被忽略的隔离数据目录后原脚本复验通过，未改动业务代码以绕过失败。

环境：macOS，Zulu OpenJDK `17.0.18`。每个任务单独用 `javac --release 8 -encoding UTF-8 -Xlint:all` 编译源码和测试，再用 JDK 17 运行；这是 Java 8 API/字节码兼容编译，不宣称已在 JDK 8 运行复验。

- RegressionTest：PASS RegressionTest: 103 checks；StateMachineTest：PASS StateMachineTest: 108 checks；SingletonTest：PASS singleton checks=15；verify-cli.py：{"cliSessions": 7, "checks": 30, "result": "PASS"}
- 返回导航：46 次会话、267 项断言、26 个表单取消输入点全部通过。
- 测试仅使用临时目录和被忽略的 `data/` 中的隔离演示样本；本工作区没有本任务正式 user/resource/reserve 文件，未创建或替换真实账号和业务数据，也不把历史迁移记录当成本机数据存在的证明。
- 运行日志：本机 `data/service-rename-20261116/`；原阶段测试和用户验收记录保留。

从仓库根目录复验返回操作（将 JDK 路径替换为本机路径）：

```text
python tests/verify-back.py --jdk <JDK目录> --tasks 12
```

独立编译运行（macOS/Linux，在本任务目录执行；生成物只放在忽略的 data 下）：

```sh
mkdir -p data/service-check/classes data/service-check/demo
find src -name '*.java' > data/service-check/sources.txt
javac --release 8 -encoding UTF-8 -d data/service-check/classes @data/service-check/sources.txt
(cd data/service-check/demo && java -cp ../classes com.campus.reservation.SystemMain)
```

Windows 继续使用既有 `run.ps1` / `test.ps1`；本轮未执行 PowerShell 脚本和 IDE 图形按钮。用户复测：核对三对接口/实现文件，运行登录、资源查询及预约菜单，在表单中输入 `back` 应取消并返回，原业务行为不变。
