package dev.wowforever.app.ui.journal;

import dev.wowforever.app.ui.UiState;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.model.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Personal research references. Exact edition/build scope prevents cross-game ID reuse. */
public final class PinnedResearch {
  public record Reference(ViewTarget target, String label) {}
  private final UiState state;
  private final String prefix;
  public PinnedResearch(UiState state, String scope) {
    this.state=Objects.requireNonNull(state);
    if(scope==null || scope.isBlank()) throw new IllegalArgumentException("Missing research scope");
    prefix="research-v1:"+encode(scope)+":";
  }
  private static String encode(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
  private static String decode(String s) { return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8); }
  public static Optional<String> key(ViewTarget target) {
    return Optional.ofNullable(switch(target) {
      case ViewTarget.Entity e -> "entity/"+e.ref().kind().name()+"/"+e.ref().id();
      case ViewTarget.WorldMap m -> "map/"+m.uiMapId()+"/"+(m.focusedChildId()==null?"":m.focusedChildId());
      case ViewTarget.Forever f -> f.section().equals("pinned")?null:"forever/"+encode(f.section())+"/"+f.id();
      case ViewTarget.ItemContent c -> "catalog/"+encode(c.section())+"/"+c.id();
      case ViewTarget.List l -> l.equals(new ViewTarget.List(l.kind()))?"list/"+l.kind().name():null;
      default -> null;
    });
  }
  private static ViewTarget target(String k) {
    String[] p=k.split("/",-1);
    return switch(p[0]) {
      case "entity" -> new ViewTarget.Entity(new EntityRef(EntityKind.valueOf(p[1]),Long.parseLong(p[2])));
      case "map" -> new ViewTarget.WorldMap(Long.parseLong(p[1]),p[2].isEmpty()?null:Long.valueOf(p[2]));
      case "forever" -> new ViewTarget.Forever(decode(p[1]),Long.parseLong(p[2]));
      case "catalog" -> new ViewTarget.ItemContent(decode(p[1]),Long.parseLong(p[2]));
      case "list" -> new ViewTarget.List(EntityKind.valueOf(p[1]));
      default -> throw new IllegalArgumentException("Unknown research reference");
    };
  }
  public List<Reference> references() {
    List<Reference> result=new ArrayList<>();
    for(String value:state.pinned()) if(value.startsWith(prefix)) try {
      String[] parts=value.substring(prefix.length()).split(":",2);
      result.add(new Reference(target(decode(parts[0])),decode(parts[1])));
    } catch(RuntimeException ignored) { /* Corrupt or obsolete references do not break the journal. */ }
    return List.copyOf(result);
  }
  public boolean toggle(ViewTarget target, String label) {
    String key=key(target).orElseThrow(()->new IllegalArgumentException("This view cannot be pinned"));
    if(label==null || label.isBlank()) throw new IllegalArgumentException("Missing page label");
    String start=prefix+encode(key)+":";
    List<String> values=new ArrayList<>(state.pinned());
    boolean removed=values.removeIf(v->v.startsWith(start));
    if(!removed) values.add(start+encode(label.length()>160?label.substring(0,160):label));
    state.setPinned(values);
    return !removed;
  }
  public void remove(ViewTarget target) {
    String start=prefix+encode(key(target).orElseThrow())+":";
    state.setPinned(state.pinned().stream().filter(v->!v.startsWith(start)).toList());
  }
}
