package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;

/**
 * The {@link KindChips} folded into one button for the narrow compact window: it shows the selected kind
 * ({@code "Items ▾"}) and opens a menu of the chips as they read (with their counts); choosing one clicks that
 * chip, so the search reacts exactly as to the chip itself. Like the chips it never takes the keyboard focus.
 */
public final class KindMenu extends JButton {
  private final KindChips chips;

  public KindMenu(KindChips chips) {
    this.chips = Objects.requireNonNull(chips, "chips");
    setFocusable(false);
    setToolTipText("What to search for");
    addActionListener(e -> popup().show(this, 0, getHeight()));
    refresh();
  }

  /** Shows the kind of the selected chip; call when the chips' selection may have changed. */
  public void refresh() {
    String kind = chips.selected().map(KindChips::label).orElse("All");
    setText(kind + " ▾");
    getAccessibleContext().setAccessibleName("Search kind: " + kind);
  }

  /** A fresh menu of the chips in order, the selected one marked. */
  public JPopupMenu popup() {
    JPopupMenu menu = new JPopupMenu();
    ButtonGroup group = new ButtonGroup();
    List<JToggleButton> all = chips.chips();
    for (int i = 0; i < all.size(); i++) {
      JToggleButton chip = all.get(i);
      Optional<EntityKind> kind = KindChips.KINDS.get(i);
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(chip.getText(), kind.equals(chips.selected()));
      item.addActionListener(e -> {
        chip.doClick();
        refresh();
      });
      group.add(item);
      menu.add(item);
    }
    return menu;
  }
}
