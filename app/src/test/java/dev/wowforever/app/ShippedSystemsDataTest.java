package dev.wowforever.app;

import dev.wowforever.app.db.SystemsRepository;
import dev.wowforever.app.model.systems.TraitRules;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Acceptance on actual archived client identities; no source mechanics are supplied by fixtures. */
@Tag("shipped-data")
class ShippedSystemsDataTest extends ShippedDataSupport {
  @Test void nineNativeClassLinkedTreesReachTheirSourceCapAndRejectTheNextPoint() {
    var c=new SystemsRepository(db).load();
    assertThat(c.build()).isEqualTo("1.60.1.70450");assertThat(c.baselineBuild()).isEqualTo("1.15.9.70003");
    var trees=c.rows("TraitTree").stream().filter(t->TraitRules.isClassTree(c,t.id())).toList();assertThat(trees).hasSize(9);
    for(var tree:trees){
      var a=TraitRules.allocation(c,tree.id());boolean progress;
      do{progress=false;for(var node:c.where("TraitNode","TraitTreeID",tree.id()))if(a.change(node.id(),1).isEmpty())progress=true;}while(progress);
      assertThat(a.cap()).isEqualTo(51);assertThat(a.spent()).as("tree %s",tree.id()).isEqualTo(51);
      for(var node:c.where("TraitNode","TraitTreeID",tree.id()))assertThat(a.change(node.id(),1)).isPresent();
      String build=a.buildString();a.clear();assertThat(a.importBuild(build)).isEmpty();assertThat(a.spent()).isEqualTo(51);
    }
  }
  @Test void legacyCapIsSharedAcrossAllThreeTreesAndChallengesRemainNeutralSourceRecords(){
    var c=new SystemsRepository(db).load();var a=TraitRules.allocation(c,1187);
    for(long tree:List.of(1187L,1188L,1189L))assertThat(c.where("TraitNode","TraitTreeID",tree)).hasSize(9);
    boolean progress;do{progress=false;for(var node:c.rows("TraitNode"))if(a.change(node.id(),1).isEmpty())progress=true;}while(progress);
    assertThat(a.spent()).isEqualTo(16);assertThat(a.cap()).isEqualTo(16);
    assertThat(c.where("TraitCurrencySource","TraitCurrencyID",4225)).hasSize(131);
    assertThat(c.where("TraitCurrencySource","TraitCurrencyID",4225)).allSatisfy(source->assertThat(c.row("Achievement",source.number("AchievementID"))).isPresent());
    assertThat(c.where("RenownRewards","CovenantID",48)).isNotEmpty();
  }
}
