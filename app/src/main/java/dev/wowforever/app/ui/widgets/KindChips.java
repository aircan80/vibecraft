package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.FlatClientProperties;
import dev.wowforever.app.model.EntityKind;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * The search kind filter as a row of pill-shaped toggle chips, All then Items, NPCs, Objects, Quests, Zones and
 * Dungeons &amp; Raids, each showing its hit count once the counts are known ("Items 9"). Exactly one chip is
 * selected; the chips never take the keyboard focus, so typing in the search box goes on while one is clicked. The
 * chips are the search dropdown's top row ({@link SearchDropdown}); given the width they have
 * ({@link #setWrapWidth}), they wrap onto as many rows as they need.
 */
public final class KindChips extends JPanel {
  /** The kinds of the chips in order; empty is All. */
  public static final List<Optional<EntityKind>> KINDS = List.of(Optional.empty(), Optional.of(EntityKind.ITEM),
      Optional.of(EntityKind.NPC), Optional.of(EntityKind.OBJECT), Optional.of(EntityKind.QUEST),
      Optional.of(EntityKind.ZONE), Optional.of(EntityKind.INSTANCE));

  /** The gap between chips and around them, in pixels. */
  private static final int GAP = 4;

  private final List<JToggleButton> chips = new ArrayList<>();
  private final ButtonGroup group = new ButtonGroup();
  private Optional<EntityKind> selected = Optional.empty();
  private Consumer<Optional<EntityKind>> onSelect = k -> {};
  /** The hit count of each kind once known, All's being their sum; empty without counts. */
  private final Map<Optional<EntityKind>, Integer> counts = new HashMap<>();
  /** The width the chips wrap within; 0 for one row. */
  private int wrapWidth;

  public KindChips() {
    super(new FlowLayout(FlowLayout.LEFT, GAP, GAP));
    setOpaque(false);
    for (Optional<EntityKind> kind : KINDS) {
      JToggleButton chip = new JToggleButton(label(kind));
      chip.setFocusable(false);
      chip.putClientProperty(FlatClientProperties.STYLE, "arc: 999");
      chip.setMargin(new Insets(2, 10, 2, 10));
      chip.getAccessibleContext().setAccessibleName(label(kind));
      chip.addActionListener(e -> chosen(kind));
      group.add(chip);
      chips.add(chip);
      add(chip);
    }
    chips.get(0).setSelected(true);
  }

  /** The chip label of {@code kind}: its plural, e.g. {@code "Items"}, {@code "Dungeons & Raids"}. */
  public static String label(EntityKind kind) {
    return switch (kind) {
      case ITEM -> "Items";
      case NPC -> "NPCs";
      case OBJECT -> "Objects";
      case QUEST -> "Quests";
      case ZONE -> "Zones";
      case INSTANCE -> "Dungeons & Raids";
      case BOSS -> "Bosses";
    };
  }

  private static String label(Optional<EntityKind> kind) {
    return kind.map(KindChips::label).orElse("All");
  }

  /** Called with the kind of a chip the user selects (not when {@link #select} sets one); replaces any earlier one. */
  public void onSelect(Consumer<Optional<EntityKind>> listener) {
    onSelect = Objects.requireNonNull(listener, "listener");
  }

  /** The selected kind; empty for All. */
  public Optional<EntityKind> selected() {
    return selected;
  }

  /** Selects the chip of {@code kind} without telling the listener. */
  public void select(Optional<EntityKind> kind) {
    int index = KINDS.indexOf(kind);
    if (index < 0) {
      throw new IllegalArgumentException("No chip for " + kind);
    }
    chips.get(index).setSelected(true);
    selected = kind;
  }

  /** Shows {@code counts} on the chips: each kind its own (0 when absent), All their sum. */
  public void setCounts(Map<EntityKind, Integer> counts) {
    int all = 0;
    for (Optional<EntityKind> kind : KINDS) {
      all += kind.map(k -> counts.getOrDefault(k, 0)).orElse(0);
    }
    this.counts.clear();
    for (int i = 0; i < KINDS.size(); i++) {
      Optional<EntityKind> kind = KINDS.get(i);
      int n = kind.isEmpty() ? all : counts.getOrDefault(kind.get(), 0);
      this.counts.put(kind, n);
      String count = String.format(Locale.ROOT, "%,d", n);
      chips.get(i).setText(label(kind) + " " + count);
      chips.get(i).getAccessibleContext().setAccessibleName(label(kind) + ", " + count + (n == 1 ? " result"
          : " results"));
    }
  }

  /** The hit count the chip of {@code kind} shows (All: the sum); empty while the chips show no counts. */
  public OptionalInt count(Optional<EntityKind> kind) {
    Integer n = counts.get(kind);
    return n == null ? OptionalInt.empty() : OptionalInt.of(n);
  }

  /** Shows the labels without counts (no search text). */
  public void clearCounts() {
    counts.clear();
    for (int i = 0; i < KINDS.size(); i++) {
      chips.get(i).setText(label(KINDS.get(i)));
      chips.get(i).getAccessibleContext().setAccessibleName(label(KINDS.get(i)));
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    chips.forEach(c -> c.setEnabled(enabled));
  }

  /**
   * Wraps the chips within {@code width} pixels (the border included): the preferred size is that wide and as tall as
   * the rows they then take ({@link #heightAt}). 0 lays them out on one row.
   */
  public void setWrapWidth(int width) {
    wrapWidth = Math.max(0, width);
    revalidate();
  }

  /** How tall the chips are laid out within {@code width} pixels, border included: one row or as many as they take. */
  public int heightAt(int width) {
    FlowLayout flow = (FlowLayout) getLayout();
    Insets in = getInsets();
    int room = width - in.left - in.right - 2 * flow.getHgap();
    int rows = 1;
    int rowHeight = 0;
    int x = 0;
    for (JToggleButton chip : chips) {
      Dimension d = chip.getPreferredSize();
      rowHeight = Math.max(rowHeight, d.height);
      if (x > 0 && x + d.width > room) { // as FlowLayout breaks its rows
        rows++;
        x = d.width;
      } else {
        x += (x > 0 ? flow.getHgap() : 0) + d.width;
      }
    }
    return in.top + in.bottom + rows * rowHeight + (rows + 1) * flow.getVgap();
  }

  @Override
  public Dimension getPreferredSize() {
    if (wrapWidth <= 0 || isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    return new Dimension(wrapWidth, heightAt(wrapWidth));
  }

  /** The chips in {@link #KINDS} order. */
  public List<JToggleButton> chips() {
    return List.copyOf(chips);
  }

  private void chosen(Optional<EntityKind> kind) {
    if (!kind.equals(selected)) {
      selected = kind;
      onSelect.accept(kind);
    }
  }
}
