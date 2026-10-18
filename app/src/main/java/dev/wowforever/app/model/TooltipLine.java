package dev.wowforever.app.model;

/**
 * One line of an item's tooltip as the importer stored it: {@code kind} (name, binding, stat, use, ...),
 * {@code text} (a tab separates left and right text) and {@code colour} ({@code #rrggbb}, or null for the default
 * text colour). {@code itemId} is the item a {@code set_item} line names (resolved by name within the item's set),
 * null for other lines and for a piece the database does not have.
 */
public record TooltipLine(String kind, String text, String colour, Long itemId, SpellTextEvidence evidence) {
  public TooltipLine { if (evidence == null) evidence = SpellTextEvidence.unknown(); }
  public TooltipLine(String kind, String text, String colour, Long itemId) {
    this(kind, text, colour, itemId, SpellTextEvidence.unknown());
  }
  /** A line that names no item. */
  public TooltipLine(String kind, String text, String colour) {
    this(kind, text, colour, null);
  }
}
