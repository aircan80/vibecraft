# A source-only public repository

Status: proposed publication design, based on inspection of the current checkout. No repository was published, no history was rewritten, and no local assets were removed. This is an engineering boundary, not a finding that the code or acquisition workflow has legal clearance.

## Recommended boundary

Create a **new, reviewed source export with fresh history** for a future public repository. Keep the working development repository, installed database, caches, and running application private and intact. Do not make the existing repository public simply by deleting asset files in a new commit: previous commits can still contain their bytes. Git LFS, release attachments, CI artifacts, and GitHub Pages also count as distribution surfaces.

The public repository should contain application/importer/parser code, authored tests with synthetic fixtures, build definitions, and reviewed documentation and license notices. It should not contain Blizzard models, textures, maps, icons, music, sounds, rendered NPC portraits, turntable frames, extracted client tables, source archives, or populated gameplay databases. A render remains derived from the model; converting it to PNG does not make it our original illustration.

Do not promise that GPL licensing of a renderer or an addon grants permission to distribute the game assets it consumes. Review code provenance and dependency obligations separately from asset provenance. Preserve required third-party license and attribution notices for any code actually exported.

## What the checkout currently does

* Maven copies `app/src/main/resources` into the application by default. That directory currently includes `data/wowforever.sqlite`, a mechanical-squirrel portrait and turntable, appearance/music/sound catalogs, compressed extracted native music tables, reference data, and other derived game data. Excluding only `.m2` or `.mp3` would miss substantial content.
* `DatabaseInstaller.install()` opens `/data/build-id.txt` and `/data/wowforever.sqlite` before checking the installed database. Therefore, removing bundled resources alone does **not** produce a working public build, even for a user who already has an installed database.
* `importer`'s `import-data` profile writes its result directly into the application's resource directory. A public packaging profile must not accidentally activate or package that output.
* Test resources include extracted native icon PNGs and CSV fixtures, plus third-party Questie/AllTheThings Lua data. A whole-directory test-resource allowlist would be unsafe. Test Java source may also embed real source extracts and needs review rather than an extension-only exemption.
* Runtime assets already have separate user cache locations. These and `.local`, Maven `target`, screenshots, review mockups with game imagery, packaged JARs, and archives must never enter the public export.

## Export procedure

1. Freeze the exact source revision and inventory pending edits. Select the revision deliberately; never silently combine HEAD with arbitrary working-tree files.
2. Produce a human-reviewed **exact-path manifest**. Candidate inputs are the three module POMs and parent POM, Maven wrapper text files, module Java source, selected authored scripts, synthetic tests, selected authored theme/icon resources, documentation, and appropriate license notices. These are candidates, not an automatic recursive allowlist. Fonts, icons, copied snippets, downloads, and fixture-generating scripts still require provenance review.
3. Build the export in a new empty directory outside the active repository. Copy only manifest entries from the frozen revision. Reject absolute paths, `..`, symlinks, submodules, duplicate destinations, `.git`, and any path absent from the manifest. Do not run `git archive HEAD` over the existing repository or copy its `.git` directory.
4. Record each exported path, SHA-256, source revision, and a review classification in a manifest outside release payloads if it reveals private paths. Inspect all text resources as well as binaries. A file suffix allowlist is only a secondary guard: JSON, Lua, CSV, Java, and SVG can carry copied game content too.
5. Run the independent asset audit on the proposed export and on its built artifacts. Fail closed on unreviewed files. Inspect ZIP/JAR contents, dependency-shaded resources, compressed payloads, generated sources, and CI upload definitions; a clean source tree alone does not establish a clean release.
6. Run an isolated source-only build and synthetic test suite from the export, with fresh dependency/cache directories where practical. Never build a release using the active checkout's existing `target/classes`, because stale resources can survive exclusions.
7. Review the resulting source tree, artifact inventory, license/notice set, README setup steps, and audit reports before initializing fresh public history. Publishing or pushing is a separate explicit action; this plan does not perform it.

For a fresh repository, the initial history must contain only the approved export. If retaining the existing public history later becomes necessary, design and validate a separate history-cleanup migration in a disposable clone, including tags and LFS objects. Do not rewrite the live development repository as an incidental cleanup step.

## Required code/build work before a runnable public release

These changes are **not implemented by this document**:

* Add a source-only/default-public packaging path that includes exact reviewed application resources and excludes all game datasets and artwork. Keep local full-data packaging available explicitly so development and the current app remain usable.
* Add an explicit external-data mode. Validate a user-selected local database read-only for supported schema, edition, build and locale before opening it. Never replace it just because its build differs from a bundled marker. With no compatible data, show setup instructions instead of an embedded-resource exception.
* Make generated art/music/table catalogs external or optional in the public build. Missing data should give a clear unavailable state; it must not trigger an unannounced download or an Era fallback.
* Give import tooling an output directory outside `src/main/resources` for user-owned local data. An explicit local install action may copy validated output into the user data location, with an atomic write and no overwrite of an existing database without an intentional replacement action.
* Replace unreviewed fixture files with small invented/synthetic examples, then identify which integration tests require separately provided local data. Do not label excluded integration tests as passing.
* Add a release gate that scans source archives, JARs and packaged installers and rejects unreviewed game-data resources. No databases, rendered art, audio, native table extracts, source dumps or cached downloads should be included in GitHub releases or CI artifacts.

Current default database locations are `$XDG_DATA_HOME/wowforever-browser/wowforever.sqlite` (Linux, default `~/.local/share`), `%LOCALAPPDATA%/WoWForeverBrowser/wowforever.sqlite` (Windows), and `~/Library/Application Support/WoWForeverBrowser/wowforever.sqlite` (macOS). Document these as **local user data**, not downloadable project assets. A new selector must not treat their existence as validation.

## Completion criteria

* Existing local app, database, preview cache and music continue to work without deletion or replacement.
* The fresh export has no connection to the private repository's asset-containing history.
* Every exported file has an exact-path review decision; denied and deferred files are recorded.
* A clean build succeeds; asset-free startup gives a useful setup screen; a separately supplied compatible local database opens correctly; incompatible data is rejected without modification.
* Packaged artifacts pass the same asset audit as the source export, and notices match the code and dependencies actually shipped.
* The user can review the concrete export and reports before publication. No statement of legal clearance is inferred from passing these checks.
