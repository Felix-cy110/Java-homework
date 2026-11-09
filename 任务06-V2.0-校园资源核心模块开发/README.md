# 任务06 V2.0 校园资源核心模块开发

状态：已提交；用户验收通过。用户于 2026-11-09 明确反馈“已经验收，继续做任务7”，确认包含默认管理员调整的任务 6 作为后续基线。实际 Git 历史中默认管理员调整已包含在 `fbabd45`，任务 7 开始时 HEAD 为 `8483cb9506822f82bdae82ca5309504e4c59165e`，工作区干净。本次仅同步验收记录，未代为提交。原资源模块 40 项检查、默认管理员调整 14 项检查保留为历史自测记录，不冒充本轮重测。

## 默认管理员调整（2026-11-09）

用户反馈：普通注册固定为 student，管理员只能通过导入演示文件创建，要求预置 `admin / 123456`。现于全部数据加载成功后初始化默认管理员，角色固定为 `admin`，复用原文件保存方法；普通注册仍生成学生。该调整来自用户明确要求，不是任务书新增条款。

- 未找到用户名 `admin` 时：追加默认管理员，成功保存 `user.txt` 后才更新内存，不影响已有学生或其他管理员。
- 已有 `admin` 管理员时：保留现有密码与注册时间，重启不重复添加、不重置密码。
- 已有同名学生时：中文提示冲突并停止启动，原账号与文件保持原样；不会将其自动升级为管理员。
- 数据损坏时不初始化；默认管理员保存失败时提示中文错误并停止，不允许仅在内存中使用该账号。

| 本轮验收项 | 实现位置 | 验证方法 | 结果 |
| --- | --- | --- | --- |
| 无需导入即可用默认管理员登录 | `initializeDefaultAdministrator` | 首次启动、已有学生数据、重启 | 通过 |
| 保留现有管理员、拒绝同名学生冲突 | 初始化分支、启动异常处理 | 原密码、时间、角色、文件对比 | 通过 |
| 保存失败停止启动；学生权限不变 | 原文件工具、注册及资源业务 | 占用文件、普通注册、资源管理回归 | 通过；源码确认保存成功后才更新内存 |

本轮自测环境为 Oracle JDK `1.8.0_341`、Windows PowerShell 5.1；同时执行默认 JDK `17.0.12` 的根目录启动配置。使用 Python 标准库在独立临时目录调用实际程序，未修改真实用户、资源、预约文件。实际编译参数为 `javac -encoding UTF-8 -Xlint:all -classpath <临时输出> -d <临时输出> <本任务全部源码>`，运行完整主类 `com.campus.reservation.SystemMain`。

| 编号 | 本轮检查 | 结果 |
| --- | --- | --- |
| A01 | JDK 8 独立编译 | 成功，无警告 |
| A02–A03 | 全新目录默认管理员登录并新增、查询资源，再重启 | 账号与资源持久化，管理员只创建一次，密码与时间不变 |
| A04–A05 | 重名注册、错误密码；普通学生注册后尝试增改删 | 重名及错误密码拒绝；学生角色与权限保持原样 |
| A06–A08 | 已有学生及其他管理员时追加；已有 admin 管理员；已有同名学生 | 保留历史字段；已有管理员不重置；同名学生提示冲突、退出码 1、原文件不变 |
| A09 | user.txt、resource.txt、reserve.txt 分别损坏 | 均不初始化管理员，不覆盖原数据，无堆栈 |
| A10–A11 | Windows 文件句柄允许读、禁止替换 user.txt，再解除占用重启 | 初始化失败停止，原文件不变且无临时残留；解除后成功 |
| A12（两项） | 独立副本显式 JDK 8 脚本、根配置默认 JDK 17 | 均可直接登录默认管理员，12 个 class 来自当前源码 |
| A13 | 对比历史源码和所有现有运行数据的 SHA-256 | 一致 |

