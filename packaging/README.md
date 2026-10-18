# Portable Java application

The default package is a self-contained application folder and compressed archive. Users extract it and
open its native launcher; Java is included. Python, Node and Blender are development tools and are not
included or required by the portable launcher. Interactive 3D rendering is outside the shipped scope.
Portraits are pre-rendered images. Dataset/asset acquisition and the first-run UI must be verified on
the final integrated build before claiming the entire application works without development tools.

## Build (maintainers only)

Use JDK 21, including `java` and `jpackage`, on PATH. Build a clean source-only JAR first:

```
./mvnw -B -Psource-only -pl app -am clean package -DskipTests
packaging/jpackage.sh
```

Windows PowerShell:

```
./packaging/jpackage.ps1
```

Both scripts default to `app-image`, bundle the Java runtime, and stage only the application JAR.
No WiX, package-manager installer, or Java installation is needed by the end user.
Linux/macOS output is a `.tar.gz`; Windows output is a `.zip`, under ignored `packaging/dist/`.
Build separately on each target OS and CPU architecture; jpackage does not cross-compile.
Windows and macOS commands are provided but require native platform acceptance before release.

`ArchiveAudit.java` checks the input JAR before packaging. Public/default packaging rejects gameplay
`data/`, SQLite catalogs, raster images, audio, models and external worker runtimes. Dependency SQLite
JNI libraries remain permitted: Java loads them internally, without a separate end-user tool.
This check supplements source/release review; it is not a legal clearance or an exhaustive content detector.
A developer can explicitly set `ALLOW_PRIVATE_ASSETS=1` on Unix or `-PrivateQa` on Windows for local
asset-containing QA. Such archives are labeled `PRIVATE-QA` and must never be published. The override
still rejects external worker scripts/runtimes.

Unix overrides: `APP_JAR=/absolute/app.jar`, `PACKAGE_DEST=/absolute/output`, `APP_VERSION=0.1.0`.
Windows: `-JarPath C:\absolute\app.jar`. Existing output folders must be removed or a fresh destination
chosen before rebuilding an app-image, so an old launcher cannot silently survive a failed build.

## Run after extracting

- Linux: open `WoW Forever Browser/bin/WoW Forever Browser`.
- macOS: open `WoW Forever Browser.app` (unsigned builds may require the normal macOS security override).
- Windows: open `WoW Forever Browser/WoW Forever Browser.exe`.

Keep the whole folder together; the launcher needs its adjacent runtime and app files. The folder can
be moved without reinstalling. User preferences, datasets and caches currently remain in the normal
per-user application directories; portable application files do not imply USB-contained user data.
A source-only archive intentionally has no gameplay dataset or bulk assets. It needs the app's separately
configured data/asset workflow; do not promise a populated offline catalog from this archive alone.

## Optional native installers

```
PKG_TYPE=deb packaging/jpackage.sh       # Linux; needs fakeroot/dpkg
PKG_TYPE=rpm packaging/jpackage.sh       # Linux; needs rpmbuild
PKG_TYPE=dmg packaging/jpackage.sh       # macOS; needs Xcode command-line tools
./packaging/jpackage.ps1 -Type msi       # Windows; needs WiX 3 candle/light
```

Windows falls back to an app-image with a warning when WiX 3 is absent. macOS uses app version `1.0.0`
by default because its bundle version cannot start with zero. Native installers are optional; the
portable app-image is the primary distribution.
