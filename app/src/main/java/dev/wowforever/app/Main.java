package dev.wowforever.app;

import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.Sql;
import dev.wowforever.app.db.UserDataDir;
import dev.wowforever.app.ui.AppController;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.BrowseTree;
import dev.wowforever.app.ui.MainFrame;
import dev.wowforever.app.ui.ThemeManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Starts the browser. {@code --self-test} installs and opens the database, prints its row counts and exits
 * (a packaging smoke test); without arguments it shows the main window.
 */
public final class Main {
  private Main() {}

  /** The open database and everything the window needs from it, loaded off the EDT. */
  private record Loaded(Database db, Repositories repos, Map<String, String> meta, BrowseTree.Data tree) {}

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("--self-test")) {
      System.exit(runSelfTest());
    }
    if (args.length > 0) {
      System.err.println("Usage: java -jar wow-forever-browser.jar [--self-test]");
      System.exit(2);
    }
    SwingUtilities.invokeLater(Main::startGui);
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

  /** On the EDT: shows the window, then opens the database in the background and wires the controller. */
  private static void startGui() {
    ThemeManager.applySaved();
    MainFrame frame = new MainFrame();
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
    Async.run(Main::load, loaded -> {
      open.set(loaded.db());
      AppController controller = new AppController(loaded.repos(), frame, Async.SWING_WORKER, loaded.meta());
      frame.attach(controller, new BrowseTree(loaded.tree(), controller, controller::openZoneList));
      controller.start();
    }, e -> {
      String reason = e.getMessage() != null ? e.getMessage() : e.toString();
      JOptionPane.showMessageDialog(frame, "The database could not be opened:\n" + reason,
          MainFrame.APP_NAME, JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    });
  }

  private static Loaded load() {
    try {
      Path file = new DatabaseInstaller(UserDataDir.resolve()).install();
      Database db = Database.open(file);
      try {
        Repositories repos = Repositories.of(db);
        return new Loaded(db, repos, db.meta(), BrowseTree.Data.load(repos));
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
