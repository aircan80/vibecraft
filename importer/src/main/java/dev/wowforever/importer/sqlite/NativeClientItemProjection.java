package dev.wowforever.importer.sqlite;

import dev.wowforever.importer.clientdata.*;
import java.sql.*;
import java.util.Map;

/** Completes existing items only when no client card has been selected yet. */
public final class NativeClientItemProjection {
  private NativeClientItemProjection() {}
  public static void apply(Connection c, Map<Long,ClientItem> items, Map<Long,ItemValues> values,
      ItemTooltips tooltips, String snapshot) throws SQLException {
    for(var item:items.values()) {
      if(!ClientItem.FOREVER.equals(item.clientSource()) || !item.hasSparse())
        throw new IllegalArgumentException("Native completion requires native Forever ItemSparse");
      try(var p=c.prepareStatement("SELECT has_client_data,client_source FROM item WHERE id=?")) {
        p.setLong(1,item.id());try(var r=p.executeQuery()) {
          if(!r.next() || r.getInt(1)!=0 || ClientItem.HOTFIX.equals(r.getString(2)) || ClientItem.ERA.equals(r.getString(2)))
            throw new IllegalArgumentException("Native completion refuses existing client card: "+item.id());
        }
      }
    }
    ClientItemUpdateWriter.apply(c,items,values,tooltips,snapshot,ClientItem.FOREVER,
        "native_client_previous_item","baseline-before-native-client:");
  }
}
