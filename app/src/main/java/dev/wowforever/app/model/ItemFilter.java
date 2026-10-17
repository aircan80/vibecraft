package dev.wowforever.app.model;

/**
 * An item list: items of class {@code classId} (null: every class) and subclass {@code subclassId} (null: every
 * subclass; {@link ItemClassNode#OTHER_SUBCLASSES}: subclasses missing from the subclass table) whose item level
 * is at least {@code minLevel} and at most {@code maxLevel} (null: no bound; an item without a level is left out
 * once either bound is set) and whose quality is {@code quality} (0 poor … 6 artifact; null: any quality, an item
 * without client data has none and is left out once a quality is set).
 */
public record ItemFilter(Integer classId, Integer subclassId, Integer minLevel, Integer maxLevel, Integer quality)
    implements ListFilter {
  /** Every item. */
  public static final ItemFilter ALL = new ItemFilter(null, null, null, null, null);
  /** The class id v0.1 item class links use for every item, whatever its class. */
  public static final int ALL_CLASSES = -1;

  public ItemFilter {
    if (subclassId != null && classId == null) {
      throw new IllegalArgumentException("A subclass filter needs its class: subclass " + subclassId);
    }
  }

  /**
   * The filter of a v0.1 item class link: {@code classId} {@link #ALL_CLASSES} for every item, {@code subclassId}
   * {@link ItemClassNode#CLASS_NODE} for the whole class.
   */
  public static ItemFilter ofClass(int classId, int subclassId) {
    if (classId == ALL_CLASSES) {
      return ALL;
    }
    return new ItemFilter(classId, subclassId == ItemClassNode.CLASS_NODE ? null : subclassId, null, null, null);
  }

  /** This filter with another class and subclass. */
  public ItemFilter withClass(Integer classId, Integer subclassId) {
    return new ItemFilter(classId, subclassId, minLevel, maxLevel, quality);
  }

  /** This filter with another quality (null: any). */
  public ItemFilter withQuality(Integer quality) {
    return new ItemFilter(classId, subclassId, minLevel, maxLevel, quality);
  }

  /** This filter with other item level bounds. */
  public ItemFilter withLevels(Integer minLevel, Integer maxLevel) {
    return new ItemFilter(classId, subclassId, minLevel, maxLevel, quality);
  }
}
