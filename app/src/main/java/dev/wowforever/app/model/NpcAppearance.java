package dev.wowforever.app.model;

import java.util.List;
import java.util.Map;

/** Exact native appearance mappings. MAPPED describes metadata, never a successful rendered preview. */
public record NpcAppearance(String product,String build,long npcId,String name,State state,
    List<Variant> variants,List<Evidence> evidence,String message,String catalogId) {
  public enum State { MAPPED, PARTIAL, UNSUPPORTED, UNRESOLVED }
  public NpcAppearance { variants=List.copyOf(variants);evidence=List.copyOf(evidence); }
  /** Texture slots retain zero placeholders; display and model scales retain their separate meanings. */
  public record Variant(long displayId,int displaySlot,long modelId,long modelFileDataId,List<Long> textures,
      Double displayScale,Double modelScale,String appearanceKey,State state,String reason,List<Evidence> evidence) {
    public Variant { textures=List.copyOf(textures);evidence=List.copyOf(evidence); }
    public boolean mapped() { return state==State.MAPPED; }
  }
  public record Evidence(String table,String recordId,String sourcePath,long sourceLine,String sourceSha256,Map<String,String> payload) {
    public Evidence { payload=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload)); }
  }
}
