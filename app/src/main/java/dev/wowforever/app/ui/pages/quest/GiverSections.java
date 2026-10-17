package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.GiverSpot;
import dev.wowforever.app.model.Spawn;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.detail.LinkTable;
import dev.wowforever.app.ui.widgets.LinkButton;
import dev.wowforever.app.ui.widgets.SourceSection;
import java.util.List;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/** Giver links with their first known location; compact rows retain location text in the name cell. */
public final class GiverSections {
  private GiverSections() {}

  public static SourceSection create(String title, List<GiverSpot> spots, Navigator nav) {
    return new GiverTable(title, spots, nav).section;
  }

  private static final class GiverTable {
    private final LinkTable<GiverSpot> table;
    private final SourceSection section;

    GiverTable(String title, List<GiverSpot> spots, Navigator nav) {
      table = LinkTable.inline(List.of(
          new Columns.Column<GiverSpot>("Name", String.class, this::name, g -> Optional.of(g.giver().ref()),
              null, g -> g.giver().quality(), null,
              g -> g.giver().ref().kind() == EntityKind.ITEM
                  ? g.giver().iconId() == null ? 0L : g.giver().iconId() : null),
          new Columns.Column<GiverSpot>("Zone", String.class, g -> zone(g), g -> first(g)
              .map(s -> new dev.wowforever.app.model.EntityRef(EntityKind.ZONE, s.zoneId()))),
          new Columns.Column<GiverSpot>("Coordinates", String.class, g -> first(g)
              .map(Columns::coordinatesText).orElse(""), null)), nav);
      table.setRows(spots);
      section = new SourceSection(title, spots.size(), table);
      section.setRowLimited(false);
      JPanel itemStarts = new JPanel();
      itemStarts.setLayout(new BoxLayout(itemStarts, BoxLayout.PAGE_AXIS));
      itemStarts.setOpaque(false);
      spots.stream().filter(g -> g.giver().ref().kind() == EntityKind.ITEM).forEach(g -> {
        var line = LinkButton.create("Starts from item: " + g.giver().displayName() + " — drops from ›",
            g.itemSources().isEmpty() ? g.giver().ref() : g.itemSources().getFirst().ref(), nav);
        itemStarts.add(line);
      });
      if (itemStarts.getComponentCount() > 0) {
        section.addAboveTable(itemStarts);
      }
    }

    private Object name(GiverSpot spot) {
      return CompactColumns.within(table) ? line(spot) : spot.giver().displayName();
    }
  }

  public static String line(GiverSpot spot) {
    return Labels.join(spot.giver().displayName(), zone(spot), first(spot).map(Columns::coordinatesText).orElse(null));
  }

  private static Optional<Spawn> first(GiverSpot spot) {
    return spot.spawns().stream().findFirst();
  }

  private static String zone(GiverSpot spot) {
    return first(spot).map(s -> s.zoneName() == null ? "Zone #" + s.zoneId() : s.zoneName()).orElse(null);
  }
}
