package dev.wowforever.app.ui.pages.quest;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.QuestPageData;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Labels;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;

/** Shared page chrome populated only from the already-loaded quest facts. */
public final class QuestHeader {
  private QuestHeader() {}

  public static HeaderPanel create(QuestPageData data, Navigator nav) {
    var summary = data.detail().summary();
    var facts = data.facts();
    HeaderPanel header = new HeaderPanel(summary.displayName(), "");
    header.setPlate(RailIcons.shared().plate(RailTarget.QUESTS)
        .orElseGet(() -> RailIcons.shared().small(RailTarget.QUESTS, 24)));
    header.setKindWord("Quest");
    header.setFacts(Labels.questLevels(summary.questLevel(), summary.requiredLevel()));
    if (facts.zoneId() != null) {
      EntityRef zone = new EntityRef(EntityKind.ZONE, facts.zoneId());
      header.addChipLink(zone.displayName(facts.zoneName()), zone, nav);
    } else if (facts.sortLabel() != null && !facts.sortLabel().isBlank()) {
      header.addChip(new Chip(facts.sortLabel(), Chip.Style.PLAIN));
    }
    String faction = Labels.factions(facts.requiredRaces());
    if (faction != null) {
      header.addChip(new Chip(faction, Chip.Style.FACTION));
    }
    String classes = Labels.classes(facts.requiredClasses());
    if (classes != null && !classes.isBlank()) {
      header.addChip(new Chip(classes, Chip.Style.PLAIN));
    }
    if (facts.repeatable()) {
      header.addChip(new Chip("Repeatable", Chip.Style.REPEATABLE));
    }
    return header;
  }
}
