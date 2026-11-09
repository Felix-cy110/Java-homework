param(
    [string]$JdkHome = ''
)

$ErrorActionPreference = 'Stop'

# 优先使用指定 JDK，其次使用 JAVA_HOME，最后查找 PATH 中的编译器。
if (-not $JdkHome) {
    $JdkHome = $env:JAVA_HOME
}
if ($JdkHome) {
    $taskCompiler = Join-Path $JdkHome 'bin\javac.exe'
} else {
    $taskCompiler = (Get-Command javac.exe -ErrorAction Stop).Source
}
$taskJava = Join-Path (Split-Path -Parent $taskCompiler) 'java.exe'
if (-not (Test-Path -LiteralPath $taskCompiler -PathType Leaf) -or
    -not (Test-Path -LiteralPath $taskJava -PathType Leaf)) {
    throw 'A complete JDK is required. Pass -JdkHome with the JDK directory.'
}

# 固定可执行文件路径，切换工作目录后仍能使用相对指定的 JDK。
$taskCompiler = (Resolve-Path -LiteralPath $taskCompiler).Path
$taskJava = (Resolve-Path -LiteralPath $taskJava).Path

# 以脚本所在目录运行，避免从仓库根目录启动时混入其他任务的同名类。
Push-Location -LiteralPath $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path out | Out-Null
    chcp.com 65001 | Out-Null

    # 先一起编译两个源文件；编译失败时不启动旧的 class 文件。
    & $taskCompiler -encoding UTF-8 -Xlint:all -classpath out -d out src\User.java src\SystemMain.java
    if ($LASTEXITCODE -ne 0) {
        throw 'Compilation failed. SystemMain was not started.'
    }

    & $taskJava '-Dfile.encoding=UTF-8' -cp out SystemMain
    if ($LASTEXITCODE -ne 0) {
        throw 'SystemMain exited with an error.'
    }
} finally {
    Pop-Location
}
