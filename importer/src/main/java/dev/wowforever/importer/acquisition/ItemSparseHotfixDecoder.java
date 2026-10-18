package dev.wowforever.importer.acquisition;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.CancellationException;import java.util.function.BooleanSupplier;
/** Decode every valid ItemSparse event; retain other statuses without inventing precedence or deletion semantics. */
public final class ItemSparseHotfixDecoder {
 public static final long TABLE_HASH=0x919be54eL;
 public DecodedItemSparseSnapshot decode(RaidbotsHotfixMaterializer.Result acquired,RaidbotsHotfixMaterializer.Pin pin,
   byte[] definitionBytes,BooleanSupplier cancelled)throws IOException {
  if(!PinnedItemSparseDefinition.BUILD.equals(pin.build()))throw new IOException("ItemSparse decoder only supports its exact reviewed build");
  var definition=PinnedItemSparseDefinition.parse(definitionBytes);Path data=acquired.snapshot(),evidence=acquired.evidence();
  if(Files.isSymbolicLink(data)||Files.isSymbolicLink(evidence)||Files.size(evidence)>16384||Files.size(data)>(64L<<20))throw new IOException("Snapshot/evidence alias or size rejected");
  Properties proof=new Properties();try(var input=Files.newInputStream(evidence)){proof.load(input);}catch(IllegalArgumentException invalid){throw new IOException("Invalid acquisition evidence",invalid);}
  return decodeBytes(readBounded(data,cancelled),proof,pin,definitionBytes,cancelled);
 }
 /** Bounded archive adapter with precisely the same definition, source, hash and framing checks. */
 public DecodedItemSparseSnapshot decodeBytes(byte[] snapshot,Properties proof,RaidbotsHotfixMaterializer.Pin pin,
   byte[] definitionBytes,BooleanSupplier cancelled)throws IOException{
  check(cancelled);if(snapshot.length>(64L<<20))throw new IOException("Hotfix snapshot exceeds byte limit");
  if(!PinnedItemSparseDefinition.BUILD.equals(pin.build()))throw new IOException("ItemSparse decoder only supports its exact reviewed build");
  var definition=PinnedItemSparseDefinition.parse(definitionBytes);
  Map<String,String> required=Map.of("provider","RAIDBOTS_HOTFIX","source",RaidbotsHotfixMaterializer.SOURCE.toString(),"product",pin.product(),"build",pin.build(),"requestedLocale",pin.locale(),"sha256",pin.sha256(),"cascRootVerified","false","decodedRowsAvailable","false");
  for(var entry:required.entrySet())if(!entry.getValue().equals(proof.getProperty(entry.getKey())))throw new IOException("Hotfix acquisition evidence does not match selected identity");
  if(!pin.sha256().equals(PinnedItemSparseDefinition.hash(snapshot)))throw new IOException("Hotfix snapshot content hash mismatch");
  var structure=new HotfixSnapshotReader().read(snapshot,pin.buildNumber(),64L<<20,500000,cancelled);
  var events=decodeEvents(definition,structure,snapshot,cancelled);
  return new DecodedItemSparseSnapshot(pin,"RAIDBOTS_HOTFIX",pin.sha256(),PinnedItemSparseDefinition.COMMIT,
      PinnedItemSparseDefinition.SHA256,PinnedItemSparseDefinition.LAYOUT,definition.columns(),events);
 }
 static List<DecodedItemSparseSnapshot.Event> decodeEvents(PinnedItemSparseDefinition definition,HotfixSnapshotReader.Snapshot structure,byte[] snapshot,BooleanSupplier cancelled)throws IOException {
  List<DecodedItemSparseSnapshot.Event> events=new ArrayList<>();int sequence=0;
  for(var entry:structure.entries()) {check(cancelled);int position=sequence++;if(entry.tableHash()!=TABLE_HASH)continue;
   Map<String,Object> values=entry.status()==HotfixSnapshotReader.Status.VALID?decodeRecord(definition,
       Arrays.copyOfRange(snapshot,Math.toIntExact(entry.payloadOffset()),Math.toIntExact(entry.payloadOffset()+entry.payloadBytes())),entry.recordId()):Map.of();
   events.add(new DecodedItemSparseSnapshot.Event(position,entry,values));
  }
  return List.copyOf(events);
 }
 private static byte[] readBounded(Path data,BooleanSupplier cancelled)throws IOException{
  try(var input=Files.newInputStream(data);var output=new ByteArrayOutputStream()){
   byte[] chunk=new byte[65536];int n;long total=0;while((n=input.read(chunk))!=-1){check(cancelled);total+=n;if(total>(64L<<20))throw new IOException("Hotfix snapshot exceeds byte limit");output.write(chunk,0,n);}return output.toByteArray();
  }
 }
 static Map<String,Object> decodeRecord(PinnedItemSparseDefinition definition,byte[] payload,long recordId)throws IOException {
  if(payload.length>1_048_576)throw new IOException("ItemSparse record exceeds byte limit");
  ByteBuffer bytes=ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);Map<String,Object> row=new LinkedHashMap<>();
  try{int column=0;for(var field:definition.fields())for(int k=0;k<field.count();k++){
   Object value;if(field.noninlineId())value=recordId;
   else if(field.type().equals("locstring")||field.type().equals("string")){
    int start=bytes.position(),end=start;while(end<bytes.limit()&&bytes.get(end)!=0){if(end-start>=65536)throw new IOException("Hotfix string exceeds byte limit");end++;}
    if(end==bytes.limit())throw new IOException("Unterminated hotfix string");value=PinnedItemSparseDefinition.utf8(Arrays.copyOfRange(payload,start,end));bytes.position(end+1);
   }else if(field.type().equals("float")){float number=bytes.getFloat();if(!Float.isFinite(number))throw new IOException("Nonfinite hotfix float");value=number;}
   else value=switch(field.bits()){case 8->field.unsigned()?(long)Byte.toUnsignedInt(bytes.get()):(long)bytes.get();case 16->field.unsigned()?(long)Short.toUnsignedInt(bytes.getShort()):(long)bytes.getShort();case 32->field.unsigned()?Integer.toUnsignedLong(bytes.getInt()):(long)bytes.getInt();default->throw new IOException("Unsupported integer width");};
   row.put(definition.columns().get(column++),value);
  }}catch(BufferUnderflowException truncated){throw new IOException("Truncated ItemSparse record",truncated);}
  if(bytes.hasRemaining())throw new IOException("Unconsumed ItemSparse payload bytes");return Collections.unmodifiableMap(row);
 }
 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("ItemSparse decode cancelled");}
}
