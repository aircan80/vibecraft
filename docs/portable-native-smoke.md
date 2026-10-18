# Portable native prerequisite smoke

`--portable-self-test <absolute-report-path>` checks a packaged runtime without opening the GUI, reading a gameplay database, contacting a provider or loading user preferences. The existing `--self-test` remains the separate gameplay-database check.

Create an **empty existing directory** named `vibecraft-portable-self-test-<unique-suffix>` in app-owned test storage. Pass an absolute `report.json` path inside it to the actual native launcher. Keep redirected stdout/stderr outside that directory. Arbitrary symlink aliases, existing reports, nonempty/nondedicated storage and any ancestor containing `.build.info` are rejected. Verified macOS system aliases follow the shared source-path policy. Native SQLite extraction stays in a newly created `sqlite-native` child configured before driver initialization.

The report is UTF-8 JSON, at most 8 KiB, written with `CREATE_NEW`. Exit **0** and the report below are jointly authoritative; GUI launchers may not expose stdout reliably. Exit **1** records a failed prerequisite with `success:false`, marker `VIBECRAFT_PORTABLE_SELF_TEST_FAILED` and a bounded `error`. Exit **2** means invalid invocation/storage or inability to create the report; an existing report is never replaced.

Success fields:

| Field | Value |
| --- | --- |
| `formatVersion` | `1` |
| `success` | `true` |
| `marker` | `VIBECRAFT_PORTABLE_SELF_TEST_OK` |
| `productVersion` | `wowforever.release.version` launcher property; package implementation version or `development` fallback |
| `javaFeature` | Actual Java feature number, at least 21 |
| `osName`, `osArch`, `javaHome` | Actual runtime properties |
| `sqliteNativeVersion` | Result of `SELECT sqlite_version()` using the loaded native SQLite library |
| `scope` | Offline native prerequisites; GUI/gameplay data are not checked |

The check resolves `java.desktop`, `java.net.http`, `java.prefs` and the importer workflow class without invoking their UI/network/preferences services, then opens SQLite only in memory and executes a query. Release automation must compare `productVersion` against its validated requested release version and run the relocated native launcher with fresh user directories. A passing result verifies that OS/architecture package's Java/JNI prerequisites, not a visible window, first-library import, media availability or complete gameplay coverage.
