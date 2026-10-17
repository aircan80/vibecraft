package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Forever's budget-computed stats, damage and armour against the Era rows of the same items (see
 * {@link EraOverlapOracle}). The fixture run must match every unchanged item; the real run, when the client data is
 * cached on this machine, must match at least 95 % of the unchanged items per category, and prints the rates and the
 * first mismatches.
 */
class EraOverlapOracleTest {
  private static final double REAL_THRESHOLD = 0.95;

  @Test
  void fixtureOverlapMatchesEveryUnchangedItem() {
    Map<String, EraOverlapOracle.Category> result = EraOverlapOracle.run(ClientItems.fixture());
    System.out.println("Era overlap oracle, fixture:\n" + EraOverlapOracle.render(result));
    assertThat(result.get("stats").unchanged).isGreaterThanOrEqualTo(15);
    assertThat(result.get("damage").unchanged).isGreaterThanOrEqualTo(10);
    assertThat(result.get("armor").unchanged).isGreaterThanOrEqualTo(8);
    for (EraOverlapOracle.Category c : result.values()) {
      assertThat(c.mismatches).as(c.name).isEmpty();
    }
  }

  @Test
  void realOverlapMatchesAtLeast95PercentPerCategory() {
    Optional<Path> dir = RealClientData.dir();
    assumeTrue(dir.isPresent(), "no cached client data on this machine");
    Map<String, EraOverlapOracle.Category> result = EraOverlapOracle.run(new ClientDataSource(dir.get()));
    System.out.println("Era overlap oracle, " + dir.get() + ":\n" + EraOverlapOracle.render(result));
    for (EraOverlapOracle.Category c : result.values()) {
      assertThat(c.unchanged).as(c.name + " compared").isGreaterThan(500);
      assertThat(c.rate()).as(c.toString()).isGreaterThanOrEqualTo(REAL_THRESHOLD);
    }
  }
}
