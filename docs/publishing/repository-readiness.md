# Repository readiness

Status: technical push checks passed; nothing pushed. The canonical repository is
`~/Development/vibecraft`. Agent worktrees, caches, raw inputs, generated databases
and release archives live outside it.

## Completed history cleanup

- Retained all 370 original commits through a one-to-one rewrite, with parent order,
  merges, author/committer dates, and all 51 original branch/tag names preserved.
- Sanitized personal identity in metadata, messages and 18 documentation/script paths.
  Future local commits use `aircan80 <329744803+aircan80@users.noreply.github.com>`.
- Removed generated SQLite database versions from every historical commit. Added
  ignore rules preventing regenerated databases and journals from being committed.
- Checked all 42 existing worktrees; pending changes were preserved.
- Verified repository connectivity and zero reachable files above GitHub's 100 MiB
  limit. Largest remaining historical file is approximately 1.37 MB; `.git` is 24 MiB.
- Preserved original Git objects, dirty-worktree patches and development databases
  in a private external backup. They are not part of this repository or a push.

Commit IDs necessarily changed. The external review directory contains the complete
old-to-new mapping and validation reports. Existing build artifacts have unchanged
hashes; metadata/documentation cleanup did not change their application code.

## Data and builds

The importer remains the rebuild path. Preserve the original source snapshots as
well as the development database: live upstream downloads may change or disappear.
The compact runtime database omits original ingestion evidence and is not itself a
rebuild authority. End-user portable packages can still include that runtime database.

Clean-source verification passed on the integrated checkout: 1,939 reported tests,
55 skips, zero failures/errors. Missing dataset classes are explicitly qualified;
the number reported by JUnit after a class-level skip is not the full test inventory.
The external-data profile separately passed 2,011 reported tests with six environment
skips before the additional discovery tests landed. An explicitly supplied missing
dataset correctly fails. See [local data builds](../development/local-data-builds.md).

## Content review

The post-cleanup inventory contains 68 tracked asset candidates and 87 historical
candidate objects, principally reference screenshots, test images, static portrait
samples and metadata. The database and its bulk archived source files are absent.
The asset inventory is a content report, not legal clearance. Preserve code and
third-party notices; project GPL licensing does not automatically license game art.

Pattern-based review found no confirmed private keys, tokens or credential URLs.
Independent review of the cleaned history passed: 371 commits, 3,448 reachable blobs,
and 376 Git administrative files had no private identity references or confirmed secrets.
No tags, GitHub Actions runs, release uploads or repository pushes have been performed.

Final checks: the integrated clean source build and source-only package passed.
The packaged source JAR passed the asset/runtime gate, and both GitHub workflows
passed actionlint. Ordinary source CI no longer assumes a database exists in Git.
The manual native workflow retains explicit hash-pinned dataset requirements.
