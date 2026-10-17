package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.SourceFilter;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;

/**
 * The drop source filter as a small combo beside a drop section's heading (the item page's "Dropped by", the NPC page's
 * "Drops"): All sources, Hide AtlasLoot-only rows, Hide Questie-only rows. Choosing a filter other than the one shown
 * hands it to {@code onChange}, which saves it and shows the page again. Its tooltip is the legend of the rate column's
 * marks ({@link #LEGEND}).
 */
public final class SourceFilterControl extends JComboBox<SourceFilter> {
  /** The legend of the marks a rate can carry, which the control's tooltip shows. */
  public static final String LEGEND = "≠ The sources disagree on this rate; hover the rate to see each source.";
  /** The control's accessible name. */
  public static final String NAME = "Drop sources";

  private final SourceFilter current;
  /** The filter last chosen (at first the page's own). */
  private SourceFilter chosen;

  public SourceFilterControl(SourceFilter current, Consumer<SourceFilter> onChange) {
    super(SourceFilter.values());
    this.current = Objects.requireNonNull(current, "current");
    this.chosen = current;
    Objects.requireNonNull(onChange, "onChange");
    setSelectedItem(current);
    setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
          boolean cellHasFocus) {
        return super.getListCellRendererComponent(list, value instanceof SourceFilter f ? f.label() : value, index,
            isSelected, cellHasFocus);
      }
    });
    setToolTipText(LEGEND);
    getAccessibleContext().setAccessibleName(NAME);
    getAccessibleContext().setAccessibleDescription(LEGEND);
    setFont(getFont().deriveFont(getFont().getSize2D() * 0.9f));
    setMaximumSize(getPreferredSize());
    addActionListener(e -> {
      if (getSelectedItem() instanceof SourceFilter f && f != chosen) {
        chosen = f;
        onChange.accept(f);
      }
    });
  }

  /** The filter the page was built with. */
  public SourceFilter current() {
    return current;
  }
}
