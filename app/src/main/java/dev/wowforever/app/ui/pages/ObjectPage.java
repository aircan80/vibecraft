package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import dev.wowforever.app.model.QuestInvolvement;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Columns;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.ClipboardPort;
import dev.wowforever.app.ui.widgets.MapView;
import java.awt.image.BufferedImage;

/**
 * The game object page, one scrolling page: the header (name, the kind word and a zone link), then the sections
 * Contains, Quests and Location (those without rows are left out). Given the decoded map of a
 * spawn zone, the Location section shows it above its table with the object's spawn points there.
 */
public final class ObjectPage extends SourcesPage {
  private final HeaderPanel header;
  private final SpawnSection spawns;

  public ObjectPage(ObjectDetail d, Navigator nav, ClipboardPort clipboard) {
    this(d, null, null, nav, clipboard);
  }

  /** The page with {@code map}, the decoded map of zone {@code mapZoneId} (both null: none), in its Location section. */
  public ObjectPage(ObjectDetail d, Long mapZoneId, BufferedImage map, Navigator nav, ClipboardPort clipboard) {
    this(d, mapZoneId, map, QuestInvolvement.fromGivers(d.starts(), d.ends()), nav, clipboard);
  }

  /** All quest roles loaded off the EDT with this object. */
  public ObjectPage(ObjectDetail d, Long mapZoneId, BufferedImage map, QuestInvolvement.Result involvement,
      Navigator nav, ClipboardPort clipboard) {
    super(nav);
    ObjectSummary s = d.summary();
    header = zoneHeader(s.displayName(), "Object #" + s.id(), s.zoneId(), s.zoneName());
    header.setKindWord(EntityKind.OBJECT.label()); // objects have no rail section, so no plate
    content.add(header);
    addLootSection("Contains", d.contains());
    addQuestInvolvements(involvement, EntityKind.OBJECT, s.zoneName());
    if (d.spawns().isEmpty()) {
      spawns = null;
    } else {
      spawns = new SpawnSection(d.spawns(), s.zoneName(), s.ref(), s.name(), mapZoneId, map, nav, clipboard);
      addSection(spawns.section());
    }
    bindJumpKeys();
  }

  public HeaderPanel header() {
    return header;
  }

  /** The map of the Location section; null without one. */
  public MapView spawnMap() {
    return spawns == null ? null : spawns.map();
  }

  /** The spawns section; null when the object has no spawn points. */
  SpawnSection spawns() {
    return spawns;
  }
}
