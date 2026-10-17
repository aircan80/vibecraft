import dev.wowforever.app.db.Database;
import dev.wowforever.app.db.DatabaseInstaller;
import dev.wowforever.app.db.Repositories;
import dev.wowforever.app.db.Sql;
import dev.wowforever.app.db.UserDataDir;
import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.AppController;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.MainFrame;
import dev.wowforever.app.ui.ThemeManager;
import dev.wowforever.app.ui.widgets.IconCache;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/** Renders README screenshots from inside the app (Swing painting, no desktop capture). */
public final class Shots {
  static MainFrame frame;
  static AppController ctl;
  static Path out;

  public static void main(String[] args) throws Exception {
    out = Path.of(args[0]);
    Files.createDirectories(out);
    SwingUtilities.invokeAndWait(() -> {
      ThemeManager.applySaved();
      frame = new MainFrame();
      frame.setVisible(true);
      frame.setExtendedState(Frame.NORMAL);
      frame.setSize(1440, 1000);
      frame.setLocation(0, 0);
    });
    Path file = new DatabaseInstaller(UserDataDir.resolve()).install();
    Database db = Database.open(file);
    Repositories repos = Repositories.of(db);
    Map<String, String> meta = db.meta();
    long deadmines = db.read(c -> Sql.one(c, "SELECT id FROM instance WHERE name = ?", rs -> rs.getLong("id"), "The Deadmines")).orElseThrow();
    SwingUtilities.invokeAndWait(() -> {
      IconCache.install(new IconCache(repos.assets()));
      ctl = new AppController(repos, frame, Async.SWING_WORKER, meta);
      frame.attach(ctl);
      ctl.start();
    });
    Thread.sleep(3500);
    shot("home");

    SwingUtilities.invokeAndWait(() -> {
      JTextField tf = (JTextField) find(frame, c -> c instanceof JTextField);
      tf.requestFocusInWindow();
      tf.setText("westfall");
    });
    Thread.sleep(2500);
    shot("search");
    SwingUtilities.invokeAndWait(() -> ((JTextField) find(frame, c -> c instanceof JTextField)).setText(""));
    Thread.sleep(800);

    open(EntityKind.ITEM, 19019); shot("item");
    open(EntityKind.NPC, 448); shot("npc");
    open(EntityKind.INSTANCE, deadmines); shot("instance");
    open(EntityKind.QUEST, 176); shot("quest");
    open(EntityKind.ZONE, 12);
    SwingUtilities.invokeAndWait(() -> {
      AbstractButton b = findLast(frame, c -> c instanceof javax.swing.JToggleButton ab && "Quests".equals(ab.getText()));
      System.out.println("quests toggle: " + b);
      if (b != null && !b.isSelected()) b.doClick();
    });
    Thread.sleep(3500);
    shot("zone");

    SwingUtilities.invokeAndWait(() -> toggleCompact());
    Thread.sleep(1500);
    open(EntityKind.NPC, 448);
    shot("compact");
    SwingUtilities.invokeAndWait(() -> toggleCompact());
    Thread.sleep(500);
    System.exit(0);
  }

  static void open(EntityKind kind, long id) throws Exception {
    SwingUtilities.invokeAndWait(() -> ctl.open(new ViewTarget.Entity(new EntityRef(kind, id))));
    Thread.sleep(3500);
  }

  static void toggleCompact() {
    try {
      Method m = MainFrame.class.getDeclaredMethod("toggleCompactMode");
      m.setAccessible(true);
      m.invoke(frame);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  static AbstractButton findLast(Container root, Predicate<Component> p) {
    AbstractButton last = null;
    java.util.ArrayDeque<Container> q = new java.util.ArrayDeque<>(java.util.List.of(root));
    while (!q.isEmpty()) {
      Container k = q.poll();
      for (Component c : k.getComponents()) {
        if (p.test(c)) last = (AbstractButton) c;
        if (c instanceof Container kk) q.add(kk);
      }
    }
    return last;
  }

  static Component find(Container root, Predicate<Component> p) {
    for (Component c : root.getComponents()) {
      if (p.test(c)) return c;
      if (c instanceof Container k) {
        Component r = find(k, p);
        if (r != null) return r;
      }
    }
    return null;
  }

  static void shot(String name) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        JComponent root = frame.getRootPane();
        int w = root.getWidth(), h = root.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        root.paint(g);
        Point origin = root.getLocationOnScreen();
        for (Window win : Window.getWindows()) {
          if (win instanceof JWindow jw && jw.isVisible() && jw.getOwner() == frame) {
            Point p = jw.getLocationOnScreen();
            Graphics2D g2 = (Graphics2D) g.create(p.x - origin.x, p.y - origin.y, jw.getWidth(), jw.getHeight());
            jw.getRootPane().paint(g2);
            g2.dispose();
          }
        }
        g.dispose();
        ImageIO.write(img, "png", out.resolve(name + ".png").toFile());
        System.out.println("shot " + name + " " + w + "x" + h);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }
}
