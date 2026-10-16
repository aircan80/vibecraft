package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ZoneRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void zoneDetailListsNpcsAndInstances() {
    ZoneDetail z = repos.zones().detail(40).get();
    assertThat(z.instances()).extracting(i -> i.name()).contains("The Deadmines");
    assertThat(z.subzones()).extracting(ZoneSummary::id).contains(1581L);
  }

  @Test
  void allListsTopLevelFirstAndOnlyZonesWithContent() {
    List<ZoneSummary> all = repos.zones().all();
    assertThat(all).extracting(ZoneSummary::id).contains(40L, 12L, 2437L);
    int firstChild = 0;
    while (firstChild < all.size() && all.get(firstChild).parentId() == null) {
      firstChild++;
    }
    assertThat(all.subList(firstChild, all.size())).allMatch(z -> z.parentId() != null);
    assertThat(all.subList(0, firstChild)).extracting(ZoneSummary::name).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    for (ZoneSummary z : all) {
      ZoneDetail d = repos.zones().detail(z.id()).get();
      assertThat(d.npcs().size() + d.objects().size() + d.quests().size() + d.instances().size() + d.subzones().size())
          .as("zone %s has content", z.id()).isPositive();
    }
  }
}
