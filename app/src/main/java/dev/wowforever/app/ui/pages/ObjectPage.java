package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.SourceSection;

/**
 * The game object page, one scrolling page: the header (name and a zone link), then the sections Contains,
 * Starts quests, Ends quests and Spawns (those without rows are left out).
 */
public final class ObjectPage extends SourcesPage {
  private final HeaderPanel header;
  private final SpawnSection spawns;

  public ObjectPage(ObjectDetail d, Navigator nav, ClipboardPort clipboard) {
    super(nav);
    ObjectSummary s = d.summary();
    boolean zoneLink = s.zoneId() != null;
    header = new HeaderPanel(s.displayName(), Labels.join("Object #" + s.id(), zoneLink ? null : s.zoneName()));
    header.setAlignmentX(LEFT_ALIGNMENT);
    if (zoneLink) {
      EntityRef zone = new EntityRef(EntityKind.ZONE, s.zoneId());
      header.addLink(zone.displayName(s.zoneName()), zone, nav);
    }
    content.add(header);
    if (!d.contains().isEmpty()) {
      addSection(new SourceSection("Contains", d.contains().size(), LootTables.plain(d.contains(), nav)));
    }
    addSection("Starts quests", d.starts().size(), VisibleColumns.of(Columns.quests(), d.starts(), s.zoneName()),
        d.starts());
    addSection("Ends quests", d.ends().size(), VisibleColumns.of(Columns.quests(), d.ends(), s.zoneName()),
        d.ends());
    if (d.spawns().isEmpty()) {
      spawns = null;
    } else {
      spawns = new SpawnSection(d.spawns(), s.zoneName(), nav, clipboard);
      addSection(spawns.section());
    }
    bindJumpKeys();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The spawns section; null when the object has no spawn points. */
  SpawnSection spawns() {
    return spawns;
  }
}
