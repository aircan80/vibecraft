package dev.wowforever.app.ui;

import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** An every-launch choice: Classic stays available alongside the Explorer Journal preview. */
public final class ExperienceChooser {
  private ExperienceChooser() {}

  /** Shows a modal chooser on the EDT. Closing it or pressing Escape cancels startup. */
  public static Optional<UiExperience> choose() {
    AtomicReference<UiExperience> result = new AtomicReference<>();
    JDialog dialog = new JDialog((Window) null, "WoW Forever · Choose your experience",
        java.awt.Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setContentPane(content(choice -> {
      result.set(choice);
      dialog.dispose();
    }, dialog::dispose));
    dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.pack();
    dialog.setMinimumSize(new Dimension(620, 470));
    dialog.setLocationRelativeTo(null);
    dialog.setVisible(true);
    return Optional.ofNullable(result.get());
  }

  /** Builds the actual chooser without creating any native window or opening the database. */
  public static JPanel content(Consumer<UiExperience> onChoose, Runnable onCancel) {
    Theme theme = Theme.current();
    JPanel root = new JPanel(new BorderLayout(0, 24));
    root.setName("experience-chooser");
    root.setBackground(theme.bgBase());
    root.setBorder(BorderFactory.createEmptyBorder(30, 32, 24, 32));
    JPanel heading = new JPanel();
    heading.setOpaque(false);
    heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
    JLabel brand = new JLabel("WOW FOREVER");
    brand.setForeground(theme.goldText());
    brand.setFont(Fonts.headingSmallCaps(15));
    heading.add(brand);
    heading.add(Box.createVerticalStrut(10));
    JLabel title = new JLabel("Choose your adventure");
    title.setFont(Fonts.heading(29));
    title.setForeground(theme.textPrimary());
    heading.add(title);
    heading.add(Box.createVerticalStrut(9));
    JLabel intro = new JLabel("Two ways to explore. The same world and source data.");
    intro.setForeground(theme.textMuted());
    heading.add(intro);
    root.add(heading, BorderLayout.NORTH);

    JPanel choices = new JPanel(new GridLayout(1, 2, 18, 0));
    choices.setOpaque(false);
    choices.add(card("Original interface", "FAMILIAR", "Your current browser",
        "The familiar navigation and screens you use today.",
        "Open original interface", KeyEvent.VK_O, UiExperience.CLASSIC, onChoose));
    choices.add(card("Explorer Journal", "NEW PREVIEW", "A fresh way to explore",
        "A labelled sidebar, guided discovery, and a clearer journey from bosses to loot.",
        "Open Explorer Journal", KeyEvent.VK_J, UiExperience.JOURNAL, onChoose));
    root.add(choices, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout(12, 0));
    footer.setOpaque(false);
    JLabel note = new JLabel("We’ll ask each time you launch.");
    note.setForeground(theme.textMuted());
    footer.add(note, BorderLayout.CENTER);
    JButton cancel = new JButton("Cancel");
    cancel.setName("cancel-startup");
    cancel.addActionListener(e -> onCancel.run());
    footer.add(cancel, BorderLayout.EAST);
    root.add(footer, BorderLayout.SOUTH);
    root.setPreferredSize(new Dimension(720, 470));
    return root;
  }

  private static JPanel card(String title, String badge, String subtitle, String description,
      String action, int mnemonic, UiExperience experience, Consumer<UiExperience> onChoose) {
    Theme theme = Theme.current();
    JPanel card = new JPanel(new BorderLayout(0, 20));
    card.setBackground(theme.bgRaised());
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(theme.hairline()), BorderFactory.createEmptyBorder(20, 20, 20, 20)));
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    JLabel tag = new JLabel(badge);
    tag.setForeground(theme.goldText());
    tag.setFont(tag.getFont().deriveFont(java.awt.Font.BOLD, 11f));
    text.add(tag);
    text.add(Box.createVerticalStrut(12));
    JLabel name = new JLabel(title);
    name.setFont(Fonts.heading(23));
    name.setForeground(theme.textPrimary());
    text.add(name);
    text.add(Box.createVerticalStrut(12));
    JLabel summary = new JLabel(subtitle);
    summary.setFont(summary.getFont().deriveFont(java.awt.Font.BOLD));
    text.add(summary);
    text.add(Box.createVerticalStrut(8));
    JLabel detail = new JLabel("<html><div style='width:220px'>" + description + "</div></html>");
    detail.setForeground(theme.textMuted());
    text.add(detail);
    card.add(text, BorderLayout.CENTER);
    JButton button = new JButton(action);
    button.setName("choose-" + experience.name().toLowerCase(java.util.Locale.ROOT));
    button.setMnemonic(mnemonic);
    button.setToolTipText("Start using " + title);
    button.getAccessibleContext().setAccessibleDescription(description);
    button.addActionListener(e -> onChoose.accept(experience));
    card.add(button, BorderLayout.SOUTH);
    return card;
  }
}