共 14 项通过，未将旧版 40 项重复计入。追加账号保存时沿用 `LocalDateTime` 的文本序列化，历史时间末尾的 `:00` 可被省略，时间值保持相同；测试按时间值验证，已有 admin 无需追加时文件逐字节不变。上述自动自测未操作 OpenIDEA 界面；用户随后已明确验收通过，未提供逐项操作记录，不将其改写为助手执行的测试。

前置版本：[任务05 V1.4](../任务05-V1.4-自定义异常/README.md)。开始本任务时，继承前置版本已验收的源码，在当前目录增量修改；本项目最终应可独立编译、运行。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。以下目标与验收摘自原文。

## 迭代目标

系统从用户模块升级为业务系统，新增校园资源管理核心能力。系统从单纯用户账号模块升级为完整业务系统，新增校园资源管理核心能力。

基于 V1.4 已有用户模块基础，新增校园资源实体、资源内存管理、文件持久化、角色权限控制；管理员拥有资源全部增删改查权限，学生仅能查询可预约资源，为后续预约业务打下数据基础。

## 验收标准（原文）

- 管理员：可以新增、查看、修改、删除资源；存在预约记录的资源无法删除；
- 学生：只能查询状态为【可预约】资源，不能执行增删改；
- 程序重启后，资源数据不丢失；
- 非法操作（学生尝试删资源、ID 不存在等）触发自定义异常，输出友好中文提示，不打印底层堆栈。

## 基线、问题与方案

任务 5 只有用户注册、登录及异常处理，登录后尚未保存会话或进入资源菜单。本任务继承其全部 8 个 Java 源文件和通用启动脚本，在 `SystemMain` 中扩展角色菜单和资源业务，新增独立的 `CampusResource` 实体及业务异常，由文件工具负责资源读写。

继续使用 `src/com/campus/reservation/` 下的 `entity`、`exception`、`util` 包；采用原生 Java、JDK 8 和已有脚本。业务方法仍留在入口，按课程进度到任务 10 再抽取三层架构。用户实体与资源实体互不依赖，落实单一职责；所有新代码同步精简中文注释。

## 要求与验收清单

| 类别 | 要求 | 实现位置 | 验证方法 | 结果 |
| --- | --- | --- | --- | --- |
| 必做 | `CampusResource` 五个私有字段、无参/全参构造、get/set、toString | `entity/CampusResource.java` | 实体契约和字段展示 | E1 通过 |
| 必做 | `List<CampusResource>` 管理、启动加载 | `SystemMain`、`FileUtil` | 首次启动、重启 | C01、R04 通过 |
| 必做 | 管理员新增、全部查询、按 ID 修改和删除 | `SystemMain` | CRUD、自动唯一 ID、默认 AVAILABLE | R01、R03–R04、E2 通过 |
| 必做 | 学生只看 AVAILABLE，业务方法拒绝学生增删改 | `SystemMain`、权限异常 | 菜单及直接调用业务方法 | R02、R06、E3、E5 通过 |
| 必做 | 名称非空、ID 不存在抛异常、状态限定三种 | `SystemMain`、业务异常 | 参数及权限边界 | R05、E4–E5 通过 |
| 必做 | `resource.txt` 自动创建，变更及时保存，流自动关闭 | `FileUtil` | 重启往返、损坏文件、保存失败 | C01、E6、E9、F01–F05 通过 |
| 必做，口径已确认 | 已有预约关联禁止删除，任务 9 再升级完整预约 | `deleteResource`、`loadReservedResourceIds` | 有关联/无关联、运行中更新关联、读取失败 | R03、E7–E8 通过 |
| 仓库约定 | 继承用户业务、统一中文异常、独立运行、注释与说明 | 源码、脚本、本 README | 登录注册回归及启动检查 | U01–U09、L01–L06 通过；IDE 界面待用户验证 |

可选项 `description` 字段和同名资源提示本次未选择，保留五个必做字段；新增菜单不询问可选描述。自动 ID 使用 UUID；资源类型限定为教室、实验室、设备、场地。具体文件格式和菜单编号属于实现选择。任务 6 未要求当期 UML 或独立正式报告。

