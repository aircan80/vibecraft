# Manual Windows and Mac preview builds

`.github/workflows/portable-preview.yml` is prepared for **manual dispatch only**.
It has not been pushed or run. It does not create tags or GitHub Releases. A dispatch
uploads the complete data-containing package and bounded evidence as an Actions
artifact retained seven days. This is still distribution through the repository's
artifact access controls; review dataset/artwork inclusion before dispatch.

Select Windows x64, Apple Silicon Mac, Intel Mac, or all. Each selected target builds
on its native runner with JDK 21, runs full Maven verification against the original
development test database, then cleans/rebuilds the source-only JAR. Existing package
scripts audit that JAR and stage only the five files pinned in
`tools/releases/preview-data.tsv`. No runtime Python/Node/Blender is deployed.

Required inputs:

- Product version `0.4.1-preview.N` (this workflow's numeric packaging train is fixed).
- HTTPS ZIP URL containing exactly the five `data/...` paths from the pinned TSV,
  and its explicit lowercase SHA256. No credentials in URLs/inputs; they are not
  secret fields. There is no configured or implicitly uploaded dataset endpoint.
- If the checkout lacks the original full-test database, an HTTPS URL for that raw
  SQLite file and SHA256
  `f527ff9ea91b6dba8a8a710d33a187d864afe4ee37454b4e4e1b5610fbd2c772`.
  This fixture is for verification, not included in output. Full tests require raw
  archive evidence and cannot truthfully be run against only the compact DB.

Missing inputs, hash mismatches, extra ZIP paths, failed tests or failed native
launcher checks prevent artifact staging/upload. The full development JAR, original
SQLite fixture, downloads and caches are never upload paths. The generated compact
DB hash is pinned; regenerating it changes timestamps, so use the reviewed exact
export instead of silently regenerating a differently hashed dataset on runners.

The job extracts its newly generated archive into a different directory containing
spaces, removes developer Java/toolchain variables and tool PATH, selects fresh
user-data/cache/settings paths and invokes the actual native launcher `--self-test`.
It checks expected counts/build and unchanged DB hash, records bundled Java version,
then stages the complete archive, SHA256SUMS, inventory and small evidence reports.
This verifies native startup/database access; **interactive GUI acceptance remains
not run** until someone launches and reviews that platform's application.

Windows uses numeric native package version `0.4.1`. JDK21/macOS requires a positive
major bundle version, so its explicit numeric metadata is `1.4.1`; product identity
and archive version remain `0.4.1-preview.N`. Builds are unsigned and Mac builds are
not notarized. Numeric metadata is recorded separately in native evidence.

Runner labels and action usage checked against official documentation:
[GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners),
[setup-java](https://github.com/actions/setup-java),
[setup-python](https://github.com/actions/setup-python),
[artifact uploads](https://github.com/actions/upload-artifact).
The explicit runners are `windows-2022` (x64), `macos-15` (Apple Silicon) and
`macos-15-intel` (Intel). Runtime architecture is checked rather than inferred from
a filename. These are prepared configurations, not claims of completed native runs.

Local verification: Python unit tests plus YAML parsing/actionlint. Native Windows
and Mac behavior can only be established by running the prepared workflow/platforms.
