"""逐阶段独立编译并验证返回菜单；仅使用隔离的演示数据，不读取正式账号。"""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import argparse
import json
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent
HOME = "1.登录  2.注册  0.退出"
HINT = "提示：输入 back 返回上一级菜单"


def verify(number, jdk):
    """每个阶段有独立编译输出、工作目录和会话日志，断言针对实际菜单行为。"""
    task = next(ROOT.glob(f"任务{number:02d}-*"))
    work = task / "data" / ("back-test-" + uuid.uuid4().hex)
    classes = work / "classes"
    classes.mkdir(parents=True)
    java = str(jdk / "bin/java.exe")
    compile_result = subprocess.run(
        [str(jdk / "bin/javac.exe"), "-encoding", "UTF-8", "-Xlint:all",
         "-classpath", str(classes), "-d", str(classes)]
        + [str(p) for p in (task / "src").rglob("*.java")],
        capture_output=True, encoding="utf-8", timeout=120)
    (work / "compile.log").write_text(compile_result.stdout + compile_result.stderr, encoding="utf-8")
    assert compile_result.returncode == 0, f"任务 {number} 编译失败：{work}"
    main = "SystemMain" if number <= 2 else "com.campus.reservation.SystemMain"
    checks, sessions, input_points = 0, 0, 0
    fixtures = {}
    if number >= 4:
        fixtures["user.txt"] = (
            "admin\t123456\tadmin\t2026-01-01T09:00\n"
            "student_test\t123456\tstudent\t2026-01-01T09:00\n")
    if number >= 6:
        fixtures["resource.txt"] = "r1\t测试教室\t教室\tAVAILABLE\t测试楼\n"
        fixtures["reserve.txt"] = ""
    if number >= 9:
        fixtures["reserve.txt"] = (
            "p1\tr1\tstudent_test\t2026-01-01T09:00\t2099-01-01T09:00"
            "\t2099-01-01T10:00\tPENDING\t演示备注" + ("\t\t" if number >= 11 else "") + "\n")

    def check(value, name):
        nonlocal checks
        assert value, f"任务 {number} / 会话 {sessions} / {name}，日志：{work}"
        checks += 1

    def run(lines, unchanged=True, case=None):
        """对取消操作逐字节比较文件，并保存复现输入和输出。"""
        nonlocal sessions
        sessions += 1
        folder = case or work / f"case-{sessions}"
        if not folder.exists():
            folder.mkdir()
            for name, value in fixtures.items():
                (folder / name).write_text(value, encoding="utf-8")
        before = {p.name: p.read_bytes() for p in folder.glob("*.txt")}
        result = subprocess.run(
            [java, "-Dfile.encoding=UTF-8", "-cp", str(classes), main],
            cwd=folder, input="\n".join(lines) + ("\n" if lines else ""),
            encoding="utf-8", capture_output=True, timeout=60)
        (work / f"session-{sessions}.log").write_text(
            result.stdout + result.stderr, encoding="utf-8")
        (work / f"input-{sessions}.json").write_text(
            json.dumps(lines, ensure_ascii=False), encoding="utf-8")
        check(result.returncode == 0, "正常退出")
        check("Exception" not in result.stderr, "无未捕获异常")
        check(result.stdout.count(HINT) == 1, "启动提示仅一次")
        if unchanged:
            check(before == {p.name: p.read_bytes() for p in folder.glob("*.txt")}, "取消不写文件")
        return result.stdout, folder

    if number == 1:
        for lines, expected in [
            (["back", "admin", "back", "student", "123456"], "登录成功，欢迎学生"),
            ([" back ", "admin", "123456"], "登录成功，欢迎管理员"),
            (["admin", "wrong"], "登录失败"),
            (["admin", "back"], "未读取到账号"),
            ([], "未读取到账号"),
        ]:
            out, _ = run(lines)
            check(expected in out, "固定账号及返回登录起点")
        input_points = 2
    else:
        out, _ = run(["back", " back ", "0"])
        check(out.count(HOME) == 3 and "无效" not in out, "首页返回仍停留")
        for operation in ("1", "2"):
            for prefix in ([], ["new_cancelled"]):
                out, _ = run([operation] + prefix + ["back", "0"])
                check(out.count(HOME) == 2 and "失败" not in out, "账号密码取消回首页")
                input_points += 1
        out, _ = run(["2", "new_cancelled", "back", "2", "new_cancelled",
                      "123456", "1", "new_cancelled", "123456"]
                     + (["back"] if number >= 6 else []) + ["0"], unchanged=False)
        check(out.count("注册成功") == 1 and "登录成功" in out, "取消后可重新注册登录")
        # 字面保留字、大小写、前缀及普通反斜杠仍可作为正常账号。
        for literal in (r"\back", r"\\back", "BACK", "back123", r"\ordinary"):
            out, folder = run(["2", literal, "123456", "1", literal, "123456"]
                              + (["back"] if number >= 6 else []) + ["0"], unchanged=False)
            check("注册成功" in out and "登录成功" in out, "转义或普通文本账号")
            if number >= 4:
                out, _ = run(["1", literal, "123456"]
                             + (["back"] if number >= 6 else []) + ["0"], case=folder)
                check("登录成功" in out, "字面账号重启登录")
        for prefix in ([], ["1"], ["1", "student_test"]):
            out, _ = run(prefix)
            check("程序退出" in out, "EOF 沿用退出行为")

    if number >= 6:
        admin = ["1", "admin", "123456"]
        student = ["1", "student_test", "123456"]
        for login in (admin, student):
            out, _ = run(login + ["back", "back", "0"])
            check(out.count("已退出登录") == 1 and out.count(HOME) == 3,
                  "角色菜单返回首页并清除登录")
        forms = [
            (admin, "1", ["新教室", "教室", "测试楼"]),
            (admin, "3", ["r1", "新名称", "教室", "新位置", "DISABLED"]),
            (admin, "4", ["r1"]),
        ]
        if number >= 8:
            forms += [(admin, "2", ["教室"]), (student, "2", ["教室"])]
        if number >= 9:
            forms += [
                (student, "5", ["r1", "2099-02-01T09:00", "2099-02-01T10:00", "备注"]),
                (student, "7", ["p1"]),
                (admin, "6", ["p1", "REJECT", "驳回原因"]),
                (admin, "7", ["r1", "student_test", "PENDING"]),
            ]
        if number >= 14:
            forms += [(admin, "10", ["3"])]
        # 每个输入点都取消一次，再执行查询以确认仍停留当前角色。
        query = ["2", ""] if number >= 8 else ["2"]
        for login, option, values in forms:
            for index in range(len(values)):
                out, _ = run(login + [option] + values[:index] + ["back"]
                             + query + ["back", "0"])
                check(out.count(HOME) == 2 and out.count("已退出登录") == 1,
                      f"操作 {option} 第 {index + 1} 项返回所属菜单")
                check("失败" not in out and "无效" not in out, "返回不当作非法字段")
                check("r1" in out, "取消后可继续查询")
                input_points += 1
        out, folder = run(admin + ["1", "临时", "back", "1", r"\back", "教室",
                                   r"\back", "back", "0"], unchanged=False)
        rows = (folder / "resource.txt").read_text(encoding="utf-8").splitlines()
        check(len(rows) == 2 and rows[-1].split("\t")[1:3] == ["back", "教室"]
              and rows[-1].split("\t")[4] == "back", "取消后完整新增与字段转义")
        out, _ = run(admin + query + ["back", "0"], case=folder)
        check("r1" in out and "back" in out, "资源重启可查")
        out, _ = run(admin + ["3", "r1", "back"])
        check("程序退出" in out, "表单返回后 EOF")

    result = {"task": number, "result": "PASS", "checks": checks, "sessions": sessions,
              "cancelInputPoints": input_points, "work": str(work), "classes": str(classes)}
    (work / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--tasks", type=int, nargs="+", default=list(range(1, 15)))
    args = parser.parse_args()
    if any(n < 1 or n > 14 for n in args.tasks):
        parser.error("仅测试当前已实现的任务 1–14")
    # 并行的是互不共享数据的 Java 进程；每个任务仍独立编译运行。
    with ThreadPoolExecutor(max_workers=2) as pool:
        pending = [pool.submit(verify, n, args.jdk.resolve()) for n in args.tasks]
        for done in as_completed(pending):
            print(json.dumps(done.result(), ensure_ascii=True), flush=True)
