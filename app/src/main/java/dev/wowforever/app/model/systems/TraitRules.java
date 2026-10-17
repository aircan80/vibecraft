package dev.wowforever.app.model.systems;

import dev.wowforever.app.model.systems.ClientSystems.*;
import dev.wowforever.app.model.systems.TalentAllocation.*;
import java.util.*;

/** Conservative decoder for the source-native Trait rows. Unknown mechanics block purchasing. */
public final class TraitRules {
  private TraitRules() {}
  private static boolean numeric(Row row,String... fields) {
    for(String field:fields)try {if(!row.values().containsKey(field))return false;Long.parseLong(row.text(field));}catch(NumberFormatException e){return false;}
    return true;
  }
  public static boolean isClassTree(Catalog c,long tree) {
    return !c.where("SkillLineXTraitTree","TraitTreeID",tree).isEmpty() && c.where("TraitTreeXTraitCurrency","TraitTreeID",tree).stream().anyMatch(r->r.number("TraitCurrencyID")==3820);
  }
  public static String name(Catalog c,Row node) {
    return entries(c,node.id()).stream().map(e->c.row("TraitDefinition",e.number("TraitDefinitionID")))
        .flatMap(Optional::stream).map(d->d.text("OverrideName_lang").isBlank()?c.spellName(d.number("SpellID")):d.text("OverrideName_lang"))
        .distinct().reduce((a,b)->a+" / "+b).orElse("Talent #"+node.id());
  }
  private static List<Row> entries(Catalog c,long node) {
    return c.where("TraitNodeXTraitNodeEntry","TraitNodeID",node).stream()
        .map(link->c.row("TraitNodeEntry",link.number("TraitNodeEntryID"))).flatMap(Optional::stream).toList();
  }
  private static Set<Long> group(Catalog c,long id) {
    Set<Long> nodes=new HashSet<>();for(Row r:c.where("TraitNodeGroupXTraitNode","TraitNodeGroupID",id))nodes.add(r.number("TraitNodeID"));return nodes;
  }
  public static TalentAllocation allocation(Catalog c,long tree) {
    Row treeRow=c.row("TraitTree",tree).orElseThrow();long system=treeRow.number("TraitSystemID");
    if(system==0 && isClassTree(c,tree))system=10;
    long currency=system==10?3820:system==45?4225:0;
    int cap=c.row("TraitCurrency",currency).map(r->(int)r.number("SourcedMax")).orElse(0);
    if(cap<1 || (system==10&&cap!=51)||(system==45&&cap!=16))throw new IllegalArgumentException("Point cap unavailable for this source build.");
    final long decodedSystem=system;
    List<Row> sourceNodes=c.rows("TraitNode").stream().filter(n->n.number("TraitTreeID")==tree || (decodedSystem==45 && c.row("TraitTree",n.number("TraitTreeID")).map(t->t.number("TraitSystemID")==45).orElse(false))).toList();
    Map<Long,Integer> maxRanks=new HashMap<>();
    for(Row node:sourceNodes)maxRanks.put(node.id(),node.number("Type")==1?entries(c,node.id()).stream().mapToInt(e->(int)e.number("MaxRanks")).sum():entries(c,node.id()).stream().mapToInt(e->(int)e.number("MaxRanks")).max().orElse(1));
    List<Node> decoded=new ArrayList<>();
    for(Row node:sourceNodes) {
      var costs=c.where("TraitTreeXTraitCost","TraitTreeID",node.number("TraitTreeID"));
      Row cost=costs.size()==1?c.row("TraitCost",costs.getFirst().number("TraitCostID")).orElse(null):null;
      String blocked="";int amount=1;
      if(cost==null || !numeric(cost,"Amount","TraitCurrencyID","CurveID") || cost.number("TraitCurrencyID")!=currency || cost.number("CurveID")!=0 || cost.number("Amount")<1)blocked="This source cost rule is not supported.";
      else amount=(int)cost.number("Amount");
      if((entries(c,node.id()).size()!=1 && node.number("Type")!=1) || (node.number("Type")!=0 && node.number("Type")!=1))blocked="Choice-node rules are not supported by this calculator yet.";
      if((node.number("Flags")&2)!=0)blocked="This client node cannot be purchased.";
      if(!numeric(node,"Type","Flags","TraitTreeID"))blocked="Required node rule fields are missing or invalid.";
      for(Row entry:entries(c,node.id()))if(!numeric(entry,"MaxRanks","NodeEntryType"))blocked="Required entry rank fields are missing or invalid.";
      List<Prerequisite> required=new ArrayList<>(),any=new ArrayList<>();Set<Long> exclusive=new HashSet<>();
      for(Row edge:c.rows("TraitEdge")) {
        if(edge.number("Type")==4 && edge.number("LeftTraitNodeID")==node.id())exclusive.add(edge.number("RightTraitNodeID"));
        if(edge.number("RightTraitNodeID")!=node.id())continue;
        long parent=edge.number("LeftTraitNodeID");
        if(edge.number("Type")==2)any.add(new Prerequisite(parent,maxRanks.getOrDefault(parent,1)));
        else if(edge.number("Type")==3)required.add(new Prerequisite(parent,maxRanks.getOrDefault(parent,1)));
        else if(edge.number("Type")==4)exclusive.add(parent);
        else if(edge.number("Type")!=0)blocked="An edge rule is not supported.";
      }
      List<SpendingGate> gates=new ArrayList<>();Set<Long> conditions=new HashSet<>();
      for(Row link:c.where("TraitNodeXTraitCond","TraitNodeID",node.id()))conditions.add(link.number("TraitCondID"));
      for(Row member:c.where("TraitNodeGroupXTraitNode","TraitNodeID",node.id()))
        for(Row link:c.where("TraitNodeGroupXTraitCond","TraitNodeGroupID",member.number("TraitNodeGroupID")))conditions.add(link.number("TraitCondID"));
      for(long id:conditions) {
        Row cond=c.row("TraitCond",id).orElse(null);
        if(cond==null){blocked="A source condition is missing.";continue;}
        boolean understood=numeric(cond,"CondType","Flags","QuestID","AchievementID","SpecSetID","RequiredLevel","TraitCondAccountElementID","TraitNodeEntryID","TraitNodeID","TraitNodeGroupID","TraitCurrencyID","SpentAmountRequired","GrantedRanks") && cond.number("CondType")==0 && cond.number("Flags")==0 && cond.number("QuestID")==0 && cond.number("AchievementID")==0 && cond.number("SpecSetID")==0 && cond.number("RequiredLevel")==0 && cond.number("TraitCondAccountElementID")==0 && cond.number("TraitNodeEntryID")==0 && cond.number("GrantedRanks")==0;
        if(!understood){blocked="A source condition needs verification before this node can be purchased.";continue;}
        long spent=cond.number("SpentAmountRequired");
        if(spent>0) {
          if(cond.number("TraitCurrencyID")!=currency)blocked="A condition uses an unsupported currency.";
          else if(cond.number("TraitNodeID")!=0)gates.add(new SpendingGate(Set.of(cond.number("TraitNodeID")),(int)spent));
          else if(cond.number("TraitNodeGroupID")!=0)gates.add(new SpendingGate(group(c,cond.number("TraitNodeGroupID")),(int)spent));
          else gates.add(new SpendingGate(maxRanks.keySet(),(int)spent));
        }
      }
      decoded.add(new Node(node.id(),maxRanks.get(node.id()),amount,0,required,exclusive,gates,any,blocked));
    }
    return new TalentAllocation(c.build()+"/"+c.snapshot()+"/"+(system==45?"legacy":tree),cap,decoded);
  }
}
