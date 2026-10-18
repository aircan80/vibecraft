package dev.wowforever.app.assets;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlizzardBuildDescriptorTest {
    static BlizzardBuildDiscovery.Snapshot snapshot()throws Exception{
        String config="1".repeat(32),cdn="2".repeat(32);
        String versions="Region|BuildConfig|CDNConfig|BuildId|VersionsName\nus|"+config+"|"+cdn+"|70451|1.60.1.70451\n";
        String cdns="Name|Path|Hosts\nus|tpr/wow|us.cdn.blizzard.com\n";
        return new BlizzardBuildDiscovery.Snapshot("wow_classic_beta",Instant.EPOCH,
                BlizzardBuildDiscovery.parseBuilds("wow_classic_beta",versions,BlizzardBuildDiscovery.Channel.CURRENT),BlizzardBuildDiscovery.parseCdns(cdns),
                List.of(SelectedNativeAssetClientTest.document("versions",versions),SelectedNativeAssetClientTest.document("cdns",cdns)));
    }
    @Test void exactDiscoverySelectionFreezesMetadataAndLocale()throws Exception{
        var snapshot=snapshot();var descriptor=BlizzardBuildDescriptor.fromDiscovery(snapshot,snapshot.builds().getFirst(),"frFR");
        assertEquals("1.60.1.70451",descriptor.build());assertEquals(0x10,descriptor.localeMask());assertEquals("us",descriptor.region());
        assertEquals(List.of(URI.create("https://us.cdn.blizzard.com/tpr/wow/")),descriptor.cdnBases());assertEquals(2,descriptor.metadataSha256().size());
        assertThrows(UnsupportedOperationException.class,()->descriptor.cdnBases().clear());assertThrows(UnsupportedOperationException.class,()->descriptor.metadataSha256().clear());
    }
    @Test void rejectsSelectedBuildNotInOfficialSnapshot()throws Exception{
        var snapshot=snapshot();var original=snapshot.builds().getFirst();var different=new BlizzardBuildDiscovery.Build(original.product(),"eu",original.version(),original.buildId(),original.buildConfigMd5(),original.cdnConfigMd5(),original.productConfigMd5(),original.channel());
        assertThrows(IllegalArgumentException.class,()->BlizzardBuildDescriptor.fromDiscovery(snapshot,different,"enUS"));
        assertThrows(IllegalArgumentException.class,()->BlizzardBuildDescriptor.fromDiscovery(snapshot,original,"unknown"));
    }
    @Test void forgedSnapshotBuildListCannotOverrideCapturedDocument()throws Exception{
        var source=snapshot();var original=source.builds().getFirst();var altered=new BlizzardBuildDiscovery.Build(original.product(),original.region(),original.version(),original.buildId(),"3".repeat(32),original.cdnConfigMd5(),original.productConfigMd5(),original.channel());
        var forged=new BlizzardBuildDiscovery.Snapshot(source.product(),source.discoveredAt(),List.of(altered),source.cdns(),source.documents());
        assertThrows(IllegalArgumentException.class,()->BlizzardBuildDescriptor.fromDiscovery(forged,altered,"enUS"));
    }
    @Test void rejectsAlteredEvidenceOrThirdPartyDiscoveryDocuments()throws Exception{
        var source=snapshot();var original=source.documents().getFirst();
        for(var document:List.of(new BlizzardBuildDiscovery.Document(original.source(),original.fetchedAt(),original.status(),"0".repeat(64),original.bytes(),original.text()),new BlizzardBuildDiscovery.Document(URI.create("https://wago.tools/versions"),original.fetchedAt(),original.status(),original.sha256(),original.bytes(),original.text()))){
            var forged=new BlizzardBuildDiscovery.Snapshot(source.product(),source.discoveredAt(),source.builds(),source.cdns(),List.of(document,source.documents().getLast()));
            assertThrows(IllegalArgumentException.class,()->BlizzardBuildDescriptor.fromDiscovery(forged,source.builds().getFirst(),"enUS"));
        }
    }
}
