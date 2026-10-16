package dev.wowforever.app.ui;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * The left-hand browse tree: item classes, zones (for NPC, object and quest lists), all zones and all
 * instances. Selecting a node (or clicking the already selected one) opens what it stands for.
 */
public final class BrowseTree extends JPanel {
  public static final String ITEMS = "Items";
  public static final String ZONES = "Zones";
  public static final String INSTANCES = "Dungeons & Raids";

  /** What the tree shows; loaded off the EDT with {@link #load(Repositories)}. */
  public record Data(List<ItemClassNode> classes, List<ZoneSummary> zones, List<InstanceSummary> instances) {
    public Data {
      classes = List.copyOf(classes);
      zones = List.copyOf(zones);
      instances = List.copyOf(instances);
    }

    /** Runs the three repository queries; call it off the EDT. */
    public static Data load(Repositories repos) {
      return new Data(repos.items().classTree(), repos.zones().all(), repos.instances().all());
    }
  }

  /** Opens the {@code kind} (NPC, object or quest) list of a zone. */
  @FunctionalInterface
  public interface ZoneLists {
    void open(EntityKind kind, long zoneId);
  }

  private sealed interface Node {}

  private record Section(String label, ViewTarget target) implements Node {
    @Override
    public String toString() {
      return label;
    }
  }

  private record ClassNode(ItemClassNode node) implements Node {
    @Override
    public String toString() {
      return node.name();
    }
  }

  private record ZoneNode(ZoneSummary zone, EntityKind listKind) implements Node {
    @Override
    public String toString() {
      return zone.displayName();
    }
  }

  private record InstanceNode(InstanceSummary instance) implements Node {
    @Override
    public String toString() {
      return instance.displayName();
    }
  }

  private final Navigator nav;
  private final ZoneLists zoneLists;
  private final DefaultTreeModel model;
  private final JTree tree;
  /** When the event that last changed the selection happened ({@link EventQueue#getMostRecentEventTime()}). */
  private long lastSelectionTime = Long.MIN_VALUE;

