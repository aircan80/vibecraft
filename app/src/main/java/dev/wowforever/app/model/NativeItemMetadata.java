package dev.wowforever.app.model;

import java.util.List;

/** Identity, equipment slot and explicit links survive even when ItemSparse stats are unavailable. */
public record NativeItemMetadata(String build, Integer inventoryType, Long iconId, List<SetMembership> sets,
    List<Effect> effects, String evidence, boolean nativeRow) {
  public NativeItemMetadata { sets=List.copyOf(sets);effects=List.copyOf(effects); }
  public record SetMembership(long id,String name,String evidence) {}
  public record Effect(long effectId,long spellId,String name,Integer triggerType,String evidence) {}
  public boolean available(){return inventoryType!=null || iconId!=null || !sets.isEmpty() || !effects.isEmpty();}
  public static NativeItemMetadata empty(){return new NativeItemMetadata("",null,null,List.of(),List.of(),"",false);}
}
