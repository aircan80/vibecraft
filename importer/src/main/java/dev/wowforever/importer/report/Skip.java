package dev.wowforever.importer.report;

import dev.wowforever.importer.entity.EntityKind;

/**
 * One piece of source data the importer could not apply.
 *
 * @param layer    the correction layer (or other stage) that held the data
 * @param kind     the entity kind addressed
 * @param entityId the entity id, or 0 when no valid id was available
 * @param field    the field name; {@code "<id>"} for a dropped row, {@code "<layer>"} for a dropped layer
 * @param reason   why it was not applied
 */
public record Skip(String layer, EntityKind kind, long entityId, String field, String reason) {

  /** The field name used when a whole correction row could not be applied. */
  public static final String ROW = "<id>";
  /** The field name used when a whole correction layer could not be applied. */
  public static final String LAYER = "<layer>";

  @Override
  public String toString() {
    return layer + " " + kind + " " + entityId + " " + field + ": " + reason;
  }
}
