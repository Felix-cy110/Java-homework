# Java-homework

[简体中文](README.md) | **English**

<!-- When editing this file, update README.md as well so that both languages reflect the same content and progress. -->

A campus resource reservation system developed for a comprehensive software construction practice course. This repository contains 20 iterative tasks, each in its own directory as an independent Java project. Each project builds incrementally on the previous one.

This page is available in Chinese and English. Task documentation and original course materials remain in Chinese.

Current status: Tasks 01–15 contain source code; Tasks 16–20 remain unimplemented. The user explicitly accepted Task 14 on November 16, 2026. Task 15 incrementally inherits accepted commit `402085d`, has passed JDK 17.0.18 self-tests and standalone verification, and is **awaiting user acceptance, uncommitted**. This round implements Task 15 only and retains the Service / ServiceImpl naming and back navigation rules. Historical native VPP updates and the specified VP 16.2 checks for Tasks 10 and 13 remain outstanding.

## Service Naming Update — November 16, 2026

The user's cross-stage request applies only to Tasks 10–14. Interfaces now use `UserService`, `ResourceService`, and `ReserveService` (Tasks 10–12) or `ReserveRecordService` (Tasks 13–14); implementations append `Impl`. All remain in the existing service package. BusinessRules, business behavior, dependency injection / eager singletons, DAO code and persistence formats are unchanged. The earlier self-test record is retained. The user has now explicitly accepted Task 14 only; acceptance is not inferred for other stages. The naming changes are included in `402085d`.

Five projects compiled independently with JDK 17.0.18 and `--release 8`; applicable Java, CLI, MVC and statistics tests passed. Navigation tests passed 231 sessions and 1,342 assertions. This does not claim JDK 8 runtime, Windows-script or IDE verification. Formal user/resource/reservation files are absent from these five task directories in this workspace; no business data was migrated or replaced. Tasks 10 and 13 still need native VPP renaming and VP 16.2 verification. Details and reproducible commands are in each task README.

## Original Requirements

- [Course task list](项目任务列表.docx)
- [Grading criteria](评分标准.docx)
- [Requirements review and documented ambiguities](docs/需求核对.md)

## Repository Structure

Directories follow the `任务编号-版本-任务名` pattern (task number, version, and task name). Task numbers are padded with a leading zero for sorting, and colons in task titles are replaced with hyphens.

```text
Java-homework/
├── README.md
├── README.en.md
├── LICENSE
├── CONTRIBUTING.md
├── AGENTS.md
├── .gitignore
├── .gitattributes
├── .github/pull_request_template.md
├── 项目任务列表.docx
├── 评分标准.docx
├── docs/需求核对.md
├── 任务01-V1.0-项目初始化/
│   ├── README.md
│   └── src/
├── 任务02-V1.1-用户注册功能/
│   ├── README.md
│   └── src/
├── …
└── 任务20-V6.0-项目视图拓展/
    ├── README.md
    └── src/
```

The repository root contains Git metadata, course documentation, and local IntelliJ IDEA configuration for multiple modules. Each task directory contains its own source code, run instructions, and any configuration, tests, or documentation needed at that stage, without depending on other task directories. Run each program with its task directory as the working directory so that versions do not share persistence files.

