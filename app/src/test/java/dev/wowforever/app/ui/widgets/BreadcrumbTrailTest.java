package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.ViewTarget;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class BreadcrumbTrailTest {
  @Test void parentsAreKeyboardLinksAndTheCurrentAreaIsPlainText() throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      var opened=new ArrayList<ViewTarget>();
      var trail=new BreadcrumbTrail(List.of(new GeographyStep("Azeroth",947,GeographyStep.Kind.MAP),
          new GeographyStep("Eastern Kingdoms",1415,GeographyStep.Kind.MAP),new GeographyStep("Westfall",40,GeographyStep.Kind.ZONE),
          new GeographyStep("The Deadmines",1581,GeographyStep.Kind.ZONE)),opened::add);
      var links=links(trail);assertThat(links).extracting(JButton::getText).containsExactly("Azeroth","Eastern Kingdoms","Westfall");
      links.get(1).doClick();links.get(2).doClick();
      assertThat(opened).containsExactly(new ViewTarget.WorldMap(1415),new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,40)));
      Object action=links.getFirst().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
      links.getFirst().getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(trail,0,""));
      assertThat(opened.getLast()).isEqualTo(new ViewTarget.WorldMap(947));
      assertThat(links).allSatisfy(b -> assertThat(b.isFocusable()).isTrue());
    });
  }
  @Test void deepTrailsWrapWithinCompactWidth() throws Exception {
    SwingUtilities.invokeAndWait(()-> {
      var trail=new BreadcrumbTrail(List.of(new GeographyStep("Azeroth",947,GeographyStep.Kind.MAP),
          new GeographyStep("Eastern Kingdoms",1415,GeographyStep.Kind.MAP),new GeographyStep("Elwynn Forest",12,GeographyStep.Kind.ZONE),
          new GeographyStep("Northshire",6170,GeographyStep.Kind.ZONE)),ignored->{});
      trail.setSize(200,500);int height=trail.getPreferredSize().height;trail.setSize(200,height);trail.doLayout();
      assertThat(height).isGreaterThan(trail.getComponent(0).getHeight());
      for(Component c:trail.getComponents()) {
        assertThat(c.getX()).isGreaterThanOrEqualTo(0);assertThat(c.getX()+c.getWidth()).isLessThanOrEqualTo(200);
        assertThat(c.getY()+c.getHeight()).isLessThanOrEqualTo(height);
      }
    });
  }
  @Test void siblingArrowNavigatesIndependentlyAndPreservesCurrentUnavailableAndKeyboardStates() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var opened = new ArrayList<ViewTarget>();
      var choices = List.of(new GeographyStep.Sibling("Desolace", 405, GeographyStep.Kind.ZONE, true, ""),
          new GeographyStep.Sibling("Mulgore", 215, GeographyStep.Kind.ZONE, true, ""),
          new GeographyStep.Sibling("Unrecorded destination", 999, GeographyStep.Kind.ZONE, false, "Not in this database"));
      var step = new GeographyStep("Desolace", 405, GeographyStep.Kind.ZONE, choices);
      var trail = new BreadcrumbTrail(List.of(new GeographyStep("Kalimdor", 1414, GeographyStep.Kind.MAP), step), opened::add);
      var buttons = links(trail);
      assertThat(buttons).extracting(JButton::getText).containsExactly("Kalimdor", "▾");
      JButton arrow = buttons.getLast();
      assertThat(arrow.getAccessibleContext().getAccessibleName()).contains("Desolace");
      var menu = (JPopupMenu) arrow.getClientProperty("breadcrumbMenu");
      assertThat(((JRadioButtonMenuItem) menu.getComponent(0)).isSelected()).isTrue();
      assertThat(menu.getComponent(2).isEnabled()).isFalse();
      for (String key : List.of("ENTER", "DOWN", "alt DOWN"))
        assertThat(arrow.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key))).isEqualTo("choose-sibling");
      assertThat(menu.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ESCAPE")))
          .isEqualTo("close-siblings");
      ((JMenuItem) menu.getComponent(1)).doClick(); buttons.getFirst().doClick();
      assertThat(opened).containsExactly(new ViewTarget.Entity(new EntityRef(EntityKind.ZONE,215)), new ViewTarget.WorldMap(1414));
      for (int width : new int[]{200, 420}) {
        trail.setSize(width, trail.getPreferredSize().height); trail.doLayout();
        for (Component part : trail.getComponents()) {
          assertThat(part.getX() + part.getWidth()).isLessThanOrEqualTo(width);
          assertThat(part.getY() + part.getHeight()).isLessThanOrEqualTo(trail.getPreferredSize().height);
        }
      }
    });
  }

  private List<JButton> links(Container c) {
    var result=new ArrayList<JButton>();
    for(Component child:c.getComponents()) {if(child instanceof JButton b)result.add(b);if(child instanceof Container p)result.addAll(links(p));}
    return result;
  }
}
