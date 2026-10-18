package dev.wowforever.app.sound;

import java.awt.*;
import javax.swing.*;

/** Effects controls deliberately independent of city music preferences. */
public final class UiSoundPanel extends JPanel {
  public UiSoundPanel(UiSoundService service){
    super(new FlowLayout(FlowLayout.LEADING,10,8));
    JCheckBox enabled=new JCheckBox("Interface sounds",!service.muted());
    enabled.setToolTipText("Verified WoW button, window and tab sounds; separate from city music.");
    enabled.addActionListener(e->{service.muted(!enabled.isSelected());if(enabled.isSelected())service.play(UiSoundService.Cue.TOGGLE);});
    JSlider volume=new JSlider(0,100,service.volume());volume.setPreferredSize(new Dimension(160,32));
    volume.getAccessibleContext().setAccessibleName("Interface sound volume");
    volume.addChangeListener(e->service.volume(volume.getValue()));
    JButton preview=new JButton("Preview");preview.addActionListener(e->service.play(UiSoundService.Cue.CONFIRM));
    add(enabled);add(new JLabel("Volume"));add(volume);add(preview);
  }
}
