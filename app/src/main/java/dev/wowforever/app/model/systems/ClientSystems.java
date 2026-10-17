package dev.wowforever.app.model.systems;

import java.util.*;

/** Immutable source-native system records. No account or character state is represented. */
public final class ClientSystems {
  private ClientSystems() {}
  public record Row(String table, long id, Map<String,String> values, String sourcePath, int sourceLine) {
    public Row { values = Map.copyOf(values); }
    public String text(String key) { return values.getOrDefault(key, ""); }
    public long number(String key) { try { return Long.parseLong(text(key)); } catch (NumberFormatException ex) { return 0; } }
    public String name() {
      for (String field : List.of("OverrideName_lang", "Name_lang", "DisplayName_lang", "TitleText_lang", "Title_lang", "Requirement_lang"))
        if (!text(field).isBlank()) return text(field);
      return table + " #" + id;
    }
  }
  public static final class Catalog {
    private final String build,snapshot,baselineBuild;
    private final Map<String,List<Row>> tables,baselineTables;
    private final Map<String,Map<Long,Row>> index;
    public Catalog(String build,String snapshot,Map<String,List<Row>> tables){this(build,snapshot,tables,"",Map.of());}
    public Catalog(String build,String snapshot,Map<String,List<Row>> tables,String baselineBuild,Map<String,List<Row>> baselineTables) {
      this.build=build;this.snapshot=snapshot;this.baselineBuild=baselineBuild;
      this.tables=freeze(tables);this.baselineTables=freeze(baselineTables);
      Map<String,Map<Long,Row>> indexed=new HashMap<>();
      this.tables.forEach((table,rows)->{Map<Long,Row> map=new HashMap<>();rows.forEach(r->map.put(r.id(),r));indexed.put(table,Map.copyOf(map));});
      index=Map.copyOf(indexed);
    }
    private static Map<String,List<Row>> freeze(Map<String,List<Row>> input) {Map<String,List<Row>> copy=new LinkedHashMap<>();input.forEach((k,v)->copy.put(k,List.copyOf(v)));return Map.copyOf(copy);}
    public String build(){return build;}public String snapshot(){return snapshot;}public String baselineBuild(){return baselineBuild;}
    public Map<String,List<Row>> tables(){return tables;}public Map<String,List<Row>> baselineTables(){return baselineTables;}
    public List<Row> rows(String table) { return tables.getOrDefault(table,List.of()); }
    public Optional<Row> row(String table,long id) { return Optional.ofNullable(index.getOrDefault(table,Map.of()).get(id)); }
    public List<Row> where(String table,String field,long value) { return rows(table).stream().filter(r->r.number(field)==value).toList(); }
    public boolean available() { return !rows("TraitSystem").isEmpty(); }
    public String spellName(long id) { return row("SpellName",id).map(Row::name).orElse("Spell #"+id); }
  }
}
