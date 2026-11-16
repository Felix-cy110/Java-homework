# 任务16 V4.3 反射机制实现通用对象工具

状态：**自测通过，待用户验收**（2026-11-16）。未暂存、提交或推送；未开始任务 17。

## 1. 要求、基线与本次变化

用户在 Mac IDEA 启动修复后明确反馈“已经验收，现在去做任务16”。本任务从已验收[任务15 V4.2](../任务15-V4.2-自定义注解实现无侵入自动日志/README.md)源码提交 `b247a21` 增量继承。开始时已有根中英文说明、任务 15 README 和任务 6 SystemMain 的未提交修改，本轮保留这些修改，不改任务 6 源码。

核对依据为[项目任务列表](../项目任务列表.docx)任务 16 全文、[评分标准](../评分标准.docx)以及[需求核对](../docs/需求核对.md)。必做范围是原生 Java 反射工具及日志复用，不新增业务或菜单，不引入 BeanUtils/Jackson 等第三方库；沿用 MVC、单例、DaoFactory、Service/ServiceImpl 命名、Stream 报表和 back 导航。

前版本的日志序列化按 User、CampusResource、ReserveRecord 类型分别判断，扩展实体时需要修改切面。本轮改成 getter 反射，新增常规 JavaBean 不必再写分支。52 个正式 Java 文件中，**新增 ObjectReflectUtil，只修改 LogAspect 和 MainView 版本文字**；实体、Result、枚举、DAO、Service、Controller 及持久化逻辑与任务 15 原样一致。

可选的 ResourceStatDTO 业务组装替换本轮不实施，避免无关改动。任务 16 不要求新增 UML 或独立正式报告，历史任务 10/13 的 VPP 待办未在本轮处理。

## 2. 验收清单与实现位置

| 原文必做项 | 实现位置 | 实际验证 |
| --- | --- | --- |
| common.util.ObjectReflectUtil，私有构造、public static API | [ObjectReflectUtil.java](src/com/campus/reservation/common/util/ObjectReflectUtil.java) | 构造/方法修饰符检查通过 |
| objectToString，通过所有标准 getter 读取属性，null 正常处理 | 同一工具类 | 五类指定实体、父类、boolean、null、稳定顺序通过 |
| copyProperties，同名兼容 getter/setter 浅拷贝 | 同一工具类 | 五类实体、跨类DTO、缺失/不匹配、继承、装箱与重载通过 |
| isAllFieldNull，检查成员值，忽略 static | 同一工具类；LogAspect 操作人参数空检查 | 私有/父类字段、无字段、静态、空串、基本类型通过 |
| 反射异常安全降级、友好提示 | 工具类 | getter/setter/构造器/toString 失败与访问失败通过 |
| 日志入参、Result 信息统一反射序列化 | [LogAspect.java](src/com/campus/reservation/common/LogAspect.java) | 新Bean自动输出、嵌套密码脱敏、降级和原结果透明通过 |
| 原业务、统计、日志和导航不退化 | [tests](tests/) | 全量回归与 17 组版本对照通过 |
| 独立编译运行、继承已有业务数据、说明 | 脚本、本 README、忽略的迁移清单 | 独立副本及真实数据隔离迁移验证通过；IDEA实际启动通过 |

### 三个工具方法的边界

- `objectToString(null)` 返回 `""`；Bean 内的 null 属性显示为 `null`。读取公有标准 `getX()` / 布尔 `isX()`，含继承属性，排除 getClass、static、桥接和合成方法；属性名排序保证稳定。使用 getter 而不是靠实体自定义 toString 冒充反射。
- 简单值、时间、枚举直接展示；数组、Iterable、Map 和嵌套 Bean 复用同一序列化过程。身份集合及 32 层限制处理循环/过深引用；只适用于普通实体属性，不承诺任意恶意 getter、无限容器或进程级致命错误的恢复。
- `copyProperties` 创建独立目标对象，但属性是**浅拷贝**，不会复制引用指向的完整对象图。目标需要可访问的无参构造；优先精确字段类型，也接受可赋值的父接口/父类及基本类型与对应包装类，不进行数字扩大/缩窄或字符串转换。同名不兼容、缺 setter、个别 getter/setter 访问失败跳过；null 不写入基本类型。源 null 返回 null，目标类型缺失/无法构造返回友好 BusinessException，不暴露底层 cause。
- `isAllFieldNull` 直接检查本类和父类的非静态成员，不调用 getter。对象 null 或没有实例字段时返回 true；`""`、0、false 都不是 null。访问失败给出友好 BusinessException，不把未知值假装成全空。
- “反射失败回退 toString”适用于字符串方法。普通调用失败时先回退对象原 toString，若它也失败则返回类名和身份标识；复制/布尔方法不能返回字符串，按上面各自契约降级。这是对原文泛化异常描述的类型安全实现说明，不是新增教师要求。

