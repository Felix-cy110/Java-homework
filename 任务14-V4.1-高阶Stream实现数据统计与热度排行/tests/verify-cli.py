from pathlib import Path
import subprocess
import shutil
import json
import uuid
import sys

# 在任务 14 演示副本中回归原控制台业务，不触碰正式文件。
task = Path(__file__).resolve().parent.parent
classes = Path(sys.argv[1]).resolve()
root = task / ('data/cli-verification-20261110-' + uuid.uuid4().hex)
root.mkdir(exist_ok=False)
for kind in ('user', 'resource', 'reserve'):
    shutil.copy2(task / f'samples/{kind}-demo.txt', root / f'{kind}.txt')
java = sys.argv[2]
checks = 0
sessions = 0


def check(condition):
    global checks
    if not condition:
        raise AssertionError('CLI verification failed; inspect isolated log.')
    checks += 1


def run(lines, expected_code=0):
    global sessions
    sessions += 1
    result = subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(classes),
                             'com.campus.reservation.SystemMain'], cwd=root,
                            input='\n'.join(lines) + '\n', encoding='utf-8',
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
    (root / f'session-{sessions}.log').write_text(result.stdout, encoding='utf-8')
    check(result.returncode == expected_code)
    check('Exception in thread' not in result.stdout and '\tat com.' not in result.stdout)
    return result.stdout


def rows(kind):
    return [line.split('\t') for line in (root / f'{kind}.txt').read_text(encoding='utf-8-sig').splitlines() if line]


users = rows('user')
admin = next(row for row in users if row[2] == 'admin')
student = next(row for row in users if row[0] == 'student_demo')
login_admin = ['1', admin[0], admin[1]]
login_student = ['1', student[0], student[1]]
record_id = rows('reserve')[0][0]
output = run(login_admin + ['8', '6', record_id, 'REJECT', '用途说明不完整', '8', '0', '0'])
check('V4.1' in output and '8.查看待审核预约' in output)
check('预约已驳回' in output)
rejected = rows('reserve')[0]
check(len(rejected) == 10 and rejected[6] == 'REJECT' and rejected[8] == '用途说明不完整' and bool(rejected[9]))
check(rejected[7] == '演示预约记录')
output = run(login_student + ['6', '7', record_id, '0', '0'])
check('用途说明不完整' in output and '不能从 REJECT 变为 CANCEL' in output)
output = run(login_admin + ['6', record_id, 'PASS', '0', '0'])
check('不能从 REJECT 变为 PASS' in output)

# 两条待审核记录分别测试通过后取消与直接取消。
pending = (task / 'samples/reserve-demo.txt').read_text(encoding='utf-8').strip('\r\n').split('\t')
with (root / 'reserve.txt').open('a', encoding='utf-8') as stream:
    for identity in ('demo-pass', 'demo-cancel'):
        pending[0] = identity
        stream.write('\t'.join(pending) + '\n')
output = run(login_admin + ['8', '6', 'demo-pass', 'PASS', '8', '0', '0'])
check('资源已变为 OCCUPIED' in output)
passed = next(row for row in rows('reserve') if row[0] == 'demo-pass')
check(passed[6] == 'PASS' and bool(passed[9]))
check(next(row for row in rows('resource') if row[0] == passed[1])[3] == 'OCCUPIED')
output = run(login_student + ['7', 'demo-pass', '7', 'demo-cancel', '6', '0', '0'])
check('预约已取消' in output)
cancelled = next(row for row in rows('reserve') if row[0] == 'demo-pass')
check(cancelled[6] == 'CANCEL' and cancelled[9] == passed[9])
check(next(row for row in rows('resource') if row[0] == passed[1])[3] == 'AVAILABLE')
check(next(row for row in rows('reserve') if row[0] == 'demo-cancel')[9] == '')
output = run(login_admin + ['8', '6', 'demo-cancel', 'UNKNOWN', '0', '0'])
check('审核结果只能为 PASS 或 REJECT' in output)
check('ReserveRecord{' not in output)
(root / 'reserve.txt').write_text('损坏记录\n', encoding='utf-8')
output = run(['0'], expected_code=1)
check('数据加载失败' in output and 'reserve.txt' in output)
print(json.dumps({'cliSessions': sessions, 'checks': checks, 'result': 'PASS'}))
