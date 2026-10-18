package dev.wowforever.app;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.db.NativeRecipeGuideRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Exact Forever crafting controls, including a newly introduced cooking output. */
@Tag("shipped-data")
class ShippedRecipeGuideTest extends ShippedDataSupport {
  @Test void savoryWhimsyfinUsesExactNativeSpellReagentsAndSkill() {
    var snapshot=new NativeRecipeGuideRepository(db).forItem(251525);
    assertThat(snapshot.build()).isEqualTo("1.60.1.70450");
    assertThat(snapshot.recipes()).singleElement().satisfies(recipe->{
      assertThat(recipe.spellId()).isEqualTo(1252566);assertThat(recipe.outputName()).isEqualTo("Savory Whimsyfin Delight");assertThat(recipe.quantity()).isEqualTo("1");
      assertThat(recipe.ingredients()).extracting(NativeRecipeGuideRepository.Ingredient::itemId).containsExactly(251524L,2678L);
      assertThat(recipe.ingredients()).extracting(NativeRecipeGuideRepository.Ingredient::count).containsExactly(1L,1L);
      assertThat(recipe.skills()).singleElement().satisfies(skill->{assertThat(skill.id()).isEqualTo(185);assertThat(skill.rank()).isEqualTo(1);assertThat(skill.trivialLow()).isEqualTo(125);assertThat(skill.trivialHigh()).isEqualTo(165);});
      assertThat(recipe.evidence()).anyMatch(e->e.table().equals("SpellReagents")&&e.line()>0);
    });
  }
  @Test void conjuredFoodAndWaterRemainContextDependentClassAbilities() {
    var repo=new NativeRecipeGuideRepository(db);
    for(long[] pair:new long[][]{{5350,5504},{5349,587},{281060,1310181}})assertThat(repo.forItem(pair[0]).recipes()).filteredOn(recipe->recipe.spellId()==pair[1]).isNotEmpty().allSatisfy(recipe->assertThat(recipe.quantity()).isEqualTo("Context-dependent"));
    assertThat(repo.forItem(5350).recipes()).anySatisfy(recipe->{assertThat(recipe.spellId()).isEqualTo(5504);assertThat(recipe.skills()).anySatisfy(skill->{assertThat(skill.association()).isEqualTo("Class ability");assertThat(skill.profession()).isFalse();});});
  }
}