### 日志接入与脱敏

`LogAspect` 不再为 User/资源/预约逐个拼接，也不手工拼接 Result 三个字段；入参数组和完整 Result 均调用工具。`isAllFieldNull` 用于日志操作人参数检查，全空 User 视为未登录，仅影响日志身份，不改权限/参数/原业务执行结果。

为保留任务 15 的密码保护，工具提供一个带敏感属性名的重载：`objectToString(obj, "password")`。切面先在入参副本中替换注解声明的敏感参数，再递归遮蔽密码 getter（不调用它），仍对消息和异常中的已知密码值做替换。敏感序列化失败时不能回退可能暴露密码的自定义 toString，因此仅记类名/身份。这一重载是安全实现选择，工具不引用具体 User 或其他实体类型。

日志八列、时间、ID、权限、文件转义均不变。新日志的 params 由 `arg0=...` 改成数组形态，Result 和实体属性改为稳定 getter 属性序；历史日志原文不重写。业务返回仍是同一 Result 对象，原异常仍原样传播；只读统计、查询及 back 取消不额外产生操作日志。

## 3. 运行与用户复测

### Mac IDEA（已实际验证）

打开仓库根目录，在右上角选择 **任务16 - V4.3**，点击绿色三角。在底部“运行”窗口输入菜单编号后回车。任务 16 已注册独立模块，src 为源码根、tests 为测试源根，JDK 为已有 Zulu 17，编码 UTF-8，Java 8 语言/字节码，工作目录和输出均属于本任务。已在 IntelliJ IDEA 2026.3 实际编译启动到 V4.3 首页。

本机 `.idea/`、`.iml` 与配置备份 `data/idea-config-20261116/` 均被 Git 忽略，不是应用依赖。其他电脑可用以下脚本独立运行，不需要复制本机 IDE 配置。

### 原生 JDK 脚本

```sh
cd "任务16-V4.3-反射机制实现通用对象工具"
sh run.sh
```

若 JDK 不在 PATH，可设置 JAVA_HOME；Windows 进入本任务后使用 `.\run.ps1`。入口为 `com.campus.reservation.SystemMain`，运行数据在本任务工作目录，输出在 `out/`。Windows 脚本本轮未运行。

### 复测步骤与预期

1. 启动应显示 V4.3，首页与管理员/学生菜单、back 规则保持任务 15 原状，没有新增“工具菜单”。
2. 用迁移的既有账号登录，管理员选 `13` 看日志、`14` 按用户名筛选。新登录记录应含 Result 的 success/msg/data 和 User 属性，密码被遮蔽，不改变账号与注册时间。
3. 使用演示账号新增/修改资源、学生提交预约、管理员审核，再查询日志与 9–12 统计；原权限、状态机、统计规则不变，查询本身不记录日志。菜单输入 back 应取消并返回。
4. 正常退出、重启，既有账号、业务数据及日志仍在。不要在正式文件中制造损坏场景；这些边界可运行下面的隔离测试。
5. 核验三个工具可直接运行 `python3 tests/run-tests.py`，或在 IDEA 的测试源中运行 `ObjectReflectUtilTest.main`（仅操作内存）。其他涉及 DAO 的测试请使用隔离执行器，不在正式工作目录直接运行。

## 4. 持久化迁移记录

源：任务 15 当前正式 `user.txt`、`resource.txt`、`reserve.txt`、`operation_log.txt`。目标迁移前四个文件均不存在；确认没有业务 JVM 在运行后备份。格式/编码/字段与主键不变，目标是空目录，所以按字节复制，不重建账号或时间，不复制编译输出、凭据配置或样例作为正式数据。

