package dev.wowforever.app.ui;

import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.ZoneSummary;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Line 2 of a master list row: the kind and a few key facts, joined by {@code " · "}; unknown facts are left out.
 * A stop-gap until the shared labels of the search ranking task replace it.
 */
final class Subtitles {
  private Subtitles() {}

  /** {@code "Armor · Cloth · iLvl 19 · Req 14"}; {@code "Item"} when nothing is known. */
  static String item(ItemSummary i) {
    StringJoiner s = joiner();
    add(s, i.className());
    add(s, i.subclassName());
    addLevel(s, "iLvl ", i.itemLevel());
    addLevel(s, "Req ", i.requiredLevel());
    return s.length() == 0 ? "Item" : s.toString();
  }

  /** {@code "NPC · Level 11 · Elite · Elwynn Forest"}. */
  static String npc(NpcSummary n) {
    StringJoiner s = joiner().add("NPC");
    add(s, levels(n.minLevel(), n.maxLevel()));
    Integer rank = n.rank();
    add(s, rank == null || rank == 0 ? null : Columns.rankText(rank));
    add(s, n.zoneName());
    return s.toString();
  }

  /** {@code "Object · Elwynn Forest"}. */
  static String object(ObjectSummary o) {
    StringJoiner s = joiner().add("Object");
    add(s, o.zoneName());
    return s.toString();
  }

  /** {@code "Quest · Level 12 · Westfall"}. */
  static String quest(QuestSummary q) {
    StringJoiner s = joiner().add("Quest");
    addLevel(s, "Level ", q.questLevel());
    add(s, q.zoneName());
    return s.toString();
  }

  /** {@code "Subzone · Elwynn Forest"}: the zone kind, then the parent zone. */
  static String zone(ZoneSummary z) {
    StringJoiner s = joiner();
    add(s, z.kind() == null || z.kind().isEmpty() ? "Zone"
        : z.kind().substring(0, 1).toUpperCase(Locale.ROOT) + z.kind().substring(1));
    add(s, z.parentName());
    return s.toString();
  }

  /** {@code "Raid (40) · Level 55–60 · Burning Steppes"}. */
  static String instance(InstanceSummary i) {
    StringJoiner s = joiner();
    String type = Columns.contentTypeText(i.contentType());
    add(s, type != null ? type : "Instance");
    add(s, levels(i.levelMin(), i.levelMax()));
    add(s, i.zoneName());
    return s.toString();
  }

  private static StringJoiner joiner() {
    return new StringJoiner(" · ");
  }

  private static void add(StringJoiner s, String part) {
    if (part != null && !part.isBlank()) {
      s.add(part);
    }
  }

  private static void addLevel(StringJoiner s, String prefix, Integer level) {
    if (level != null && level > 0) {
      s.add(prefix + level);
    }
  }

  private static String levels(Integer min, Integer max) {
    String text = new Columns.Levels(min, max).toString();
    return text.isEmpty() ? null : "Level " + text;
  }
}
