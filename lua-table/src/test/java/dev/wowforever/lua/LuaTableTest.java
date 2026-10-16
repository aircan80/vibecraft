package dev.wowforever.lua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LuaTableTest {

  /** Builds {1,{2,'x'}} by hand; Task 4 may switch this to the evaluator. */
  private static LuaTable nested(long inner, String s) {
    var in = new LuaTable();
    in.put(LuaNumber.of(1), LuaNumber.of(inner));
    in.put(LuaNumber.of(2), new LuaString(s));
    var t = new LuaTable();
    t.put(LuaNumber.of(1), LuaNumber.of(1));
    t.put(LuaNumber.of(2), in);
    return t;
  }

  @Test
  void numericKeysUnifyIntAndFloat() {
    var t = new LuaTable();
    t.put(LuaNumber.of(1), new LuaString("a"));
    assertThat(t.get(new LuaNumber(1.0))).isEqualTo(new LuaString("a"));
  }

  @Test
  void arrayLengthAndHoles() {
    var t = new LuaTable();
    t.put(LuaNumber.of(3), LuaNumber.of(9));
    assertThat(t.arrayLength()).isEqualTo(3);
    assertThat(t.array()).containsExactly(LuaNil.NIL, LuaNil.NIL, LuaNumber.of(9));
  }

  @Test
  void identityTableReturnsKey() {
    assertThat(LuaTable.identity().get("Taragaman")).isEqualTo(new LuaString("Taragaman"));
    assertThat(LuaTable.identity().get(5)).isEqualTo(LuaNil.NIL);
    assertThat(LuaTable.identity().entries()).isEmpty();
  }

  @Test
  void deepEquals() {
    assertThat(LuaValues.deepEquals(nested(2, "x"), nested(2, "x"))).isTrue();
    assertThat(LuaValues.deepEquals(nested(2, "x"), nested(3, "x"))).isFalse();
  }

  @Test
  void putNilRemovesAndNilKeyRejected() {
    var t = new LuaTable();
    t.put(new LuaString("k"), LuaNumber.of(1));
    t.put(new LuaString("k"), LuaNil.NIL);
    assertThat(t.isEmpty()).isTrue();
    assertThatThrownBy(() -> t.put(LuaNil.NIL, LuaNumber.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void entriesKeepInsertionOrder() {
    var t = new LuaTable();
    t.put(new LuaString("b"), LuaNumber.of(1));
    t.put(new LuaString("a"), LuaNumber.of(2));
    assertThat(t.entries()).extracting(e -> e.getKey()).containsExactly(new LuaString("b"), new LuaString("a"));
  }

  @Test
  void numberHelpers() {
    assertThat(new LuaNumber(2.5).isInteger()).isFalse();
    assertThat(LuaNumber.of(7).asLong()).isEqualTo(7L);
    assertThat(LuaValues.asLong(LuaNumber.of(7))).hasValue(7L);
    assertThat(LuaValues.asLong(new LuaNumber(2.5))).isEmpty();
    assertThat(LuaValues.asString(new LuaString("s"))).hasValue("s");
    assertThat(LuaValues.asTable(new LuaString("s"))).isEmpty();
  }
}
