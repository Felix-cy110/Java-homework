# 任务09 V3.0 资源预约核心业务开发

状态：自测通过，待用户验收。2026-11-13 已补充全局 back 返回；原阶段验收历史保留，本轮需用户复验，尚未提交。

前置版本：[任务08 V2.2](../任务08-V2.2-函数式编程/README.md)。本项目可在当前目录独立编译运行，不引用其他任务的源码或运行时文件。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。

## 本次目标与问题

任务 8 只有资源管理和最小预约关联文件，无法表达申请人、预约时间、状态和审核结果，学生也无法提交、查询或取消完整预约。任务 9 新增预约实体和状态枚举，把 `reserve.txt` 升级为八列完整记录，并在原入口中闭合“申请→审核→占用→取消→释放”的业务链路。

任务 9 仍沿用当前阶段的入口、实体、异常、工具包结构，不提前引入任务 10 的 Service/DAO 三层架构；所有业务操作继续通过 `Result<T>` 返回，异常在入口边界转换为中文失败提示。

## 验收清单

| 要求 | 实现位置 | 验证方法 |
| --- | --- | --- |
| `ReserveRecord` 无参/全参构造、getter/setter、`toString` | `entity/ReserveRecord.java` | 编译及 CLI 输出完整字段 |
| `ReserveStatus` 四种状态 | `entity/ReserveStatus.java` | 文件加载、审核和取消流程 |
| 八列预约文件启动加载、自动创建、写入后持久化 | `util/FileUtil.java` | 独立副本多次启动、检查 `reserve.txt` |
| 学生只能预约 AVAILABLE 资源 | `submitReservation` | 学生提交成功及 OCCUPIED 拒绝 |
| 时间顺序、结束时间、PASS 时间冲突校验 | `validateReservationTime`、`hasOverlappingPassedReservation` | 逆序/过期/重叠边界 |
| 学生查询自己的全部预约 | `queryMyReservations` | 学生菜单 6 |
| 学生仅取消 PENDING/PASS 自己的记录 | `cancelReservation` | 成功取消、越权和重复取消 |
| 管理员查看、审核和筛选预约 | `queryAllReservations`、`auditReservation`、`filterReservations` | 管理员菜单 5/6/7 |
| PASS 占用资源，取消后无其他 PASS 时恢复 AVAILABLE | `auditReservation`、`cancelReservation` | 状态文件对照 |
| 只有 PASS 预约阻止删除资源 | `deleteResource` | PASS 删除失败，CANCEL/PENDING 删除成功 |
| 查询和按 ID 查找使用 Stream、Lambda、Optional | 预约查询方法 | 源码检查和 CLI 回归 |
| 学生/管理员权限边界 | `requireStudent`、`requireAdministrator` | 角色菜单与业务方法失败 Result |

## 实现与交付物

```text
src/com/campus/reservation/
├── SystemMain.java
├── entity/
│   ├── CampusResource.java
│   ├── ReserveRecord.java
│   ├── ReserveStatus.java
│   ├── Result.java
│   └── User.java
├── exception/
│   ├── ReserveCancelException.java
│   ├── ReserveConflictException.java
│   ├── ReserveRecordNotFoundException.java
│   └── ResourceUnavailableException.java
└── util/
    └── FileUtil.java
```

- `SystemMain.java`：保留任务 8 资源 CRUD 和 Stream 查询，扩展学生预约、管理员审核/筛选、取消释放和删除保护。
- `ReserveRecord.java`、`ReserveStatus.java`：预约数据模型与固定状态，不包含业务逻辑。
- `FileUtil.java`：使用 UTF-8、转义、try-with-resources、临时文件和原子替换读写八列预约记录。
- `samples/reserve-demo.txt`：提供可复制的八列演示预约记录，不含真实凭据。
- `run.ps1`、`.openidea/run.toml`：递归编译并启动任务 9 入口。

预约文件每行格式为：

```text
recordId  resourceId  applicantUsername  applyTime  reserveStartTime  reserveEndTime  status  remark
```

字段之间使用制表符分隔，文本字段支持反斜杠、制表符、换行和 BOM 转义。

## 数据迁移

