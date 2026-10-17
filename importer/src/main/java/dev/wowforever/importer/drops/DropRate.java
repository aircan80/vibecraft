package dev.wowforever.importer.drops;

/**
 * A drop percentage and its source: questie-correction, atlasloot-override, questie-wowhead, vmangos or atlasloot.
 * {@code questOnly} marks a rate that only applies while the looter is on the quest (a VMaNGOS row with a negative
 * {@code ChanceOrQuestChance}); every other source records observed or configured rates for every kill.
 */
public record DropRate(double percent, String source, boolean questOnly) {
  public DropRate(double percent, String source) {
    this(percent, source, false);
  }
}
