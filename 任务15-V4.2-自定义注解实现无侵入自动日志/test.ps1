param([string]$JdkHome = '', [switch]$Baseline)
$ErrorActionPreference = 'Stop'
# Python 仅组织隔离测试；应用运行仍只依赖原生 JDK。
if (-not $JdkHome) { $JdkHome = $env:JAVA_HOME }
$argsList = @((Join-Path $PSScriptRoot 'tests/run-tests.py'))
if ($JdkHome) { $argsList += @('--jdk', $JdkHome) }
if ($Baseline) { $argsList += '--baseline' }
& python @argsList
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
