package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.InstanceDetail;
import dev.wowforever.app.model.LootRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class InstanceRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void bossLootInSlotOrder() {
    InstanceDetail rfc = repos.instances().detail(1).get();
    assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).isSorted();
  }

  @Test
  void ragefireStartsWithTaragaman() {
    InstanceDetail rfc = repos.instances().detail(1).get();
    assertThat(rfc.summary().name()).isEqualTo("Ragefire Chasm");
    assertThat(rfc.bosses().get(0).npcId()).isEqualTo(11520L);
    assertThat(repos.instances().bossLoot(rfc.bosses().get(0).id())).extracting(LootRow::slot).containsExactly(1, 2, 3);
    assertThat(repos.instances().all()).extracting(i -> i.id()).contains(1L);
  }
}