As requested by the user on November 10, 2026, later stages also inherit existing application data, stored as an independent copy in the new task directory. Copy or merge compatible formats; convert and migrate data when the format or storage mechanism changes. Back up existing target data and check for conflicts first. Preserve any data that cannot be migrated and document the reason. Runtime data and backups remain excluded from Git. See the [data migration rules](AGENTS.md#阶段间持久化数据迁移).

Under the user's updated requirements, Tasks 03–08 organize Java packages by responsibility under `src/com/campus/reservation/`. The entry point stays in the root package. Task 03 introduces `entity`, Task 04 adds `util`, Task 05 adds `exception`, Task 06 extends this structure with resource management, Task 07 adds `Result` to `entity`, and Task 08 continues with the same package structure. Compilation and execution continue to use the JDK and existing scripts, without Maven or frameworks. Tasks 1 and 2 retain their original structure. The source of these decisions is recorded in the [requirements review](docs/需求核对.md).

Tasks 01–15 contain source code for their respective stages; Tasks 16–20 contain placeholders. Task 10 introduced UI / Service / DAO and UML, Task 11 completed reservation state transitions, Task 12 added singleton services and DAOs with a DAO factory, and Task 13 introduced View → Controller → Service → DAO. The earlier correction added the required enums and common packages and ReserveRecordService name to both Tasks 13 and 14. Task 14 provides four live Stream reports within the corrected MVC structure. Task 15 adds an annotation/reflection logging aspect and administrator log queries. Existing business rules and file formats remain compatible, and each task runs independently.

Tasks 03–14 provide OpenIDEA run configurations that work from the repository root or their respective task directories. Task 14 historically inherited 2 accounts, 4 resources and 1 reservation from Task 13. Those formal files are absent in this workspace on November 16, 2026, leaving no business data to migrate to Task 15; see the task READMEs. Task 15 provides independent run.sh / run.ps1 scripts; local IDE configurations were not added or verified this round.

This local IntelliJ IDEA workspace contains independent modules and run configurations for Tasks 01–14; each uses its own task directory. The `.idea` configuration is local and ignored by Git. Task 14 was verified on the command line with JDK 8; its GUI run buttons await user verification.

Configuration fix recorded on November 10, 2026: IDEA previously loaded only an empty root module, retained incorrect Maven import settings, and lacked compilation steps before launch. The task modules were restored, the incorrect Maven configuration was removed, and SDK, output, and build settings were completed. The JPS project loader bundled with the local IntelliJ IDEA `2025.3.1.1` installation validated all 20 modules, with zero JPS compilation errors. The compiled output of Tasks 01–09 successfully started under JDK 17 in isolated working directories and used Java 8 bytecode. Hashes of 131 protected files, including source code, existing launch configurations, and application data, remained unchanged. IDEA automatically reindexed the modules; the GUI Run button still awaits user verification. The `.idea/` configuration, backups, and validation records are stored only on this machine and ignored by Git, so a fresh clone will not include them.

## Task Index

Tasks 01–12 passed their original stage acceptance and were committed. Task 11's source commit is `05d8375`, with comment updates in `cfc1185`; Task 12's source commit is `4aa55d6`. Task 13's original source is `028a216`, with README updates in `ba1a743`. On November 11, 2026, the user accepted the original Task 13, then requested package corrections in Tasks 13 and 14. Those corrections were included in commit `8f07a0c`; a commit alone does not establish user acceptance. The November 13 navigation work covered Tasks 01–14, passed self-tests and awaited renewed user acceptance at the time; it is now included in later baselines. On November 16, 2026, the user explicitly accepted Task 14 (`402085d`). Task 15 is self-tested, awaiting user acceptance and uncommitted. Tasks 16–20 remain unimplemented.

| Task | Version | Project |
| --- | --- | --- |
| 01 | V1.0 | [Project initialization](任务01-V1.0-项目初始化/README.md) |
| 02 | V1.1 | [User registration](任务02-V1.1-用户注册功能/README.md) |
| 03 | V1.2 | [Object-oriented encapsulation: refactoring the User entity](任务03-V1.2-面向对象封装-User实体类重构/README.md) |
| 04 | V1.3 | [File persistence with Java I/O](任务04-V1.3-IO文件持久化功能开发/README.md) |
| 05 | V1.4 | [Custom exceptions](任务05-V1.4-自定义异常/README.md) |
| 06 | V2.0 | [Core campus resource management](任务06-V2.0-校园资源核心模块开发/README.md) |
| 07 | V2.1 | [A unified Result wrapper](任务07-V2.1-统一返回结果封装Result/README.md) |
| 08 | V2.2 | [Functional programming](任务08-V2.2-函数式编程/README.md) |
| 09 | V3.0 | [Core resource reservation features](任务09-V3.0-资源预约核心业务开发/README.md) |
| 10 | V3.1 | [Refactoring to a three-layer architecture](任务10-V3.1-三层架构重构/README.md) |
| 11 | V3.2 | [Reservation approval and state machine transitions](任务11-V3.2-预约审核流程与状态机流转开发/README.md) |
| 12 | V3.3 | [Engineering improvements](任务12-V3.3-工程优化/README.md) |
| 13 | V4.0 | [MVC architecture](任务13-V4.0-MVC架构/README.md) |
| 14 | V4.1 | [Advanced Stream operations for statistics and popularity rankings](任务14-V4.1-高阶Stream实现数据统计与热度排行/README.md) |
| 15 | V4.2 | [Automatic logging with custom annotations without modifying business logic](任务15-V4.2-自定义注解实现无侵入自动日志/README.md) |
| 16 | V4.3 | [Generic object utilities using reflection](任务16-V4.3-反射机制实现通用对象工具/README.md) |
| 17 | V5.0 | [Asynchronous tasks with thread pools and thread safety](任务17-V5.0-线程池异步任务与并发安全优化/README.md) |
| 18 | V5.1 | [Extending behavior with Adapter and Decorator patterns without modifying business logic](任务18-V5.1-适配器、装饰器模式业务无侵入拓展/README.md) |
| 19 | V5.2 | [Persistence layer refactoring: configuration-driven storage options](任务19-V5.2-持久层重构-配置驱动多存储方案/README.md) |
| 20 | V6.0 | [Additional user interfaces](任务20-V6.0-项目视图拓展/README.md) |

Task 18's title conflicts with its detailed requirements. The directory retains the original title for now; see the requirements review for the scope discussion.

Task 14 adds administrator menu options **9. Resource statistics, 10. TopN popular resources, 11. User statistics, 12. Global reservation status**. Reports are computed from current reservation states, preserve groups for deleted resources, and are unavailable to students. See the [Task 14 run and acceptance instructions](任务14-V4.1-高阶Stream实现数据统计与热度排行/README.md).

## Task 15 Automatic Operation Logs — November 16, 2026

Based on accepted Task 14 commit `402085d`, Task 15 uses `@OperationLog` and native reflection to record registration/login, resource changes, and reservation submissions/reviews/cancellations without changing original Service method bodies. Logs append to an independent `operation_log.txt`. Administrators gain **13. All operation logs** and **14. Logs by username**, sorted newest first; students cannot query logs. Passwords are redacted, and logging failures do not replace business results.

The full suite passed on JDK 17.0.18 with --release 8: 97 new logging checks, 4 restart checks, five log-file failure cases, 17 paired baseline sessions, and 47 back-navigation sessions, alongside existing regressions. A standalone copy also compiled, started and passed tests without other task directories. JDK 8 runtime, Windows wrappers and IDE buttons were not verified. Both source and target formal business files are absent, so zero records were migrated; sample data was not represented as real migration data.

Run `sh run.sh` and `python3 tests/run-tests.py` inside Task 15. See the [Task 15 README](任务15-V4.2-自定义注解实现无侵入自动日志/README.md) for deliverables, results and acceptance steps. Status: **self-tested, awaiting user acceptance**. No staging, commit, push or Task 16 work was performed.

## Global Navigation (November 13, 2026)

Every input page in Tasks 01–14 now accepts lowercase `back`, with one startup hint per run. It cancels an unfinished form and returns to its menu; at a role menu it logs out to the home menu, and at home it stays there. Task 1 has no home menu: it returns to the username prompt and still ends after one completed login check. Enter `\back` for literal text `back`; numeric menu options and business rules remain intact. This is a user-requested interaction improvement that preserves the assignment's architecture stages; see the [requirements review](docs/需求核对.md#用户追加的全局返回2026-11-13).

Under JDK `1.8.0_341`, the 14 independent projects passed 449 console sessions and 2482 assertions covering 216 form input points. Existing regression suites for Tasks 10–14 also passed. Hashes of 273 protected business source, application data and script files remained unchanged. No persistence format changed or existing data was overwritten; backups and logs remain in ignored task-local `data/` directories. Each task README records results, launch instructions and acceptance steps. GUI run buttons were not clicked this round; all stages await user verification.

```powershell
python .\tests\verify-back.py --jdk D:/jdk8
```

Add `--tasks 14` to test one stage. Tests use isolated demo data without reading or overwriting real accounts. Python is only a test tool; applications still use the JDK and existing scripts. That navigation round did not implement Tasks 15–20 early. Task 15 now inherits the rule; Tasks 16–20 remain unimplemented.

## UML Architecture Diagrams

Task 13's MVC diagram follows **View → Controller → Service → DAO**. The PlantUML source and directly rendered SVG/PNG now use the paired Service / ServiceImpl names. This is a PlantUML preview, not a new Visual Paradigm export. The native VPP and files under vpp-preview remain historical pre-rename versions, pending an update and reopening in VP 16.2. The diagram represents Task 13, without Task 14's statistics additions.

[![Task 13 MVC class diagram: View and Controller](任务13-V4.0-MVC架构/docs/uml/architecture.png)](任务13-V4.0-MVC架构/docs/uml/architecture.png)

- [All four UML diagrams and architecture evolution notes](任务13-V4.0-MVC架构/README.md#uml-交付物): View / Controller, Service / DAO / singletons / factory, entities and states, and exception inheritance.
- [Editable Visual Paradigm project (VPP)](任务13-V4.0-MVC架构/docs/uml/任务13-MVC架构.vpp) · [Main diagram SVG](任务13-V4.0-MVC架构/docs/uml/architecture.svg) · [PlantUML source](任务13-V4.0-MVC架构/docs/uml/architecture.puml).
- [Task 10 three-layer architecture UML](任务10-V3.1-三层架构重构/README.md): the stage's three-layer structure is preserved with navigation updated; earlier diagrams remain in backups and the baseline commit for comparison with MVC.

## Iteration Workflow

1. Develop tasks in numerical order, completing the current task and obtaining user acceptance before proceeding. Record actual test results.
2. When starting the next task, inherit the previous accepted task's source code and persistent application data, then make incremental changes. Back up data, verify compatibility, and convert it if necessary. Do not copy build output, machine-specific IDE settings, or unrelated local credentials.
3. Preserve completed task directories as records of each stage. Do not copy later features back into earlier tasks. Each task's change description records its additions, refactoring, and regression checks.
4. Keep Git history in the repository root; do not initialize nested repositories inside task directories. For an independent delivery, provide the complete task directory and its documentation.

## Development and Acceptance Constraints

- Use JDK 8 or later and plain Java without third-party frameworks. Task 1 must be verified with JDK 8 as specified in the assignment.
- Task 1 keeps all code in `SystemMain`. Under the user's confirmed requirements, Task 7 retains existing packages without adding architectural layers, which differs from the original requirement to avoid both packages and layers. A three-layer architecture is introduced in Task 10, followed by MVC in Task 13.
- The console version is the required baseline. Additional user interfaces in Task 20 are optional for bonus points and must not modify core business code.
- Tasks 10 and 13 deliver UML class diagrams for the three-layer and MVC architectures, respectively. Task 13 includes PlantUML, SVG / PNG, and a native VPP reopened and checked with Visual Paradigm 16.2, while preserving Task 10's diagrams. Final delivery includes source code, a project report, and a recorded demonstration.
- Runtime user, resource, reservation, and log files are ignored by Git. Demo data intended for version control belongs in each task's `samples/` directory, with loading instructions.
- Tasks 01–14 were independently compiled and run with JDK `1.8.0_341`. The corrected Tasks 13 and 14 passed package checks, business regression, state machine, console, 14 paired baseline sessions each, and existing-data compatibility checks. Task 14 also passed all four report regressions. See each task README for historical navigation acceptance; Task 14 is now explicitly accepted, while IDE GUI buttons still await verification. Task 15 has passed compilation with --release 8 and self-tests on JDK 17.0.18 (not JDK 8 runtime verification); Tasks 16–20 are not yet implemented or tested.

## Grading

| Category | Points |
| --- | --- |
| Completion of iterative task features | 40 |
| Code quality and engineering practices | 20 |
| Architecture, design patterns, and design principles | 15 |
| UML modeling | 10 |
| Final documentation and demonstration | 15 |

The base score is 100 points. Additional user interfaces can earn up to 5 bonus points, and advanced persistence in Task 19 can earn up to 5 bonus points, for a total bonus of up to 10 points.

## Contributing

Contributions through Issues and Pull Requests are welcome. Read the [contribution guide](CONTRIBUTING.md) before starting for the development scope, environment conventions, self-testing and user acceptance process, and commit and PR requirements.

## Commits and Pull Requests

Commit messages and PR titles follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/#specification), with descriptions written in Chinese, for example:

```text
feat(task01): 实现管理员和学生登录
fix(task02): 修复重复用户名校验遗漏的问题
refactor(task10): 完成三层架构重构
docs(task13): 更新 MVC 类图与运行说明
```

PR descriptions must be written in Chinese and explain the original problem, its cause, the solution, and the test methods and results. State which tests were not run and why. The template is available at [.github/pull_request_template.md](.github/pull_request_template.md).

## License

The code and documentation authored for this repository are licensed under the [MIT License](LICENSE), with the notice `Copyright (c) 2026 Felix-cy110`. Rights to third-party materials, including the original course assignment and grading criteria, remain with their respective owners; those materials are not relicensed by this license.
