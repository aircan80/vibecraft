package dev.wowforever.importer.schema;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Field layout of one QuestieDB entity kind: positional index, name, storage type and shape. */
public final class EntitySchema {
  private final String entity;
  private final List<String> names;
  private final List<FieldType> types;
  private final List<Optional<Shape>> shapes;
  private final Map<String, Integer> indexByName = new HashMap<>();

  /**
   * @param names  field names in index order (element 0 is field 1)
   * @param types  storage types in index order
   * @param shapes shapes in index order, {@code null} for scalar fields
   */
  public EntitySchema(String entity, List<String> names, List<FieldType> types, List<Shape> shapes) {
    if (names.size() != types.size() || names.size() != shapes.size()) {
      throw new IllegalArgumentException("names, types and shapes differ in length for " + entity);
    }
    this.entity = entity;
    this.names = List.copyOf(names);
    this.types = List.copyOf(types);
    this.shapes = shapes.stream().map(Optional::ofNullable).toList();
    for (int i = 0; i < names.size(); i++) {
      if (indexByName.put(names.get(i), i + 1) != null) {
        throw new IllegalArgumentException("duplicate field " + names.get(i) + " in " + entity);
      }
    }
  }

  public static EntitySchema fromMeta(Path metaFile) {
    return MetaLoader.load(metaFile);
  }

  /** The meta file's {@code entity} name, e.g. {@code "Item"}. */
  public String entity() {
    return entity;
  }

  public int fieldCount() {
    return names.size();
  }

  /** The 1-based index of a field. */
  public int index(String name) {
    Integer i = indexByName.get(name);
    if (i == null) {
      throw new IllegalArgumentException("unknown " + entity + " field: " + name);
    }
    return i;
  }

  public boolean has(String name) {
    return indexByName.containsKey(name);
  }

  public String name(int index) {
    return names.get(checked(index) - 1);
  }

  public FieldType type(int index) {
    return types.get(checked(index) - 1);
  }

  public Optional<Shape> shape(int index) {
    return shapes.get(checked(index) - 1);
  }

  /** Field names in index order. */
  public List<String> names() {
    return names;
  }

  private int checked(int index) {
    if (index < 1 || index > names.size()) {
      throw new IllegalArgumentException(entity + " has no field index " + index + " (1.." + names.size() + ")");
    }
    return index;
  }

  @Override
  public String toString() {
    return "EntitySchema[" + entity + ", " + names + "]";
  }
}
