Releases are built by the four-platform [GitHub Actions release workflow](../docs/releases/GITHUB-RELEASES.md). The scripts below also support local developer packaging; current releases are data-free and use first-run library setup.

# Vibecraft portable Java application

Every place. Every drop. Every adventure.

A World of Warcraft companion for exploration, loot, crafting, and lore.

The portable package is a self-contained application folder and compressed archive. Users extract it and
open its native launcher; Java is included. Python, Node and Blender are development tools and are not
included or required by the portable launcher. Interactive 3D rendering is outside the shipped scope.
Portraits are pre-rendered images. Dataset/asset acquisition and the first-run UI must be verified on
the final integrated build before claiming the entire application works without development tools.

## Source-only developer build (maintainers only)

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
Windows: `-JarPath C:\absolute\app.jar`, `-Version 0.4.1-preview.1 -NativeVersion 0.4.1`.
The product version is retained in the archive name, `release-version.txt` and JVM release property.
Platform numeric package metadata is separate: use `NATIVE_APP_VERSION` on Unix or `-NativeVersion` on
Windows when the platform cannot represent the product identifier. macOS JDK21 requires a positive
numeric major component; provide its build metadata version explicitly. Scripts fail with an explanation
rather than silently renaming a `0.x` product release to `1.0.0`.
Existing output folders must be removed or a fresh destination
chosen before rebuilding an app-image, so an old launcher cannot silently survive a failed build.

## Run after extracting

- Linux: open `Vibecraft/bin/Vibecraft`.
- macOS: open `Vibecraft.app` (unsigned builds may require the normal macOS security override).
- Windows: open `Vibecraft/Vibecraft.exe`.

Keep the whole folder together; the launcher needs its adjacent runtime and app files. The folder can
be moved without reinstalling. User preferences, datasets and caches currently remain in the normal
per-user application directories; portable application files do not imply USB-contained user data.
A source-only developer archive intentionally has no gameplay dataset or bulk assets. It is a source/QA
boundary, not the complete end-user product. The end-user archive includes the reviewed compact database
using the explicit mode below; first launch should not require a separate dataset download.

## Complete portable release with reviewed data

The intended end-user release includes a compact usable gameplay database. Keep the GitHub source export
free of bulk data, then stage the release data separately. This mode does not waive the source-JAR audit
and is distinct from private QA. It does not establish legal clearance or authorize publication.

Prepare a data root containing `data/wowforever.sqlite`, and a reviewed UTF-8 manifest with one row per
selected file: `SHA256<TAB>bytes<TAB>relative-path`. Hashes are lowercase 64-character SHA-256 values.
Paths must start with `data/`; absolute paths, traversal, duplicate names and worker/model files are rejected.
Unlisted files are never copied. A database entry at `data/wowforever.sqlite` is mandatory.

```
APP_JAR=/path/to/source-only.jar \
RELEASE_DATA_DIR=/path/to/reviewed-root \
RELEASE_DATA_MANIFEST=/path/to/reviewed-manifest.tsv \
APP_VERSION=0.4.1-preview.1 packaging/jpackage.sh
```

PowerShell: add `-ReleaseDataDir C:\reviewed-root -ReleaseDataManifest C:\reviewed-manifest.tsv`.
The Java staging tool verifies each file's exact byte size and SHA-256, copies only that inventory, and
verifies the staged copy. A `data/release-data-inventory.tsv` report is included alongside the database.
Static portrait/audio files may be individually reviewed and listed; this is never a blanket asset allow.
The native launcher is configured with the database path relative to its own application directory
(`$APPDIR`), so extraction/relocation does not require users to supply a property or install Java.
Archives use a distinct `-with-data` suffix. Do not combine reviewed-data and private-QA modes.

## Optional native installers

```
PKG_TYPE=deb packaging/jpackage.sh       # Linux; needs fakeroot/dpkg
PKG_TYPE=rpm packaging/jpackage.sh       # Linux; needs rpmbuild
PKG_TYPE=dmg packaging/jpackage.sh       # macOS; needs Xcode command-line tools
./packaging/jpackage.ps1 -Type msi       # Windows; needs WiX 3 candle/light
```

Windows falls back to an app-image with a warning when WiX 3 is absent. Native installers are optional; the
portable app-image with reviewed gameplay data is the intended end-user distribution.

## Assemble a Windows x64 ZIP from Linux

`cross-windows.py` is a developer-only alternative when a Windows jpackage runner is unavailable.
It cross-assembles a portable folder from the frozen application JAR, the same reviewed data manifest,
and an official pinned Temurin Windows x64 JRE. Python and the JDK are build tools only; neither Python
nor developer scripts are copied into the ZIP. Native Windows `jpackage.ps1` remains the preferred
platform build and acceptance path.

```
python3 packaging/cross-windows.py \
  --jar /external/frozen/source-only.jar \
  --jar-sha256 REVIEWED_FROZEN_JAR_SHA256 \
  --data-root /external/reviewed-root \
  --data-manifest /external/reviewed-data.tsv \
  --output /external/windows-bundle \
  --version 0.4.1-preview.1
```

Build outputs must be outside the source checkout. The runtime pin is committed in
`windows-runtime.json`; changing Java versions requires reviewing that pin. The script verifies both
the official checksum sidecar and pinned SHA-256/size before extracting the runtime, preserves its
`legal/` tree, and retains runtime provenance and a complete file inventory. It runs the same Java
source-JAR audit and reviewed-data staging checks used by native packaging. ZIP CRC integrity is checked.
The package includes Java's Windows dependency DLLs from the official distribution.

The fallback `Vibecraft.cmd` uses only its adjacent `runtime/bin/javaw.exe`, with paths relative to the
launcher. An independently generated native `Vibecraft.exe` can be added using `--launcher-exe`,
`--launcher-sha256`, one or more `--launcher-license` files, and `--launcher-provenance`. The EXE's
SHA-256 and Windows PE header are checked; attribution/license files are included. Launch4j is a build
tool only; its builder and GNU binutils must not be copied into the end-user folder.

The ZIP name, README and inventory explicitly say **NOT LAUNCH-TESTED ON WINDOWS**. Assembly on Linux,
valid PE headers, hashes and ZIP integrity do not establish that the application has launched on Windows.
Extract the entire ZIP on a Windows x64 machine and double-click `Vibecraft.exe` (or `Vibecraft.cmd` when
using the fallback package) to perform that acceptance step. No archive is uploaded by this tool.
