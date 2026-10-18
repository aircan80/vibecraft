# Builds a portable Windows app-image + ZIP with its own Java runtime.
# Usage: packaging\jpackage.ps1                   (portable folder + ZIP)
#        packaging\jpackage.ps1 -Type app-image   (self-contained folder, no WiX needed)
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Type = 'app-image',
    [string]$JarPath = '',
    [switch]$PrivateQa,
    [string]$ReleaseDataDir = '',
    [string]$ReleaseDataManifest = '',
    [string]$Version = '0.1.0',
    [string]$NativeVersion = ''
)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^[A-Za-z0-9][A-Za-z0-9.+_-]*$') { throw 'Invalid product Version.' }
if (-not $NativeVersion) { $NativeVersion = $Version }
if ($NativeVersion -notmatch '^\d+(\.\d+){0,2}$') {
    throw 'Windows needs numeric -NativeVersion (for example 0.4.1); -Version remains the product release identifier.'
}

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

$withData = [bool]($ReleaseDataDir -or $ReleaseDataManifest)
if ($withData -and (-not $ReleaseDataDir -or -not $ReleaseDataManifest)) {
    throw 'Set -ReleaseDataDir and -ReleaseDataManifest together.'
}
if ($withData -and $PrivateQa) { throw 'Reviewed release-data and private-QA modes cannot be combined.' }

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
Set-Content -Path (Join-Path $Stage 'release-version.txt') -Value "product-version=$Version`nnative-package-version=$NativeVersion" -Encoding UTF8
if ($withData) {
    & java (Join-Path $PSScriptRoot 'ReleaseData.java') $ReleaseDataDir $ReleaseDataManifest $Stage
    if ($LASTEXITCODE -ne 0) { throw 'Release data manifest validation failed.' }
}

$jpArgs = @(
    '--input', $Stage,
    '--main-jar', $JarName,
    '--main-class', 'dev.wowforever.app.Main',
    '--name', 'WoW Forever Browser',
    '--app-version', $NativeVersion,
    '--dest', $Dest,
    '--java-options', '-Djava.awt.headless=false',
    '--java-options', "-Dwowforever.release.version=$Version",
    '--vendor', 'wow-forever-browser contributors',
    '--description', 'Browse World of Warcraft: Forever items and their sources',
    '--type', $Type
)
if ($withData) {
    $jpArgs += @('--java-options', '-Dwowforever.database=$APPDIR/data/wowforever.sqlite',
        '--java-options', '-Dwowforever.data.dir=$APPDIR/data')
}
if ($Type -ne 'app-image') {
    $jpArgs += @('--win-dir-chooser', '--win-menu', '--win-shortcut')
}

Write-Host "Running jpackage (type: $Type)..."
& jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Remove-Item -Recurse -Force $Stage
if ($Type -eq 'app-image') {
    $label = 'wow-forever-browser-' + $Version + '-Windows-' + $env:PROCESSOR_ARCHITECTURE
    if ($withData) { $label += '-with-data' }
    if ($PrivateQa) { $label += '-PRIVATE-QA' }
    Compress-Archive -Path (Join-Path $Dest 'WoW Forever Browser') -DestinationPath (Join-Path $Dest ($label + '.zip')) -Force
}
Write-Host "Done. Output in $Dest"
