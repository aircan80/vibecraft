package dev.wowforever.importer.corrections;

import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.LuaValue;
import java.util.Optional;

/** A decoded key of a correction table: which field it addresses and how the value applies. */
public record CorrectionKey(Op op, int fieldIndex) {

  /** How a correction value combines with the existing field value. */
  public enum Op { REPLACE, ADD, REMOVE }

  static final String ADD_PREFIX = "op:add:";
  static final String REMOVE_PREFIX = "op:remove:";

  public static Optional<CorrectionKey> parse(LuaValue key) {
    if (key instanceof LuaNumber n && n.isInteger() && n.value() >= 1 && n.value() <= Integer.MAX_VALUE) {
      return Optional.of(new CorrectionKey(Op.REPLACE, (int) n.value()));
    }
    if (key instanceof LuaString s) {
      String v = s.value();
      if (v.startsWith(ADD_PREFIX)) {
        return index(v.substring(ADD_PREFIX.length())).map(i -> new CorrectionKey(Op.ADD, i));
      }
      if (v.startsWith(REMOVE_PREFIX)) {
        return index(v.substring(REMOVE_PREFIX.length())).map(i -> new CorrectionKey(Op.REMOVE, i));
      }
    }
    return Optional.empty();
  }

  private static Optional<Integer> index(String digits) {
    try {
      return Optional.of(Integer.parseInt(digits));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
