package dev.wowforever.app.model;

import java.util.List;

/**
 * A node of the item class tree: a class ({@code subclassId == -1}) with its subclasses as children. A class
 * whose items include subclasses missing from the static subclass table gets a last child named
 * {@code "(other)"} with {@code subclassId == -2}.
 */
public record ItemClassNode(int classId, String name, List<ItemClassNode> children, int subclassId) {
  /** The {@code subclassId} of a class node. */
  public static final int CLASS_NODE = -1;
  /** The {@code subclassId} of the "(other)" node: items whose subclass is unknown or null. */
  public static final int OTHER_SUBCLASSES = -2;

  public ItemClassNode {
    children = List.copyOf(children);
  }

  public boolean isClass() {
    return subclassId == CLASS_NODE;
  }

  public boolean isOther() {
    return subclassId == OTHER_SUBCLASSES;
  }
}
