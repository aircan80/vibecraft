package dev.wowforever.app.assets;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/** Bounded, read-only installation inspection for the main setup picker. This class does not
 * decode CASC archives, establish asset completeness, use the network or write to the game folder.
 */
public final class LocalWowDiscovery {
    public enum Status { SUPPORTED_METADATA, OTHER_EDITION, UNSUPPORTED_BUILD, INCOMPLETE, INVALID, CHANGED }
    public record Installation(Path directory,String product,String version,String buildConfigMd5,
                               String rootContentMd5,String encodingContentMd5,String encodingKey,
                               Set<String> locales,Status status,String explanation,String buildInfoSha256) {
        public Installation { locales=Set.copyOf(locales); }
    }
    public record Progress(int inspectedCandidates,int candidateLimit,Path candidate) {}
    public record Snapshot(Instant inspectedAt,List<Installation> installations,int inspectedCandidates,
                           boolean limitReached,List<String> notes) {
        public Snapshot { installations=List.copyOf(installations);notes=List.copyOf(notes); }
    }
    private static final int FILE_LIMIT=1024*1024,CANDIDATE_LIMIT=64;
    private static final long SCAN_NANOS=Duration.ofSeconds(5).toNanos();
    private static final Set<String> LOCALES=Set.of("enUS","enGB","deDE","frFR","esES","esMX","itIT","ptBR","ruRU","koKR","zhCN","zhTW");
    private final Map<String,String> supportedConfigs;
    public LocalWowDiscovery(){this(Map.of(NativeAssetClient.PRODUCT+":"+NativeAssetClient.BUILD,NativeAssetClient.CONFIG));}
    LocalWowDiscovery(Map<String,String> supportedConfigs){this.supportedConfigs=Map.copyOf(supportedConfigs);}

