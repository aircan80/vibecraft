package dev.wowforever.app.model.systems;

import dev.wowforever.app.model.systems.ClientSystems.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TraitRulesTest {
  private Row row(String table,long id,String... cells) { Map<String,String> values=new HashMap<>();
    if(table.equals("TraitNode"))for(String field:List.of("Type","Flags","TraitTreeID"))values.put(field,"0");
    if(table.equals("TraitNodeEntry"))values.put("NodeEntryType","0");
    if(table.equals("TraitCost"))values.put("CurveID","0");
    if(table.equals("TraitCond"))for(String field:List.of("CondType","Flags","QuestID","AchievementID","SpecSetID","RequiredLevel","TraitCondAccountElementID","TraitNodeEntryID","TraitNodeID","TraitNodeGroupID","TraitCurrencyID","SpentAmountRequired","GrantedRanks"))values.put(field,"0");for(int i=0;i<cells.length;i+=2)values.put(cells[i],cells[i+1]);return new Row(table,id,values,"fixture.csv",2); }
  @Test void sourceGroupSpendingAndSourceRankAreUsed(){
    Map<String,List<Row>> tables=new HashMap<>();
    tables.put("TraitTree",List.of(row("TraitTree",1,"TraitSystemID","10")));
    tables.put("TraitCurrency",List.of(row("TraitCurrency",3820,"SourcedMax","51")));
    tables.put("TraitTreeXTraitCost",List.of(row("TraitTreeXTraitCost",1,"TraitTreeID","1","TraitCostID","10")));
    tables.put("TraitCost",List.of(row("TraitCost",10,"TraitCurrencyID","3820","Amount","1")));
    tables.put("TraitNode",List.of(row("TraitNode",100,"TraitTreeID","1"),row("TraitNode",101,"TraitTreeID","1")));
    tables.put("TraitNodeXTraitNodeEntry",List.of(row("TraitNodeXTraitNodeEntry",1,"TraitNodeID","100","TraitNodeEntryID","20"),row("TraitNodeXTraitNodeEntry",2,"TraitNodeID","101","TraitNodeEntryID","21")));
    tables.put("TraitNodeEntry",List.of(row("TraitNodeEntry",20,"MaxRanks","2"),row("TraitNodeEntry",21,"MaxRanks","1")));
    tables.put("TraitNodeGroupXTraitNode",List.of(row("TraitNodeGroupXTraitNode",1,"TraitNodeGroupID","5","TraitNodeID","100"),row("TraitNodeGroupXTraitNode",2,"TraitNodeGroupID","6","TraitNodeID","101")));
    tables.put("TraitNodeGroupXTraitCond",List.of(row("TraitNodeGroupXTraitCond",1,"TraitNodeGroupID","6","TraitCondID","50")));
    tables.put("TraitCond",List.of(row("TraitCond",50,"TraitCurrencyID","3820","SpentAmountRequired","2","TraitNodeGroupID","5")));
    var allocation=TraitRules.allocation(new Catalog("b","s",tables),1);
    assertThat(allocation.change(101,1)).isPresent();allocation.change(100,1);assertThat(allocation.change(101,1)).isPresent();allocation.change(100,1);assertThat(allocation.change(101,1)).isEmpty();assertThat(allocation.change(100,1)).isPresent();
    Map<String,String> incomplete=new HashMap<>(tables.get("TraitCond").getFirst().values());incomplete.remove("Flags");
    tables.put("TraitCond",List.of(new Row("TraitCond",50,incomplete,"fixture.csv",2)));
    var blocked=TraitRules.allocation(new Catalog("b","s",tables),1);blocked.change(100,1);blocked.change(100,1);
    assertThat(blocked.change(101,1)).hasValue("A source condition needs verification before this node can be purchased.");
  }
}
