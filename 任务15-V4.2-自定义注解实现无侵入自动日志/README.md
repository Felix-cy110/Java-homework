# 任务15 V4.2 自定义注解实现无侵入自动日志

状态：**自测通过，待用户验收**（2026-11-16）。本轮未暂存、提交或推送，不进入任务 16。

## 1. 要求与继承基线

用户于 2026-11-16 明确确认“任务14已经验收通过，现在去做任务15”。本任务从已验收的[任务14 V4.1](../任务14-V4.1-高阶Stream实现数据统计与热度排行/README.md)提交 `402085d` 继承源码、演示样本和测试，未复制编译产物、IDE 本机配置或凭据；任务 14 源码、数据保持不变，仅补记其验收结论。

已核对[项目任务列表](../项目任务列表.docx)中的任务 15 完整内容及[评分标准](../评分标准.docx)，并遵循[需求核对](../docs/需求核对.md)。本次要求是在既有 MVC、单例、DAO 工厂及 Stream 统计上，使用自定义注解和反射自动收集操作日志，不把日志代码写进原 Service 业务方法，不引入 Spring、AspectJ 或其他框架。

继续沿用接口 `…Service`、实现 `…ServiceImpl` 的命名，均在 `service` 包；保留既有预约规则、状态机、四类报表及 `back` 行为。任务 15 原文未要求新 UML 或独立正式报告，本轮不新增这些交付，也不把历史任务 10/13 的 VPP 待办标成已完成。

## 2. 问题、方案与验收清单

前置版本尚无操作日志。若逐个在业务方法中插入写日志代码，会使重复的日志处理混入业务逻辑。本轮把采集、脱敏和持久化错误隔离集中到 `LogAspect`，控制器统一经反射调用服务，原有三个业务 ServiceImpl 仅添加注解和对应 import，方法主体不变。

调用方向：`View → Controller → ControllerSupport → LogAspect.invoke → Service → DAO`。切面在原调用结束后将日志交给 `DaoFactory.getOperationLogDao()`；管理员查询仍为 `View → Controller → 日志查询 Service → 日志 DAO`，查询本身不打日志，避免递归。

| 必做要求 | 实现位置（均位于 src/com/campus/reservation/） | 实际验证 |
| --- | --- | --- |
| 方法级、运行期注解；反射读取描述 | [common/annotation/OperationLog.java](src/com/campus/reservation/common/annotation/OperationLog.java) | 注解元数据、实现方法查找、无注解调用通过 |
| 八字段日志实体 | [entity/OperationLogRecord.java](src/com/campus/reservation/entity/OperationLogRecord.java) | 字段、构造器、读写、展示转义通过 |
| 饿汉单例 DAO，启动加载、追加文件、工厂入口 | [dao/OperationLogDao.java](src/com/campus/reservation/dao/OperationLogDao.java)、DaoFactory、SystemMain | 单例、文件往返、新 JVM 重启、异常文件通过 |
| 保持业务返回和异常，集中采集日志 | [common/LogAspect.java](src/com/campus/reservation/common/LogAspect.java)、controller/ControllerSupport.java | 原 Result 对象、原异常、只执行一次、日志故障隔离通过 |
| 注册/登录、资源增改删、预约提交/审核通过/驳回/取消 | UserServiceImpl、ResourceServiceImpl、ReserveRecordServiceImpl | 注解覆盖和真实控制台会话通过 |
| 管理员全量查询、按用户名筛选、时间倒序 | [service/OperationLogServiceImpl.java](src/com/campus/reservation/service/OperationLogServiceImpl.java)、OperationLogController、AdminView | Stream 排序、精确筛选、学生/未登录拒绝通过 |
| 原业务、统计、返回导航不变；独立项目 | [tests](tests/)、[run.sh](run.sh)、[run.ps1](run.ps1) | 全量回归、17 组基线对照、独立副本编译运行通过 |

### 日志内容与边界

