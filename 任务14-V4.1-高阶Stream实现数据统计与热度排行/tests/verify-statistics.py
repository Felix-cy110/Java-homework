"""使用真实输入输出核对四类报表、非法参数、学生菜单和只读性。"""
from pathlib import Path
import subprocess
import shutil
import sys
import uuid

TASK = Path(__file__).resolve().parent.parent
CLASSES = Path(sys.argv[1]).resolve()
JAVA = sys.argv[2]
ROOT = TASK / 'data' / ('statistics-cli-' + uuid.uuid4().hex)
ROOT.mkdir(parents=True)
for kind in ('user', 'resource', 'reserve'):
    shutil.copy2(TASK / f'samples/{kind}-demo.txt', ROOT / f'{kind}.txt')
users = [line.split('	') for line in (ROOT / 'user.txt').read_text(encoding='utf-8-sig').splitlines() if line]
admin = next(row for row in users if row[2] == 'admin')
student = next(row for row in users if row[2] == 'student')
checks = 0
sessions = 0


def check(condition, message):
    """失败仅报告场景名称，日志保存在忽略目录中。"""
    global checks
    assert condition, message
    checks += 1


def run(lines):
    """每次启动独立 JVM，避免测试依赖上次会话内存。"""
    global sessions
    sessions += 1
    process = subprocess.run([JAVA, '-Dfile.encoding=UTF-8', '-cp', str(CLASSES),
                              'com.campus.reservation.SystemMain'], cwd=ROOT,
                             input=chr(10).join(lines) + chr(10), encoding='utf-8',
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
    (ROOT / f'session-{sessions}.log').write_text(process.stdout, encoding='utf-8')
    check(process.returncode == 0, 'console exits successfully')
    check('Exception in thread' not in process.stdout and '	at com.' not in process.stdout, 'no stack trace')
    return process.stdout


# 删除资源的历史预约与零热度资源混合，期望值按固定样本手工核对。
(ROOT / 'resource.txt').write_text(chr(10).join([
    chr(9).join(['r1', '统计教室', '教室', 'OCCUPIED', 'A101']),
    chr(9).join(['r2', '统计设备', '设备', 'AVAILABLE', 'A102'])]) + chr(10), encoding='utf-8')
records = [('one', 'r1', 'PASS'), ('two', 'r1', 'REJECT'), ('three', 'r1', 'PENDING'),
           ('four', 'r2', 'CANCEL'), ('five', 'gone', 'REJECT')]
(ROOT / 'reserve.txt').write_text(''.join(
    '	'.join([identity, resource, student[0], '2030-01-01T10:00', '2030-01-02T10:00',
               '2030-01-02T11:00', status, '演示']) + chr(10)
    for identity, resource, status in records), encoding='utf-8')
# 演示账号名不是默认 admin；先完成原有启动补建，再核对统计查询的只读性。
run(['0'])
before = {kind: (ROOT / f'{kind}.txt').read_bytes() for kind in ('user', 'resource', 'reserve')}
output = run(['1', admin[0], admin[1], '9', '10', '1', '11', '12', '0', '0'])
for expected in ('V4.1', '资源预约统计报表：', '热门资源 TOP 1：', '用户预约统计报表：', '全局预约状态汇总：',
                 '资源已删除', '1 | r1 | 统计教室 | 3 | 1 | 1 | 0 | 33.33%',
                 student[0] + ' | 5 | 1 | 2', 'PENDING | 1', 'PASS | 1', 'REJECT | 2', 'CANCEL | 1'):
    check(expected in output, 'expected report output: ' + expected)
top_section = output.split('热门资源 TOP 1：', 1)[1].split('管理员菜单：', 1)[0]
check('r1 | 统计教室' in top_section and '资源已删除' not in top_section and 'r2 |' not in top_section, 'Top1 truncation')
check(before == {kind: (ROOT / f'{kind}.txt').read_bytes() for kind in before}, 'reports never persist')

for value in ('0', '-1', 'abc', '1.5', '2147483648', ''):
    output = run(['1', admin[0], admin[1], '10', value, '12', '0', '0'])
    check('统计失败：' in output or '操作失败：' in output, 'friendly invalid TopN ' + value)
    check('全局预约状态汇总：' in output, 'continues after bad TopN')
output = run(['1', admin[0], admin[1], '10'])
check('输入已结束' in output, 'EOF while entering TopN')
output = run(['1', student[0], student[1], '9', '10', '11', '12', '0', '0'])
check(output.count('无效菜单选项') == 4, 'student rejects all report options')
check('9.资源预约统计' not in output and '全局预约状态汇总：' not in output, 'student has no reports')

(ROOT / 'reserve.txt').write_text('', encoding='utf-8')
output = run(['1', admin[0], admin[1], '9', '10', '10', '11', '12', '0', '0'])
check(output.count('暂无符合条件的记录。') == 3, 'empty grouped reports')
check(all(status + ' | 0' in output for status in ('PENDING', 'PASS', 'REJECT', 'CANCEL')), 'four zero states')
print(f'PASS statistics CLI: {checks} checks, {sessions} sessions')
