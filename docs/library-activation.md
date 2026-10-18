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