迁移数量：**1 个账号、0 条资源、0 条预约、0 条历史日志**。无字段冲突、无未迁移记录。账号密码仅保留于本地数据副本，没有输出到说明或 Git；两任务使用独立文件，源任务保持不变。

迁移清单和原件备份在 `data/migration-task15-20261116/`：`source/` 为源副本，`target/` 因目标原本无文件而为空，`validation/` 为隔离验证副本，`manifest.json` 保存数量、摘要和实际命令。所有路径均被 Git 忽略。

在 validation 中实际执行两次独立 JVM：加载已有账号、登录、资源/预约查询、关联检查和四类报表；第一次产生 1 条登录日志，第二次重启读到它并再产生 1 条。验证未改变三份业务文件。正式目标写入**原始备份**而非带测试登录日志的 validation，写入后四份文件与源逐字节一致；IDEA只启动到首页，未添加测试登录记录。真实源没有资源或预约，非空关联兼容由隔离演示回归覆盖，不虚称用真实预约完成验证。

同内容重跑无需追加，源缺失不意味着删除目标；若后续已有新业务记录，需要先备份并按主键合并，不能直接重复覆盖。`samples/` 只用于自动测试的隔离演示值。

## 5. 实际自测与限制（2026-11-16）

环境：Mac Apple Silicon、Zulu **JDK 17.0.18**。正式自测用 `javac --release 8 -encoding UTF-8 -Xlint:all` 编译，JDK 17 运行，编译无警告/错误；不宣称已用 JDK 8 运行时验证。Python 3 仅为测试调度工具，应用本身仍只依赖 JDK。

本任务内可直接复现：

```sh
python3 tests/run-tests.py
# 仓库内额外核对上一阶段；独立交付不需要此参数。
python3 tests/run-tests.py --baseline
# JDK 不在 PATH 时：python3 tests/run-tests.py --jdk /实际/JDK目录
```

实际从仓库根目录执行 `python3 任务16*/tests/run-tests.py --baseline`，另在只含 src/tests/samples 和脚本的独立副本执行不带 --baseline 的完整测试，两者均退出 0。

| 测试 | 实际结果 |
| --- | --- |
| ObjectReflectUtilTest | 64 项通过：五类实体、继承、boolean、static、null、浅拷贝、兼容/不匹配、访问失败、循环、降级等 |
| ReflectLogTest | 9 项通过：新 Bean、完整 Result、嵌套脱敏、空操作人、失败回退和业务透明性 |
| 原业务 / 状态机 / 单例 / MVC / 统计 Java 回归 | 103 / 108 / 15 / 26 / 72 项通过 |
| 原注解日志与新 JVM 重启 | 97 / 4 项通过，17 条测试日志重启保留 |
| 日志文件故障及原业务启动失败 | 损坏/目录/重复ID/缺末尾换行/写入失败五模式通过；三份业务文件损坏拒绝启动通过 |
| 业务 / 统计 / 日志 CLI | 30 项7会话 / 53 项11会话 / 45 项8会话通过 |
| 任务15→16 源码与行为对照 | 184 项、17 组成对会话通过；只允许切面和版本标识有变更 |
| back 导航 | 274 项、47 会话、27 个表单取消输入点通过 |
| 真实数据迁移隔离验证 | 两次独立 JVM 的加载、登录、报表、关联和日志重启通过；正式副本字节一致 |
| 独立交付 | run.sh 启动退出成功；不依赖任务 15 的完整测试通过 |
| IDEA | 实际编译启动，V4.3 首页显示正常 |

完整记录：`data/self-test-8d1ed5ac79314c02877877ca6b9cba95/result.json`；导航：`data/back-test-5a5f669ece734a7ebe425cb5f0e5a3f8/result.json`；独立副本：`data/standalone-check-20261116/`，其 `startup.log` 和 `data/self-test-1e4cdd32d56c4b2394281437f55541c2/result.json` 保存实际结果。这些本机运行记录被忽略，测试源码和命令随项目交付。

未执行：JDK 8 运行时、Windows PowerShell、真实非空资源/预约的迁移（源中没有），不将这些项标为通过。所有已执行自测通过，但用户尚未验收任务 16；本轮不自动提交或推进任务 17。

[返回任务索引](../README.md)
