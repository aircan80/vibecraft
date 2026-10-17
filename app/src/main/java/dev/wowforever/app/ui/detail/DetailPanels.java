package dev.wowforever.app.ui.detail;

import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.ItemDetail;
import dev.wowforever.app.model.NpcDetail;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.QuestDetail;
import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.pages.InstancePage;
import dev.wowforever.app.ui.pages.ItemPage;
import dev.wowforever.app.ui.pages.NpcPage;
import dev.wowforever.app.ui.pages.ObjectPage;
import dev.wowforever.app.ui.pages.QuestPage;
import dev.wowforever.app.ui.pages.ZonePage;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.Images;
import java.awt.image.BufferedImage;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Builds entity detail pages in two steps: {@link #load} queries the repository (call it off the EDT), and
 * {@link #render} builds the panel from what it returned (call it on the EDT).
 */
public final class DetailPanels {
  private DetailPanels() {}

  /**
   * What {@link #load} returns for an item: the item and its decoded icon (null when there is none). The page
   * does not show the icon yet (the tooltip card and icons arrive with Part 2).
   */
  public record LoadedItem(ItemDetail detail, BufferedImage icon) {}

  /**
   * What {@link #load} returns for a zone: the zone and its decoded map (null: none, or it does not decode). The
   * page does not show the map yet (maps with pins arrive with Part 2).
   */
  public record LoadedZone(ZoneDetail detail, BufferedImage map) {}

  /**
   * The detail record of {@code ref} ({@link LoadedItem}, {@link NpcDetail}, {@link LoadedZone}, ...), or empty when
   * there is no such entity. Bosses have no page of their own (they are shown inside their instance), so they load
   * empty. An item's icon (or the question-mark fallback) and a zone's map are read and decoded here, off the EDT.
   * The icon id is the summary's, which an item with only an {@code Item} row (no tooltip card) has too.
   */
  public static Optional<Object> load(EntityRef ref, Repositories repos) {
    long id = ref.id();
    Optional<?> detail = switch (ref.kind()) {
      case ITEM -> repos.items().detail(id).map(d -> new LoadedItem(d,
          repos.assets().iconOrFallback(iconId(d)).flatMap(Images::decode).orElse(null)));
      case NPC -> repos.npcs().detail(id);
      case OBJECT -> repos.objects().detail(id);
      case QUEST -> repos.quests().detail(id);
      case ZONE -> repos.zones().detail(id)
          .map(d -> new LoadedZone(d, d.map().flatMap(m -> Images.decode(m.jpeg())).orElse(null)));
      case INSTANCE -> repos.instances().detail(id);
      case BOSS -> Optional.empty();
    };
    return detail.map(Object.class::cast);
  }

  /** The summary's icon id, else the card's (null when neither has one). */
  private static Long iconId(ItemDetail d) {
    Long summary = d.summary().iconId();
    return summary != null ? summary : d.card().iconId();
  }

  /** The page for a record from {@link #load}. */
  public static JComponent render(Object detail, Repositories repos, Navigator nav) {
    return switch (detail) {
      case LoadedItem p -> new ItemPage(p.detail(), nav);
      case NpcDetail d -> new NpcPage(d, nav, ClipboardPort.system());
      case ObjectDetail d -> new ObjectPage(d, nav, ClipboardPort.system());
      case QuestDetail d -> new QuestPage(d, nav);
      case LoadedZone p -> new ZonePage(p.detail(), nav);
      case InstanceDetail d -> new InstancePage(d, repos, nav);
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** The display name of a record from {@link #load}. */
  public static String name(Object detail) {
    return switch (detail) {
      case LoadedItem p -> p.detail().summary().displayName();
      case NpcDetail d -> d.summary().displayName();
      case ObjectDetail d -> d.summary().displayName();
      case QuestDetail d -> d.summary().displayName();
      case LoadedZone p -> p.detail().summary().displayName();
      case InstanceDetail d -> d.summary().displayName();
      default -> throw new IllegalArgumentException("No detail page for " + detail.getClass().getName());
    };
  }

  /** {@link #load} then {@link #render} on the calling thread, or {@link #notFound} for a missing entity. */
  public static JComponent build(EntityRef ref, Repositories repos, Navigator nav) {
    return load(ref, repos).map(d -> render(d, repos, nav)).orElseGet(() -> notFound(ref));
  }

  /** The detail shown for an entity the database does not have: {@code "Not found: <kind> #<id>"}. */
  public static JLabel notFound(EntityRef ref) {
    JLabel label = new JLabel("Not found: " + ref.kind().label() + " #" + ref.id(), SwingConstants.LEADING);
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    return label;
  }
}
