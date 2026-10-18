package dev.wowforever.app.ui.journal;

import dev.wowforever.app.ui.UiState;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.model.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class PinnedResearchTest {
  Preferences prefs;
  UiState state;
  @BeforeEach void setup(){prefs=Preferences.userRoot().node("wowforever-test-pins/"+UUID.randomUUID());state=new UiState(prefs);}
  @AfterEach void cleanup() throws Exception {prefs.removeNode();}
  @Test void persistsExactTargetsAndUnicodeLabelsAcrossStoreRestart(){
    PinnedResearch pins=new PinnedResearch(state,"Forever:70450:enUS");
    List<ViewTarget> targets=List.of(new ViewTarget.Entity(new EntityRef(EntityKind.NPC,2671)),new ViewTarget.WorldMap(947,1415L),new ViewTarget.ItemContent("sets",2133),new ViewTarget.Forever("cities"),new ViewTarget.List(EntityKind.INSTANCE));
    for(var target:targets)assertTrue(pins.toggle(target,"Stormwind · 城市 "+target));
    var restored=new PinnedResearch(new UiState(prefs),"Forever:70450:enUS").references();
    assertEquals(targets,restored.stream().map(PinnedResearch.Reference::target).toList());
    assertTrue(restored.getFirst().label().contains("城市"));
  }
  @Test void duplicateTargetTogglesRegardlessOfLabelAndForeignScopeIsInvisible(){
    var target=new ViewTarget.ItemContent("sets",2133);
    var first=new PinnedResearch(state,"forever:70450");
    var other=new PinnedResearch(state,"classic:70450");
    first.toggle(target,"First label");other.toggle(target,"Classic reference");
    assertEquals(1,first.references().size());assertEquals(1,other.references().size());
    assertFalse(first.toggle(target,"Renamed label"));assertTrue(first.references().isEmpty());assertEquals(1,other.references().size());
  }
  @Test void boundedReferencesIgnoreCorruptAndOldUnscopedKeys(){
    state.setPinned(List.of("item:19019","research-v1:broken:data"));
    var pins=new PinnedResearch(state,"forever:70450");assertTrue(pins.references().isEmpty());
    for(int i=1;i<150;i++)pins.toggle(new ViewTarget.WorldMap(i),"Map #"+i);
    assertTrue(pins.references().size()<=UiState.MAX_PINNED);
    assertEquals(new ViewTarget.WorldMap(149),pins.references().getLast().target());
  }
  @Test void unsupportedTransientSearchAndFilteredListsDoNotBecomeMisleadingPins(){
    assertTrue(PinnedResearch.key(new ViewTarget.Search("food",Optional.empty())).isEmpty());
    assertTrue(PinnedResearch.key(ViewTarget.List.itemClass(2,0)).isEmpty());
    assertTrue(PinnedResearch.key(new ViewTarget.Forever("pinned")).isEmpty());
    assertThrows(IllegalArgumentException.class,()->new PinnedResearch(state,""));
  }
  @Test void keyboardOpenUsesExactTargetAndRemoveDoesNotNavigate() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var pins=new PinnedResearch(state,"forever:70450");var target=new ViewTarget.WorldMap(1415,1519L);pins.toggle(target,"Eastern Kingdoms");
      List<ViewTarget> opened=new ArrayList<>();var page=new PinnedResearchPage(pins,opened::add);
      var scroll=(JScrollPane)Arrays.stream(page.getComponents()).filter(c->c instanceof JScrollPane).findFirst().orElseThrow();
      var list=(JList<?>)scroll.getViewport().getView();
      list.getActionMap().get("open-reference").actionPerformed(null);assertEquals(List.of(target),opened);
      list.getActionMap().get("remove-reference").actionPerformed(null);assertTrue(pins.references().isEmpty());assertEquals(1,opened.size());
    });
  }
}
