package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.ObjectDetail;
import dev.wowforever.app.model.ObjectSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ObjectRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void detailHasContentsAndSpawns() {
    ObjectDetail book = repos.objects().detail(386759).get();
    assertThat(book.summary().zoneName()).isEqualTo("Elwynn Forest");
    assertThat(book.contains()).isNotEmpty().allMatch(l -> l.dropPct() == null && l.slot() == null);
    assertThat(repos.objects().detail(1560).get().spawns()).isNotEmpty().allMatch(s -> s.zoneId() == 12);
  }

  @Test
  void byZoneAndMissing() {
    assertThat(repos.objects().byZone(12)).extracting(ObjectSummary::id).contains(386759L, 1560L);
    assertThat(repos.objects().detail(-1)).isEmpty();
  }
}
