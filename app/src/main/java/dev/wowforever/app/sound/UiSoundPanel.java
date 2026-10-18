package dev.wowforever.app.sound;

import java.awt.*;
import javax.swing.*;

/** Effects controls deliberately independent of city music preferences. */
public final class UiSoundPanel extends JPanel {
  public UiSoundPanel(UiSoundService service){
    super(new GridBagLayout());
    JCheckBox enabled=new JCheckBox("Interface sounds",!service.muted());
    enabled.setToolTipText("Verified WoW button, window and tab sounds; separate from city music.");
    enabled.addActionListener(e->{service.muted(!enabled.isSelected());if(enabled.isSelected())service.play(UiSoundService.Cue.TOGGLE);});
    JSlider volume=new JSlider(0,100,service.volume());volume.setPreferredSize(new Dimension(160,32));
    volume.getAccessibleContext().setAccessibleName("Interface sound volume");
    volume.addChangeListener(e->service.volume(volume.getValue()));
    JButton preview=new JButton("Preview");preview.addActionListener(e->service.play(UiSoundService.Cue.CONFIRM));
    GridBagConstraints row = new GridBagConstraints();
    row.insets=new Insets(4,4,4,4);row.anchor=GridBagConstraints.LINE_START;
    row.gridx=0;row.gridy=0;row.gridwidth=3;row.weightx=1;row.fill=GridBagConstraints.HORIZONTAL;add(enabled,row);
    row.gridy=1;row.gridwidth=1;row.weightx=0;row.gridx=0;add(new JLabel("Volume"),row);
    row.gridx=1;row.weightx=1;volume.setMinimumSize(new Dimension(60,32));add(volume,row);
    row.gridx=2;row.weightx=0;row.fill=GridBagConstraints.NONE;add(preview,row);
  }
}
