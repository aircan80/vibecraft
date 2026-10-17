package dev.wowforever.app.db;

import java.util.Optional;

/** Embedded images: item icons (56×56 PNG) and named client UI textures. */
public final class AssetRepository {
  /** The id of the question-mark icon, shown for items without an icon of their own. */
  public static final long FALLBACK_ICON_ID = 0;

  private final Database db;

  public AssetRepository(Database db) {
    this.db = db;
  }

  /** The PNG of icon {@code id}, or empty when the database has no such icon. */
  public Optional<byte[]> icon(long id) {
    return db.read(c -> Sql.one(c, "SELECT png FROM icon WHERE id = ?", rs -> rs.getBytes("png"), id));
  }

  /**
   * The PNG of icon {@code id}, or the {@link #FALLBACK_ICON_ID question mark} when {@code id} is null or its icon
   * is missing; empty when the database has neither.
   */
  public Optional<byte[]> iconOrFallback(Long id) {
    Optional<byte[]> own = id == null ? Optional.empty() : icon(id);
    return own.isPresent() ? own : icon(FALLBACK_ICON_ID);
  }

  /** The PNG of the client UI texture {@code name} (e.g. {@code rail.quests}, {@code rail.quests@24}), if any. */
  public Optional<byte[]> uiIcon(String name) {
    return db.read(c -> Sql.one(c, "SELECT png FROM ui_asset WHERE name = ?", rs -> rs.getBytes("png"), name));
  }
}
