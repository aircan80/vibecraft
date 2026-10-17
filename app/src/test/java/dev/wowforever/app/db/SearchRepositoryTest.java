package dev.wowforever.app.db;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.model.SearchHit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(FixtureDb.class)
class SearchRepositoryTest {
  private final Repositories repos = FixtureDb.repositories();

  @Test
  void prefixSearchFindsItem() {
    assertThat(repos.search().search("subterr", Optional.empty(), 10)).extracting(h -> h.ref()).contains(new EntityRef(ITEM, 14149));
  }

  @Test
  void specialCharactersDoNotThrow() {
    for (String q : List.of("\"", "'", "*", "-", "OR", "(", "a\"b", "   ")) {
      assertThatCode(() -> repos.search().search(q, Optional.empty(), 10)).doesNotThrowAnyException();
    }
  }

  @Test
  void kindFilter() {
    List<SearchHit> hits = repos.search().search("Taragaman", Optional.of(NPC), 10);
    assertThat(hits).isNotEmpty().allMatch(h -> h.ref().kind() == NPC);
  }

  @Test
  void matchExpressionQuotesEachTermAsPrefix() {
    assertThat(SearchRepository.matchExpression("  a\"b   cape ")).isEqualTo("\"a\"\"b\"* \"cape\"*");
    assertThat(SearchRepository.matchExpression("OR ( -")).isEqualTo("\"OR\"*");
    assertThat(SearchRepository.matchExpression(" \" * ")).isEmpty();
  }

  @Test
  void quotedTermsStillMatch() {
    assertThat(repos.search().search("\"subterranean\"", Optional.of(ITEM), 10))
        .extracting(SearchHit::ref).contains(new EntityRef(ITEM, 14149));
  }

  @Test
  void blankTextGivesNoHits() {
    assertThat(repos.search().search("  \t ", Optional.empty(), 10)).isEmpty();
  }

  @Test
  void multipleTermsAreAnded() {
    assertThat(repos.search().search("subterranean cape", Optional.empty(), 10))
        .extracting(SearchHit::ref).contains(new EntityRef(ITEM, 14149));
    assertThat(repos.search().search("subterranean taragaman", Optional.empty(), 10)).isEmpty();
  }

