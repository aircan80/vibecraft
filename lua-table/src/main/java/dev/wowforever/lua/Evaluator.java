package dev.wowforever.lua;

import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.TableEntry;
import java.util.ArrayList;
import java.util.List;

/** Evaluates expressions against a scope; anything unresolvable becomes a reported LuaUnresolved. */
public final class Evaluator {
  private final String fileName;
  private final Scope scope;
  private final CallHandler calls;
  private final ProblemCollector problems;

  public Evaluator(String fileName, Scope scope, CallHandler calls, ProblemCollector problems) {
    this.fileName = fileName;
    this.scope = scope;
    this.calls = calls;
    this.problems = problems;
  }

  public LuaValue eval(Expr e) {
    return switch (e) {
      case Expr.Literal l -> l.value();
      case Expr.Name n -> scope.lookup(n.name()).orElseGet(() -> unresolved("unknown name " + n.name(), n.line()));
      case Expr.Field f -> index(eval(f.target()), new LuaString(f.name()), f.line());
      case Expr.Index i -> index(eval(i.target()), eval(i.key()), i.line());
      case Expr.Call c -> call(render(c.fn()), c.args(), c.line());
      case Expr.MethodCall m -> call(render(m.target()) + ":" + m.method(), m.args(), m.line());
      case Expr.Binary b -> binary(b);
      case Expr.Unary u -> unary(u);
      case Expr.Paren p -> eval(p.inner());
      case Expr.TableCtor t -> table(t);
      case Expr.Vararg v -> unresolved("vararg", v.line());
    };
  }

  private LuaUnresolved unresolved(String reason, int line) {
    problems.add(fileName, line, reason);
    return new LuaUnresolved(reason, line);
  }

  private static String render(Expr e) {
    return switch (e) {
      case Expr.Name n -> n.name();
      case Expr.Field f -> render(f.target()) + "." + f.name();
      case Expr.MethodCall m -> render(m.target()) + ":" + m.method();
      default -> "<expr>";
    };
  }

  private LuaValue index(LuaValue target, LuaValue key, int line) {
    // An unresolved target or key was already reported where it arose; pass it on.
    if (target instanceof LuaUnresolved) {
      return target;
    }
    if (key instanceof LuaUnresolved) {
      return key;
    }
    if (!(target instanceof LuaTable t)) {
      return unresolved("index of non-table " + target, line);
    }
    return t.get(normalize(key));
  }

  private LuaValue call(String name, List<Expr> argExprs, int line) {
    List<LuaValue> args = new ArrayList<>(argExprs.size());
    for (Expr a : argExprs) {
      args.add(eval(a));
    }
    return calls.call(name, args, line).orElseGet(() -> unresolved("call to " + name + " unsupported", line));
  }

  private static boolean truthy(LuaValue v) {
    return !(v instanceof LuaNil) && !(v instanceof LuaBoolean b && !b.value());
  }

  private LuaValue binary(Expr.Binary b) {
    LuaValue l = eval(b.left());
    if (b.op().equals("and") || b.op().equals("or")) {
      // Short-circuits like Lua; an unresolved left operand is returned so the problem stays visible.
      if (l instanceof LuaUnresolved) {
        return l;
      }
      return truthy(l) == b.op().equals("and") ? eval(b.right()) : l;
    }
    LuaValue r = eval(b.right());
    return switch (b.op()) {
      case "==" -> new LuaBoolean(LuaValues.deepEquals(l, r));
      case "~=" -> new LuaBoolean(!LuaValues.deepEquals(l, r));
      case "<", "<=", ">", ">=" -> compare(b.op(), l, r, b.line());
      case ".." -> concat(l, r, b.line());
      default -> arithmetic(b.op(), l, r, b.line());
    };
  }

  private LuaValue arithmetic(String op, LuaValue l, LuaValue r, int line) {
    if (!(l instanceof LuaNumber x) || !(r instanceof LuaNumber y)) {
      return unresolved("arithmetic on non-number", line);
    }
    return switch (op) {
      case "+" -> new LuaNumber(x.value() + y.value());
      case "-" -> new LuaNumber(x.value() - y.value());
      case "*" -> new LuaNumber(x.value() * y.value());
      case "/" -> new LuaNumber(x.value() / y.value());
      default -> unresolved("unsupported operator " + op, line);
    };
  }

  private LuaValue concat(LuaValue l, LuaValue r, int line) {
    String a = text(l);
    String c = text(r);
    return a == null || c == null ? unresolved("concatenation of non-string", line) : new LuaString(a + c);
  }

  private static String text(LuaValue v) {
    if (v instanceof LuaString s) {
      return s.value();
    }
    if (v instanceof LuaNumber n) {
      return n.isInteger() ? String.format("%d", n.asLong()) : Double.toString(n.value());
    }
    return null;
  }

  private LuaValue compare(String op, LuaValue l, LuaValue r, int line) {
    int c;
    if (l instanceof LuaNumber x && r instanceof LuaNumber y) {
      c = Double.compare(x.value(), y.value());
    } else if (l instanceof LuaString x && r instanceof LuaString y) {
      c = x.value().compareTo(y.value());
    } else {
      return unresolved("comparison of incompatible values", line);
    }
    return new LuaBoolean(
        switch (op) {
          case "<" -> c < 0;
          case "<=" -> c <= 0;
          case ">" -> c > 0;
          default -> c >= 0;
        });
  }

  private LuaValue unary(Expr.Unary u) {
    LuaValue v = eval(u.operand());
    return switch (u.op()) {
      case "not" -> new LuaBoolean(!truthy(v) && !(v instanceof LuaUnresolved));
      case "-" -> v instanceof LuaNumber n
          ? normalize(new LuaNumber(-n.value()))
          : v instanceof LuaUnresolved ? v : unresolved("arithmetic on non-number", u.line());
      case "#" -> v instanceof LuaTable t
          ? LuaNumber.of(t.arrayLength())
          : v instanceof LuaString s
              ? LuaNumber.of(s.value().length())
              : v instanceof LuaUnresolved ? v : unresolved("length of non-table", u.line());
      default -> unresolved("unsupported operator " + u.op(), u.line());
    };
  }

  /** Maps -0.0 to 0.0 so that table keys [0] and [-0] coincide. */
  private static LuaValue normalize(LuaValue v) {
    return v instanceof LuaNumber n && n.value() == 0.0 ? new LuaNumber(0.0) : v;
  }

  private LuaValue table(Expr.TableCtor ctor) {
    LuaTable t = new LuaTable();
    long position = 1;
    for (TableEntry entry : ctor.entries()) {
      switch (entry) {
        case TableEntry.Positional p -> t.put(LuaNumber.of(position++), eval(p.value()));
        case TableEntry.Named n -> t.put(new LuaString(n.name()), eval(n.value()));
        case TableEntry.Keyed k -> {
          LuaValue key = eval(k.key());
          LuaValue value = eval(k.value());
          if (key instanceof LuaNil) {
            unresolved("table key is nil", ctor.line());
          } else if (!(key instanceof LuaUnresolved)) {
            // An unresolved key was already reported when it was evaluated; the entry is dropped.
            t.put(normalize(key), value);
          }
        }
      }
    }
    return t;
  }
}
