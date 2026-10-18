package dev.wowforever.app.assets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlizzardBuildDiscoveryTest {
    static final String CONFIG="84bea4233331e390fda8724c6f91ffa5", CDN="f065eade5276a5ac05606e60a4c5a1f7";
    static final String VERSIONS="Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|BuildId!DEC:4|VersionsName!String:0\n## seqn = 1\nus|"+CONFIG+"|"+CDN+"|70450|1.60.1.70450\n";
    static final String CDNS="Name!STRING:0|Path!STRING:0|Hosts!STRING:0\nus|tpr/wow|level3.blizzard.com us.cdn.blizzard.com\n";
    static BlizzardBuildDiscovery.Response response(int status,String text){return new BlizzardBuildDiscovery.Response(status,text.getBytes(StandardCharsets.UTF_8));}
    static BlizzardBuildDiscovery discovery(int preloadStatus,String preload){return new BlizzardBuildDiscovery((uri,c,p)->{
        return switch(uri.getPath().substring(uri.getPath().lastIndexOf('/')+1)){
            case "versions"->response(200,VERSIONS);case "cdns"->response(200,CDNS);default->response(preloadStatus,preload);
        };
    });}
    @Test void exactOfficialPointersAndEvidenceRemainImmutable()throws Exception{
        try(var client=discovery(200,"No matched data for selector 'v2/products/wow_classic_beta/bgdl'")){
            var result=client.discover("wow_classic_beta",()->false,p->{});
            assertEquals(1,result.builds().size());var b=result.builds().getFirst();
            assertEquals("wow_classic_beta",b.product());assertEquals("us",b.region());assertEquals("1.60.1.70450",b.version());
            assertEquals(CONFIG,b.buildConfigMd5());assertEquals(CDN,b.cdnConfigMd5());assertEquals(BlizzardBuildDiscovery.Channel.CURRENT,b.channel());
            assertEquals(BlizzardBuildDiscovery.Status.NOT_PUBLISHED,result.documents().getLast().status());
            assertEquals(64,result.documents().getFirst().sha256().length());assertTrue(result.documents().getFirst().source().toString().startsWith("https://us.version.battle.net/"));
            assertThrows(UnsupportedOperationException.class,()->result.builds().clear());
            assertThrows(UnsupportedOperationException.class,()->result.cdns().getFirst().hosts().clear());
        }
    }
    @Test void preloadExistsOnlyWhenActualPublishedTableContainsBuild()throws Exception{
        try(var client=discovery(200,VERSIONS.replace("70450","70451"))){
            var result=client.discover("wow_classic_beta",()->false,p->{});assertEquals(2,result.builds().size());
            assertEquals(BlizzardBuildDiscovery.Channel.PRELOAD,result.builds().getLast().channel());assertEquals(70451,result.builds().getLast().buildId());
        }
        try(var client=discovery(404,"not found")){assertEquals(1,client.discover("wow_classic_beta",()->false,p->{}).builds().size());}
        try(var client=discovery(204,"")){assertEquals(1,client.discover("wow_classic_beta",()->false,p->{}).builds().size());}
    }
    @Test void failuresAreNotReclassifiedAsMissingPreload()throws Exception{
        try(var client=discovery(503,"temporarily unavailable")){assertThrows(IOException.class,()->client.discover("wow_classic_beta",()->false,p->{}));}
        try(var client=discovery(200,"invalid metadata")){assertThrows(IOException.class,()->client.discover("wow_classic_beta",()->false,p->{}));}
    }
    @Test void typedHeadersBomCommentsAndColumnOrderAreParsed()throws Exception{
        String input="\ufeff#comment\nVersionsName!String:0|CDNConfig!HEX:16|BuildId!DEC:4|Region!STRING:0|BuildConfig!HEX:16\n1.60.1.70450|"+CDN+"|70450|eu|"+CONFIG+"\n";
        assertEquals("eu",BlizzardBuildDiscovery.parseBuilds("wow_classic_beta",input,BlizzardBuildDiscovery.Channel.CURRENT).getFirst().region());
    }
    @Test void rejectsContradictoryBuildAndMalformedHashesRowsHeaders() {
        for(String input:List.of(VERSIONS.replace("1.60.1.70450","1.60.1.70451"),VERSIONS.replace(CONFIG,"../../evil"),VERSIONS.replace("|70450|","|x|"),VERSIONS+"broken|row",VERSIONS.replace("BuildConfig!HEX:16","Region!STRING:0"))){
            assertThrows(IOException.class,()->BlizzardBuildDiscovery.parseBuilds("wow_classic_beta",input,BlizzardBuildDiscovery.Channel.CURRENT));
        }
    }
    @Test void rejectsUnsafeCdnPathsAndHosts(){
        assertThrows(IOException.class,()->BlizzardBuildDiscovery.parseCdns(CDNS.replace("tpr/wow","../wow")));
        assertThrows(IOException.class,()->BlizzardBuildDiscovery.parseCdns(CDNS.replace("level3.blizzard.com","https://evil.test/")));
        assertThrows(IOException.class,()->BlizzardBuildDiscovery.parseCdns(CDNS.replace("us.cdn.blizzard.com","evil..test")));
    }
    @Test void cancellationBeforeAndBetweenRequestsAvoidsFurtherTransport()throws Exception{
        AtomicInteger calls=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();
        try(var client=new BlizzardBuildDiscovery((uri,c,p)->{calls.incrementAndGet();cancelled.set(true);return response(200,VERSIONS);})){
            assertThrows(CancellationException.class,()->client.discover("wow_classic_beta",()->true,p->{}));assertEquals(0,calls.get());
            assertThrows(CancellationException.class,()->client.discover("wow_classic_beta",cancelled::get,p->{}));assertEquals(1,calls.get());
        }
    }
    @Test void refusesEdtAndInvalidProductsBeforeTransport()throws Exception{
        AtomicInteger calls=new AtomicInteger();try(var client=new BlizzardBuildDiscovery((uri,c,p)->{calls.incrementAndGet();return response(200,VERSIONS);})){
            SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->client.discover("wow_classic_beta",()->false,p->{})));
            assertThrows(IllegalArgumentException.class,()->client.discover("../wow",()->false,p->{}));assertEquals(0,calls.get());
        }
    }
    @Test void rejectsOversizedOrInvalidUtf8Metadata()throws Exception{
        try(var client=new BlizzardBuildDiscovery((uri,c,p)->new BlizzardBuildDiscovery.Response(200,new byte[1024*1024+1]))){assertThrows(IOException.class,()->client.discover("wow_classic_beta",()->false,p->{}));}
        try(var client=new BlizzardBuildDiscovery((uri,c,p)->new BlizzardBuildDiscovery.Response(200,new byte[]{(byte)0xff}))){assertThrows(IOException.class,()->client.discover("wow_classic_beta",()->false,p->{}));}
    }
}
