package dev.wowforever.app.ui.pages;

import dev.wowforever.app.db.ItemContentRepository;
import dev.wowforever.app.nav.Navigator;
import java.awt.BorderLayout;
import javax.swing.*;

/** Client appearance identity, not a claim about transmog eligibility. */
public final class AppearancePanel extends JPanel {
  public AppearancePanel(ItemContentRepository.Appearance data,Navigator nav) {
    super(new BorderLayout(0,6));setBorder(BorderFactory.createTitledBorder("Appearance"));
    JTextArea note=new JTextArea(data.note()+(data.build().isEmpty()?"":" · Client "+data.build()));
    note.setEditable(false);note.setOpaque(false);note.setLineWrap(true);note.setWrapStyleWord(true);
    add(note,BorderLayout.NORTH);
    if(data.available())add(ItemContentPage.rows(data.members(),nav,(s,id)->{}),BorderLayout.CENTER);
    getAccessibleContext().setAccessibleName("Appearance: "+data.note());
  }
}
