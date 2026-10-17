package dev.wowforever.app.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.HierarchyEvent;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

/**
 * The bottom line, {@value #HEIGHT} px tall on {@code bg.surface} under a gold {@linkplain Theme#hairline() hairline}:
 * the data's provenance on the left ({@link #provenanceText}: {@code "Forever beta 1.60.1 · 20,492 items"}), the
 * page message in the centre (loading, row counts, errors), and on the right the database build
 * ({@link #buildLabel}, the whole id in its tooltip) and the theme and layout toggles, which {@code MainFrame}
 * wires to the theme switch and the F10 layout switch. The provenance and the build are hidden while the line sits
 * in the compact window (an enclosing component carries {@link CompactColumns#KEY}), which has no room for them; the
 * toggles stay, as the layout toggle is a way back to the full window.
 */
public final class StatusBar extends JPanel {
  /** How many leading characters of the build id the status line and the home page show. */
  public static final int SHORT_BUILD_LENGTH = 8;
  /** The line's height, in logical pixels. */
  static final int HEIGHT = 22;
  /** The client product of the Forever beta, as the database's {@code meta} table names it. */
  static final String FOREVER_BETA = "wow_classic_beta";

  private final JLabel provenance = new JLabel(" ");
  private final JLabel message = new JLabel(" ", SwingConstants.CENTER);
  private final JLabel info = new JLabel(" ");
  private final JToggleButton themeToggle = toggle("Dark", "Dark theme (also in the View menu)", "Dark theme");
  private final JToggleButton layoutToggle = toggle("Compact", "Compact window (F10)", "Compact window");

  public StatusBar() {
    super(new BorderLayout(UIScale.scale(12), 0));
    setBorder(BorderFactory.createEmptyBorder(1, 8, 0, 4)); // the top pixel is the hairline
    JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, UIScale.scale(4), 0));
    right.setOpaque(false);
    right.add(info);
    right.add(themeToggle);
    right.add(layoutToggle);
    add(provenance, BorderLayout.WEST);
    add(message, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        boolean compact = CompactColumns.within(this);
        info.setVisible(!compact);
        provenance.setVisible(!compact);
      }
    });
  }

  /** A small borderless toggle button, as on a toolbar. */
  private static JToggleButton toggle(String text, String tooltip, String accessibleName) {
    JToggleButton b = new JToggleButton(text);
    b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    b.putClientProperty(FlatClientProperties.STYLE, "margin: 0,6,0,6");
    b.setToolTipText(tooltip);
    b.getAccessibleContext().setAccessibleName(accessibleName);
    b.setFocusable(false); // F10 and the View menu are the keyboard's way
    return b;
  }

  /** {@code "Data build 6c9ed1fa"}: the first {@value #SHORT_BUILD_LENGTH} characters of {@code buildId}. */
  public static String buildLabel(String buildId) {
    return "Data build " + buildId.substring(0, Math.min(SHORT_BUILD_LENGTH, buildId.length()));
  }

  /**
   * {@code "Forever beta 1.60.1 · 20,492 items"}: where the data comes from, from the database's {@code meta}
   * values {@code client_product} (the Forever beta's {@value #FOREVER_BETA} reads "Forever beta", any other product
   * its id), {@code client_build} (its first three parts) and {@code client_item_count}. Empty when any of them is
   * missing or the count is not a number.
   */
  public static String provenanceText(Map<String, String> meta) {
    String product = trimmed(meta.get("client_product"));
    String build = trimmed(meta.get("client_build"));
    String count = trimmed(meta.get("client_item_count"));
    if (product == null || build == null || count == null) {
      return "";
    }
    long items;
    try {
      items = Long.parseLong(count);
    } catch (NumberFormatException e) {
      return "";
    }
    String[] parts = build.split("\\.");
    String version = parts.length > 3 ? String.join(".", parts[0], parts[1], parts[2]) : build;
    String name = FOREVER_BETA.equals(product) ? "Forever beta" : product;
    return String.format(Locale.ROOT, "%s %s · %,d items", name, version, items);
  }

  /** {@code s} trimmed; null when it is null or blank. */
  private static String trimmed(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  /** Shows {@link #provenanceText} of {@code meta}, with the client product and its whole build in the tooltip. */
  public void setProvenance(Map<String, String> meta) {
    String text = provenanceText(meta);
    provenance.setText(text.isEmpty() ? " " : text);
    provenance.setToolTipText(text.isEmpty() ? null
        : "Data mined from the game client " + trimmed(meta.get("client_product")) + " "
            + trimmed(meta.get("client_build")));
  }

  /** The provenance as shown (even while hidden in the compact window); empty when there is none. */
  public String provenance() {
    return provenance.getText().isBlank() ? "" : provenance.getText();
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

  /** The dark theme toggle; {@code MainFrame} gives it its model and action. */
  public AbstractButton themeToggle() {
    return themeToggle;
  }

  /** The compact layout toggle; {@code MainFrame} gives it its model and action (F10's). */
  public AbstractButton layoutToggle() {
    return layoutToggle;
  }

  JLabel infoLabel() {
    return info;
  }

  JLabel provenanceLabel() {
    return provenance;
  }

  JLabel messageLabel() {
    return message;
  }

  /** As wide as its parts, always {@value #HEIGHT} px tall. */
  @Override
  public Dimension getPreferredSize() {
    return new Dimension(super.getPreferredSize().width, UIScale.scale(HEIGHT));
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(super.getMinimumSize().width, UIScale.scale(HEIGHT));
  }

  /** The surface, then the gold hairline along the top. */
  @Override
  protected void paintComponent(Graphics g) {
    Theme t = Theme.current();
    g.setColor(t.bgSurface());
    g.fillRect(0, 0, getWidth(), getHeight());
    g.setColor(t.hairline());
    g.fillRect(0, 0, getWidth(), 1);
  }

  /** The surface, for children that ask (looked up per call, so theme changes apply). */
  @Override
  public Color getBackground() {
    Theme t = Theme.current();
    return t != null ? t.bgSurface() : super.getBackground(); // null while the super constructor runs
  }
}
