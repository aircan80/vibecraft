# Source-only preparation

This directory does not publish anything. Keep the active repository and local game data private.

Normal end-user releases may include a separately reviewed compact runtime database and bypass this setup. The source-only profile is for the public source/build boundary and optional existing-local-data usage, not the default end-user package.

## Local-data startup

A source-only graphical launch opens a file chooser for an existing local database and remembers a successfully validated selection. Invalid or moved files prompt again; Cancel closes the application. No command line is needed for this selection. It does not create or download a database.

For automation, a source-only application can use an existing compatible, locally obtained database:

```
java -Dwowforever.database=/absolute/path/to/wowforever.sqlite -jar app.jar
```

The database is validated and opened read-only. It is not copied, updated, or replaced. Forever schema 4, product `wow_classic_beta`, build `1.60.*`, enUS (the existing legacy database omits locale and is treated as enUS, matching the app registry), a nonblank projection ID, and core entity tables are required. This is structural validation, not proof of provenance or permission. Without a database, graphical startup offers local file selection; headless self-test reports the required property. A complete data-creation wizard is future work.

Build from a clean isolated checkout using `./mvnw -Psource-only -pl app -am clean package -DskipTests`. This profile includes authored UI theme/SVG resources and notices, not gameplay data, NPC portraits/turntables, extracted tables, music or sound catalogs. Dependencies still carry their own licensed resources. Audit the final JAR before distribution. Never reuse an existing full-data `target/classes` directory. The normal local full-data build remains unchanged.

## Exact-path reviewed export

`export_source.py` copies approved regular text files from one immutable Git commit into a new directory. It never copies Git history or working-tree modifications. It rejects asset/archive/database extensions, fixtures, symlinks, duplicate or escaping paths, unapproved entries, and mismatched content hashes. These guards supplement human review; text code or JSON can also contain copied material.

Prepare a manifest with this structure, one explicit entry per file:

```
{"revision":"FULL_COMMIT_SHA","files":[
  {"path":"pom.xml","sha256":"SHA256_OF_REVIEWED_COMMITTED_BYTES",
   "approved":true,"review_note":"Reviewed build definition; no game data"}
]}
```

Then run:

```
python3 tools/publishing/export_source.py --repo /path/to/private/repo \
  --revision FULL_COMMIT_SHA --manifest /path/to/reviewed-manifest.json \
  --output /path/to/new-empty-source-export
```

Do not automatically mark every `.java`, `.lua`, or text resource approved. Exclude third-party fixture/data extracts until separately reviewed. Demo portraits are deliberately excluded by this first exporter: adding a small sample requires an explicit individually hashed exception and corresponding rights review; a blanket image directory exemption is inappropriate.

Run the separate publication audit on the exported tree and packaged artifacts, review notices/dependency obligations, and test from the export before considering publication. Passing the exporter does not establish legal clearance. A complete public snapshot is not yet approved or produced by these preparation changes.

Synthetic exporter checks: `python3 -m unittest discover -s tools/publishing -p 'test_*.py' -v`.
