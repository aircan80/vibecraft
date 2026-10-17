package dev.wowforever.app;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Preferences kept in memory for the test JVM, so that no test reads or writes the user's real settings (on
 * Windows, the registry). Surefire names it in the {@code java.util.prefs.PreferencesFactory} system property
 * (app/pom.xml); {@code PreferencesIsolationTest} fails the build when it is not in use.
 */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
  private static final Preferences USER = new Node(null, "");
  private static final Preferences SYSTEM = new Node(null, "");

  @Override
  public Preferences userRoot() {
    return USER;
  }

  @Override
  public Preferences systemRoot() {
    return SYSTEM;
  }

  /** A node whose values and children live in maps; {@link AbstractPreferences} does the locking. */
  static final class Node extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Node> children = new HashMap<>();

    Node(Node parent, String name) {
      super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
      values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
      return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
      values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
      values.clear();
      if (parent() instanceof Node p) {
        p.children.remove(name());
      }
    }

    @Override
    protected String[] keysSpi() {
      return values.keySet().toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi() {
      return children.keySet().toArray(String[]::new);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
      return children.computeIfAbsent(name, n -> new Node(this, n));
    }

    @Override
    protected void syncSpi() {}

    @Override
    protected void flushSpi() {}
  }
}
