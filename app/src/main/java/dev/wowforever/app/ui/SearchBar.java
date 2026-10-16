package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The search box and kind filter. Typing searches after a {@value #DEBOUNCE_MILLIS} ms pause; Enter or a kind
 * change searches at once. Blank text never searches.
 */
public final class SearchBar extends JPanel {
  public static final int DEBOUNCE_MILLIS = 250;
  private static final String[] KIND_LABELS = {"All", "Items", "NPCs", "Objects", "Quests", "Zones",
      "Dungeons & Raids"};
  private static final EntityKind[] KINDS = {null, EntityKind.ITEM, EntityKind.NPC, EntityKind.OBJECT,
      EntityKind.QUEST, EntityKind.ZONE, EntityKind.INSTANCE};

  private final Consumer<ViewTarget.Search> onSearch;
  private final JTextField text = new JTextField(32);
  private final JComboBox<String> kind = new JComboBox<>(KIND_LABELS);
  private final Timer debounce;

  public SearchBar(Consumer<ViewTarget.Search> onSearch) {
    this(onSearch, DEBOUNCE_MILLIS);
  }

  SearchBar(Consumer<ViewTarget.Search> onSearch, int debounceMillis) {
    super(new BorderLayout(6, 0));
    this.onSearch = Objects.requireNonNull(onSearch, "onSearch");
    debounce = new Timer(debounceMillis, e -> fire());
    debounce.setRepeats(false);
    text.putClientProperty("JTextField.placeholderText", "Search items, NPCs, quests, zones… (Ctrl+F)");
    text.putClientProperty("JTextField.showClearButton", true);
    text.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        debounce.restart();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        debounce.restart();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        debounce.restart();
      }
    });
    text.addActionListener(e -> fire());
    kind.addActionListener(e -> fire());
    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    add(new JLabel("Search:"), BorderLayout.WEST);
    add(text, BorderLayout.CENTER);
    add(kind, BorderLayout.EAST);
  }

  /** Searches now for the current text and kind (cancelling a pending debounce); blank text does nothing. */
  void fire() {
    debounce.stop();
    String query = text.getText().strip();
    if (query.isEmpty()) {
      return;
    }
    onSearch.accept(new ViewTarget.Search(query, Optional.ofNullable(KINDS[kind.getSelectedIndex()])));
  }

  /** Moves the keyboard focus to the search box and selects its text. */
  public void focus() {
    text.requestFocusInWindow();
    text.selectAll();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    text.setEnabled(enabled);
    kind.setEnabled(enabled);
  }

  public JTextField textField() {
    return text;
  }

  public JComboBox<String> kindCombo() {
    return kind;
  }

  boolean debounceRunning() {
    return debounce.isRunning();
  }
}
