package dev.wowforever.app.assets;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Frozen official discovery selection. This descriptor pins published pointers; the reader still
 * authenticates actual configuration, encoding, root and requested content bytes before trusting them.
 */
public final class BlizzardBuildDescriptor {
    private static final Map<String,Integer> LOCALES=Map.ofEntries(
            Map.entry("enUS",0x2),Map.entry("koKR",0x4),Map.entry("frFR",0x10),Map.entry("deDE",0x20),
            Map.entry("zhCN",0x40),Map.entry("esES",0x80),Map.entry("zhTW",0x100),Map.entry("enGB",0x200),
            Map.entry("esMX",0x1000),Map.entry("ruRU",0x2000),Map.entry("ptBR",0x4000),Map.entry("itIT",0x8000));
    private final String product,build,region,locale,buildConfigMd5,cdnConfigMd5;
    private final List<URI> cdnBases;
    private final Map<String,String> metadataSha256;
    private final Instant capturedAt;
    private BlizzardBuildDescriptor(String product,String build,String region,String locale,String config,String cdnConfig,
                                    List<URI> cdnBases,Map<String,String> metadataSha256,Instant capturedAt){
        this.product=product;this.build=build;this.region=region;this.locale=locale;this.buildConfigMd5=config;
        this.cdnConfigMd5=cdnConfig;this.cdnBases=List.copyOf(cdnBases);this.metadataSha256=Map.copyOf(metadataSha256);this.capturedAt=capturedAt;
    }
    public String product(){return product;} public String build(){return build;} public String region(){return region;}
    public String locale(){return locale;} public int localeMask(){return LOCALES.get(locale);}
    public String buildConfigMd5(){return buildConfigMd5;} public String cdnConfigMd5(){return cdnConfigMd5;}
    public List<URI> cdnBases(){return cdnBases;} public Map<String,String> metadataSha256(){return metadataSha256;}
    public Instant capturedAt(){return capturedAt;}

    /** Accepts only a selection represented in the captured official documents, not an arbitrary CDN URL. */
    public static BlizzardBuildDescriptor fromDiscovery(BlizzardBuildDiscovery.Snapshot snapshot,
                                                        BlizzardBuildDiscovery.Build selected,String locale){
        Objects.requireNonNull(snapshot);Objects.requireNonNull(selected);
        if(!LOCALES.containsKey(locale))throw new IllegalArgumentException("Unsupported native locale: "+locale);
        if(!snapshot.product().matches("wow(?:_[a-z0-9]+)*")||!snapshot.product().equals(selected.product())||!snapshot.builds().contains(selected))
            throw new IllegalArgumentException("Build is not a selection from this product snapshot");
        String endpoint=selected.channel()==BlizzardBuildDiscovery.Channel.PRELOAD?"bgdl":"versions";
        var buildDocument=document(snapshot,endpoint);var cdnDocument=document(snapshot,"cdns");
        try{
            if(!BlizzardBuildDiscovery.parseBuilds(snapshot.product(),buildDocument.text(),selected.channel()).contains(selected))
                throw new IllegalArgumentException("Selected build is not in its captured official document");
            var locations=BlizzardBuildDiscovery.parseCdns(cdnDocument.text()).stream().filter(c->c.region().equals(selected.region())).toList();
            if(locations.size()!=1||!snapshot.cdns().contains(locations.getFirst()))throw new IllegalArgumentException("No unique captured CDN location for selected region");
            var location=locations.getFirst();List<URI> bases=new ArrayList<>();
            for(String host:location.hosts())bases.add(URI.create("https://"+host+"/"+location.path()+"/"));
            if(bases.isEmpty())throw new IllegalArgumentException("Selected CDN location has no hosts");
            return new BlizzardBuildDescriptor(snapshot.product(),selected.version(),selected.region(),locale,
                    selected.buildConfigMd5(),selected.cdnConfigMd5(),bases,
                    Map.of(endpoint,buildDocument.sha256(),"cdns",cdnDocument.sha256()),snapshot.discoveredAt());
        }catch(java.io.IOException malformed){throw new IllegalArgumentException("Captured official metadata cannot establish selected identity",malformed);}
    }
    private static BlizzardBuildDiscovery.Document document(BlizzardBuildDiscovery.Snapshot snapshot,String endpoint){
        URI source=URI.create("https://us.version.battle.net/"+snapshot.product()+"/"+endpoint);
        var documents=snapshot.documents().stream().filter(d->source.equals(d.source())).toList();
        if(documents.size()!=1)throw new IllegalArgumentException("Missing or ambiguous captured official "+endpoint+" document");
        var document=documents.getFirst();byte[] bytes=document.text().getBytes(StandardCharsets.UTF_8);
        if(document.status()!=BlizzardBuildDiscovery.Status.PUBLISHED||bytes.length>1024*1024||bytes.length!=document.bytes()||!sha(bytes).equals(document.sha256()))
            throw new IllegalArgumentException("Captured official metadata evidence is inconsistent");
        return document;
    }
    /** Compatibility profile for previously reviewed audio callers; it does not claim a new discovery capture. */
    static BlizzardBuildDescriptor legacyForever(){
        return new BlizzardBuildDescriptor(NativeAssetClient.PRODUCT,NativeAssetClient.BUILD,"us","enUS",NativeAssetClient.CONFIG,NativeAssetClient.CDN_CONFIG,
                List.of(URI.create("https://us.cdn.blizzard.com/tpr/wow/")),Map.of(),Instant.EPOCH);
    }
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
