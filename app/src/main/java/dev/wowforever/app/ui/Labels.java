package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data values in words for subtitles and headers: content types, zone kinds, NPC ranks, level ranges and the
 * one-line facts of each kind of entity, joined by {@code " · "}. Unknown parts are left out; no method prints
 * {@code "null"}, {@code "iLvl 1"} or a raw enum value such as {@code RAID40}.
 */
public final class Labels {
  private static final String SEPARATOR = " · ";
  /** {@code "Requires <skill> (<rank>)"}, the rank optional. */
  private static final Pattern REQUIRES = Pattern.compile("(?:Requires\\s+)?(.+?)(?:\\s*\\((\\d+)\\))?");
  /** The classes of the game by class id. */
  private static final Map<Integer, String> CLASSES = new TreeMap<>(Map.of(1, "Warrior", 2, "Paladin", 3, "Hunter",
      4, "Rogue", 5, "Priest", 7, "Shaman", 8, "Mage", 9, "Warlock", 11, "Druid"));
  /** The class mask of every class of the game. */
  private static final int ALL_CLASSES = CLASSES.keySet().stream().mapToInt(id -> 1 << (id - 1)).sum();

  private Labels() {}

  /**
   * An entity as {@code "item:19019"}, {@code "npc:448"} and so on (what the search box reads back when pasted);
   * empty for a kind without such a text (a boss).
   */
  public static Optional<String> idText(EntityRef ref) {
    return Optional.ofNullable(ref.kind().dbKind()).map(k -> k + ":" + ref.id());
  }

  /** {@code RAID40} → Raid (40), {@code DUNGEON} → Dungeon, anything else title-cased; null when blank. */
  public static String contentType(String contentType) {
    if (isBlank(contentType)) {
      return null;
    }
    String upper = contentType.strip().toUpperCase(Locale.ROOT);
    if (upper.equals("DUNGEON")) {
      return "Dungeon";
    }
    if (upper.startsWith("RAID")) {
      String size = upper.substring("RAID".length());
      if (size.isEmpty()) {
        return "Raid";
      }
      if (size.chars().allMatch(Character::isDigit)) {
        return "Raid (" + size + ")";
      }
    }
    return titleCase(contentType);
  }

  /** The {@code zone.kind} in words: raid → Raid zone, subzone → Sub-zone, unknown → Area; null when blank. */
  public static String zoneKind(String kind) {
    if (isBlank(kind)) {
      return null;
    }
    return switch (kind.strip().toLowerCase(Locale.ROOT)) {
      case "raid" -> "Raid zone";
      case "dungeon" -> "Dungeon";
      case "city" -> "City";
      case "continent" -> "Continent";
      case "subzone" -> "Sub-zone";
      case "zone" -> "Zone";
      case "unknown" -> "Area";
      default -> titleCase(kind);
    };
  }

  /** NPC rank: 1 Elite, 2 Rare Elite, 3 Boss, 4 Rare; empty for normal (0), unknown and null. */
  public static String rank(Integer rank) {
    if (rank == null) {
      return "";
    }
    return switch (rank) {
      case 1 -> "Elite";
      case 2 -> "Rare Elite";
      case 3 -> "Boss";
      case 4 -> "Rare";
      default -> "";
    };
  }

  /** {@code "Level 10–20"}, {@code "Level 60"} when equal or only one is known; null when neither is above 0. */
  public static String levels(Integer min, Integer max) {
    Integer low = positive(min) != null ? positive(min) : positive(max);
    Integer high = positive(max) != null ? positive(max) : positive(min);
    if (low == null) {
      return null;
    }
    return low.equals(high) ? "Level " + low : "Level " + low + "–" + high;
  }

  /**
   * {@code "Mail · iLvl 37 · Req 32"}: the subclass, or the class when the subclass is unknown or named like it
   * ("Quest" reads "Quest item", so a quest item is not taken for a quest); the item level and required level only
   * when above 1.
   */
  public static String itemSubtitle(String className, String subclassName, Integer itemLevel, Integer requiredLevel) {
    StringJoiner s = joiner();
    String kind = isBlank(subclassName) || subclassName.equals(className) ? className : subclassName;
    add(s, "Quest".equals(kind) ? "Quest item" : kind);
    add(s, above1(itemLevel) ? "iLvl " + itemLevel : null);
    add(s, above1(requiredLevel) ? "Req " + requiredLevel : null);
    return s.toString();
  }

  /**
   * The skill of a tooltip's required-skill line: {@code "Requires Leatherworking (65)"} → {@code "Leatherworking
   * 65"}, a line without a rank → the skill; null for a null or blank line.
   */
  public static String skill(String requiresLine) {
    if (isBlank(requiresLine)) {
      return null;
    }
    Matcher m = REQUIRES.matcher(requiresLine.strip());
    if (m.matches()) {
      return m.group(2) == null ? m.group(1) : m.group(1) + " " + m.group(2);
    }
    return requiresLine.strip();
  }

