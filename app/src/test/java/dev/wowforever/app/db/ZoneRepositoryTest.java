package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.ZoneDetail;
import dev.wowforever.app.model.ZoneMap;
import dev.wowforever.app.model.ZoneSummary;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class ZoneRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void zoneSummaryNamesItsParentAndInstancesTheirZone() {
    ZoneSummary deadmines = repos.zones().detail(1581).get().summary();
    assertThat(deadmines.parentId()).isEqualTo(40L);
    assertThat(deadmines.parentName()).isEqualTo("Westfall");
    assertThat(repos.zones().all()).filteredOn(z -> z.id() == 1581).singleElement()
        .satisfies(z -> assertThat(z.parentName()).isEqualTo("Westfall"));
    assertThat(repos.zones().detail(40).get().instances()).filteredOn(i -> i.id() != 0)
        .anySatisfy(i -> assertThat(i.zoneName()).isEqualTo("The Deadmines"));
  }

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

  @Test
  void mapJoinsTheZonesUiMap() {
    ZoneMap map = repos.zones().map(40).orElseThrow();
    assertThat(map.uiMapId()).isEqualTo(1436);
    assertThat(map.width()).isEqualTo(1002);
    assertThat(map.height()).isEqualTo(668);
    assertThat(map.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8); // JPEG start-of-image marker
    assertThat(repos.zones().detail(40).get().map())
        .hasValueSatisfying(m -> assertThat(m.uiMapId()).isEqualTo(1436));
  }

  @Test
  void zoneWithoutAMapHasNone() {
    assertThat(repos.zones().map(2437)).isEmpty();
    assertThat(repos.zones().detail(2437).get().map()).isEmpty();
    assertThat(repos.zones().map(-1)).isEmpty();
  }
}
