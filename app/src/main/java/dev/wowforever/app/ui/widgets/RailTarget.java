package dev.wowforever.app.ui.widgets;

/** The sections of the {@link Rail}, top to bottom: their names and the icon each shows. */
public enum RailTarget {
  HOME("Home", "home"),
  ITEMS("Items", "items"),
  NPCS("NPCs", "npcs"),
  QUESTS("Quests", "quests"),
  ZONES("Zones", "zones"),
  INSTANCES("Dungeons & Raids", "dungeons");

  private final String label;
  private final String iconName;

  RailTarget(String label, String iconName) {
    this.label = label;
    this.iconName = iconName;
  }

  /** The name shown as the tooltip and read by screen readers. */
  public String label() {
    return label;
  }

  /** The {@link Icons} name of the button's icon. */
  public String iconName() {
    return iconName;
  }
}
