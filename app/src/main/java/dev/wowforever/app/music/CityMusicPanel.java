package dev.wowforever.app.music;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Compact city soundtrack controls; playback/download never happens in this panel. */
public final class CityMusicPanel extends JPanel {
  private final CityMusicService service;
  private final JToggleButton mute=new JToggleButton("Mute music");
  private final JSlider volume=new JSlider(0,100);
  private final JTextArea status=new JTextArea();
  private final Consumer<CityMusicService.State> listener=this::refresh;
  private boolean updating;
  public CityMusicPanel(CityMusicService service) {
    super(new BorderLayout(8,6));this.service=service;setOpaque(false);
    JPanel buttons=new JPanel(new BorderLayout(4,5));buttons.setOpaque(false);
    JPanel volumeRow=new JPanel(new BorderLayout(6,0));volumeRow.setOpaque(false);
    mute.getAccessibleContext().setAccessibleName("Mute city music");mute.addActionListener(e->{if(!updating)service.muted(mute.isSelected());});
    volume.setPreferredSize(new Dimension(100,28));volume.getAccessibleContext().setAccessibleName("City music volume");
    volume.addChangeListener(e->{if(!updating)service.volume(volume.getValue());});
    buttons.add(mute,BorderLayout.NORTH);volumeRow.add(new JLabel("Volume"),BorderLayout.WEST);volumeRow.add(volume,BorderLayout.CENTER);buttons.add(volumeRow,BorderLayout.CENTER);add(buttons,BorderLayout.NORTH);
    status.setEditable(false);status.setOpaque(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setRows(2);status.setFont(UIManager.getFont("Label.font"));add(status,BorderLayout.CENTER);
    setMinimumSize(new Dimension(0,0));setPreferredSize(new Dimension(180,135));
    setBorder(BorderFactory.createTitledBorder("City soundtrack"));refresh(service.state());
  }
  private void refresh(CityMusicService.State state) {
    updating=true;mute.setSelected(state.muted());volume.setValue(state.volume());status.setText(state.status());
    status.setToolTipText("Native Forever city music · downloaded and cached on demand · stops when you leave the city");updating=false;
  }
  @Override public void addNotify(){super.addNotify();service.addListener(listener);refresh(service.state());}
  @Override public void removeNotify(){service.removeListener(listener);super.removeNotify();}
}
