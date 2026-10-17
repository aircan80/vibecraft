package dev.wowforever.app.ui.widgets;

import java.util.List;
import java.util.Set;
import dev.wowforever.app.ui.widgets.MapView.PinKind;

/** Registered map controls; future layers declare their legend and preset membership here. */
public record MapLayer(String id, String label, Set<PinKind> kinds, boolean defaultVisible, Set<String> presets) {
  public MapLayer {
    kinds = Set.copyOf(kinds);
    presets = Set.copyOf(presets);
  }

  public static final String BOSSES_AND_RARES = "bosses-and-rares";
  public static final List<MapLayer> REGISTERED = List.of(
      new MapLayer("bosses", "Boss", Set.of(PinKind.BOSS), true, Set.of(BOSSES_AND_RARES)),
      new MapLayer("rares", "Rare", Set.of(PinKind.RARE), true, Set.of(BOSSES_AND_RARES)),
      new MapLayer("npcs", "NPC", Set.of(PinKind.NPC), true, Set.of()),
      new MapLayer("objects", "Object", Set.of(PinKind.OBJECT), true, Set.of()),
      new MapLayer("starts", "Starts a quest", Set.of(PinKind.QUEST_START), true, Set.of()),
      new MapLayer("ends", "Ends a quest", Set.of(PinKind.QUEST_END), true, Set.of()),
      new MapLayer("kill", "Kill", Set.of(PinKind.KILL), true, Set.of()),
      new MapLayer("collect", "Collect", Set.of(PinKind.COLLECT), true, Set.of()),
      new MapLayer("interact", "Interact", Set.of(PinKind.INTERACT), true, Set.of()),
      new MapLayer("explore", "Explore", Set.of(PinKind.EXPLORE), true, Set.of()));
}
