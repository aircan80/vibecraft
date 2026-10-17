package dev.wowforever.app.ui.detail;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.Chip;
import dev.wowforever.app.ui.widgets.LinkButton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;

/**
 * The top of a detail page, the shared page skeleton: an optional {@value #PLATE} px icon plate (navy in both
 * themes, {@link #setPlate}), the entity name in {@value #TITLE_SIZE} pt Marcellus ({@link Fonts#heading}, at the
 * current scale, so it follows the text size), optionally followed by badges such as a rank chip and a muted kind
 * word ({@link #setKindWord}); then a subtitle line that may end in links; then a one-line strip of positive facts
 * ({@link #setFacts}) followed by outline chips ({@link #addChip}, {@link #addChipLink}), shown once it has either.
 * The title may be coloured (an item's quality colour, say).
 */
public final class HeaderPanel extends JPanel {
  private static final String SEPARATOR = " · ";
  private static final int BADGE_GAP = 8;
  private static final int CHIP_GAP = 6;
  /** The title's size in points at 100 % text size (Marcellus, {@link Fonts#heading}). */
  static final float TITLE_SIZE = 20f;
  /** The kind word's size in points at 100 % text size (Marcellus SC, {@link Fonts#headingSmallCaps}). */
  static final float KIND_SIZE = 12f;
  /** The icon plate's side in logical pixels. */
  static final int PLATE = 28;
  private static final int PLATE_ARC = 6;

  private final JLabel title;
  private final JPanel titleRow = new JPanel();
  private final Plate plate = new Plate();
  private final Component plateGap = Box.createHorizontalStrut(UIScale.scale(BADGE_GAP));
  private final JLabel kindWord = new JLabel() {
    @Override
    public Color getForeground() { // looked up per paint, so theme changes apply
      Theme t = Theme.current();
      return t != null ? t.textMuted() : super.getForeground();
    }

    @Override
    public void updateUI() { // the font again at the new scale, so it follows the text size
      super.updateUI();
      setFont(Fonts.headingSmallCaps(UIScale.scale(KIND_SIZE)));
    }
  };
  private final Component kindGap = Box.createHorizontalStrut(UIScale.scale(BADGE_GAP));
  private final JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final JPanel factsRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
  private final JLabel facts = mutedLabel();
  private final JLabel note = mutedLabel();
  private final List<JButton> links = new ArrayList<>();
  private final List<JComponent> chips = new ArrayList<>();
  private String subtitle;
  private Supplier<Color> titleColor;

