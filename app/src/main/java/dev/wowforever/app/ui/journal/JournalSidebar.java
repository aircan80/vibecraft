package dev.wowforever.app.ui.journal;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.theme.Theme;
import dev.wowforever.app.ui.widgets.RailIcons;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** The optional Journal shell's labelled navigation. Every destination uses the existing browser history. */
public final class JournalSidebar extends JPanel {
  private record Entry(String label, RailTarget icon, ViewTarget target, JButton button) {}
  private final Consumer<ViewTarget> navigate;
  private final List<Entry> entries = new ArrayList<>();
  private final JLabel brand = new JLabel("WoW Forever");
  private final JLabel edition = new JLabel("EXPLORER JOURNAL · BETA");
  private final JLabel browse = new JLabel("EXPLORE");
  private final JLabel footer = new JLabel("Offline knowledge. Ready to explore.");
  private boolean collapsed;
  private ViewTarget selected = new ViewTarget.Home();

  public JournalSidebar(Consumer<ViewTarget> navigate) {
    this.navigate = Objects.requireNonNull(navigate);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(20, 10, 12, 10));
    brand.setFont(brand.getFont().deriveFont(Font.BOLD, 19f));
    edition.setFont(edition.getFont().deriveFont(9f));
    browse.setFont(browse.getFont().deriveFont(Font.BOLD, 10f));
    footer.setFont(footer.getFont().deriveFont(10f));
    for (JLabel label : new JLabel[]{brand, edition, browse, footer}) label.setAlignmentX(LEFT_ALIGNMENT);
    add(brand); add(Box.createVerticalStrut(5)); add(edition); add(Box.createVerticalStrut(30));
    add(browse); add(Box.createVerticalStrut(9));
    addEntry("Home", RailTarget.HOME, new ViewTarget.Home());
    addEntry("World & maps", RailTarget.ZONES, new ViewTarget.WorldMap(947));
    addEntry("Cities", RailTarget.ZONES, new ViewTarget.Forever("cities"));
    addEntry("Dungeons & raids", RailTarget.INSTANCES, new ViewTarget.List(EntityKind.INSTANCE));
    addEntry("Gear & items", RailTarget.ITEMS, new ViewTarget.List(EntityKind.ITEM));
    addEntry("Quests", RailTarget.QUESTS, new ViewTarget.List(EntityKind.QUEST));
    addEntry("NPCs", RailTarget.NPCS, new ViewTarget.List(EntityKind.NPC));
    add(Box.createVerticalStrut(14));
    JButton character = addEntry("Character & systems", RailTarget.FOREVER, new ViewTarget.Forever("talents"));
    popup(character, new String[]{"Talents", "Legacy", "Races", "Camping", "Class spells"},
        new ViewTarget[]{new ViewTarget.Forever("talents"),
          new ViewTarget.Forever("legacy"), new ViewTarget.Forever("races"), new ViewTarget.Forever("camping"),
          new ViewTarget.Forever("class-spells")});
    JButton professions = addEntry("Professions & sets", RailTarget.ITEMS, new ViewTarget.ItemContent("recipes"));
    popup(professions, new String[]{"Recipes", "Item sets", "Trinkets", "XP food", "Comprehension", "Stat glossary"},
        new ViewTarget[]{new ViewTarget.ItemContent("recipes"), new ViewTarget.ItemContent("sets"),
          new ViewTarget.ItemContent("trinkets"), new ViewTarget.ItemContent("xp-food"),
          new ViewTarget.ItemContent("comprehension"), new ViewTarget.ItemContent("stats")});
    addEntry("What's changed", RailTarget.FOREVER, new ViewTarget.Forever("hub"));
    add(Box.createVerticalGlue()); add(Box.createVerticalStrut(18)); add(footer);
    setCollapsed(false);
    select(selected);
  }

  private JButton addEntry(String label, RailTarget icon, ViewTarget target) {
    JButton button = new JButton(label, RailIcons.shared().small(icon, 20));
    button.setHorizontalAlignment(SwingConstants.LEFT);
    button.setIconTextGap(11);
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.setBorder(new EmptyBorder(11, 8, 11, 6));
    button.putClientProperty("JButton.buttonType", "roundRect");
    button.setToolTipText(label);
    button.getAccessibleContext().setAccessibleName(label);
    button.addActionListener(e -> navigate.accept(target));
    button.addFocusListener(new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) { refreshTheme(); }
      @Override public void focusLost(FocusEvent e) { refreshTheme(); }
    });
    entries.add(new Entry(label, icon, target, button));
    add(button); add(Box.createVerticalStrut(3));
    return button;
  }

  private void popup(JButton button, String[] names, ViewTarget[] targets) {
    for (var listener : button.getActionListeners()) button.removeActionListener(listener);
    button.addActionListener(e -> {
      JPopupMenu menu = new JPopupMenu();
      for (int i = 0; i < names.length; i++) {
        ViewTarget target = targets[i];
        JMenuItem item = new JMenuItem(names[i]);
        item.addActionListener(event -> navigate.accept(target));
        menu.add(item);
      }
      menu.show(button, button.getWidth(), 0);
    });
  }

  /** Compact mode keeps all destinations available, with accessible names and hover labels. */
  public void setCollapsed(boolean collapsed) {
    this.collapsed = collapsed;
    brand.setText(collapsed ? "W" : "WoW Forever");
    brand.setToolTipText("WoW Forever · Explorer Journal");
    edition.setVisible(!collapsed); browse.setVisible(!collapsed); footer.setVisible(!collapsed);
    int width = collapsed ? 56 : 216;
    setPreferredSize(new Dimension(width, 600));
    setMinimumSize(new Dimension(width, 0));
    for (Entry entry : entries) {
      entry.button.setText(collapsed ? "" : entry.label);
      entry.button.setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
      entry.button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
      entry.button.setPreferredSize(new Dimension(width - 20, 44));
    }
    revalidate(); repaint();
  }

  public boolean isCollapsed() { return collapsed; }

  public void setReady(boolean ready) {
    for (Entry entry : entries) entry.button.setEnabled(ready);
  }

  public void select(ViewTarget target) {
    selected = Objects.requireNonNull(target);
    refreshTheme();
  }

  public void refreshTheme() {
    Theme t = Theme.current();
    setBackground(t.bgRaised());
    brand.setForeground(t.textPrimary());
    edition.setForeground(t.goldText()); browse.setForeground(t.textMuted()); footer.setForeground(t.textMuted());
    for (Entry entry : entries) {
      boolean active = matches(entry.target, selected);
      entry.button.setBackground(active ? t.selection() : t.bgRaised());
      entry.button.setForeground(t.textPrimary());
      entry.button.setSelected(active);
      entry.button.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(entry.button.hasFocus() || active ? t.goldText() : t.bgRaised()),
          new EmptyBorder(10, 7, 10, 5)));
      entry.button.getAccessibleContext().setAccessibleDescription(
          active ? "Current section: " + entry.label : "Open " + entry.label);
      entry.button.setIcon(RailIcons.shared().small(entry.icon, 20));
      entry.button.putClientProperty("JButton.selectedState", active ? "selected" : null);
    }
    repaint();
  }

  static boolean matches(ViewTarget route, ViewTarget target) {
    if (route instanceof ViewTarget.Forever city && city.section().equals("cities")) return target instanceof ViewTarget.Forever f && f.section().equals("cities");
    if (route instanceof ViewTarget.Home) return target instanceof ViewTarget.Home;
    if (route instanceof ViewTarget.WorldMap) return target instanceof ViewTarget.WorldMap
        || kind(target) == EntityKind.ZONE;
    if (route instanceof ViewTarget.List list && list.kind() == EntityKind.INSTANCE
        && target instanceof ViewTarget.Forever forever)
      return forever.section().equals("ladder") || forever.section().equals("discovery");
    if (route instanceof ViewTarget.List list) return kind(target) == list.kind()
        || list.kind() == EntityKind.INSTANCE && kind(target) == EntityKind.BOSS;
    if (route instanceof ViewTarget.ItemContent) return target instanceof ViewTarget.ItemContent;
    if (route instanceof ViewTarget.Forever f && target instanceof ViewTarget.Forever actual)
      return f.section().equals("hub")
          ? List.of("hub", "pvp", "pvp-faction").contains(actual.section())
          : List.of("talents", "legacy", "races", "race", "camping", "class-spells").contains(actual.section());
    return false;
  }

  private static EntityKind kind(ViewTarget target) {
    if (target instanceof ViewTarget.List list) return list.kind();
    if (target instanceof ViewTarget.Entity entity) return entity.ref().kind();
    if (target instanceof ViewTarget.Search search) return search.kind().orElse(null);
    return null;
  }
}
