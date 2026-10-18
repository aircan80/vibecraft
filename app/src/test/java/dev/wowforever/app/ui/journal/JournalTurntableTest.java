package dev.wowforever.app.ui.journal;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.NpcTurntables;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class JournalTurntableTest {
  @Test void arrowsSliderKeyboardAndDragSelectActualFramesAndWrap()throws Exception {
    SwingUtilities.invokeAndWait(()->{
      List<BufferedImage> images=new ArrayList<>();for(int i=0;i<12;i++)images.add(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB));
      var viewer=new JournalTurntable(new NpcTurntables.Turntable(images,NpcTurntables.BUILD,"test"));
      assertThat(viewer.frameIndex()).isEqualTo(10);
      click(viewer,"Rotate right");assertThat(viewer.frameIndex()).isEqualTo(11);
      click(viewer,"Rotate right");assertThat(viewer.frameIndex()).isZero();
      click(viewer,"Rotate left");assertThat(viewer.frameIndex()).isEqualTo(11);
      var slider=(JSlider)all(viewer).stream().filter(JSlider.class::isInstance).findFirst().orElseThrow();slider.setValue(4);assertThat(viewer.frameIndex()).isEqualTo(4);
      var canvas=(JComponent)all(viewer).stream().filter(c->"npc-turntable-canvas".equals(c.getName())).findFirst().orElseThrow();
      assertThat(canvas.isFocusable()).isTrue();assertThat(canvas.getAccessibleContext().getAccessibleName()).contains("12-view bind-pose");
      canvas.getActionMap().get("turn-right").actionPerformed(new ActionEvent(canvas,0,"test"));assertThat(viewer.frameIndex()).isEqualTo(5);
      canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,10,10,1,false));
      canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,2,0,66,10,1,false));assertThat(viewer.frameIndex()).isEqualTo(7);
      click(viewer,"Reset view");assertThat(viewer.frameIndex()).isEqualTo(10);
      viewer.pause();assertThat(((JToggleButton)all(viewer).stream().filter(c->c instanceof JToggleButton b&&b.getText().equals("Play rotation")).findFirst().orElseThrow()).isSelected()).isFalse();
      viewer.setSize(264,420);for(int i=0;i<4;i++)layout(viewer);
      for(Component c:all(viewer))if(c instanceof JButton b)assertThat(b.getWidth()).isGreaterThan(80);
    });
  }
  private static void click(Container c,String title){((JButton)all(c).stream().filter(x->x instanceof JButton b&&title.equals(b.getText())).findFirst().orElseThrow()).doClick();}
  private static List<Component> all(Container c){var result=new ArrayList<Component>();for(var child:c.getComponents()){result.add(child);if(child instanceof Container nested)result.addAll(all(nested));}return result;}
  private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}
