package dev.wowforever.app.ui.widgets;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.*;
import java.awt.event.*;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.*;

/** Quiet source disclosure with a short pointer/focus summary and persistent keyboard-accessible details. */
public final class SourceInfoButton extends JButton {
  private final String scope;
  private final String summary;
  private final Supplier<? extends JComponent> details;
  private Popup focusSummary;
  private JDialog dialog;

  public SourceInfoButton(String scope,String summary,Supplier<? extends JComponent> details){
    super("ⓘ");this.scope=Objects.requireNonNull(scope);this.summary=Objects.requireNonNull(summary);this.details=Objects.requireNonNull(details);
    setName("source-info-button");setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));setMargin(new Insets(0,0,0,0));
    setPreferredSize(new Dimension(28,28));setMinimumSize(new Dimension(28,28));setMaximumSize(new Dimension(28,28));
    putClientProperty("JButton.buttonType","roundRect");setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    getAccessibleContext().setAccessibleName("Sources for "+scope);
    getAccessibleContext().setAccessibleDescription(summary+" Open for retained source values and technical evidence.");
    setToolTipText("<html><div style='width:270px;font-size:18px'>"+escape(summary)+"<br>Open for details.</div></html>");
    addFocusListener(new FocusAdapter(){
      @Override public void focusGained(FocusEvent event){showFocusSummary();}
      @Override public void focusLost(FocusEvent event){hideFocusSummary();}
    });
    addHierarchyListener(event->{if(!isShowing()){hideFocusSummary();if(dialog!=null)dialog.dispose();}});
    addActionListener(event->openDetails());
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"open-sources");
    getActionMap().put("open-sources",new AbstractAction(){@Override public void actionPerformed(ActionEvent event){doClick();}});
    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"hide-source-summary");
    getActionMap().put("hide-source-summary",new AbstractAction(){@Override public void actionPerformed(ActionEvent event){hideFocusSummary();}});
  }
  /** Builds retained evidence for a source surface; callers use this on the EDT. No I/O is performed. */
  public JComponent detailsContent(){return details.get();}
  private void showFocusSummary(){
    if(!isShowing()||GraphicsEnvironment.isHeadless()||dialog!=null)return;
    hideFocusSummary();var text=new JLabel(getToolTipText());text.setOpaque(true);text.setBackground(Theme.current().bgSurface());text.setForeground(Theme.current().textPrimary());
    text.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.current().hairline()),BorderFactory.createEmptyBorder(8,10,8,10)));
    Point point=getLocationOnScreen();Rectangle screen=getGraphicsConfiguration().getBounds();Dimension size=text.getPreferredSize();
    int x=Math.max(screen.x,Math.min(point.x,screen.x+screen.width-size.width));int y=point.y+getHeight()+4;
    if(y+size.height>screen.y+screen.height)y=Math.max(screen.y,point.y-size.height-4);
    focusSummary=PopupFactory.getSharedInstance().getPopup(this,text,x,y);focusSummary.show();
  }
  private void hideFocusSummary(){if(focusSummary!=null){focusSummary.hide();focusSummary=null;}}
  private void openDetails(){
    hideFocusSummary();if(dialog!=null){dialog.toFront();return;}
    Window owner=SwingUtilities.getWindowAncestor(this);
    dialog=new JDialog(owner,"Sources · "+scope,Dialog.ModalityType.DOCUMENT_MODAL);dialog.setName("source-details-dialog");
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    var content=new JPanel(new BorderLayout(12,12));content.setBackground(Theme.current().bgSurface());content.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
    var heading=new WrappingText(scope,"Source evidence scope");heading.setFont(new Font(Font.SANS_SERIF,Font.BOLD,20));content.add(heading,BorderLayout.NORTH);
    var scroll=new JScrollPane(detailsContent(),ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);scroll.getViewport().setBackground(Theme.current().bgSurface());scroll.getVerticalScrollBar().setUnitIncrement(24);content.add(scroll);
    var close=new JButton("Close");close.setName("source-details-close");close.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));
    close.addActionListener(event->dialog.dispose());content.add(close,BorderLayout.SOUTH);dialog.setContentPane(content);
    dialog.getRootPane().registerKeyboardAction(event->dialog.dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),WHEN_IN_FOCUSED_WINDOW);
    dialog.addWindowListener(new WindowAdapter(){
      @Override public void windowOpened(WindowEvent event){close.requestFocusInWindow();}
      @Override public void windowClosed(WindowEvent event){dialog=null;SwingUtilities.invokeLater(()->{if(isShowing())requestFocusInWindow();});}
    });
    Rectangle screen=getGraphicsConfiguration()==null?new Rectangle(0,0,800,700):getGraphicsConfiguration().getBounds();
    int ownerWidth=owner==null?screen.width:owner.getWidth();int ownerHeight=owner==null?screen.height:owner.getHeight();
    dialog.setSize(Math.max(280,Math.min(720,Math.min(ownerWidth-20,screen.width-40))),Math.max(300,Math.min(750,Math.min(ownerHeight-40,screen.height-60))));
    dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
  }
  private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
  @Override public void removeNotify(){hideFocusSummary();if(dialog!=null)dialog.dispose();super.removeNotify();}
}
