param([string]$JdkHome = '')
$ErrorActionPreference = 'Stop'

# 与运行脚本使用同一 JDK 选择规则，测试仅使用当前任务源码。
if (-not $JdkHome) { $JdkHome = $env:JAVA_HOME }
$taskCompiler = if ($JdkHome) { Join-Path $JdkHome 'bin\javac.exe' } else { (Get-Command javac.exe).Source }
$taskCompiler = (Resolve-Path -LiteralPath $taskCompiler).Path
$taskJava = Join-Path (Split-Path -Parent $taskCompiler) 'java.exe'
$testRoot = Join-Path $PSScriptRoot ('data/self-test-' + [Guid]::NewGuid().ToString('N'))
$testClasses = Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
$taskSources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests') -Filter '*.java' -File | ForEach-Object { $_.FullName })
& $taskCompiler -encoding UTF-8 -Xlint:all -classpath $testClasses -d $testClasses @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }

# 每组测试使用全新工作目录，状态矩阵和损坏文件不影响业务回归数据。
foreach ($testName in @('RegressionTest', 'StateMachineTest', 'PersistenceTest')) {
    $caseRoot = Join-Path $testRoot $testName
    New-Item -ItemType Directory -Path $caseRoot | Out-Null
    $testStart = New-Object System.Diagnostics.ProcessStartInfo
    $testStart.FileName = $taskJava
    $testStart.Arguments = '-Dfile.encoding=UTF-8 -cp "' + $testClasses + '" ' + $testName
    $testStart.WorkingDirectory = $caseRoot
    $testStart.UseShellExecute = $false
    $testStart.CreateNoWindow = $true
    $testStart.RedirectStandardOutput = $true
    $testStart.RedirectStandardError = $true
    $testStart.StandardOutputEncoding = [Text.Encoding]::UTF8
    $testStart.StandardErrorEncoding = [Text.Encoding]::UTF8
    $testProcess = New-Object System.Diagnostics.Process
    $testProcess.StartInfo = $testStart
    try {
        [void]$testProcess.Start()
        $testOutput = $testProcess.StandardOutput.ReadToEndAsync()
        $testError = $testProcess.StandardError.ReadToEndAsync()
        $testProcess.WaitForExit()
        Write-Output $testOutput.Result
        if ($testError.Result) { Write-Output $testError.Result }
        if ($testProcess.ExitCode -ne 0) { throw "$testName failed." }
    } finally {
        $testProcess.Dispose()
    }
}
Write-Output "Test data: $testRoot"
