# WoW Forever Item & Drop Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cross-platform Java desktop app that browses every World of Warcraft: Forever item and its sources (NPC/object/container drops with %, vendors, quest rewards, dungeon boss loot) from a SQLite database built from QuestieDB and AtlasLootClassic and shipped inside the app.

**Architecture:** Three Maven modules. `lua-table` parses the Lua table literals the addon databases are written in and evaluates them against a symbol scope. `importer` turns the pinned upstream files into `wowforever.sqlite` (raw entities, layered corrections, drop rates, zones, AtlasLoot instances) and prints an import report. `app` is a Swing + FlatLaf browser over read-only JDBC repositories, with the database extracted from the JAR on first run.

**Tech Stack:** Java 21, Maven 3.9 (wrapper committed), JUnit 5.11, AssertJ 3.27, sqlite-jdbc 3.49, FlatLaf 3.6, maven-shade for the fat JAR, GitHub Actions (ubuntu/windows/macos).

**Spec:** `docs/superpowers/specs/2026-10-16-wow-forever-item-browser-design.md`

## Global Constraints

- Java 21, no preview features. Build with `./mvnw -B verify` (Windows: `.\mvnw.cmd -B verify`); only a JDK may be required.
- Modules and packages: `lua-table` → `dev.wowforever.lua`; `importer` → `dev.wowforever.importer`; `app` → `dev.wowforever.app`.
- Data sources pinned: QuestieDB commit `49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4`, AtlasLootClassic commit `8e99341e4e779328460bf7684c0d5b22ce50ddf1`. Files are fetched only from `https://raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>`. A pre-populated cache exists on this machine at `~/.cache/wowforever-importer/{questiedb,atlasloot}/<sha>/<path>`; the importer's default cache dir is exactly that layout.
- Correction order: raw base → legacy → generated delta-base → traces → authored Forever fixes. Only static `Load()`-style providers.
- Unsupported Lua constructs or correction shapes are reported, never fatal (unless `--fail-on-unsupported`, which fails only for skips on UI fields listed in the spec §3).
- The shipped DB lives at `app/src/main/resources/data/wowforever.sqlite` and is committed. The app never touches the network. All app DB access runs off the Event Dispatch Thread.
- Tests must pass headless (`-Djava.awt.headless=true`); never instantiate `JFrame`/`JDialog` in tests.
- Project license GPL-2.0-or-later; `NOTICE.md` credits QuestieDB and AtlasLootClassic with links and commits.
- Commit after every task with a conventional-commit message. Keep files focused; one responsibility per class.

## Review Focus

1. Search text containing FTS operators or quotes (`'`, `"`, `*`, `-`, `OR`, `(`) must return results or an empty list, never throw. Pinned in Task 13 (`SearchRepositoryTest.specialCharactersDoNotThrow`).
2. A drop/vendor/quest link that points at an id with no row (Forever data has thousands of these) must render as `Unknown NPC #id` (or item/object/quest) and still navigate. Pinned in Task 13 (`ItemRepositoryTest.unknownNpcInDropsKeepsRow`) and Task 16 (`ItemDetailPanelTest.unknownNpcRendersPlaceholder`).
3. Lua string escapes (`Recruit\'s Shirt`, `\"`, `\\`, `\ddd`, `\n`) and long strings must decode exactly; a wrong decode silently corrupts thousands of names. Pinned in Task 2 (`LexerTest.decodesEscapes`, `LexerTest.longStrings`).
4. A correction that creates a brand-new entity with only some fields (e.g. name + `npcDrops_add`) must produce a row with NULLs elsewhere and no crash on write. Pinned in Task 8 (`CorrectionApplierTest.createsMissingEntityWithPartialFields`) and Task 11 (`SqliteWriterTest.writesEntityWithNullFields`).
5. First run on a machine with no data directory, and a later run after the embedded DB changed, must both end with the current DB open. Pinned in Task 13 (`DatabaseInstallerTest.extractsWhenMissing`, `DatabaseInstallerTest.replacesWhenBuildIdDiffers`).

---

### Task 1: Reactor scaffolding, CI, license files

**Files:**
- Modify: `pom.xml` (parent; currently a placeholder)
- Create: `lua-table/pom.xml`, `importer/pom.xml`, `app/pom.xml`
- Create: `lua-table/src/main/java/dev/wowforever/lua/package-info.java` (javadoc only), same for the other two modules
- Create: `lua-table/src/test/java/dev/wowforever/lua/SmokeTest.java` (one trivial assertion), same for the other two modules
- Create: `.gitignore`, `README.md`, `LICENSE`, `NOTICE.md`, `.github/workflows/ci.yml`

**Interfaces:**
- Produces: the reactor; property `${sqlite.version}` etc. in the parent `dependencyManagement`.

- [ ] **Step 1: Write the parent POM**

Parent `pom.xml`: `groupId dev.wowforever`, `artifactId wow-forever-browser`, `version 0.1.0-SNAPSHOT`, `packaging pom`, modules `lua-table, importer, app`. Properties: `maven.compiler.release 21`, `project.build.sourceEncoding UTF-8`, `junit.version 5.11.4`, `assertj.version 3.27.3`, `sqlite.version 3.49.1.0`, `flatlaf.version 3.6`, `slf4j.version 2.0.17`. `dependencyManagement`: `org.junit:junit-bom` (import), `org.assertj:assertj-core`, `org.xerial:sqlite-jdbc`, `com.formdev:flatlaf`, `org.slf4j:slf4j-api`, `org.slf4j:slf4j-simple`. `pluginManagement`: `maven-compiler-plugin 3.14.0`, `maven-surefire-plugin 3.5.3` with `<argLine>-Djava.awt.headless=true</argLine>`, `maven-jar-plugin 3.4.2`, `maven-shade-plugin 3.6.0`, `exec-maven-plugin 3.5.0`. Every module declares `junit-jupiter` and `assertj-core` test dependencies. If a version fails to resolve, pick the latest release on Maven Central and note it in the commit message.

- [ ] **Step 2: Write the three module POMs and smoke tests**

`lua-table`: no runtime deps. `importer`: depends on `lua-table`, `sqlite-jdbc`, `slf4j-api`, `slf4j-simple`. `app`: depends on `sqlite-jdbc`, `flatlaf`, `slf4j-api`, `slf4j-simple` (the test-scope dependency on `importer` and its test-jar is added in Task 6). Each `SmokeTest` asserts `1 + 1 == 2`.

- [ ] **Step 3: Write support files**

`.gitignore`: `target/`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`, `importer/import-report.txt`. `LICENSE`: the GPL-2.0 text downloaded from `https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt`. `NOTICE.md`: credits "QuestieDB (https://github.com/Questie/QuestieDB), commit 49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4, no license file published; used with attribution" and "AtlasLootClassic (https://github.com/Hoizame/AtlasLootClassic), commit 8e99341e4e779328460bf7684c0d5b22ce50ddf1, GPL-2.0". `README.md`: one paragraph, build command, run command `java -jar app/target/wow-forever-browser.jar`, data rebuild command (filled in by Task 12). `ci.yml`: matrix `os: [ubuntu-latest, windows-latest, macos-latest]`, `actions/checkout@v4`, `actions/setup-java@v4` with `distribution: temurin`, `java-version: 21`, `cache: maven`, step `run: ./mvnw -B verify` with `shell: bash`.

- [ ] **Step 4: Verify the reactor builds**

Run: `./mvnw -B -q verify`
Expected: BUILD SUCCESS, three smoke tests run.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "build: maven reactor with lua-table, importer, app modules and CI"
```

---

### Task 2: Lua lexer

**Files:**
- Create: `lua-table/src/main/java/dev/wowforever/lua/Token.java`, `TokenType.java`, `Lexer.java`, `LuaSyntaxException.java`
- Test: `lua-table/src/test/java/dev/wowforever/lua/LexerTest.java`

**Interfaces:**
- Produces: `record Token(TokenType type, String text, int line)`; `enum TokenType { NUMBER, STRING, NAME, NIL, TRUE, FALSE, FUNCTION, LOCAL, RETURN, END, IF, THEN, ELSE, ELSEIF, FOR, WHILE, DO, REPEAT, UNTIL, IN, AND, OR, NOT, LBRACE, RBRACE, LBRACKET, RBRACKET, LPAREN, RPAREN, COMMA, SEMICOLON, ASSIGN, DOT, COLON, PLUS, MINUS, STAR, SLASH, EQ, NE, LT, LE, GT, GE, CONCAT, ELLIPSIS, HASH, EOF }`; `Lexer(String source, String fileName)`, `List<Token> tokenize()`; `LuaSyntaxException(String fileName, int line, String message)` extends `RuntimeException`.
- STRING tokens carry the **decoded** value; NUMBER tokens carry the source text.

- [ ] **Step 1: Write the failing tests**

```java
class LexerTest {
  @Test void decodesEscapes() {
    assertThat(tok("'Recruit\\'s Shirt'")).containsExactly(new Token(STRING, "Recruit's Shirt", 1), eof(1));
    assertThat(tok("\"a\\\"b\\\\c\\n\\65\"").get(0).text()).isEqualTo("a\"b\\c\nA");
  }
  @Test void longStrings() {
    assertThat(tok("[[return {\n[1]='x'}]]").get(0)).isEqualTo(new Token(STRING, "return {\n[1]='x'}", 1));
    assertThat(tok("[==[a]]b]==]").get(0).text()).isEqualTo("a]]b");
  }
  @Test void skipsComments() {
    assertThat(tok("--[[ block\n]] 1 -- line\n2")).extracting(Token::type).containsExactly(NUMBER, NUMBER, EOF);
    assertThat(tok("--[[ block\n]] 1 -- line\n2").get(1).line()).isEqualTo(3);
  }
  @Test void numbersAndOperators() {
    assertThat(tok("-1.5 3 == ~= .. ...")).extracting(Token::type)
        .containsExactly(MINUS, NUMBER, NUMBER, EQ, NE, CONCAT, ELLIPSIS, EOF);
  }
  @Test void keywordsAndNames() {
    assertThat(tok("local nil_x = nil")).extracting(Token::type).containsExactly(LOCAL, NAME, ASSIGN, NIL, EOF);
  }
  @Test void unterminatedStringFailsWithLine() {
    assertThatThrownBy(() -> tok("x = 'abc")).isInstanceOf(LuaSyntaxException.class).hasMessageContaining("line 1");
  }
}
```
`tok(s)` = `new Lexer(s, "t.lua").tokenize()`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl lua-table test`
Expected: compilation failure (classes missing).

- [ ] **Step 3: Implement the lexer**

Escapes to support: `\n \t \r \\ \" \' \a \b \f \v`, `\ddd` (up to 3 decimal digits), `\x` fallback: keep the char. Long strings/comments use `[=*[` ... `]=*]` with matching level; a newline directly after the opening bracket is dropped (Lua rule). Line counting includes newlines inside long strings and comments.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl lua-table test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add lua-table && git commit -m "feat(lua): lexer for the Lua subset used by addon databases"
```

---

### Task 3: Lua values and expression parser

**Files:**
- Create: `lua-table/src/main/java/dev/wowforever/lua/LuaValue.java` (sealed), `LuaNil.java`, `LuaBoolean.java`, `LuaNumber.java`, `LuaString.java`, `LuaTable.java`, `LuaUnresolved.java`, `LuaValues.java`
- Create: `lua-table/src/main/java/dev/wowforever/lua/ast/Expr.java` (sealed interface with nested records), `TableEntry.java`
- Create: `lua-table/src/main/java/dev/wowforever/lua/Parser.java`
- Test: `lua-table/src/test/java/dev/wowforever/lua/LuaTableTest.java`, `ParserTest.java`

**Interfaces:**
- `sealed interface LuaValue permits LuaNil, LuaBoolean, LuaNumber, LuaString, LuaTable, LuaUnresolved`.
  `LuaNil.NIL` singleton; `record LuaBoolean(boolean value)`; `record LuaNumber(double value)` with `static LuaNumber of(long)`, `boolean isInteger()`, `long asLong()`; `record LuaString(String value)`; `record LuaUnresolved(String reason, int line)`.
- `final class LuaTable`: `void put(LuaValue key, LuaValue value)` (putting `LuaNil` removes), `LuaValue get(LuaValue key)`, `LuaValue get(long)`, `LuaValue get(String)`, `int arrayLength()` (largest positive integer key, 0 if none), `List<LuaValue> array()` (indices 1..arrayLength, `NIL` for holes), `List<Map.Entry<LuaValue, LuaValue>> entries()` (insertion order), `boolean isEmpty()`, `static LuaTable identity()` (any `get(LuaString k)` returns `k`; `entries()` empty). Keys compare by value (`LuaNumber(1)` equals `LuaNumber(1.0)`).
- `LuaValues`: `static boolean deepEquals(LuaValue a, LuaValue b)`; `static OptionalLong asLong(LuaValue)`; `static Optional<String> asString(LuaValue)`; `static Optional<LuaTable> asTable(LuaValue)`.
- AST: `sealed interface Expr` with records `Literal(LuaValue value, int line)`, `Name(String name, int line)`, `Field(Expr target, String name, int line)`, `Index(Expr target, Expr key, int line)`, `Call(Expr fn, List<Expr> args, int line)`, `MethodCall(Expr target, String method, List<Expr> args, int line)`, `Binary(String op, Expr left, Expr right, int line)`, `Unary(String op, Expr operand, int line)`, `Paren(Expr inner, int line)`, `TableCtor(List<TableEntry> entries, int line)`, `Vararg(int line)`. `sealed interface TableEntry` with `Positional(Expr value)`, `Keyed(Expr key, Expr value)`, `Named(String name, Expr value)`.
- `Parser(List<Token> tokens, String fileName)`: `Expr parseExpression()`; `boolean atEnd()`; token cursor helpers are package-private for Task 5.

- [ ] **Step 1: Write the failing tests**

```java
class LuaTableTest {
  @Test void numericKeysUnifyIntAndFloat() { var t = new LuaTable(); t.put(LuaNumber.of(1), new LuaString("a")); assertThat(t.get(new LuaNumber(1.0))).isEqualTo(new LuaString("a")); }
  @Test void arrayLengthAndHoles() { var t = new LuaTable(); t.put(LuaNumber.of(3), LuaNumber.of(9)); assertThat(t.arrayLength()).isEqualTo(3); assertThat(t.array()).containsExactly(LuaNil.NIL, LuaNil.NIL, LuaNumber.of(9)); }
  @Test void identityTableReturnsKey() { assertThat(LuaTable.identity().get("Taragaman")).isEqualTo(new LuaString("Taragaman")); }
  @Test void deepEquals() { assertThat(LuaValues.deepEquals(parse("{1,{2,'x'}}"), parse("{1,{2,'x'}}"))).isTrue(); assertThat(LuaValues.deepEquals(parse("{1,{2}}"), parse("{1,{3}}"))).isFalse(); }
}
class ParserTest {
  @Test void parsesPositionalKeyedAndNamedEntries() {
    Expr e = parse("{ 'a', nil, [3] = 'c', name = 4, }");
    assertThat(((TableCtor) e).entries()).hasSize(4);
    assertThat(((TableCtor) e).entries().get(2)).isInstanceOf(Keyed.class);
    assertThat(((TableCtor) e).entries().get(3)).isEqualTo(new Named("name", new Literal(LuaNumber.of(4), 1)));
  }
  @Test void parsesDottedIndexedAndCalls() {
    assertThat(parse("itemKeys.npcDrops")).isEqualTo(new Field(new Name("itemKeys", 1), "npcDrops", 1));
    assertThat(parse("AL[\"x\"]")).isInstanceOf(Index.class);
    assertThat(parse("data:AddDifficulty(\"NORMAL\", nil)")).isInstanceOf(MethodCall.class);
    assertThat(parse("GetForVersion({10,13},{8,13})")).isInstanceOf(Call.class);
  }
  @Test void precedence() {
    // -1 + 2 * 3 .. 'x' == y and z or w  ->  or( and( ==( ..( +( -1, *(2,3)), 'x'), y), z), w)
    Expr e = parse("-1 + 2 * 3 .. 'x' == y and z or w");
    assertThat(((Binary) e).op()).isEqualTo("or");
    assertThat(((Binary) ((Binary) e).left()).op()).isEqualTo("and");
  }
  @Test void reportsLineOnError() { assertThatThrownBy(() -> parse("{\n[1] = }")).isInstanceOf(LuaSyntaxException.class).hasMessageContaining("line 2"); }
}
```
`parse(s)` in `ParserTest` = `new Parser(new Lexer(s,"t.lua").tokenize(),"t.lua").parseExpression()`; in `LuaTableTest` it is the same followed by `new Evaluator(...)` once Task 4 exists — until then write `LuaTableTest.deepEquals` against hand-built tables.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl lua-table test`
Expected: compilation failure.

