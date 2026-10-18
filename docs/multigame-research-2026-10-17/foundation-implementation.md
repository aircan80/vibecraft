# Edition foundation implementation

This is a safe Forever-only session boundary, not a multi-game import or a database migration. The existing database remains read-only and its schema and archived comparison sources remain unchanged.

`EditionRegistry.fromLegacyMetadata(db.meta())` recognizes an installed Forever gameplay projection only when the product is `wow_classic_beta`, the build is `1.60.*`, and a nonblank database build ID exists. Classic Era, Retail, and Anniversary TBC are listed as not installed. An Era reference snapshot never enables the Classic catalog. The product mapping is a compatibility check for this existing schema, not automatic discovery of future live channels.

Integration after opening the database:

```java
var registry = EditionRegistry.fromLegacyMetadata(db.meta());
var session = new CatalogSession(registry, applicationPreferences.node("game-selection"));
var repositories = Repositories.of(db, session.token().context());
var picker = new EditionPicker(registry, session, token -> {
  // Before enabling a second catalog: rebuild repositories, clear current UI, restore scoped history.
});
```

The picker is a normal Swing panel for the game menu/settings. It is separate from Original/Explorer Journal interface selection. Disabled games explain that their own validated gameplay catalog is required. The panel does not implement downloading or pretend coverage exists.

Every worker must capture its immutable token before queuing, then combine `session.guard(token)` with the existing per-navigation generation guard in `Async.runIf`. A new selection invalidates queued work and late results, including an A-to-A reload. It cannot interrupt an already-running JDBC query.

`CatalogContext.key(nativeTarget)` is the identity for new caches and in-memory history: edition, projection, exact client build and locale all participate in equality. `session.statePreferences()` gives a deterministic SHA-256 scope for persisted filters/history, avoiding Java Preferences' node-name length limit. It does not copy old global navigation into another game. Existing caches and navigation remain Forever-only until their call sites adopt the context; no second edition can be enabled through this registry or repository factory in the meantime.

The overload `Repositories.of(db, context)` validates exact installed identity before constructing legacy unscoped repositories. The old overload remains source-compatible for existing Forever callers. Do not use it as a future multi-game bypass. Adding another installed entry requires edition-scoped SQL/schema, coverage acceptance and repository adaptation first.

No on-disk migration, installed database copy, catalog import, or destructive operation occurs. Removal of this feature returns to the current Forever-only behavior; game preferences are separate from interface settings.

Validation: six focused tests cover unknown/Era metadata rejection, unavailable selection and stale preference recovery, session/generation rejection of queued and late async results, same-native-ID isolation across editions/builds/locales, disabled selector options, persisted scoped state, and the repository boundary against an imported fixture database. Run:

```sh
./mvnw -q -pl app -am test -Dtest=CatalogSessionTest,ScopedRepositoriesTest -Dsurefire.failIfNoSpecifiedTests=false
```
