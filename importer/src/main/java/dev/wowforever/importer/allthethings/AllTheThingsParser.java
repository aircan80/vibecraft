package dev.wowforever.importer.allthethings;

import dev.wowforever.lua.LuaChunk;
import dev.wowforever.lua.LuaNumber;
import dev.wowforever.lua.LuaString;
import dev.wowforever.lua.ast.Expr;
import dev.wowforever.lua.ast.Stmt;
import dev.wowforever.lua.ast.TableEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static evidence reader, never a Lua evaluator. Unknown conditions are retained as issues, not guessed. */
public final class AllTheThingsParser {
  private AllTheThingsParser() {}
  public record QuestLink(long questId, long relatedId, String kind, String sourcePath, int sourceLine, String evidence) {}
  public record Quest(long id, String name, List<Long> sourceQuests, Integer requiredCount, String race,
      List<String> timeline, String sourcePath, int sourceLine, boolean supported, String detail) {
    public Quest { sourceQuests = List.copyOf(sourceQuests); timeline = List.copyOf(timeline); }
  }
  public record Issue(String sourcePath, int sourceLine, String detail) {}
  public record Result(List<QuestLink> links, List<Quest> quests, List<Issue> issues) {
    public Result { links = List.copyOf(links); quests = List.copyOf(quests); issues = List.copyOf(issues); }
  }
  private static final Set<String> ACTIVE_TAGS = Set.of("FOREVER", "CAMELOT", "ANYCLASSIC", "CLASSIC", "CRIEVE", "EXPLORATION", "OBJECTIVES", "NOSIMPLIFY");
  private static final Set<String> INACTIVE_TAGS = Set.of("RETAIL", "SOD", "TBC", "WRATH", "CATA", "MOP", "VANILLA");

  public static Result parse(Map<String, String> inputs) {
    var quests = new ArrayList<Quest>(); var issues = new ArrayList<Issue>();
    inputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(input -> {
      try {
        String text = preprocessor(input.getValue(), input.getKey(), issues);
        walk(LuaChunk.parse(text, input.getKey()).statements(), input.getKey(), text.lines().toList(), quests, issues, false);
      } catch (RuntimeException e) { issues.add(new Issue(input.getKey(), 0, "Could not parse source: " + e.getMessage())); }
    });
    Map<Long, List<Quest>> byId = new HashMap<>();
    for (Quest q : quests) byId.computeIfAbsent(q.id(), ignored -> new ArrayList<>()).add(q);
    var links = new ArrayList<QuestLink>();
    for (Quest q : quests) {
      if (!q.supported() || q.sourceQuests().isEmpty()) continue;
      var claims = byId.get(q.id()).stream().filter(p -> !p.sourceQuests().isEmpty()).toList();
      if (claims.stream().anyMatch(p -> !p.supported() || !p.sourceQuests().equals(q.sourceQuests())
          || !java.util.Objects.equals(p.requiredCount(), q.requiredCount()))) {
        issues.add(new Issue(q.sourcePath(), q.sourceLine(), "Conflicting quest prerequisite variants: " + q.id())); continue;
      }
      if (q.sourceQuests().stream().anyMatch(id -> byId.containsKey(id)
          && byId.get(id).stream().anyMatch(reference -> !reference.supported()))) {
        issues.add(new Issue(q.sourcePath(), q.sourceLine(), "Known prerequisite has unsupported eligibility: " + q.id())); continue;
      }
      String kind;
      int n = q.sourceQuests().size(), required = q.requiredCount() == null ? n : q.requiredCount();
      if (required == 1) kind = "pre_single";
      else if (required == n && factionAlternatives(q.sourceQuests(), byId)) kind = "pre_single";
      else if (required == n && unconditionalReferences(q.sourceQuests(), byId)) kind = "pre_group";
      else {
        issues.add(new Issue(q.sourcePath(), q.sourceLine(), "Unrepresentable or conditional prerequisite group for " + q.id())); continue;
      }
      for (long id : q.sourceQuests()) links.add(new QuestLink(q.id(), id, kind, q.sourcePath(), q.sourceLine(),
          "AllTheThings Forever; " + q.detail() + "; " + kind));
    }
    return new Result(links, quests, issues);
  }

  private static boolean factionAlternatives(List<Long> ids, Map<Long, List<Quest>> quests) {
    if (ids.size() != 2) return false;
    var races = new LinkedHashSet<String>();
    for (long id : ids) {
      List<Quest> rows = quests.get(id);
      if (rows == null || rows.isEmpty() || rows.stream().anyMatch(q -> !q.supported())) return false;
      Set<String> variants = new LinkedHashSet<>(); for (Quest q : rows) variants.add(q.race());
      if (variants.size() != 1) return false;
      races.add(variants.iterator().next());
    }
    return races.equals(Set.of("ALLIANCE_ONLY", "HORDE_ONLY"));
  }
  private static boolean unconditionalReferences(List<Long> ids, Map<Long, List<Quest>> quests) {
    return ids.stream().allMatch(id -> quests.containsKey(id) && quests.get(id).stream()
        .allMatch(q -> q.supported() && q.race().equals("UNRESTRICTED")));
  }

