package dev.wowforever.lua;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import java.util.List;
import java.util.Optional;

/** Executes the straight-line statements of a chunk against a scope. */
public final class ChunkEvaluator {
  private final String fileName;
  private final CallHandler calls;
  private final ProblemCollector problems;

  public ChunkEvaluator(String fileName, CallHandler calls, ProblemCollector problems) {
    this.fileName = fileName;
    this.calls = calls;
    this.problems = problems;
  }

  /** Runs the statements; returns the value of a {@code return} statement if one was reached. */
  public Optional<LuaValue> run(List<Stmt> stmts, Scope scope) {
    Evaluator ev = new Evaluator(fileName, scope, calls, problems);
    for (Stmt s : stmts) {
      switch (s) {
        case Stmt.Local l -> {
          for (int i = 0; i < l.names().size(); i++) {
            scope.define(l.names().get(i), i < l.values().size() ? ev.eval(l.values().get(i)) : LuaNil.NIL);
          }
        }
        case Stmt.Assign a -> assign(a, scope, ev);
        case Stmt.ExprStmt e -> ev.eval(e.expr());
        case Stmt.Return r -> {
          return Optional.of(ev.eval(r.value()));
        }
        case Stmt.Skipped k -> { }
        case Stmt.FunctionDef f -> { }
      }
    }
    return Optional.empty();
  }

  /** Runs a function body in a child scope and returns its result, or NIL. */
  public LuaValue runFunction(Stmt.FunctionDef f, Scope scope) {
    return run(f.body(), scope.child()).orElse(LuaNil.NIL);
  }

  private void assign(Stmt.Assign a, Scope scope, Evaluator ev) {
    LuaValue value = ev.eval(a.value());
    switch (a.target()) {
      case Expr.Name n -> scope.define(n.name(), value);
      case Expr.Field f -> store(ev.eval(f.target()), new LuaString(f.name()), value, a.line());
      case Expr.Index i -> store(ev.eval(i.target()), ev.eval(i.key()), value, a.line());
      default -> problems.add(fileName, a.line(), "unsupported assignment target");
    }
  }

  private void store(LuaValue target, LuaValue key, LuaValue value, int line) {
    if (target instanceof LuaUnresolved || key instanceof LuaUnresolved) {
      return; // already reported where it arose
    }
    if (!(target instanceof LuaTable t)) {
      problems.add(fileName, line, "assignment to field of non-table " + target);
      return;
    }
    if (key instanceof LuaNil) {
      problems.add(fileName, line, "assignment with nil key");
      return;
    }
    try {
      t.put(key, value);
    } catch (UnsupportedOperationException e) {
      problems.add(fileName, line, "assignment to read-only table");
    }
  }
}
