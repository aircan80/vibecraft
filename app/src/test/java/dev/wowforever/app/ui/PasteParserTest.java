package dev.wowforever.app.ui;

import static dev.wowforever.app.model.EntityKind.ITEM;
import static dev.wowforever.app.model.EntityKind.NPC;
import static dev.wowforever.app.model.EntityKind.OBJECT;
import static dev.wowforever.app.model.EntityKind.QUEST;
import static dev.wowforever.app.model.EntityKind.ZONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasteParserTest {

  private static Optional<ViewTarget> entity(EntityKind kind, long id) {
    return Optional.of(new ViewTarget.Entity(new EntityRef(kind, id)));
  }

  private static Optional<ViewTarget> search(String text) {
    return Optional.of(new ViewTarget.Search(text, Optional.empty()));
  }

  @Test
  void inGameItemLinkWithColourCodeOpensTheItem() {
    assertThat(PasteParser.parse(
        "|cffa335ee|Hitem:19019:0:0:0:0:0:0:0:60|h[Thunderfury, Blessed Blade of the Windseeker]|h|r"))
        .isEqualTo(entity(ITEM, 19019));
  }

  @Test
  void bracketedNameSearchesForItKeepingTheApostrophe() {
    assertThat(PasteParser.parse("[Captain Sanders' Hidden Treasure]"))
        .isEqualTo(search("Captain Sanders' Hidden Treasure"));
    assertThat(PasteParser.parse("  [ Scarlet Belt ]\n")).isEqualTo(search("Scarlet Belt"));
  }

  @Test
  void questLinkOpensTheQuest() {
    assertThat(PasteParser.parse("|cffffff00|Hquest:2018:20|h[Rites of the Earthmother]|h|r"))
        .isEqualTo(entity(QUEST, 2018));
  }

  @Test
  void linkOfAnotherTypeSearchesForItsName() {
    assertThat(PasteParser.parse("|cff71d5ff|Hspell:2061|h[Flash Heal]|h|r")).isEqualTo(search("Flash Heal"));
  }

  @Test
  void itemAndNpcStrings() {
    assertThat(PasteParser.parse("item:10329")).isEqualTo(entity(ITEM, 10329));
    assertThat(PasteParser.parse("item:19019:0:0:0:0:0:0:0:60")).isEqualTo(entity(ITEM, 19019));
    assertThat(PasteParser.parse("NPC:448")).isEqualTo(entity(NPC, 448));
    assertThat(PasteParser.parse(" quest:2018 ")).isEqualTo(entity(QUEST, 2018));
  }

  @Test
  void wowheadUrls() {
    assertThat(PasteParser.parse(
        "https://www.wowhead.com/classic/item=19019/thunderfury-blessed-blade-of-the-windseeker"))
        .isEqualTo(entity(ITEM, 19019));
    assertThat(PasteParser.parse("https://classic.wowhead.com/item=10329")).isEqualTo(entity(ITEM, 10329));
    assertThat(PasteParser.parse("wowhead.com/classic/npc=448/hogger")).isEqualTo(entity(NPC, 448));
    assertThat(PasteParser.parse("https://www.wowhead.com/classic/quest=2018")).isEqualTo(entity(QUEST, 2018));
    assertThat(PasteParser.parse("https://www.wowhead.com/classic/zone=40/westfall")).isEqualTo(entity(ZONE, 40));
    assertThat(PasteParser.parse("https://www.wowhead.com/classic/object=1617/silverleaf"))
        .isEqualTo(entity(OBJECT, 1617));
    assertThat(PasteParser.parse("https://www.wowhead.com/classic?item=10329")).isEqualTo(entity(ITEM, 10329));
  }

  @Test
  void bareNumbersAreItemIds() {
    assertThat(PasteParser.parse("19019")).isEqualTo(entity(ITEM, 19019));
    assertThat(PasteParser.parse(" #10329 ")).isEqualTo(entity(ITEM, 10329));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "hogger", "Thunderfury, Blessed Blade", "0", "-5", "12.5", "99999999999999999999",
      "[]", "[ ]", "item:", "item:abc", "https://example.com/item=19019", "https://www.wowhead.com/classic",
      "|Hitem:|h[]|h", "spell:2061", "[Scarlet Belt] and more"})
  void garbageGivesNothing(String text) {
    assertThat(PasteParser.parse(text)).isEmpty();
  }
}