  public HeaderPanel(String title, String subtitle) {
    this.title = new JLabel(title) {
      @Override
      public Color getForeground() { // looked up per paint, so theme changes apply
        Supplier<Color> colour = titleColor;
        return colour != null ? colour.get() : super.getForeground();
      }

      @Override
      public void updateUI() { // the font again at the new scale, so it follows the text size
        super.updateUI();
        setFont(Fonts.heading(UIScale.scale(TITLE_SIZE)));
      }
    };
    this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
    this.title.setAlignmentY(CENTER_ALIGNMENT);
    titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.LINE_AXIS));
    titleRow.setAlignmentX(LEFT_ALIGNMENT);
    titleRow.setOpaque(false);
    plate.setVisible(false);
    plateGap.setVisible(false);
    titleRow.add(plate);
    titleRow.add(plateGap);
    titleRow.add(this.title);
    kindWord.setAlignmentY(CENTER_ALIGNMENT);
    kindWord.setVisible(false);
    kindGap.setVisible(false);
    subtitleRow.setAlignmentX(LEFT_ALIGNMENT);
    subtitleRow.setOpaque(false);
    subtitleRow.add(new JLabel(subtitle));
    factsRow.setAlignmentX(LEFT_ALIGNMENT);
    factsRow.setOpaque(false);
    factsRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
    factsRow.add(facts);
    factsRow.setVisible(false);
    note.setAlignmentX(LEFT_ALIGNMENT);
    note.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
    note.setVisible(false);
    add(titleRow);
    add(subtitleRow);
    add(factsRow);
    add(note);
  }

  /**
   * Shows {@code text} muted on its own line under the facts (an item page's "Stats hidden until this item drops on a
   * realm"); null or blank hides it.
   */
  public void setNote(String text) {
    boolean shown = text != null && !text.isBlank();
    note.setText(shown ? text : null);
    note.setVisible(shown);
    revalidate();
  }

  /** The note as read; null when there is none. */
  public String note() {
    return note.isVisible() ? note.getText() : null;
  }

  /** A label in the muted text colour, looked up per paint so theme changes apply. */
  private static JLabel mutedLabel() {
    return new JLabel() {
      @Override
      public Color getForeground() {
        Theme t = Theme.current();
        return t != null ? t.textMuted() : super.getForeground();
      }
    };
  }

  /** Shows {@code badge} after the title (and any earlier badges), vertically centred on it. */
  public void addBadge(JComponent badge) {
    badge.setAlignmentY(CENTER_ALIGNMENT);
    int at = kindWord.getParent() == titleRow ? titleRow.getComponentZOrder(kindGap) : titleRow.getComponentCount();
    titleRow.add(Box.createHorizontalStrut(UIScale.scale(BADGE_GAP)), at);
    titleRow.add(badge, at + 1);
  }

  /** Shows {@code icon} centred on a navy plate before the title; null removes the plate. */
  public void setPlate(Icon icon) {
    plate.icon = icon;
    plate.setVisible(icon != null);
    plateGap.setVisible(icon != null);
    revalidate();
    repaint();
  }

  /**
   * Shows {@code word} ({@code "NPC"}, {@code "Quest"}) after the title and its badges, muted, in small caps; null
   * or blank removes it.
   */
  public void setKindWord(String word) {
    boolean shown = word != null && !word.isBlank();
    kindWord.setText(shown ? word : null);
    if (shown && kindWord.getParent() == null) {
      titleRow.add(kindGap);
      titleRow.add(kindWord);
    }
    kindWord.setVisible(shown);
    kindGap.setVisible(shown);
    revalidate();
  }

  /** The kind word; null when there is none. */
  public String kindWord() {
    return kindWord.isVisible() ? kindWord.getText() : null;
  }

  /**
   * Shows {@code text}, the page's positive facts ({@code "1 NPC · 1 quest"}), on the facts line, before any chips;
   * null or empty leaves the line to the chips (or hides it without them).
   */
  public void setFacts(String text) {
    facts.setText(text == null ? "" : text);
    facts.setVisible(!facts.getText().isEmpty());
    updateFactsRow();
  }

  /** The facts as read; empty when there are none. */
  public String facts() {
    return facts.getText();
  }

  /** Adds {@code chip} to the facts line, after the facts and the chips added before it. */
  public void addChip(Chip chip) {
    addToFactsRow(chip);
  }

  /**
   * Adds a chip reading {@code text} that opens {@code ref} (a zone, say) to the facts line, after the facts and the
   * chips added before it: link text in an outline. Returns its button.
   */
  public JButton addChipLink(String text, EntityRef ref, Navigator nav) {
    JButton b = LinkButton.create(text, ref, nav);
    b.setBorder(new ChipBorder());
    b.setBorderPainted(true);
    addToFactsRow(b);
    return b;
  }

  private void addToFactsRow(JComponent chip) {
    if (!facts.getText().isEmpty() || !chips.isEmpty()) {
      factsRow.add(Box.createHorizontalStrut(UIScale.scale(CHIP_GAP)));
    }
    factsRow.add(chip);
    chips.add(chip);
    updateFactsRow();
  }

  private void updateFactsRow() {
    factsRow.setVisible(!facts.getText().isEmpty() || !chips.isEmpty());
    revalidate();
    repaint();
  }

  /** The chips of the facts line, in order. */
  public List<JComponent> chips() {
    return List.copyOf(chips);
  }

  /**
   * Appends {@code " · <text>"} to the subtitle, {@code text} being a link button that opens {@code ref}.
   * Returns the button.
   */
  public JButton addLink(String text, EntityRef ref, Navigator nav) {
    return addLink(text, new ViewTarget.Entity(ref), nav);
  }

  /** Appends {@code " · <text>"} to the subtitle, {@code text} being a link button that opens {@code target}. */
  public JButton addLink(String text, ViewTarget target, Navigator nav) {
    JButton b = LinkButton.create(text, target, nav);
    subtitleRow.add(new JLabel(subtitle.isEmpty() ? "" : SEPARATOR));
    subtitleRow.add(b);
    subtitle = subtitle.isEmpty() ? text : subtitle + SEPARATOR + text;
    links.add(b);
    return b;
  }

  public String title() {
    return title.getText();
  }

  /**
   * Shows or hides the title row (the plate, the title, its badges and the kind word), leaving the subtitle and facts
   * lines: for a page that shows the name elsewhere, such as an item's tooltip card.
   */
  public void setTitleShown(boolean shown) {
    titleRow.setVisible(shown);
  }

  /** Whether the title row is shown. */
  public boolean isTitleShown() {
    return titleRow.isVisible();
  }

  /** Colours the title with {@code colour}, asked on every paint; null restores the look-and-feel colour. */
  public void setTitleColor(Supplier<Color> colour) {
    titleColor = colour;
    title.repaint();
  }

  /** The colour the title is painted in. */
  public Color titleColor() {
    return title.getForeground();
  }

  /** The subtitle line as read, link texts included. */
  public String subtitle() {
    return subtitle;
  }

  /** The link buttons of the subtitle line, in order. */
  public List<JButton> links() {
    return List.copyOf(links);
  }

  /** The icon plate before the title (hidden until {@link #setPlate}). */
  JComponent plate() {
    return plate;
  }

  /** The facts line: the facts label, then the chips. */
  JPanel factsRow() {
    return factsRow;
  }

  public JLabel factsLabel() {
    return facts;
  }

  /** A {@value #PLATE} px rounded square in {@link Theme#plate} (navy in both themes) with an icon centred on it. */
  private static final class Plate extends JComponent {
    private Icon icon;

    Plate() {
      setAlignmentY(CENTER_ALIGNMENT);
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      int side = UIScale.scale(PLATE);
      return new Dimension(side, side);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.current().plate());
        float arc = UIScale.scale(PLATE_ARC);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
        if (icon != null) {
          icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
        }
      } finally {
        g2.dispose();
      }
    }
  }

  /** The outline of a chip link: a rounded rectangle in the muted text colour around padded text. */
  private static final class ChipBorder extends AbstractBorder {
    private static final int PAD_X = 6;
    private static final int PAD_Y = 1;
    private static final int ARC = 8;

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(Theme.current().textMuted());
        float arc = UIScale.scale(ARC);
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1, height - 1, arc, arc));
      } finally {
        g2.dispose();
      }
    }

    @Override
    public java.awt.Insets getBorderInsets(Component c, java.awt.Insets insets) {
      insets.set(UIScale.scale(PAD_Y), UIScale.scale(PAD_X), UIScale.scale(PAD_Y), UIScale.scale(PAD_X));
      return insets;
    }
  }
}
