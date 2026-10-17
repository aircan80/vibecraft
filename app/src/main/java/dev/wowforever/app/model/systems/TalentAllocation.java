package dev.wowforever.app.model.systems;

import java.util.*;

/** Transient hypothetical allocation. Rules must come from a verified decoder, never layout guesses. */
public final class TalentAllocation {
  public record Prerequisite(long nodeId,int ranks) { public Prerequisite { if(ranks<1)throw new IllegalArgumentException("Prerequisite ranks"); } }
  public record SpendingGate(Set<Long> nodes,int minimum) { public SpendingGate { nodes=Set.copyOf(nodes); if(minimum<0)throw new IllegalArgumentException("Negative threshold"); } }
  public record Node(long id,int maxRanks,int cost,int minimumSpent,List<Prerequisite> prerequisites,
                     Set<Long> exclusiveNodes,List<SpendingGate> gates,List<Prerequisite> anyPrerequisites,String unavailableReason) {
    public Node {
      if(maxRanks<1 || cost<1 || minimumSpent<0)throw new IllegalArgumentException("Invalid node rule");
      prerequisites=List.copyOf(prerequisites); exclusiveNodes=Set.copyOf(exclusiveNodes); gates=List.copyOf(gates);anyPrerequisites=List.copyOf(anyPrerequisites);
      unavailableReason=Objects.requireNonNull(unavailableReason);
    }
  }
  private final String identity;
  private final int cap;
  private final Map<Long,Node> nodes;
  private Map<Long,Integer> ranks=Map.of();
  public TalentAllocation(String identity,int cap,Collection<Node> nodes) {
    if(cap<1 || identity.isBlank() || identity.contains("|"))throw new IllegalArgumentException("Invalid allocation identity");
    this.identity=identity;this.cap=cap;
    Map<Long,Node> index=new LinkedHashMap<>();
    for(Node node:nodes)if(index.put(node.id(),node)!=null)throw new IllegalArgumentException("Duplicate node");
    this.nodes=Map.copyOf(index);
  }
  public Optional<Node> rule(long id){return Optional.ofNullable(nodes.get(id));}
  public int cap(){return cap;}
  public int spent(){return spent(ranks);}
  public int rank(long id){return ranks.getOrDefault(id,0);}
  public Map<Long,Integer> ranks(){return ranks;}
  public Optional<String> change(long id,int delta) {
    if(delta!=1 && delta!=-1)return Optional.of("Change one rank at a time.");
    if(!nodes.containsKey(id))return Optional.of("Unknown talent.");
    Map<Long,Integer> next=new HashMap<>(ranks);
    int rank=next.getOrDefault(id,0)+delta;
    if(rank==0)next.remove(id);else next.put(id,rank);
    Optional<String> error=validate(next);
    if(error.isEmpty())ranks=Map.copyOf(next);
    return error;
  }
  public void clear(){ranks=Map.of();}
  public String buildString() {
    StringJoiner join=new StringJoiner(",", "wf1|"+identity+"|", "");
    ranks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->join.add(e.getKey()+":"+e.getValue()));
    return join.toString();
  }
  public Optional<String> importBuild(String value) {
    String prefix="wf1|"+identity+"|";
    if(value==null || !value.startsWith(prefix))return Optional.of("This build belongs to another tree or data build.");
    Map<Long,Integer> next=new HashMap<>();
    try {
      String body=value.substring(prefix.length());
      if(!body.isEmpty())for(String part:body.split(",",-1)) {
        String[] pair=part.split(":",-1);
        if(pair.length!=2)throw new NumberFormatException();
        long id=Long.parseLong(pair[0]);int rank=Integer.parseInt(pair[1]);
        if(rank<1 || next.put(id,rank)!=null)throw new NumberFormatException();
      }
    }catch(NumberFormatException ex){return Optional.of("Malformed build string.");}
    Optional<String> error=validate(next);
    if(error.isEmpty())ranks=Map.copyOf(next);
    return error;
  }
  private int spent(Map<Long,Integer> allocation) {
    long total=0;
    for(var e:allocation.entrySet())if(nodes.containsKey(e.getKey()))total+=(long)e.getValue()*nodes.get(e.getKey()).cost();
    return total>Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)total;
  }
  private int gatePoints(Map<Long,Integer> allocation,SpendingGate gate,long self) {
    int total=0;for(long id:gate.nodes())if(id!=self && nodes.containsKey(id))total+=allocation.getOrDefault(id,0)*nodes.get(id).cost();return total;
  }
  private Optional<String> validate(Map<Long,Integer> allocation) {
    int points=spent(allocation);
    if(points>cap)return Optional.of("Point cap: "+cap+".");
    for(var entry:allocation.entrySet()) {
      Node node=nodes.get(entry.getKey());int rank=entry.getValue();
      if(node==null)return Optional.of("Unknown talent.");
      if(rank<1 || rank>node.maxRanks())return Optional.of("Rank limit: "+node.maxRanks()+".");
      if(!node.unavailableReason().isBlank())return Optional.of(node.unavailableReason());
      int elsewhere=points-rank*node.cost();
      if(elsewhere<node.minimumSpent())return Optional.of("Requires "+node.minimumSpent()+" points in other talents.");
      for(SpendingGate gate:node.gates())if(gatePoints(allocation,gate,node.id())<gate.minimum())return Optional.of("Requires "+gate.minimum()+" points in its source-defined branch.");
      if(!node.anyPrerequisites().isEmpty() && node.anyPrerequisites().stream().noneMatch(p->allocation.getOrDefault(p.nodeId(),0)>=p.ranks()))return Optional.of("Requires a connected prerequisite at its maximum rank.");
      for(Prerequisite required:node.prerequisites())if(allocation.getOrDefault(required.nodeId(),0)<required.ranks())
        return Optional.of("Requires "+required.ranks()+" ranks in talent #"+required.nodeId()+".");
      for(long other:node.exclusiveNodes())if(allocation.getOrDefault(other,0)>0)return Optional.of("These talents are mutually exclusive.");
    }
    // A threshold cannot fund itself through a mutually dependent allocation. Replay purchases from zero.
    Map<Long,Integer> reachable=new HashMap<>();
    boolean progress;
    do {
      progress=false;
      for(var entry:allocation.entrySet()) {
        Node node=nodes.get(entry.getKey());int have=reachable.getOrDefault(node.id(),0);
        if(have>=entry.getValue())continue;
        if(spent(reachable)-have*node.cost()<node.minimumSpent())continue;
        if(node.gates().stream().anyMatch(g->gatePoints(reachable,g,node.id())<g.minimum()))continue;
        if(!node.anyPrerequisites().isEmpty() && node.anyPrerequisites().stream().noneMatch(p->reachable.getOrDefault(p.nodeId(),0)>=p.ranks()))continue;
        if(node.prerequisites().stream().anyMatch(p->reachable.getOrDefault(p.nodeId(),0)<p.ranks()))continue;
        reachable.put(node.id(),have+1);progress=true;
      }
    }while(progress);
    if(!reachable.equals(allocation))return Optional.of("This allocation cannot be reached under the prerequisite rules.");
    return Optional.empty();
  }
}
