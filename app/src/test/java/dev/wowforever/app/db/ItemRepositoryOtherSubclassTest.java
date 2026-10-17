package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.ItemClassNode;
import dev.wowforever.app.model.ItemSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Items whose subclass is missing from the static subclass table sit under an "(other)" node of their class. */
@ExtendWith(FixtureDb.class)
class ItemRepositoryOtherSubclassTest {

  @Test
  void unknownSubclassesGetAnOtherNodeAndList(@TempDir Path dir) throws Exception {
    Path copy = dir.resolve("copy.sqlite");
    Files.copy(FixtureDb.path(), copy);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy); Statement s = c.createStatement()) {
      // Weapon (2) has no subclass 99 in the subclass table (the client table ends at 20, Fishing Pole)
      s.executeUpdate("INSERT INTO item (id, name, class_id, subclass_id) VALUES (990001, 'Odd Blade', 2, 99)");
      s.executeUpdate("INSERT INTO item (id, name, class_id, subclass_id) VALUES (990002, 'Plain Blade', 2, NULL)");
    }
    try (Database db = Database.open(copy)) {
      ItemRepository items = new ItemRepository(db);
      ItemClassNode weapon = items.classTree().stream().filter(n -> n.classId() == 2).findFirst().orElseThrow();
      assertThat(weapon.children()).last()
          .isEqualTo(new ItemClassNode(2, "(other)", List.of(), ItemClassNode.OTHER_SUBCLASSES));
      assertThat(weapon.children().getLast().isOther()).isTrue();

      List<ItemSummary> other = items.list(2, ItemClassNode.OTHER_SUBCLASSES, 500, 0);
      assertThat(other).extracting(ItemSummary::id).contains(990001L, 990002L);
      assertThat(other).allMatch(i -> i.subclassName() == null && "Weapon".equals(i.className()));
      assertThat(items.list(2, null, 500, 0)).extracting(ItemSummary::id).contains(990001L, 990002L);
      assertThat(items.list(2, 7, 500, 0)).extracting(ItemSummary::id).doesNotContain(990001L, 990002L);
    }
  }

  @Test
  void otherNodeExactlyWhenTheClassHasUnknownSubclasses() {
    ItemRepository items = FixtureDb.repositories().items();
    for (ItemClassNode cls : items.classTree()) {
      boolean hasOther = cls.children().stream().anyMatch(ItemClassNode::isOther);
      assertThat(hasOther).as("class %s", cls.name())
          .isEqualTo(!items.list(cls.classId(), ItemClassNode.OTHER_SUBCLASSES, 1, 0).isEmpty());
      assertThat(cls.children().stream().filter(ItemClassNode::isOther).count()).isLessThanOrEqualTo(1);
    }
  }
}