用户要求每次任务推进都同步已有持久化数据。本次在任务 8 程序退出后执行迁移：

- `user.txt`：直接复制 2 个账号，保留管理员 `admin / 123456` 和学生账号；
- `resource.txt`：直接复制 1 条资源及其 ID、状态、位置；
- `reserve.txt`：任务 8 只有资源 ID 关联格式，当前源文件为空，因此转换为任务 9 八列格式的空文件；若旧文件存在非空关联，缺少申请人和时间等字段时不虚构预约记录，只保留源备份并待人工处理；
- 迁移前备份：`data/migration-20261110-task08-to-task09/`，包含 `source/`、`target-before/` 和 `report.json`；
- 任务 8 源文件保持不变，任务 9 使用自己的独立副本，运行数据和备份由 `.gitignore` 排除。

迁移后已通过启动加载、管理员/学生登录、预约写入、重启读取和状态联动检查。

## 环境与运行

使用原生 Java，无 Maven、Gradle、Spring Boot 或第三方框架。实际编译环境为 Windows x64、Oracle JDK 1.8.0_341（`D:\jdk8`），同时用 JDK 17 编译器完成本轮检查。

在 OpenIDEA 打开仓库根目录或单独打开本任务，选择“任务09：编译并运行”。也可执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\MyProject\Java-homework\任务09-V3.0-资源预约核心业务开发\run.ps1" -JdkHome "D:\jdk8"
```

手动编译运行：

```powershell
Set-Location 'D:\MyProject\Java-homework\任务09-V3.0-资源预约核心业务开发'
New-Item -ItemType Directory -Force -Path out | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
& 'D:\jdk8\bin\javac.exe' -encoding UTF-8 -Xlint:all -classpath out -d out @taskSources
& 'D:\jdk8\bin\java.exe' '-Dfile.encoding=UTF-8' -cp out com.campus.reservation.SystemMain
```

学生登录 `chenyang / 123456`，管理员登录 `admin / 123456`。学生菜单使用 5 提交、6 查询、7 取消；管理员菜单使用 5 查看、6 审核、7 筛选。时间输入使用 ISO 格式，例如 `2099-01-01T10:00`。

## 本轮自测记录

测试均在 `data/self-test/`、`data/self-test2/`、`data/self-test3/` 隔离副本中执行，没有覆盖任务 9 正式迁移数据。

| 检查 | 实际结果 |
| --- | --- |
| 递归编译 | JDK 编译器退出码 0，19 个 `.class` 生成；仅出现环境中缺失 `D:\jdk\lib\dt.jar`、`tools.jar` 的警告 |
| 学生提交与查询 | 成功生成 PENDING 八列记录，学生只能看到自己的记录 |
| 管理员审核通过 | PASS 写入 `reserve.txt`，资源同步变为 OCCUPIED |
| 学生取消 PASS | 状态变为 CANCEL，资源恢复 AVAILABLE；再次取消返回友好失败 Result |
| 时间冲突 | 已有 PASS 的重叠申请被拒绝，未改变原记录 |
| 管理员驳回/筛选入口 | 审核菜单支持 REJECT，筛选按资源、申请人、状态使用 Stream |
| 驳回与权限边界 | `self-test3` 中 REJECT 写入成功且资源保持 AVAILABLE；bob 取消 chenyang 的记录被拒绝，原记录保持 REJECT |
| 已占用资源预约 | `self-test3` 中 OCCUPIED 资源提交预约返回友好失败，`reserve.txt` 未新增记录 |
| 删除保护 | PASS 记录阻止删除；取消 PASS 后，仍有 PENDING 时资源可删除，符合任务 9 规则 |
| 重启持久化 | 多次独立启动均能加载之前写入的预约记录和资源状态 |

未执行：OpenIDEA 实际界面点击、其他操作系统/JDK 版本、磁盘耗尽、断电和不支持原子替换的文件系统。上述项目不影响本轮已执行的核心业务验证，留给用户验收时复测。

## 用户验收步骤

1. 启动任务 9，使用 `chenyang / 123456` 登录，查询资源后提交一个未来时间段预约，确认记录为 `PENDING` 且重启后仍存在。
2. 退出登录后使用 `admin / 123456` 登录，选择查看全部预约，按记录 ID 选择 `PASS`，确认资源变为 `OCCUPIED`。
3. 学生再次登录取消该 `PASS` 记录，确认状态变为 `CANCEL`、资源恢复 `AVAILABLE`，再次取消会失败。
4. 管理员尝试删除仍有 `PASS` 记录的资源应失败；只有 `PENDING/REJECT/CANCEL` 记录时允许删除。
5. 输入重叠时间、结束时间早于当前时间、学生查看他人记录或管理员执行学生操作，确认均返回中文失败提示且不打印堆栈。

用户验收记录：2026-11-10 用户明确表示“已验收，现在可以去做任务10”。任务 9 源码提交为 `b01dfe7`；助手本轮未执行暂存、提交、推送或创建 PR。

## 全局返回补充与复验（2026-11-13）

本轮基线为当前提交 `8f07a0c`。原交互在填写表单时缺少取消入口，只能继续输入或结束程序；现按用户明确要求跨阶段补充 `back`，实现位于 `src/com/campus/reservation/SystemMain.java`，保留本任务原有结构与业务规则，并补充精简中文注释。这是用户追加的交互完善，两份 DOCX 未禁止返回菜单，不将它写成教师新增要求；上文验收与自测属于历史，本轮状态以本节及页首为准。

注册、登录及当期已有表单的任意输入处输入 `back`，取消本次尚未提交的内容并返回所属菜单；首页输入 `back` 保持首页，原数字菜单和 `0` 保留。角色菜单输入 `back` 退出登录并回到首页。列表和统计展示后自动回到角色菜单。 命令仅识别小写，忽略前后空白；`BACK`、`back123` 仍为普通文本。输入 `\back` 表示字面文本 `back`，连续前导反斜杠仅去掉一层，其他输入保持原样。提示及转义说明仅在启动时显示一次。

| 本轮检查 | 实际结果 |
| --- | --- |
| 独立编译与运行 | Oracle JDK `1.8.0_341`，`D:\jdk8`，`javac -encoding UTF-8 -Xlint:all` 通过 |
| 导航与取消 | 46 次独立控制台会话、267 项断言通过，覆盖 26 个表单输入点 |
| 提示、转义及原行为 | 单次启动提示、大小写/转义、取消后继续操作、输入结束等通过；有持久化的阶段同时验证取消不写文件及重启可读 |
| 隔离测试记录 | `data/back-test-4217f730624b468cac9c7b83561b21ba/result.json`；同目录保留编译与会话日志，不纳入 Git |
| 数据与业务保护 | 全仓库 273 个受保护业务源码、正式数据和脚本哈希一致；任务 15–20 未改动 |

在仓库根目录可复跑本阶段导航测试（Python 只用于测试，应用运行只需要 JDK）：

```powershell
python .\tests\verify-back.py --jdk D:/jdk8 --tasks 9
```

删除 `--tasks 9` 可测试全部任务 1–14；合计已通过 449 次控制台会话、2482 项断言，覆盖 216 个表单输入点。测试只使用新建的隔离演示数据。

修改前源码、说明、UML 及已有正式数据备份于 `data/back-navigation-e3904a3a2c9b4ba6b6b7c128f17eb8c6/`，该目录被 Git 忽略。各任务原有账号、资源及预约文件保持原样；本轮不改变持久化格式，也未开始新阶段，因此不重新覆盖或迁移数据，无冲突及未迁移项。

用户复验：启动后在首页输入 `back`，应仍显示首页；进入注册，在密码处输入 `back`，应回首页且不新增账号；使用原账号登录。在新增资源或预约等当前已有表单中途输入 `back`，应回角色菜单且没有写入；角色菜单再输入 `back`，应回首页。最后输入 `0` 退出，全程启动提示只出现一次。 如需验证字面值，在文本字段输入 `\back` 应作为普通 `back` 处理；格式受限字段仍按原规则校验。沿用上文原运行方式，IDE 图形运行按钮本轮未点击，留用户复测。当前为“自测通过，待用户验收”，未暂存、提交或推送。

[返回任务索引](../README.md)
