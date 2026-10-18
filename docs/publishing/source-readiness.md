# Public source readiness

The root `LICENSE` contains GNU GPL version 2. That code license does not license Blizzard artwork, audio or gameplay datasets. This checklist is engineering preparation, not a determination of rights or legal clearance.

Normal end-user releases should include a reviewed compact runtime database and start directly. The external-data chooser below is an optional source-build/local-data workflow, not the required normal product onboarding. Database inclusion needs a separate explicit release policy and asset audit; code publication remains a fresh asset-free export.

## Implemented preparation

- Source-only application resource profile: `-Psource-only`, from a clean checkout/build.
- Source-only first-run file chooser for an existing local database, followed by worker-thread validation; incompatible selections return to the chooser with the reason.
- Successful selection remembered for future launches and revalidated each time. A missing/moved/incompatible remembered file prompts for another. Cancel closes startup without deleting or changing the database.
- Explicit external database property remains available for automation. Full-data local builds retain embedded-data behavior.
- Immutable revision exporter requires individually approved paths, content hashes and review notes; history, dirty worktree content, asset/archive extensions, symlinks and fixtures are excluded.

## Before a public repository

- [ ] Pin the actual source revision to publish; exclude pending unrelated edits.
- [ ] Review code provenance, copied snippets, dependency licenses and notices. Confirm intended GPL version wording against the actual files, rather than assuming all inputs are GPL.
- [ ] Approve an exact-path manifest for the complete source snapshot. No blanket approval of Java, Lua, JSON, CSV or test resources.
- [ ] Replace or omit third-party fixture/data extracts; keep synthetic tests and accurately identify excluded integration coverage.
- [ ] Scan the export for secrets, local paths, credentials, private reports, source archives and game content, including text-encoded content.
- [ ] Build from the exported tree and audit source archives, shaded JARs, native packages and CI artifacts. Dependencies can legitimately include native libraries or licensed fonts; do not confuse those with reviewed gameplay assets.
- [ ] Review ignore rules, CI upload rules and future asset-generation paths so later updates cannot silently upload local data.
- [ ] Initialize new history from the reviewed export only. No push or GitHub repository creation is performed by the preparation tools.

## Before an end-user download

- [ ] Verify packaged runtime launch on each supported platform; no Python, Node or Blender required for viewing existing portraits.
- [ ] Exercise actual first-run file selection, invalid selection, cancellation and remembered-path restart on a native display.
- [ ] Provide a documented, usable local data creation/setup path. A chooser alone does not generate a database for a brand-new user.
- [ ] Decide the optional separately distributed portrait sample policy; samples need individual provenance/rights review. Bulk artwork must not enter the code release by accident.
- [ ] Reduce the generated runtime database separately from source publication. Source archives should contain no populated gameplay database; omitting archival tables from a runtime DB requires query/dependency verification and does not itself settle distribution rights.

The current implementation supports selecting an existing database. It does not yet provide a complete data acquisition wizard or an approved public dataset download. Keep the running local app and its private data intact while preparing the public source export.
