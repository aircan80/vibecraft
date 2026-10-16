package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Expr.*;
import dev.wowforever.lua.ast.TableEntry.Keyed;
import dev.wowforever.lua.ast.TableEntry.Named;
import org.junit.jupiter.api.Test;

class ParserTest {

  private static Expr parse(String s) {
    return new Parser(new Lexer(s, "t.lua").tokenize(), "t.lua").parseExpression();
  }

  @Test
  void parsesPositionalKeyedAndNamedEntries() {
    Expr e = parse("{ 'a', nil, [3] = 'c', name = 4, }");
    assertThat(((TableCtor) e).entries()).hasSize(4);
    assertThat(((TableCtor) e).entries().get(2)).isInstanceOf(Keyed.class);
    assertThat(((TableCtor) e).entries().get(3))
        .isEqualTo(new Named("name", new Literal(LuaNumber.of(4), 1)));
  }

  @Test
  void parsesDottedIndexedAndCalls() {
    assertThat(parse("itemKeys.npcDrops"))
        .isEqualTo(new Field(new Name("itemKeys", 1), "npcDrops", 1));
    assertThat(parse("AL[\"x\"]")).isInstanceOf(Index.class);
    assertThat(parse("data:AddDifficulty(\"NORMAL\", nil)")).isInstanceOf(MethodCall.class);
    assertThat(parse("GetForVersion({10,13},{8,13})")).isInstanceOf(Call.class);
  }

  @Test
  void precedence() {
    // -1 + 2 * 3 .. 'x' == y and z or w  ->  or( and( ==( ..( +( -1, *(2,3)), 'x'), y), z), w)
    Expr e = parse("-1 + 2 * 3 .. 'x' == y and z or w");
    assertThat(((Binary) e).op()).isEqualTo("or");
    assertThat(((Binary) ((Binary) e).left()).op()).isEqualTo("and");
  }

  @Test
  void reportsLineOnError() {
    assertThatThrownBy(() -> parse("{\n[1] = }"))
        .isInstanceOf(LuaSyntaxException.class)
        .hasMessageContaining("line 2");
  }

  @Test
  void concatIsRightAssociativeAndAdditiveLeft() {
    Binary c = (Binary) parse("a .. b .. c");
    assertThat(c.op()).isEqualTo("..");
    assertThat(c.right()).isInstanceOf(Binary.class);
    Binary s = (Binary) parse("a - b - c");
    assertThat(s.left()).isInstanceOf(Binary.class);
  }

  @Test
  void callSugarAndHexNumbers() {
    assertThat(parse("f\"str\"")).isInstanceOf(Call.class);
    assertThat(parse("f{1}")).isInstanceOf(Call.class);
    assertThat(parse("0x10")).isEqualTo(new Literal(LuaNumber.of(16), 1));
    assertThat(parse("1.5e1")).isEqualTo(new Literal(new LuaNumber(15.0), 1));
  }

  @Test
  void parenUnaryVarargAndLiterals() {
    assertThat(parse("(a)")).isInstanceOf(Paren.class);
    assertThat(parse("#t")).isEqualTo(new Unary("#", new Name("t", 1), 1));
    assertThat(parse("not true"))
        .isEqualTo(new Unary("not", new Literal(new LuaBoolean(true), 1), 1));
    assertThat(parse("...")).isEqualTo(new Vararg(1));
    assertThat(parse("nil")).isEqualTo(new Literal(LuaNil.NIL, 1));
  }

  @Test
  void parseExpressionStopsAfterExpressionAndAtEndWorks() {
    var p = new Parser(new Lexer("a b", "t.lua").tokenize(), "t.lua");
    assertThat(p.parseExpression()).isEqualTo(new Name("a", 1));
    assertThat(p.atEnd()).isFalse();
    p.parseExpression();
    assertThat(p.atEnd()).isTrue();
  }
}
