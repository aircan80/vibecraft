package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.LootFilter;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;


/** Equipment slot is Item.InventoryType, never the addon entry's ordering number. */
final class LootFilterControls extends JPanel {
  record Choice(String label, Integer id) { @Override public String toString() { return label; } }
  private final JComboBox<Choice> classes = new JComboBox<>(new Choice[] {
      new Choice("All classes", null), new Choice("Warrior",1), new Choice("Paladin",2),
      new Choice("Hunter",3), new Choice("Rogue",4), new Choice("Priest",5), new Choice("Shaman",7),
      new Choice("Mage",8), new Choice("Warlock",9), new Choice("Druid",11)});
  private final JComboBox<Choice> slots = new JComboBox<>(new Choice[] {
      new Choice("All slots",null), new Choice("Head",1), new Choice("Neck",2), new Choice("Shoulder",3),
      new Choice("Shirt",4), new Choice("Chest / robe",5), new Choice("Waist",6), new Choice("Legs",7),
      new Choice("Feet",8), new Choice("Wrist",9), new Choice("Hands",10), new Choice("Finger",11),
      new Choice("Trinket",12), new Choice("One-hand",13), new Choice("Shield",14), new Choice("Ranged",15),
      new Choice("Back",16), new Choice("Two-hand",17), new Choice("Bag",18), new Choice("Tabard",19),
      new Choice("Main hand",21), new Choice("Off hand",22),
      new Choice("Held in off hand",23), new Choice("Ammo",24), new Choice("Thrown",25),
      new Choice("Quiver",27), new Choice("Relic",28)});
  private final JComboBox<Choice> resistance = new JComboBox<>(new Choice[] {
      new Choice("Any resistance",null), new Choice("Fire",51), new Choice("Frost",52),
      new Choice("Holy",53), new Choice("Shadow",54), new Choice("Nature",55),
      new Choice("Arcane",56), new Choice("All resistances (stat)",124)});
  private final javax.swing.JSpinner minimum = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(1,0,10000,1));
  private boolean restoring;
  LootFilterControls(Runnable changed) {
    super(new java.awt.GridLayout(0,2,8,4));
    setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,128));
    resistance.setName("loot-resistance-filter"); minimum.setName("loot-resistance-minimum");
    resistance.getAccessibleContext().setAccessibleName("Loot recorded resistance school");
    minimum.getAccessibleContext().setAccessibleName("Minimum recorded resistance");
    resistance.setToolTipText("Explicit numeric Forever stats only. Missing values stay unknown. All-resistance stat is separate; not a derived total.");
    minimum.setToolTipText("Minimum recorded value, inclusive. Explicit zero is known; missing is unknown.");
    minimum.setEnabled(false);
    classes.setName("loot-class-filter"); slots.setName("loot-slot-filter");
    classes.getAccessibleContext().setAccessibleName("Loot class restriction");
    slots.getAccessibleContext().setAccessibleName("Loot equipment slot");
    classes.setToolTipText("Forever class restrictions only; does not check armor or weapon proficiency.");
    slots.setToolTipText("Forever equipment slot; unknown fields are counted separately.");
    JLabel classLabel=new JLabel("Class restriction"); classLabel.setLabelFor(classes);
    JLabel slotLabel=new JLabel("Slot"); slotLabel.setLabelFor(slots);
    add(classLabel); add(classes); add(slotLabel); add(slots);
    JLabel resistanceLabel=new JLabel("Recorded resistance"); resistanceLabel.setLabelFor(resistance);
    JLabel minimumLabel=new JLabel("Minimum value"); minimumLabel.setLabelFor(minimum);
    add(resistanceLabel); add(resistance); add(minimumLabel); add(minimum);
    resistance.addActionListener(e -> {
      minimum.setEnabled(((Choice) resistance.getSelectedItem()).id()!=null);
      if (!restoring) changed.run();
    });
    minimum.addChangeListener(e -> { if (!restoring) changed.run(); });
    classes.addActionListener(e -> { if (!restoring) changed.run(); }); slots.addActionListener(e -> { if (!restoring) changed.run(); });
  }
  void restore(LootFilter filter) {
    restoring = true;
    try {
      select(classes,filter.classId()); select(slots,filter.inventoryType());
      select(resistance,filter.resistanceStat()); minimum.setValue(filter.minimumResistance());
    }
    finally { restoring = false; }
  }
  private void select(JComboBox<Choice> box, Integer id) {
    for (int i=0;i<box.getItemCount();i++) {
      if (java.util.Objects.equals(box.getItemAt(i).id(),id)) { box.setSelectedIndex(i); return; }
    }
    box.setSelectedIndex(0);
  }
  LootFilter filter() { return new LootFilter(((Choice) classes.getSelectedItem()).id(),
      ((Choice) slots.getSelectedItem()).id(), ((Choice) resistance.getSelectedItem()).id(), (Integer) minimum.getValue()); }
}
