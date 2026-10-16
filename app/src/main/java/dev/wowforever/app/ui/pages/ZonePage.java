package dev.wowforever.app.ui.pages;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceSummary;
import dev.wowforever.app.model.NpcSummary;
import dev.wowforever.app.model.QuestSummary;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.LinkButton;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The zone page, one scrolling page: the header (kind, the level range of the zone's NPCs, a parent link), its
 * dungeons and raids and its sub-zones as compact rows of links, an area reserved for the map, then the sections
 * NPCs (bosses and rares first), Objects and Quests (by level). Sections without rows are left out.
 */
public final class ZonePage extends SourcesPage {
  /** The height kept free for the zone map. */
  static final int MAP_HEIGHT = 200;

  private final HeaderPanel header;
  private final List<JButton> instanceLinks = new ArrayList<>();
  private final List<JButton> subzoneLinks = new ArrayList<>();
  private final JPanel mapPlaceholder = new JPanel() {
    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
  };

  public ZonePage(ZoneDetail d, Navigator nav) {
    super(nav);
    ZoneSummary s = d.summary();
    header = new HeaderPanel(s.displayName(), Labels.join("Zone #" + s.id(), Labels.zoneSubtitle(s.kind(), null),
        levelRange(d.npcs())));
    header.setAlignmentX(LEFT_ALIGNMENT);
    if (s.parentId() != null) {
      EntityRef parent = new EntityRef(EntityKind.ZONE, s.parentId());
      header.addLink(parent.displayName(s.parentName()), parent, nav);
    }
    content.add(header);
    addLinkRow("Dungeons & raids", d.instances(), i -> Labels.instanceTitle(i.displayName(), i.key()),
        InstanceSummary::ref, instanceLinks);
    addLinkRow("Sub-zones", d.subzones(), ZoneSummary::displayName, ZoneSummary::ref, subzoneLinks);
    mapPlaceholder.setOpaque(false);
    mapPlaceholder.setPreferredSize(new Dimension(0, UIScale.scale(MAP_HEIGHT)));
    mapPlaceholder.setAlignmentX(LEFT_ALIGNMENT);
    content.add(mapPlaceholder);
    List<NpcSummary> npcs = d.npcs().stream().sorted(Comparator.comparingInt((NpcSummary n) -> rankOrder(n.rank()))
        .thenComparing(NpcSummary::displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(NpcSummary::id)).toList();
    addSection("NPCs", npcs.size(), VisibleColumns.of(Columns.npcs(), npcs, s.name()), npcs);
    addSection("Objects", d.objects().size(), VisibleColumns.of(Columns.objects(), d.objects(), s.name()),
        d.objects());
    List<QuestSummary> quests = d.quests().stream().sorted(Comparator.comparing(QuestSummary::questLevel,
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(QuestSummary::displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparingLong(QuestSummary::id)).toList();
    addSection("Quests", quests.size(), VisibleColumns.of(Columns.quests(), quests, s.name()), quests);
    bindJumpKeys();
  }

  /**
   * {@code "Level 5–12"}: the lowest and highest level of the zone's NPCs, levels of 0 and below and unknown ones
   * left out (an NPC with one known level counts it as both); null when no NPC has a level.
   */
  static String levelRange(List<NpcSummary> npcs) {
    Integer low = null;
    Integer high = null;
    for (NpcSummary n : npcs) {
      Integer min = positive(n.minLevel()) != null ? positive(n.minLevel()) : positive(n.maxLevel());
      Integer max = positive(n.maxLevel()) != null ? positive(n.maxLevel()) : positive(n.minLevel());
      if (min != null) {
        low = low == null ? min : Math.min(low, min);
        high = high == null ? max : Math.max(high, max);
      }
    }
    return Labels.levels(low, high);
  }

  private static Integer positive(Integer level) {
    return level != null && level > 0 ? level : null;
  }

  /** Bosses first, then rare elites, rares, elites and the rest (normal or unknown). */
  private static int rankOrder(Integer rank) {
    if (rank == null) {
      return 4;
    }
    return switch (rank) {
      case 3 -> 0;
      case 2 -> 1;
      case 4 -> 2;
      case 1 -> 3;
      default -> 4;
    };
  }

  /** A row {@code "<label>: a · b"} of links to {@code entries}; nothing when there are none. */
  private <T> void addLinkRow(String label, List<T> entries, Function<T, String> text, Function<T, EntityRef> ref,
      List<JButton> links) {
    if (entries.isEmpty()) {
      return;
    }
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0)) {
      @Override
      public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
      }
    };
    row.setOpaque(false);
    row.setAlignmentX(LEFT_ALIGNMENT);
    row.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));
    row.add(PageContent.mutedLabel(label + ": "));
    for (T entry : entries) {
      if (!links.isEmpty()) {
        row.add(new JLabel(" · "));
      }
      JButton link = LinkButton.create(Objects.requireNonNull(text.apply(entry)), ref.apply(entry), nav);
      links.add(link);
      row.add(link);
    }
    content.add(row);
  }

  public HeaderPanel header() {
    return header;
  }

  /** The links to the zone's dungeons and raids, in order. */
  public List<JButton> instanceLinks() {
    return List.copyOf(instanceLinks);
  }

  /** The links to the zone's sub-zones, in order. */
  public List<JButton> subzoneLinks() {
    return List.copyOf(subzoneLinks);
  }

  /** The empty area kept for the zone map. */
  public JPanel mapPlaceholder() {
    return mapPlaceholder;
  }
}
