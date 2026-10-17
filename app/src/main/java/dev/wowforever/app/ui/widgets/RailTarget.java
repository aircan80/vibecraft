package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityKind;
import java.util.Optional;

/**
 * The sections of the {@link Rail}, top to bottom: their names, the SVG icon each shows until the client art is
 * loaded, and the client texture that replaces it ({@link RailIcons}).
 */
public enum RailTarget {
  HOME("Home", "home", "rail.home", false),
  FOREVER("WoW: Forever", "quests", "rail.forever", false),
  ITEMS("Items", "items", "rail.items", false),
  NPCS("NPCs", "npcs", "rail.npcs", true),
  QUESTS("Quests", "quests", "rail.quests", true),
  ZONES("Zones", "zones", "rail.zones", false),
  INSTANCES("Dungeons & Raids", "dungeons", "rail.instances", false);

  private final String label;
  private final String iconName;
  private final String assetName;
  private final boolean gossip;

  RailTarget(String label, String iconName, String assetName, boolean gossip) {
    this.label = label;
    this.iconName = iconName;
    this.assetName = assetName;
    this.gossip = gossip;
  }

  /** The name shown as the tooltip and read by screen readers. */
  public String label() {
    return label;
  }

  /** The {@link Icons} name of the button's SVG icon, shown until (or without) the client art. */
  public String iconName() {
    return iconName;
  }

  /** The database's {@code ui_asset} name of the client texture: {@code "rail.home"}, and so on. */
  public String assetName() {
    return assetName;
  }

  /**
   * Whether the texture is a 16 px GossipFrame glyph (NPCs, Quests) rather than 64 px Interface/Icons art; the two
   * are fitted to the plate differently ({@link RailIcons#plateArt}).
   */
  public boolean gossip() {
    return gossip;
  }

  /** The rail section that lists entities of {@code kind}; empty for objects and bosses, which have none. */
  public static Optional<RailTarget> of(EntityKind kind) {
    return switch (kind) {
      case ITEM -> Optional.of(ITEMS);
      case NPC -> Optional.of(NPCS);
      case QUEST -> Optional.of(QUESTS);
      case ZONE -> Optional.of(ZONES);
      case INSTANCE -> Optional.of(INSTANCES);
      case OBJECT, BOSS -> Optional.empty();
    };
  }
}
