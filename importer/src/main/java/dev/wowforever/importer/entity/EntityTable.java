package dev.wowforever.importer.entity;

import dev.wowforever.importer.schema.EntitySchema;
import dev.wowforever.lua.LuaNil;
import dev.wowforever.lua.LuaValue;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * All rows of one entity kind, keyed by id. A row holds one value per schema field; element
 * {@code i - 1} is field index {@code i}, and absent fields are {@link LuaNil#NIL}.
 */
public final class EntityTable {
  private final EntityKind kind;
  private final EntitySchema schema;
  private final SortedMap<Long, LuaValue[]> rows = new TreeMap<>();

  public EntityTable(EntityKind kind, EntitySchema schema) {
    this.kind = kind;
    this.schema = schema;
  }

  public EntityKind kind() {
    return kind;
  }

  public EntitySchema schema() {
    return schema;
  }

  /** The live rows, ordered by id. */
  public SortedMap<Long, LuaValue[]> rows() {
    return rows;
  }

  public boolean contains(long id) {
    return rows.containsKey(id);
  }

  /** Adds a row with every field nil; the id must not exist yet. */
  public LuaValue[] create(long id) {
    if (rows.containsKey(id)) {
      throw new IllegalArgumentException(kind + " " + id + " already exists");
    }
    LuaValue[] row = new LuaValue[schema.fieldCount()];
    Arrays.fill(row, LuaNil.NIL);
    rows.put(id, row);
    return row;
  }

  public LuaValue get(long id, String field) {
    return row(id)[schema.index(field) - 1];
  }

  public void set(long id, String field, LuaValue v) {
    row(id)[schema.index(field) - 1] = v;
  }

  public int size() {
    return rows.size();
  }

  private LuaValue[] row(long id) {
    LuaValue[] row = rows.get(id);
    if (row == null) {
      throw new NoSuchElementException("no " + kind + " " + id);
    }
    return row;
  }
}
