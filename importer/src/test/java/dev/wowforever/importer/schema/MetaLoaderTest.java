package dev.wowforever.importer.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import org.junit.jupiter.api.Test;

class MetaLoaderTest {

  @Test
  void loadsItemSchema() {
    EntitySchema s = MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/itemMeta.lua"));
    assertThat(s.fieldCount()).isEqualTo(16);
    assertThat(s.index("npcDrops")).isEqualTo(2);
    assertThat(s.shape(2)).contains(Shape.IDARRAY);
    assertThat(s.type(1)).isEqualTo(FieldType.STRING);
    assertThat(s.shape(1)).isEmpty();
  }

  @Test
  void loadsQuestShapes() {
    EntitySchema s = MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/questMeta.lua"));
    assertThat(s.shape(s.index("startedBy"))).contains(Shape.QUESTGIVERS);
    assertThat(s.shape(s.index("reputationReward"))).contains(Shape.PAIRS);
  }

  @Test
  void namesFollowIndexOrder() {
    EntitySchema s = EntitySchema.fromMeta(Fixtures.questieDbRoot().resolve("src/meta/npcMeta.lua"));
    assertThat(s.entity()).isEqualTo("Npc");
    assertThat(s.names()).hasSize(15).startsWith("name", "minLevelHealth").endsWith("npcFlags");
    assertThat(s.name(7)).isEqualTo("spawns");
    assertThat(s.shape(7)).contains(Shape.SPAWNLIST);
    assertThat(s.type(9)).isEqualTo(FieldType.NUMBER);
  }

  @Test
  void unknownFieldNameIsRejected() {
    EntitySchema s = MetaLoader.load(Fixtures.questieDbRoot().resolve("src/meta/objectMeta.lua"));
    assertThatThrownBy(() -> s.index("nope")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope");
  }
}