  /**
   * The classes of a client class mask ({@code AllowableClass}, bit {@code id - 1} per class id), in class id order
   * ({@code "Warrior, Paladin"}); null when the item is not restricted: a null, zero or negative mask, or one that
   * allows every class of the game.
   */
  public static String classes(Integer mask) {
    if (mask == null || mask <= 0 || (mask & ALL_CLASSES) == ALL_CLASSES) {
      return null;
    }
    StringJoiner names = new StringJoiner(", ");
    CLASSES.forEach((id, name) -> {
      if ((mask & (1 << (id - 1))) != 0) {
        names.add(name);
      }
    });
    return names.length() == 0 ? null : names.toString();
  }

  /** {@code "Elite · Level 34–35 · Scarlet Monastery"}. */
  public static String npcSubtitle(Integer rank, Integer minLevel, Integer maxLevel, String zoneName) {
    return join(rank(rank), levels(minLevel, maxLevel), zoneName);
  }

  /** {@code "Raid (40) · Level 60–63 · Burning Steppes"}; {@code "Instance"} stands in for an unknown type. */
  public static String instanceSubtitle(String contentType, Integer levelMin, Integer levelMax, String zoneName) {
    String type = contentType(contentType);
    return join(type != null ? type : EntityKind.INSTANCE.label(), levels(levelMin, levelMax), zoneName);
  }

  /** {@code "Dungeon · Westfall"}: the zone kind in words ({@code "Zone"} when unknown), then the parent zone. */
  public static String zoneSubtitle(String kind, String parentName) {
    String words = zoneKind(kind);
    return join(words != null ? words : EntityKind.ZONE.label(), parentName);
  }

  /** {@code "Level 10 · Elwynn Forest"}. */
  public static String questSubtitle(Integer level, String zoneName) {
    return join(levels(level, level), zoneName);
  }

  /** The object's zone, or empty. */
  public static String objectSubtitle(String zoneName) {
    return join(zoneName);
  }

  /**
   * {@code facts} led by the kind where they do not name it themselves: NPC, quest and object facts get
   * {@code "NPC · "}, {@code "Quest · "} or {@code "Object · "}; empty facts become the kind label.
   */
  public static String withKind(EntityKind kind, String facts) {
    return switch (kind) {
      case NPC, QUEST, OBJECT -> join(kind.label(), facts);
      default -> isBlank(facts) ? kind.label() : facts;
    };
  }

  /**
   * The part of an instance's AtlasLoot {@code key} its {@code name} does not already say: the key split at
   * CamelCase and punctuation, without the words that make up the name (also run together, as in {@code Ruinsof})
   * and without "the" and "of". {@code ScarletMonasteryLibrary} of Scarlet Monastery → Library,
   * {@code LowerBlackrockSpire} of Blackrock Spire → Lower; null when nothing is left, the key is blank or it is a
   * client dungeon's ({@link InstanceSummary#CLIENT_KEY_PREFIX}).
   */
  public static String instanceWing(String name, String key) {
    if (isBlank(key) || key.startsWith(InstanceSummary.CLIENT_KEY_PREFIX)) {
      return null;
    }
    Set<String> nameWords = new HashSet<>(List.of("the", "of"));
    if (name != null) {
      for (String word : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
        if (!word.isEmpty()) {
          nameWords.add(word);
        }
      }
    }
    StringJoiner wing = new StringJoiner(" ");
    for (String word : key.strip().split("(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})|[^\\p{L}\\p{N}]+")) {
      if (!word.isEmpty() && !madeOf(word.toLowerCase(Locale.ROOT), nameWords)) {
        wing.add(word);
      }
    }
    return wing.length() == 0 ? null : wing.toString();
  }

  /** {@code "Scarlet Monastery › Library"}: the name with its {@link #instanceWing}, or the name alone. */
  public static String instanceTitle(String name, String key) {
    String wing = instanceWing(name, key);
    return wing == null ? name : name + " › " + wing;
  }

  /** {@code subtitle} with {@code " · ×4"} appended when {@code count} is above 1. */
  public static String withCount(String subtitle, int count) {
    return count > 1 ? join(subtitle, "×" + count) : subtitle;
  }

  /** The non-blank {@code parts} joined by {@code " · "}; empty when none is. */
  public static String join(String... parts) {
    StringJoiner s = joiner();
    for (String part : parts) {
      add(s, part);
    }
    return s.toString();
  }

  private static StringJoiner joiner() {
    return new StringJoiner(SEPARATOR);
  }

  private static void add(StringJoiner s, String part) {
    if (!isBlank(part)) {
      s.add(part.strip());
    }
  }

  /** Whether {@code word} is one or more of {@code words} run together. */
  private static boolean madeOf(String word, Set<String> words) {
    if (word.isEmpty()) {
      return true;
    }
    for (String w : words) {
      if (word.startsWith(w) && madeOf(word.substring(w.length()), words)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static boolean above1(Integer level) {
    return level != null && level > 1;
  }

  /** A level, or null when unknown (null, 0 or below). */
  static Integer positive(Integer level) {
    return level != null && level > 0 ? level : null;
  }

  /** {@code WORLD_BOSS} → World Boss: words split at underscores and spaces, each capitalised. */
  private static String titleCase(String raw) {
    StringJoiner words = new StringJoiner(" ");
    for (String word : raw.strip().split("[_\\s]+")) {
      if (!word.isEmpty()) {
        words.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return words.toString();
  }
}
