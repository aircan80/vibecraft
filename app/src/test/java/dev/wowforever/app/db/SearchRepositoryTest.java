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
  void itemSubtitleOmitsASubclassNamedLikeItsClass() {
    assertThat(repos.search().search("unsent letter", Optional.of(ITEM), 5)).singleElement()
        .satisfies(h -> assertThat(h.subtitle()).isEqualTo("Item · Quest · iLvl 16"));
  }

  @Test
  void subtitleNamesKind() {
    assertThat(repos.search().search("Subterranean Cape", Optional.of(ITEM), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).startsWith("Item"));
    assertThat(repos.search().search("Taragaman", Optional.of(NPC), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).startsWith("NPC · Level"));
    assertThat(repos.search().search("hogger", Optional.of(NPC), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("NPC · Level 11 · Elwynn Forest"));
    assertThat(repos.search().search("ragefire", Optional.of(EntityKind.INSTANCE), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("Instance · DUNGEON 10–13"));
    assertThat(repos.search().search("deadmines", Optional.of(EntityKind.ZONE), 1))
        .singleElement().satisfies(h -> assertThat(h.subtitle()).isEqualTo("Zone · dungeon"));
  }

  @Test
  void bossesAreNotIndexed() {
    assertThat(repos.search().search("Taragaman", Optional.of(EntityKind.BOSS), 10)).isEmpty();
  }
}
