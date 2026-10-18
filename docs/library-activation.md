# Managed library activation (Refs #1, #6)

`LibraryActivation.activateForNextStartup` is an explicit, background-worker operation. It rechecks a
`LibraryBuildJob.Candidate` against its exact SHA-256, selected `SetupPlan`, completed projection/source
identity and browser compatibility. It copies the candidate into a fresh application-owned
`libraries/versions/<build-id>-<uuid>/wowforever.sqlite` directory. The candidate and any open database
remain untouched. No controller or open browser session switches databases.

After revalidating the copied database against the full candidate policy and receipt, rechecking its hash,
and flushing the copy, activation atomically replaces `libraries/selection.properties`.
The bounded pointer contains the active and previous validated selections, exact hashes, native
product/build/locale, ingestion identity, core entity counts and coverage warnings. A failed or cancelled
operation leaves the previous pointer intact and removes its unpublished copy. Unsupported atomic moves
fail rather than falling back to a partially published pointer. Previous versions are retained; automatic
garbage collection is not implemented. Concurrent activations use a file lock.

`ExternalDatabaseSetup.resolve` consumes this registry at the next ordinary startup, off the EDT:

1. Explicit `wowforever.database` override.
2. Hash- and compatibility-validated managed selection, then its retained previous selection.
3. Embedded database, when present.
4. Existing saved external database path, then the setup workspace's existing-file escape.

Invalid registry paths, aliases, hashes and identity fields are rejected. Registered paths must remain
inside the versioned managed root. Protected WoW folders are read-only, and the currently open database
and its sidecars cannot be activation output. Caller-supplied protected game roots are combined with
those in the selected source plan. Startup retains the old external-file fallback if no managed version
can be validated. Saved external preferences are not rewritten by managed selection.

Historical data-inclusive portable previews set `wowforever.database` in their launcher, so that explicit
override continues to win. They will not adopt a managed activation while that override remains present.
The planned ordinary source/setup launcher does not need to supply it.

The current core candidate receipt explicitly records that additive foundation/maps/hotfix/collector
replay is incomplete. Those warnings survive activation and registry resolution. This API and startup
consumer do not enable automatic UI activation: real-data acceptance, runtime compaction and an explicit
reviewed caller decision remain separate work. Synthetic unit fixtures establish atomicity and routing;
they do not establish a complete imported game library.

## Compact runtime activation

`activateExportedForNextStartup(fullCandidate, runtimePath, exportReceipt, plan, cancelled)` preserves the
full-candidate API and shares its immutable-copy/atomic-pointer mechanism. The full candidate and compact
runtime input must both be inside the actual application data directory, protected by `SetupStorage`.
Selecting their parent as a new permissive storage root is not an alternative.

The operation rederives the full candidate's hash and complete source validation, then independently calls
`RuntimeLibraryExporter.verifyExport`. A caller-created receipt or `VerifiedExport` record is never an
authority token. Verification binds actual source/runtime sizes and hashes, build identity, retained table
rows, original metadata, omitted raw-record/archive-byte counts and archive identities. Forging a new hash
for a modified runtime does not bypass comparison with the full source.

Only that verified compact file is copied into versioned library storage. The copied size and SHA-256,
structural compatibility, runtime profile and source identity are checked again before atomic publication.
There is one expensive source/runtime row-verification pass before copying. Identical copied bytes do not
need a second full row pass. The compact copy is not sent to the full raw-proof validator: runtime-v1
intentionally omits those raw blobs. Registry selection keeps the original full source's validated identity,
counts and coverage warnings, alongside the compact database's exact content hash. Startup therefore needs
no archived raw blobs. This backend does not enable a UI activation action or relax real-data readiness.

## Active library asset permissions

Pointer format 2 persists the reviewed plan's independent Blizzard CDN and Wago permissions, with bounded
read-only game roots and the selected database's exact SHA-256. Both activation paths record those
permissions without changing the current process's policy or database guard. Old format-1 selections and
the compatible three-argument `Selection` constructor default to cache-only; database metadata does not
grant provider access. Invalid permission/hash bindings reject that selection and can use the previous
validated selection with its own permissions.

`AssetDownloadPermissions` starts with an empty active-catalog context and cache-only policy. Only
`ExternalDatabaseSetup` configures that context after choosing/opening a validated selection. A chooser
that has just built a managed library is matched against the newly published registry before returning
its database. Its managed path is not saved as a generic legacy path, which could otherwise bypass the
recorded consent later. An unverified path inside managed storage stays cache-only through the existing
file escape. Explicit overrides and existing external/embedded legacy catalogs retain the previous
provider behavior through `LEGACY`; existing music mute/volume preferences are unchanged.

`current()`, `activeDatabase()` and the atomic combined `snapshot()` are memory-only. Setup management can
capture the active database and protected roots on the EDT without scanning or hashing a library. Shared
native asset clients enforce the current provider flags and `checkCacheWrite(cacheRoot, destination)`;
cache writes must remain inside app-owned cache storage, outside protected game folders and aliases.
The explicit descriptor clients used by setup jobs retain their own reviewed transport policy.