## 预约关联的阶段口径

用户于 2026-11-09 明确选择“实现最小预约关联文件（仅保存资源 ID）用于删除校验，任务 9 再升级完整预约”。本阶段 `reserve.txt` 每行一个资源 ID，有关联即禁止删除；删除前重新读取，因此程序运行期间补入的关联也能生效。关联读取失败时停止本次删除，不将错误当成无预约。状态为 OCCUPIED 本身不表示存在预约记录，不能代替关联判断。

本任务不增加申请、审核、取消、时间冲突等任务 9 功能。任务 9 将在独立目录中引入 `ReserveRecord`、完整文件格式，并改为仅 PASS 记录禁止删除。本任务的简化文件不能直接作为任务 9 的完整记录读取。来源见[需求核对](../docs/需求核对.md)，这是用户确认的阶段口径，不是教师补充要求。

## 实现和交付物

```text
src/com/campus/reservation/
├── SystemMain.java
├── entity/
│   ├── User.java
│   └── CampusResource.java
├── exception/
│   ├── BusinessException.java
│   ├── EmptyParameterException.java
│   ├── PasswordErrorException.java
│   ├── UserAlreadyExistsException.java
│   ├── UserNotFoundException.java
│   ├── PermissionDeniedException.java
│   ├── ResourceNotFoundException.java
│   └── ResourceReservedException.java
└── util/
    └── FileUtil.java
```

- [SystemMain.java](src/com/campus/reservation/SystemMain.java)：保留任务 5 的注册、登录和输入校验方法；新增角色菜单、资源增删改查、权限与参数校验。登录成功后进入角色菜单，`0` 退出登录回到首页；首页 `0` 退出程序。
- [CampusResource.java](src/com/campus/reservation/entity/CampusResource.java)：五字段纯数据实体，字段均为 `String`；注册用户与资源互不持有引用。
- [FileUtil.java](src/com/campus/reservation/util/FileUtil.java)：继承用户文件格式，新增资源与最小预约关联读取、资源保存；复用转义和原子替换策略，统一文件创建检查，读写流自动关闭。
- [PermissionDeniedException.java](src/com/campus/reservation/exception/PermissionDeniedException.java)、[ResourceNotFoundException.java](src/com/campus/reservation/exception/ResourceNotFoundException.java)、[ResourceReservedException.java](src/com/campus/reservation/exception/ResourceReservedException.java)：均继承任务 5 的 `BusinessException`，菜单统一捕获并输出中文提示。
- [run.ps1](run.ps1)、[本任务 OpenIDEA 配置](.openidea/run.toml)、[根目录配置](../.openidea/run.toml)：递归编译全部 12 个源文件，使用完整主类名启动，数据始终位于本任务工作目录。
- [演示用户](samples/user-demo.txt)、[演示资源](samples/resource-demo.txt)、[演示预约关联](samples/reserve-demo.txt)：明确的演示值，默认不会自动导入，也不包含真实数据。

新增、修改和删除都先构造候选列表，文件成功保存后才改变内存；修改使用新实体，避免保存失败前把原对象改掉。所有写方法先校验管理员身份，即使绕过菜单直接调用也会拒绝学生、未登录及未知角色。查询方法只向学生返回 AVAILABLE 资源。

资源名称必须非空且不能仅含常规空白；类型须为四种中文值之一；状态须为三个大写值之一；位置允许空字符串。名称和位置保留原值，修改时全部重新输入；ID 不可修改。UUID 为实现选择，同时检查当前集合防止重复；同名资源允许存在。新增状态固定 AVAILABLE。

## 数据文件

运行工作目录必须为本任务目录，脚本和运行配置会自动切换。首次启动自动创建缺失文件，并在全部数据加载成功后向用户文件补建缺失的默认管理员；资源与预约文件仍为空，不读取其他任务的数据。

