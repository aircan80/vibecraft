package dev.wowforever.importer.drops;

/**
 * A drop percentage and its source: questie-correction, vmangos, atlasloot-override, questie-wowhead or atlasloot.
 */
public record DropRate(double percent, String source) {}
