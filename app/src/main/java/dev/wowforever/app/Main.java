package dev.wowforever.app;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.Sql;
import dev.wowforever.app.db.UserDataDir;
import dev.wowforever.app.ui.AppController;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.MainFrame;
import dev.wowforever.app.ui.ExperienceChooser;
import dev.wowforever.app.ui.UiExperience;
import dev.wowforever.app.ui.ThemeManager;
import dev.wowforever.app.ui.theme.Fonts;
import dev.wowforever.app.ui.widgets.IconCache;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the browser. {@code --self-test} installs and opens the database, prints its row counts and exits
 * (a packaging smoke test); without arguments it shows the main window.
 */
public final class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private Main() {}

  /** The open database and everything the window needs from it, loaded off the EDT. */
  private record Loaded(Database db, Repositories repos, Map<String, String> meta) {}

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("--self-test")) {
      System.exit(runSelfTest());
    }
    UiExperience override;
    try {
      override = startupExperience(args);
    } catch (IllegalArgumentException e) {
      System.err.println("Usage: java -jar wow-forever-browser.jar [--self-test | --ui=classic | --ui=journal]");
      System.exit(2);
      return;
    }
    SwingUtilities.invokeLater(() -> startGui(override));
  }

  /** Prints {@code items=<n> npcs=<n> instances=<n> build_id=<id>} for the database at {@code db}; returns 0. */
  public static int selfTest(Path db) throws SQLException {
    try (Database d = Database.open(db)) {
      System.out.println("items=" + count(d, "item") + " npcs=" + count(d, "npc") + " instances="
          + count(d, "instance") + " build_id=" + d.meta().get("build_id"));
    }
    return 0;
  }

  private static int runSelfTest() {
    try {
      return selfTest(new DatabaseInstaller(UserDataDir.resolve()).install());
    } catch (IOException | SQLException | RuntimeException e) {
      System.err.println("Self-test failed: " + e);
      return 1;
    }
  }

  private static long count(Database d, String table) {
    return d.read(c -> Sql.one(c, "SELECT count(*) AS n FROM " + table, rs -> rs.getLong("n")).orElseThrow());
  }

  /**
   * On the EDT: shows the window, then opens the database in the background and wires the controller. A failure
   * of either step is logged and shown, and the application exits with status 1.
   */
  /** Explicit command-line choices support repeatable previews; normal startup always asks. */
  static UiExperience startupExperience(String[] args) {
    if (args.length == 0) {
      return null;
    }
    if (args.length == 1) {
      return switch (args[0]) {
        case "--ui=classic" -> UiExperience.CLASSIC;
        case "--ui=journal" -> UiExperience.JOURNAL;
        default -> throw new IllegalArgumentException("Unknown startup argument");
      };
    }
    throw new IllegalArgumentException("Expected a single startup option");
  }

  private static void startGui(UiExperience override) {
    Fonts.install();
    ThemeManager.applySaved();
    UiExperience experience = override != null ? override : ExperienceChooser.choose().orElse(null);
    if (experience == null) {
      return; // No frame, database installation or background worker has been created.
    }
    MainFrame frame = new MainFrame(experience);
    AtomicReference<Database> open = new AtomicReference<>();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        Database db = open.get();
        if (db != null) {
          db.close();
        }
        System.exit(0);
      }
    });
    frame.status().setMessage("Loading database…");
    frame.setVisible(true);
    Async.run(Main::load, loaded -> runOrFail(() -> {
      open.set(loaded.db());
      IconCache.install(new IconCache(loaded.repos().assets()));
      AppController controller = new AppController(loaded.repos(), frame, Async.SWING_WORKER, loaded.meta());
      frame.attach(controller);
      controller.start();
    }, e -> startupFailed(frame, "The browser could not start:\n", e)),
        e -> startupFailed(frame, "The database could not be opened:\n", e));
  }

  /** Runs {@code body}; anything it throws goes to {@code onFailure} instead of escaping. */
  static void runOrFail(Runnable body, Consumer<Exception> onFailure) {
    try {
      body.run();
    } catch (Exception e) {
      onFailure.accept(e);
    } catch (Error e) { // say, a class failing to load: must not leave the window on "Loading database…"
      onFailure.accept(new IllegalStateException(e.toString(), e));
    }
  }

  /** Logs a start-up failure with its stack trace. */
  static void logStartupFailure(Throwable e) {
    LOG.error("Startup failed", e);
  }

  /** Logs {@code e}, shows {@code prefix} and its reason in a dialog over {@code frame}, then exits with 1. */
  private static void startupFailed(MainFrame frame, String prefix, Exception e) {
    logStartupFailure(e);
    String reason = e.getMessage() != null ? e.getMessage() : e.toString();
    JOptionPane.showMessageDialog(frame, prefix + reason, MainFrame.APP_NAME, JOptionPane.ERROR_MESSAGE);
    System.exit(1);
  }

  private static Loaded load() {
    try {
      Path file = new DatabaseInstaller(UserDataDir.resolve()).install();
      Database db = Database.open(file);
      try {
        Repositories repos = Repositories.of(db);
        return new Loaded(db, repos, db.meta());
      } catch (RuntimeException e) {
        db.close();
        throw e;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (SQLException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }
}
