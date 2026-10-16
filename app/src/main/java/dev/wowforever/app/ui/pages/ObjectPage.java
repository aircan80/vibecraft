package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.HeaderPanel;

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
    header = zoneHeader(s.displayName(), "Object #" + s.id(), s.zoneId(), s.zoneName());
    content.add(header);
    addLootSection("Contains", d.contains());
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
