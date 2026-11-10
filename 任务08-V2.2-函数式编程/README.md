# 任务08 V2.2 函数式编程

状态：用户验收通过，待提交。JDK 8 编译、Stream/Optional 契约、迁移后登录查询和命令行回归已通过；用户已确认任务 8 验收通过，本轮未提交。

前置版本：[任务07 V2.1](../任务07-V2.1-统一返回结果封装Result/README.md)。用户于 2026-11-10 确认任务 7 已验收，任务 8 以任务 7 的已验收源码和本地业务数据为基线。当前任务可独立编译、运行，不引用其他任务的源码或运行路径。

完整要求见[项目任务列表](../项目任务列表.docx)，歧义记录见[需求核对](../docs/需求核对.md)。

## 迭代目标

任务 7 的资源集合查询已经统一返回 Result，但资源筛选、查找和 ID 检查仍使用传统循环。本任务只重构资源相关集合查询，使用 Stream、Lambda 和 Optional 表达筛选、模糊匹配、排序及可能不存在的资源；注册、登录、资源写入、持久化格式、权限规则和预约关联规则继续沿用任务 7。

## 原文要求与验收清单

| 要求 | 实现位置 | 验证方法 | 结果 |
| --- | --- | --- | --- |
| 学生查询只返回 AVAILABLE 资源 | queryResources | 管理员/学生集合对照 | F03、CLI 通过 |
| 资源名称关键词模糊查询 | queryResources(User, List, keyword) | 空关键词、大小写关键词、无匹配 | F01–F04、CLI 通过 |
| 查询结果按资源 ID 自然升序 | queryResources | 无序输入集合对照 | F01、F02 通过 |
| 资源 ID 查找使用 Optional | findResourceById | 存在、缺失、空集合 | F06、F07 通过 |
| 资源 ID 唯一检查使用 Stream | addResource | 源码检查和新增回归 | 通过 |
| 查询返回 Result，空值不触发空指针 | 查询方法与 Result | null 集合、非法身份、失败数据 | F05–F08 通过 |
| 新增、修改、删除和持久化规则保持 | 继承任务 7 源码 | 迁移数据加载、登录、重启和 CLI | 通过 |
| 任务 7 数据同步到任务 8 | 本地迁移与备份 | 字段、账号登录、查询、重启、哈希 | 2026-11-10 通过；数据及备份不提交 Git |

任务书中的“多条件组合查询”属于可选项。本实现将角色状态过滤、名称关键词和 ID 排序组合在同一查询管道中，关键词为空时保持原来的全部查询语义。

## 问题、原因与方案

原实现使用 for 循环逐项筛选资源，并用循环查找资源 ID，逻辑分散且无法直接表达“可能找不到”的状态。任务 8 在 SystemMain 中把资源查询改为链式 Stream，把单资源查找改为 Optional<CampusResource>，最后仍包装为任务 7 的 Result<T>。

查询管道依次完成空集合保护、忽略空值、角色过滤、名称关键词过滤、ID 自然升序和列表收集。名称匹配会去除关键词首尾空白并忽略大小写；资源名称为空时按空字符串处理。新增资源的 UUID 碰撞判断使用 anyMatch，不修改原集合。

资源查询区域不再使用 for 遍历；displayResult 的输出循环、用户查找循环和 FileUtil 的文件行读写仍保留，因为它们不是资源集合查询逻辑。没有提前引入预约业务、三层架构或新的持久化方式。

## 实现与交付物

    src/com/campus/reservation/
    ├── SystemMain.java
    ├── entity/
    │   ├── Result.java
    │   ├── User.java
    │   └── CampusResource.java
    ├── exception/              # 继承任务 7 的 8 个异常类
    └── util/
        └── FileUtil.java

- SystemMain.java：资源查询使用 Stream/Optional，其他任务 7 业务保持增量继承。
- Result.java：沿用统一结果结构，查询成功和失败的返回格式不变。
- FileUtil.java：沿用任务 7 的 UTF-8、转义、原子保存和最小预约关联格式。
- run.ps1、独立任务配置、根目录配置：递归编译当前任务 13 个 Java 文件。

核心方法：

| 方法 | 作用 |
| --- | --- |
| queryResources(User, List<CampusResource>) | 空关键词兼容入口 |
| queryResources(User, List<CampusResource>, String) | Stream 组合角色过滤、关键词过滤和 ID 排序 |
| findResourceById(List<CampusResource>, String) | Optional 查找资源，结果转为 Result |
| addResource(...) | 使用 anyMatch 检查 UUID 碰撞，写入规则不变 |

控制台查询时，管理员和学生均会看到“请输入资源名称关键词（可留空）”。空行查询全部可见资源；输入关键词查询名称包含该关键词的资源。学生仍只能看到 AVAILABLE，管理员仍可查看全部状态。

## 数据与迁移