  /** Loads the data on the calling thread; for tests and tools (production loads {@link Data} off the EDT). */
  public BrowseTree(Repositories repos, Navigator nav) {
    this(Data.load(repos), nav,
        (kind, zoneId) -> nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE, zoneId))));
  }

  public BrowseTree(Data data, Navigator nav, ZoneLists zoneLists) {
    super(new BorderLayout());
    this.nav = Objects.requireNonNull(nav, "nav");
    this.zoneLists = Objects.requireNonNull(zoneLists, "zoneLists");
    model = new DefaultTreeModel(buildRoot(data));
    tree = new JTree(model);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.addTreeSelectionListener(e -> {
      lastSelectionTime = EventQueue.getMostRecentEventTime();
      if (e.getNewLeadSelectionPath() != null) {
        activate(e.getNewLeadSelectionPath());
      }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        // a click on the node that was already selected re-opens it; a click that changed the selection has
        // been handled by the selection listener (same event, same timestamp)
        if (path != null && e.getWhen() != lastSelectionTime && e.getClickCount() == 1
            && SwingUtilities.isLeftMouseButton(e) && path.equals(tree.getSelectionPath())) {
          activate(path);
        }
      }
    });
    add(new JScrollPane(tree), BorderLayout.CENTER);
  }

  /** The section under which zone nodes open the {@code kind} list. */
  public static String zoneSection(EntityKind kind) {
    return switch (kind) {
      case NPC -> "NPCs by zone";
      case OBJECT -> "Objects by zone";
      case QUEST -> "Quests by zone";
      default -> throw new IllegalArgumentException("no zone section for " + kind);
    };
  }

  private static DefaultMutableTreeNode buildRoot(Data data) {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Browse");
    DefaultMutableTreeNode items = section(root, ITEMS, null);
    for (ItemClassNode cls : data.classes()) {
      DefaultMutableTreeNode clsNode = new DefaultMutableTreeNode(new ClassNode(cls));
      for (ItemClassNode sub : cls.children()) {
        clsNode.add(new DefaultMutableTreeNode(new ClassNode(sub)));
      }
      items.add(clsNode);
    }
    for (EntityKind kind : List.of(EntityKind.NPC, EntityKind.OBJECT, EntityKind.QUEST)) {
      addZones(section(root, zoneSection(kind), null), data.zones(), kind);
    }
    section(root, ZONES, new ViewTarget.List(EntityKind.ZONE));
    DefaultMutableTreeNode instances = section(root, INSTANCES, new ViewTarget.List(EntityKind.INSTANCE));
    for (InstanceSummary i : data.instances()) {
      instances.add(new DefaultMutableTreeNode(new InstanceNode(i)));
    }
    return root;
  }

  private static DefaultMutableTreeNode section(DefaultMutableTreeNode root, String label, ViewTarget target) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Section(label, target));
    root.add(node);
    return node;
  }

  /** Adds the zones under {@code section}, each subzone under its parent when the parent is listed. */
  private static void addZones(DefaultMutableTreeNode section, List<ZoneSummary> zones, EntityKind kind) {
    Map<Long, DefaultMutableTreeNode> nodes = new LinkedHashMap<>();
    for (ZoneSummary z : zones) {
      nodes.put(z.id(), new DefaultMutableTreeNode(new ZoneNode(z, kind)));
    }
    for (ZoneSummary z : zones) {
      DefaultMutableTreeNode node = nodes.get(z.id());
      DefaultMutableTreeNode parent = z.parentId() == null ? null : nodes.get(z.parentId());
      boolean cycle = parent != null && (parent == node || parent.isNodeAncestor(node));
      (parent == null || cycle ? section : parent).add(node);
    }
  }

  private void activate(TreePath path) {
    Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    if (!(user instanceof Node node)) {
      return;
    }
    switch (node) {
      case Section s -> {
        if (s.target() != null) {
          nav.open(s.target());
        }
      }
      case ClassNode c -> nav.open(new ViewTarget.ItemClass(c.node().classId(), c.node().subclassId()));
      case ZoneNode z -> zoneLists.open(z.listKind(), z.zone().id());
      case InstanceNode i -> nav.open(new ViewTarget.Entity(i.instance().ref()));
    }
  }

  public TreeModel model() {
    return model;
  }

  public JTree tree() {
    return tree;
  }

  /** The names of the instance nodes, in tree order. */
  public List<String> instanceNodeNames() {
    List<String> names = new ArrayList<>();
    DefaultMutableTreeNode section = child((DefaultMutableTreeNode) model.getRoot(), INSTANCES);
    for (int i = 0; i < section.getChildCount(); i++) {
      names.add(section.getChildAt(i).toString());
    }
    return names;
  }

  /** Selects the instance node named {@code name} (which opens it). */
  public void selectInstance(String name) {
    selectPath(INSTANCES, name);
  }

  /** Selects the node reached by following {@code labels} from the root. */
  void selectPath(String... labels) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getRoot();
    for (String label : labels) {
      node = child(node, label);
    }
    select(node);
  }

  /** Selects zone {@code zoneId} under {@code section}. */
  void selectZone(String section, long zoneId) {
    DefaultMutableTreeNode node = zoneNode(section, zoneId);
    if (node == null) {
      throw new IllegalArgumentException("no zone " + zoneId + " under " + section);
    }
    select(node);
  }

  /** The node of zone {@code zoneId} under {@code section}, or null. */
  DefaultMutableTreeNode zoneNode(String section, long zoneId) {
    Enumeration<TreeNode> all = child((DefaultMutableTreeNode) model.getRoot(), section).depthFirstEnumeration();
    while (all.hasMoreElements()) {
      DefaultMutableTreeNode n = (DefaultMutableTreeNode) all.nextElement();
      if (n.getUserObject() instanceof ZoneNode z && z.zone().id() == zoneId) {
        return n;
      }
    }
    return null;
  }

  private void select(DefaultMutableTreeNode node) {
    TreePath path = new TreePath(node.getPath());
    tree.expandPath(path.getParentPath());
    tree.setSelectionPath(path);
    tree.scrollPathToVisible(path);
  }

  private static DefaultMutableTreeNode child(DefaultMutableTreeNode parent, String label) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      if (parent.getChildAt(i).toString().equals(label)) {
        return (DefaultMutableTreeNode) parent.getChildAt(i);
      }
    }
    throw new IllegalArgumentException("no node " + label + " under " + parent);
  }
}
