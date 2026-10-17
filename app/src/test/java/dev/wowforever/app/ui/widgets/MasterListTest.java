package dev.wowforever.app.ui.widgets;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterListTest {
  private final ManualDelay delay = new ManualDelay();
  private final MasterList list = new MasterList(delay);
  private final List<ListEntry> previews = new ArrayList<>();
  private final List<ListEntry> commits = new ArrayList<>();

  private static List<ListEntry> entries(int n) {
    return IntStream.range(0, n)
        .mapToObj(i -> new ListEntry(new EntityRef(ITEM, i + 1), "Item " + i, "Armor · Cloth", null, null))
        .toList();
  }

  @BeforeEach
  void wire() {
    list.onPreview(previews::add);
    list.onCommit(commits::add);
  }

  @Test
  void holdingArrowCoalescesPreviews() {
    List<ListEntry> rows = entries(60);
    list.setEntries(rows);
    for (int i = 0; i < 50; i++) {
      list.selectIndex(i);
    }
    assertThat(previews).isEmpty();
    delay.fire();
    assertThat(previews).containsExactly(rows.get(49));
    assertThat(delay.pending()).isZero();
  }

  @Test
  void anEmptyListBeforeAnySearchShowsAPlaceholderInsteadOfAHeading() {
    assertThat(list.isPlaceholderShown()).isTrue();
    assertThat(list.headingLabel().isVisible()).isFalse();
    assertThat(list.placeholder().getText()).isEqualTo(MasterList.PLACEHOLDER).isEqualTo("Search results appear here");
    assertThat(list.placeholder().getForeground()).isEqualTo(Theme.current().textMuted());

    list.show("Search: westfall", entries(2), false);
    assertThat(list.isPlaceholderShown()).isFalse();
    assertThat(list.placeholder().isVisible()).isFalse();
    assertThat(list.headingLabel().isVisible()).isTrue();
    assertThat(list.heading()).isEqualTo("Search: westfall (2)");

    list.show("Search: nothing", List.of(), false); // a search without hits keeps its heading
    assertThat(list.isPlaceholderShown()).isFalse();
    assertThat(list.heading()).isEqualTo("Search: nothing (0)");

    list.clear();
    assertThat(list.isPlaceholderShown()).isTrue();
    assertThat(list.headingLabel().isVisible()).isFalse();
    assertThat(list.entries()).isEmpty();
  }

  @Test
  void previewDelayIs120Millis() {
    assertThat(MasterList.PREVIEW_DELAY_MILLIS).isEqualTo(120);
  }

  @Test
  void enterCommitsAtOnceAndDropsThePendingPreview() {
    List<ListEntry> rows = entries(5);
    list.setEntries(rows);
    list.selectIndex(2);
    JList<ListEntry> jlist = list.jlist();
    Object key = jlist.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
    jlist.getActionMap().get(key).actionPerformed(new ActionEvent(jlist, ActionEvent.ACTION_PERFORMED, ""));
    assertThat(commits).containsExactly(rows.get(2));
    assertThat(delay.pending()).isZero();
    delay.fire();
    assertThat(previews).isEmpty();
  }

  private void click(int row, int clickCount, int y) {
    JList<ListEntry> jlist = list.jlist();
    Rectangle cell = jlist.getCellBounds(row, row);
    jlist.dispatchEvent(new MouseEvent(jlist, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK,
        cell.x + 5, y < 0 ? cell.y + 5 : y, clickCount, false, MouseEvent.BUTTON1));
  }

  @Test
  void singleClickCommits() {
    List<ListEntry> rows = entries(5);
    list.setEntries(rows);
    list.jlist().setSize(300, 600);
    list.selectIndex(3); // the press selects the row and schedules its preview
    click(3, 1, -1);
    assertThat(commits).containsExactly(rows.get(3));
    assertThat(delay.pending()).isZero();
    delay.fire();
    assertThat(previews).isEmpty();
  }

  @Test
  void theSecondClickOfADoubleClickIsIgnored() {
    List<ListEntry> rows = entries(5);
    list.setEntries(rows);
    list.jlist().setSize(300, 600);
    click(2, 1, -1);
    click(2, 2, -1);
    assertThat(commits).containsExactly(rows.get(2));
  }

  @Test
  void clickBelowTheLastRowCommitsNothing() {
    list.setEntries(entries(2));
    JList<ListEntry> jlist = list.jlist();
    jlist.setSize(300, 600);
    Rectangle last = jlist.getCellBounds(1, 1);
    click(1, 1, last.y + last.height + 20);
    assertThat(commits).isEmpty();
  }

  @Test
  void replacingTheEntriesDropsThePendingPreview() {
    list.setEntries(entries(5));
    list.selectIndex(1);
    list.setEntries(entries(3));
    delay.fire();
    assertThat(previews).isEmpty();
    assertThat(list.entries()).hasSize(3);
    assertThat(list.selected()).isEmpty();
  }

  @Test
  void headingShowsTheCount() {
    list.show("Armor", entries(2), false);
    assertThat(list.heading()).isEqualTo("Armor (2)");
    list.show("Search: a", entries(2), true);
    assertThat(list.heading()).isEqualTo("Search: a (first 2)");
  }

  @Test
  void rendererShowsTitleAndSubtitle() {
    ListEntry entry = new ListEntry(new EntityRef(ITEM, 14149), "Subterranean Cape", "Armor · Cloak · iLvl 24",
        null, null);
    list.setEntries(List.of(entry));
    JList<ListEntry> jlist = list.jlist();
    Component c = jlist.getCellRenderer().getListCellRendererComponent(jlist, entry, 0, false, false);
    assertThat(c).isInstanceOfSatisfying(MasterList.EntryRenderer.class, r -> {
      assertThat(r.title().getText()).isEqualTo("Subterranean Cape");
      assertThat(r.subtitle().getText()).isEqualTo("Armor · Cloak · iLvl 24");
      assertThat(r.title().getForeground()).isEqualTo(Theme.current().textPrimary());
      assertThat(r.subtitle().getForeground()).isEqualTo(Theme.current().textMuted());
      assertThat(r.subtitle().getFont().getSize2D()).isLessThan(r.title().getFont().getSize2D());
    });
    c.setBounds(0, 0, 300, jlist.getFixedCellHeight());
    c.validate(); // as the list's renderer pane does before painting a cell
    MasterList.EntryRenderer r = (MasterList.EntryRenderer) c;
    assertThat(r.title().getWidth()).isPositive();
    assertThat(r.subtitle().getY()).isGreaterThanOrEqualTo(r.title().getY() + r.title().getHeight());
    assertThat(jlist.getFixedCellHeight())
        .isGreaterThanOrEqualTo(c.getPreferredSize().height)
        .isGreaterThan(jlist.getFontMetrics(jlist.getFont()).getHeight() * 2 - 1);
  }

  @Test
  void entriesReadAsTheirTitle() {
    ListEntry entry = new ListEntry(new EntityRef(ITEM, 1), "Linen Cloth", "Trade Goods", null, null);
    assertThat(entry).hasToString("Linen Cloth");
  }
}
