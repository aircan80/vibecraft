package dev.wowforever.app.ui;

import dev.wowforever.app.db.FixtureDb;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.journal.*;
import dev.wowforever.app.ui.widgets.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import java.util.prefs.Preferences;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FixtureDb.class)
class PinnedResearchControllerTest {
  @Test void referenceOpensThroughNormalHistoryAndMissingEntityIsRecoverable() throws Exception {
    Preferences prefs=Preferences.userRoot().node("wowforever-test-controller-pins/"+UUID.randomUUID());
    try {
      var pins=new PinnedResearch(new UiState(prefs),"forever:test");
      var missing=new ViewTarget.Entity(new EntityRef(EntityKind.NPC,999999999));pins.toggle(missing,"Previously visited NPC");
      var view=new MainView(){
        final MasterList list=new MasterList(new ManualDelay());final StatusBar status=new StatusBar();
        @Override public UiExperience uiExperience(){return UiExperience.JOURNAL;}
        @Override public MasterList masterList(){return list;}
        @Override public StatusBar status(){return status;}
        @Override public void setDetail(JComponent c){}
        @Override public void setHistoryState(boolean b,boolean f){}
        @Override public void setTitleSuffix(String s){}
        @Override public void selectRail(RailTarget t){}
        @Override public JComponent pinnedResearchPage(java.util.function.Consumer<ViewTarget> open){return new PinnedResearchPage(pins,open);}
      };
      var controller=new AppController(FixtureDb.repositories(),view,Runnable::run);
      var references=new ViewTarget.Forever("pinned");
      controller.start();controller.open(references);
      var page=(PinnedResearchPage)view.pinnedResearchPage(controller::open);
      var scroll=(JScrollPane)Arrays.stream(page.getComponents()).filter(c->c instanceof JScrollPane).findFirst().orElseThrow();
      ((JList<?>)scroll.getViewport().getView()).getActionMap().get("open-reference").actionPerformed(null);
      assertEquals(missing,controller.current());controller.back();assertEquals(references,controller.current());
      controller.back();assertEquals(new ViewTarget.Home(),controller.current());controller.forward();assertEquals(references,controller.current());
      assertEquals(1,pins.references().size());
    } finally {prefs.removeNode();}
  }
}