  private static void walk(Object node, String path, List<String> lines, List<Quest> quests, List<Issue> issues, boolean inherited) {
    if (node instanceof Stmt.FunctionDef) return; // declaring a function does not execute its data calls
    if (node instanceof Stmt.Skipped skipped) { issues.add(new Issue(path, skipped.line(), "Unexecuted control-flow block: " + skipped.keyword())); return; }
    boolean unsupportedConstructor = false;
    if (node instanceof Expr.Call call && call.fn() instanceof Expr.Name name && Set.of("q", "quest").contains(name.name())) {
      Quest q = quest(call, path, lines);
      if (q != null && inherited) q = new Quest(q.id(), q.name(), q.sourceQuests(), q.requiredCount(), "UNKNOWN",
          q.timeline(), q.sourcePath(), q.sourceLine(), false, q.detail() + "; Inherited or dynamic container condition");
      unsupportedConstructor = q == null || !q.supported();
      if (q != null) { quests.add(q); if (!q.supported()) issues.add(new Issue(path, q.sourceLine(), q.detail())); }
      else issues.add(new Issue(path, call.line(), "Dynamic or unsupported quest constructor"));
    }
    boolean childConditional = inherited || unsupportedConstructor;
    if (node instanceof Expr.TableCtor table) for (TableEntry entry : table.entries()) {
      Set<String> harmlessContainerFields = Set.of("groups", "g", "name", "description", "lore", "icon", "maps", "mapID",
          "coords", "coord", "provider", "providers", "qg", "qgs", "lvl", "lvls", "sourceQuest", "sourceQuests", "sqreq", "sourceQuestNumRequired");
      if (entry instanceof TableEntry.Named field && !harmlessContainerFields.contains(field.name())) childConditional = true;
      if (entry instanceof TableEntry.Keyed field) {
        if (!(field.key() instanceof Expr.Literal literal && literal.value() instanceof LuaString key)
            || !harmlessContainerFields.contains(key.value())) childConditional = true;
      }
      if (entry instanceof TableEntry.Named field && Set.of("races", "r", "c", "classes", "timeline", "lockCriteria",
          "OnInit", "OnUpdate", "u", "customCollect", "requireSkill", "minReputation").contains(field.name())) childConditional = true;
      if (entry instanceof TableEntry.Keyed field && field.key() instanceof Expr.Literal l && l.value() instanceof LuaString key
          && Set.of("races", "r", "c", "classes", "timeline", "lockCriteria", "OnInit", "OnUpdate", "u", "customCollect",
          "requireSkill", "minReputation").contains(key.value())) childConditional = true;
    }
    if (node instanceof Expr.Call call && (!(call.fn() instanceof Expr.Name name)
        || !Set.of("maproot", "m", "n", "header", "q", "quest", "i", "o", "ach", "explorationHeader").contains(name.name())))
      childConditional = true;
    if (node instanceof Iterable<?> list) for (Object child : list) walk(child, path, lines, quests, issues, childConditional);
    else if (node != null && node.getClass().isRecord()) for (var field : node.getClass().getRecordComponents()) {
      try { walk(field.getAccessor().invoke(node), path, lines, quests, issues, childConditional); }
      catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
  }

  private static Quest quest(Expr.Call call, String path, List<String> lines) {
    if (call.args().size() != 2 || !(call.args().get(1) instanceof Expr.TableCtor table)) return null;
    Long id = integer(call.args().getFirst()); if (id == null) return null;
    Map<String, Expr> fields = new LinkedHashMap<>(); var problems = new ArrayList<String>();
    for (TableEntry entry : table.entries()) {
      String key = switch (entry) {
        case TableEntry.Named named -> named.name();
        case TableEntry.Keyed keyed -> keyed.key() instanceof Expr.Literal l && l.value() instanceof LuaString s ? s.value() : null;
        default -> null;
      };
      Expr value = switch (entry) { case TableEntry.Named n -> n.value(); case TableEntry.Keyed k -> k.value(); default -> null; };
      if (key == null) problems.add("Unsupported or dynamic quest field key");
      else if (fields.putIfAbsent(key, value) != null) problems.add("Duplicate field " + key);
    }
    Set<String> allowedFields = Set.of("sourceQuest", "sourceQuests", "sourceQuestNumRequired", "sqreq", "races", "timeline",
        "qg", "qgs", "providers", "provider", "coords", "coord", "maps", "mapID", "lvl", "lvls", "groups", "g", "name");
    for (String key : fields.keySet()) if (!allowedFields.contains(key)) problems.add("Unsupported quest field " + key);
    List<Long> prerequisites = List.of();
    if (fields.containsKey("sourceQuest") && fields.containsKey("sourceQuests")) problems.add("Both sourceQuest and sourceQuests");
    if (fields.containsKey("sourceQuest")) {
      Long single = integer(fields.get("sourceQuest"));
      if (single == null) problems.add("Dynamic sourceQuest"); else prerequisites = List.of(single);
    } else if (fields.containsKey("sourceQuests")) {
      prerequisites = ids(fields.get("sourceQuests")); if (prerequisites == null) { problems.add("Dynamic sourceQuests"); prerequisites = List.of(); }
    }
    Integer required = null;
    Expr threshold = fields.get("sourceQuestNumRequired");
    if (fields.containsKey("sqreq")) {
      if (threshold != null) problems.add("Both prerequisite threshold fields");
      threshold = fields.get("sqreq");
    }
    if (threshold != null) {
      Long number = integer(threshold);
      if (number == null || number > prerequisites.size()) problems.add("Invalid prerequisite threshold");
      else required = number.intValue();
    }
    String race = "UNRESTRICTED";
    if (fields.containsKey("races")) {
      Expr e = fields.get("races");
      if (e instanceof Expr.Name name && Set.of("ALLIANCE_ONLY", "HORDE_ONLY").contains(name.name())) race = name.name();
      else { race = "UNKNOWN"; problems.add("Unsupported race condition"); }
    }
    if (fields.containsKey("r")) { race = "UNKNOWN"; problems.add("Unsupported faction condition r"); }
    List<String> timeline = new ArrayList<>();
    if (fields.containsKey("timeline")) {
      if (fields.get("timeline") instanceof Expr.TableCtor t) for (TableEntry entry : t.entries()) {
        if (entry instanceof TableEntry.Positional p && p.value() instanceof Expr.Field f && f.target() instanceof Expr.Name n && n.name().equals("TIMELINE")) {
          timeline.add(f.name()); if (!f.name().equals("ADDED_1_60_1")) problems.add("Unsupported timeline " + f.name());
        } else problems.add("Dynamic timeline");
      } else problems.add("Dynamic timeline");
    }
    for (String condition : List.of("isBreadcrumb", "altQuests", "altQuestID", "lockCriteria", "DisablePartySync", "OnInit", "OnUpdate", "OnTooltip", "customCollect", "c", "classes", "requireSkill", "minReputation", "u", "description"))
      if (fields.containsKey(condition)) problems.add("Conditional field " + condition);
    String name = null;
    if (call.line() > 0 && call.line() <= lines.size()) {
      String line = lines.get(call.line() - 1); int comment = line.indexOf("--");
      if (comment >= 0) name = line.substring(comment + 2).strip();
    }
    String detail = problems.isEmpty() ? "sourceQuests=" + prerequisites + "; required=" + (required == null ? "all eligible" : required) + "; race=" + race
        : String.join("; ", problems);
    return new Quest(id, name, prerequisites, required, race, timeline, path, call.line(), problems.isEmpty(), detail);
  }
  private static Long integer(Expr expr) {
    return expr instanceof Expr.Literal l && l.value() instanceof LuaNumber n && n.isInteger()
        && n.value() > 0 && n.value() <= Integer.MAX_VALUE ? n.asLong() : null;
  }
  private static List<Long> ids(Expr expr) {
    if (!(expr instanceof Expr.TableCtor table)) return null;
    var out = new ArrayList<Long>();
    for (TableEntry e : table.entries()) {
      if (!(e instanceof TableEntry.Positional p)) return null;
      Long id = integer(p.value()); if (id == null || out.contains(id)) return null; out.add(id);
    }
    return out;
  }

  /** Unknown preprocessor expressions disable their whole branch; line numbers stay unchanged. */
  private static String preprocessor(String text, String path, List<Issue> issues) {
    record Gate(boolean parent, boolean condition, boolean known, boolean elseSeen) {}
    var stack = new java.util.ArrayDeque<Gate>(); boolean active = true;
    var out = new StringBuilder(); int line = 0;
    for (String raw : text.split("\\R", -1)) {
      line++; String directive = raw.strip();
      if (directive.matches("(?i)--\\s*#IF\\s+.*")) {
        String tag = directive.replaceFirst("(?i)--\\s*#IF\\s+", "").strip();
        boolean known = ACTIVE_TAGS.contains(tag) || INACTIVE_TAGS.contains(tag);
        boolean condition = ACTIVE_TAGS.contains(tag);
        stack.push(new Gate(active, condition, known, false)); active = active && known && condition;
        if (!known) issues.add(new Issue(path, line, "Unknown preprocessor condition: " + tag));
        out.append('\n');
      } else if (directive.matches("(?i)--\\s*#(?:ELSEIF|ELIF)\\b.*")) {
        throw new IllegalArgumentException("Unsupported preprocessor branch: " + directive);
      } else if (directive.matches("(?i)--\\s*#ELSE\\s*")) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Unmatched preprocessor ELSE");
        Gate g = stack.pop();
        if (g.elseSeen()) throw new IllegalArgumentException("Duplicate preprocessor ELSE");
        stack.push(new Gate(g.parent(), g.condition(), g.known(), true));
        active = g.parent() && g.known() && !g.condition(); out.append('\n');
      } else if (directive.matches("(?i)--\\s*#ENDIF.*")) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Unmatched preprocessor ENDIF");
        active = stack.pop().parent(); out.append('\n');
      } else out.append(active ? raw : "").append('\n');
    }
    if (!stack.isEmpty()) throw new IllegalArgumentException("Unclosed preprocessor condition");
    return out.toString();
  }
}
