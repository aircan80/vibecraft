package dev.wowforever.app.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The bottom line: a message on the left (loading, row counts, errors) and database info on the right. */
public final class StatusBar extends JPanel {
  private final JLabel message = new JLabel(" ");
  private final JLabel info = new JLabel(" ");

  public StatusBar() {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    add(message, BorderLayout.CENTER);
    add(info, BorderLayout.EAST);
  }

  public void setMessage(String text) {
    message.setText(text == null || text.isEmpty() ? " " : text);
  }

  public String message() {
    return message.getText().isBlank() ? "" : message.getText();
  }

  public void setInfo(String text) {
    info.setText(text == null || text.isEmpty() ? " " : text);
  }

  public String info() {
    return info.getText().isBlank() ? "" : info.getText();
  }
}
