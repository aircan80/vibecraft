package dev.wowforever.importer;

/** Explicit core importer choices. Questie remains required; native, ATT and server inputs remain in ImportOptions. */
public record CoreSourceSelection(boolean atlasLoot) {
  public static final CoreSourceSelection DEFAULT = new CoreSourceSelection(true);
  public static final CoreSourceSelection QUESTIE_ONLY = new CoreSourceSelection(false);
}
