package dev.wowforever.importer.acquisition;
import java.util.*;
/** Ordered event evidence, deliberately not a resolved overlay or database-ready rows. */
public record DecodedItemSparseSnapshot(RaidbotsHotfixMaterializer.Pin identity,String provider,
 String snapshotSha256,String definitionCommit,String definitionSha256,String layout,
 List<String> columns,List<Event> events) {
 public DecodedItemSparseSnapshot{columns=List.copyOf(columns);events=List.copyOf(events);}
 public record Event(int snapshotSequence,HotfixSnapshotReader.Entry source,Map<String,Object> values){
  public Event{values=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
 }
}
