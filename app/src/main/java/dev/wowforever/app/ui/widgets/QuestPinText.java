package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.QuestRef;
import dev.wowforever.app.ui.HtmlText;
import dev.wowforever.app.ui.widgets.MapView.Cluster;
import dev.wowforever.app.ui.widgets.MapView.Pin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a {@link MapView} marker of quest givers says: its tooltip, listing each giver's quests, and the quests its
 * menu offers. A tooltip longer than {@value #WRAP_CHARS} characters wraps at {@value #WRAP_PX} px; a marker of
 * several givers lists at most {@value #TOOLTIP_LINES} of them.
 */
final class QuestPinText {
  /** Most giver lines a marker's tooltip lists; the rest are counted ({@code "+3 more"}). */
  static final int TOOLTIP_LINES = 12;
  /** The longest tooltip line kept on one line. */
  static final int WRAP_CHARS = 80;
  /** The width a longer tooltip wraps at, in logical px. */
  static final int WRAP_PX = 360;

  private QuestPinText() {}

  /**
   * The tooltip of marker {@code c}, which has quests: {@code "Marshal Dughan — Wanted: Hogger (11), A Bundle of
   * Trouble (5)"} for one giver, its starts and ends on lines of their own ({@code "Starts: …"}, {@code "Ends: …"})
   * when it does both; for several givers a list headed {@code "<n> pins"} with a line each.
   */
  static String tooltip(Cluster c) {
    Map<Pin, Integer> entities = c.entities();
    if (entities.size() == 1) {
      Pin p = c.pins().get(0);
      Roles roles = Roles.of(c.quests());
      if (!roles.both()) {
        String line = p.name() + " — " + labels(c.quests());
        return line.length() > WRAP_CHARS ? html(List.of(line)) : line;
      }
      return html(List.of(p.name(), "Starts: " + labels(roles.starts()), "Ends: " + labels(roles.ends())));
    }
    List<String> lines = new ArrayList<>();
    lines.add(c.count() + " pins");
    int shown = 0;
    for (Map.Entry<Pin, Integer> e : entities.entrySet()) {
      if (shown == TOOLTIP_LINES) {
        lines.add("+" + (entities.size() - shown) + " more");
        break;
      }
      lines.add(entityLine(c, e.getKey(), e.getValue()));
      shown++;
    }
    return html(lines);
  }

  /** A giver's quests split into those it starts and those it ends, each in the given order. */
  record Roles(List<QuestRef> starts, List<QuestRef> ends) {
    static Roles of(List<QuestRef> quests) {
      return new Roles(quests.stream().filter(QuestRef::starts).toList(),
          quests.stream().filter(q -> !q.starts()).toList());
    }

    /** Whether the giver both starts and ends quests here. */
    boolean both() {
      return !starts.isEmpty() && !ends.isEmpty();
    }
  }

  /**
   * One giver's line in a marker's list: its name and its quests ({@code "Gryan — Starts: …; Ends: …"} when it does
   * both), or its name and pin count when it lists none.
   */
  private static String entityLine(Cluster c, Pin giver, int count) {
    List<QuestRef> quests = c.pins().stream().filter(p -> p.ref().equals(giver.ref()))
        .flatMap(p -> p.quests().stream()).distinct().toList();
    if (quests.isEmpty()) {
      return count > 1 ? giver.name() + " (" + count + ")" : giver.name();
    }
    Roles roles = Roles.of(quests);
    String list = !roles.both() ? labels(quests)
        : "Starts: " + labels(roles.starts()) + "; Ends: " + labels(roles.ends());
    return giver.name() + " — " + list;
  }

  /** {@code "Wanted: Hogger (11), A Bundle of Trouble (5)"}, at most {@value MapView#LIST_CAP} of them. */
  static String labels(List<QuestRef> quests) {
    String list = quests.stream().limit(MapView.LIST_CAP).map(QuestRef::label).collect(Collectors.joining(", "));
    return quests.size() > MapView.LIST_CAP ? list + ", …" : list;
  }

  /** The marker's quests, each once whether started or ended there (the first role kept), for its menu. */
  static List<QuestRef> distinctQuests(Cluster c) {
    Map<Long, QuestRef> byId = new LinkedHashMap<>();
    c.quests().forEach(q -> byId.putIfAbsent(q.questId(), q));
    return List.copyOf(byId.values());
  }

  /** {@code lines} as HTML, one per line, wrapped at {@value #WRAP_PX} px when one is long. */
  private static String html(List<String> lines) {
    String body = lines.stream().map(HtmlText::escape).collect(Collectors.joining("<br>"));
    boolean wrap = lines.stream().anyMatch(l -> l.length() > WRAP_CHARS);
    return wrap ? "<html><body style='width: " + UIScale.scale(WRAP_PX) + "px'>" + body + "</body></html>"
        : "<html>" + body + "</html>";
  }
}
