# Repository workflow

Track new features, enhancements, bugs and substantive work in GitHub issues at
https://github.com/aircan80/vibecraft/issues. Search existing issues before creating
one. Keep the relevant issue updated with status, implementation commits, checks,
limitations and the concrete next step so another session can resume the work.
Reference issue numbers in commits. Close issues only after their acceptance
criteria are met; distinguish mockups, backend foundations and working app features.
Keep the roadmap and development dashboard synchronized with issue progress.

Use the GitHub noreply identity for commits:
`aircan80 <329744803+aircan80@users.noreply.github.com>`.

Keep agent worktrees, task folders, downloads, generated databases, private source
archives and release artifacts outside this repository. The shipped application
is Java only. Game data stays outside Git; runtime catalogs must retain their
edition/build provenance and must not silently substitute other game versions.

Blizzard/WoW installation directories are strictly read-only sources. Never write
updates, downloads, partial files, caches, extracted assets or generated databases
there. All writes go to Vibecraft-owned storage; reject configured writable
locations inside a selected game installation. Local-file reuse and explicit CDN
permission are complementary. Missing content must not trigger an unselected
CDN, export provider or game-edition fallback.