任务 8 继续使用任务 7 的数据格式：

| 文件 | 格式 | 本轮处理 |
| --- | --- | --- |
| user.txt | 账号、密码、角色、注册时间四列 | 直接迁移 |
| resource.txt | ID、名称、类型、状态、位置五列 | 直接迁移 |
| reserve.txt | 每行一个资源 ID | 直接迁移 |

用户要求每个任务同步已有业务数据，因此在确认任务 7 程序退出后，将任务 7 的 2 个账号、1 条资源和 0 条预约关联复制到任务 8。两阶段 FileUtil 格式兼容，无需转换；任务 8 原来没有运行数据，未发生目标冲突，也没有覆盖目标记录。

迁移前备份位于 data/migration-20261110-task07-to-task08/，其中 source/ 保存任务 7 原文件，target-before/ 保存任务 8 迁移前文件，report.json 保存数量和摘要校验结果。目录和运行数据被 Git 忽略，不提交仓库。任务 7 原文件保持不变，重复复制不会追加重复记录。

迁移后任务 8 当前数据为：

- 管理员：admin / 123456；
- 学生：账号来自任务 7 的已验收数据；
- 资源：任务 7 已有的操作系统考试教室，状态为 AVAILABLE；
- 预约关联：0 条。

## 环境与运行

使用原生 Java，无 Maven 或第三方框架。实际环境为 Windows x64、Oracle JDK 1.8.0_341（D:\jdk8）、JDK 17.0.12（D:\jdk）、Windows PowerShell 5.1。

在 OpenIDEA 打开仓库根目录或单独打开本任务，选择“任务08：编译并运行”。也可执行：

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\MyProject\Java-homework\任务08-V2.2-函数式编程\run.ps1" -JdkHome "D:\jdk8"

手动编译运行：

    Set-Location 'D:\MyProject\Java-homework\任务08-V2.2-函数式编程'
    New-Item -ItemType Directory -Force -Path out | Out-Null
    $taskSources = @(Get-ChildItem -LiteralPath src -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
    & 'D:\jdk8\bin\javac.exe' -encoding UTF-8 -Xlint:all -classpath out -d out @taskSources
    if ($LASTEXITCODE -ne 0) { throw '编译失败，请检查上方错误。' }
    chcp 65001 | Out-Null
    & 'D:\jdk8\bin\java.exe' '-Dfile.encoding=UTF-8' -cp out com.campus.reservation.SystemMain

登录管理员使用 admin / 123456。资源菜单选择 2 后输入名称关键词，直接回车表示查询全部。菜单 0 退出登录，首页 0 退出程序。

## 本轮自测记录

临时测试文件和编译输出位于系统临时目录，不属于交付源码，未覆盖其他任务数据。

| 检查 | 实际命令或方法 | 结果 |
| --- | --- | --- |
| JDK 8 编译 | D:\jdk8\bin\javac.exe -encoding UTF-8 -Xlint:all ... | 13 个源码文件成功，无警告 |
| Stream/Optional 契约 | 临时 Task08FunctionalTest，反射调用真实业务方法 | 8 项通过 |
| 资源查询源码 | 检查资源业务区域无 for，包含 Stream 和 Optional | 通过，资源区域 0 个 for、3 处 Stream、8 处 Optional |
| 迁移后管理员 CLI | 独立副本登录、空关键词、关键词查询、退出 | 通过 |
| 迁移后学生 CLI | 独立副本登录、关键词查询、学生过滤、退出 | 通过 |
| 数据迁移 | 目标版本加载、登录、角色查询、字段往返、两次重启 | 通过，2 个账号、1 条资源、0 条关联 |
| 持久化保护 | 源数据、迁移前备份、目标数据摘要核对 | 通过，任务 7 原数据未改变 |

尚未执行：OpenIDEA 实际界面点击、其他操作系统和 JDK 版本、磁盘耗尽、断电及不支持原子替换的文件系统。当前不新增并发读写能力。

## 用户验收步骤

1. 选择“任务08：编译并运行”，确认显示 V2.2；用 admin / 123456 登录，选择 2 后直接回车，确认能看到迁移来的资源。
2. 再选择 2 输入 考试，确认只显示名称包含“考试”的资源；输入不存在的关键词，确认显示空列表提示。
3. 注册或登录学生账号，选择 2 输入 教室，确认只显示 AVAILABLE 资源；管理员查询可看到全部状态。
4. 新增多个资源后查询，确认结果按资源 ID 升序；修改、删除、重启和原有权限规则保持任务 7 行为。
5. 检查 Result、Stream 查询链和 Optional 查找代码，确认没有提前加入任务 9 预约流程或任务 10 分层。

用户验收记录：用户于 2026-11-10 明确确认任务 8 已验收通过；本轮未暂存、提交、推送或创建 PR。

[返回任务索引](../README.md)
