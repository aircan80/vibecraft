package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JToolTip;
import javax.swing.border.LineBorder;

/**
 * The {@value #WIDTH} px icon rail at the far left: one toggle button per {@link RailTarget}, named by its tooltip,
 * which shows on the navy tooltip card. Each icon sits on a navy plate ({@link RailButtonUI}, which paints the idle,
 * hover and selected states). The buttons start with the light SVG glyphs (gold when selected) and take the client
 * art once it is loaded ({@link #setIcons}); the plates keep their size, so nothing moves.
 */
public final class Rail extends JToolBar {
  /** The rail's width in logical pixels. */
  public static final int WIDTH = 48;
  private static final int SVG_SIZE = 22;

  private final Map<RailTarget, JToggleButton> buttons = new EnumMap<>(RailTarget.class);
  private final ButtonGroup group = new ButtonGroup();
  private Consumer<RailTarget> onSelect = t -> {};

  public Rail() {
    super(VERTICAL);
    setFloatable(false);
    setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
    for (RailTarget t : RailTarget.values()) {
      JToggleButton b = new RailButton(Icons.icon(t.iconName(), SVG_SIZE, theme -> RailButtonUI.GLYPH));
      b.setSelectedIcon(Icons.icon(t.iconName(), SVG_SIZE, Theme::accentGold));
      b.setToolTipText(t.label());
      b.getAccessibleContext().setAccessibleName(t.label());
      b.setAlignmentX(CENTER_ALIGNMENT);
      b.addActionListener(e -> onSelect.accept(t));
      group.add(b);
      buttons.put(t, b);
      add(b);
    }
  }

  /**
   * Shows the client art of {@code icons} on the plates of the targets that have it, in full colour in every state;
   * the others keep their SVG glyphs. Call on the EDT.
   */
  public void setIcons(RailIcons icons) {
    Objects.requireNonNull(icons, "icons");
    for (RailTarget t : RailTarget.values()) {
      icons.plate(t).ifPresent(art -> {
        JToggleButton b = buttons.get(t);
        b.setIcon(art);
        b.setSelectedIcon(null);
      });
    }
    repaint();
  }

  /** Called with the target of the button the user clicks; replaces any earlier listener. */
  public void onSelect(Consumer<RailTarget> listener) {
    onSelect = Objects.requireNonNull(listener, "listener");
  }

  /** Marks {@code target}'s button as selected without firing; null clears the selection. */
  public void select(RailTarget target) {
    if (target == null) {
      group.clearSelection();
    } else {
      buttons.get(target).setSelected(true);
    }
  }

  public JToggleButton button(RailTarget target) {
    return buttons.get(target);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(UIScale.scale(WIDTH), super.getPreferredSize().height);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(UIScale.scale(WIDTH), super.getMinimumSize().height);
  }

  /** A rail button: painted by {@link RailButtonUI} under every look and feel, its tooltip on the navy card. */
  private static final class RailButton extends JToggleButton {
    RailButton(Icon icon) {
      super(icon);
    }

    @Override
    public void updateUI() {
      setUI(new RailButtonUI());
    }

    @Override
    public JToolTip createToolTip() {
      JToolTip tip = super.createToolTip();
      tip.setBackground(TooltipLinesPanel.BACKGROUND);
      tip.setForeground(TooltipLinesPanel.DEFAULT_TEXT);
      tip.setBorder(BorderFactory.createCompoundBorder(new LineBorder(TooltipLinesPanel.BORDER),
          BorderFactory.createEmptyBorder(3, 7, 3, 7)));
      return tip;
    }
  }
}
