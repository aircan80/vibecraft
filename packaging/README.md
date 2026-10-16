# Packaging

These scripts wrap the shaded jar into a native package with `jpackage` (JDK 21).
The package bundles a Java runtime, so end users need no separate JDK.

## Build the jar first

```
./mvnw -B -pl app -am package -DskipTests
```

This produces `app/target/wow-forever-browser.jar`. Both scripts refuse to run without it.

## Prerequisites

| OS | Needed |
|---|---|
| All | JDK 21 with `jpackage` on `PATH` |
| Windows | WiX Toolset 3.x (`candle.exe`, `light.exe` on `PATH`) for `msi`/`exe`. Not needed for `app-image`. |
| macOS | Xcode command-line tools (`xcode-select --install`) |
| Linux | `fakeroot` and `dpkg` for `deb` (`rpmbuild` for `rpm`) |

## Run

Windows (PowerShell):

```
powershell -File packaging\jpackage.ps1                 # msi, falls back to app-image with a warning if WiX is missing
powershell -File packaging\jpackage.ps1 -Type app-image # self-contained folder with an .exe launcher
```

macOS / Linux:

```
packaging/jpackage.sh                  # dmg on macOS, deb on Linux
PKG_TYPE=rpm packaging/jpackage.sh     # override the package type
```

The scripts can be started from any working directory.

## Output

Everything lands in `packaging/dist/` (git-ignored):

- Windows: `WoW Forever Browser-0.1.0.msi`, or `WoW Forever Browser/WoW Forever Browser.exe` for `app-image`
- macOS: `WoW Forever Browser-0.1.0.dmg`
- Linux: `wow-forever-browser_0.1.0-1_amd64.deb`

Installers are built per OS; jpackage cannot cross-compile.
