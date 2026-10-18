package dev.wowforever.app.db;

import java.awt.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;

/** First-run local data selection. Validation happens on the calling background worker. */
public final class ExternalDatabaseSetup {
  static final String PATH_KEY = "database-path";
  @FunctionalInterface interface Prompt { Path choose(String error) throws IOException; }
  @FunctionalInterface interface Validator { Path validate(Path file) throws IOException; }
  private ExternalDatabaseSetup() {}

  /** Called off the EDT; only the setup workspace runs on the EDT. Cancel closes startup normally. */
  public static Path resolve(Component parent, Path dataDir) throws IOException {
    if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Database setup must run off the EDT");
    String override = System.getProperty(DatabaseInstaller.EXTERNAL_DATABASE_PROPERTY);
    if (override != null && !override.isBlank()) return new DatabaseInstaller(dataDir).install();
    return resolveWithManagedLibrary(dataDir,Preferences.userNodeForPackage(ExternalDatabaseSetup.class),
        error -> chooseOnEdt(parent, error), DatabaseInstaller::validateExternal,
        DatabaseInstaller.class.getResource("/data/wowforever.sqlite") != null);
  }

  /** Explicit overrides are handled first by resolve; managed activation applies only to a later startup. */
  static Path resolveWithManagedLibrary(Path dataDir,Preferences preferences,Prompt prompt,
      Validator validator,boolean embeddedAvailable) throws IOException {
    LibraryRegistry.offEdt();
    String managedFailure=null;
    try {
      var managed=new LibraryRegistry(dataDir,java.util.List.of(),java.util.Optional.empty()).resolve(()->false);
      if(managed.isPresent())return managed.orElseThrow().database();
    } catch(IOException failure) {
      managedFailure="The managed library could not be validated. Choose an existing database or rebuild the library.";
    }
    if(embeddedAvailable)return new DatabaseInstaller(dataDir).install();
    String message=managedFailure;
    return resolve(preferences,error -> prompt.choose(error==null?message:error),validator);
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
    FutureTask<Path> dialog = new FutureTask<>(() ->
        dev.wowforever.app.ui.setup.LibrarySetupDialog.chooseDatabase(parent, error));
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
