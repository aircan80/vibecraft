package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.lua.ast.Expr;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

  static LuaValue eval(String src, Scope scope) {
    return eval(src, scope, CallHandler.NONE, new ProblemCollector());
  }

  static LuaValue eval(String src, Scope scope, CallHandler calls, ProblemCollector problems) {
    Expr e = new Parser(new Lexer(src, "t.lua").tokenize(), "t.lua").parseExpression();
    return new Evaluator("t.lua", scope, calls, problems).eval(e);
  }

  @Test
  void evaluatesNestedTablesWithHoles() {
    LuaTable t = (LuaTable) eval("{'a', nil, {[12] = {{45, 60}}}, x = -1.5}", new Scope());
    assertThat(t.get(1)).isEqualTo(new LuaString("a"));
    assertThat(t.get(2)).isEqualTo(LuaNil.NIL);
    assertThat(((LuaTable) ((LuaTable) t.get(3)).get(12)).arrayLength()).isEqualTo(1);
    assertThat(t.get("x")).isEqualTo(new LuaNumber(-1.5));
  }

  @Test
  void resolvesDottedNamesFromScope() {
    Scope s = new Scope();
    LuaTable keys = new LuaTable();
    keys.put(new LuaString("npcDrops"), LuaNumber.of(2));
    LuaTable db = new LuaTable();
    db.put(new LuaString("itemKeys"), keys);
    s.define("QuestieDB", db);
    assertThat(eval("QuestieDB.itemKeys.npcDrops", s)).isEqualTo(LuaNumber.of(2));
  }

  @Test
  void unknownNameBecomesUnresolvedAndIsReported() {
    ProblemCollector p = new ProblemCollector();
    LuaTable t = (LuaTable) eval("{[1] = foo.bar}", new Scope(), CallHandler.NONE, p);
    assertThat(t.get(1)).isInstanceOf(LuaUnresolved.class);
    assertThat(p.problems()).hasSize(1);
    assertThat(p.problems().get(0).line()).isEqualTo(1);
  }

  @Test
  void unresolvedKeyDropsEntry() {
    ProblemCollector p = new ProblemCollector();
    LuaTable t = (LuaTable) eval("{[MISSING] = 1, [2] = 2}", new Scope(), CallHandler.NONE, p);
    assertThat(t.entries()).hasSize(1);
    assertThat(p.size()).isEqualTo(1);
  }

  @Test
  void callsGoToHandler() {
    CallHandler h =
        (name, args, line) -> name.equals("GetForVersion") ? Optional.of(args.get(0)) : Optional.empty();
    assertThat(eval("GetForVersion({10,13},{8,13})", new Scope(), h, new ProblemCollector()))
        .isInstanceOf(LuaTable.class);
    assertThat(
            eval(
                "data:AddDifficulty('NORMAL')",
                new Scope(),
                (n, a, l) -> Optional.of(new LuaString(n)),
                new ProblemCollector()))
        .isEqualTo(new LuaString("data:AddDifficulty"));
  }

  @Test
  void arithmeticConcatAndLogic() {
    Scope s = new Scope();
    s.define("isHorde", new LuaBoolean(false));
    assertThat(eval("1 + 2 * 3", s)).isEqualTo(LuaNumber.of(7));
    assertThat(eval("'a' .. 1 .. 'b'", s)).isEqualTo(new LuaString("a1b"));
    assertThat(eval("(isHorde and {1}) or {2}", s))
        .satisfies(v -> assertThat(((LuaTable) v).get(1)).isEqualTo(LuaNumber.of(2)));
    assertThat(eval("'Horde' == 'Horde'", s)).isEqualTo(new LuaBoolean(true));
  }

  @Test
  void identityTableIndexing() {
    Scope s = new Scope();
    s.define("AL", LuaTable.identity());
    assertThat(eval("AL[\"Taragaman the Hungerer\"]", s))
        .isEqualTo(new LuaString("Taragaman the Hungerer"));
  }

  @Test
  void unsupportedCallIsReportedWithCalleeName() {
    ProblemCollector p = new ProblemCollector();
    LuaValue v = eval("string.format('%d', 1)", new Scope(), CallHandler.NONE, p);
    assertThat(v).isInstanceOf(LuaUnresolved.class);
    assertThat(p.problems()).hasSize(1);
    assertThat(p.problems().get(0).message()).contains("string.format");
    assertThat(p.problems().get(0).file()).isEqualTo("t.lua");
  }

  @Test
  void negativeZeroKeysCoincide() {
    LuaTable t = (LuaTable) eval("{[0] = 'a', [-0] = 'b'}", new Scope());
    assertThat(t.entries()).hasSize(1);
    assertThat(eval("-0", new Scope())).isEqualTo(new LuaNumber(0.0));
  }

  @Test
  void unaryOperatorsAndLength() {
    Scope s = new Scope();
    assertThat(eval("not nil", s)).isEqualTo(new LuaBoolean(true));
    assertThat(eval("#{1,2,3}", s)).isEqualTo(LuaNumber.of(3));
    assertThat(eval("#'abcd'", s)).isEqualTo(LuaNumber.of(4));
    assertThat(eval("nil or 5", s)).isEqualTo(LuaNumber.of(5));
  }

  @Test
  void arithmeticOnNonNumberIsUnresolved() {
    ProblemCollector p = new ProblemCollector();
    assertThat(eval("'a' + 1", new Scope(), CallHandler.NONE, p)).isInstanceOf(LuaUnresolved.class);
    assertThat(p.problems().get(0).message()).contains("arithmetic on non-number");
  }

  @Test
  void childScopeShadowsAndFallsBack() {
    Scope parent = new Scope();
    parent.define("a", LuaNumber.of(1));
    parent.define("b", LuaNumber.of(2));
    Scope child = parent.child();
    child.define("a", LuaNumber.of(9));
    assertThat(child.lookup("a")).hasValue(LuaNumber.of(9));
    assertThat(child.lookup("b")).hasValue(LuaNumber.of(2));
    assertThat(parent.lookup("a")).hasValue(LuaNumber.of(1));
    assertThat(child.lookup("c")).isEmpty();
  }

  @Test
  void fieldOnNilIsUnresolvedAndMissingKeyIsNil() {
    Scope s = new Scope();
    s.define("t", new LuaTable());
    ProblemCollector p = new ProblemCollector();
    assertThat(eval("t.missing", s, CallHandler.NONE, p)).isEqualTo(LuaNil.NIL);
    assertThat(p.size()).isZero();
    assertThat(eval("t.missing.x", s, CallHandler.NONE, p)).isInstanceOf(LuaUnresolved.class);
    assertThat(p.size()).isEqualTo(1);
  }
}
