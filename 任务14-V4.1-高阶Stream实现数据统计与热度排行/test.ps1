param([string]$JdkHome = '')
$ErrorActionPreference = 'Stop'

# 测试文件放在独立目录，避免覆盖用户的正式数据。
if (-not $JdkHome) { $JdkHome = $env:JAVA_HOME }
$compiler = if ($JdkHome) { Join-Path $JdkHome 'bin\javac.exe' } else { (Get-Command javac.exe).Source }
$compiler = (Resolve-Path -LiteralPath $compiler).Path
$java = Join-Path (Split-Path -Parent $compiler) 'java.exe'
$root = Join-Path $PSScriptRoot ('data/self-test-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $root 'classes'
$case = Join-Path $root 'singleton'
New-Item -ItemType Directory -Force -Path $classes, $case | Out-Null
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem (Join-Path $PSScriptRoot 'tests') -Filter '*.java' | ForEach-Object FullName)
& $compiler -encoding UTF-8 -Xlint:all -classpath $classes -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
foreach ($name in @('user', 'resource', 'reserve')) {
    Copy-Item (Join-Path $PSScriptRoot "samples/$name-demo.txt") (Join-Path $case "$name.txt")
}

# 新进程验证损坏文件重新加载并给出受影响文件的提示。
Push-Location $case
try {
    & $java '-Dfile.encoding=UTF-8' -cp $classes SingletonTest
    if ($LASTEXITCODE -ne 0) { throw 'Singleton checks failed.' }
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $java
    $start.Arguments = '-Dfile.encoding=UTF-8 -cp "' + $classes + '" com.campus.reservation.SystemMain'
    $start.WorkingDirectory = $case
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.StandardErrorEncoding = [Text.Encoding]::UTF8
    foreach ($name in @('user', 'resource', 'reserve')) {
        foreach ($file in @('user', 'resource', 'reserve')) {
            Copy-Item (Join-Path $PSScriptRoot "samples/$file-demo.txt") (Join-Path $case "$file.txt") -Force
        }
        [IO.File]::WriteAllText((Join-Path $case "$name.txt"), "invalid`n", [Text.Encoding]::UTF8)
        $process = [Diagnostics.Process]::Start($start)
        try {
            $output = $process.StandardOutput.ReadToEndAsync()
            $errorOutput = $process.StandardError.ReadToEndAsync()
            $process.WaitForExit()
            if ($process.ExitCode -ne 1 -or ($output.Result + $errorOutput.Result) -notmatch "$name.txt") {
                throw "Startup error check failed: $name"
            }
        } finally { $process.Dispose() }
    }
    Write-Output 'PASS restart and startup error check'
} finally {
    Pop-Location
}
# 每个测试使用独立进程和空目录，报表样本不会覆盖正式数据。
foreach ($testName in @('RegressionTest', 'StateMachineTest', 'MvcTest', 'StatisticsTest')) {
    $caseRoot = Join-Path $root $testName
    New-Item -ItemType Directory -Path $caseRoot | Out-Null
    $start.WorkingDirectory = $caseRoot
    $start.Arguments = '-Dfile.encoding=UTF-8 -cp "' + $classes + '" ' + $testName
    $process = [Diagnostics.Process]::Start($start)
    try {
        $output = $process.StandardOutput.ReadToEndAsync()
        $errorOutput = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        Write-Output $output.Result.Trim()
        if ($process.ExitCode -ne 0) { throw $errorOutput.Result }
    } finally { $process.Dispose() }
}
Write-Output "Test classes: $classes"
Write-Output "Test data: $root"
# Python 仅用于隔离控制台回归，不是程序运行依赖。
& python (Join-Path $PSScriptRoot 'tests/verify-cli.py') $classes $java
if ($LASTEXITCODE -ne 0) { throw 'CLI regression failed.' }
& python (Join-Path $PSScriptRoot 'tests/verify-mvc.py') $classes $java
if ($LASTEXITCODE -ne 0) { throw 'MVC boundary or baseline comparison failed.' }
# 四类报表、权限和非法条数由真实控制台会话覆盖。
& python (Join-Path $PSScriptRoot 'tests/verify-statistics.py') $classes $java
if ($LASTEXITCODE -ne 0) { throw 'Statistics CLI checks failed.' }
