package dev.wowforever.importer.serverdb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a tiny VMaNGOS world database ({@code mangos.sqlite}) with the columns the importer reads, laid out like the
 * official SQLite release (creature_template, creature_loot_template, reference_loot_template, item_template). Every
 * item a loot row names gets an {@code item_template} row at patch 0, unless {@link #unknownItem} says otherwise.
 */
public final class ServerDbFixture {
  private static final String LOOT_COLUMNS = "(`entry` integer NOT NULL DEFAULT '0', `item` integer NOT NULL DEFAULT"
      + " '0', `ChanceOrQuestChance` float NOT NULL DEFAULT '100', `groupid` integer NOT NULL DEFAULT '0',"
      + " `mincountOrRef` integer NOT NULL DEFAULT '1', `maxcount` integer NOT NULL DEFAULT '1', `condition_id`"
      + " integer NOT NULL DEFAULT '0', `patch_min` integer NOT NULL DEFAULT '0', `patch_max` integer NOT NULL"
      + " DEFAULT '10')";
  private static final List<String> SCHEMA = List.of(
      "CREATE TABLE `creature_template` (`entry` integer NOT NULL DEFAULT '0', `patch` integer NOT NULL DEFAULT"
          + " '0', `name` char(100) NOT NULL DEFAULT '0', `loot_id` integer NOT NULL DEFAULT '0',"
          + " PRIMARY KEY (`entry`, `patch`))",
      "CREATE TABLE `creature_loot_template` " + LOOT_COLUMNS,
      "CREATE TABLE `reference_loot_template` " + LOOT_COLUMNS,
      "CREATE TABLE `item_template` (`entry` integer NOT NULL DEFAULT '0', `patch` integer NOT NULL DEFAULT '0',"
          + " `name` varchar(255) NOT NULL DEFAULT '', PRIMARY KEY (`entry`, `patch`))");

  private final List<String> rows = new ArrayList<>();
  private boolean forbiddenTable;
  private final Set<Long> items = new TreeSet<>();
  private final Set<Long> unknownItems = new HashSet<>();

  /** A creature (patch 0) whose loot table is {@code lootId}; 0 means no loot. */
  public ServerDbFixture creature(long entry, long lootId) {
    return creature(entry, 0, lootId);
  }

  public ServerDbFixture creature(long entry, int patch, long lootId) {
    rows.add(String.format(Locale.ROOT, "INSERT INTO creature_template(entry, patch, name, loot_id)"
        + " VALUES (%d, %d, 'npc %d', %d)", entry, patch, entry, lootId));
    return this;
  }

  /** A creature_loot_template row valid in every patch (0 to 10). */
  public ServerDbFixture loot(long entry, long item, double chance, int group, int mincountOrRef, int maxcount) {
    return row("creature_loot_template", entry, item, chance, group, mincountOrRef, maxcount, 0, 10);
  }

  /** A creature_loot_template row referencing {@code reference} at {@code chance}, rolled {@code times} times. */
  public ServerDbFixture lootRef(long entry, long reference, double chance, int group, int times) {
    return loot(entry, reference, chance, group, (int) -reference, times);
  }

  public ServerDbFixture lootPatchedGrouped(long entry, long item, double chance, int group, int patchMin,
      int patchMax) {
    return row("creature_loot_template", entry, item, chance, group, 1, 1, patchMin, patchMax);
  }

  /** A reference row whose {@code item} column is {@code item} (VMaNGOS ignores it on references). */
  public ServerDbFixture lootRefAs(long entry, long item, long reference, double chance, int group, int times) {
    return loot(entry, item, chance, group, (int) -reference, times);
  }

  /**
   * A {@code forbidden_items} row: the item is left out of the loot from {@code patch} on ({@code afterOrBefore = 0})
   * or up to {@code patch} ({@code afterOrBefore = 1}). Without a call the table does not exist.
   */
  public ServerDbFixture forbidden(long item, int patch, int afterOrBefore) {
    forbiddenTable = true;
    rows.add(String.format(Locale.ROOT, "INSERT INTO forbidden_items(entry, patch, after_or_before)"
        + " VALUES (%d, %d, %d)", item, patch, afterOrBefore));
    return this;
  }

  /** Leaves {@code item} out of {@code item_template}, as an item the server does not know. */
  public ServerDbFixture unknownItem(long item) {
    unknownItems.add(item);
    return this;
  }

  public ServerDbFixture lootPatched(long entry, long item, double chance, int patchMin, int patchMax) {
    return row("creature_loot_template", entry, item, chance, 0, 1, 1, patchMin, patchMax);
  }

  /** A reference_loot_template row valid in every patch. */
  public ServerDbFixture ref(long entry, long item, double chance, int group, int mincountOrRef, int maxcount) {
    return row("reference_loot_template", entry, item, chance, group, mincountOrRef, maxcount, 0, 10);
  }

  public ServerDbFixture refRef(long entry, long reference, double chance, int group, int times) {
    return ref(entry, reference, chance, group, (int) -reference, times);
  }

  private ServerDbFixture row(String table, long entry, long item, double chance, int group, int mincountOrRef,
      int maxcount, int patchMin, int patchMax) {
    if (mincountOrRef >= 0) {
      items.add(item);
    }
    rows.add(String.format(Locale.ROOT, "INSERT INTO %s(entry, item, ChanceOrQuestChance, groupid, mincountOrRef,"
        + " maxcount, patch_min, patch_max) VALUES (%d, %d, %s, %d, %d, %d, %d, %d)", table, entry, item,
        Double.toString(chance), group, mincountOrRef, maxcount, patchMin, patchMax));
    return this;
  }

  /** Writes {@code dir/mangos.sqlite} and returns {@code dir}. */
  public Path write(Path dir) throws IOException, SQLException {
    Files.createDirectories(dir);
    Path file = dir.resolve(ServerDbSource.WORLD_DB);
    Files.deleteIfExists(file);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        Statement s = c.createStatement()) {
      for (String sql : SCHEMA) {
        s.execute(sql);
      }
      if (forbiddenTable) {
        s.execute("CREATE TABLE `forbidden_items` (`entry` integer NOT NULL DEFAULT '0', `patch` integer NOT NULL"
            + " DEFAULT '0', `after_or_before` integer NOT NULL DEFAULT '0',"
            + " PRIMARY KEY (`entry`, `after_or_before`))");
      }
      for (String sql : rows) {
        s.execute(sql);
      }
      for (long item : items) {
        if (!unknownItems.contains(item)) {
          s.execute(String.format(Locale.ROOT, "INSERT INTO item_template(entry, patch, name) VALUES (%d, 0,"
              + " 'item %d')", item, item));
        }
      }
    }
    return dir;
  }
}