- [ ] **Step 3: Implement values, AST and parser**

Precedence (low to high): `or`; `and`; `< > <= >= ~= ==`; `..` (right assoc); `+ -`; `* /`; unary `not - #`. Primary expressions: literal, name, `(expr)`, table constructor, `...`; suffixes: `.name`, `[expr]`, `(args)`, `:name(args)`, and the Lua sugar `f"str"` / `f{...}` (a single string or table argument without parentheses).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl lua-table test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add lua-table && git commit -m "feat(lua): value model and expression parser"
```

---

### Task 4: Evaluator with scopes and call handlers

**Files:**
- Create: `lua-table/src/main/java/dev/wowforever/lua/Scope.java`, `CallHandler.java`, `Problem.java`, `ProblemCollector.java`, `Evaluator.java`
- Test: `lua-table/src/test/java/dev/wowforever/lua/EvaluatorTest.java`

**Interfaces:**
- `final class Scope`: `Scope()` and `Scope(Scope parent)`, `void define(String name, LuaValue v)`, `Optional<LuaValue> lookup(String name)` (walks parents), `Scope child()`.
- `interface CallHandler { Optional<LuaValue> call(String functionName, List<LuaValue> args, int line); }` where `functionName` is the rendered callee (`"format"`, `"GetForVersion"`, `"data:AddDifficulty"`, `"UnitFactionGroup"`); `CallHandler.NONE` returns empty.
- `record Problem(String file, int line, String message)`; `final class ProblemCollector { void add(String file, int line, String message); List<Problem> problems(); int size(); }`.
- `final class Evaluator(String fileName, Scope scope, CallHandler calls, ProblemCollector problems)`: `LuaValue eval(Expr e)`. Rules: unknown `Name` → `LuaUnresolved("unknown name X", line)` + problem; `Field`/`Index` on a non-table → unresolved; on a table → `get` (missing key → `NIL`); `Call`/`MethodCall` → handler, empty → unresolved + problem; arithmetic on numbers; `..` on strings/numbers; `==`/`~=` via `deepEquals`; `and`/`or` with Lua truthiness (nil/false are false); unary `-`, `not`, `#` (array length). `TableCtor`: positional counter increments on every positional entry including nil ones; an entry whose key evaluates to unresolved or nil is dropped and reported; a value that is unresolved is **kept** (so consumers can report at field level).

- [ ] **Step 1: Write the failing tests**

```java
class EvaluatorTest {
  @Test void evaluatesNestedTablesWithHoles() {
    LuaTable t = (LuaTable) eval("{'a', nil, {[12] = {{45, 60}}}, x = -1.5}", new Scope());
    assertThat(t.get(1)).isEqualTo(new LuaString("a")); assertThat(t.get(2)).isEqualTo(LuaNil.NIL);
    assertThat(((LuaTable) ((LuaTable) t.get(3)).get(12)).arrayLength()).isEqualTo(1);
    assertThat(t.get("x")).isEqualTo(new LuaNumber(-1.5));
  }
  @Test void resolvesDottedNamesFromScope() {
    Scope s = new Scope(); LuaTable keys = new LuaTable(); keys.put(new LuaString("npcDrops"), LuaNumber.of(2));
    LuaTable db = new LuaTable(); db.put(new LuaString("itemKeys"), keys); s.define("QuestieDB", db);
    assertThat(eval("QuestieDB.itemKeys.npcDrops", s)).isEqualTo(LuaNumber.of(2));
  }
  @Test void unknownNameBecomesUnresolvedAndIsReported() {
    ProblemCollector p = new ProblemCollector();
    LuaTable t = (LuaTable) eval("{[1] = foo.bar}", new Scope(), CallHandler.NONE, p);
    assertThat(t.get(1)).isInstanceOf(LuaUnresolved.class); assertThat(p.problems()).hasSize(1); assertThat(p.problems().get(0).line()).isEqualTo(1);
  }
  @Test void unresolvedKeyDropsEntry() {
    ProblemCollector p = new ProblemCollector();
    LuaTable t = (LuaTable) eval("{[MISSING] = 1, [2] = 2}", new Scope(), CallHandler.NONE, p);
    assertThat(t.entries()).hasSize(1); assertThat(p.size()).isEqualTo(1);
  }
  @Test void callsGoToHandler() {
    CallHandler h = (name, args, line) -> name.equals("GetForVersion") ? Optional.of(args.get(0)) : Optional.empty();
    assertThat(eval("GetForVersion({10,13},{8,13})", new Scope(), h, new ProblemCollector())).isInstanceOf(LuaTable.class);
    assertThat(eval("data:AddDifficulty('NORMAL')", new Scope(), (n,a,l) -> Optional.of(new LuaString(n)), new ProblemCollector())).isEqualTo(new LuaString("data:AddDifficulty"));
  }
  @Test void arithmeticConcatAndLogic() {
    Scope s = new Scope(); s.define("isHorde", new LuaBoolean(false));
    assertThat(eval("1 + 2 * 3", s)).isEqualTo(LuaNumber.of(7));
    assertThat(eval("'a' .. 1 .. 'b'", s)).isEqualTo(new LuaString("a1b"));
    assertThat(eval("(isHorde and {1}) or {2}", s)).satisfies(v -> assertThat(((LuaTable) v).get(1)).isEqualTo(LuaNumber.of(2)));
    assertThat(eval("'Horde' == 'Horde'", s)).isEqualTo(new LuaBoolean(true));
  }
  @Test void identityTableIndexing() {
    Scope s = new Scope(); s.define("AL", LuaTable.identity());
    assertThat(eval("AL[\"Taragaman the Hungerer\"]", s)).isEqualTo(new LuaString("Taragaman the Hungerer"));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl lua-table test`
Expected: compilation failure.

