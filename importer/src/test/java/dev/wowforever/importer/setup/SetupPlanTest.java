package dev.wowforever.importer.setup;

import static org.assertj.core.api.Assertions.*;
import static dev.wowforever.importer.setup.SetupPlan.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupPlanTest {
  @TempDir Path temporary;
  private static final NativeIdentity BUILD=new NativeIdentity("wow_classic_beta","1.60.1.70450","enUS");
  private SetupPlan plan(Set<Source> sources,Set<Acquisition> acquisitions,List<Path> roots) {
    return new SetupPlan(sources,acquisitions,sources.contains(Source.NATIVE_DATA)?Optional.of(BUILD):Optional.empty(),roots,SetupPlan.currentPins(sources));
  }
  private SetupPlan nativePlan(Acquisition method) {return plan(Set.of(Source.QUESTIE,Source.NATIVE_DATA),Set.of(method),List.of());}
  @Test void wagoAloneIsValidWithoutGivingCdnOrLocalPermissionAndAtlasIsOptional() {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    assertThat(plan.allows(Acquisition.WAGO_EXPORT)).isTrue();
    assertThat(plan.allows(Acquisition.BLIZZARD_CDN)).isFalse();
    assertThat(plan.allows(Acquisition.LOCAL_INSTALLATION)).isFalse();
    assertThat(plan.coreSelection().atlasLoot()).isFalse();
    assertThat(plan.sourcePins()).containsOnlyKeys(Source.QUESTIE);
  }
  @Test void invalidSourceAcquisitionAndEditionCombinationsAreRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> plan(Set.of(Source.QUESTIE),Set.of(Acquisition.WAGO_EXPORT),List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(Set.of(Source.QUESTIE,Source.NATIVE_DATA),Set.of(),List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> nativePlan(Acquisition.LOCAL_INSTALLATION));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(Set.of(Source.NATIVE_DATA),Set.of(Acquisition.WAGO_EXPORT),List.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> new NativeIdentity("wow_classic","1.15.0.1","enUS"));
    assertThatIllegalArgumentException().isThrownBy(() -> new NativeIdentity("wow_classic_beta","1.60.1.70450","deDE"));
  }
  @Test void inputSetsCannotBeMutatedAfterPlanCreation() {
    Set<Source> selected=new HashSet<>(Set.of(Source.QUESTIE,Source.NATIVE_DATA));
    Set<Acquisition> providers=new HashSet<>(Set.of(Acquisition.WAGO_EXPORT));
    SetupPlan plan=plan(selected,providers,List.of());selected.add(Source.ATLASLOOT);providers.add(Acquisition.BLIZZARD_CDN);
    assertThat(plan.sources()).doesNotContain(Source.ATLASLOOT);
    assertThat(plan.allows(Acquisition.BLIZZARD_CDN)).isFalse();
  }
  @Test void preparedImporterUsesOnlySelectedMaterializedInputsAndJobArchive() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"first",List.of());
    Path questie=Files.createDirectory(temporary.resolve("questie"));
    Path ignoredAtlas=Files.createDirectory(temporary.resolve("unchecked-atlas"));
    Path ignoredAtt=Files.createDirectory(temporary.resolve("unchecked-att"));
    Path nativeData=client("https://wago.tools","1.60.1.70450",false);
    var inputs=new MaterializedInputs(questie,Optional.of(ignoredAtlas),Optional.of(ignoredAtt),Optional.of(new MaterializedClient(nativeData,Acquisition.WAGO_EXPORT)));
    var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs);
    assertThat(prepared.options().out()).isEqualTo(job.paths().archiveDatabase());
    assertThat(prepared.options().atlasLootDir()).isEmpty();assertThat(prepared.options().allTheThingsDir()).isEmpty();
    assertThat(prepared.options().skipAllTheThings()).isTrue();assertThat(prepared.coreSelection().atlasLoot()).isFalse();
    assertThat(Files.exists(prepared.options().out())).isFalse();
  }
  @Test void preparedCaptureUsesExistingArchiveWorkflowWithoutTouchingActiveDatabase() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    Path active=Files.writeString(temporary.resolve("active.sqlite"),"last working database sentinel");
    var job=plan.prepareJob(temporary.resolve("app"),Optional.of(active),"capture",List.of());
    Path data=client("https://wago.tools","1.60.1.70450",false);
    var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs(
        dev.wowforever.importer.Fixtures.questieDbRoot(),data,Acquisition.WAGO_EXPORT));
    String ingestion=prepared.capture();
    assertThat(ingestion).matches("[a-f0-9]{64}");
    try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+job.paths().archiveDatabase());
        var s=c.createStatement();var r=s.executeQuery("SELECT role FROM source_ingestion_input ORDER BY role")) {
      List<String> roles=new ArrayList<>();while(r.next())roles.add(r.getString(1));
      assertThat(roles).containsExactly("client","configuration","questiedb");
    }
    assertThat(Files.readString(active)).isEqualTo("last working database sentinel");
    assertThat(job.paths().candidateDatabase()).doesNotExist();
  }
  @Test void unselectedProviderMismatchedBuildAndEraFallbackCannotBePrepared() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.BLIZZARD_CDN);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    Path questie=Files.createDirectory(temporary.resolve("questie"));
    Path wago=client("https://wago.tools","1.60.1.70450",false);
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,wago,Acquisition.WAGO_EXPORT)));
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,wago,Acquisition.BLIZZARD_CDN)));
    Path wrong=client("https://cdn.example.invalid","1.60.1.70000",false);
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,wrong,Acquisition.BLIZZARD_CDN)));
    Path era=client("https://cdn.example.invalid","1.60.1.70450",true);
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,era,Acquisition.BLIZZARD_CDN)));
  }
  @Test void changedSourcePinsAndChangedNativeBytesRequireReview() throws Exception {
    SetupPlan original=nativePlan(Acquisition.WAGO_EXPORT);
    SetupPlan stale=new SetupPlan(original.sources(),original.acquisitions(),original.nativeIdentity(),List.of(),Map.of(Source.QUESTIE,"old-pin"));
    var job=original.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    Path questie=Files.createDirectory(temporary.resolve("questie"));
    Path data=client("https://wago.tools","1.60.1.70450",false);
    assertThatIOException().isThrownBy(() -> stale.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT)));
    Files.writeString(data.resolve("csv/Item.csv"),"changed");
    assertThatIOException().isThrownBy(() -> original.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT)));
  }
  @Test void unlistedEraAndHotfixFilesCannotSneakInFromAStaleInputDirectory() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    Path questie=Files.createDirectory(temporary.resolve("questie"));
    Path data=client("https://wago.tools","1.60.1.70450",false);
    Files.writeString(data.resolve("csv/ItemSparse.era.csv"),"stale era");
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT)));
    Files.delete(data.resolve("csv/ItemSparse.era.csv"));
    Files.writeString(data.resolve("hotfix.json"),"{}");
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT)));
  }
  @Test void communityOnlyDraftDoesNotPretendToBeAnActivatableForeverCatalog() throws Exception {
    SetupPlan plan=plan(Set.of(Source.QUESTIE),Set.of(),List.of());
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"job",List.of());
    assertThatIOException().isThrownBy(() -> plan.prepareCapture(job.storage(),job.paths(),new MaterializedInputs(temporary,Optional.empty(),Optional.empty(),Optional.empty())));
  }
  @Test void nativeBytesChangedAfterPreparationCannotBeCaptured() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"changed",List.of());
    Path data=client("https://wago.tools","1.60.1.70450",false);
    var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs(
        dev.wowforever.importer.Fixtures.questieDbRoot(),data,Acquisition.WAGO_EXPORT));
    Files.writeString(data.resolve("csv/Item.csv"),"ID,Name\n1,Changed after review\n");
    assertThatIOException().isThrownBy(prepared::capture).withMessageContaining("changed after review");
    assertThat(job.paths().archiveDatabase()).doesNotExist();assertNoSnapshots(job.paths().directory());
  }
  @Test void metadataReplacementAndUnselectedFilesAfterPreparationAreRejected() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    for(String changed:List.of("build.lock","csv/ItemSparse.era.csv","hotfix.json")) {
      var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"change-"+changed.replaceAll("[^A-Za-z]",""),List.of());
      Path data=client("https://wago.tools","1.60.1.70450",false);
      var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs(
          dev.wowforever.importer.Fixtures.questieDbRoot(),data,Acquisition.WAGO_EXPORT));
      Files.writeString(data.resolve(changed),"unreviewed replacement or new input");
      assertThatIOException().isThrownBy(prepared::capture).withMessageContaining("changed after review");
      assertThat(job.paths().archiveDatabase()).doesNotExist();assertNoSnapshots(job.paths().directory());
    }
  }
  @Test void selectedCommunityBytesAreFrozenAndSourcesRemainUnchangedOnSuccessfulCapture() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    Path questie=Files.createDirectory(temporary.resolve("local-questie"));
    Path fixture=dev.wowforever.importer.Fixtures.questieDbRoot();
    try(var files=Files.walk(fixture)) {
      for(Path file:files.filter(Files::isRegularFile).toList()) {
        Path target=questie.resolve(fixture.relativize(file));Files.createDirectories(target.getParent());Files.copy(file,target);
      }
    }
    Path data=client("https://wago.tools","1.60.1.70450",false);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"community",List.of());
    var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT));
    String selected=dev.wowforever.importer.source.SourceSpec.load().stream().filter(spec->spec.key().equals("questiedb"))
        .findFirst().orElseThrow().paths().stream().filter(name->Files.isRegularFile(questie.resolve(name))).findFirst().orElseThrow();
    Files.writeString(questie.resolve(selected),"changed source");
    assertThatIOException().isThrownBy(prepared::capture).withMessageContaining("changed after review");
    assertThat(job.paths().archiveDatabase()).doesNotExist();
    var accepted=plan.prepareCapture(job.storage(),job.paths(),inputs(questie,data,Acquisition.WAGO_EXPORT));
    String ingestion=accepted.capture();
    assertThat(Files.readString(questie.resolve(selected))).isEqualTo("changed source");
    assertNoSnapshots(job.paths().directory());
    try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+job.paths().archiveDatabase());
        var statement=connection.prepareStatement("SELECT b.bytes FROM source_ingestion_input i JOIN source_archive_file f ON f.snapshot_id=i.snapshot_id JOIN source_archive_blob b ON b.sha256=f.sha256 WHERE i.ingestion_id=? AND i.role='questiedb' AND f.path=?")) {
      statement.setString(1,ingestion);statement.setString(2,selected);
      try(var rows=statement.executeQuery()){assertThat(rows.next()).isTrue();assertThat(new String(rows.getBytes(1),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("changed source");}
    }
  }
  @Test void failedArchiveWriteCleansPrivateCopiesWithoutChangingSources() throws Exception {
    SetupPlan plan=nativePlan(Acquisition.WAGO_EXPORT);
    var job=plan.prepareJob(temporary.resolve("app"),Optional.empty(),"failed-write",List.of());
    Path data=client("https://wago.tools","1.60.1.70450",false);
    String original=Files.readString(data.resolve("csv/Item.csv"));
    var prepared=plan.prepareCapture(job.storage(),job.paths(),inputs(
        dev.wowforever.importer.Fixtures.questieDbRoot(),data,Acquisition.WAGO_EXPORT));
    Files.createDirectory(job.paths().archiveDatabase());
    assertThatThrownBy(prepared::capture).isInstanceOf(java.sql.SQLException.class);
    assertNoSnapshots(job.paths().directory());
    assertThat(Files.readString(data.resolve("csv/Item.csv"))).isEqualTo(original);
    assertThat(job.paths().candidateDatabase()).doesNotExist();
  }
  private void assertNoSnapshots(Path directory) throws Exception {
    try(var files=Files.list(directory)){assertThat(files.map(path->path.getFileName().toString()).toList()).noneMatch(name->name.startsWith("capture-"));}
  }
  private MaterializedInputs inputs(Path questie,Path client,Acquisition method){return new MaterializedInputs(questie,Optional.empty(),Optional.empty(),Optional.of(new MaterializedClient(client,method)));}
  private Path client(String base,String build,boolean era) throws Exception {
    Path dir=Files.createTempDirectory(temporary,"client-");Files.createDirectory(dir.resolve("csv"));
    byte[] bytes="ID,Name\n1,Synthetic\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(dir.resolve("csv/Item.csv"),bytes);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    Files.writeString(dir.resolve("build.lock"),"{\"product\":\"wow_classic_beta\",\"build\":\""+build+"\",\"locale\":\"enUS\",\"fetched_at\":\"synthetic\",\"base_url\":\""+base+"\",\"tables\":{\"Item\":\""+hash+"\"}"+(era?",\"fallback_product\":\"wow_classic\",\"fallback_build\":\"1.15.0.1\"":"")+"}");return dir;
  }
}
