package dev.wowforever.importer.acquisition;
import dev.wowforever.importer.setup.SetupPlan;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Reviewed source metadata preset, never game payload or permission to contact a provider. */
public final class Forever70450Bootstrap {
 private Forever70450Bootstrap(){}
 public static WagoTableMaterializer.Manifest wagoManifest(){
  Properties values=new Properties();try(var input=Forever70450Bootstrap.class.getResourceAsStream("forever-70450-native-tables.properties")){
   if(input==null)throw new IllegalStateException("Reviewed native table pins are missing");values.load(input);
  }catch(IOException malformed){throw new IllegalStateException("Cannot read reviewed native table pins",malformed);}
  Map<String,String> pins=new TreeMap<>();values.forEach((k,v)->pins.put(k.toString(),v.toString()));StringBuilder canonical=new StringBuilder();pins.forEach((k,v)->canonical.append(k).append('=').append(v).append('\n'));
  if(pins.size()!=51||!PinnedItemSparseOverlayMaterializer.BASE_MANIFEST_SHA.equals(PinnedItemSparseDefinition.hash(canonical.toString().getBytes(StandardCharsets.UTF_8))))throw new IllegalStateException("Reviewed native table pins do not match exact overlay base contract");
  return new WagoTableMaterializer.Manifest(new SetupPlan.NativeIdentity("wow_classic_beta",PinnedItemSparseDefinition.BUILD,"enUS"),pins);
 }
}
