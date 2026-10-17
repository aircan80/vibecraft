package dev.wowforever.app.ui.pages.quest;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.ObjectiveRow;
import dev.wowforever.app.model.ObjectiveVerb;
import dev.wowforever.app.model.QuestSourceItem;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Numbered objective rows with explicit focus and entity links; all data is already loaded. */
public final class ObjectivesPanel extends JPanel {
  private final List<ObjectiveRowView> rows;
  private final JLabel carryLine;
  private final JLabel needLine;

  public ObjectivesPanel(List<ObjectiveRow> rows, List<QuestSourceItem> items, Navigator nav, IntConsumer onFocus) {
    this(rows, items, nav, onFocus, n -> {});
  }

  public ObjectivesPanel(List<ObjectiveRow> objectives, List<QuestSourceItem> items, Navigator nav,
      IntConsumer onFocus, IntConsumer onShowAll) {
    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    List<ObjectiveRowView> views = new ArrayList<>();
    for (ObjectiveRow row : objectives) {
      ObjectiveRowView view = new ObjectiveRowView(row, nav, onFocus, onShowAll);
      views.add(view);
      add(view);
    }
    rows = List.copyOf(views);
    carryLine = itemLine(items, "provided", "You carry: ", " (given at start)");
    needLine = itemLine(items, "required", "You will need: ", "");
    add(carryLine);
    add(needLine);
    getAccessibleContext().setAccessibleName("Quest objective locations");
  }

  private static JLabel itemLine(List<QuestSourceItem> items, String kind, String prefix, String suffix) {
    String names = items.stream().filter(i -> kind.equals(i.kind())).map(i -> i.displayName() + suffix)
        .collect(java.util.stream.Collectors.joining(", "));
    JLabel label = new JLabel(names.isEmpty() ? "" : prefix + names);
    label.setVisible(!names.isEmpty());
    label.setAlignmentX(LEFT_ALIGNMENT);
    return label;
  }

  public List<ObjectiveRowView> rows() { return rows; }
  public JLabel carryLine() { return carryLine; }
  public JLabel needLine() { return needLine; }

  /** One row's controls, exposed for keyboard navigation and headless acceptance checks. */
  public static final class ObjectiveRowView extends JPanel {
    private final ObjectiveRow row;
    private final JButton focusButton;
    private final List<JButton> targetLinks = new ArrayList<>();
    private final JTextArea text;
    private final JTextArea whereLine;
    private final JButton whereLink;
    private final JButton showAllButton;

    ObjectiveRowView(ObjectiveRow row, Navigator nav, IntConsumer onFocus, IntConsumer onShowAll) {
      this.row = row;
      setOpaque(false);
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setAlignmentX(LEFT_ALIGNMENT);
      setBorder(BorderFactory.createEmptyBorder(0, 0, UIScale.scale(10), 0));
      JPanel head = new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(4), 0));
      head.setOpaque(false);
      head.setAlignmentX(LEFT_ALIGNMENT);
      focusButton = new JButton(Integer.toString(row.number()));
      focusButton.getAccessibleContext().setAccessibleName("Show objective " + row.number() + " on the map");
      focusButton.addActionListener(e -> onFocus.accept(row.number()));
      head.add(focusButton);
      head.add(new JLabel(row.verb().word()));
      add(head);
      for (EntityRef target : row.targets()) {
        String name = row.targets().getFirst().equals(target) ? row.targetName() : row.sources().stream()
            .filter(s -> s.ref().equals(target)).map(s -> s.ref().displayName(s.name())).findFirst().orElse(null);
        JButton link = link(target.displayName(name), () -> nav.open(new ViewTarget.Entity(target)));
        targetLinks.add(link);
        add(link);
      }
      // A raw numeric reputation value is a required quantity, deliberately absent from this app.
      String description = ObjectiveText.description(row.text());
      text = muted(description);
      text.setVisible(!description.isBlank());
      add(text);
      String where = ObjectiveText.where(row);
      whereLine = muted(where);
      whereLink = link(where, () -> {
        if (row.instance() != null) nav.open(new ViewTarget.Entity(row.instance().ref()));
      });
      whereLink.setVisible(row.instance() != null);
      whereLine.setVisible(row.instance() == null);
      add(whereLine);
      add(whereLink);
      showAllButton = link("Show all " + row.sources().size() + " sources", () -> {
        onShowAll.accept(row.number());
        showAllButton().setVisible(false);
        revalidate();
      });
      showAllButton.setVisible(row.verb() == ObjectiveVerb.COLLECT && row.sources().size() > QuestMapPanel.TOP_SOURCES);
      add(showAllButton);
      MouseAdapter focus = new MouseAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON1) onFocus.accept(row.number());
        }
      };
      addMouseListener(focus);
      text.addMouseListener(focus);
      whereLine.addMouseListener(focus);
      head.addMouseListener(focus);
      getAccessibleContext().setAccessibleName("Objective " + row.number() + ": " + row.verb().word()
          + (row.targetName() == null ? "" : " " + row.targetName()));
    }

    @Override public Dimension getPreferredSize() {
      int width = getWidth() > 0 ? getWidth() : 480;
      text.setSize(width, 1);
      whereLine.setSize(width, 1);
      return super.getPreferredSize();
    }

    @Override public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public ObjectiveRow row() { return row; }
    public JButton focusButton() { return focusButton; }
    public List<JButton> targetLinks() { return List.copyOf(targetLinks); }
    public JTextArea text() { return text; }
    public JTextArea whereLine() { return whereLine; }
    public JButton whereLink() { return whereLink; }
    public JButton showAllButton() { return showAllButton; }
  }

  private static JTextArea muted(String text) {
    JTextArea area = new JTextArea(text) {
      @Override public Color getForeground() { return Theme.current().textMuted(); }
    };
    area.setFont(UIManager.getFont("Label.font"));
    area.setOpaque(false);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setAlignmentX(LEFT_ALIGNMENT);
    area.getAccessibleContext().setAccessibleName(text.isBlank() ? "Objective description" : text);
    return area;
  }

  private static JButton link(String text, Runnable action) {
    JButton button = new JButton(text);
    button.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(2), 0, UIScale.scale(2), 0));
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setHorizontalAlignment(JButton.LEADING);
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.addActionListener(e -> action.run());
    return button;
  }
}