    /** Remembered locations are searched first. Common locations are shallow candidates, never a disk crawl. */
    public Snapshot scan(List<Path> remembered,boolean includeCommonLocations,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Local installation inspection must run off the EDT");
        Objects.requireNonNull(remembered);Objects.requireNonNull(cancelled);Objects.requireNonNull(progress);
        List<Path> candidates=new ArrayList<>(remembered);if(includeCommonLocations)candidates.addAll(commonLocations());
        List<Installation> matches=new ArrayList<>();List<String> notes=new ArrayList<>();Set<Path> seen=new HashSet<>();
        long deadline=System.nanoTime()+SCAN_NANOS;int inspected=0;boolean limited=false;
        for(Path candidate:candidates){
            check(cancelled);if(inspected>=CANDIDATE_LIMIT||System.nanoTime()>deadline){limited=true;break;}
            if(candidate==null)continue;
            inspected++;progress.accept(new Progress(inspected,CANDIDATE_LIMIT,candidate));
            Path root;
            try{root=locate(candidate,cancelled);}catch(IOException failure){notes.add("A selected installation location could not be inspected.");continue;}
            if(root==null||!seen.add(root))continue;
            try{matches.addAll(inspectRoot(root,cancelled,deadline));}
            catch(InspectionLimit reached){limited=true;notes.add("Installation inspection reached its time limit.");break;}
            catch(IOException|IllegalArgumentException failure){matches.add(failure(root,Status.INVALID,"Installation metadata could not be validated: "+failure.getMessage(),""));}
        }
        check(cancelled);
        if(limited)notes.add("Search limit reached; inspect another folder with Browse. Filesystem operations may still wait on the operating system.");
        return new Snapshot(Instant.now(),matches,inspected,limited,notes);
    }
    public Snapshot inspect(Path selectedFolder,BooleanSupplier cancelled,Consumer<Progress> progress)throws IOException {
        return scan(List.of(selectedFolder),false,cancelled,progress);
    }
    /** Recheck a selected metadata identity before/after later extraction. Never reads or writes game assets. */
    public boolean stillMatches(Installation installation,BooleanSupplier cancelled)throws IOException {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Local installation inspection must run off the EDT");
        check(cancelled);Path directory=installation.directory().toRealPath();
        if(!hash("SHA-256",read(contained(directory,directory.resolve(".build.info")),cancelled)).equals(installation.buildInfoSha256()))return false;
        String config=installation.buildConfigMd5();if(!config.matches("[0-9a-f]{32}"))return false;
        Path path=directory.resolve("Data/config/"+config.substring(0,2)+"/"+config.substring(2,4)+"/"+config);
        if(!Files.isRegularFile(path))path=directory.resolve("data/config/"+config.substring(0,2)+"/"+config.substring(2,4)+"/"+config);
        return hash("MD5",read(contained(directory,path),cancelled)).equals(config);
    }
    /** Known conventional locations only. Registry/product-database and arbitrary prefix discovery are not implemented. */
    public static List<Path> commonLocations(){
        List<Path> candidates=new ArrayList<>();String home=System.getProperty("user.home"),os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win")){
            for(String variable:List.of("ProgramFiles(x86)","ProgramFiles")){String base=System.getenv(variable);if(base!=null&&!base.isBlank())candidates.add(Path.of(base,"World of Warcraft"));}
            candidates.add(Path.of("C:\\Games\\World of Warcraft"));
        }else if(os.contains("mac")){
            candidates.add(Path.of("/Applications/World of Warcraft"));candidates.add(Path.of("/Users/Shared/Blizzard/World of Warcraft"));
        }else{
            candidates.add(Path.of(home,"Games","world-of-warcraft"));candidates.add(Path.of(home,"Games","World of Warcraft"));
            for(Path prefix:List.of(Path.of(home,".wine"),Path.of(home,"Games","battlenet"),Path.of(home,"Games","battle-net"),
                    Path.of(home,".local/share/bottles/bottles/Battle.net"),Path.of(home,".var/app/com.usebottles.bottles/data/bottles/bottles/Battle.net"))){
                candidates.add(prefix.resolve("drive_c/Program Files (x86)/World of Warcraft"));candidates.add(prefix.resolve("drive_c/Program Files/World of Warcraft"));
            }
        }
        return List.copyOf(candidates);
    }
    private static Path locate(Path candidate,BooleanSupplier cancelled)throws IOException {
        Path current=candidate.toAbsolutePath().normalize();
        if(Files.isRegularFile(current)&&current.getFileName().toString().equals(".build.info"))current=current.getParent();
        for(int depth=0;current!=null&&depth<3;depth++,current=current.getParent()){
            check(cancelled);if(Files.isRegularFile(current.resolve(".build.info")))return current.toRealPath();
        }
        return null;
    }
    private List<Installation> inspectRoot(Path directory,BooleanSupplier cancelled,long deadline)throws IOException {
        Path infoPath=contained(directory,directory.resolve(".build.info"));byte[] original=read(infoPath,cancelled);String originalSha=hash("SHA-256",original);
        List<Map<String,String>> rows=rows(decode(original));List<Installation> results=new ArrayList<>();
        for(Map<String,String> row:rows){
            check(cancelled);if(System.nanoTime()>deadline)throw new InspectionLimit();if(!"1".equals(row.get("Active")))continue;
            String product=row.getOrDefault("Product",""),version=row.getOrDefault("Version",""),config=row.getOrDefault("Build Key","");
            Set<String> locales=new LinkedHashSet<>();for(String token:row.getOrDefault("Tags","").split("[^A-Za-z0-9]+"))if(LOCALES.contains(token))locales.add(token);
            if(!product.matches("wow(?:_[a-z0-9]+)*")||!version.matches("[0-9]+(?:\\.[0-9]+){3}")||!config.matches("[0-9a-f]{32}")){
                results.add(new Installation(directory,product,version,config,"","","",locales,Status.INVALID,"Active product/build/configuration identity is missing or malformed.",originalSha));continue;
            }
            Path configPath=directory.resolve("Data/config/"+config.substring(0,2)+"/"+config.substring(2,4)+"/"+config);
            if(!Files.isRegularFile(configPath))configPath=directory.resolve("data/config/"+config.substring(0,2)+"/"+config.substring(2,4)+"/"+config);
            if(!Files.isRegularFile(configPath)){
                results.add(new Installation(directory,product,version,config,"","","",locales,Status.INCOMPLETE,"Selected build configuration is not present locally; no download was attempted.",originalSha));continue;
            }
            try{
                byte[] bytes=read(contained(directory,configPath),cancelled);
                if(!hash("MD5",bytes).equals(config))throw new IOException("Build configuration MD5 mismatch");
                Map<String,String> values=new HashMap<>();for(String line:decode(bytes).split("\\R")){int at=line.indexOf(" = ");if(at>0)values.put(line.substring(0,at),line.substring(at+3));}
                String number=version.substring(version.lastIndexOf('.')+1),patch=version.substring(0,version.lastIndexOf('.'));
                if(!values.getOrDefault("build-name","").matches("WOW-"+Pattern.quote(number)+"patch"+Pattern.quote(patch)+"(?:_.*)?"))throw new IOException("Build configuration version disagrees with installation metadata");
                String declaredProduct=values.getOrDefault("build-uid",product);if(!product.equals(declaredProduct))throw new IOException("Build configuration product disagrees with installation metadata");
                String root=values.getOrDefault("root","");String[] encoding=values.getOrDefault("encoding","").split("\\s+");
                if(!root.matches("[0-9a-f]{32}")||encoding.length!=2||!encoding[0].matches("[0-9a-f]{32}")||!encoding[1].matches("[0-9a-f]{32}"))throw new IOException("Missing or malformed root/encoding identity");
                Status status;String explanation;
                String supported=supportedConfigs.get(product+":"+version);
                if(supported!=null&&supported.equals(config)){status=Status.SUPPORTED_METADATA;explanation="Reviewed Forever build metadata matches. Local CASC/DB2 extraction and asset completeness are not established.";}
                else if(product.equals("wow_classic_beta")&&version.startsWith("1.60.")){status=Status.UNSUPPORTED_BUILD;explanation="Forever beta metadata is valid, but this exact build/configuration has no reviewed local extraction profile.";}
                else{status=Status.OTHER_EDITION;explanation="A different WoW edition/channel was found. It cannot supply the Forever catalog.";}
                if(!hash("SHA-256",read(infoPath,cancelled)).equals(originalSha)){status=Status.CHANGED;explanation="Installation identity changed during inspection; retry when the game update finishes.";}
                results.add(new Installation(directory,product,version,config,root,encoding[0],encoding[1],locales,status,explanation,originalSha));
            }catch(IOException failure){results.add(new Installation(directory,product,version,config,"","","",locales,Status.INVALID,failure.getMessage(),originalSha));}
        }
        if(results.isEmpty())results.add(failure(directory,Status.INCOMPLETE,"No active WoW product is recorded in this installation.",originalSha));
        return results;
    }
    private static Installation failure(Path directory,Status status,String explanation,String sha){return new Installation(directory,"","","","","","",Set.of(),status,explanation,sha);}
    private static Path contained(Path directory,Path path)throws IOException{Path real=path.toRealPath();if(!real.startsWith(directory))throw new IOException("Installation metadata escapes the selected directory");return real;}
    private static byte[] read(Path path,BooleanSupplier cancelled)throws IOException{
        check(cancelled);if(Files.size(path)>FILE_LIMIT)throw new IOException("Installation metadata exceeds 1 MiB bound");
        try(var in=Files.newInputStream(path)){byte[] bytes=in.readNBytes(FILE_LIMIT+1);if(bytes.length>FILE_LIMIT)throw new IOException("Installation metadata exceeds 1 MiB bound");check(cancelled);return bytes;}
    }
    private static String decode(byte[] bytes)throws IOException{try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException failure){throw new IOException("Installation metadata is not UTF-8",failure);}}
    private static List<Map<String,String>> rows(String text)throws IOException{
        if(text.startsWith("\ufeff"))text=text.substring(1);String[] headers=null;List<Map<String,String>> rows=new ArrayList<>();
        for(String line:text.split("\\R")){if(line.isBlank()||line.startsWith("#"))continue;String[] values=line.split("\\|",-1);
            if(headers==null){headers=new String[values.length];Set<String> names=new HashSet<>();for(int i=0;i<values.length;i++){headers[i]=values[i].split("!",2)[0];if(!names.add(headers[i]))throw new IOException("Duplicate installation metadata header");}if(!names.containsAll(Set.of("Active","Product","Version","Build Key")))throw new IOException("Missing required installation metadata headers");}
            else{if(rows.size()>=128)throw new IOException("Installation metadata exceeds 128-row bound");if(values.length!=headers.length)throw new IOException("Installation metadata row width mismatch");Map<String,String> row=new LinkedHashMap<>();for(int i=0;i<values.length;i++)row.put(headers[i],values[i]);rows.add(row);}
        }
        if(headers==null)throw new IOException("Installation metadata has no header");return rows;
    }
    private static String hash(String algorithm,byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static final class InspectionLimit extends IOException {}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Installation inspection cancelled");}
}
