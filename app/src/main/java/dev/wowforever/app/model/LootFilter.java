package dev.wowforever.app.model;

import java.util.ArrayList;
import java.util.List;

/** Filters source-qualified Forever fields. Class is restriction compatibility, not armor proficiency. */
public record LootFilter(Integer classId, Integer inventoryType) {
  public record Result(List<LootRow> rows, int excluded, int unknown, int unknownClass, int unknownSlot) {}

  public Result apply(List<LootRow> rows) {
    var matches = new ArrayList<LootRow>();
    int excluded = 0, unknown = 0, classes = 0, slots = 0;
    for (LootRow row : rows) {
      boolean classUnknown = classId != null && row.allowableClass() == null;
      boolean slotUnknown = inventoryType != null && row.inventoryType() == null;
      boolean classMismatch = classId != null && !classUnknown && row.allowableClass() != -1
          && (row.allowableClass() & (1 << (classId - 1))) == 0;
      boolean slotMismatch = inventoryType != null && !slotUnknown
          && !sameSlot(inventoryType, row.inventoryType());
      // A known mismatch is excluded even if the other selected field is unknown.
      if (classMismatch || slotMismatch) excluded++;
      else if (classUnknown || slotUnknown) {
        unknown++;
        if (classUnknown) classes++;
        if (slotUnknown) slots++;
      } else matches.add(row);
    }
    return new Result(List.copyOf(matches), excluded, unknown, classes, slots);
  }
  private static boolean sameSlot(int selected, int actual) {
    return selected == actual || selected == 5 && actual == 20 || selected == 15 && actual == 26;
  }

}
