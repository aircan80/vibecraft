package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JPanel;

/**
 * The row under a zone map that tells what its pins are: a dot per kind in the map's own colour (boss, rare, NPC,
 * object) and the quest givers' glyphs ({@code "!"} starts a quest, {@code "?"} ends one), each with its word in
 * muted text. The map's colours are read per paint, so theme changes apply.
 */
public final class PinLegend extends JPanel {
  /** The kinds the legend tells, in order, with their words. */
  private static final List<PinKind> KINDS =
      List.of(PinKind.BOSS, PinKind.RARE, PinKind.NPC, PinKind.OBJECT, PinKind.QUEST_START, PinKind.QUEST_END);
  private static final int ICON = 14;

  private final List<JLabel> entries = new ArrayList<>();

  public PinLegend() {
    super(new FlowLayout(FlowLayout.LEADING, UIScale.scale(10), 0));
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    for (PinKind kind : KINDS) {
      JLabel entry = new JLabel(word(kind), new PinIcon(kind), JLabel.LEADING) {
        @Override
        public Color getForeground() { // looked up per paint, so theme changes apply
          return Theme.current().textMuted();
        }
      };
      entry.setIconTextGap(UIScale.scale(4));
      entry.setFont(entry.getFont().deriveFont(entry.getFont().getSize2D() * 0.9f));
      entries.add(entry);
      add(entry);
    }
    getAccessibleContext().setAccessibleName("Map legend");
  }

  /** Keyboard-accessible switches for registered layers with pins on this map. */
  public static PinLegend interactive(MapView map) {
    PinLegend legend = new PinLegend();
    Runnable rebuild = () -> {
      legend.removeAll();
      legend.entries.clear();
      for (MapLayer layer : MapLayer.REGISTERED) {
        if (map.pins().stream().filter(p -> !map.bossesAndRaresOnly() || p.kind() != PinKind.NPC)
            .noneMatch(p -> layer.kinds().contains(p.kind()))) continue;
        PinKind kind = layer.kinds().iterator().next();
        JToggleButton toggle = new JToggleButton(layer.label(), new PinIcon(kind),
            layer.kinds().stream().allMatch(map::isKindVisible));
        toggle.putClientProperty("mapLayer", layer);
        toggle.setFocusable(true);
        toggle.addActionListener(e -> layer.kinds().forEach(k -> map.setKindVisible(k, toggle.isSelected())));
        legend.add(toggle);
      }
      legend.revalidate();
      legend.repaint();
    };
    map.addPropertyChangeListener("pinFilter", e -> rebuild.run());
    map.addPropertyChangeListener("pins", e -> rebuild.run());
    map.addPropertyChangeListener("kindVisibility", e -> {
      for (Component component : legend.getComponents()) {
        JToggleButton toggle = (JToggleButton) component;
        MapLayer layer = (MapLayer) toggle.getClientProperty("mapLayer");
        toggle.setSelected(layer.kinds().stream().allMatch(map::isKindVisible));
      }
    });
    rebuild.run();
    return legend;
  }

  /** What the legend calls pins of {@code kind}. */
  static String word(PinKind kind) {
    return switch (kind) {
      case SELF -> "This one";
      case KILL -> "Kill";
      case COLLECT -> "Collect";
      case INTERACT -> "Interact";
      case EXPLORE -> "Explore";
      case BOSS -> "Boss";
      case RARE -> "Rare";
      case NPC -> "NPC";
      case OBJECT -> "Object";
      case QUEST_START -> "Starts a quest";
      case QUEST_END -> "Ends a quest";
    };
  }

  /** The entries, in order. */
  public List<JLabel> entries() {
    return List.copyOf(entries);
  }

  /** The texts of the entries, in order. */
  public List<String> texts() {
    return java.util.Arrays.stream(getComponents()).map(c -> c instanceof JLabel label
        ? label.getText() : ((JToggleButton) c).getText()).toList();
  }

  /** A pin as the map draws it: a dot in the kind's colour with a dark outline, or a quest giver's glyph. */
  static final class PinIcon implements Icon {
    private final PinKind kind;

    PinIcon(PinKind kind) {
      this.kind = kind;
    }

    PinKind kind() {
      return kind;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = x + getIconWidth() / 2.0;
        double cy = y + getIconHeight() / 2.0;
        if (kind.quest()) {
          PinGlyphs.paint(g2, PinGlyphs.font(c.getFont(), false).deriveFont(UIScale.scale(12f)), kind, cx, cy);
        } else if (kind.layer() == MapView.Layer.OBJECTIVES) {
          PinGlyphs.paintObjective(g2, c.getFont(), kind, 1, cx, cy, UIScale.scale(4f));
        } else {
          double r = UIScale.scale(kind == PinKind.BOSS ? 5 : 4);
          Ellipse2D.Double dot = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
          g2.setColor(MapView.colour(kind));
          g2.fill(dot);
          g2.setStroke(new BasicStroke(UIScale.scale(1f)));
          g2.setColor(MapView.OUTLINE);
          g2.draw(dot);
        }
      } finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return UIScale.scale(ICON);
    }

    @Override
    public int getIconHeight() {
      return UIScale.scale(ICON);
    }
  }
}
