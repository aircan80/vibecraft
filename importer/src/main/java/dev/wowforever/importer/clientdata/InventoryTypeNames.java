package dev.wowforever.importer.clientdata;

import java.util.Map;
import java.util.Optional;

/** {@code InventoryType} to the slot name the tooltip shows (a robe is "Chest", a shield "Off Hand"). */
public final class InventoryTypeNames {
  public static final int BAG = 18;
  public static final int QUIVER = 27;

  private static final Map<Integer, String> NAMES = Map.ofEntries(
      Map.entry(1, "Head"),
      Map.entry(2, "Neck"),
      Map.entry(3, "Shoulder"),
      Map.entry(4, "Shirt"),
      Map.entry(5, "Chest"),
      Map.entry(6, "Waist"),
      Map.entry(7, "Legs"),
      Map.entry(8, "Feet"),
      Map.entry(9, "Wrist"),
      Map.entry(10, "Hands"),
      Map.entry(11, "Finger"),
      Map.entry(12, "Trinket"),
      Map.entry(13, "One-Hand"),
      Map.entry(14, "Off Hand"),
      Map.entry(15, "Ranged"),
      Map.entry(16, "Back"),
      Map.entry(17, "Two-Hand"),
      Map.entry(BAG, "Bag"),
      Map.entry(19, "Tabard"),
      Map.entry(20, "Chest"),
      Map.entry(21, "Main Hand"),
      Map.entry(22, "Off Hand"),
      Map.entry(23, "Held In Off-hand"),
      Map.entry(24, "Ammo"),
      Map.entry(25, "Thrown"),
      Map.entry(26, "Ranged"),
      Map.entry(QUIVER, "Quiver"),
      Map.entry(28, "Relic"));

  private InventoryTypeNames() {}

  /** Empty for 0 (not equippable) and unknown types. */
  public static Optional<String> name(Integer inventoryType) {
    return Optional.ofNullable(inventoryType == null ? null : NAMES.get(inventoryType));
  }
}
