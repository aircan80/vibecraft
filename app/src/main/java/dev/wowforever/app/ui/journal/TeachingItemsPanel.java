package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import javax.swing.*;

/** Source-backed teaching items reuse the ordinary item acquisition journey. No SQL in rendering. */
public final class TeachingItemsPanel extends JPanel {
  public TeachingItemsPanel(Snapshot data,Navigator nav) {
    super(new JournalSurface.Stack());setOpaque(false);
    JournalSurface.add(this,JournalSurface.title("Teaching items",24));
    JournalSurface.add(this,JournalSurface.copy(data.coverage()));
    if(data.items().isEmpty())return;
    JournalSurface.add(this,JournalSurface.copy("Forever "+data.build()+" · "+data.locale()+". Open an item to inspect any separately reported vendor, drop or quest relationships. Coverage may be empty. Reported relationships do not prove current stock, price, drop chance or beta availability."));
    for(var item:data.items()) {
      JPanel card=JournalSurface.card();JournalSurface.add(card,JournalSurface.title(item.name(),20));
      JButton open=new JButton("Open item & acquisition");open.getAccessibleContext().setAccessibleName("Open "+item.name()+" and acquisition evidence");open.addActionListener(e->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM,item.itemId()))));JournalSurface.add(card,open);
      for(var support:item.supports()){JournalSurface.add(card,JournalSurface.copy(support.relationship()));JournalSurface.add(card,JournalSurface.copy(support.condition()));}
      JPanel details=JournalSurface.stack();details.setVisible(false);
      JournalSurface.add(details,JournalSurface.copy("Snapshot "+data.snapshotId()+" · Creation spell #"+data.spellId()));
      for(var support:item.supports())for(var evidence:support.evidence()){
        JournalSurface.add(details,JournalSurface.copy(evidence.table()+" · "+evidence.path()+":"+evidence.line()));
        JournalSurface.add(details,JournalSurface.copy(evidence.fields().toString()));
      }
      JToggleButton disclosure=new JToggleButton("Show teaching source evidence");disclosure.addActionListener(e->{details.setVisible(disclosure.isSelected());disclosure.setText(disclosure.isSelected()?"Hide teaching source evidence":"Show teaching source evidence");revalidate();});
      JournalSurface.add(card,disclosure);JournalSurface.add(card,details);JournalSurface.add(this,card);
    }
  }
}
