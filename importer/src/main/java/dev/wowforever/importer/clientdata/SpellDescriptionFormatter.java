package dev.wowforever.importer.clientdata;

import dev.wowforever.importer.report.ImportReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Formats the client's spell description templates ({@code Spell.Description_lang}) into tooltip text (spec 4.5).
 *
 * <p>Placeholders ({@code N} is a 1-based effect index, default 1; {@code <id>} names another spell):
 * <ul>
 *   <li>{@code $sN}: the effect's value; with a {@code Variance} the range "min to max", where min and max are
 *       {@code base * (1 -/+ variance / 2)} rounded. Printed as a magnitude; when the template puts a sign right
 *       before it, that sign stays unless the value is negative, which prints {@code -}.
 *   <li>{@code $mN} the base value, {@code $MN} the maximum (same sign rule); {@code $bN} the points per combo point.
 *   <li>{@code $oN}: the total over time, {@code base * duration / period}, the period being
 *       {@code EffectAuraPeriod}; the regeneration auras 84 and 85 (food and drink) have none in the data and tick
 *       every 5 s in the game.
 *   <li>{@code $d}/{@code $D}: the spell's duration ("10 sec", "2 min", "1 hour", "2 hrs", "1 day").
 *   <li>{@code $tN} the period in seconds, {@code $xN} the chain targets, {@code $aN} the radius (needs
 *       {@code SpellRadius}), {@code $h} the proc chance in percent (needs {@code SpellAuraOptions}).
 *   <li>{@code $/N;sN} (and {@code $*N;sN}): a value divided (multiplied) by N.
 *   <li>{@code ${expr}} with {@code + - * /}, parentheses, the values above, {@code $pl} (level 60) and
 *       {@code $max $min $floor $ceil $abs $round}; a following {@code .N} sets N decimals (default none).
 *   <li>{@code $<id>sN} etc.: the same for another spell; {@code $@spelldesc<id>}, {@code $@spellname<id>},
 *       {@code $@spelltooltip<id>}; {@code $@spellicon<id>} prints nothing.
 *   <li>{@code $lsingular:plural;} after the preceding number; {@code $gmale:female;} takes the first form.
 *   <li>{@code $?cond[a]?cond[b][else]}: no player is known, so a condition on a known spell, aura or class is false
 *       and the first true branch (else the last) is taken; a condition on {@code $pl} is evaluated at level 60.
 *   <li>{@code |cffRRGGBB}, {@code |cn...:}, {@code |r}, {@code |T...|t} and link markup are stripped; CRLF, LF,
 *       {@code |n} and a literal {@code \n} become a line break.
 * </ul>
 * Anything else ({@code $u} stacks, {@code $z} home, {@code $aN} without radii, {@code $h} without a proc chance, a
 * missing effect ...) is kept as written and makes the result incomplete. The formatter remembers every spell whose text was
 * incomplete; {@link #report} counts them as {@value #UNFORMATTED} with a note listing them.
 */
public final class SpellDescriptionFormatter {
  public static final String UNFORMATTED = "clientdata.unformatted";
  static final int PLAYER_LEVEL = 60;
  static final int REGEN_TICK_MS = 5000;
  static final Set<Integer> REGEN_AURAS = Set.of(84, 85);
  private static final int MAX_DEPTH = 4;
  private static final int NOTE_LIMIT = 50;
  private static final Pattern PLURAL = Pattern.compile("[lL]([^:;$|\\[\\]]*):([^;$|\\[\\]]*);");
  private static final Pattern GENDER = Pattern.compile("[gG]([^:;$|\\[\\]]*):([^;$|\\[\\]]*);");
  private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)\\D*$");
  private static final Pattern LEVEL_CONDITION = Pattern.compile("\\$(?:pl|PL)(<=|>=|==|!=|<|>|=)(\\d+)");
  private static final Pattern OTHER_CONDITION = Pattern.compile("[a-zA-Z]+\\d*");

  /**
   * A formatted text.
   *
   * @param complete   every placeholder was replaced
   * @param unresolved the placeholders kept as written, in order of first appearance
   */
  public record Formatted(String text, boolean complete, List<String> unresolved, SpellTextEvidence evidence) {
    public Formatted(String text, boolean complete, List<String> unresolved) { this(text, complete, unresolved, null); }
    public Formatted {
      unresolved = List.copyOf(unresolved);
    }
  }

  private final SpellTables tables;
  private final Map<Long, Set<String>> unformatted = new TreeMap<>();

  public SpellDescriptionFormatter(SpellTables tables) {
    this.tables = tables;
  }

  public SpellTables tables() {
    return tables;
  }

  /** The spell's own {@code Description_lang}, formatted. */
  public Formatted description(long spellId) {
    return format(spellId, tables.description(spellId), true);
  }

  /** {@code template} formatted with {@code spellId} as the spell its placeholders refer to. */
  public Formatted format(long spellId, String template) {
    return format(spellId, template, false);
  }

  private Formatted format(long spellId, String template, boolean spellTable) {
    Run run = new Run();
    String text = template == null ? "" : render(spellId, template, run, 0).strip();
    if (!run.unresolved.isEmpty()) {
      unformatted.computeIfAbsent(spellId, k -> new LinkedHashSet<>()).addAll(run.unresolved);
    }
    var tokens = new ArrayList<>(run.unresolved);
    return new Formatted(text, tokens.isEmpty(), tokens, tables.evidence(spellId, template, tokens, spellTable));
  }

  /** The spells formatted incompletely so far, in id order, with their unresolved placeholders. */
  public Map<Long, Set<String>> unformattedSpells() {
    return Collections.unmodifiableMap(unformatted);
  }

  /**
   * Counts {@value #UNFORMATTED} (spells, always emitted) and notes the first 50 with their placeholders, plus how
   * many spells each kind of placeholder left unformatted.
   */
  public void report(ImportReport report) {
    report.count(UNFORMATTED, unformatted.size());
    if (unformatted.isEmpty()) {
      return;
    }
    report.note(UNFORMATTED + ": " + unformatted.size() + " spells; first " + Math.min(NOTE_LIMIT, unformatted.size())
        + ": " + unformatted.entrySet().stream().limit(NOTE_LIMIT)
            .map(e -> e.getKey() + " (" + String.join(" ", e.getValue()) + ")")
            .collect(Collectors.joining(", ")));
    Map<String, Integer> kinds = new TreeMap<>();
    for (Set<String> tokens : unformatted.values()) {
      tokens.stream().map(SpellDescriptionFormatter::tokenKind).distinct().forEach(k -> kinds.merge(k, 1, Integer::sum));
    }
    report.note(UNFORMATTED + ".tokens: " + kinds.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
        .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")));
  }

  /** {@code $16783a1} -> {@code $<id>aN}; an expression or condition by its opening. */
  static String tokenKind(String token) {
    if (token.startsWith("${")) {
      return "${...}";
    }
    if (token.startsWith("$?")) {
      return "$?...";
    }
    return token.replaceFirst("^\\$\\d+", "\\$<id>").replaceFirst("(?<=[a-zA-Z])\\d+$", "N");
  }

  // ---------------------------------------------------------------------------------------------------------------

  private static final class Run {
    final Set<String> unresolved = new LinkedHashSet<>();
  }

  private String render(long spellId, String t, Run run, int depth) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    int n = t.length();
    while (i < n) {
      char c = t.charAt(i);
      if (c == '\r') {
        i++;
        if (i >= n || t.charAt(i) != '\n') {
          out.append('\n');
        }
        continue;
      }
      if (c == '\\' && i + 1 < n && t.charAt(i + 1) == 'n') {
        out.append('\n');
        i += 2;
        continue;
      }
      if (c == '|') {
        int skip = escape(t, i, out);
        if (skip > 0) {
          i += skip;
          continue;
        }
      }
      if (c == '$') {
        i = placeholder(spellId, t, i, out, run, depth);
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  /** Consumes a {@code |} escape, returning its length (0 when it is a plain bar). */
  private static int escape(String t, int i, StringBuilder out) {
    int n = t.length();
    if (i + 1 >= n) {
      return 0;
    }
    char e = t.charAt(i + 1);
    switch (e) {
      case 'c' -> {
        if (i + 10 <= n && t.substring(i + 2, i + 10).chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
          return 10;
        }
        if (i + 2 < n && t.charAt(i + 2) == 'n') {
          int colon = t.indexOf(':', i + 3);
          return colon < 0 ? 0 : colon - i + 1;
        }
        return 0;
      }
      case 'r', 'h' -> {
        return 2;
      }
      case 'n' -> {
        out.append('\n');
        return 2;
      }
      case 'T' -> {
        int end = t.indexOf("|t", i + 2);
        return end < 0 ? 0 : end - i + 2;
      }
      case 'H' -> {
        int end = t.indexOf("|h", i + 2);
        return end < 0 ? 0 : end - i + 2;
      }
      default -> {
        return 0;
      }
    }
  }

  /** Renders the placeholder starting at {@code i} (a {@code $}) into {@code out}; returns the index after it. */
  private int placeholder(long spellId, String t, int i, StringBuilder out, Run run, int depth) {
    int n = t.length();
    int p = i + 1;
    if (p >= n) {
      out.append('$');
      return p;
    }
    char d = t.charAt(p);
    if (d == '{') {
      return expression(spellId, t, i, out, run);
    }
    if (d == '/' || d == '*') {
      return scaled(spellId, t, i, out, run);
    }
    if (d == '?') {
      return conditional(spellId, t, i, out, run, depth);
    }
    if (d == '@') {
      return reference(t, i, out, run, depth);
    }
    if (d == 'l' || d == 'L') {
      Matcher m = PLURAL.matcher(t).region(p, n);
      if (m.lookingAt()) {
        Matcher last = LAST_NUMBER.matcher(out);
        boolean one = last.find() && Double.parseDouble(last.group(1)) == 1.0;
        out.append(one ? m.group(1) : m.group(2));
        return m.end();
      }
    }
    if (d == 'g' || d == 'G') {
      Matcher m = GENDER.matcher(t).region(p, n);
      if (m.lookingAt()) {
        out.append(m.group(1));
        return m.end();
      }
    }
    if (Character.isDigit(d) || Character.isLetter(d)) {
      Ref ref = Ref.parse(t, p);
      if (ref == null) {
        out.append('$');
        return p;
      }
      String raw = t.substring(i, ref.end());
      String text = refText(ref.spell(spellId), ref.letters(), ref.index(), t, i, out);
      if (text == null) {
        out.append(raw);
        run.unresolved.add(raw);
      } else {
        out.append(text);
      }
      return ref.end();
    }
    out.append('$');
    return p;
  }

  /**
   * A value reference: an optional spell id, the letters, an optional effect index.
   *
   * @param id    the other spell, or -1 for the current one
   * @param index 1-based, 1 when absent
   */
  private record Ref(long id, String letters, int index, int end) {
    long spell(long current) {
      return id < 0 ? current : id;
    }

    static Ref parse(String t, int p) {
      int n = t.length();
      int k = p;
      long id = -1;
      if (Character.isDigit(t.charAt(k))) {
        while (k < n && Character.isDigit(t.charAt(k))) {
          k++;
        }
        id = Long.parseLong(t.substring(p, k));
      }
      int letters = k;
      while (k < n && Character.isLetter(t.charAt(k))) {
        k++;
      }
      if (k == letters) {
        return null;
      }
      String name = t.substring(letters, k);
      int digits = k;
      // an effect index has at most two digits: a spell has at most 32 effects
      while (k < n && Character.isDigit(t.charAt(k)) && k - digits < 2) {
        k++;
      }
      int index = k == digits ? 1 : Integer.parseInt(t.substring(digits, k));
      return new Ref(id, name, index, k);
    }
  }

  /** The text of a value reference, or null when it cannot be formatted. */
  private String refText(long spell, String letters, int index, String t, int at, StringBuilder out) {
    switch (letters) {
      case "s", "S" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        if (e == null) {
          return null;
        }
        long lo = Math.round(Math.abs(e.basePoints() * (1 - e.variance() / 2)));
        long hi = Math.round(Math.abs(e.basePoints() * (1 + e.variance() / 2)));
        String magnitude = e.variance() > 0 && lo != hi ? Math.min(lo, hi) + " to " + Math.max(lo, hi)
            : number(Math.abs(e.basePoints()));
        return signed(e.basePoints(), magnitude, t, at, out);
      }
      case "m" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        return e == null ? null : signed(e.basePoints(), number(Math.abs(e.basePoints())), t, at, out);
      }
      case "M" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        if (e == null) {
          return null;
        }
        double max = e.basePoints() * (1 + e.variance() / 2);
        return signed(max, number(Math.round(Math.abs(max))), t, at, out);
      }
      case "b", "B" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        return e == null || e.pointsPerResource() == 0 ? null
            : signed(e.pointsPerResource(), number(Math.abs(e.pointsPerResource())), t, at, out);
      }
      case "o", "O" -> {
        OptionalDouble total = overTime(spell, index);
        return total.isEmpty() ? null
            : signed(total.getAsDouble(), number(Math.round(Math.abs(total.getAsDouble()))), t, at, out);
      }
      case "d", "D" -> {
        OptionalLong ms = tables.durationMs(spell);
        return ms.isPresent() && ms.getAsLong() > 0 ? duration(ms.getAsLong()) : null;
      }
      case "t", "T" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        return e == null || e.auraPeriodMs() <= 0 ? null : number(e.auraPeriodMs() / 1000.0);
      }
      case "x", "X" -> {
        SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
        return e == null || e.chainTargets() <= 0 ? null : String.valueOf(e.chainTargets());
      }
      case "a", "A" -> {
        OptionalDouble r = tables.radius(spell, index - 1);
        return r.isEmpty() ? null : number(r.getAsDouble());
      }
      case "h", "H" -> {
        OptionalInt pct = tables.procChance(spell);
        return pct.isEmpty() ? null : String.valueOf(pct.getAsInt());
      }
      default -> {
        return null;
      }
    }
  }

  /** The numeric value of a reference inside an expression, or empty. */
  private OptionalDouble refValue(long spell, String letters, int index) {
    switch (letters) {
      case "s", "S", "m" -> {
        return tables.effect(spell, index - 1).map(e -> OptionalDouble.of(e.basePoints())).orElse(OptionalDouble.empty());
      }
      case "M" -> {
        return tables.effect(spell, index - 1).map(e -> OptionalDouble.of(e.basePoints() * (1 + e.variance() / 2)))
            .orElse(OptionalDouble.empty());
      }
      case "b", "B" -> {
        return tables.effect(spell, index - 1).map(e -> OptionalDouble.of(e.pointsPerResource()))
            .orElse(OptionalDouble.empty());
      }
      case "o", "O" -> {
        return overTime(spell, index);
      }
      case "d", "D" -> {
        OptionalLong ms = tables.durationMs(spell);
        return ms.isPresent() && ms.getAsLong() > 0 ? OptionalDouble.of(ms.getAsLong() / 1000.0)
            : OptionalDouble.empty();
      }
      case "t", "T" -> {
        return tables.effect(spell, index - 1).filter(e -> e.auraPeriodMs() > 0)
            .map(e -> OptionalDouble.of(e.auraPeriodMs() / 1000.0)).orElse(OptionalDouble.empty());
      }
      case "x", "X" -> {
        return tables.effect(spell, index - 1).filter(e -> e.chainTargets() > 0)
            .map(e -> OptionalDouble.of(e.chainTargets())).orElse(OptionalDouble.empty());
      }
      case "a", "A" -> {
        return tables.radius(spell, index - 1);
      }
      case "h", "H" -> {
        OptionalInt pct = tables.procChance(spell);
        return pct.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(pct.getAsInt());
      }
      case "pl", "PL" -> {
        return OptionalDouble.of(PLAYER_LEVEL);
      }
      default -> {
        return OptionalDouble.empty();
      }
    }
  }

  private OptionalDouble overTime(long spell, int index) {
    SpellTables.Effect e = tables.effect(spell, index - 1).orElse(null);
    OptionalLong ms = tables.durationMs(spell);
    if (e == null || ms.isEmpty() || ms.getAsLong() <= 0) {
      return OptionalDouble.empty();
    }
    int period = e.auraPeriodMs() > 0 ? e.auraPeriodMs() : REGEN_AURAS.contains(e.aura()) ? REGEN_TICK_MS : 0;
    return period == 0 ? OptionalDouble.empty() : OptionalDouble.of(e.basePoints() * ms.getAsLong() / period);
  }

  /**
   * Applies the sign rule: with a {@code +} or {@code -} right before the placeholder, a negative value turns that
   * sign into {@code -}; the magnitude follows either way.
   */
  private static String signed(double value, String magnitude, String t, int at, StringBuilder out) {
    if (at > 0 && (t.charAt(at - 1) == '+' || t.charAt(at - 1) == '-') && out.length() > 0
        && out.charAt(out.length() - 1) == t.charAt(at - 1) && value < 0) {
      out.setCharAt(out.length() - 1, '-');
    }
    return magnitude;
  }

  /** {@code ${expr}} with an optional {@code .N} precision after it. */
  private int expression(long spellId, String t, int i, StringBuilder out, Run run) {
    int n = t.length();
    int close = matching(t, i + 1, '{', '}');
    if (close < 0) {
      out.append(t, i, n);
      run.unresolved.add(t.substring(i));
      return n;
    }
    int end = close + 1;
    int precision = 0;
    if (end + 1 < n && t.charAt(end) == '.' && Character.isDigit(t.charAt(end + 1))) {
      int k = end + 1;
      while (k < n && Character.isDigit(t.charAt(k))) {
        k++;
      }
      precision = Integer.parseInt(t.substring(end + 1, k));
      end = k;
    }
    try {
      double v = new Expr(t.substring(i + 2, close), spellId).parse();
      out.append(fixed(v, precision));
    } catch (IllegalArgumentException e) {
      out.append(t, i, end);
      run.unresolved.add(t.substring(i, end));
    }
    return end;
  }

  /** {@code $/N;ref} or {@code $*N;ref}. */
  private int scaled(long spellId, String t, int i, StringBuilder out, Run run) {
    int n = t.length();
    int k = i + 2;
    int digits = k;
    while (k < n && Character.isDigit(t.charAt(k))) {
      k++;
    }
    Ref ref = k > digits && k < n - 1 && t.charAt(k) == ';' ? Ref.parse(t, k + 1) : null;
    if (ref == null) {
      out.append('$');
      run.unresolved.add(t.substring(i, Math.min(n, k + 1)));
      return i + 1;
    }
    double factor = Double.parseDouble(t.substring(digits, k));
    OptionalDouble v = refValue(ref.spell(spellId), ref.letters(), ref.index());
    if (v.isEmpty() || factor == 0) {
      out.append(t, i, ref.end());
      run.unresolved.add(t.substring(i, ref.end()));
      return ref.end();
    }
    double value = t.charAt(i + 1) == '/' ? v.getAsDouble() / factor : v.getAsDouble() * factor;
    out.append(signed(value, number(Math.abs(value)), t, i, out));
    return ref.end();
  }

  /** {@code $?cond[a]?cond[b][else]}: the first true branch, else the else branch (empty without one). */
  private int conditional(long spellId, String t, int i, StringBuilder out, Run run, int depth) {
    int n = t.length();
    int k = i + 2;
    String chosen = null;
    String otherwise = "";
    while (true) {
      int open = t.indexOf('[', k);
      if (open < 0) {
        return failedConditional(t, i, out, run);
      }
      String cond = t.substring(k, open);
      int close = matching(t, open, '[', ']');
      if (close < 0) {
        return failedConditional(t, i, out, run);
      }
      Boolean truth = condition(cond);
      if (truth == null) {
        run.unresolved.add("$?" + cond);
        truth = false;
      }
      if (truth && chosen == null) {
        chosen = t.substring(open + 1, close);
      }
      k = close + 1;
      if (k < n && t.charAt(k) == '?') {
        k++;
        continue;
      }
      if (k < n && t.charAt(k) == '[') {
        int end = matching(t, k, '[', ']');
        if (end < 0) {
          return failedConditional(t, i, out, run);
        }
        otherwise = t.substring(k + 1, end);
        k = end + 1;
      }
      break;
    }
    out.append(render(spellId, chosen != null ? chosen : otherwise, run, depth));
    return k;
  }

  private static int failedConditional(String t, int i, StringBuilder out, Run run) {
    out.append("$?");
    run.unresolved.add("$?");
    return i + 2;
  }

  /**
   * A condition with {@code |}, {@code &}, {@code !} and parentheses; {@code $pl}/{@code $PL} comparisons at level
   * 60, any other atom (a spell, aura or class test) false. Null when an atom is not understood.
   */
  static Boolean condition(String cond) {
    String c = cond.replace(" ", "").replace("(", "").replace(")", "");
    if (c.isEmpty()) {
      return null;
    }
    Boolean result = null;
    char op = '|';
    int k = 0;
    while (k <= c.length()) {
      int next = k;
      while (next < c.length() && c.charAt(next) != '|' && c.charAt(next) != '&') {
        next++;
      }
      Boolean atom = atom(c.substring(k, next));
      if (atom == null) {
        return null;
      }
      result = result == null ? atom : op == '|' ? result || atom : result && atom;
      if (next >= c.length()) {
        break;
      }
      op = c.charAt(next);
      k = next + 1;
    }
    return result;
  }

  private static Boolean atom(String a) {
    boolean negate = false;
    while (a.startsWith("!")) {
      negate = !negate;
      a = a.substring(1);
    }
    Boolean value;
    Matcher level = LEVEL_CONDITION.matcher(a);
    if (level.matches()) {
      int x = Integer.parseInt(level.group(2));
      value = switch (level.group(1)) {
        case "<" -> PLAYER_LEVEL < x;
        case "<=" -> PLAYER_LEVEL <= x;
        case ">" -> PLAYER_LEVEL > x;
        case ">=" -> PLAYER_LEVEL >= x;
        case "!=" -> PLAYER_LEVEL != x;
        default -> PLAYER_LEVEL == x;
      };
    } else if (OTHER_CONDITION.matcher(a).matches()) {
      value = false;
    } else {
      return null;
    }
    return negate != value;
  }

  /** {@code $@spelldesc<id>}, {@code $@spellname<id>}, {@code $@spelltooltip<id>}, {@code $@spellicon<id>}. */
  private int reference(String t, int i, StringBuilder out, Run run, int depth) {
    int n = t.length();
    int k = i + 2;
    while (k < n && Character.isLetter(t.charAt(k))) {
      k++;
    }
    String name = t.substring(i + 2, k);
    int digits = k;
    while (k < n && Character.isDigit(t.charAt(k))) {
      k++;
    }
    String raw = t.substring(i, k);
    String text = null;
    if (k > digits) {
      long id = Long.parseLong(t.substring(digits, k));
      text = switch (name) {
        case "spelldesc" -> depth < MAX_DEPTH && !tables.description(id).isEmpty()
            ? render(id, tables.description(id), run, depth + 1).strip() : null;
        case "spelltooltip", "spellaura" -> depth < MAX_DEPTH && !tables.auraDescription(id).isEmpty()
            ? render(id, tables.auraDescription(id), run, depth + 1).strip() : null;
        case "spellname" -> tables.name(id).orElse(null);
        case "spellicon" -> "";
        default -> null;
      };
    }
    if (text == null) {
      out.append(raw);
      run.unresolved.add(raw);
    } else {
      out.append(text);
    }
    return k;
  }

  /** The index of the bracket closing the one at {@code open}, or -1. */
  private static int matching(String t, int open, char opening, char closing) {
    int level = 0;
    for (int k = open; k < t.length(); k++) {
      char c = t.charAt(k);
      if (c == opening) {
        level++;
      } else if (c == closing && --level == 0) {
        return k;
      }
    }
    return -1;
  }

  /** A recursive-descent evaluator for {@code ${...}}; throws {@link IllegalArgumentException} on anything else. */
  private final class Expr {
    private final String s;
    private final long spellId;
    private int pos;

    Expr(String s, long spellId) {
      this.s = s;
      this.spellId = spellId;
    }

    double parse() {
      double v = sum();
      skip();
      if (pos != s.length()) {
        throw new IllegalArgumentException("trailing " + s.substring(pos));
      }
      return v;
    }

    private double sum() {
      double v = product();
      while (true) {
        skip();
        if (eat('+')) {
          v += product();
        } else if (eat('-')) {
          v -= product();
        } else {
          return v;
        }
      }
    }

    private double product() {
      double v = unary();
      while (true) {
        skip();
        if (eat('*')) {
          v *= unary();
        } else if (eat('/')) {
          double d = unary();
          if (d == 0) {
            throw new IllegalArgumentException("division by zero");
          }
          v /= d;
        } else {
          return v;
        }
      }
    }

    private double unary() {
      skip();
      if (eat('-')) {
        return -unary();
      }
      if (eat('+')) {
        return unary();
      }
      return primary();
    }

    private double primary() {
      skip();
      if (eat('(')) {
        double v = sum();
        skip();
        expect(')');
        return v;
      }
      if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
          pos++;
        }
        return Double.parseDouble(s.substring(start, pos));
      }
      if (eat('$')) {
        int start = pos;
        while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
          pos++;
        }
        String fn = s.substring(start, pos);
        if (!fn.isEmpty() && pos < s.length() && s.charAt(pos) == '(') {
          pos++;
          List<Double> args = new ArrayList<>();
          args.add(sum());
          skip();
          while (eat(',')) {
            args.add(sum());
            skip();
          }
          expect(')');
          return function(fn, args);
        }
        pos = start;
        if (pos >= s.length()) {
          throw new IllegalArgumentException("dangling $");
        }
        Ref ref = Ref.parse(s, pos);
        if (ref == null) {
          throw new IllegalArgumentException("bad reference");
        }
        pos = ref.end();
        OptionalDouble v = refValue(ref.spell(spellId), ref.letters(), ref.index());
        if (v.isEmpty()) {
          throw new IllegalArgumentException("unknown value " + ref.letters());
        }
        return v.getAsDouble();
      }
      throw new IllegalArgumentException("unexpected input at " + pos);
    }

    private double function(String fn, List<Double> a) {
      return switch (fn) {
        case "max" -> a.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        case "min" -> a.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        case "floor" -> Math.floor(one(a));
        case "ceil" -> Math.ceil(one(a));
        case "abs" -> Math.abs(one(a));
        case "round" -> Math.round(one(a));
        default -> throw new IllegalArgumentException("unknown function " + fn);
      };
    }

    private double one(List<Double> a) {
      if (a.size() != 1) {
        throw new IllegalArgumentException("one argument expected");
      }
      return a.get(0);
    }

    private void skip() {
      while (pos < s.length() && s.charAt(pos) == ' ') {
        pos++;
      }
    }

    private boolean eat(char c) {
      if (pos < s.length() && s.charAt(pos) == c) {
        pos++;
        return true;
      }
      return false;
    }

    private void expect(char c) {
      if (!eat(c)) {
        throw new IllegalArgumentException("expected " + c);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  /** A duration as spell texts print it: "1.5 sec", "10 sec", "2 min", "1 hour", "2 hrs", "1 day", "3 days". */
  public static String duration(long ms) {
    if (ms < 60_000) {
      return number(ms / 1000.0) + " sec";
    }
    if (ms < 3_600_000) {
      return number(ms / 60_000.0) + " min";
    }
    if (ms < 86_400_000) {
      double h = ms / 3_600_000.0;
      return h == 1 ? "1 hour" : number(h) + " hrs";
    }
    double d = ms / 86_400_000.0;
    return d == 1 ? "1 day" : number(d) + " days";
  }

  /** A whole number without decimals, else up to two decimals with trailing zeros dropped. */
  static String number(double v) {
    if (Math.abs(v - Math.rint(v)) < 1e-9) {
      return String.valueOf((long) Math.rint(v));
    }
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  /** {@code v} with exactly {@code decimals} decimals, rounded half up. */
  static String fixed(double v, int decimals) {
    String s = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    return s.equals("-0") || s.matches("-0\\.0+") ? s.substring(1) : s;
  }
}
