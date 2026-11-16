"""使用指定 JDK 独立编译和测试任务16；所有业务数据隔离在忽略的 data 目录。"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import sys
import uuid

TASK = Path(__file__).resolve().parent.parent


def main():
    """默认不依赖其他任务；--baseline 额外核对保存的任务15源码。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--baseline', action='store_true')
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    java = str(args.jdk / ('bin/java'+suffix)) if args.jdk else shutil.which('java')
    javac = str(args.jdk / ('bin/javac'+suffix)) if args.jdk else shutil.which('javac')
    if not java or not javac:
        raise SystemExit('需要完整 JDK，请指定 --jdk。')
    java, javac = str(Path(java).resolve()), str(Path(javac).resolve())
    version = subprocess.run([javac,'-version'],capture_output=True,text=True,check=True)
    version_text=(version.stdout+version.stderr).strip()
    work=TASK/'data'/('self-test-'+uuid.uuid4().hex)
    classes=work/'classes';classes.mkdir(parents=True)
    sources=list((TASK/'src').rglob('*.java'))+list((TASK/'tests').glob('*.java'))
    release=[] if '1.8.' in version_text else ['--release','8']
    compile_command=[javac,*release,'-encoding','UTF-8','-Xlint:all','-d',str(classes),*[str(p) for p in sources]]
    compiled=subprocess.run(compile_command,capture_output=True,text=True)
    (work/'compile.log').write_text(compiled.stdout+compiled.stderr)
    if compiled.returncode:raise SystemExit('编译失败，见 '+str(work/'compile.log'))
    results=[]

    def run(name, command, cwd):
        """将真实命令和结果保存到隔离目录，失败立即停止，不改正式数据。"""
        p=subprocess.run(command,cwd=cwd,capture_output=True,text=True,timeout=240)
        (work/(name+'.log')).write_text(p.stdout+p.stderr)
        results.append({'test':name,'command':command,'exit':p.returncode,'output':p.stdout.strip()})
        (work/'result.json').write_text(json.dumps({'jdk':version_text,'compileCommand':compile_command,'results':results},ensure_ascii=False,indent=2))
        print(name+': '+('PASS' if p.returncode==0 else 'FAIL')+' '+p.stdout.strip(),flush=True)
        if p.returncode:raise SystemExit('测试失败，见 '+str(work/(name+'.log')))

    for name in ['RegressionTest','StateMachineTest','SingletonTest','MvcTest','StatisticsTest','OperationLogTest','ObjectReflectUtilTest','ReflectLogTest']:
        cwd=work/name;cwd.mkdir()
        if name=='SingletonTest':
            for kind in ['user','resource','reserve']:
                shutil.copyfile(TASK/'samples'/(kind+'-demo.txt'),cwd/(kind+'.txt'))
        run(name,[java,'-Dfile.encoding=UTF-8','-cp',str(classes),name],cwd)
    run('LogRestartTest',[java,'-cp',str(classes),'LogRestartTest'],work/'OperationLogTest')
    for mode in ['malformed','directory','duplicate','nonewline','write']:
        cwd=work/('failure-'+mode);cwd.mkdir()
        run('LogFailure-'+mode,[java,'-cp',str(classes),'LogFailureTest',mode],cwd)
    # 原三份业务文件损坏仍按旧规则拒绝启动，不让日志容错掩盖业务错误。
    for kind in ['user','resource','reserve']:
        cwd=work/('startup-'+kind);cwd.mkdir()
        for f in ['user','resource','reserve']:
            shutil.copyfile(TASK/'samples'/(f+'-demo.txt'),cwd/(f+'.txt'))
        (cwd/(kind+'.txt')).write_text('invalid\n')
        p=subprocess.run([java,'-cp',str(classes),'com.campus.reservation.SystemMain'],cwd=cwd,input='',capture_output=True,text=True,timeout=20)
        if p.returncode!=1 or kind+'.txt' not in p.stdout+p.stderr:raise AssertionError('原业务损坏文件检查 '+kind)
        (work/('startup-'+kind+'.log')).write_text(p.stdout+p.stderr)
        results.append({'test':'startup-'+kind,'exit':p.returncode,'expectedExit':1,'passed':True})
        (work/'result.json').write_text(json.dumps({'jdk':version_text,'compileCommand':compile_command,'results':results},ensure_ascii=False,indent=2))
    print('PASS original startup failure checks=3',flush=True)
    for script in ['verify-cli.py','verify-statistics.py','verify-logs.py']+(['verify-mvc.py'] if args.baseline else []):
        run(script,[sys.executable,str(TASK/'tests'/script),str(classes),java],TASK)
    run('verify-back.py',[sys.executable,str(TASK/'tests/verify-back.py'),'--jdk',str(Path(java).parent.parent)],TASK)
    print('Test records: '+str(work),flush=True)


if __name__=='__main__':
    main()
