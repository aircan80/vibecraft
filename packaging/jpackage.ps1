# Builds a Windows package for WoW Forever Browser with jpackage.
# Usage: packaging\jpackage.ps1                   (msi, needs WiX 3.x)
#        packaging\jpackage.ps1 -Type app-image   (self-contained folder, no WiX needed)
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Type = 'msi'
)
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$JarName = 'wow-forever-browser.jar'
$Jar = Join-Path $Root "app\target\$JarName"
$Dest = Join-Path $Root 'packaging\dist'
$Stage = Join-Path $Dest 'input'

if (-not (Test-Path $Jar)) {
    Write-Error "Missing $Jar`nBuild it first: ./mvnw -B -pl app -am package -DskipTests"
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

# Stage only the shaded jar so test jars and class dirs are not bundled.
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force $Stage | Out-Null
Copy-Item $Jar $Stage

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
Write-Host "Done. Output in $Dest"
