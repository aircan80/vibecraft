package dev.wowforever.importer.schema;

/** Structural shape of a table field, from a meta file's {@code structures} table. */
public enum Shape {
  IDARRAY,
  STRINGARRAY,
  PAIR,
  PAIRS,
  QUESTGIVERS,
  OBJECTIVES,
  SPAWNLIST,
  WAYPOINTLIST,
  TRIGGER,
  EXTRAOBJECTIVES
}
