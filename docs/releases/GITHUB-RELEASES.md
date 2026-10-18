# Native releases through GitHub Actions

Tracking: [#19](https://github.com/aircan80/vibecraft/issues/19).

`.github/workflows/release.yml` is the release path. Four independent native jobs build audited, data-free Java 21 portable app-images: Ubuntu 24.04 x64, Windows 2022 x64, macOS 15 Apple silicon, and macOS 15 Intel. No dataset URL, populated test fixture, game asset bundle, signing secret, or developer machine is required. Python is a CI helper only; it is absent from the shipped application. The first-run application workflow builds the user's local library.

A maintainer selects a product version when ready, aligns the root POM numeric version, reviews changes and pushes a `v<SemVer>` tag. Invalid tags and tags whose numeric core differs from the POM fail before native jobs. A `-SNAPSHOT` POM suffix is permitted because tag identity is recorded independently in the launcher and release manifest. Prerelease SemVer tags produce GitHub prereleases. This implementation does not choose a tag or publish anything by itself.

Every native job runs `clean verify` against authored checkout test fixtures/metadata (without external gameplay data), helper tests, then a separate `-Psource-only -pl app -am clean package -DskipTests` to stage the restricted distribution before the strict JAR audit, native `jpackage`, and safe extraction of the final archive into a path containing spaces before running its actual launcher with `--portable-self-test <absolute-report-path>`. Smoke uses fresh user directories, no tools on PATH, and no Java environment overrides. It checks bundled Java 21, formatVersion 1, exact product identity, expected OS/CPU, runtime home inside the extracted bundle and SQLite JNI via the application report. Packages contain their runtime and licenses. UI/data-source acceptance remains a separate requirement; the offline smoke does not download data or open the GUI.

Only after all four native jobs pass does a separate job receive `contents: write`. It downloads the current run's four artifact sets, verifies exact filenames/checksums/native receipts and requires every platform evidence file to identify the expected workflow source commit, and creates one GitHub Release using the existing tag (`--verify-tag`). It never creates a tag or overwrites an existing release. Archives, individual SHA256 files and bounded platform evidence are attached. Earlier failures prevent publication. Workflow-level concurrency prevents overlapping runs for the same ref.

Manual `workflow_dispatch` takes an explicit validated version and builds the same four artifact sets, but cannot reach the publication job. Use this to establish real runner acceptance before selecting a release tag. No Actions run or release was created as part of implementing this workflow.

Native package versions are separate from product identity: Windows/Linux use the numeric SemVer core. JDK21 macOS requires a positive-major CFBundleVersion, so the workflow maps product `(major, minor, patch)` to native `(major+1, minor, patch)` and records both in `release-version.txt`. The archive, launcher property, smoke report and GitHub Release retain the exact product SemVer. Builds are unsigned/not notarized; credentials are not requested or embedded.

The old `portable-preview.yml` is retained as a historical manual populated-dataset preview workflow. It neither publishes releases nor represents the current data-free release path.

## Official references checked

- [Hosted runner labels and architectures](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Checkout](https://github.com/actions/checkout), [Java setup](https://github.com/actions/setup-java), [Python setup](https://github.com/actions/setup-python)
- [Artifact upload](https://github.com/actions/upload-artifact), [artifact download](https://github.com/actions/download-artifact)

Local implementation checks cover semantic-version injection/mismatch rejection, explicit macOS version mapping, unexpected asset rejection, four-platform artifact completeness and tampered checksums, wrong schema/OS/architecture/source commit, and archive traversal/link escapes while preserving valid executable permissions and internal links. Real GitHub native runs are still required before claiming cross-platform acceptance.
