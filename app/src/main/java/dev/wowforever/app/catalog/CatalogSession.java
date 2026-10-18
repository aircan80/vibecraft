package dev.wowforever.app.catalog;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

/** Selection is independent of interface choice. A new generation invalidates every old worker token. */
public final class CatalogSession {
  public record Token(CatalogContext context, long generation, java.util.UUID sessionId) {}
  private final EditionRegistry registry;
  private final Preferences preferences;
  private final java.util.UUID sessionId = java.util.UUID.randomUUID();
  private volatile Token active;
  public CatalogSession(EditionRegistry registry, Preferences preferences) {
    this.registry = Objects.requireNonNull(registry);
    this.preferences = Objects.requireNonNull(preferences);
    CatalogContext initial = registry.installed(preferences.get("edition", "forever"))
        .or(() -> registry.installed("forever"))
        .orElseThrow(() -> new IllegalStateException("No validated gameplay catalog installed"));
    active = new Token(initial, 0, sessionId);
  }
  public Token token() { return active; }
  public synchronized Token select(String editionId) {
    CatalogContext next = registry.installed(editionId)
        .orElseThrow(() -> new IllegalArgumentException("Game catalog is not installed: " + editionId));
    preferences.put("edition", editionId);
    active = new Token(next, active.generation() + 1, sessionId);
    return active;
  }
  /** Even A → A reloads invalidate A's previous deliveries. */
  public boolean current(Token token) { return active.equals(token); }
  public BooleanSupplier guard(Token token) { return () -> current(token); }
  public Preferences statePreferences() {
    CatalogContext c = active.context();
    // Projection ID can exceed Preferences' 80-character node limit; use an SHA-256 digest.
    return preferences.node("catalog-state").node(digest(c));
  }
  private static String digest(CatalogContext context) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      for (String part : new String[] {context.editionId(), context.projectionId(), context.clientBuild(), context.locale()}) {
        byte[] bytes = part.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
}
