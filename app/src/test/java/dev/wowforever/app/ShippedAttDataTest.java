package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.importer.allthethings.AllTheThingsSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Release checks for the actual retained ATT evidence and the Sleeping Bag chain it supplies. */
@Tag("shipped-data")
class ShippedAttDataTest extends ShippedDataSupport {
  private static final Set<String> SLEEPING_BAG = Set.of("79192:79007:pre_single", "79192:79008:pre_single",
      "79980:79192:pre_single", "79974:79980:pre_single", "79975:79974:pre_single", "79976:79975:pre_single");

  @Test void sleepingBagRelationshipsHaveExactArchivedAuthoredEvidence() throws Exception {
    var evidence = repos.quests().chain(79976).evidence();
    assertThat(evidence).hasSize(6);
    assertThat(evidence.stream().map(e -> e.questId() + ":" + e.relatedId() + ":" + e.kind()).collect(java.util.stream.Collectors.toSet()))
        .isEqualTo(SLEEPING_BAG);
    String pin = db.meta().get("att_commit");
    assertThat(pin).isEqualTo(AllTheThingsSource.PIN);
    for (var source : evidence) {
      assertThat(source.source()).isEqualTo("allthethings-forever");
      assertThat(source.evidenceStatus()).isEqualTo("authored");
      assertThat(source.qualification()).contains("not in-game verified");
      assertThat(source.artifactPath()).startsWith(AllTheThingsSource.FOREVER_ROOT);
      URI url = source.uri().orElseThrow();
      assertThat(url.getScheme()).isEqualTo("https");
      assertThat(url.getHost()).isEqualTo("github.com");
      assertThat(url.getPath()).isEqualTo("/" + AllTheThingsSource.OWNER + "/" + AllTheThingsSource.REPOSITORY
          + "/blob/" + pin + "/" + source.artifactPath());
      assertThat(url.getFragment()).matches("L[1-9][0-9]*");
      int line = Integer.parseInt(url.getFragment().substring(1));
      byte[] bytes = db.read(c -> {
        try (var s = c.prepareStatement("SELECT b.bytes FROM source_archive_file f JOIN source_archive_blob b"
            + " ON b.sha256=f.sha256 JOIN source_archive_snapshot snapshot ON snapshot.id=f.snapshot_id"
            + " WHERE f.snapshot_id=? AND f.path=? AND snapshot.version=?")) {
          s.setString(1, source.snapshotId()); s.setString(2, source.artifactPath()); s.setString(3, pin);
          try (var r = s.executeQuery()) { assertThat(r.next()).isTrue(); return r.getBytes(1); }
        }
      });
      var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
      assertThat(line).isLessThanOrEqualTo(lines.size());
      assertThat(lines.get(line - 1)).contains("q(" + source.questId() + ",");
    }
  }

  @Test void chainIncludesBothFactionStartsAsAlternativesAndOneSixQuestSpine() {
    var chain = repos.quests().chain(79976);
    assertThat(chain.steps()).hasSize(7).allSatisfy(step -> {
      assertThat(step.name()).isNotBlank();
      assertThat(step.loop()).isFalse();
    });
    assertThat(chain.steps()).extracting(QuestChain.Step::questId)
        .containsExactlyInAnyOrder(79007L, 79008L, 79192L, 79980L, 79974L, 79975L, 79976L);
    assertThat(chain.steps().stream().filter(s -> s.parentQuestId() != null && s.parentQuestId() == 79192))
        .hasSize(2).allSatisfy(step -> assertThat(step.connector()).isEqualTo(QuestChain.Connector.REQUIRES_ONE));
    assertThat(chain.spine()).hasSize(6);
    assertThat(chain.spine().getFirst().questId()).isIn(79007L, 79008L);
    assertThat(chain.spine().stream().skip(1).map(QuestChain.Step::questId)).containsExactly(79192L, 79980L, 79974L, 79975L, 79976L);
    assertThat(chain.truncated()).isFalse();
    assertThat(chain.attUnsupportedConditions()).isEqualTo(Integer.parseInt(db.meta().get("att_unsupported_conditions")));
  }

  @Test void catalogDistinguishesArchivedAttAndDerivedBaselineFromLegacyProviderReferences() {
    var catalog = repos.sourceArchive().catalog();
    assertThat(catalog.available()).isTrue();
    assertThat(catalog.history()).filteredOn(run -> run.active()).singleElement().satisfies(run -> {
      assertThat(run.inputs()).extracting(input -> input.role()).containsExactlyInAnyOrder("allthethings", "legacy-app", "configuration");
      assertThat(run.validationSummary()).containsIgnoringCase("Legacy baseline");
    });
    assertThat(catalog.snapshots()).filteredOn(s -> s.source().equals("allthethings")).anySatisfy(snapshot -> {
      assertThat(snapshot.version()).isEqualTo(AllTheThingsSource.PIN);
      assertThat(snapshot.files()).extracting(file -> file.path()).containsExactlyInAnyOrderElementsOf(AllTheThingsSource.paths());
      assertThat(catalog.activeRoles().get(snapshot.id())).contains("allthethings");
    });
    assertThat(catalog.snapshots()).filteredOn(s -> s.id().equals(db.meta().get("source_base_snapshot_id")))
        .singleElement().satisfies(snapshot -> {
          assertThat(snapshot.source()).isEqualTo("legacy-app-database");
          assertThat(snapshot.version()).contains("derived app baseline");
          assertThat(snapshot.files()).singleElement().satisfies(file -> assertThat(file.path()).isEqualTo("base.sqlite"));
          assertThat(catalog.activeRoles().get(snapshot.id())).contains("legacy-app");
        });
    assertThat(catalog.legacySources()).extracting(source -> source.source()).contains("QuestieDB", "Blizzard client (via wago.tools)");
    assertThat(catalog.snapshots()).noneSatisfy(snapshot -> assertThat(snapshot.source()).isIn("questiedb", "blizzard-client"));
  }

  @Test void selectedCountersReportAndCanonicalRowsAgreeWithoutOverwritingCoreData() throws Exception {
    long selected = Long.parseLong(db.meta().get("att_quest_links_selected"));
    assertThat(selected).isPositive();
    assertThat(scalar("SELECT count(*) FROM (SELECT DISTINCT quest_id,related_id,kind FROM quest_relation_source"
        + " WHERE source='allthethings-forever')")).isEqualTo(selected);
    assertThat(scalar("SELECT count(*) FROM quest_relation_source s LEFT JOIN quest_relation r"
        + " ON r.quest_id=s.quest_id AND r.related_id=s.related_id AND r.kind=s.kind WHERE r.quest_id IS NULL")).isZero();
    String report = committedReport();
    assertThat(reportCounter(report, "allthethings.questLinksSelected")).isEqualTo(selected);
    assertThat(reportCounter(report, "allthethings.questGroupsConflicting"))
        .isEqualTo(Long.parseLong(db.meta().get("att_quest_links_conflict")));
    assertThat(reportCounter(report, "allthethings.conditionsUnsupported"))
        .isEqualTo(Long.parseLong(db.meta().get("att_unsupported_conditions")));
    assertThat(db.meta().get("client_build")).isEqualTo("1.60.1.70450");
    assertThat(count("item")).isGreaterThan(20_000);
  }
}
