package dev.wowforever.app.ui.setup;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

/** The main application's first-run workspace. No database connection is required. */
public final class LibrarySetupDialog {
  private LibrarySetupDialog() {}
  /** Called on the EDT. Closing setup cancels startup; a selected file is validated by the caller off the EDT. */
  public static Path chooseDatabase(Component parent, String error) {return chooseDatabase(parent,error,null);}
  public static Path chooseDatabase(Component parent,String error,LibrarySetupService service) {
    if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Setup dialog must run on the EDT");
    var selected=new AtomicReference<Path>();
    java.awt.Window owner=parent instanceof java.awt.Window window?window:parent==null?null:SwingUtilities.getWindowAncestor(parent);
    var window=new AtomicReference<JDialog>();
    var panel=new LibrarySetupPanel(preferences(),file->{selected.set(file);window.get().dispose();},error,service);
    var dialog=dialog(owner,parent,"Vibecraft · Build your library",panel);window.set(dialog);
    dialog.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){panel.close();}});
    dialog.setLocationRelativeTo(parent);dialog.setVisible(true);return selected.get();
  }
  /** Opens source preferences for the current browser. It cannot replace the active database. */
  public static void manageSources(Component parent){manageSources(parent,null);}
  public static void manageSources(Component parent,LibrarySetupService service){
    if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Source management must run on the EDT");
    java.awt.Window owner=parent instanceof java.awt.Window window?window:parent==null?null:SwingUtilities.getWindowAncestor(parent);
    var panel=LibrarySetupPanel.sourceManager(preferences(),service);
    var dialog=dialog(owner,parent,"Vibecraft · Sources & updates",panel);
    dialog.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){panel.close();}});
    dialog.setLocationRelativeTo(parent);dialog.setVisible(true);
  }
  private static Preferences preferences(){return Preferences.userNodeForPackage(LibrarySetupPanel.class).node("source-draft");}
  private static JDialog dialog(java.awt.Window owner,Component parent,String title,LibrarySetupPanel panel){
    var dialog=new JDialog(owner,title,Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);dialog.setContentPane(panel);dialog.setMinimumSize(new Dimension(420,580));
    var screen=parent==null?java.awt.Toolkit.getDefaultToolkit().getScreenSize():parent.getGraphicsConfiguration().getBounds().getSize();
    dialog.setSize(Math.min(1080,screen.width-40),Math.min(850,screen.height-60));return dialog;
  }

}
