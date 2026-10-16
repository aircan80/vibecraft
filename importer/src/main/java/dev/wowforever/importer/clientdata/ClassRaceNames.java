package dev.wowforever.importer.clientdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The "Classes:" and "Races:" requirement lines from {@code ChrClasses} and {@code ChrRaces}.
 *
 * <p>Masks are tested bit by bit, never by equality: a class's bit is {@code ID - 1}, a race's is
 * {@code ChrRaces.PlayableRaceBit} (bits 32 and up live in {@code AllowableRace_1}). Every {@code ChrClasses} row is
 * playable (Forever ships the nine classic classes); the playable races are the classic eight plus Forever's two
 * Skyborne races, as far as {@code ChrRaces} has them ({@code ChrRaces} also lists retail races Forever lacks).
 * A line is shown only when the allowed playable set is a non-empty strict subset of the playable set.
 */
public final class ClassRaceNames {
  /** Human, Orc, Dwarf, Night Elf, Undead, Tauren, Gnome, Troll, High Order Skyborne, Windshaper Skyborne. */
  static final Set<Long> PLAYABLE_RACES = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 95L, 96L);

  /** Playable classes and races by bit, in id order. */
  private final Map<Long, Entry> classes;
  private final Map<Long, Entry> races;

  private record Entry(int bit, String name) {}

  private ClassRaceNames(Map<Long, Entry> classes, Map<Long, Entry> races) {
    this.classes = classes;
    this.races = races;
  }

  /** @throws IllegalStateException when a table is missing or lacks a column read here */
  public static ClassRaceNames load(ClientDataSource src) {
    Map<Long, Entry> classes = new TreeMap<>();
    for (CsvTable.Row r : require(src.table("ChrClasses"), List.of("ID", "Name_lang")).rows()) {
      long id = r.longOr("ID", 0);
      if (id > 0 && id <= 64 && !r.get("Name_lang").isEmpty()) {
        classes.put(id, new Entry((int) id - 1, r.get("Name_lang")));
      }
    }
    Map<Long, Entry> races = new TreeMap<>();
    for (CsvTable.Row r : require(src.table("ChrRaces"), List.of("ID", "Name_lang", "PlayableRaceBit")).rows()) {
      long id = r.longOr("ID", 0);
      int bit = r.intOr("PlayableRaceBit", -1);
      if (PLAYABLE_RACES.contains(id) && bit >= 0 && bit < 64) {
        races.put(id, new Entry(bit, r.get("Name_lang")));
      }
    }
    return new ClassRaceNames(classes, races);
  }

  /** "Classes: Warrior, Hunter", or empty for no restriction ({@code -1}, 0, null or every playable class). */
  public Optional<String> classes(Integer mask) {
    if (mask == null) {
      return Optional.empty();
    }
    long bits = mask & 0xFFFF_FFFFL;
    return line("Classes: ", classes, bits, 0);
  }

  /**
   * "Races: Human, Dwarf", or empty for no restriction. {@code low} is {@code AllowableRace_0} (bits 0 to 31, as the
   * signed value the client stores), {@code high} {@code AllowableRace_1}; a null half allows all of its bits.
   */
  public Optional<String> races(Long low, Long high) {
    if (low == null && high == null) {
      return Optional.empty();
    }
    long lowBits = low == null ? 0xFFFF_FFFFL : low & 0xFFFF_FFFFL;
    long highBits = high == null ? 0xFFFF_FFFFL : high & 0xFFFF_FFFFL;
    return line("Races: ", races, lowBits, highBits);
  }

  private static Optional<String> line(String prefix, Map<Long, Entry> playable, long low, long high) {
    List<String> allowed = new ArrayList<>();
    for (Entry e : playable.values()) {
      long half = e.bit() < 32 ? low : high;
      if ((half & (1L << (e.bit() % 32))) != 0) {
        allowed.add(e.name());
      }
    }
    if (allowed.isEmpty() || allowed.size() == playable.size()) {
      return Optional.empty();
    }
    return Optional.of(prefix + String.join(", ", allowed));
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
