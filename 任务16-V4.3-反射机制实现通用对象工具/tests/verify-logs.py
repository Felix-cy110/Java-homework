"""真实控制台日志验收：新增菜单、权限、筛选、back、脱敏和重启。"""
from pathlib import Path
import json
import shutil
import subprocess
import sys
import uuid

TASK = Path(__file__).resolve().parent.parent
CLASSES = Path(sys.argv[1]).resolve()
JAVA = sys.argv[2]
ROOT = TASK / 'data' / ('logs-cli-' + uuid.uuid4().hex)
ROOT.mkdir(parents=True)
for kind in ('user', 'resource', 'reserve'):
    shutil.copyfile(TASK / 'samples' / (kind + '-demo.txt'), ROOT / (kind + '.txt'))
rows = [line.split('\t') for line in (ROOT / 'user.txt').read_text(encoding='utf-8-sig').splitlines()]
admin = next(row for row in rows if row[2] == 'admin')
student = next(row for row in rows if row[2] == 'student')
checks, sessions = 0, 0


def check(ok, name):
    """断言不输出原始账号密码。"""
    global checks
    if not ok:
        raise AssertionError(name)
    checks += 1


def run(lines):
    """每次新 JVM 在隔离工作目录内运行，日志读取与业务文件均不越界。"""
    global sessions
    sessions += 1
    p = subprocess.run([JAVA, '-Dfile.encoding=UTF-8', '-cp', str(CLASSES),
                        'com.campus.reservation.SystemMain'], cwd=ROOT,
                       input='\n'.join(lines)+'\n', capture_output=True, text=True, timeout=20)
    (ROOT / ('session-' + str(sessions) + '.log')).write_text(p.stdout + p.stderr)
    check(p.returncode == 0, '正常退出')
    check('Exception in thread' not in p.stdout+p.stderr, '无堆栈输出')
    check(p.stdout.count('提示：输入 back 返回上一级菜单') == 1, '提示只一次')
    return p.stdout


out=run(['1', admin[0], admin[1], '13', '14', admin[0], '0', '0'])
check('13.查看全部操作日志' in out and '14.按用户名查询日志' in out, '管理员菜单')
check('OperationLogRecord{' in out and 'operationDesc=\'用户登录\'' in out, '日志实际展示')
check('UserServiceImpl.loginUser' in out, '方法名展示')
check(admin[1] not in out, '查询不泄露密码')
path=ROOT/'operation_log.txt'
check(len(path.read_text().splitlines()) == 1, '读日志不产生额外日志')
out=run(['1', student[0], student[1], '13', '14', '0', '0'])
check('查看全部操作日志' not in out and '按用户名查询日志' not in out, '学生无菜单')
check('OperationLogRecord{' not in out, '学生输入编号也看不到日志')
check(len(path.read_text().splitlines()) == 2, '学生只记录登录')
out=run(['1', admin[0], admin[1], '14', student[0], '0', '0'])
shown=[line for line in out.splitlines() if 'OperationLogRecord{' in line]
check(len(shown)==1 and ("username='"+student[0]+"'") in shown[0], '用户名过滤精确')
check(len(path.read_text().splitlines()) == 3, '重启加载追加而非覆盖')
before={k:(ROOT/(k+'.txt')).read_bytes() for k in ['user','resource','reserve']}
out=run(['1', admin[0], admin[1], '14', 'back', '13', 'back', 'back', '0'])
check(len(path.read_text().splitlines()) == 4, '取消筛选不触发日志写入')
check('已退出登录' in out, '角色返回首页')
check(before=={k:(ROOT/(k+'.txt')).read_bytes() for k in before}, '日志查询不修改业务文件')
out=run(['2', 'cancel_log_user', 'back', '0'])
check(len(path.read_text().splitlines()) == 4, '取消注册不产生日志')
check(before['user']==(ROOT/'user.txt').read_bytes(), '取消注册不提交')
out=run(['1', admin[0], admin[1], '14', r'\back', '0', '0'])
check('暂无符合条件的记录' in out, '字面back作为用户名筛选')
check(len(path.read_text().splitlines()) == 5, '转义查询不写日志')
out=run(['1', admin[0], admin[1], '14', '   ', '0', '0'])
check('操作失败' in out or '不能为空' in out, '空用户名提示')
check(len(path.read_text().splitlines()) == 6, '空查询不写日志')
# 故障时仍能启动和登录，管理员能看到失败提示，保留损坏文件字节。
path.write_text('invalid\n')
broken=path.read_bytes()
out=run(['1',admin[0],admin[1],'13','0','0'])
check('操作日志加载失败' in out, '日志损坏提示')
check(path.read_bytes()==broken, '不覆盖损坏日志')
print(json.dumps({'result':'PASS','checks':checks,'sessions':sessions},ensure_ascii=False))
