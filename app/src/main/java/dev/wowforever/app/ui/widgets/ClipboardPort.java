package dev.wowforever.app.ui.widgets;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Where a page copies text to: the system clipboard in the app, a recording port in tests. */
@FunctionalInterface
public interface ClipboardPort {
  void copy(String text);

  /** The system clipboard, looked up on each copy (so building a page needs no clipboard). */
  static ClipboardPort system() {
    return text -> {
      StringSelection selection = new StringSelection(text);
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    };
  }
}