- 八字段为 `logId`、`username`、`operateTime`、`methodFullName`、`operationDesc`、`params`、`resultInfo`、`exceptionMsg`。日志 ID 使用 UUID，时间使用 `LocalDateTime`。
- `@OperationLog.value` 是业务描述。另加 `sensitiveParams` 和 `usernameParam`，分别指定敏感入参位置和登录前用户名位置；它们是本次实现选择，不是额外教师要求。注册/登录发生前没有已登录用户，日志记输入的账号，不代表认证已成功。
- 原审核入口 `auditReservation` 同时处理通过和驳回，保持原方法不拆分；注解描述“审核预约（通过/驳回）”，参数保留 `PASS` / `REJECT` 和原因。共标记八个方法，覆盖要求中的九类操作。
- 已由业务处理并返回 `Result.fail` 的失败记录到结果字段，异常字段为空；真正抛出的异常保留类型、消息和堆栈，切面解包后继续抛出同一异常。控制器仍按原规则转换面向用户的错误提示。
- 密码参数用脱敏标记替换；返回提示或异常引用同一敏感值时也脱敏，`User` 对象只记录用户名和角色。日志用户名保留原值以支持精确筛选。单行终端展示转义换行、制表符等，完整异常堆栈保存在日志文件，管理员查询时以转义后的单行内容展示；普通业务错误仍使用原来的友好提示。
- 查询资源、预约、统计及日志不写操作日志；初始化默认管理员也不额外生成操作日志。输入表单 `back` 在业务提交前取消，因此不生成该项业务日志。
- 日志写入失败不覆盖已经完成的原业务结果；管理员查询会看到日志故障提示。损坏日志不自动清空或重写，保留原文件供修复，修复后重新启动。原三份业务文件损坏仍按前置版本规则拒绝启动，不因日志容错而被忽略。
- 当前是单进程控制台阶段，未提前实现任务 17 的线程池或跨进程并发能力。

## 3. 编译、运行与输入示例

应用仅依赖完整 JDK 8 或以上。入口为 `com.campus.reservation.SystemMain`。本轮实际环境为 **Zulu JDK 17.0.18**，自测以 `javac --release 8 -encoding UTF-8 -Xlint:all` 编译并在 JDK 17 上运行；未在 JDK 8 运行时复验。

从仓库根目录进入本任务：

```sh
cd "任务15-V4.2-自定义注解实现无侵入自动日志"
sh run.sh
```

脚本固定本任务目录为工作目录，编译到忽略的 `out/`。如 JDK 不在 PATH，先设置 `JAVA_HOME`。Windows 可进入本目录后执行：

```powershell
.\run.ps1
```

首页选择 `1` 登录、`2` 注册、`0` 退出。新目录首次启动沿用原版本默认管理员初始化（演示账号 `admin` / `123456`）；这属于正常运行行为，不是迁移所得数据。已有管理员不会被覆盖。学生通过首页注册。

管理员保留原 1–12 菜单，新增 **13.查看全部操作日志**、**14.按用户名查询日志**。例如登录管理员后输入 `13`，应出现登录记录；输入 `14` 后填写 `admin`，应仅显示该账号的记录，时间倒序。所有输入继续支持小写 `back`，启动时只提示一次；字面文本 `back` 用 `\back` 输入。

## 4. 持久化与迁移记录

业务文件仍为本任务工作目录的 `user.txt`、`resource.txt`、`reserve.txt`；新增 `operation_log.txt`，UTF-8，每条记录八列，以制表符分隔，反斜线、换行、回车和制表符转义后写入。DAO 追加记录并使用 try-with-resources 关闭文件；读取时校验字段和重复 ID，返回防御性副本，不修改原业务文件格式。

2026-11-16 核对结果：任务 14 与任务 15 均不存在正式 `user.txt`、`resource.txt`、`reserve.txt`、`operation_log.txt`，所以**本次迁移业务记录数为 0**，没有可转换的源数据，没有目标冲突，不覆盖或删除任何正式数据。根文档中的旧迁移数量是历史记录，不代表本工作区现有文件。未将样例冒充迁移数据，也未为迁移虚构账号、时间、资源或预约。

本机核对清单：`data/migration-task14-20261116/manifest.json`；预留源/目标备份目录为其同级 `source/`、`target/`，因原文件不存在而为空。整个 `data/` 及正式运行数据均已被 Git 忽略。真实数据的迁移、登录/关联/重启验证本轮无法执行，原因是源数据缺失；若后续提供源文件，应先退出程序，备份并按仓库迁移规则验证后导入，不能覆盖用户在任务 15 新增的数据。

[samples](samples/) 继承前阶段演示文件，仅供测试。需要演示样本时，在独立临时工作目录运行已编译的类，把 `samples/user-demo.txt`、`resource-demo.txt`、`reserve-demo.txt` 分别复制为该临时目录的三份业务文件；不要覆盖本任务正式文件。自动测试已经在隔离目录完成上述装载。

