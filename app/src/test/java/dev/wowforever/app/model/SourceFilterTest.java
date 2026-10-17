package dev.wowforever.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceFilterTest {
  @Test
  void allKeepsEveryOrigin() {
    assertThat(SourceFilter.ALL.keeps("questie")).isTrue();
    assertThat(SourceFilter.ALL.keeps("atlasloot")).isTrue();
    assertThat(SourceFilter.ALL.keeps("both")).isTrue();
  }

  @Test
  void hidingOneSourceDropsOnlyRowsFromThatSourceAlone() {
    assertThat(SourceFilter.HIDE_ATLASLOOT_ONLY.keeps("atlasloot")).isFalse();
    assertThat(SourceFilter.HIDE_ATLASLOOT_ONLY.keeps("questie")).isTrue();
    assertThat(SourceFilter.HIDE_ATLASLOOT_ONLY.keeps("both")).isTrue();
    assertThat(SourceFilter.HIDE_QUESTIE_ONLY.keeps("questie")).isFalse();
    assertThat(SourceFilter.HIDE_QUESTIE_ONLY.keeps("atlasloot")).isTrue();
    assertThat(SourceFilter.HIDE_QUESTIE_ONLY.keeps("both")).isTrue();
    assertThat(SourceFilter.HIDE_QUESTIE_ONLY.keeps(null)).as("no origin: kept").isTrue();
  }

  @Test
  void eachFilterHasAMenuLabel() {
    assertThat(SourceFilter.ALL.label()).isEqualTo("All sources");
    assertThat(SourceFilter.HIDE_ATLASLOOT_ONLY.label()).isEqualTo("Hide AtlasLoot-only rows");
    assertThat(SourceFilter.HIDE_QUESTIE_ONLY.label()).isEqualTo("Hide Questie-only rows");
  }
}
