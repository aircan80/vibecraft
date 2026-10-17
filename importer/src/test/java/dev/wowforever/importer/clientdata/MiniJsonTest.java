package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MiniJsonTest {

  @Test
  void parsesObjectsArraysStringsNumbersAndLiterals() {
    Object v = MiniJson.parse(" {\"a\": [1, -2.5e1, true, false, null], \"s\": \"q\\\"\\\\\\/\\n\\t\\u0041\", \"o\": {}} ");

    assertThat(v).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> m = (Map<String, Object>) v;
    assertThat(m.keySet()).containsExactly("a", "s", "o");
    assertThat((List<Object>) m.get("a")).containsExactly(1L, -25.0, true, false, null);
    assertThat(m.get("s")).isEqualTo("q\"\\/\n\tA");
    assertThat((Map<?, ?>) m.get("o")).isEmpty();
    assertThat(MiniJson.parse("[]")).isEqualTo(List.of());
  }

  @Test
  void rejectsMalformedInputWithOffset() {
    assertThatThrownBy(() -> MiniJson.parse("{\"a\" 1}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offset 5");
    assertThatThrownBy(() -> MiniJson.parse("[1,]")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MiniJson.parse("\"open")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MiniJson.parse("{} x")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("trailing");
  }
}
