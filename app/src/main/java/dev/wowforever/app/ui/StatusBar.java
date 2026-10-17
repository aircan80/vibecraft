package dev.wowforever.app.ui;

import dev.wowforever.app.ui.detail.CompactColumns;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The bottom line: a message on the left (loading, row counts, errors) and the database build on the right
 * ({@link #buildLabel}, the whole id in its tooltip). The build is hidden while the line sits in the compact window
 * (an enclosing component carries {@link CompactColumns#KEY}), which has no room for it.
 */
public final class StatusBar extends JPanel {
  /** How many leading characters of the build id the status line and the home page show. */
  public static final int SHORT_BUILD_LENGTH = 8;

  private final JLabel message = new JLabel(" ");
  private final JLabel info = new JLabel(" ");

  public StatusBar() {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    add(message, BorderLayout.CENTER);
    add(info, BorderLayout.EAST);
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        info.setVisible(!CompactColumns.within(this));
      }
    });
  }

  /** {@code "Data build 6c9ed1fa"}: the first {@value #SHORT_BUILD_LENGTH} characters of {@code buildId}. */
  public static String buildLabel(String buildId) {
    return "Data build " + buildId.substring(0, Math.min(SHORT_BUILD_LENGTH, buildId.length()));
  }

  public void setMessage(String text) {
    message.setText(text == null || text.isEmpty() ? " " : text);
  }

  public String message() {
    return message.getText().isBlank() ? "" : message.getText();
  }

  /** Shows {@code buildId} as {@link #buildLabel} with the whole id in the tooltip; null shows nothing. */
  public void setBuild(String buildId) {
    info.setText(buildId == null || buildId.isEmpty() ? " " : buildLabel(buildId));
    info.setToolTipText(buildId == null || buildId.isEmpty() ? null : buildId);
  }

  /** The build text as shown (even while hidden in the compact window); empty when there is none. */
  public String info() {
    return info.getText().isBlank() ? "" : info.getText();
  }

  JLabel infoLabel() {
    return info;
  }
}
