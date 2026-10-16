package dev.wowforever.app.model;

import java.util.List;

/** A node of the item class tree: a class ({@code subclassId == -1}) with its subclasses as children. */
public record ItemClassNode(int classId, String name, List<ItemClassNode> children, int subclassId) {
  /** The {@code subclassId} of a class node. */
  public static final int CLASS_NODE = -1;

  public ItemClassNode {
    children = List.copyOf(children);
  }

  public boolean isClass() {
    return subclassId == CLASS_NODE;
  }
}
