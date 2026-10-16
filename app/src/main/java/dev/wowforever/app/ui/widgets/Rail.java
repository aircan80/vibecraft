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
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

/**
 * The {@value #WIDTH} px icon rail at the far left: one toggle button per {@link RailTarget}, named by its
 * tooltip. The selected button's icon turns gold.
 */
public final class Rail extends JToolBar {
  /** The rail's width in logical pixels. */
  public static final int WIDTH = 48;
  private static final int ICON_SIZE = 22;

  private final Map<RailTarget, JToggleButton> buttons = new EnumMap<>(RailTarget.class);
  private final ButtonGroup group = new ButtonGroup();
  private Consumer<RailTarget> onSelect = t -> {};

  public Rail() {
    super(VERTICAL);
    setFloatable(false);
    setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
    for (RailTarget t : RailTarget.values()) {
      JToggleButton b = new JToggleButton(Icons.icon(t.iconName(), ICON_SIZE));
      b.setSelectedIcon(Icons.icon(t.iconName(), ICON_SIZE, Theme::accentGold));
      b.setToolTipText(t.label());
      b.getAccessibleContext().setAccessibleName(t.label());
      b.setAlignmentX(CENTER_ALIGNMENT);
      b.addActionListener(e -> onSelect.accept(t));
      group.add(b);
      buttons.put(t, b);
      add(b);
    }
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
}
