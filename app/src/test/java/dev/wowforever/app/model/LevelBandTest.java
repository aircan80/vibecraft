package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.LevelBand.Weighted;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LevelBandTest {
  /** {@code count} NPCs of {@code level}, each met {@code spawns} times. */
  private static List<Weighted> npcs(int count, double level, int spawns) {
    List<Weighted> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      out.add(new Weighted(level, spawns));
    }
    return out;
  }

  @Test
  void anElwynnLikeZoneReadsOneToTen() {
    List<Weighted> elwynn = new ArrayList<>();
    elwynn.addAll(npcs(10, 2, 20)); // wolves, kobolds
    elwynn.addAll(npcs(10, 6, 20));
    elwynn.addAll(npcs(10, 9.5, 15)); // the Defias near Westfall
    elwynn.addAll(npcs(3, 11, 5)); // Hogger and friends
    elwynn.addAll(npcs(2, 60, 1)); // a visitor of another level
    assertThat(LevelBand.of(elwynn)).contains(new LevelBand(1, 10));
  }

  @Test
  void aWestfallLikeZoneReadsTenToTwenty() {
    List<Weighted> westfall = new ArrayList<>();
    westfall.addAll(npcs(10, 11.5, 20));
    westfall.addAll(npcs(10, 15, 20));
    westfall.addAll(npcs(10, 18.5, 20));
    westfall.addAll(npcs(1, 30, 1));
    assertThat(LevelBand.of(westfall)).contains(new LevelBand(10, 20));
  }

  @Test
  void weightsCountAndNpcsNeverMetDoNot() {
    assertThat(LevelBand.of(List.of(new Weighted(40, 0), new Weighted(22, 3)))).contains(new LevelBand(20, 20));
    assertThat(LevelBand.of(List.of(new Weighted(40, 0)))).isEmpty();
    assertThat(LevelBand.of(List.of())).isEmpty();
  }

  @Test
  void theLowEndRoundsDownAndTheHighEndToTheNearestFive() {
    assertThat(LevelBand.of(List.of(new Weighted(33, 1), new Weighted(43, 1))))
        .as("p10 34, p90 42").contains(new LevelBand(30, 40));
    assertThat(LevelBand.of(List.of(new Weighted(33, 1), new Weighted(48, 1))))
        .as("p10 34.5, p90 46.5").contains(new LevelBand(30, 45));
    assertThat(LevelBand.of(List.of(new Weighted(1, 1)))).contains(new LevelBand(1, 1));
    assertThat(LevelBand.of(List.of(new Weighted(58, 1)))).as("p10 and p90 58")
        .contains(new LevelBand(55, 60));
  }
}
