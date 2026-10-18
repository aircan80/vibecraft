package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NativeRecipeGuideRepository;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.BorderLayout;
import java.awt.Insets;
import javax.swing.*;

/** A recipe's exact native evidence, supplied as an immutable worker snapshot. */
public final class RecipeFieldGuidePanel extends JPanel {
  public RecipeFieldGuidePanel(NativeRecipeGuideRepository.Snapshot data,Navigator nav) {
    super(new BorderLayout());var body=new JournalSurface.Body();add(JournalSurface.scroll(body));
    JournalSurface.add(body,JournalSurface.title("Crafting field guide",30));
    if(!data.available()) {
      JournalSurface.add(body,JournalSurface.copy("No exact create-item recipe is retained for this item in the selected Forever snapshot. This does not establish that the item cannot be crafted."));return;
    }
    JournalSurface.add(body,JournalSurface.copy("Forever build "+data.build()+". Follow a reagent to its item page to inspect its recorded acquisition sources. Trainers and recipe availability are not inferred."));
    for(var recipe:data.recipes()) {
      JPanel card=JournalSurface.card();JournalSurface.add(card,JournalSurface.title(recipe.name(),25));
      itemLink(card,"Creates "+recipe.quantity()+" × "+recipe.outputName(),recipe.outputItemId(),nav);
      if(recipe.skills().isEmpty())JournalSurface.add(card,JournalSurface.copy("Profession and required skill rank are not retained."));
      for(var skill:recipe.skills())JournalSurface.add(card,JournalSurface.copy(skill.name()+" · Required skill "+value(skill.rank())+" · Skill-up thresholds "+value(skill.trivialLow())+" / "+value(skill.trivialHigh())));
      JournalSurface.add(card,JournalSurface.title("Gather your reagents",20));
      for(var ingredient:recipe.ingredients())itemLink(card,value(ingredient.count())+" × "+ingredient.name()+(Long.valueOf(0).equals(ingredient.count())?" · Zero in native row; not a positive consumption requirement":""),ingredient.itemId(),nav);
      JournalSurface.add(card,JournalSurface.copy(recipe.reagentCoverage()));
      JournalSurface.add(card,JournalSurface.copy("Yield is derived from native create-item effect base points and variance using the existing client crafting rule. Each create effect is retained separately; difficulty-specific effects are not merged."));
      JToggleButton disclosure=new JToggleButton("Show recipe source evidence");JPanel evidence=JournalSurface.stack();evidence.setVisible(false);
      JournalSurface.add(evidence,JournalSurface.copy("Snapshot "+data.snapshotId()+" · Spell #"+recipe.spellId()+" · "+recipe.variant()));
      for(var source:recipe.evidence())JournalSurface.add(evidence,JournalSurface.copy(source.table()+" · "+source.path()+":"+source.line()));
      disclosure.addActionListener(event->{evidence.setVisible(disclosure.isSelected());disclosure.setText(disclosure.isSelected()?"Hide recipe source evidence":"Show recipe source evidence");revalidate();});
      JournalSurface.add(card,disclosure);JournalSurface.add(card,evidence);JournalSurface.add(body,card);
    }
  }
  private static String value(Long value){return value==null?"Unknown":value.toString();}
  private static void itemLink(JPanel parent,String text,long item,Navigator nav) {
    JournalSurface.add(parent,JournalSurface.copy(text));
    JButton link=new JButton("Inspect item");link.getAccessibleContext().setAccessibleName("Inspect "+text);link.setHorizontalAlignment(SwingConstants.LEADING);link.setMargin(new Insets(10,12,10,12));
    link.addActionListener(event->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.ITEM,item))));JournalSurface.add(parent,link);
  }
}
