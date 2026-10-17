package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveSource;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.RateSource;
import java.util.List;
import java.util.Locale;

/** Location descriptions, without the unreliable quantities required by a quest. */
public final class ObjectiveText {
  private ObjectiveText() {}

  /**
   * Removes clear required quantities. If a remaining number is ambiguous, omit that prose rather than
   * guessing; the structured verb, targets and location still describe the objective. Verified model names
   * and explicit step ordinals remain intact.
   */
  public static String description(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String text = raw.strip()
        .replaceAll("(?i)\\b(?:at least|at most|exactly|a total of)\\s+\\d+\\s+", "")
        .replaceAll("(?i)\\b(save|kill|slay|defeat|collect|bring|gather|obtain|rescue|heal|treat)\\s+\\d+\\s+", "$1 ")
        .replaceFirst("^\\d+\\s+(?=[\\p{L}])", "");
    String checked = text.replaceAll("(?i)\\bStep\\s+\\d+:", "")
        .replace("A-Me 01", "A-Me").replace("Draco-Incarcinatrix 900", "Draco-Incarcinatrix");
    return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}/-])\\d+(?![\\p{L}\\p{N}/-])")
        .matcher(checked).find() ? "" : text;
  }

  public static String rate(Double pct) {
    return pct == null ? "" : String.format(Locale.ROOT, pct >= 1 ? "%.0f %%" : "%.1f %%", pct);
  }

  public static String where(ObjectiveRow row) {
    if (row.instance() != null) return "Inside " + row.instance().displayName() + " — open dungeon page ›";
    if (row.verb() == ObjectiveVerb.ESCORT) return row.points().isEmpty()
        && row.sources().stream().allMatch(s -> s.spawns().isEmpty())
        ? "Escort — from the quest giver's spot" : "Escort — location marked on the map.";
    List<ObjectiveSource> sources = row.sources();
    if (row.verb() == ObjectiveVerb.COLLECT && !sources.isEmpty()) {
      boolean objects = sources.stream().allMatch(s -> s.ref().kind() == EntityKind.OBJECT);
      int shown = Math.min(sources.size(), objects ? 2 : 3);
      String names = sources.subList(0, shown).stream().map(s -> {
        String name = s.ref().displayName(s.name());
        return objects || s.dropPct() == null ? name : name + " " + rate(s.dropPct())
            + (RateSource.VMANGOS.equals(s.pctSource()) ? " (Vanilla rate)" : "");
      }).collect(java.util.stream.Collectors.joining(", "));
      return (objects ? "Found in " : "Drops from ") + names
          + (sources.size() > shown ? ", +" + (sources.size() - shown) + " more" : "");
    }
    var spawns = sources.stream().flatMap(s -> s.spawns().stream()).toList();
    if (!spawns.isEmpty()) {
      String zones = spawns.stream().map(s -> s.zoneName() == null ? "Zone #" + s.zoneId() : s.zoneName())
          .distinct().collect(java.util.stream.Collectors.joining(", "));
      return zones + " · " + spawns.size() + (spawns.size() == 1 ? " spawn" : " spawns")
          + (row.verb() == ObjectiveVerb.KILL && row.targets().size() > 1
              ? " of " + row.targets().size() + " NPCs" : "");
    }
    return row.points().isEmpty() ? "Source unknown — not in the quest database yet."
        : "Location marked on the map.";
  }
}
