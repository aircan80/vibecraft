# Source repository and portable downloads

The current decision is to retain the project's commit history in
`~/Development/vibecraft`, sanitize personal identity, and remove generated databases
from every historical commit. This supersedes the earlier proposal to start a new
repository with fresh history. All original commits and merge relationships were
preserved; their IDs changed during the cleanup. Nothing has been pushed.

The source repository and end-user downloads have different contents:

- Source: application/importer code, synthetic fixtures, build tooling, documentation
  and reviewed reference assets/notices. Generated databases, raw acquisition caches,
  task workspaces and packaged applications remain outside Git.
- Portable application: OS launcher, bundled Java, compact gameplay database and
  selected support files. Extract and run. This remains available alongside a future
  small application download with integrated data setup.
- Developer evidence: original source snapshots, full ingestion database, renderer
  labs and the pre-cleanup Git backup remain in external local storage.

`-Psource-only` builds the small JAR without gameplay resources. Optional external
local-data builds provide real-data verification without adding the database to source
control. See the development build documentation as that integration lands.

The exact-path source exporter remains available for making reviewed subsets; fresh
history is no longer the selected migration strategy. Publication preparation does
not upload anything. Preserve third-party license/attribution obligations separately
from game-art/data provenance. See [repository readiness](repository-readiness.md).
