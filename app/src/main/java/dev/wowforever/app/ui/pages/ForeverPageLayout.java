package dev.wowforever.app.ui.pages;

import dev.wowforever.app.ui.widgets.WrappingText;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.*;

/** Small shared scrolling layout for data-only discovery pages. */
abstract class ForeverPageLayout extends JPanel {
  protected final PageContent body=new PageContent();
  ForeverPageLayout(String title) {
    super(new BorderLayout());
    body.setBorder(BorderFactory.createEmptyBorder(12,12,20,12));
    JLabel heading=new JLabel(title);heading.setFont(heading.getFont().deriveFont(Font.BOLD,22));addRow(heading);
    JScrollPane scroll=new JScrollPane(body);scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getVerticalScrollBar().setUnitIncrement(16);add(scroll);
  }
  protected void addRow(JComponent row){row.setAlignmentX(Component.LEFT_ALIGNMENT);body.add(row);body.add(Box.createVerticalStrut(8));}
  protected void text(String text){addRow(new WrappingText(text,"Forever information"));}
}
