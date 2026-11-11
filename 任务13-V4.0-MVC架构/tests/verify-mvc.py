"""核对真实分层边界，并对任务 12/13 执行相同控制台输入进行差异回归。"""
from pathlib import Path
import hashlib
import re
import shutil
import subprocess
import sys
import uuid

task = Path(__file__).resolve().parent.parent
baseline = task.parent / '任务12-V3.3-工程优化'
classes = Path(sys.argv[1]).resolve()
java = Path(sys.argv[2]).resolve()
root = task / 'data' / ('mvc-verification-' + uuid.uuid4().hex)
root.mkdir(parents=True)
package = Path('src/com/campus/reservation')
checks = 0


def check(condition, name):
    """只输出检查名称，业务文件留在隔离目录中。"""
    global checks
    if not condition:
        raise AssertionError(name)
    checks += 1


def digest(path):
    return hashlib.sha256(path.read_bytes()).digest()


# 只归一包声明、导入、服务名和等值枚举文本；其余原业务源码必须保持一致。
def canonical_source(path):
    text = path.read_text(encoding='utf-8-sig').replace('ReserveRecordService', 'ReserveService')
    for status in ('AVAILABLE', 'OCCUPIED', 'DISABLED'):
        text = text.replace('ResourceStatus.' + status + '.name()', chr(34) + status + chr(34))
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    return chr(10).join(line.strip() for line in text.splitlines()
                       if line.strip() and not line.startswith(('package ', 'import ')))

mapping = {'entity/Result.java': 'common/Result.java',
           'entity/ReserveStatus.java': 'enums/ReserveStatus.java',
           'exception/BusinessException.java': 'common/BusinessException.java',
           'service/ReserveService.java': 'service/ReserveRecordService.java'}
for name in ('service', 'dao', 'entity', 'exception', 'utils'):
    for path in (baseline / package / name).glob('*.java'):
        relative = path.relative_to(baseline / package).as_posix()
        target = task / package / mapping.get(relative, relative)
        check(target.is_file(), relative + ' migrated target exists')
        check(canonical_source(path) == canonical_source(target), relative + ' original behavior preserved')

# 原文要求的包和类型必须存在，且旧路径不能遗留重复类。
required = {'entity': {'User', 'CampusResource', 'ReserveRecord'},
            'enums': {'ReserveStatus', 'ResourceStatus'}, 'common': {'Result', 'BusinessException'},
            'view': {'MainView', 'AdminView', 'StudentView'},
            'controller': {'UserController', 'ResourceController', 'ReserveController'},
            'service': {'UserService', 'ResourceService', 'ReserveRecordService'},
            'dao': {'UserDao', 'ResourceDao', 'ReserveRecordDao', 'DaoFactory'}}
for folder, names in required.items():
    for name in names:
        path = task / package / folder / (name + '.java')
        check(path.is_file(), folder + '/' + name + ' exists')
        check('package com.campus.reservation.' + folder + ';' in path.read_text(encoding='utf-8'), name + ' package')
for old in ('entity/Result.java', 'entity/ReserveStatus.java', 'exception/BusinessException.java', 'service/ReserveService.java'):
    check(not (task / package / old).exists(), 'removed old path ' + old)
check('ResourceStatus.AVAILABLE.name()' in (task / package / 'service/BusinessRules.java').read_text(encoding='utf-8'),
      'resource enum participates in validation')

for path in (task / package).rglob('*.java'):
    text = path.read_text(encoding='utf-8-sig')
    layer = path.parent.name
    if layer == 'view':
        check(not re.search(r'import com\.campus\.reservation\.(?:service|dao|utils)\.', text), 'view imports')
        check(not re.search(r'\b(?:getInstance|Files|Paths|FileUtil)\b', text), 'view no storage')
    if layer == 'controller':
        check(not re.search(r'import com\.campus\.reservation\.(?:view|dao|utils)\.', text), 'controller imports')
    if layer != 'view':
        check(not re.search(r'System\.(?:out|err|in)|\bScanner\b', text), 'all console IO in view')

# 使用相同 JDK 编译旧版到临时目录，不修改旧任务源码或数据。
old_classes = root / 'baseline-classes'
old_classes.mkdir()
sources = [str(p) for p in (baseline / 'src').rglob('*.java')]
subprocess.run([str(java.with_name('javac.exe')), '-encoding', 'UTF-8', '-d', str(old_classes), *sources], check=True)
student = ['1', 'student_demo', 'Student123']
admin = ['1', 'admin', '123456']
# 密码来自已提交的演示文件，不读取正式账号；避免样例口令修改造成误判。
demo_users = [line.split('\t') for line in (task / 'samples/user-demo.txt').read_text(encoding='utf-8-sig').splitlines()]
student[2] = next(row[1] for row in demo_users if row[0] == student[1])
admin[1:3] = next(row[:2] for row in demo_users if row[2] == 'admin')
cases = [
    ['0'], [], ['unknown', '1', '', '0'], ['1', 'nobody', 'bad', '0'],
    ['2', 'mvc_new', '123456', '1', 'mvc_new', '123456', '6', '0', '0'],
    admin + ['2', '', '5', '8', '7', '', '', 'UNKNOWN', '0', '0'],
    admin + ['1', 'MVC测试', '教室', 'A101', '2', 'MVC', '0', '0'],
    admin + ['3', 'demo-available', '修改教室', '教室', 'A102', 'DISABLED', '4', 'demo-disabled', '0', '0'],
    student + ['1', '6', '5', 'demo-available', 'bad-time', '0', '0'],
    student + ['5', 'demo-available', '2030-01-02T10:00', '2030-01-02T11:00', '申请', '6', '0', '0'],
    admin + ['6', 'demo-pending', 'UNKNOWN', '0', '0'],
    student + ['5'], admin + ['3'], ['1', 'admin'],
]


def normalize(text):
    """仅忽略版本、动态 UUID 和运行时生成的时间，不掩盖提示或状态差异。"""
    text = text.replace('V3.3', 'VERSION').replace('V4.0', 'VERSION')
    text = re.sub(r'\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b', 'UUID', text)
    return re.sub(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?', 'TIME', text)


for index, lines in enumerate(cases):
    runs = []
    for label, classpath in [('old', old_classes), ('new', classes)]:
        work = root / f'{index}-{label}'
        work.mkdir()
        for kind in ('user', 'resource', 'reserve'):
            shutil.copy2(task / f'samples/{kind}-demo.txt', work / f'{kind}.txt')
        output = subprocess.run([str(java), '-Dfile.encoding=UTF-8', '-cp', str(classpath),
                                 'com.campus.reservation.SystemMain'], cwd=work,
                                input=('\n'.join(lines) + '\n') if lines else '', encoding='utf-8',
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
        (work / 'console.log').write_text(output.stdout, encoding='utf-8')
        runs.append((output.returncode, normalize(output.stdout),
                     [normalize((work / f'{kind}.txt').read_text(encoding='utf-8-sig'))
                      for kind in ('user', 'resource', 'reserve')]))
    check(runs[0] == runs[1], f'baseline CLI parity {index}')

print(f'PASS MVC boundaries / unchanged packages / CLI parity: {checks} checks, {len(cases)} paired sessions')
