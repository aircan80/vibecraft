package dev.wowforever.app.ui;

import dev.wowforever.app.db.*;
import dev.wowforever.app.model.ForeverContent;
import dev.wowforever.app.model.CampingData;
import dev.wowforever.app.model.systems.ClientSystems;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.pages.*;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Immutable catalog snapshots cross from the database worker to the Swing event thread. */
public final class CatalogPages {
  private CatalogPages() {}

  public static Object load(ViewTarget target, Database database) {
    if (target instanceof ViewTarget.ItemContent item)
      return new ItemContentRepository(database).load(item.section(), item.id());
    if (!(target instanceof ViewTarget.Forever page)) throw new IllegalArgumentException("Not a catalog page");
    return switch (page.section()) {
      case "hub", "ladder" -> new ForeverContentRepository(database).hub();
      case "pvp", "pvp-faction" -> new ForeverContentRepository(database).pvp();
      case "discovery" -> new ForeverContentRepository(database).discoveries();
      case "races", "race" -> new RaceRepository(database).load();
      case "camping" -> new CampingRepository(database).load();
      case "talents", "legacy", "class-spells" -> new SystemsRepository(database).load();
      default -> throw new IllegalArgumentException("Unknown Forever section: " + page.section());
    };
  }

  public static JComponent render(ViewTarget target, Object snapshot, Navigator nav) {
    if (target instanceof ViewTarget.ItemContent item)
      return new ItemContentPage((ItemContentRepository.Snapshot) snapshot, nav,
          (section,id) -> nav.open(new ViewTarget.ItemContent(section,id)));
    var page = (ViewTarget.Forever) target;
    return switch (page.section()) {
      case "hub" -> new ForeverHubPage((ForeverContent.Hub) snapshot, nav);
      case "ladder" -> new DungeonLadderPage((ForeverContent.Hub) snapshot, nav);
      case "pvp" -> new PvpPage((ForeverContent.Pvp) snapshot, nav);
      case "pvp-faction" -> ((ForeverContent.Pvp) snapshot).factions().stream()
          .filter(faction -> faction.id() == page.id()).findFirst()
          .<JComponent>map(faction -> new PvpFactionPage(faction,nav))
          .orElseGet(() -> new JLabel("Faction not found in the archived sources"));
      case "discovery" -> discoveries(snapshot).stream().filter(discovery -> discovery.mapId() == page.id()).findFirst()
          .<JComponent>map(discovery -> new InstanceDiscoveryPage(discovery,nav))
          .orElseGet(() -> new JLabel("Instance not found in the archived sources"));
      case "races" -> new RacesPage((RaceRepository.Catalog) snapshot,
          race -> nav.open(new ViewTarget.Forever("race",race)));
      case "race" -> new RacePage((RaceRepository.Catalog) snapshot,Math.toIntExact(page.id()));
      case "camping" -> new CampingPage((CampingData) snapshot);
      case "talents" -> new TalentPage((ClientSystems.Catalog) snapshot);
      case "legacy" -> new LegacyPage((ClientSystems.Catalog) snapshot);
      case "class-spells" -> new ClassSpellsPage((ClientSystems.Catalog) snapshot);
      default -> throw new IllegalArgumentException("Unknown Forever section: " + page.section());
    };
  }

  @SuppressWarnings("unchecked")
  private static java.util.List<ForeverContent.Discovery> discoveries(Object value) {
    return (java.util.List<ForeverContent.Discovery>) value;
  }
}