## 5. 实际自测（2026-11-16）

Python 3 仅用于组织测试，不是应用运行依赖。在本任务目录执行：

```sh
python3 tests/run-tests.py
# 仓库内可额外比较任务14；独立交付不需要这一参数。
python3 tests/run-tests.py --baseline
# 未配置默认 JDK 时使用：python3 tests/run-tests.py --jdk /实际/JDK目录
```

Windows 提供 `test.ps1`（调用 PATH 中的 `python`，需 Python 3），可加 `-JdkHome` 或 `-Baseline`。脚本使用 UUID 命名的 `data/self-test-*` 目录，保存真实命令、输出和 `result.json`，不读取或覆盖正式账号与预约。

| 测试 | 结果 |
| --- | --- |
| 原业务 / 状态机 / 单例 / MVC / 统计 Java 回归 | 分别 103 / 108 / 15 / 26 / 72 项检查通过 |
| OperationLogTest | 97 项检查通过，生成 17 条日志；含脱敏、权限、返回一致性和异常 |
| LogRestartTest | 新 JVM 加载相同 17 条日志，4 项检查通过 |
| LogFailureTest | 损坏内容、路径是目录、重复 ID、无末尾换行、写入失败五种模式通过；日志失败不阻止资源业务提交 |
| 原业务文件损坏启动 | user/resource/reserve 三类均按原规则拒绝启动，通过 |
| 基础控制台 / 统计控制台 / 日志控制台 | 分别 30 项/7 会话、53 项/11 会话、45 项/8 会话通过 |
| 任务14→15 源码与行为对照 | 163 项检查、17 组成对会话通过；原 Service 去除新增注解后与基线逐字一致 |
| back 导航 | 274 项断言、47 次会话、27 个表单取消输入点通过；日志筛选 back 另在日志控制台用例验证 |
| 独立交付验证 | 仅复制 src/tests/samples 和运行/测试脚本；run.sh 启动退出正常，不带 --baseline 的完整测试再次通过 |

本机实际命令（仓库根目录）：

```sh
/Users/felix-sun/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
  任务15*/tests/run-tests.py --baseline
```

对应 JDK 为 `/Users/felix-sun/Documents/Codex/.devtools/micromamba-root/envs/dev/lib/jvm`。全量结果见 `data/self-test-a2c2ba4e3b854d4c8d8e6d7864d566c3/result.json`（编译日志无警告/错误），导航见 `data/back-test-344d38deffe64fdfb10e240d6bb1c4af/result.json`。独立副本位于 `data/standalone-check-20261116/`，其 `startup.log`、`independent-tests.log` 和 `data/self-test-e515580a5b5a475596e362aa5402d7d9/result.json` 记录独立验证。以上是忽略的本机记录，不随 Git 分发；测试源码和复现命令随任务交付。

未执行项：Windows PowerShell 包装脚本、IDE 图形运行按钮、JDK 8 运行时和真实源数据迁移检查；前述自测通过仅指实际执行环境和用例，不冒充这些未执行项或用户验收。

## 6. 用户验收步骤与预期

1. 进入本任务目录，执行 `sh run.sh`（Windows 使用 `run.ps1`），确认版本 V4.2、启动只提示一次 `back`。
2. 注册演示学生、尝试一次错误密码及一次正确登录；管理员登录后选 `13`，应有注册/登录成功和失败记录，密码不可见。
3. 管理员新增、修改、删除一条测试资源；学生对另一可用资源提交预约，管理员分别测试通过和驳回，学生再测试取消。每次已调用的业务应生成对应日志，原权限和状态规则不变；预约时间使用未来时间且不与已存在记录冲突。
4. 管理员选 `14`，输入刚才的学生用户名，应只显示该账号日志且时间倒序；输入不存在的用户名应无记录。再次进入筛选时输入 `back` 应返回管理员菜单。
5. 查看原 9–12 统计报表，再查看日志，统计查询本身不增加记录。学生菜单不显示日志入口；在学生菜单输入 `13` / `14` 不得访问管理员日志。
6. 正常退出并重新启动，原账号、资源、预约和日志应保留；本次新登录会再产生一条日志。需要复测异常文件时只在隔离副本操作，可直接运行自动测试，不能破坏正式数据。

当前仅完成助手自测。待用户亲自复测并明确确认后记录验收通过；Git 操作仍需另行授权。

[返回任务索引](../README.md)
