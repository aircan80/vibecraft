package dev.wowforever.importer.setup;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static dev.wowforever.importer.setup.SetupPlan.*;

/** Versioned local plan persistence. Loading never downloads data or upgrades source pins. */
public final class SetupPlanStore {
  private static final int MAX_BYTES=65536;
  private SetupPlanStore() {}

  public static void save(SetupStorage storage, Path destination, SetupPlan plan) throws IOException {
    Path file=storage.verifyDestination(destination);
    Properties values=new Properties();
    values.setProperty("version","1");
    values.setProperty("sources",join(plan.sources()));
    values.setProperty("acquisitions",join(plan.acquisitions()));
    values.setProperty("roots",Integer.toString(plan.localGameRoots().size()));
    for(int i=0;i<plan.localGameRoots().size();i++) values.setProperty("root."+i,plan.localGameRoots().get(i).toString());
    for(var pin:plan.sourcePins().entrySet()) values.setProperty("pin."+pin.getKey().name(),pin.getValue());
    plan.nativeIdentity().ifPresent(identity -> {
      values.setProperty("product",identity.product());values.setProperty("build",identity.build());values.setProperty("locale",identity.locale());
    });
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();values.store(bytes,"Vibecraft setup plan; no data downloaded");
    if(bytes.size()>MAX_BYTES) throw new IOException("Setup plan is too large");
    Files.createDirectories(storage.verifyDestination(file.getParent()));
    Path temporary=storage.verifyDestination(file.resolveSibling(file.getFileName()+".tmp"));
    Files.write(temporary,bytes.toByteArray(),StandardOpenOption.CREATE_NEW);
    try { Files.move(temporary,storage.verifyDestination(file),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
    finally { Files.deleteIfExists(temporary); }
  }

  public static SetupPlan load(Path file) throws IOException {
    if(Files.size(file)>MAX_BYTES) throw new IOException("Setup plan is too large");
    Properties values=new Properties();
    try(var input=Files.newInputStream(file)){
      byte[] bytes=input.readNBytes(MAX_BYTES+1);
      if(bytes.length>MAX_BYTES) throw new IOException("Setup plan is too large");
      values.load(new ByteArrayInputStream(bytes));
    } catch(IllegalArgumentException failure){throw new IOException("Invalid setup plan encoding",failure);}
    try {
      if(!"1".equals(values.getProperty("version"))) throw new IOException("Unsupported setup plan version");
      Set<Source> sources=enums(values.getProperty("sources"),Source.class);
      Set<Acquisition> methods=enums(values.getProperty("acquisitions"),Acquisition.class);
      int count=Integer.parseInt(values.getProperty("roots"));
      if(count<0||count>32) throw new IOException("Invalid local installation count");
      List<Path> roots=new ArrayList<>();for(int i=0;i<count;i++) roots.add(Path.of(Objects.requireNonNull(values.getProperty("root."+i))));
      Map<Source,String> pins=new EnumMap<>(Source.class);
      Set<String> allowed=new HashSet<>(Set.of("version","sources","acquisitions","roots"));
      for(int i=0;i<count;i++) allowed.add("root."+i);
      for(Source source:sources) if(source!=Source.NATIVE_DATA){String key="pin."+source;allowed.add(key);pins.put(source,Objects.requireNonNull(values.getProperty(key)));}
      Optional<NativeIdentity> identity=Optional.empty();
      if(sources.contains(Source.NATIVE_DATA)) {
        allowed.addAll(Set.of("product","build","locale"));
        identity=Optional.of(new NativeIdentity(values.getProperty("product"),values.getProperty("build"),values.getProperty("locale")));
      }
      if(!allowed.equals(values.stringPropertyNames())) throw new IOException("Unknown or missing setup plan fields");
      return new SetupPlan(sources,methods,identity,roots,pins);
    } catch(RuntimeException failure){throw new IOException("Invalid setup plan",failure);}
  }
  private static String join(Set<? extends Enum<?>> values){return values.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));}
  private static <T extends Enum<T>> Set<T> enums(String text,Class<T> type){
    if(text==null) throw new IllegalArgumentException("Missing selection");
    Set<T> result=EnumSet.noneOf(type);
    if(!text.isEmpty())for(String part:text.split(",",-1))if(!result.add(Enum.valueOf(type,part)))throw new IllegalArgumentException("Duplicate selection");
    return result;
  }
}
