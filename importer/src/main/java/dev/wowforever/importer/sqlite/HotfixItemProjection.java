package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.*;
import java.sql.*;
import java.util.Map;

/** Targeted hotfix item projection; the caller owns the transaction. */
public final class HotfixItemProjection {
  private HotfixItemProjection() {}
  public static void apply(Connection c, Map<Long,ClientItem> items, Map<Long,ItemValues> values,
      ItemTooltips tooltips, String snapshot) throws SQLException {
    ClientItemUpdateWriter.apply(c,items,values,tooltips,snapshot,ClientItem.HOTFIX,
        "hotfix_previous_item","baseline-before-hotfix:");
  }
}
