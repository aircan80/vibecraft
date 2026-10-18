package dev.wowforever.app.db;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** First-run local data selection. Validation happens on the calling background worker. */
public final class ExternalDatabaseSetup {
  static final String PATH_KEY = "database-path";
  @FunctionalInterface interface Prompt { Path choose(String error) throws IOException; }
  @FunctionalInterface interface Validator { Path validate(Path file) throws IOException; }
  private ExternalDatabaseSetup() {}

  /** Called off the EDT; only the native file chooser runs on the EDT. Cancel closes startup normally. */
  public static Path resolve(Component parent, Path dataDir) throws IOException {
    if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Database setup must run off the EDT");
    String override = System.getProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
    if ((override != null && !override.isBlank())
        || DatabaseInstaller.class.getResource("/data/wowforever.sqlite") != null) {
      return new DatabaseInstaller(dataDir).install();
    }
    return resolve(Preferences.userNodeForPackage(ExternalDatabaseSetup.class),
        error -> chooseOnEdt(parent, error), DatabaseInstaller::validateExternal);
  }

  static Path resolve(Preferences preferences, Prompt prompt, Validator validator) throws IOException {
    String saved = preferences.get(PATH_KEY, "");
    String error = null;
    if (!saved.isBlank()) {
      try { return validator.validate(Path.of(saved)); }
      catch (IOException | RuntimeException failure) { error = "The previous database could not be opened. Choose another file."; }
    }
    while (true) {
      Path selected = prompt.choose(error);
      if (selected == null) throw new CancellationException("Database setup cancelled");
      try {
        Path valid = validator.validate(selected);
        preferences.put(PATH_KEY, valid.toString());
        return valid;
      } catch (IOException failure) {
        error = failure.getMessage();
      }
    }
  }

  private static Path chooseOnEdt(Component parent, String error) throws IOException {
    FutureTask<Path> dialog = new FutureTask<>(() -> {
      JFileChooser chooser = new JFileChooser();
      Path existing = UserDataDir.resolve().resolve(DatabaseInstaller.FILE_NAME);
      if (java.nio.file.Files.isRegularFile(existing)) chooser.setSelectedFile(existing.toFile());
      chooser.setDialogTitle("Choose your local WoW Forever database");
      chooser.setApproveButtonText("Use database");
      chooser.setFileFilter(new FileNameExtensionFilter("SQLite database (*.sqlite, *.db)", "sqlite", "db"));
      JTextArea explanation = new JTextArea("This application does not include game data.\n"
          + "Choose an existing WoW Forever database on your computer.\n"
          + "Your file stays in place and is opened read-only.\n\n"
          + (error == null ? "Cancel to close the application." : "Could not use that file:\n" + error));
      explanation.setEditable(false);
      explanation.setLineWrap(true);
      explanation.setWrapStyleWord(true);
      explanation.setColumns(32);
      explanation.setRows(12);
      explanation.setOpaque(false);
      explanation.setFont(chooser.getFont().deriveFont(15f));
      explanation.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
      chooser.setAccessory(explanation);
      return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    });
    SwingUtilities.invokeLater(dialog);
    try { return dialog.get(); }
    catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IOException("Database setup interrupted", failure);
    } catch (java.util.concurrent.ExecutionException failure) {
      throw new IOException("Database chooser could not open", failure.getCause());
    }
  }
}
