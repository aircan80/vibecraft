package dev.wowforever.app.catalog;

import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/** Embeddable game selector, distinct from the Original/Journal interface chooser. */
public final class EditionPicker extends JPanel {
  public EditionPicker(EditionRegistry registry, CatalogSession session, Consumer<CatalogSession.Token> selected) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setName("game-edition-picker");
    getAccessibleContext().setAccessibleName("Game catalog");
    add(new JLabel("Game catalog"));
    ButtonGroup group = new ButtonGroup();
    for (EditionRegistry.Edition edition : registry.editions()) {
      JRadioButton choice = new JRadioButton(edition.toString());
      choice.setName("edition-" + edition.id());
      choice.setEnabled(edition.installed());
      choice.setSelected(session.token().context().editionId().equals(edition.id()));
      choice.setToolTipText(edition.installed() ? "Uses this game's verified data only"
          : "A validated gameplay catalog must be installed before this game can be selected");
      choice.addActionListener(event -> {
        if (!session.token().context().editionId().equals(edition.id())) {
          selected.accept(session.select(edition.id()));
        }
      });
      group.add(choice); add(choice);
    }
    add(new JLabel("Other games need their own validated data. Era comparison data is not a Classic catalog."));
  }
}
