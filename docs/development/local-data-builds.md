# Builds without a database in source control

Generated SQLite databases belong outside the source checkout and its Git history. The source tree retains code, importer inputs/configuration and tests needed to reproduce them. A normal end-user release may include a separately reviewed compact runtime database; this is distinct from committing a development database.

## Clean clone verification

```
./mvnw clean verify
```

Runs fixture/unit tests. Tests requiring the full gameplay dataset are reported as skipped with an explicit reason when no dataset is supplied. This includes quest UI exercises that load real quest chains, not just classes named `Shipped*`. Fixture tests still build their small temporary databases from test inputs. Existing cache-dependent native tests may also report their own documented skips. A successful source verification is not a claim that real-data acceptance ran.

## Full verification against an external database

Place these matching files together outside the checkout:

```
/path/to/development-data/wowforever.sqlite
/path/to/development-data/build-id.txt
/path/to/development-data/import-report.txt
```

Then run:

```
./mvnw -Plocal-data -Dwowforever.localDataDir=/path/to/development-data clean verify
```

The default directory for this explicit profile is `~/.local/share/vibecraft/development-data`. The application tests open the specified database read-only through `wowforever.database`. Maven copies only the two small sidecars into **test** resources; it does not copy the database into source or application resources. Normal synthetic tests remain enabled. An explicitly selected missing, incompatible or corrupt database fails instead of being silently skipped. Sidecars must describe the selected database; mismatches remain test failures.

This profile verifies data without bundling it into the application JAR. Use the separately reviewed release packaging workflow to create a compact-data end-user package. For optional source-only GUI startup, select an existing database in the application or pass `-Dwowforever.database=/path/to/wowforever.sqlite` before `-jar` for automation.

Always use a clean build when changing resource profiles, because previously generated resources can otherwise remain in `target`.

## Regenerate outside the source tree

```
./mvnw -pl importer -am -Pimport-data \
  -Dimport.output.dir=/path/to/new-development-data \
  -Dclientdata.dir=/path/to/converted-pinned-client-data verify
```

The existing importer CLI profile now defaults to external output under `~/.local/share/vibecraft/development-data`. `-Dimport.output=/path/to/database.sqlite` can override the exact output file. Build ID and import report are produced beside the database by the importer. Prefer a new staging directory, validate the output, and explicitly select it for verification; this developer command does not replace a running app's active database.

The importer still requires its actual pinned inputs and currently converted native-client data; these commands do not claim that Python-based conversion has already been replaced. The integrated Java-first setup is specified separately in [the Vibemining design](../design/vibemining-setup.md).

## Verification recorded when introducing this workflow

| Mode | Lua tests / skipped | Importer tests / skipped | App tests / skipped | Failures / errors |
| --- | --- | --- | --- | --- |
| Clean source build, no gameplay DB | 71 / 1 | 584 / 5 | 1275 / 49 | 0 / 0 |
| External original dataset profile | 71 / 1 | 584 / 5 | 1356 / 0 | 0 / 0 |

JUnit reports a class-level before-all abort as a skip record rather than enumerating every test method that would have run in that class. Consequently the reported total differs between modes; the source-only result must not be presented as full gameplay-data coverage. The six non-app skips are pre-existing environment-dependent checks. A separate negative check with an explicitly nonexistent local dataset failed with one error and zero skips, as intended. The application JAR produced by the full external-data verification contained no `data/wowforever.sqlite` entry.
