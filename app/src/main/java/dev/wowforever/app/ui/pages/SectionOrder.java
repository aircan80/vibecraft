package dev.wowforever.app.ui.pages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The fixed order of the sections of each kind of page (the shared page skeleton): a page shows the sections it has
 * in its list's order, and any section the list does not name after those, in the order the page built them. Section
 * titles are the keys, as {@link SourcesPage#section} looks them up.
 */
public final class SectionOrder {
  /** The item page; "Dropped by" holds the instance loot lists too. A later task fills "Quests". */
  public static final List<String> ITEM = List.of("Dropped by", "Sold by", "Quest reward", "Contained in", "Quests");
  /** The NPC page. "Starts quests" and "Ends quests" follow until a later task merges them into "Quests". */
  public static final List<String> NPC = List.of("Drops", "Sells", "Quests", "Location");
  /** The zone page: "Map" and "Dungeons & raids" are the map and the row of instance links. */
  public static final List<String> ZONE = List.of("Map", "Dungeons & raids", "Quests", "NPCs", "Objects");
  /**
   * The dungeon or raid page, laid out in two panes: Encounters over Other loot lists on the left, Loot then Quests on
   * the right (the instance page sorts the right-hand pane by this order).
   */
  public static final List<String> INSTANCE = List.of("Encounters", "Loot", "Other loot lists", "Quests");

  private SectionOrder() {}

  /**
   * {@code blocks} in {@code order} by their {@code title}: the ones {@code order} names in its order, then the rest
   * in the order given.
   */
  public static <T> List<T> sort(List<String> order, List<T> blocks, Function<T, String> title) {
    List<T> sorted = new ArrayList<>(blocks);
    sorted.sort(Comparator.comparingInt(b -> rank(order, title.apply(b)))); // stable: the rest keep their order
    return sorted;
  }

  /** Where {@code title} goes in {@code order}: its index, or after every title the order names. */
  static int rank(List<String> order, String title) {
    int i = order.indexOf(title);
    return i < 0 ? order.size() : i;
  }

  /** {@code order} with {@code title} right after {@code anchor} (at the end when {@code anchor} is not in it). */
  public static List<String> after(List<String> order, String anchor, String title) {
    List<String> out = new ArrayList<>(order);
    int at = out.indexOf(anchor);
    out.add(at < 0 ? out.size() : at + 1, title);
    return List.copyOf(out);
  }
}
