$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$configurationPath = Join-Path $workspaceRoot '.tools\environment.json'
if (-not (Test-Path -LiteralPath $configurationPath)) {
    throw 'Run python tools/bootstrap.py first.'
}
$toolConfiguration = Get-Content -LiteralPath $configurationPath -Raw | ConvertFrom-Json
$env:JAVA_HOME = $toolConfiguration.java_home
$env:ANDROID_HOME = $toolConfiguration.android_sdk
$env:ANDROID_SDK_ROOT = $toolConfiguration.android_sdk
$env:GRADLE_USER_HOME = Join-Path $workspaceRoot '.tools\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $workspaceRoot '.tools\android-user'
$toolPaths = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin'),
    (Join-Path $toolConfiguration.gradle.path 'bin'),
    (Join-Path $toolConfiguration.jadx.path 'bin'),
    (Join-Path $toolConfiguration.mitmproxy.path 'Scripts')
)
$env:Path = ($toolPaths -join ';') + ';' + $env:Path
Write-Output 'Android development and APK analysis tools are available in this terminal.'
