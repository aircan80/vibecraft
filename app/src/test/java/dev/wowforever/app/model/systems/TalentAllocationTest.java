package dev.wowforever.app.model.systems;

import dev.wowforever.app.model.systems.TalentAllocation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TalentAllocationTest {
  private Node node(long id,int ranks,int cost){return new Node(id,ranks,cost,0,List.of(),Set.of(),List.of(),List.of(),"");}
  @Test void limitsRanksCostAndBothRealCaps(){
    for(int cap:List.of(16,51)){
      var a=new TalentAllocation("build/tree",cap,List.of(node(1,100,1),node(2,5,2)));
      for(int i=0;i<cap;i++)assertThat(a.change(1,1)).isEmpty();
      assertThat(a.change(1,1)).hasValue("Point cap: "+cap+".");assertThat(a.spent()).isEqualTo(cap);
    }
    var a=new TalentAllocation("b",16,List.of(node(1,2,2)));
    assertThat(a.change(1,1)).isEmpty();assertThat(a.spent()).isEqualTo(2);assertThat(a.change(1,1)).isEmpty();
    assertThat(a.change(1,1)).hasValue("Rank limit: 2.");
  }
  @Test void rejectsRefundThatBreaksPrerequisitesAndBranchThreshold(){
    var child=new Node(2,1,1,0,List.of(new Prerequisite(1,2)),Set.of(),List.of(new SpendingGate(Set.of(1L),2)),List.of(),"");
    var a=new TalentAllocation("b",51,List.of(node(1,2,1),child));
    assertThat(a.change(2,1)).isPresent();a.change(1,1);a.change(1,1);assertThat(a.change(2,1)).isEmpty();
    assertThat(a.change(1,-1)).isPresent();assertThat(a.rank(1)).isEqualTo(2);assertThat(a.change(2,-1)).isEmpty();assertThat(a.change(1,-1)).isEmpty();
  }
  @Test void branchGatesCannotUseUnrelatedTreesOrFundThemselves(){
    var child=new Node(2,5,1,0,List.of(),Set.of(),List.of(new SpendingGate(Set.of(1L,2L),2)),List.of(),"");
    var a=new TalentAllocation("b",51,List.of(node(1,2,1),child,node(3,5,1)));
    a.change(3,1);a.change(3,1);assertThat(a.change(2,1)).isPresent();
    assertThat(a.importBuild("wf1|b|2:3")).isPresent();assertThat(a.ranks()).containsOnlyKeys(3L);
  }
  @Test void importAtomicIdentityBoundAndRoundTrips(){
    var a=new TalentAllocation("70450/42",51,List.of(node(1,5,1),node(2,5,1)));a.change(1,1);
    String value=a.buildString();a.clear();assertThat(a.importBuild(value)).isEmpty();assertThat(a.rank(1)).isEqualTo(1);
    for(String bad:List.of("wf1|other|1:1","wf1|70450/42|1:2,1:3","wf1|70450/42|999:1","wf1|70450/42|1:-3","wf1|70450/42|1:999999999"))
      assertThat(a.importBuild(bad)).isPresent();
    assertThat(a.rank(1)).isEqualTo(1);
  }
  @Test void unsupportedConditionsAndCyclesNeverAllocate(){
    var blocked=new Node(1,1,1,0,List.of(),Set.of(),List.of(),List.of(),"Account condition not verified");
    var a=new TalentAllocation("b",16,List.of(blocked));assertThat(a.change(1,1)).hasValue("Account condition not verified");
    var x=new Node(1,1,1,0,List.of(new Prerequisite(2,1)),Set.of(),List.of(),List.of(),"");
    var y=new Node(2,1,1,0,List.of(new Prerequisite(1,1)),Set.of(),List.of(),List.of(),"");
    assertThat(new TalentAllocation("b",16,List.of(x,y)).importBuild("wf1|b|1:1,2:1")).isPresent();
  }
  @Test void sufficientEdgesAcceptEitherParent(){
    var child=new Node(3,1,1,0,List.of(),Set.of(),List.of(),List.of(new Prerequisite(1,2),new Prerequisite(2,1)),"");
    var a=new TalentAllocation("b",16,List.of(node(1,2,1),node(2,1,1),child));
    a.change(1,1);assertThat(a.change(3,1)).isPresent();a.change(2,1);assertThat(a.change(3,1)).isEmpty();
  }
}