| 文件 | 每行内容 | 用途 |
| --- | --- | --- |
| `user.txt` | 账号、密码、角色、注册时间，四列以实际制表符分隔 | 沿用任务 5 的账户数据；普通注册仍为 student |
| `resource.txt` | ID、名称、类型、状态、位置，五列以实际制表符分隔 | 保存全部资源，新增、修改、删除后立即落盘 |
| `reserve.txt` | 一个资源 ID | 最小预约关联，用于删除校验；同 ID 多行表示有关联，空行忽略 |

三种文件均采用 UTF-8，兼容文件开头 BOM 与空行。文本字段的反斜杠、制表符、回车、换行、BOM 分别转义为 `\\`、`\t`、`\r`、`\n`、`\uFEFF`。位置为空时第五列仍保留。用户密码在文件中保留原值，控制台对象输出仍显示掩码。

损坏记录、重复资源 ID、非法状态/类型、数据文件被目录占用等情况会给出中文系统提示并停止启动，退出码 1；原文件不会被当成空数据覆盖。运行时保存或关联读取失败可留在菜单重试。运行数据、`out/` 和写入临时文件已被根 `.gitignore` 忽略。

## 环境与运行

仅需 JDK 8 或以上，使用原生 Java。实际环境及测试结果见下方记录。Java 源码为 UTF-8 无 BOM；脚本保留 UTF-8 BOM，兼容 Windows PowerShell 5.1。

在 OpenIDEA 打开仓库根目录或单独打开本任务目录，选择 **任务06：编译并运行**。若未看到配置，重新打开项目。也可从任意 PowerShell 目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\MyProject\Java-homework\任务06-V2.0-校园资源核心模块开发\run.ps1" -JdkHome "D:\jdk8"
```

脚本优先使用 `-JdkHome`，其次使用 `JAVA_HOME`，最后查找 PATH 中的编译器。换电脑时替换项目及 JDK 路径。手动编译运行：

```powershell
Set-Location 'D:\MyProject\Java-homework\任务06-V2.0-校园资源核心模块开发'
New-Item -ItemType Directory -Force -Path out | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
& 'D:\jdk8\bin\javac.exe' -encoding UTF-8 -Xlint:all -classpath out -d out @taskSources
if ($LASTEXITCODE -ne 0) { throw '编译失败，请先检查上方错误。' }
chcp 65001 | Out-Null
& 'D:\jdk8\bin\java.exe' '-Dfile.encoding=UTF-8' -cp out com.campus.reservation.SystemMain
```

### 直接使用默认管理员

无需导入演示用户即可登录：账号 `admin`，初始密码 `123456`，角色 `admin`。首次启动自动创建并保存；普通注册仍为 `student`，注册同名 `admin` 会按已有账号拒绝。若文件中已存在 admin 管理员，以保存的密码为准，程序不会重置。

直接运行“任务06：编译并运行”，依次输入 `1`、`admin`、`123456`，应进入管理员资源菜单；空数据目录查询时显示暂无资源，可自行新增。菜单 `0` 退出登录，首页 `0` 退出程序。

如果旧版本曾注册同名学生，程序会保留该账号并提示冲突。请退出程序，备份本任务 `user.txt`，把该学生的账号列改为未占用的新名称、其余字段保持原样，再启动即可创建默认管理员；也可在无历史数据的独立副本中验证。

### 导入演示数据（可选）

下列示例仅用于快速获得四种资源场景和演示学生，不是使用管理员功能的前提。请在首次启动前或未运行过的独立副本中导入。首次启动后 `user.txt` 已有默认管理员，下面的保护检查会拒绝覆盖。只要任一目标已有内容，就整体停止；不要删除现有数据来导入示例。

```powershell
# 先检查全部目标，避免导入一半后才发现原有数据。
$taskDemoFiles = @{
    'user.txt' = 'samples\user-demo.txt'
    'resource.txt' = 'samples\resource-demo.txt'
    'reserve.txt' = 'samples\reserve-demo.txt'
}
foreach ($taskDataName in $taskDemoFiles.Keys) {
    if (Test-Path -LiteralPath $taskDataName) {
        $taskDataFile = Get-Item -LiteralPath $taskDataName
        if ($taskDataFile.PSIsContainer -or $taskDataFile.Length -gt 0) {
            throw "已有数据或同名目录：$taskDataName。请在独立副本中导入演示文件。"
        }
    }
}
foreach ($taskDataName in $taskDemoFiles.Keys) {
    Copy-Item -LiteralPath $taskDemoFiles[$taskDataName] -Destination $taskDataName
}
```

| 演示角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin_demo` | `demo123` |
| 学生 | `student_demo` | `demo123` |

