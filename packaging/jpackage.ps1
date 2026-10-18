# Builds a portable Windows app-image + ZIP with its own Java runtime.
# Usage: packaging\jpackage.ps1                   (portable folder + ZIP)
#        packaging\jpackage.ps1 -Type app-image   (self-contained folder, no WiX needed)
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Type = 'app-image',
    [string]$JarPath = '',
    [switch]$PrivateQa
)
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$JarName = 'wow-forever-browser.jar'
$Jar = Join-Path $Root "app\target\$JarName"
if ($JarPath) { $Jar = (Resolve-Path $JarPath).Path }
$Dest = Join-Path $Root 'packaging\dist'
$Stage = Join-Path $Dest 'input'

if (-not (Test-Path $Jar)) {
    Write-Error "Missing $Jar`nBuild it first: ./mvnw -B -Psource-only -pl app -am clean package -DskipTests"
    exit 1
}
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Error 'jpackage not found; use a JDK 21 and put its bin folder on PATH.'
    exit 1
}

if ($Type -ne 'app-image') {
    $hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -and (Get-Command light.exe -ErrorAction SilentlyContinue)
    if (-not $hasWix) {
        Write-Warning "WiX 3.x (candle.exe/light.exe) not found on PATH; cannot build '$Type'. Falling back to 'app-image'."
        $Type = 'app-image'
    }
}

$auditArgs = @((Join-Path $PSScriptRoot 'ArchiveAudit.java'), $Jar)
if ($PrivateQa) {
    Write-Warning 'PRIVATE QA ONLY: asset-containing output must not be published.'
    $auditArgs += '--private-qa'
}
& java @auditArgs
if ($LASTEXITCODE -ne 0) { throw 'Archive audit rejected the input JAR.' }

# Stage only the shaded jar so test jars and class dirs are not bundled.
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force $Stage | Out-Null
Copy-Item $Jar (Join-Path $Stage $JarName)

$jpArgs = @(
    '--input', $Stage,
    '--main-jar', $JarName,
    '--main-class', 'dev.wowforever.app.Main',
    '--name', 'WoW Forever Browser',
    '--app-version', '0.1.0',
    '--dest', $Dest,
    '--java-options', '-Djava.awt.headless=false',
    '--vendor', 'wow-forever-browser contributors',
    '--description', 'Browse World of Warcraft: Forever items and their sources',
    '--type', $Type
)
if ($Type -ne 'app-image') {
    $jpArgs += @('--win-dir-chooser', '--win-menu', '--win-shortcut')
}

Write-Host "Running jpackage (type: $Type)..."
& jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Remove-Item -Recurse -Force $Stage
if ($Type -eq 'app-image') {
    $label = 'wow-forever-browser-0.1.0-Windows-' + $env:PROCESSOR_ARCHITECTURE
    if ($PrivateQa) { $label += '-PRIVATE-QA' }
    Compress-Archive -Path (Join-Path $Dest 'WoW Forever Browser') -DestinationPath (Join-Path $Dest ($label + '.zip')) -Force
}
Write-Host "Done. Output in $Dest"
