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
  public static Path chooseDatabase(Component parent, String error) {
    if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Setup dialog must run on the EDT");
    var selected=new AtomicReference<Path>();
    java.awt.Window owner=parent instanceof java.awt.Window window?window:parent==null?null:SwingUtilities.getWindowAncestor(parent);
    var dialog=new JDialog(owner,"Vibecraft · Build your library",Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setContentPane(new LibrarySetupPanel(Preferences.userNodeForPackage(LibrarySetupPanel.class).node("source-draft"),file->{selected.set(file);dialog.dispose();},error));
    dialog.setMinimumSize(new Dimension(420,580));
    var screen=parent==null?java.awt.Toolkit.getDefaultToolkit().getScreenSize():parent.getGraphicsConfiguration().getBounds().getSize();
    dialog.setSize(Math.min(1080,screen.width-40),Math.min(850,screen.height-60));
    dialog.setLocationRelativeTo(parent);dialog.setVisible(true);return selected.get();
  }
}
