package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.NpcDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class NpcRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void detailHasDropsWithPctAndSpawns() {
    NpcDetail d = repos.npcs().detail(639).get();
    assertThat(d.drops()).anyMatch(l -> l.item().id() == 2874 && l.dropPct() == 60.0);
    assertThat(repos.npcs().detail(448).get().spawns()).isNotEmpty();
  }

  @Test
  void byZoneListsSpawnZoneNpcs() {
    assertThat(repos.npcs().byZone(2437)).extracting(n -> n.id()).contains(11520L);
  }
}
