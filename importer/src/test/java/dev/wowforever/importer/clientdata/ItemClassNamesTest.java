package dev.wowforever.importer.clientdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.wowforever.importer.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemClassNamesTest {

  @Test
  void readsClassAndSubclassNamesKeyedByClassIds() {
    ItemClassNames names = ItemClassNames.load(new ClientDataSource(Fixtures.clientDataRoot()));

    assertThat(names.classes()).hasSize(17);
    assertThat(names.classes()).containsEntry(2, "Weapon").containsEntry(4, "Armor").containsEntry(18, "WoW Token");
    assertThat(names.classes().keySet()).startsWith(0, 1, 2); // ordered by class id, not by row ID
    assertThat(names.subclasses().get(2)).containsEntry(7, "Sword").containsEntry(15, "Dagger");
    assertThat(names.subclasses().get(4)).containsEntry(1, "Cloth").containsEntry(6, "Shield");
    assertThat(names.subclasses().get(0)).containsEntry(-1, "NewItem");
    assertThat(names.subclasses().values().stream().mapToInt(java.util.Map::size).sum()).isEqualTo(100);
  }

  @Test
  void emptyDisplayNameFallsBackToTheVerboseName(@TempDir Path tmp) throws IOException {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    Files.writeString(csv.resolve("ItemClass.csv"), "ID,ClassName_lang,ClassID\n3,Weapon,2\n");
    Files.writeString(csv.resolve("ItemSubClass.csv"),
        "DisplayName_lang,VerboseName_lang,ID,ClassID,SubClassID\n,Long Swords,9,2,7\n,,10,2,8\n");
    ItemClassNames names = ItemClassNames.load(new ClientDataSource(tmp));
    assertThat(names.subclasses().get(2)).containsExactly(java.util.Map.entry(7, "Long Swords"));
  }

  @Test
  void aMissingColumnFailsNamingTableAndColumn(@TempDir Path tmp) throws IOException {
    Path csv = Files.createDirectories(tmp.resolve("csv"));
    Files.writeString(csv.resolve("ItemClass.csv"), "ID,Name_lang,ClassID\n");
    Files.writeString(csv.resolve("ItemSubClass.csv"), "DisplayName_lang,VerboseName_lang,ID,ClassID,SubClassID\n");
    assertThatThrownBy(() -> ItemClassNames.load(new ClientDataSource(tmp)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ItemClass")
        .hasMessageContaining("ClassName_lang");
  }
}