- [ ] **Step 3: Implement `Scope`, `CallHandler`, `ProblemCollector`, `Evaluator`**

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl lua-table test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add lua-table && git commit -m "feat(lua): evaluator with scopes, call handlers and problem reporting"
```

---

### Task 5: Statement chunks: locals, assignments, functions, return

**Files:**
- Create: `lua-table/src/main/java/dev/wowforever/lua/ast/Stmt.java` (sealed with nested records), `lua-table/src/main/java/dev/wowforever/lua/LuaChunk.java`, `ChunkEvaluator.java`
- Modify: `Parser.java` (add `List<Stmt> parseChunk()`)
- Test: `lua-table/src/test/java/dev/wowforever/lua/LuaChunkTest.java`, `ChunkEvaluatorTest.java`

**Interfaces:**
- `sealed interface Stmt`: `Local(List<String> names, List<Expr> values, int line)`, `Assign(Expr target, Expr value, int line)` (target is `Name`, `Field` or `Index`), `FunctionDef(String qualifiedName, List<String> params, List<Stmt> body, int line)` (qualified like `QuestieItemFixes:Load`, `ForeverBaseItem:Load`, `local function f` → `f`), `Return(Expr value, int line)`, `ExprStmt(Expr expr, int line)`, `Skipped(String keyword, int line)` (an `if`/`for`/`while`/`do`/`repeat` block skipped to its matching `end`/`until`, counting nested openers including `function`).
- `final class LuaChunk`: `static LuaChunk parse(String source, String fileName)`, `static LuaChunk parse(Path file)` (UTF-8), `List<Stmt> statements()`, `String fileName()`, `Optional<FunctionDef> function(String qualifiedName)` (searches top level), `Optional<Expr> assignedValue(String renderedTarget)` where targets render as `QuestieDB.itemData`, `data["Ragefire"]`, `constants.zoneIDs`, `List<Assign> assignments()` (top level, in order), `Optional<String> longStringAssignedTo(String renderedTarget)` (the string literal value, if the assigned expression is a `Literal(LuaString)`), `Optional<Expr> localValue(String name)` (the value expression of a top-level `local name = ...`).
- `final class ChunkEvaluator(String fileName, CallHandler calls, ProblemCollector problems)`: `Optional<LuaValue> run(List<Stmt> stmts, Scope scope)` executes `Local` (define each name; missing values → `NIL`), `Assign` (to `Name` → define in scope; to `Field`/`Index` whose target evaluates to a `LuaTable` → `put`; otherwise report), `ExprStmt` (evaluate, discard), `Skipped` (ignore), `FunctionDef` (ignore), `Return` (evaluate and stop; returned). `LuaValue runFunction(FunctionDef f, Scope scope)` runs the body in `scope.child()` and returns the `Return` value or `NIL`.

- [ ] **Step 1: Write the failing tests**

```java
class LuaChunkTest {
  static final String SRC = """
      local QuestieDB = QuestieLoader:ImportModule("QuestieDB");
      QuestieDB.itemData = [[return {
      [25] = {'Worn Shortsword',nil,nil},
      }]]
      function QuestieItemFixes:Load()
          local itemKeys = QuestieDB.itemKeys
          return { [730] = { [itemKeys.npcDrops] = {1259, 1418} } }
      end
      if UnitFactionGroup("player") == "Horde" then
          HORDE_DIFF = data:AddDifficulty(FACTION_HORDE, "horde", nil, 1)
      else
          ALLIANCE_DIFF = data:AddDifficulty(FACTION_ALLIANCE, "alliance", nil, 1)
      end
      data["Ragefire"] = { MapID = 2437 }
      """;
  @Test void findsLongStringAssignment() { assertThat(LuaChunk.parse(SRC, "f.lua").longStringAssignedTo("QuestieDB.itemData")).hasValueSatisfying(s -> assertThat(s).startsWith("return {")); }
  @Test void findsFunction() { assertThat(LuaChunk.parse(SRC, "f.lua").function("QuestieItemFixes:Load")).hasValueSatisfying(f -> assertThat(f.body()).hasSize(2)); }
  @Test void skipsIfBlocks() { assertThat(LuaChunk.parse(SRC, "f.lua").statements()).filteredOn(s -> s instanceof Stmt.Skipped).hasSize(1); }
  @Test void listsIndexedAssignments() { assertThat(LuaChunk.parse(SRC, "f.lua").assignedValue("data[\"Ragefire\"]")).isPresent(); }
}
class ChunkEvaluatorTest {
  @Test void runsFunctionBodyWithLocalAliases() {
    Scope s = new Scope(); LuaTable keys = new LuaTable(); keys.put(new LuaString("npcDrops"), LuaNumber.of(2));
    LuaTable db = new LuaTable(); db.put(new LuaString("itemKeys"), keys); s.define("QuestieDB", db);
    LuaChunk c = LuaChunk.parse(LuaChunkTest.SRC, "f.lua");
    LuaValue r = new ChunkEvaluator("f.lua", CallHandler.NONE, new ProblemCollector()).runFunction(c.function("QuestieItemFixes:Load").get(), s);
    assertThat(((LuaTable) ((LuaTable) ((LuaTable) r).get(730)).get(2)).array()).containsExactly(LuaNumber.of(1259), LuaNumber.of(1418));
  }
  @Test void topLevelAssignmentsPopulateScopeTables() {
    Scope s = new Scope(); s.define("constants", new LuaTable());
    new ChunkEvaluator("e.lua", CallHandler.NONE, new ProblemCollector()).run(LuaChunk.parse("constants.zoneIDs = { DUSKWOOD = 10 }\nlocal z = constants.zoneIDs", "e.lua").statements(), s);
    assertThat(((LuaTable) s.lookup("z").get()).get("DUSKWOOD")).isEqualTo(LuaNumber.of(10));
  }
  @Test void returnOfNestedChunkFromLongString() {
    LuaChunk outer = LuaChunk.parse(LuaChunkTest.SRC, "f.lua");
    LuaChunk inner = LuaChunk.parse(outer.longStringAssignedTo("QuestieDB.itemData").get(), "f.lua#itemData");
    LuaValue v = new ChunkEvaluator("f.lua#itemData", CallHandler.NONE, new ProblemCollector()).run(inner.statements(), new Scope()).get();
    assertThat(((LuaTable) ((LuaTable) v).get(25)).get(1)).isEqualTo(new LuaString("Worn Shortsword"));
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl lua-table test`
Expected: compilation failure.

- [ ] **Step 3: Implement `Stmt`, `Parser.parseChunk()`, `LuaChunk`, `ChunkEvaluator`**

Statement dispatch on the leading token: `local` (`local function` → `FunctionDef`); `function`; `return`; `if`/`for`/`while`/`do`/`repeat` → skip block; otherwise parse an expression and, if followed by `=`, it is an `Assign`, else an `ExprStmt`. Optional `;` after any statement.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl lua-table test`
Expected: PASS.

- [ ] **Step 5: Parse the real cached files as a sanity test**

Add `lua-table/src/test/java/dev/wowforever/lua/RealFilesTest.java`, `@EnabledIf("cacheExists")`, that parses every `.lua` under `~/.cache/wowforever-importer/questiedb/<sha>/src/corrections/Forever` and `data/Forever` (outer chunk and the inner long strings for `QuestieDB.itemData|npcData|objectData|questData`) and asserts no `LuaSyntaxException`. Run: `./mvnw -B -q -pl lua-table test`. Expected: PASS within 60 s. Fix lexer/parser gaps it reveals before committing.

- [ ] **Step 6: Commit**

```bash
git add lua-table && git commit -m "feat(lua): statement chunks, function bodies and long-string extraction"
```

---

### Task 6: Importer sources, schemas and raw entity loading

**Files:**
- Create: `importer/src/main/resources/dev/wowforever/importer/sources.properties`
- Create: `importer/src/main/java/dev/wowforever/importer/source/SourceSpec.java`, `SourceFetcher.java`, `SourceRoots.java`
- Create: `importer/src/main/java/dev/wowforever/importer/schema/FieldType.java`, `Shape.java`, `EntitySchema.java`, `MetaLoader.java`
- Create: `importer/src/main/java/dev/wowforever/importer/entity/EntityKind.java`, `EntityTable.java`, `RawEntityLoader.java`
- Create: fixture tree `importer/src/test/resources/fixtures/questiedb/` and `importer/src/test/resources/fixtures/atlasloot/` (see Step 1)
- Create: `importer/src/test/java/dev/wowforever/importer/Fixtures.java`
- Modify: `importer/pom.xml` (add `maven-jar-plugin` `test-jar` goal); `app/pom.xml` (add test-scope dependencies on `importer` and `importer` `<type>test-jar</type>`)
- Test: `importer/src/test/java/dev/wowforever/importer/source/SourceFetcherTest.java`, `schema/MetaLoaderTest.java`, `entity/RawEntityLoaderTest.java`

**Interfaces:**
- `sources.properties` keys: `questiedb.owner=Questie`, `questiedb.repo=QuestieDB`, `questiedb.commit=49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4`, `questiedb.paths=` (comma-separated, exactly the spec §3 QuestieDB table minus `docs/*`), `atlasloot.owner=Hoizame`, `atlasloot.repo=AtlasLootClassic`, `atlasloot.commit=8e99341e4e779328460bf7684c0d5b22ce50ddf1`, `atlasloot.paths=AtlasLootClassic_DungeonsAndRaids/data.lua,AtlasLootClassic_DungeonsAndRaids/droprate.lua,AtlasLootClassic_DungeonsAndRaids/droprate_override.lua,LICENSE`.
- `record SourceSpec(String key, String owner, String repo, String commit, List<String> paths)` with `static List<SourceSpec> load()` (from the properties resource) and `URI rawUrl(String path)`.
- `final class SourceFetcher(Path cacheDir, HttpClient http)`: `Path resolve(SourceSpec spec, Optional<Path> localDir)` → returns `localDir` if present, else `cacheDir/<key>/<commit>` after downloading every missing path (atomic write via temp file + move; `IOException` with the URL on failure). `static Path defaultCacheDir()` = `~/.cache/wowforever-importer`.
- `record SourceRoots(Path questieDb, Path atlasLoot)`.
- `enum FieldType { NUMBER, STRING, TABLE }`; `enum Shape { IDARRAY, STRINGARRAY, PAIR, PAIRS, QUESTGIVERS, OBJECTIVES, SPAWNLIST, WAYPOINTLIST, TRIGGER, EXTRAOBJECTIVES }`.
- `final class EntitySchema`: `String entity()`, `int fieldCount()`, `int index(String name)` (throws `IllegalArgumentException` for unknown), `String name(int index)`, `FieldType type(int)`, `Optional<Shape> shape(int)`, `List<String> names()`; `static EntitySchema fromMeta(Path metaFile)` via `MetaLoader`.
- `MetaLoader`: `static EntitySchema load(Path metaFile)` — evaluates the `local meta = {...}` statement (scope has `LibQuestieDB` = `NIL`-safe: define nothing; the trailing `if LibQuestieDB then` block is skipped by the parser) and reads `entity`, `fieldCount`, `keys`, `types`, `structures`.
- `enum EntityKind { ITEM("Item","itemData","itemKeys"), NPC("Npc","npcData","npcKeys"), OBJECT("Object","objectData","objectKeys"), QUEST("Quest","questData","questKeys") }` with `String metaEntity()`, `String dataField()`, `String keysField()`.
- `final class EntityTable(EntityKind kind, EntitySchema schema)`: `SortedMap<Long, LuaValue[]> rows()`, `boolean contains(long id)`, `LuaValue[] create(long id)` (nil-filled), `LuaValue get(long id, String field)`, `void set(long id, String field, LuaValue v)`, `int size()`.
- `RawEntityLoader`: `static EntityTable load(Path dbFile, EntityKind kind, EntitySchema schema, ProblemCollector problems)` — takes `longStringAssignedTo("QuestieDB." + kind.dataField())`, parses the inner chunk, evaluates with an empty scope, converts each `[id] = {...}` into a row (positional 1..fieldCount; entries beyond `fieldCount` reported).
- `Fixtures` (test-jar): `static Path questieDbRoot()`, `static Path atlasLootRoot()` (copies the classpath fixture trees to a temp dir once per JVM), `static SourceRoots roots()`.

- [ ] **Step 1: Build the fixture tree**

Copy from the cache into `importer/src/test/resources/fixtures/questiedb/` the same relative paths as upstream, each file **truncated to a handful of entities** but keeping the exact framing (header lines, `[[return {`, `}]]`, `function X:Load()` ... `end`). Required content:
- `data/Forever/foreverItemDB.lua`: items 25, 730, 2874, 5191, 14145, 14148, 14149, 16800, 19019 (copy the real rows).
- `data/Forever/foreverNpcDB.lua`: npcs 448, 639, 1259, 11518, 11520, 12118, 11502 (copy the real rows).
- `data/Forever/foreverObjectDB.lua`: objects 1560, 2843 (real rows).
- `data/Forever/foreverQuestDB.lua`: quests 2, 136, 9422 (real rows).
- `src/meta/*Meta.lua`: copy whole files (they are small).
- `src/corrections/enum/*.lua`: copy whole files.
- `src/corrections/Forever/legacy/classicItemFixes.lua`: keep header, `Load()` with entries 730 and 1262 only, and the `LoadFactionFixes` function body replaced by `return {}`.
- `src/corrections/Forever/legacy/classicNPCFixes.lua`: `Load()` with entries 300 and 314 (uses `zoneIDs`), `LoadFactionFixes` → `return {}`.
- `src/corrections/Forever/legacy/classicObjectFixes.lua`, `classicQuestFixes.lua`, `classicQuestReputationFixes.lua`: header + `Load()` returning two real entries each.
- `src/corrections/Forever/legacy/itemStartFixes.lua`: entries 1307 and 1357.
- `src/corrections/Forever/generated/foreverBaseItem.lua`: entries 117 (`vendors_add`), 159, and one real entry whose id is not in the raw fixture (a created entity).
- `src/corrections/Forever/generated/foreverBaseNpc.lua`, `foreverBaseObject.lua`, `foreverBaseQuest.lua`: two real entries each (include one new-id entity in the npc file).
- `src/corrections/Forever/traces/*.lua`: two real entries each; `foreverItemTraces.lua` must include `[190179]` (`name` "Avelina's Heart", `npcDrops_add` `{185333}`); NPC 185333 must **not** exist in any fixture (it is the unknown-reference case).
- `src/corrections/Forever/forever{Item,NPC,Object,Quest}Fixes.lua`: header, `Load()` with entries 159 and 750 (item), two npc entries, two object entries, quest 490 (creates fields with `raceIDs.ALL_ALLIANCE`, `factionIDs.DARNASSUS`); `LoadDynamic()` → `return {}`.
- `support/Forever/DropTables/classicItemDrops.lua`: header + entries for items 159 and 2874 (`[2874] = { [639] = 56.07 }` may be hand-written).
- `support/Forever/DropTables/itemDropCorrections.lua`: header + `Era = { [725] = { [98] = DropKeys.WOWHEAD }, [2874] = { [639] = 60.0 } }`, other expansions `{}`.
- `support/Forever/Zones/dungeons.lua`: header + entries 209, 718, 1581, 2437, 2717 and the trailing `if` block as-is.
- `support/Forever/Zones/subZoneToParentZone.lua`: header + 5 entries.
- `importer/src/test/resources/fixtures/atlasloot/AtlasLootClassic_DungeonsAndRaids/data.lua`: the full header (lines 1–60 of the real file), the `KEYS` local, and the `Ragefire`, `TheDeadmines` and `MoltenCore` instances (copy verbatim, trimming MoltenCore to Lucifron and Magmadar). `droprate.lua`: header + the real `[639]` entry and a hand-written `[11520] = { [14149] = 33.3, [14148] = 33.3, [14145] = 33.3 }` entry. `droprate_override.lua`: header + `[639] = { [2874] = 57.0 }`.

- [ ] **Step 2: Write the failing tests**

```java
class SourceFetcherTest {
  @Test void usesLocalDirWhenGiven(@TempDir Path tmp) { SourceSpec s = SourceSpec.load().get(0); assertThat(new SourceFetcher(tmp, HttpClient.newHttpClient()).resolve(s, Optional.of(Fixtures.questieDbRoot()))).isEqualTo(Fixtures.questieDbRoot()); }
  @Test void downloadsMissingFilesIntoCache(@TempDir Path tmp) {
    // fake HttpClient via a tiny local HttpServer (com.sun.net.httpserver) serving "ok:" + path
    SourceSpec s = new SourceSpec("t", "o", "r", "abc", List.of("a/b.lua"));
    Path root = new SourceFetcher(tmp, client, baseUri).resolve(s, Optional.empty());   // overload with base URI for tests
    assertThat(root.resolve("a/b.lua")).content().isEqualTo("ok:/o/r/abc/a/b.lua");
    assertThat(root).isEqualTo(tmp.resolve("t/abc"));
  }
  @Test void specLoadsBothSources() { assertThat(SourceSpec.load()).extracting(SourceSpec::key).containsExactly("questiedb", "atlasloot"); }
}
class MetaLoaderTest {
  @Test void loadsItemSchema() {
    EntitySchema s = MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/itemMeta.lua"));
    assertThat(s.fieldCount()).isEqualTo(16); assertThat(s.index("npcDrops")).isEqualTo(2); assertThat(s.shape(2)).contains(Shape.IDARRAY); assertThat(s.type(1)).isEqualTo(FieldType.STRING); assertThat(s.shape(1)).isEmpty();
  }
  @Test void loadsQuestShapes() { EntitySchema s = MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/questMeta.lua")); assertThat(s.shape(s.index("startedBy"))).contains(Shape.QUESTGIVERS); assertThat(s.shape(s.index("reputationReward"))).contains(Shape.PAIRS); }
}
class RawEntityLoaderTest {
  @Test void loadsItems() {
    EntityTable t = RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/foreverItemDB.lua"), EntityKind.ITEM, itemSchema(), new ProblemCollector());
    assertThat(t.get(14149, "name")).isEqualTo(new LuaString("Subterranean Cape"));
    assertThat(((LuaTable) t.get(14149, "npcDrops")).array()).contains(LuaNumber.of(11520));
    assertThat(t.get(25, "npcDrops")).isEqualTo(LuaNil.NIL);
    assertThat(LuaValues.asLong(t.get(25, "itemLevel"))).hasValue(2);
  }
  @Test void loadsNpcSpawns() {
    EntityTable t = RawEntityLoader.load(Fixtures.questieDbRoot().resolve("data/Forever/foreverNpcDB.lua"), EntityKind.NPC, npcSchema(), new ProblemCollector());
    LuaTable spawns = (LuaTable) t.get(448, "spawns");   // Hogger
    assertThat(spawns.get(12)).isInstanceOf(LuaTable.class);
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw -B -q -pl importer test`
Expected: compilation failure.

- [ ] **Step 4: Implement the classes and the test-jar goal**

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw -B -q -pl lua-table,importer install -DskipTests=false`
Expected: PASS; `importer/target/importer-0.1.0-SNAPSHOT-tests.jar` exists.

- [ ] **Step 6: Commit**

```bash
git add importer && git commit -m "feat(importer): source fetching, entity schemas, raw entity loading and fixtures"
```

---

### Task 7: Enum tables and the correction scope

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/corrections/EnumLoader.java`, `CorrectionScope.java`, `CorrectionKey.java`
- Test: `importer/src/test/java/dev/wowforever/importer/corrections/EnumLoaderTest.java`, `CorrectionScopeTest.java`

**Interfaces:**
- `EnumLoader`: `static LuaTable load(Path questieDbRoot, ProblemCollector problems)` — evaluates, in this order, `src/corrections/enum/{constants,zones,factions,items,quests,professions,icons,phases,waypoints,expansions}.lua` into one `constants` `LuaTable`. Scope for each file: `LibQuestieDB` = table with `Enum` → the shared constants table (so `local constants = LibQuestieDB.Enum` works); the `constants.lua` file's `if LibQuestieDB then ... else ... end` is skipped, so `constants` is pre-defined by the loader.
- `record CorrectionKey(Op op, int fieldIndex)` with `enum Op { REPLACE, ADD, REMOVE }`; `static Optional<CorrectionKey> parse(LuaValue key)` — `LuaNumber n` → `REPLACE`; `LuaString "op:add:N"` → `ADD`; `"op:remove:N"` → `REMOVE`; else empty.
- `CorrectionScope`: `record Bound(Scope scope, CallHandler calls)`; `static Bound build(LuaTable constants, Map<EntityKind, EntitySchema> schemas)` returning a scope that defines:
  - `QuestieDB` table with `itemKeys`, `npcKeys`, `objectKeys`, `questKeys` (each `name → LuaNumber(index)`, `name_add → LuaString("op:add:" + index)`, `name_remove → LuaString("op:remove:" + index)`), plus `raceKeys`, `classKeys`, `npcFlags` from `constants.byExpansion.Forever` (falling back per table to `Classic` when Forever lacks it), `sortKeys`, `specialFlags`, `questFlags`, `factionIDs`, `itemClasses`, `waypointPresets`, `iconTypes` from `constants`.
  - `ZoneDB` table with `zoneIDs`; `QuestieProfessions` with `professionKeys`, `specializationKeys`, `rankNames`; `Phasing` with `phases`; `QuestieCorrections` as an empty table; `DropDB` with `correctionKeys` = `constants.dropCorrectionKeys`; `l10n` empty table.
  - `QuestieLoader` handled by the call handler: `bound.calls()` answers `QuestieLoader:ImportModule(name)` and `QuestieLoader:CreateModule(name)` with the scope's table of that name (or an empty table), `UnitClassBase(...)` → `"WARRIOR"`, `UnitFactionGroup(...)` → `"Alliance"`.

- [ ] **Step 1: Write the failing tests**

```java
class EnumLoaderTest {
  @Test void loadsZoneAndFactionIds() {
    LuaTable c = EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector());
    assertThat(((LuaTable) c.get("zoneIDs")).get("DUSKWOOD")).isEqualTo(LuaNumber.of(10));
    assertThat(((LuaTable) c.get("factionIDs")).get("DARNASSUS")).isEqualTo(LuaNumber.of(69));
  }
  @Test void computesForeverRaceMasks() {
    LuaTable c = EnumLoader.load(Fixtures.questieDbRoot(), new ProblemCollector());
    LuaTable forever = (LuaTable) ((LuaTable) c.get("byExpansion")).get("Forever");
    assertThat(((LuaTable) forever.get("raceKeys")).get("HUMAN")).isEqualTo(LuaNumber.of(1));
    assertThat(LuaValues.asLong(((LuaTable) forever.get("raceKeys")).get("ALL_ALLIANCE"))).isPresent();
  }
}
class CorrectionScopeTest {
  @Test void bindsKeysAndOperations() {
    Scope s = CorrectionScope.build(constants(), schemas()).scope();
    assertThat(eval("QuestieDB.itemKeys.npcDrops", s)).isEqualTo(LuaNumber.of(2));
    assertThat(CorrectionKey.parse(eval("QuestieDB.itemKeys.npcDrops_add", s))).contains(new CorrectionKey(Op.ADD, 2));
    assertThat(CorrectionKey.parse(eval("QuestieDB.questKeys.finishedBy_remove", s))).contains(new CorrectionKey(Op.REMOVE, 3));
  }
  @Test void bindsAliasesUsedByCorrectionFiles() {
    Scope s = CorrectionScope.build(constants(), schemas()).scope();
    assertThat(eval("ZoneDB.zoneIDs.ELWYNN_FOREST", s)).isEqualTo(LuaNumber.of(12));
    assertThat(eval("QuestieDB.raceKeys.ALL_ALLIANCE", s)).isInstanceOf(LuaNumber.class);
    assertThat(eval("QuestieProfessions.professionKeys.COOKING", s)).isEqualTo(LuaNumber.of(185));
    assertThat(eval("QuestieDB.itemClasses.QUEST", s)).isEqualTo(LuaNumber.of(12));
  }
  @Test void loaderCallsReturnScopeTables() {
    CorrectionScope.Bound b = CorrectionScope.build(constants(), schemas());
    LuaValue v = b.calls().call("QuestieLoader:ImportModule", List.of(new LuaString("ZoneDB")), 1).get();
    assertThat(((LuaTable) v).get("zoneIDs")).isInstanceOf(LuaTable.class);
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl importer test`
Expected: compilation failure.

- [ ] **Step 3: Implement `EnumLoader`, `CorrectionKey`, `CorrectionScope`**

The `CallHandler` in `Bound` closes over the built scope so `QuestieLoader:ImportModule("ZoneDB")` returns the same table instance the scope holds.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl importer test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add importer && git commit -m "feat(importer): enum tables and correction symbol scope"
```

---

### Task 8: Correction layers and the applier

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/corrections/CorrectionLayer.java`, `CorrectionLayers.java`, `TableOps.java`, `CorrectionApplier.java`
- Create: `importer/src/main/java/dev/wowforever/importer/report/ImportReport.java`, `Skip.java`, `UiFields.java`
- Test: `importer/src/test/java/dev/wowforever/importer/corrections/TableOpsTest.java`, `CorrectionApplierTest.java`, `CorrectionLayersTest.java`

**Interfaces:**
- `record CorrectionLayer(String name, String relativePath, String function, EntityKind kind)`; `CorrectionLayers.forever()` returns, in order: legacy `classicItemFixes.lua`/`QuestieItemFixes:Load`/ITEM, `classicNPCFixes.lua`/`QuestieNPCFixes:Load`/NPC, `classicObjectFixes.lua`/`QuestieObjectFixes:Load`/OBJECT, `classicQuestFixes.lua`/`QuestieQuestFixes:Load`/QUEST, `classicQuestReputationFixes.lua`/`QuestieClassicQuestReputationFixes:Load`/QUEST, `itemStartFixes.lua`/`QuestieItemStartFixes:LoadAutomaticQuestStarts`/ITEM; generated `foreverBaseItem.lua`/`ForeverBaseItem:Load`, `foreverBaseNpc.lua`/`ForeverBaseNpc:Load`, `foreverBaseObject.lua`/`ForeverBaseObject:Load`, `foreverBaseQuest.lua`/`ForeverBaseQuest:Load`; traces `foreverItemTraces.lua`/`ForeverItemTraces:Load`, `foreverNpcTraces.lua`/`ForeverNpcTraces:Load`, `foreverObjectTraces.lua`/`ForeverObjectTraces:Load`, `foreverQuestTraces.lua`/`ForeverQuestTraces:Load`; authored `foreverItemFixes.lua`/`ForeverItemFixes:Load`, `foreverNPCFixes.lua`/`ForeverNpcFixes:Load`, `foreverObjectFixes.lua`/`ForeverObjectFixes:Load`, `foreverQuestFixes.lua`/`ForeverQuestFixes:Load`. All paths under `src/corrections/Forever/`.
- `record Skip(String layer, EntityKind kind, long entityId, String field, String reason)`.
- `final class ImportReport`: `void count(String counter, long delta)`, `long get(String counter)`, `void skip(Skip s)`, `List<Skip> skips()`, `long skipsOnUiFields()`, `String render()` (counters sorted, then skips grouped by layer+field with counts, then first 50 raw skips), `void writeTo(Path)`.
- `UiFields`: `static boolean isUiField(EntityKind kind, String field)` per spec §3 acceptance bar: ITEM `name,npcDrops,objectDrops,itemDrops,vendors,questRewards,relatedQuests,startQuest,class,subClass,itemLevel,requiredLevel`; NPC `name,subName,minLevel,maxLevel,rank,zoneID,spawns,questStarts,questEnds,npcFlags`; OBJECT `name,zoneID,spawns,questStarts,questEnds`; QUEST `name,startedBy,finishedBy,requiredLevel,questLevel,zoneOrSort,objectivesText,questFlags,specialFlags`.
- `TableOps`: `static Optional<LuaValue> add(Shape shape, LuaValue existing, LuaTable operand)`, `static Optional<LuaValue> remove(Shape shape, LuaValue existing, LuaTable operand)`; empty `Optional` means the shape is unsupported (`OBJECTIVES`, `WAYPOINTLIST`, `TRIGGER`, `EXTRAOBJECTIVES`, `PAIR`). Semantics: `IDARRAY`/`STRINGARRAY`/`PAIRS` — append absent elements in operand order after existing (deep equality), remove all equal; `QUESTGIVERS` — operand groups `[1]`,`[2]`,`[3]` each applied as `IDARRAY` to the existing group, untouched groups preserved, result keeps nil holes; `SPAWNLIST` — operand `[zoneId] = {tuples}`, per zone apply as list of tuples where tuples compare equal when x and y match and phases match treating absent phase as 0. An empty operand `{}` returns `existing` unchanged. `existing == NIL` is treated as an empty table of the shape.
- `final class CorrectionApplier(ImportReport report)`: `void apply(EntityTable table, LuaTable corrections, String layerName)`; `void applyLayer(CorrectionLayer layer, Path questieDbRoot, Map<EntityKind, EntityTable> tables, CorrectionScope.Bound scope, ProblemCollector problems)` (parses the file, runs the function, calls `apply`). Row rules: key not an integer → `Skip(field="<id>")`; row value not a table → skip; missing entity → `create` + `count("created." + kind)`; per field: `CorrectionKey.parse` fails → skip `"unknown key"`; value `LuaUnresolved` → skip with its reason; REPLACE with empty `LuaTable` → set `NIL`, `count("deleted")`; REPLACE with non-empty table on a NUMBER/STRING field → skip `"table into scalar"`; REPLACE otherwise → set, `count("replaced")`; ADD/REMOVE → `TableOps`, empty → skip `"unsupported op on shape X"`, else set, `count("ops")`. A REPLACE and an op on the same field in one row: apply both and `count("replace+op")`.

- [ ] **Step 1: Write the failing tests**

```java
class TableOpsTest {
  @Test void idarrayAddAppendsAbsent() { assertThat(arr(TableOps.add(IDARRAY, lua("{1,2}"), (LuaTable) lua("{2,3}")).get())).containsExactly(1L, 2L, 3L); }
  @Test void idarrayRemoveDeletesAllMatches() { assertThat(arr(TableOps.remove(IDARRAY, lua("{1,2,2,3}"), (LuaTable) lua("{2}")).get())).containsExactly(1L, 3L); }
  @Test void nilExistingActsAsEmpty() { assertThat(arr(TableOps.add(IDARRAY, LuaNil.NIL, (LuaTable) lua("{5}")).get())).containsExactly(5L); }
  @Test void questgiversAddKeepsOtherGroups() { LuaTable r = (LuaTable) TableOps.add(QUESTGIVERS, lua("{{17079}}"), (LuaTable) lua("{[2] = {424005}}")).get(); assertThat(arr(r.get(1))).containsExactly(17079L); assertThat(arr(r.get(2))).containsExactly(424005L); }
  @Test void spawnlistRemoveMatchesTuplesIgnoringZeroPhase() { LuaTable r = (LuaTable) TableOps.remove(SPAWNLIST, lua("{[12] = {{45,60,0},{46,61}}}"), (LuaTable) lua("{[12] = {{45,60}}}")).get(); assertThat(((LuaTable) r.get(12)).arrayLength()).isEqualTo(1); }
  @Test void pairsUseWholeRecordEquality() { assertThat(((LuaTable) TableOps.remove(PAIRS, lua("{{72,100},{73,200}}"), (LuaTable) lua("{{72,999}}")).get()).arrayLength()).isEqualTo(2); }
  @Test void unsupportedShapeIsEmpty() { assertThat(TableOps.add(OBJECTIVES, LuaNil.NIL, (LuaTable) lua("{}"))).isEmpty(); }
}
class CorrectionApplierTest {
  @Test void replacesDeletesAndCreates() {
    EntityTable items = itemsWith(730);                         // name "Murloc Eye", npcDrops {1,2}
    apply(items, "{ [730] = { [itemKeys.npcDrops] = {9}, [itemKeys.vendors] = {} }, [999999] = { [itemKeys.name] = 'New' } }", "test");
    assertThat(arr(items.get(730, "npcDrops"))).containsExactly(9L); assertThat(items.get(730, "vendors")).isEqualTo(LuaNil.NIL);
    assertThat(items.get(999999, "name")).isEqualTo(new LuaString("New")); assertThat(report.get("created.ITEM")).isEqualTo(1);
  }
  @Test void createsMissingEntityWithPartialFields() {
    EntityTable items = itemsWith();
    apply(items, "{ [190179] = { [itemKeys.name] = \"Avelina's Heart\", [itemKeys.npcDrops_add] = {185333} } }", "traces");
    assertThat(items.get(190179, "itemLevel")).isEqualTo(LuaNil.NIL); assertThat(arr(items.get(190179, "npcDrops"))).containsExactly(185333L);
  }
  @Test void reportsUnresolvedValuesAndUnsupportedOps() {
    EntityTable quests = questsWith(2);
    apply(quests, "{ [2] = { [questKeys.objectives_add] = {{{123}}}, [questKeys.name] = UNKNOWN_SYMBOL } }", "x");
    assertThat(report.skips()).extracting(Skip::field).containsExactlyInAnyOrder("objectives", "name");
    assertThat(report.skipsOnUiFields()).isEqualTo(1);
  }
  @Test void tableIntoScalarIsSkipped() { EntityTable items = itemsWith(730); apply(items, "{ [730] = { [itemKeys.itemLevel] = {1,2} } }", "x"); assertThat(report.skips()).hasSize(1); assertThat(items.get(730, "itemLevel")).isNotEqualTo(lua("{1,2}")); }
}
class CorrectionLayersTest {
  @Test void appliesAllFixtureLayersInOrder() {
    Map<EntityKind, EntityTable> tables = loadFixtureTables();
    CorrectionApplier a = new CorrectionApplier(report);
    for (CorrectionLayer l : CorrectionLayers.forever()) a.applyLayer(l, Fixtures.questieDbRoot(), tables, scope, problems);
    assertThat(tables.get(ITEM).get(159, "npcDrops")).isEqualTo(LuaNil.NIL);          // foreverItemFixes deletes
    assertThat(tables.get(ITEM).contains(190179)).isTrue();                            // trace-created entity
    assertThat(LuaValues.asLong(tables.get(ITEM).get(1307, "startQuest"))).hasValue(123);
    assertThat(tables.get(QUEST).get(490, "name")).isEqualTo(new LuaString("Bounty: Gnarlpine Furbolg"));
    assertThat(report.skipsOnUiFields()).isZero();
  }
}
```
Helpers: `lua(s)` evaluates with `CorrectionScope`; `apply(table, src, layer)` evaluates `src` in the correction scope and calls `applier.apply`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -B -q -pl importer test`
Expected: compilation failure.

- [ ] **Step 3: Implement `TableOps`, `ImportReport`, `Skip`, `UiFields`, `CorrectionLayer(s)`, `CorrectionApplier`**

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -B -q -pl importer test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add importer && git commit -m "feat(importer): layered corrections with add/remove table operations and reporting"
```

---

### Task 9: Drop rates and zones

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/drops/DropRate.java`, `DropRates.java`, `DropRateLoader.java`
- Create: `importer/src/main/java/dev/wowforever/importer/zones/Zone.java`, `ZoneNames.java`, `ZoneLoader.java`
- Test: `importer/src/test/java/dev/wowforever/importer/drops/DropRateLoaderTest.java`, `zones/ZoneLoaderTest.java`, `zones/ZoneNamesTest.java`

**Interfaces:**
- `record DropRate(double percent, String source)` with sources `"questie-correction"`, `"atlasloot-override"`, `"questie-wowhead"`, `"atlasloot"`.
- `final class DropRates`: `void put(long npcId, long itemId, DropRate r, int precedence)` (lower precedence number wins; keep the best), `Optional<DropRate> get(long npcId, long itemId)`, `int size()`.
- `DropRateLoader`: `static DropRates load(SourceRoots roots, ProblemCollector problems)` — reads Questie `support/Forever/DropTables/classicItemDrops.lua` (`QuestieClassicItemDrops.wowheadData` long string → `[itemId] = {[npcId] = pct}`; precedence 3), `itemDropCorrections.lua` (`QuestieItemDropCorrections.Era` table only; numeric values → precedence 1; `WOWHEAD`/`PSERVER` markers (−1/−2) ignored; scope defines `DropDB.correctionKeys`), AtlasLoot `droprate.lua` (`local DropRateData` → `[npcId] = {[itemId] = pct}`; precedence 4) and `droprate_override.lua` (precedence 2). The AtlasLoot files start with `local Droprate = _G.AtlasLoot.Data.Droprate`; scope defines `_G` as an empty table so that line resolves to `NIL` without a problem. The DropRateData table is read via `LuaChunk.assignedValue("DropRateData")` on the `Local` statement — add `Optional<Expr> localValue(String name)` to `LuaChunk` if not already present.
- `record Zone(long id, String name, Long parentId, String kind)`; kinds `zone`, `subzone`, `dungeon`, `raid`, `city`, `continent`.
- `ZoneNames`: `static String fromSymbol(String symbol)` — title-case words split on `_`, keep `OF/THE/AND` lowercase except first word, apply the override map (`THE_TEMPLE_OF_ATAL_HAKKAR → "The Temple of Atal'Hakkar"`, `ZUL_FARRAK → "Zul'Farrak"`, `ZUL_GURUB → "Zul'Gurub"`, `RUINS_OF_AHN_QIRAJ → "Ruins of Ahn'Qiraj"`, `TEMPLE_OF_AHN_QIRAJ → "Temple of Ahn'Qiraj"`, `ONYXIAS_LAIR → "Onyxia's Lair"`, `BLACKFATHOM_DEEPS`, `NIGHTS_WEB_HOLLOW → "Night's Web Hollow"`, `STORMWIND_CITY → "Stormwind City"`; extend as the real import reveals others, matching Wowhead spelling).
- `ZoneLoader`: `static Map<Long, Zone> load(Path questieDbRoot, LuaTable constants, Set<Long> raidAreaIds, ProblemCollector problems)` — one `Zone` per `constants.zoneIDs` entry (kind `zone`; ids 10073, 10074, 10075, 10089 → `continent`; ids 1497, 1519, 1537, 1637, 1638, 1657 → `city`); `dungeons.lua` entries override the name and set parent + kind (`raid` if the id is in `raidAreaIds`, else `dungeon`); `subZoneToParentZone.lua` entries add missing ids as kind `subzone` with the given parent (name from `zoneIDs` symbol when present, else `"Zone #<id>"`).

- [ ] **Step 1: Write the failing tests**

```java
class DropRateLoaderTest {
  @Test void precedenceAcrossSources() {
    DropRates r = DropRateLoader.load(Fixtures.roots(), new ProblemCollector());
    assertThat(r.get(639, 2874)).contains(new DropRate(60.0, "questie-correction"));   // correction beats override 57.0 and wowhead 56.07
    assertThat(r.get(11520, 14149)).hasValueSatisfying(d -> assertThat(d.source()).isEqualTo("atlasloot"));
  }
  @Test void markerValuesAreIgnored() { assertThat(DropRateLoader.load(Fixtures.roots(), new ProblemCollector()).get(98, 725)).isEmpty(); }
}
class ZoneNamesTest {
  @Test void titleCasesSymbols() { assertThat(ZoneNames.fromSymbol("DUN_MOROGH")).isEqualTo("Dun Morogh"); assertThat(ZoneNames.fromSymbol("THE_HINTERLANDS")).isEqualTo("The Hinterlands"); assertThat(ZoneNames.fromSymbol("SWAMP_OF_SORROWS")).isEqualTo("Swamp of Sorrows"); assertThat(ZoneNames.fromSymbol("ZUL_FARRAK")).isEqualTo("Zul'Farrak"); }
}
class ZoneLoaderTest {
  @Test void mergesZoneSources() {
    Map<Long, Zone> z = ZoneLoader.load(Fixtures.questieDbRoot(), constants(), Set.of(2717L), new ProblemCollector());
    assertThat(z.get(10L)).isEqualTo(new Zone(10, "Duskwood", null, "zone"));
    assertThat(z.get(1581L).name()).isEqualTo("The Deadmines"); assertThat(z.get(1581L).parentId()).isEqualTo(40L); assertThat(z.get(1581L).kind()).isEqualTo("dungeon");
    assertThat(z.get(2717L).kind()).isEqualTo("raid");
    assertThat(z.get(133L).kind()).isEqualTo("subzone");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl importer test`. Expected: compilation failure.

- [ ] **Step 3: Implement the drop-rate and zone loaders**

- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q -pl importer test`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add importer lua-table && git commit -m "feat(importer): drop-rate precedence and zone tables"
```

---

### Task 10: AtlasLoot instances, bosses and loot

**Files:**
- Create: `importer/src/main/java/dev/wowforever/importer/atlasloot/AtlasInstance.java`, `AtlasBoss.java`, `AtlasLoot.java`, `AtlasLootScope.java`, `AtlasLootLoader.java`
- Test: `importer/src/test/java/dev/wowforever/importer/atlasloot/AtlasLootLoaderTest.java`

**Interfaces:**
- `record AtlasLoot(String difficulty, int slot, long itemId)`; `record AtlasBoss(String name, Long npcId, Long objectId, Integer level, String specialType, boolean extraList, int sortOrder, List<AtlasLoot> loot)`; `record AtlasInstance(String key, String name, Long mapId, Long instanceId, String contentType, Integer levelMin, Integer levelMax, List<AtlasBoss> bosses)`.
- `AtlasLootScope`: `static Scope scope()` defining `AL` and `ALIL` as `LuaTable.identity()`, `NORMAL_DIFF="NORMAL"`, `RAID20_DIFF="RAID20"`, `RAID40_DIFF="RAID40"`, `ALLIANCE_DIFF="ALLIANCE"`, `HORDE_DIFF="HORDE"`, `LOAD_DIFF="ALLIANCE"`, `NORMAL_ITTYPE="Item"`, `SET_ITTYPE="Set"`, `QUEST_EXTRA_ITTYPE="Quest"`, `PRICE_EXTRA_ITTYPE="Price"`, `DUNGEON_CONTENT="DUNGEON"`, `RAID20_CONTENT="RAID20"`, `RAID40_CONTENT="RAID40"`, `ATLASLOOT_IT_ALLIANCE="ALLIANCE"`, `ATLASLOOT_IT_HORDE="HORDE"`, `ATLASLOOT_IT_AMOUNT1="AMOUNT1"`, `_G` = empty table, `ATLASLOOT_DUNGEON_COLOR`/`ATLASLOOT_RAID20_COLOR`/`ATLASLOOT_RAID40_COLOR` = `""`; `static CallHandler calls()` answering `format` (replace each `%s`/`%d` in order with the following args rendered as strings), `GetForVersion` → first arg, `data:AddDifficulty|AddItemTableType|AddExtraItemTableType|AddContentType` → `LuaString` of the first arg (so the top-level `local` lines evaluate consistently even though pre-defined), `UnitFactionGroup` → `"Alliance"`, `AtlasLoot.ItemDB:Add` and `getfenv` → empty table, anything else → empty `Optional`.
- `AtlasLootLoader`: `static List<AtlasInstance> load(Path atlasLootRoot, Map<Long, String> areaNames, ProblemCollector problems)` — runs the top-level chunk of `AtlasLootClassic_DungeonsAndRaids/data.lua` with `ChunkEvaluator` (so `local KEYS = {...}` etc. land in scope), then takes every top-level `Assign` whose target renders as `data["..."]`; `name` = `areaNames.get(MapID)` else key split on camel-case (`"TheDeadmines" → "The Deadmines"`); `LevelRange` `{min, max, maxRec}` → min/max; `contentType` from `ContentType`. Each entry of `items` that is a table: skip if `TableType == "Set"` or `IgnoreAsSource == true`; `name` with WoW colour codes stripped (`|cffRRGGBB` and `|r` removed, `|r %s`-style leftovers trimmed); `npcId` from `npcID` (if it is a table, first element); `objectId` from `ObjectID`; loot rows from every entry whose key is one of the difficulty strings: row `{slot, itemId}` with numeric second element → `AtlasLoot`; string second element → skipped silently (header/icon row); `GetItemsFromDiff` entries ignored.

- [ ] **Step 1: Write the failing tests**

```java
class AtlasLootLoaderTest {
  @Test void loadsInstancesBossesAndLoot() {
    List<AtlasInstance> all = AtlasLootLoader.load(Fixtures.atlasLootRoot(), Map.of(2437L, "Ragefire Chasm", 1581L, "The Deadmines"), new ProblemCollector());
    assertThat(all).extracting(AtlasInstance::key).containsExactly("Ragefire", "TheDeadmines", "MoltenCore");
    AtlasInstance rfc = all.get(0);
    assertThat(rfc.name()).isEqualTo("Ragefire Chasm"); assertThat(rfc.levelMin()).isEqualTo(10); assertThat(rfc.levelMax()).isEqualTo(13); assertThat(rfc.contentType()).isEqualTo("DUNGEON");
    AtlasBoss tara = rfc.bosses().get(0);
    assertThat(tara.name()).isEqualTo("Taragaman the Hungerer"); assertThat(tara.npcId()).isEqualTo(11520L);
    assertThat(tara.loot()).containsExactly(new AtlasLoot("NORMAL", 1, 14149), new AtlasLoot("NORMAL", 2, 14148), new AtlasLoot("NORMAL", 3, 14145));
  }
  @Test void skipsHeaderRowsSetTablesAndKeepsExtraLists() {
    AtlasInstance dm = AtlasLootLoader.load(Fixtures.atlasLootRoot(), Map.of(), new ProblemCollector()).get(1);
    assertThat(dm.name()).isEqualTo("The Deadmines");
    assertThat(dm.bosses()).noneMatch(b -> b.name().equals("Keys"));                      // KEYS local has IgnoreAsSource
    assertThat(dm.bosses()).anyMatch(b -> b.extraList() && b.name().equals("Trash Mobs"));
    assertThat(dm.bosses().stream().flatMap(b -> b.loot().stream())).allMatch(l -> l.itemId() > 0);
  }
  @Test void moltenCoreIsRaid40() { assertThat(AtlasLootLoader.load(Fixtures.atlasLootRoot(), Map.of(), new ProblemCollector()).get(2).contentType()).isEqualTo("RAID40"); }
}
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl importer test`. Expected: compilation failure.

- [ ] **Step 3: Implement the AtlasLoot scope and loader**

- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q -pl importer test`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add importer && git commit -m "feat(importer): AtlasLoot instance, boss and loot extraction"
```

---

### Task 11: SQLite schema, writer, pipeline and CLI

**Files:**
- Create: `importer/src/main/resources/dev/wowforever/importer/schema.sql`
- Create: `importer/src/main/java/dev/wowforever/importer/sqlite/ItemClasses.java`, `SqliteWriter.java`
- Create: `importer/src/main/java/dev/wowforever/importer/ImportPipeline.java`, `ImportOptions.java`, `Main.java`
- Modify: `importer/pom.xml` (shade plugin → `importer/target/importer.jar` with `Main-Class: dev.wowforever.importer.Main`; `exec-maven-plugin` in profile `import-data` running `Main` with `--out ../app/src/main/resources/data/wowforever.sqlite`)
- Test: `importer/src/test/java/dev/wowforever/importer/sqlite/SqliteWriterTest.java`, `ImportPipelineTest.java`

**Interfaces:**
- `schema.sql`: exactly the spec §6 tables, with these column types and indexes: `item_npc_drop(item_id INTEGER, npc_id INTEGER, drop_pct REAL, pct_source TEXT, origin TEXT NOT NULL, PRIMARY KEY(item_id, npc_id))`, index `idx_item_npc_drop_npc ON item_npc_drop(npc_id)`; `idx_item_object_drop_obj`, `idx_item_container_drop_container`, `idx_item_vendor_npc`, `idx_quest_reward_item_item`, `idx_item_related_quest_quest`, `idx_npc_spawn_npc`, `idx_npc_spawn_zone`, `idx_object_spawn_object`, `idx_object_spawn_zone`, `idx_npc_zone ON npc(zone_id)`, `idx_object_zone`, `idx_quest_zone ON quest(zone_or_sort)`, `idx_quest_giver_entity ON quest_giver(kind, entity_id)`, `idx_boss_instance`, `idx_boss_loot_item ON boss_loot(item_id)`, `idx_item_class ON item(class_id, subclass_id)`; `CREATE VIRTUAL TABLE search_index USING fts5(kind UNINDEXED, id UNINDEXED, name, tokenize = 'unicode61 remove_diacritics 2')`.
- `ItemClasses`: `static Map<Integer, String> classes()` = 0 Consumable, 1 Container, 2 Weapon, 3 Gem, 4 Armor, 5 Reagent, 6 Projectile, 7 Trade Goods, 8 Generic, 9 Recipe, 10 Money, 11 Quiver, 12 Quest, 13 Key, 14 Permanent, 15 Miscellaneous; `static Map<Integer, Map<Integer, String>> subclasses()` = Weapon {0 One-Handed Axe, 1 Two-Handed Axe, 2 Bow, 3 Gun, 4 One-Handed Mace, 5 Two-Handed Mace, 6 Polearm, 7 One-Handed Sword, 8 Two-Handed Sword, 10 Staff, 13 Fist Weapon, 14 Miscellaneous, 15 Dagger, 16 Thrown, 17 Spear, 18 Crossbow, 19 Wand, 20 Fishing Pole}; Armor {0 Miscellaneous, 1 Cloth, 2 Leather, 3 Mail, 4 Plate, 6 Shield, 7 Libram, 8 Idol, 9 Totem}; Consumable {0 Consumable, 1 Potion, 2 Elixir, 3 Flask, 4 Scroll, 5 Food & Drink, 6 Item Enhancement, 7 Bandage, 8 Other}; Container {0 Bag, 1 Soul Bag, 2 Herb Bag, 3 Enchanting Bag, 4 Engineering Bag}; Projectile {2 Arrow, 3 Bullet}; Trade Goods {0 Trade Goods, 1 Parts, 2 Explosives, 3 Devices}; Recipe {0 Book, 1 Leatherworking, 2 Tailoring, 3 Engineering, 4 Blacksmithing, 5 Cooking, 6 Alchemy, 7 First Aid, 8 Enchanting, 9 Fishing}; Quiver {2 Quiver, 3 Ammo Pouch}; Miscellaneous {0 Junk, 1 Reagent, 2 Pet, 3 Holiday, 4 Other, 5 Mount}; Key {0 Key, 1 Lockpick}; Reagent {0 Reagent}; Quest {0 Quest}; Money {0 Money}; Gem {0 Gem}.
- `record ImportOptions(Path out, Path cacheDir, Optional<Path> questieDbDir, Optional<Path> atlasLootDir, boolean failOnUnsupported)` with `static ImportOptions parse(String[] args)` (`--out`, `--cache`, `--questiedb-dir`, `--atlasloot-dir`, `--fail-on-unsupported`; `--out` required).
- `final class SqliteWriter`: `static void write(Path out, ImportModel model)` where `record ImportModel(Map<EntityKind, EntityTable> tables, DropRates dropRates, Map<Long, Zone> zones, List<AtlasInstance> instances, Map<String, String> meta)`. Deletes any existing file, creates the schema, inserts everything in one transaction with batches of 5,000, then `INSERT INTO search_index`, `ANALYZE`, `VACUUM`. Mapping rules: entity scalar fields → columns (`LuaNil` → NULL, `LuaUnresolved` → NULL + counted); `objectivesText` → lines joined with `\n`; `startedBy`/`finishedBy` → `quest_giver` rows (groups 1/2/3 → `npc`/`object`/`item`, role `start`/`end`); `npcDrops` → `item_npc_drop(origin='questie')`; AtlasLoot boss loot with `npcId` → upsert into `item_npc_drop` (`origin` becomes `both` when the row existed) and always into `boss_loot`; `drop_pct`/`pct_source` from `DropRates` for every `item_npc_drop` and `boss_loot` row; `spawns` → one row per coordinate (phase ignored); `instance.zone_id` = `map_id` when a zone with that id exists; `boss.id` and `instance.id` are sequential from 1 in input order; `search_index` rows for item/npc/game_object/quest/zone/instance names.
- `ImportPipeline`: `static ImportReport run(ImportOptions opts)` — fetch, schemas, raw load, enums, scope, corrections, drop rates, zones (raid area ids = AtlasLoot instances with content type `RAID20`/`RAID40` → their `MapID`s), AtlasLoot (area names from zones), `meta` (`build_id` = SHA-256 hex of `questiedb_commit + atlasloot_commit + importer_version + schema.sql` contents, `built_at` ISO-8601 UTC, `questiedb_commit`, `atlasloot_commit`, `importer_version` = `0.1.0`), write, then `report.count("rows.<table>", n)` for every table, write `import-report.txt` beside `out`, print `report.render()`. `Main.main` exits 0, or 2 when `failOnUnsupported && report.skipsOnUiFields() > 0`, or 1 on exception.

- [ ] **Step 1: Write the failing tests**

```java
class SqliteWriterTest {
  @Test void writesEntityWithNullFields(@TempDir Path tmp) throws Exception {
    ImportModel m = minimalModelWithItem(190179, "Avelina's Heart");      // only name + npcDrops set
    SqliteWriter.write(tmp.resolve("t.sqlite"), m);
    try (Connection c = open(tmp.resolve("t.sqlite"))) {
      ResultSet rs = c.createStatement().executeQuery("select name, item_level, class_id from item where id = 190179");
      assertThat(rs.next()).isTrue(); assertThat(rs.getString(1)).isEqualTo("Avelina's Heart"); assertThat(rs.getObject(2)).isNull();
    }
  }
  @Test void mergesAtlasLootIntoNpcDropsWithOrigin(@TempDir Path tmp) throws Exception { /* item 14149 with questie npcDrops {11520} + atlas boss 11520 loot 14149 and 14148 → origin both / atlasloot; boss_loot has 2 rows */ }
  @Test void ftsIndexFindsPrefix(@TempDir Path tmp) throws Exception { /* query: select id from search_index where search_index match '"subterr"*' → 14149 */ }
}
class ImportPipelineTest {
  @Test void endToEndOnFixtures(@TempDir Path tmp) throws Exception {
    ImportOptions o = new ImportOptions(tmp.resolve("out.sqlite"), tmp.resolve("cache"), Optional.of(Fixtures.questieDbRoot()), Optional.of(Fixtures.atlasLootRoot()), true);
    ImportReport r = ImportPipeline.run(o);
    try (Connection c = open(o.out())) {
      assertThat(scalar(c, "select count(*) from instance")).isEqualTo(3);
      assertThat(scalar(c, "select drop_pct from item_npc_drop where item_id = 2874 and npc_id = 639")).isEqualTo(60.0);
      assertThat(scalar(c, "select origin from item_npc_drop where item_id = 14149 and npc_id = 11520")).isEqualTo("both");
      assertThat(scalar(c, "select count(*) from boss_loot bl join boss b on b.id = bl.boss_id where b.npc_id = 11520")).isEqualTo(3);
      assertThat(scalar(c, "select name from zone where id = 1581")).isEqualTo("The Deadmines");
      assertThat(scalar(c, "select value from meta where key = 'questiedb_commit'")).isEqualTo("49e7dbca1f18eb3b8e57ee5d9562ac3e5c9adcc4");
      assertThat(scalar(c, "select count(*) from quest_giver where quest_id = 2 and role = 'start'")).isEqualTo(1);
    }
    assertThat(r.skipsOnUiFields()).isZero(); assertThat(tmp.resolve("import-report.txt")).exists();
  }
}
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl importer test`. Expected: compilation failure.

- [ ] **Step 3: Implement `schema.sql`, `ItemClasses`, `SqliteWriter`, `ImportOptions`, `ImportPipeline`, `Main`, and the shade/exec plugin config**

- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q -pl importer verify`. Expected: PASS; `importer/target/importer.jar` exists.

- [ ] **Step 5: Commit**

```bash
git add importer && git commit -m "feat(importer): SQLite schema, writer, pipeline and CLI"
```

---

### Task 12: Build and commit the real database

**Files:**
- Create: `app/src/main/resources/data/wowforever.sqlite` (generated), `app/src/main/resources/data/import-report.txt` (copied)
- Modify: `README.md` (data rebuild section), `importer/.../zones/ZoneNames.java` (override additions), possibly `TableOps`/`CorrectionApplier` if the report shows UI-field skips

- [ ] **Step 1: Run the real import against the cache**

Run: `java -jar importer/target/importer.jar --out app/src/main/resources/data/wowforever.sqlite --fail-on-unsupported` (the cache at `~/.cache/wowforever-importer` already holds every file; the fetcher downloads any that are missing).
Expected: exit code 0 and a report with `rows.item` > 15,000, `rows.npc` > 8,000, `rows.instance` = 33, `rows.item_npc_drop` > 50,000, and zero skips on UI fields. If it exits 2, read the grouped skips in `import-report.txt`, extend the supported shapes or scope bindings for those fields (tests first, in Task 7/8 files), and rerun. Skips on non-UI quest fields (`objectives`, `extraObjectives`, `preQuestGroup` …) are acceptable.

- [ ] **Step 2: Sanity-query the result**

Run (sqlite via a short Java/jshell or `python -I -c "import sqlite3..."`): `select count(*) from item where name like 'Zone #%'` → 0; `select name from zone where name like 'Zone #%' limit 20` → review and add `ZoneNames` overrides for any top-level zone that still shows a symbol-derived oddity (`Atal Hakkar`, `Ahn Qiraj` …); `select n.name, d.drop_pct from item_npc_drop d join npc n on n.id = d.npc_id where d.item_id = 19019` → includes Ragnaros (11502) with `origin` `atlasloot` or `both`; `select count(*) from item_npc_drop d left join npc n on n.id = d.npc_id where n.id is null` → record the number in the README as "unknown-NPC references".

- [ ] **Step 3: Verify size and commit**

`ls -la app/src/main/resources/data/` → expected under 80 MB. Copy `import-report.txt` next to it. Write `app/src/main/resources/data/build-id.txt` containing exactly the value of `select value from meta where key = 'build_id'` (no newline); the importer should do this itself: add `--build-id-out <path>` to `ImportOptions` (default: `<out dir>/build-id.txt`) so a rebuild keeps both in sync. Update README "Rebuilding the data" with the exact command and the three row counts.

```bash
git add app/src/main/resources/data README.md importer && git commit -m "data: import QuestieDB 49e7dbc + AtlasLootClassic 8e99341 into wowforever.sqlite"
```

---

### Task 13: App database access and repositories

**Files:**
- Create: `app/src/main/java/dev/wowforever/app/db/UserDataDir.java`, `DatabaseInstaller.java`, `Database.java`, `Sql.java`
- Create: `app/src/main/java/dev/wowforever/app/model/EntityKind.java`, `EntityRef.java`, `SearchHit.java`, `ItemSummary.java`, `ItemDetail.java`, `DropSource.java`, `ItemSource.java`, `NpcSummary.java`, `NpcDetail.java`, `Spawn.java`, `ObjectSummary.java`, `ObjectDetail.java`, `QuestSummary.java`, `QuestDetail.java`, `QuestGiver.java`, `ZoneSummary.java`, `ZoneDetail.java`, `InstanceSummary.java`, `InstanceDetail.java`, `BossSummary.java`, `LootRow.java`, `ItemClassNode.java`
- Create: `app/src/main/java/dev/wowforever/app/db/SearchRepository.java`, `ItemRepository.java`, `NpcRepository.java`, `ObjectRepository.java`, `QuestRepository.java`, `ZoneRepository.java`, `InstanceRepository.java`, `Repositories.java`
- Test: `app/src/test/java/dev/wowforever/app/db/FixtureDb.java` (JUnit extension building the fixture DB once via `ImportPipeline` + `Fixtures` from the importer test-jar), `DatabaseInstallerTest.java`, `SearchRepositoryTest.java`, `ItemRepositoryTest.java`, `NpcRepositoryTest.java`, `InstanceRepositoryTest.java`, `ZoneRepositoryTest.java`, `QuestRepositoryTest.java`

**Interfaces:**
- `UserDataDir.resolve()`: Windows `%LOCALAPPDATA%\WoWForeverBrowser`, macOS `~/Library/Application Support/WoWForeverBrowser`, else `$XDG_DATA_HOME/wowforever-browser` or `~/.local/share/wowforever-browser`; `UserDataDir.resolve(Map<String,String> env, String osName, Path home)` for tests.
- `DatabaseInstaller(Path dataDir)`: `Path install()` — reads `data/wowforever.sqlite` from the classpath; target `dataDir/wowforever.sqlite`; extracts when missing or when `meta.build_id` of the existing file differs from the embedded one (the embedded build id is the classpath resource `data/build-id.txt` produced in Task 12); extraction goes to a temp file then atomic move. `Path install(InputStream embedded, String embeddedBuildId)` for tests.
- `Database`: `static Database open(Path file)` (JDBC URL `jdbc:sqlite:<file>`, `SQLiteConfig.setReadOnly(true)`, `setOpenMode(READONLY)`), `Connection connection()`, `Map<String,String> meta()`, `void close()`. Single connection, used from worker threads sequentially (synchronize repository calls on the `Database`).
- `Sql`: `static <T> List<T> list(Connection c, String sql, RowMapper<T> m, Object... params)`, `static <T> Optional<T> one(...)`, `interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }`.
- `enum EntityKind { ITEM, NPC, OBJECT, QUEST, ZONE, INSTANCE, BOSS }`; `record EntityRef(EntityKind kind, long id)`.
- `record SearchHit(EntityRef ref, String name, String subtitle)`; `record ItemSummary(long id, String name, Integer itemLevel, Integer requiredLevel, String className, String subclassName, int sourceCount)`; `record DropSource(EntityRef ref, String name, Integer levelMin, Integer levelMax, String zoneName, Long zoneId, Double dropPct, String pctSource, String origin)` (name null when unknown → model method `displayName()` returns `"Unknown NPC #id"` per kind); `record ItemSource(String kind, EntityRef ref, String name)` for objects/containers/vendors/quests; `record ItemDetail(ItemSummary summary, Integer classId, Integer subclassId, Long startQuestId, List<DropSource> droppedBy, List<ItemSource> containedInObjects, List<ItemSource> containedInItems, List<ItemSource> soldBy, List<ItemSource> rewardFrom, List<ItemSource> relatedQuests)`; `record NpcSummary(long id, String name, String subName, Integer minLevel, Integer maxLevel, Integer rank, String zoneName, Long zoneId)`; `record Spawn(long zoneId, String zoneName, double x, double y)`; `record LootRow(EntityRef item, String itemName, Integer itemLevel, Double dropPct, String origin, Integer slot)`; `record NpcDetail(NpcSummary summary, int npcFlags, String friendlyTo, List<LootRow> drops, List<LootRow> sells, List<QuestSummary> starts, List<QuestSummary> ends, List<Spawn> spawns)`; `ObjectSummary/ObjectDetail` analogous (contains, starts, ends, spawns); `record QuestGiver(EntityRef ref, String name, String role)`; `record QuestSummary(long id, String name, Integer questLevel, Integer requiredLevel, String zoneName)`; `record QuestDetail(QuestSummary summary, String objectivesText, List<QuestGiver> givers, List<LootRow> rewards, List<ItemSource> relatedItems)`; `record ZoneSummary(long id, String name, String kind, Long parentId)`; `record ZoneDetail(ZoneSummary summary, List<NpcSummary> npcs, List<ObjectSummary> objects, List<QuestSummary> quests, List<InstanceSummary> instances, List<ZoneSummary> subzones)`; `record InstanceSummary(long id, String name, String contentType, Integer levelMin, Integer levelMax)`; `record BossSummary(long id, String name, Long npcId, Integer level, String specialType, boolean extraList)`; `record InstanceDetail(InstanceSummary summary, List<BossSummary> bosses)`; `record ItemClassNode(int classId, String name, List<ItemClassNode> children, int subclassId)` (subclassId −1 for class nodes).
- Repositories (all take `Database`): `SearchRepository.search(String text, Optional<EntityKind> kind, int limit) → List<SearchHit>` (FTS query built as `"<escaped>"*` per whitespace-separated term, joined with spaces; double quotes inside terms are doubled; empty/blank text → empty list; ordered by `bm25(search_index)` then name); `ItemRepository.detail(long) → Optional<ItemDetail>`, `list(Integer classId, Integer subclassId, int limit, int offset) → List<ItemSummary>`, `classTree() → List<ItemClassNode>`; `NpcRepository.detail(long) → Optional<NpcDetail>`, `byZone(long zoneId) → List<NpcSummary>`; `ObjectRepository.detail`, `byZone`; `QuestRepository.detail`, `byZone`; `ZoneRepository.all() → List<ZoneSummary>` (top-level first, name order), `detail(long)` where `instances` are the instances whose `zone_id` is this zone or a zone whose `parent_id` is this zone, `subzones` are zones with `parent_id` = this zone, and npcs/objects/quests match on their own `zone_id`/`zone_or_sort`; `InstanceRepository.all()`, `detail(long)`, `bossLoot(long bossId) → List<LootRow>`; `record Repositories(SearchRepository search, ItemRepository items, NpcRepository npcs, ObjectRepository objects, QuestRepository quests, ZoneRepository zones, InstanceRepository instances)` with `static Repositories of(Database db)`.

- [ ] **Step 1: Write the failing tests**

```java
class DatabaseInstallerTest {
  @Test void extractsWhenMissing(@TempDir Path dir) throws Exception { Path p = new DatabaseInstaller(dir).install(stream(FixtureDb.path()), "b1"); assertThat(p).exists(); assertThat(Database.open(p).meta()).containsKey("build_id"); }
  @Test void replacesWhenBuildIdDiffers(@TempDir Path dir) throws Exception {
    Files.write(dir.resolve("wowforever.sqlite"), new byte[] {1, 2, 3});   // garbage "old" db: unreadable build id counts as different
    Path p = new DatabaseInstaller(dir).install(stream(FixtureDb.path()), "b1"); assertThat(Files.size(p)).isGreaterThan(1000);
  }
  @Test void keepsWhenBuildIdMatches(@TempDir Path dir) throws Exception { /* install once, record mtime, install again with same id → mtime unchanged */ }
}
class SearchRepositoryTest {
  @Test void prefixSearchFindsItem() { assertThat(repos.search().search("subterr", Optional.empty(), 10)).extracting(h -> h.ref()).contains(new EntityRef(ITEM, 14149)); }
  @Test void specialCharactersDoNotThrow() { for (String q : List.of("\"", "'", "*", "-", "OR", "(", "a\"b", "   ")) assertThatCode(() -> repos.search().search(q, Optional.empty(), 10)).doesNotThrowAnyException(); }
  @Test void kindFilter() { assertThat(repos.search().search("Taragaman", Optional.of(NPC), 10)).allMatch(h -> h.ref().kind() == NPC); }
}
class ItemRepositoryTest {
  @Test void detailListsAllSources() {
    ItemDetail d = repos.items().detail(14149).get();
    assertThat(d.droppedBy()).extracting(s -> s.ref().id()).contains(11520L);
    assertThat(d.droppedBy().get(0).zoneName()).isNotNull();
  }
  @Test void unknownNpcInDropsKeepsRow() { ItemDetail d = repos.items().detail(190179).get(); assertThat(d.droppedBy()).singleElement().satisfies(s -> { assertThat(s.name()).isNull(); assertThat(s.displayName()).isEqualTo("Unknown NPC #185333"); }); }
  @Test void classTreeHasWeaponsAndArmor() { assertThat(repos.items().classTree()).extracting(ItemClassNode::name).contains("Weapon", "Armor"); }
}
class NpcRepositoryTest { @Test void detailHasDropsWithPctAndSpawns() { NpcDetail d = repos.npcs().detail(639).get(); assertThat(d.drops()).anyMatch(l -> l.item().id() == 2874 && l.dropPct() == 60.0); assertThat(repos.npcs().detail(448).get().spawns()).isNotEmpty(); } }
class InstanceRepositoryTest { @Test void bossLootInSlotOrder() { InstanceDetail rfc = repos.instances().detail(1).get(); assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).isSorted(); } }
class ZoneRepositoryTest { @Test void zoneDetailListsNpcsAndInstances() { ZoneDetail z = repos.zones().detail(40).get(); assertThat(z.instances()).extracting(i -> i.name()).contains("The Deadmines"); } }
class QuestRepositoryTest { @Test void questDetailHasGivers() { assertThat(repos.quests().detail(2).get().givers()).isNotEmpty(); } }
```
`FixtureDb` builds the DB once into a static temp path by calling `ImportPipeline.run` with `Fixtures.roots()`; `repos` = `Repositories.of(Database.open(FixtureDb.path()))`.

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl app test`. Expected: compilation failure.

- [ ] **Step 3: Implement the db and model packages**

- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q install -DskipTests -pl lua-table,importer && ./mvnw -B -q -pl app test`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app && git commit -m "feat(app): read-only database access, installer and repositories"
```

---

### Task 14: Navigation history and view targets

**Files:**
- Create: `app/src/main/java/dev/wowforever/app/nav/ViewTarget.java`, `NavigationHistory.java`, `Navigator.java`
- Test: `app/src/test/java/dev/wowforever/app/nav/NavigationHistoryTest.java`

**Interfaces:**
- `sealed interface ViewTarget` with records `Entity(EntityRef ref)`, `Search(String text, Optional<EntityKind> kind)`, `ItemClass(int classId, int subclassId)`, `List(EntityKind kind)` (all zones / all instances), `Home()`.
- `final class NavigationHistory`: `void push(ViewTarget t)` (clears forward stack; ignores a push equal to current), `Optional<ViewTarget> back()`, `Optional<ViewTarget> forward()`, `boolean canBack()`, `boolean canForward()`, `Optional<ViewTarget> current()`.
- `interface Navigator { void open(ViewTarget t); }`.

- [ ] **Step 1: Write the failing tests**

```java
class NavigationHistoryTest {
  @Test void backAndForward() { var h = new NavigationHistory(); h.push(new Home()); h.push(new Entity(new EntityRef(ITEM, 1))); assertThat(h.back()).contains(new Home()); assertThat(h.canForward()).isTrue(); assertThat(h.forward()).contains(new Entity(new EntityRef(ITEM, 1))); }
  @Test void pushClearsForward() { var h = new NavigationHistory(); h.push(new Home()); h.push(new List(ZONE)); h.back(); h.push(new List(INSTANCE)); assertThat(h.canForward()).isFalse(); }
  @Test void duplicatePushIgnored() { var h = new NavigationHistory(); h.push(new Home()); h.push(new Home()); assertThat(h.canBack()).isFalse(); }
  @Test void backAtStartIsEmpty() { assertThat(new NavigationHistory().back()).isEmpty(); }
}
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl app test`. Expected: compilation failure.
- [ ] **Step 3: Implement** the three types.
- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q -pl app test`. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add app && git commit -m "feat(app): navigation history and view targets"
```

---

### Task 15: Application shell: main window, search, browse tree, results table

**Files:**
- Create: `app/src/main/java/dev/wowforever/app/Main.java`, `ui/AppController.java`, `ui/MainFrame.java`, `ui/SearchBar.java`, `ui/BrowseTree.java`, `ui/ResultsPanel.java`, `ui/ResultsTableModel.java`, `ui/Columns.java`, `ui/LinkRenderer.java`, `ui/ThemeManager.java`, `ui/Async.java`, `ui/StatusBar.java`
- Modify: `app/pom.xml` (shade → `app/target/wow-forever-browser.jar`, `Main-Class: dev.wowforever.app.Main`; filter out signature files)
- Test: `app/src/test/java/dev/wowforever/app/ui/ResultsTableModelTest.java`, `SearchBarTest.java`, `BrowseTreeTest.java`, `AppControllerTest.java`, `MainSelfTestTest.java`

**Interfaces:**
- `Main.main(String[] args)`: `--self-test` → install DB, open, print `items=<n> npcs=<n> instances=<n> build_id=<id>` and exit 0 (used by CI/packaging smoke); otherwise set FlatLaf via `ThemeManager.applySaved()`, run `DatabaseInstaller`+`Database.open` on a background thread while a `MainFrame` shows "Loading database…" in the status bar, then `AppController.start()`.
- `ThemeManager`: `static void applySaved()` (Preferences key `theme` = `light`|`dark`, default light → `FlatLightLaf`/`FlatDarkLaf`), `static void toggle(Component root)`, `static boolean isDark()`.
- `Async`: `static <T> void run(Supplier<T> work, Consumer<T> onEdt, Consumer<Exception> onError)` using `SwingWorker`.
- `AppController(Repositories repos, MainFrame frame)` implements `Navigator`: `open(ViewTarget)` pushes history and dispatches: `Search` → `repos.search()` into `ResultsPanel`; `ItemClass` → `items().list(...)`; `List(ZONE|INSTANCE)` → zones/instances table; `Entity` → detail panel (Task 16); `Home` → welcome text. `back()`/`forward()` re-open without pushing. Exposes `NavigationHistory history()` and `ViewTarget current()` for tests; all repository calls go through `Async`, with a seam `AppController(Repositories, MainFrame, Executor)` so tests can pass `Runnable::run` and assert synchronously.
- `ResultsTableModel<T>` extends `AbstractTableModel`: `ResultsTableModel(List<Columns.Column<T>> columns)`, `void setRows(List<T>)`, `T rowAt(int)`, `Optional<EntityRef> refAt(int row, int col)` (link cells). `Columns`: static column lists `items()`, `npcs()`, `objects()`, `quests()`, `zones()`, `instances()`, `searchHits()`, each `record Column<T>(String title, Class<?> type, Function<T, Object> value, Function<T, Optional<EntityRef>> link)`; item columns: Name (link), Item level, Required level, Class, Subclass, Sources.
- `SearchBar`: `JTextField` + kind `JComboBox` (`All`, `Items`, `NPCs`, `Objects`, `Quests`, `Zones`, `Dungeons & Raids`), debounce 250 ms, calls `Consumer<ViewTarget.Search> onSearch` on Enter or after the debounce; `void focus()`, `JTextField textField()`, `JComboBox<String> kindCombo()`. Package-private `void fire()` for tests (bypasses the timer).
- `BrowseTree`: `JTree` with root children `Items` (class → subclass nodes from `ItemRepository.classTree()`), `NPCs by zone`, `Objects by zone`, `Quests by zone` (zone nodes), `Zones`, `Dungeons & Raids` (instance nodes); selection → `Navigator.open(...)`: class node → `ItemClass`, zone node under NPCs/Objects/Quests → `Entity(ZONE)` (the zone detail lists them), `Zones` root → `List(ZONE)`, instance node → `Entity(INSTANCE)`. `BrowseTree(Repositories, Navigator)`; `TreeModel model()`, `List<String> instanceNodeNames()`, `void selectInstance(String name)` for tests.
- `ResultsPanel`: `JTable` on a `ResultsTableModel`, sortable (`TableRowSorter`), double-click/Enter on a row → `Navigator.open(Entity(ref))` using the Name column's link; `void show(String title, ResultsTableModel<?> model)`, `ResultsTableModel<?> model()`; `JLabel` title shows `"<title> (<n>)"`.
- `MainFrame(Navigator nav)`: `BorderLayout`; north: back/forward buttons + `SearchBar`; a `JSplitPane` (left `BrowseTree` in a scroll pane, right another split: `ResultsPanel` and detail area); south `StatusBar`; menu `View → Dark theme` (checkbox) and `Help → About` (shows `NOTICE.md` text from the classpath); keyboard `Ctrl+F` → `searchBar.focus()`, `Alt+Left`/`Alt+Right` → back/forward. `void setDetail(JComponent)`, `ResultsPanel results()`, `SearchBar searchBar()`, `StatusBar status()`, `void setHistoryState(boolean canBack, boolean canForward)`. Constructed as a `JFrame` in production; for tests the content is built by `MainFrame.buildContent(...)` returning a `JPanel`, and `MainFrame` is never instantiated in tests.
- `app/src/main/resources/NOTICE.md` is a copy of the root `NOTICE.md` (add a `maven-resources-plugin` copy step or commit a copy).

- [ ] **Step 1: Write the failing tests**

```java
class ResultsTableModelTest {
  @Test void itemColumnsAndLinks() {
    var m = new ResultsTableModel<>(Columns.items()); m.setRows(List.of(new ItemSummary(14149, "Subterranean Cape", 19, 14, "Armor", "Cloth", 1)));
    assertThat(m.getColumnCount()).isEqualTo(6); assertThat(m.getValueAt(0, 0)).isEqualTo("Subterranean Cape"); assertThat(m.refAt(0, 0)).contains(new EntityRef(ITEM, 14149)); assertThat(m.refAt(0, 1)).isEmpty();
  }
}
class SearchBarTest {
  @Test void fireSendsTextAndKind() { AtomicReference<ViewTarget.Search> got = new AtomicReference<>(); SearchBar b = new SearchBar(got::set); b.textField().setText("hogger"); b.kindCombo().setSelectedIndex(2); b.fire(); assertThat(got.get()).isEqualTo(new ViewTarget.Search("hogger", Optional.of(NPC))); }
  @Test void blankTextDoesNotFire() { AtomicInteger n = new AtomicInteger(); SearchBar b = new SearchBar(t -> n.incrementAndGet()); b.textField().setText("  "); b.fire(); assertThat(n).hasValue(0); }
}
class BrowseTreeTest {
  @Test void treeHasTopLevelSectionsAndInstances() { BrowseTree t = new BrowseTree(repos, target -> {}); TreeModel m = t.model(); assertThat(m.getChildCount(m.getRoot())).isEqualTo(6); assertThat(t.instanceNodeNames()).contains("Ragefire Chasm", "The Deadmines"); }
  @Test void selectingInstanceOpensEntity() { List<ViewTarget> opened = new ArrayList<>(); BrowseTree t = new BrowseTree(repos, opened::add); t.selectInstance("Ragefire Chasm"); assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(INSTANCE, 1))); }
}
class AppControllerTest {
  @Test void searchOpensResultsAndPushesHistory() {
    AppController c = new AppController(repos, frameStub(), Runnable::run);
    c.open(new ViewTarget.Search("subterr", Optional.empty()));
    assertThat(c.current()).isEqualTo(new ViewTarget.Search("subterr", Optional.empty())); assertThat(frameStub().results().model().getRowCount()).isEqualTo(1);
  }
  @Test void backRestoresPreviousTarget() { AppController c = new AppController(repos, frameStub(), Runnable::run); c.open(new ViewTarget.List(ZONE)); c.open(new ViewTarget.List(INSTANCE)); c.back(); assertThat(c.current()).isEqualTo(new ViewTarget.List(ZONE)); }
}
class MainSelfTestTest {
  @Test void selfTestPrintsCounts() { String out = captureStdout(() -> Main.selfTest(FixtureDb.path())); assertThat(out).contains("items=").contains("instances=3"); }
}
```
`frameStub()` = a `MainFrame`-like test double: introduce `interface MainView { ResultsPanel results(); void setDetail(JComponent c); void setHistoryState(boolean, boolean); StatusBar status(); }` implemented by `MainFrame` and by a headless `JPanel`-based stub in tests; `AppController` depends on `MainView`.

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl app test`. Expected: compilation failure.

- [ ] **Step 3: Implement the shell**

Keep each class under ~250 lines. `Main.selfTest(Path db)` is the testable core of `--self-test`.

- [ ] **Step 4: Run tests and build the fat JAR** — Run: `./mvnw -B -q -pl app verify`. Expected: PASS; then `java -jar app/target/wow-forever-browser.jar --self-test` prints `items=` with the real counts and exits 0.

- [ ] **Step 5: Launch the real UI once** — Run: `java -jar app/target/wow-forever-browser.jar` (foreground, on this Windows machine). Expected: window opens, typing `hogger` lists Hogger, the tree shows 33 instances, dark theme toggles. Close it. Fix anything broken before committing.

- [ ] **Step 6: Commit**

```bash
git add app && git commit -m "feat(app): main window, search, browse tree and results table"
```

---

### Task 16: Detail panels with link navigation

**Files:**
- Create: `app/src/main/java/dev/wowforever/app/ui/detail/DetailPanels.java`, `LinkTable.java`, `HeaderPanel.java`, `ItemDetailPanel.java`, `NpcDetailPanel.java`, `ObjectDetailPanel.java`, `QuestDetailPanel.java`, `ZoneDetailPanel.java`, `InstanceDetailPanel.java`, `BossLootPanel.java`
- Modify: `ui/AppController.java` (`Entity` targets → `DetailPanels.build(ref, repos, navigator)` into `MainView.setDetail`)
- Test: `app/src/test/java/dev/wowforever/app/ui/detail/ItemDetailPanelTest.java`, `NpcDetailPanelTest.java`, `InstanceDetailPanelTest.java`, `ZoneDetailPanelTest.java`, `LinkTableTest.java`

**Interfaces:**
- `LinkTable<T>`: `JPanel` wrapping a `JTable` on `ResultsTableModel<T>`; `LinkTable(List<Columns.Column<T>> cols, Navigator nav)`, `void setRows(List<T>)`, `int rowCount()`, `JTable table()`, `void activateRow(int)` (what double-click/Enter does: opens the first link column's ref). Columns for drop sources: `Columns.dropSources()` = Source (link, `displayName()`), Level, Zone (link to zone), Drop % (formatted `56.07 %`, `—` when null), Origin.
- `HeaderPanel(String title, String subtitle)` with `String title()`, `String subtitle()`.
- `DetailPanels.build(EntityRef ref, Repositories repos, Navigator nav) → JComponent` (runs the repository call; the caller runs it off the EDT via `Async` and only constructs the panel on the EDT: split into `DetailPanels.load(ref, repos) → Object detail` and `DetailPanels.render(Object detail, Navigator nav) → JComponent`).
- `ItemDetailPanel(ItemDetail d, Navigator nav)`: header `"<name>"` / `"Item #id · <class> · <subclass> · iLvl n · Requires n"`; `JTabbedPane` tabs with counts in titles: `Dropped by (n)`, `Contained in (n)` (objects + container items), `Sold by (n)`, `Reward from (n)`, `Related quests (n)`; tabs with 0 rows still appear but are disabled. Accessor `JTabbedPane tabs()` and `LinkTable<DropSource> droppedBy()`.
- `NpcDetailPanel(NpcDetail d, Navigator nav)`: header `"<name> <subName>"` / `"NPC #id · Level a–b · <rank text> · <zone link>"`; tabs `Drops (n)`, `Sells (n)`, `Starts quests (n)`, `Ends quests (n)`, `Spawns (n)` (zone link, x, y). Rank text: 0 Normal, 1 Elite, 2 Rare Elite, 3 Boss, 4 Rare. Accessors `HeaderPanel header()`, `JTabbedPane tabs()`.
- `ObjectDetailPanel`, `QuestDetailPanel` (header + objectives text area + tabs `Given by`, `Turned in to`, `Rewards`, `Related items`), `ZoneDetailPanel` (tabs `NPCs`, `Objects`, `Quests`, `Dungeons & Raids`, `Sub-zones`; accessor `tabs()`), `InstanceDetailPanel(InstanceDetail d, Repositories repos, Navigator nav)` (left list of bosses; selecting one loads loot synchronously through `repos.instances().bossLoot(id)` into a `BossLootPanel`; accessors `void selectBoss(int index)`, `BossLootPanel lootPanel()`), `BossLootPanel(List<LootRow> loot, Navigator nav)` with `int rowCount()`. Every panel exposes `JTabbedPane tabs()` where it has tabs.

- [ ] **Step 1: Write the failing tests**

```java
class LinkTableTest { @Test void activateRowOpensLink() { List<ViewTarget> opened = new ArrayList<>(); var t = new LinkTable<>(Columns.dropSources(), opened::add); t.setRows(List.of(dropSource(11520, "Taragaman the Hungerer"))); t.activateRow(0); assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(NPC, 11520))); } }
class ItemDetailPanelTest {
  @Test void tabsCarryCounts() { var p = new ItemDetailPanel(repos.items().detail(14149).get(), t -> {}); assertThat(p.tabs().getTitleAt(0)).startsWith("Dropped by (").doesNotStartWith("Dropped by (0"); }
  @Test void unknownNpcRendersPlaceholder() { var p = new ItemDetailPanel(repos.items().detail(190179).get(), t -> {}); assertThat(p.droppedBy().table().getValueAt(0, 0)).isEqualTo("Unknown NPC #185333"); }
  @Test void dropPercentFormatted() { var p = new ItemDetailPanel(repos.items().detail(2874).get(), t -> {}); assertThat(p.droppedBy().table().getValueAt(0, 3)).isEqualTo("60.00 %"); }
}
class NpcDetailPanelTest { @Test void headerShowsLevelsAndRank() { var p = new NpcDetailPanel(repos.npcs().detail(639).get(), t -> {}); assertThat(p.header().subtitle()).contains("NPC #639").contains("Level 20"); } }
class InstanceDetailPanelTest { @Test void selectingBossLoadsLoot() { var p = new InstanceDetailPanel(repos.instances().detail(1).get(), repos, t -> {}); p.selectBoss(0); assertThat(p.lootPanel().rowCount()).isEqualTo(3); } }
class ZoneDetailPanelTest { @Test void zoneTabsListInstances() { var p = new ZoneDetailPanel(repos.zones().detail(40).get(), t -> {}); assertThat(p.tabs().getTitleAt(3)).isEqualTo("Dungeons & Raids (1)"); } }
```

- [ ] **Step 2: Run tests to verify they fail** — Run: `./mvnw -B -q -pl app test`. Expected: compilation failure.

- [ ] **Step 3: Implement the detail panels and wire `AppController`**

- [ ] **Step 4: Run tests to verify they pass** — Run: `./mvnw -B -q -pl app verify`. Expected: PASS.

- [ ] **Step 5: Launch the real UI and click through** — Run `java -jar app/target/wow-forever-browser.jar`: search `thunderfury` → open item → Dropped by shows Ragnaros → click Ragnaros → NPC page → Drops tab → click an item → Back twice returns to the search. Open Dungeons & Raids → Molten Core → Lucifron → loot shows. Fix anything broken.

- [ ] **Step 6: Commit**

```bash
git add app && git commit -m "feat(app): entity detail panels with cross-link navigation"
```

---

### Task 17: Shipped-data sanity tests, packaging polish, docs

**Files:**
- Create: `app/src/test/java/dev/wowforever/app/ShippedDataTest.java`
- Modify: `app/pom.xml` (surefire `groups` config so `shipped-data` runs by default but can be excluded with `-DexcludedGroups=shipped-data`), `README.md`, `.github/workflows/ci.yml` (upload `app/target/wow-forever-browser.jar` as an artifact on ubuntu; run `java -jar ... --self-test` on all three OSes)

**Interfaces:** none new.

- [ ] **Step 1: Write the sanity tests**

```java
@Tag("shipped-data")
class ShippedDataTest {
  static Repositories repos;  // open app/src/main/resources/data/wowforever.sqlite via DatabaseInstaller into a temp dir
  @Test void minimumCounts() { assertThat(count("item")).isGreaterThan(15_000); assertThat(count("npc")).isGreaterThan(8_000); assertThat(count("instance")).isEqualTo(33); assertThat(count("item_npc_drop")).isGreaterThan(50_000); assertThat(count("search_index")).isGreaterThan(30_000); }
  @Test void noOrphansInPrimaryLinks() {
    assertThat(scalar("select count(*) from boss_loot bl left join item i on i.id = bl.item_id where i.id is null")).isLessThan(count("boss_loot") / 20);   // AtlasLoot items missing from Questie: tolerate < 5 %
    assertThat(scalar("select count(*) from boss b left join instance i on i.id = b.instance_id where i.id is null")).isZero();
    assertThat(scalar("select count(*) from npc_spawn s left join zone z on z.id = s.zone_id where z.id is null")).isZero();
  }
  @Test void knownFacts() {
    assertThat(repos.items().detail(14149).get().droppedBy()).extracting(s -> s.ref().id()).contains(11520L);
    assertThat(repos.items().detail(19019).get().droppedBy()).extracting(s -> s.ref().id()).contains(11502L);
    assertThat(repos.npcs().detail(639).get().drops()).anyMatch(l -> l.item().id() == 2874 && l.dropPct() != null && l.dropPct() > 50);
    assertThat(repos.search().search("hogger", Optional.empty(), 5)).extracting(h -> h.ref()).contains(new EntityRef(NPC, 448));
    assertThat(repos.zones().detail(1581).get().summary().name()).isEqualTo("The Deadmines");
  }
}
```

- [ ] **Step 2: Run the whole build** — Run: `./mvnw -B verify`. Expected: BUILD SUCCESS, every module's tests green, `app/target/wow-forever-browser.jar` produced.

- [ ] **Step 3: Update README and CI**

README sections: What it is (one paragraph + the §9 caveat that this targets WoW: Forever data from QuestieDB and AtlasLoot), Requirements (Java 21), Run, Build, Rebuild the data (`./mvnw -pl importer -Pimport-data verify` or the `java -jar importer/target/importer.jar --out …` command; cache location; `--fail-on-unsupported`), Tests (`./mvnw verify`, `-DexcludedGroups=shipped-data`), Data sources & licensing (link `NOTICE.md`), Known limitations (no stats/icons, Forever-only content still incomplete upstream, unknown-NPC references count from Task 12). CI: add the self-test step and artifact upload.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "test: shipped-data sanity checks; docs and CI polish"
```

---

### Task 18 (stretch): Native installers with jpackage

**Files:**
- Create: `packaging/jpackage.sh`, `packaging/jpackage.ps1`, `packaging/README.md`
- Modify: `README.md` (Packaging section)

- [ ] **Step 1:** Scripts call `jpackage --input app/target --main-jar wow-forever-browser.jar --main-class dev.wowforever.app.Main --name "WoW Forever Browser" --app-version 0.1.0 --dest packaging/dist --type <msi|dmg|deb>` (type chosen by OS) with `--java-options -Djava.awt.headless=false`. Document that WiX is needed on Windows and Xcode CLT on macOS.
- [ ] **Step 2:** Run the Windows script here. Expected: `packaging/dist/WoW Forever Browser-0.1.0.msi` exists (if WiX is missing, use `--type app-image` and note it).
- [ ] **Step 3:** Commit: `git add packaging README.md && git commit -m "build: jpackage scripts for native installers"`.
