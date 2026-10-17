package dev.wowforever.app.model;

import java.util.List;

/** Preloaded, source-labelled Forever discovery facts. Absence is never a negative claim. */
public final class ForeverContent {
  private ForeverContent() {}
  public record Change(String kind, String id, String status, String fields, String name, EntityRef ref) {
    public Change(String kind,String id,String status,String fields) { this(kind,id,status,fields,"",null); }
    public Change(String kind,String id,String status,String fields,String name) { this(kind,id,status,fields,name,null); }
    public String displayName() { return name==null || name.isBlank()?kind+" #"+id:name; }
  }
  public record Context(String foreverBuild, String eraBuild, List<Change> changes) {
    public Context { changes = List.copyOf(changes); }
    public static Context empty() { return new Context("unknown", "unknown", List.of()); }
  }
  public record Encounter(String name, String state, String source) {}
  public record Discovery(long mapId, String name, String type, String build, List<Encounter> encounters,
                          String opening, String openingSource) {
    public Discovery { encounters = List.copyOf(encounters); }
  }
  public record Faction(long id, String name, String description, String build) {}
  public record Pvp(List<Discovery> battlegrounds, List<Faction> factions) {
    public Pvp { battlegrounds=List.copyOf(battlegrounds); factions=List.copyOf(factions); }
  }
  public record Hub(Context context, List<InstanceSummary> instances, List<Discovery> discoveries, Pvp pvp) {
    public Hub { instances=List.copyOf(instances); discoveries=List.copyOf(discoveries); }
  }
}
