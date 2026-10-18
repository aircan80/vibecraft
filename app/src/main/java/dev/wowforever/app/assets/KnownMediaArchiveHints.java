package dev.wowforever.app.assets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Reviewed routing metadata only. The referenced official index must still authenticate the EKey/interval. */
final class KnownMediaArchiveHints {
    static final String RESOURCE="/dev/wowforever/app/assets/forever-70450-media-hints.tsv";
    static final String SHA256="6448f55405deffd404eb006a1b40ff3abea81db58bb6320e726a8caebe6111f0";
    record Entry(long fileDataId,String contentMd5,String encodedKey,String archive) {}
    private final Map<String,String> identity;
    private final Map<Long,Entry> entries;
    private KnownMediaArchiveHints(Map<String,String> identity,Map<Long,Entry> entries){this.identity=Map.copyOf(identity);this.entries=Map.copyOf(entries);}
    private static final class Bundled {
        static final KnownMediaArchiveHints VALUE;
        static final IOException FAILURE;
        static {
            KnownMediaArchiveHints value=null;IOException failure=null;
            try(var input=KnownMediaArchiveHints.class.getResourceAsStream(RESOURCE)) {
                if(input==null)throw new IOException("Known-media metadata resource missing");
                byte[] bytes=input.readNBytes(16385);value=parse(bytes,SHA256);
            }catch(IOException invalid){failure=invalid;}
            VALUE=value;FAILURE=failure;
        }
    }
    static KnownMediaArchiveHints reviewed()throws IOException {
        if(Bundled.FAILURE!=null)throw new IOException("Known-media routing metadata unavailable",Bundled.FAILURE);return Bundled.VALUE;
    }
    static KnownMediaArchiveHints parse(byte[] bytes,String expectedSha256)throws IOException {
        if(bytes.length>16384||!hash(bytes).equals(expectedSha256))throw new IOException("Known-media metadata SHA-256 mismatch");
        String[] lines=new String(bytes,StandardCharsets.UTF_8).split("\\R");
        if(lines.length<4||!lines[1].startsWith("# ")||!lines[2].equals("fdid\tcontent_md5\tencoding_key\tarchive"))throw new IOException("Known-media metadata framing");
        Map<String,String> identity=new HashMap<>();
        for(String pair:lines[1].substring(2).split(" ")) {
            String[] parts=pair.split("=",-1);if(parts.length!=2||identity.putIfAbsent(parts[0],parts[1])!=null)throw new IOException("Known-media identity fields");
        }
        if(!identity.keySet().equals(Set.of("product","build","config","cdn","root","encoding"))
                ||!identity.get("product").matches("wow(?:_[a-z0-9]+)*")||!identity.get("build").matches("[0-9]+(?:\\.[0-9]+){3}"))throw new IOException("Known-media build identity");
        for(String field:List.of("config","cdn","root","encoding"))if(!identity.get(field).matches("[0-9a-f]{32}"))throw new IOException("Known-media hash identity");
        Map<Long,Entry> entries=new TreeMap<>();
        for(int i=3;i<lines.length;i++) {
            String[] row=lines[i].split("\t",-1);if(row.length!=4||entries.size()>=64)throw new IOException("Known-media row framing/bound");
            long fdid;try{fdid=Long.parseLong(row[0]);}catch(NumberFormatException invalid){throw new IOException("Known-media FDID",invalid);}
            if(fdid<1||fdid>0xffffffffL)throw new IOException("Known-media FDID bound");
            for(int j=1;j<4;j++)if(!row[j].matches("[0-9a-f]{32}"))throw new IOException("Known-media row hash");
            if(entries.putIfAbsent(fdid,new Entry(fdid,row[1],row[2],row[3]))!=null)throw new IOException("Duplicate known-media FDID");
        }
        if(entries.isEmpty())throw new IOException("Empty known-media metadata");return new KnownMediaArchiveHints(identity,entries);
    }
    Optional<String> archive(BlizzardBuildDescriptor descriptor,String root,String encoding,long fdid,String content,String ekey) {
        if(!identity.get("product").equals(descriptor.product())||!identity.get("build").equals(descriptor.build())
                ||!identity.get("config").equals(descriptor.buildConfigMd5())||!identity.get("cdn").equals(descriptor.cdnConfigMd5())
                ||!identity.get("root").equals(root)||!identity.get("encoding").equals(encoding))return Optional.empty();
        Entry entry=entries.get(fdid);
        return entry!=null&&entry.contentMd5().equals(content)&&entry.encodedKey().equals(ekey)?Optional.of(entry.archive()):Optional.empty();
    }
    Collection<Entry> entries(){return entries.values();}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