演示资源共四项：`demo-available` 可预约、`demo-occupied` 占用、`demo-disabled` 停用、`demo-reserved` 可预约但有最小预约关联。此阶段“可预约状态”和“是否存在关联”分别验证，尚无预约申请或状态联动逻辑。

## 用户验收步骤

1. 先按“直接使用默认管理员”验证无需导入即可用 `admin / 123456` 登录。若还要复现后面的固定资源 ID 场景，在未运行过的独立副本中导入演示数据，再选择“任务06：编译并运行”，使用 `admin / 123456` 登录并选择 `2`，应显示全部四个资源及五个字段；原演示账号 `admin_demo / demo123` 也保留有效。
2. 选择 `1`，依次输入 `新增会议室`、`教室`、`南楼一层`，预期新增成功、自动生成 UUID、状态 AVAILABLE。选择 `2` 可查到新资源，记下 ID。
3. 选择 `3`，输入刚才的 ID，然后依次输入 `更名会议室`、`实验室`、`北楼二层`、`DISABLED`。预期修改成功且 ID 不变。依次输入 `0` 退出登录、`0` 退出程序，再运行并用管理员查询，修改应保留。
4. 管理员选择 `4` 并输入 `demo-reserved`，应提示已有预约记录，资源仍存在；删除 `demo-occupied` 应成功，因为示例中它没有预约关联。重启后已删除项不再出现。
5. 退出登录，使用 `student_demo / demo123` 登录，选择 `2`。仅展示 AVAILABLE 资源，不能看到占用、停用资源的 ID 或详情。输入 `1`、`3`、`4`，均提示只有管理员可执行，且不要求继续输入资源内容。
6. 用管理员测试空名称、错误类型、不存在 ID、非法状态；均应显示中文业务提示并继续菜单，不改变原资源。所有交互输入中途结束应正常退出，不保存半完成的操作。
7. 在首页注册新的学生账号，验证重复注册、错误密码、成功登录和重启后登录。注册同名 `admin` 应提示账号已存在；其他正常注册账号仍为学生。异常文件测试只在副本中进行。

## 原资源模块自测记录（默认管理员调整前）

日期：2026-11-09。以下为新增默认管理员之前的历史测试，其中首次启动空用户文件、可注册学生 admin 的行为已被本轮明确调整；其余结果不冒充本轮重跑。实际环境为 Windows x64、Oracle JDK `1.8.0_341`（`D:\jdk8`）、默认 JDK `17.0.12`（`D:\jdk`）、Windows PowerShell 5.1、Python `3.11.9` 标准库。

从本任务目录使用 JDK 8 的 `javac -encoding UTF-8 -Xlint:all -classpath <临时输出> -d <临时输出> <本任务全部源码>` 编译，输出到隔离目录，结果成功且无警告。业务进程以 `java -Dfile.encoding=UTF-8 -cp <临时输出> com.campus.reservation.SystemMain` 启动；尖括号处为每轮实际生成的临时目录或源码列表。运行数据、旧版编译输出、测试驱动和故意损坏的副本均在系统临时目录，不写入真实账号文件。