  @Test
  void zonePlaceholderNamesAreNotSearchable(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      s.executeUpdate("insert into search_index(kind, id, name) values ('zone', 9999, 'Zone #9999')");
      s.executeUpdate("insert into search_index(kind, id, name) values ('zone', 9998, 'Zone 9999 Valley')");
    }
    try (Database db = Database.open(copy)) {
      assertThat(new SearchRepository(db).search("Zone 9999", Optional.empty(), 10))
          .extracting(SearchHit::name).containsExactly("Zone 9999 Valley");
    }
  }

  @Test
  void aSubZoneNamedLikeItsParentIsHiddenAndTheParentKept(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      s.executeUpdate("insert into zone(id, name, parent_id, kind) values (95000, 'Gloomvale', null, 'zone'), "
          + "(95001, 'Gloom Maul', 95000, 'dungeon'), (95002, 'Gloom Maul', 95001, 'dungeon'), "
          + "(95003, 'Gloom Maul', 95001, 'dungeon'), (95004, 'Gloom Maul Gate', 95001, 'subzone')");
      s.executeUpdate("insert into search_index(kind, id, name) values ('zone', 95001, 'Gloom Maul'), "
          + "('zone', 95002, 'Gloom Maul'), ('zone', 95003, 'Gloom Maul'), ('zone', 95004, 'Gloom Maul Gate')");
    }
    try (Database db = Database.open(copy)) {
      SearchRepository search = new SearchRepository(db);
      assertThat(search.search("gloom maul", Optional.empty(), 10)).extracting(h -> h.ref().id())
          .containsExactly(95001L, 95004L);
      assertThat(search.search("gloom maul", Optional.of(EntityKind.ZONE), 10)).hasSize(2);
      assertThat(search.countsByKind("gloom maul")).isEqualTo(Map.of(EntityKind.ZONE, 2));
    }
  }

  @Test
  void itemSubtitleOmitsASubclassNamedLikeItsClass() {
    assertThat(repos.search().search("unsent letter", Optional.of(ITEM), 5)).singleElement()
        .satisfies(h -> assertThat(h.subtitle()).isEqualTo("Quest · iLvl 16 · Req 16"));
  }

  @Test
  void subtitlesAreWorded() {
    assertThat(repos.search().search("Subterranean Cape", Optional.of(ITEM), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("Cloth · iLvl 18 · Req 13"));
    assertThat(repos.search().search("Taragaman", Optional.of(NPC), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).startsWith("NPC · Elite · Level"));
    assertThat(repos.search().search("hogger", Optional.of(NPC), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("NPC · Elite · Level 11 · Elwynn Forest"));
    assertThat(repos.search().search("ragefire", Optional.of(EntityKind.INSTANCE), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("Dungeon · Level 10–13 · Orgrimmar"));
    assertThat(repos.search().search("deadmines", Optional.of(EntityKind.ZONE), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("Dungeon · Westfall"));
  }

  @Test
  void bossesAreNotIndexed() {
    assertThat(repos.search().search("Taragaman", Optional.of(EntityKind.BOSS), 10)).isEmpty();
  }

  @Test
  void countsByKindAgreesWithTheKindFilteredSearch() {
    for (String text : List.of("westfall", "deadmines", "hogger", "subterranean", "molten core", "ragefire", "the")) {
      Map<EntityKind, Integer> counts = repos.search().countsByKind(text);
      for (EntityKind kind : EntityKind.values()) {
        int hits = repos.search().search(text, Optional.of(kind), 10_000).size();
        assertThat(counts.getOrDefault(kind, 0)).as("%s %s", text, kind).isEqualTo(hits);
      }
      assertThat(counts.values()).as(text).allMatch(n -> n > 0);
    }
    assertThat(repos.search().countsByKind("deadmines")).containsKeys(EntityKind.ZONE);
  }

  @Test
  void countsByKindFoldsSameNamesExceptPlaces(@TempDir Path tmp) throws Exception {
    Path copy = tmp.resolve("db.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      s.executeUpdate("insert into search_index(kind, id, name) values ('object', 94001, 'Gloomhold'), "
          + "('object', 94002, 'Gloomhold'), ('object', 94003, 'Gloomhold Chest'), ('zone', 96001, 'Gloomhold'), "
          + "('zone', 96002, 'Gloomhold'), ('zone', 96003, 'Zone #96003 Gloomhold')");
    }
    try (Database db = Database.open(copy)) {
      assertThat(new SearchRepository(db).countsByKind("gloomhold"))
          .isEqualTo(Map.of(EntityKind.OBJECT, 2, EntityKind.ZONE, 2));
    }
  }

  @Test
  void countsByKindOfBlankOrSymbolTextIsEmpty() {
    for (String q : List.of("", "   ", "\"", "*", "-", "(")) {
      assertThat(repos.search().countsByKind(q)).as(q).isEmpty();
    }
    assertThat(repos.search().countsByKind(null)).isEmpty();
  }

  @Test
  void itemHitsCarryQualityOtherHitsDoNot() {
    assertThat(repos.search().search("Eye of Sulfuras", Optional.of(ITEM), 1)).singleElement()
        .satisfies(h -> assertThat(h.quality()).isEqualTo(5));
    assertThat(repos.search().search("Smelling Salts", Optional.of(ITEM), 1)).singleElement()
        .satisfies(h -> assertThat(h.quality()).isNull());
    assertThat(repos.search().search("hogger", Optional.of(NPC), 1)).singleElement()
        .satisfies(h -> assertThat(h.quality()).isNull());
  }

  @Test
  void itemHitsCarryTheirIconOtherHitsDoNot() {
    assertThat(repos.search().search("Eye of Sulfuras", Optional.of(ITEM), 1)).singleElement()
        .satisfies(h -> assertThat(h.iconId()).isEqualTo(134124L));
    assertThat(repos.search().search("Smelling Salts", Optional.of(ITEM), 1)).singleElement()
        .satisfies(h -> assertThat(h.iconId()).isNull());
    assertThat(repos.search().search("hogger", Optional.of(NPC), 1)).singleElement()
        .satisfies(h -> assertThat(h.iconId()).isNull());
  }
}
