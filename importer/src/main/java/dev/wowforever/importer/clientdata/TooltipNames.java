package dev.wowforever.importer.clientdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The names a tooltip refers to: subclass display names ({@code ItemSubClass.DisplayName_lang}, the short "Sword"
 * rather than the verbose "One-Handed Swords"), skills ({@code SkillLine.DisplayName_lang}), factions
 * ({@code Faction.Name_lang}), classes and races ({@link ClassRaceNames}) and item names (for set pieces).
 */
public final class TooltipNames {
  private final Map<Integer, Map<Integer, String>> subclasses;
  private final Map<Long, String> skills;
  private final Map<Long, String> factions;
  private final ClassRaceNames classRace;
  private final Map<Long, String> items;

  TooltipNames(Map<Integer, Map<Integer, String>> subclasses, Map<Long, String> skills, Map<Long, String> factions,
      ClassRaceNames classRace, Map<Long, String> items) {
    this.subclasses = subclasses;
    this.skills = skills;
    this.factions = factions;
    this.classRace = classRace;
    this.items = items;
  }

  /**
   * @param itemNames item names by id: the client's where known, Questie's otherwise
   * @throws IllegalStateException when a table is missing or lacks a column read here
   */
  public static TooltipNames load(ClientDataSource src, Map<Long, String> itemNames) {
    Map<Integer, Map<Integer, String>> subclasses = new HashMap<>();
    for (CsvTable.Row r : require(src.table("ItemSubClass"), List.of("ClassID", "SubClassID", "DisplayName_lang"))
        .rows()) {
      String name = r.get("DisplayName_lang");
      if (!name.isEmpty() && r.getInt("ClassID").isPresent() && r.getInt("SubClassID").isPresent()) {
        subclasses.computeIfAbsent(r.getInt("ClassID").getAsInt(), k -> new HashMap<>())
            .putIfAbsent(r.getInt("SubClassID").getAsInt(), name);
      }
    }
    return new TooltipNames(subclasses, names(src.table("SkillLine"), "DisplayName_lang"),
        names(src.table("Faction"), "Name_lang"), ClassRaceNames.load(src), Map.copyOf(itemNames));
  }

  public Optional<String> subclass(Integer classId, Integer subclassId) {
    if (classId == null || subclassId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(subclasses.getOrDefault(classId, Map.of()).get(subclassId));
  }

  public Optional<String> skill(long id) {
    return Optional.ofNullable(skills.get(id));
  }

  public Optional<String> faction(long id) {
    return Optional.ofNullable(factions.get(id));
  }

  public ClassRaceNames classRace() {
    return classRace;
  }

  /** The item's name, or {@code Item #id}. */
  public String item(long id) {
    String n = items.get(id);
    return n == null || n.isBlank() ? "Item #" + id : n;
  }

  private static Map<Long, String> names(CsvTable t, String column) {
    require(t, List.of("ID", column));
    Map<Long, String> out = new HashMap<>();
    for (CsvTable.Row r : t.rows()) {
      if (r.getLong("ID").isPresent() && !r.get(column).isEmpty()) {
        out.put(r.getLong("ID").getAsLong(), r.get(column));
      }
    }
    return out;
  }

  private static CsvTable require(CsvTable t, List<String> columns) {
    for (String col : columns) {
      if (!t.hasColumn(col)) {
        throw new IllegalStateException("client table " + t.source() + " has no column " + col);
      }
    }
    return t;
  }
}
