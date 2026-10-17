package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.Craft;
import dev.wowforever.app.model.CraftReagent;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ItemRepository#createdBy}, {@link ItemRepository#reagentFor} and {@link ItemRepository#taughtBy} on the
 * fixture database, whose client data holds Heavy Leather, Sulfuron Hammer, Eye of Sulfuras, Coarse Dynamite,
 * Transmute: Air to Fire and Prowler Steak; of their items only Eye of Sulfuras (17204) is in the item table.
 */
@ExtendWith(FixtureDb.class)
class CraftReaderTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void createdByListsTheCraftWithItsSkillAndReagents() {
    assertThat(repos.items().createdBy(4234)).singleElement().satisfies(k -> {
      assertThat(k.spellId()).isEqualTo(20649);
      assertThat(k.spellName()).isEqualTo("Heavy Leather");
      assertThat(k.itemId()).isEqualTo(4234);
      assertThat(k.quantityText()).isEqualTo("1");
      assertThat(k.skillLineId()).isEqualTo(165L);
      assertThat(k.skillName()).isEqualTo("Leatherworking");
      assertThat(k.minSkill()).isEqualTo(1);
      assertThat(k.trivialLow()).isEqualTo(125);
      assertThat(k.trivialHigh()).isEqualTo(135);
      assertThat(k.recipe()).isNull();
      assertThat(k.recipeItemIds()).isEmpty();
      assertThat(k.reagents()).containsExactly(new CraftReagent(2319, 5, null, null, null));
      assertThat(k.reagents().getFirst().displayName()).isEqualTo("Unknown item #2319");
    });
    assertThat(repos.items().createdBy(4365)).singleElement()
        .satisfies(k -> assertThat(k.quantityText()).isEqualTo("1-3"));
  }

  @Test
  void reagentsAreResolvedFromTheItemTableInSlotOrder() {
    assertThat(repos.items().createdBy(17182)).singleElement().satisfies(k -> {
      assertThat(k.spellName()).isEqualTo("Eye of Sulfuras");
      assertThat(k.skillLineId()).isNull();
      assertThat(k.skillName()).isNull();
      assertThat(k.minSkill()).isNull();
      assertThat(k.reagents()).extracting(CraftReagent::itemId).containsExactly(17193L, 17204L);
      CraftReagent eye = k.reagents().get(1);
      assertThat(eye.name()).isEqualTo("Eye of Sulfuras");
      assertThat(eye.count()).isEqualTo(1);
      assertThat(eye.quality()).isNotNull();
      assertThat(eye.iconId()).isNotNull();
    });
  }

  @Test
  void reagentForListsTheCraftsThatUseTheItem() {
    assertThat(repos.items().reagentFor(17204)).singleElement().satisfies(k -> {
      assertThat(k.spellId()).isEqualTo(21160);
      assertThat(k.itemId()).isEqualTo(17182);
      assertThat(k.itemName()).as("Sulfuras is not in the fixture's item table").isNull();
      assertThat(k.reagents()).hasSize(2);
    });
    assertThat(repos.items().reagentFor(2319)).extracting(Craft::spellId).containsExactly(20649L);
    assertThat(repos.items().reagentFor(25)).isEmpty();
  }

  @Test
  void taughtByListsTheCraftsARecipeTeaches() {
    assertThat(repos.items().taughtBy(13482)).singleElement().satisfies(k -> {
      assertThat(k.spellName()).isEqualTo("Transmute: Air to Fire");
      assertThat(k.recipeItemId()).isEqualTo(13482L);
      assertThat(k.recipeItemIds()).containsExactly(13482L);
      assertThat(k.skillName()).isEqualTo("Alchemy");
    });
    for (long recipe : List.of(238645L, 250394L)) {
      assertThat(repos.items().taughtBy(recipe)).as("recipe %d", recipe).singleElement().satisfies(k -> {
        assertThat(k.spellName()).isEqualTo("Prowler Steak");
        assertThat(k.quantityText()).isEqualTo("2");
        assertThat(k.recipeItemId()).as("the lowest id").isEqualTo(238645L);
        assertThat(k.recipeItemIds()).containsExactly(238645L, 250394L);
      });
    }
    assertThat(repos.items().createdBy(238637)).singleElement()
        .satisfies(k -> assertThat(k.recipeItemIds()).containsExactly(238645L, 250394L));
    assertThat(repos.items().taughtBy(4234)).isEmpty();
  }

  @Test
  void craftsAreOrderedBySkillThenNameAndReagentsKeepTheirInsertOrder(@TempDir Path dir) throws Exception {
    try (SchemaDb schema = SchemaDb.create(dir)) {
      schema.sql("INSERT INTO item (id, name, quality, icon_id) VALUES (50, 'Bolt', 2, 777), (90, 'Pattern', 1, 1)");
      craft(schema, 3, "Zeta", null, null);
      craft(schema, 2, "Beta", "Tailoring", 90L);
      craft(schema, 1, "Alpha", "Tailoring", null);
      schema.sql("INSERT INTO craft_reagent (spell_id, item_id, count) VALUES (2, 60, 3), (2, 50, 1), (1, 50, 2)");
      // a second recipe item for craft 2, inserted before the first: the ids come back in id order
      schema.sql("INSERT INTO craft_recipe_item (spell_id, item_id) VALUES (2, 95), (2, 90)");
      try (Database db = schema.open()) {
        ItemRepository items = new ItemRepository(db);
        List<Craft> crafts = items.createdBy(5);
        assertThat(crafts).extracting(Craft::spellName).containsExactly("Alpha", "Beta", "Zeta");
        Craft beta = crafts.get(1);
        assertThat(beta.reagents()).containsExactly(new CraftReagent(60, 3, null, null, null),
            new CraftReagent(50, 1, "Bolt", 2, 777L));
        assertThat(beta.recipeItemName()).isEqualTo("Pattern");
        assertThat(beta.itemName()).isNull();
        assertThat(crafts.get(2).reagents()).isEmpty();
        assertThat(items.reagentFor(50)).extracting(Craft::spellName).containsExactly("Alpha", "Beta");
        assertThat(items.taughtBy(90)).extracting(Craft::spellId).containsExactly(2L);
        assertThat(items.taughtBy(95)).extracting(Craft::spellId).containsExactly(2L);
        assertThat(beta.recipeItemIds()).containsExactly(90L, 95L);
        assertThat(crafts.get(0).recipeItemIds()).isEmpty();
      }
    }
  }

  @Test
  void everyReaderIsEmptyOnADatabaseWithoutTheCraftTables(@TempDir Path dir) throws Exception {
    try (SchemaDb schema = SchemaDb.create(dir)) {
      schema.sql("DROP TABLE craft_recipe_item");
      schema.sql("DROP TABLE craft_reagent");
      schema.sql("DROP TABLE craft");
      try (Database db = schema.open()) {
        ItemRepository items = new ItemRepository(db);
        assertThat(items.createdBy(4234)).isEmpty();
        assertThat(items.reagentFor(2319)).isEmpty();
        assertThat(items.taughtBy(13482)).isEmpty();
      }
    }
  }

  private static void craft(SchemaDb schema, long spell, String name, String skill, Long recipe) throws Exception {
    schema.sql("INSERT INTO craft (spell_id, spell_name, item_id, quantity_min, quantity_max, skill_name,"
        + " recipe_item_id) VALUES (?, ?, 5, 1, 1, ?, ?)", spell, name, skill, recipe);
  }
}
