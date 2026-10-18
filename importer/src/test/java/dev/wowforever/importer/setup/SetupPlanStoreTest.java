package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupPlanStoreTest {
  @TempDir Path temporary;
  @Test void selectedChoicesRoundTripWithoutAddingProvidersOrChangingPins() throws Exception {
    var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA);
    var plan=new SetupPlan(sources,Set.of(SetupPlan.Acquisition.WAGO_EXPORT),
        Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),List.of(),SetupPlan.currentPins(sources));
    var storage=new SetupStorage(temporary,List.of(),Optional.empty());
    Path file=temporary.resolve("draft.properties");SetupPlanStore.save(storage,file,plan);
    assertThat(SetupPlanStore.load(file)).isEqualTo(plan);
    assertThat(SetupPlanStore.load(file).allows(SetupPlan.Acquisition.BLIZZARD_CDN)).isFalse();
    assertThat(Files.exists(temporary.resolve("draft.properties.tmp"))).isFalse();
  }
  @Test void explicitRaidbotsChoiceAndSnapshotPinRoundTripWithoutAddingCdn() throws Exception {
    var sources=Set.of(SetupPlan.Source.QUESTIE,SetupPlan.Source.NATIVE_DATA,SetupPlan.Source.RAIDBOTS_HOTFIX);
    var plan=new SetupPlan(sources,Set.of(SetupPlan.Acquisition.WAGO_EXPORT),
        Optional.of(new SetupPlan.NativeIdentity("wow_classic_beta","1.60.1.70450","enUS")),List.of(),SetupPlan.currentPins(sources));
    var storage=new SetupStorage(temporary,List.of(),Optional.empty());
    Path file=temporary.resolve("hotfix.properties");SetupPlanStore.save(storage,file,plan);
    assertThat(SetupPlanStore.load(file)).isEqualTo(plan);
    assertThat(SetupPlanStore.load(file).allows(SetupPlan.Acquisition.BLIZZARD_CDN)).isFalse();
  }
  @Test void unknownVersionOrFieldsAndGameFolderDestinationsAreRejected() throws Exception {
    Path file=temporary.resolve("bad.properties");Files.writeString(file,"version=2\n");
    assertThatIOException().isThrownBy(() -> SetupPlanStore.load(file));
    Files.writeString(file,"version=1\nsources=QUESTIE\nacquisitions=\nroots=0\npin.QUESTIE=synthetic\nunexpected=true\n");
    assertThatIOException().isThrownBy(() -> SetupPlanStore.load(file));
    Path game=Files.createDirectory(temporary.resolve("game"));
    var storage=new SetupStorage(temporary,List.of(game),Optional.empty());
    var sources=Set.of(SetupPlan.Source.QUESTIE);
    var plan=new SetupPlan(sources,Set.of(),Optional.empty(),List.of(),SetupPlan.currentPins(sources));
    assertThatIOException().isThrownBy(() -> SetupPlanStore.save(storage,game.resolve("draft.properties"),plan));
    assertThat(Files.exists(game.resolve("draft.properties"))).isFalse();
  }
}
