package dev.wowforever.lua;

import java.util.List;
import java.util.Optional;

/** Supplies the result of a function call the evaluator cannot compute itself. */
public interface CallHandler {
  CallHandler NONE = (functionName, args, line) -> Optional.empty();

  /** Returns the call result, or empty if the call is unsupported. */
  Optional<LuaValue> call(String functionName, List<LuaValue> args, int line);
}