脚本测试实际执行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File run.ps1`，从根配置和独立任务配置解析原始命令、参数和工作目录；指定 JDK 8 时从两个工作目录用脚本绝对路径启动并追加 `-JdkHome D:\jdk8`。独立副本仅携带源码、脚本及演示文件，不含旧 class。编译、普通 JVM 和启动脚本均设置超时。

| 编号 | 实际检查 | 结果 |
| --- | --- | --- |
| C01 | 无文件首次启动 | 自动创建三个空文件，通过 |
| U01–U08 | 与任务 5 对照空账号、空/短密码、用户不存在、重复注册/错密码、中文空格、无效菜单、账号/密码 EOF | 8 项通过；仅归一化版本号和注册时间，其余输出一致 |
| U09 | 注册名为 admin 的用户，登录后退出并重启登录 | 学生角色与注册时间保持一致，通过 |
| R01–R02 | 管理员查询全部；学生查询及输入三个越权菜单编号 | 学生仅看到 AVAILABLE，越权无数据修改，通过 |
| R03–R04 | 新增、修改、无关联删除、有关联拒删，重启再查询与新增 | 四字段修改、默认状态、UUID、删除及重启恢复通过 |
| R05–R06 | 空名称、非法类型/状态、不存在 ID；管理员退出后切换学生 | 参数失败无写入，会话切换不保留管理员权限，通过 |
| R07 | 资源菜单及新增、修改、删除输入位置的 10 种 EOF | 全部友好退出，没有半完成写入，通过 |
| E1–E3 | 临时 Java 驱动检查实体、连续生成 8 个不同 ID；直接反射调用业务方法 | 字段封装及默认状态正确；学生、null、未知角色的增删改均准确抛出权限异常，通过 |
| E4–E5 | 直接调用业务方法检查空值、ID、类型、状态；切换三种状态并查询 | 异常类型准确且无副作用，学生过滤及落盘正确，通过 |
| E6 | 中文、制表符、换行、反斜杠及 BOM 字段保存再读取 | 字段完整往返，通过 |
| E7–E8 | 运行中写入关联；资源三种状态下拒删；损坏关联；解除关联后删除 | 不依赖状态推测关联，实时读取有效，错误时保留数据，通过 |
| E9 | 保存空列表并重读 | 文件为空且返回空集合，通过 |
| F01–F04 | 7 种损坏资源、损坏关联/用户文件、同名目录、三文件 BOM/空行 | 损坏数据退出码 1、无堆栈、原文保留；合法 BOM/空行正常，通过 |
| F05 | Windows 独占句柄占用 resource.txt，直接调用新增、修改、删除；解除后重试 | 三次失败均不改变原对象或列表、文件不变、无残留临时文件；重试全部成功 |
| L01–L04 | 根/任务配置的默认 JDK 17；根/任务目录显式 JDK 8 | 12 个 class 均由当前源码生成；管理员新增后重启，学生可查询新增资源，通过 |
| L05–L06 | 脚本加载损坏数据；保留旧 class 后故意制造源码语法错误 | 运行失败只输出中文提示和退出码 1；编译失败不启动旧程序，通过 |
| Z | SHA-256 对比所有历史任务源码、原始 DOCX 及现有运行数据 | 一致，通过 |
| D01–D02 | 执行上方演示导入命令；再次在已有账号数据时执行 | 首次完整导入；已有数据时整体拒绝且所有文件不变，通过 |

上述共 40 项检查通过；包含若干多输入、多进程子场景，未将子场景重复计数。临时反射、Stream 和 Python 驱动只用于测试，不加入课程业务源码。占用测试驱动初次未兼容 `IOException` 子类型和 Windows CRLF，修正驱动后该项复测通过，未因此修改业务实现。

历史自测未执行：OpenIDEA 界面加载及点击运行（当时只实际执行其配置命令）；非 Windows 系统、其他 JDK 版本、无原子移动支持的文件系统、磁盘耗尽及断电。本阶段仍按单实例读写设计，不提供并发修改协调。用户已验收并授权开始任务 7；任务 6 源码及数据作为阶段成果保留。

[返回任务索引](../README.md)
