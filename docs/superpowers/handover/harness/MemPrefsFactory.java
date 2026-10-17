import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** In-memory preferences so the screenshot run never touches the user's saved window state or theme. */
public final class MemPrefsFactory implements PreferencesFactory {
  private static final MemPrefs USER = new MemPrefs(null, "");
  private static final MemPrefs SYSTEM = new MemPrefs(null, "");

  @Override public Preferences systemRoot() { return SYSTEM; }
  @Override public Preferences userRoot() { return USER; }

  static final class MemPrefs extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, MemPrefs> children = new HashMap<>();

    MemPrefs(AbstractPreferences parent, String name) { super(parent, name); }
    @Override protected void putSpi(String key, String value) { values.put(key, value); }
    @Override protected String getSpi(String key) { return values.get(key); }
    @Override protected void removeSpi(String key) { values.remove(key); }
    @Override protected void removeNodeSpi() { values.clear(); }
    @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
    @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
    @Override protected AbstractPreferences childSpi(String name) { return children.computeIfAbsent(name, n -> new MemPrefs(this, n)); }
    @Override protected void syncSpi() throws BackingStoreException {}
    @Override protected void flushSpi() throws BackingStoreException {}
  }
}
