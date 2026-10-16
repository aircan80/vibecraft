package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.LinkRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;

/** A link outside a table: borderless text in the link colour, with a hand cursor, that opens an entity. */
public final class LinkButton {
  private LinkButton() {}

  /** A link reading {@code text} that opens {@code ref}. */
  public static JButton create(String text, EntityRef ref, Navigator nav) {
    JButton b = new JButton(text) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        return LinkRenderer.linkColor();
      }
    };
    b.setBorderPainted(false);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setMargin(new Insets(0, 0, 0, 0));
    b.setBorder(BorderFactory.createEmptyBorder());
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setToolTipText("Open " + text);
    b.addActionListener(e -> nav.open(new ViewTarget.Entity(ref)));
    return b;
  }
}
