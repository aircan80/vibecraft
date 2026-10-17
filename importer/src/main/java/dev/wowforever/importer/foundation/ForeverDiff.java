package dev.wowforever.importer.foundation;

import dev.wowforever.importer.clientdata.MiniJson;
import java.sql.*;
import java.util.*;

/** Recomputable, source-cell comparisons; never infers novelty from missing coverage. */
public final class ForeverDiff {
    private ForeverDiff() {}

    public static void initialize(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS forever_diff (
                  kind TEXT NOT NULL,id TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('new','changed')),
                  fields TEXT NOT NULL,era_snapshot_id TEXT NOT NULL,forever_snapshot_id TEXT NOT NULL,
                  era_build TEXT NOT NULL,forever_build TEXT NOT NULL,
                  PRIMARY KEY(kind,id,era_snapshot_id,forever_snapshot_id))
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS forever_diff_coverage (
                  kind TEXT NOT NULL,era_snapshot_id TEXT NOT NULL,forever_snapshot_id TEXT NOT NULL,
                  state TEXT NOT NULL CHECK(state IN ('comparable','missing_table','schema_mismatch','key_mismatch','partial_schema')),
                  detail TEXT NOT NULL,era_rows INTEGER,forever_rows INTEGER,
                  PRIMARY KEY(kind,era_snapshot_id,forever_snapshot_id))
                """);
        }
    }

    public static void project(Connection connection, String eraSnapshot, String foreverSnapshot) throws SQLException {
        if (Objects.equals(eraSnapshot, foreverSnapshot)) throw new IllegalArgumentException("Snapshots must differ");
        String eraBuild = validate(connection, eraSnapshot, "wow_classic_era", "1.15.");
        String foreverBuild = validate(connection, foreverSnapshot, "wow_classic_beta", "1.60.");
        initialize(connection);
        Map<String, Manifest> era = manifests(connection, eraSnapshot);
        Map<String, Manifest> forever = manifests(connection, foreverSnapshot);
        Set<String> kinds = new TreeSet<>(era.keySet());
        kinds.addAll(forever.keySet());
        // Coverage includes the roadmap's topical tables even when neither input contains one.
        kinds.addAll(List.of("Item", "ItemSparse", "ItemSet", "ItemSetSpell", "AreaTable", "Map",
                "LFGDungeons", "Faction", "TaxiNodes", "SkillLineAbility", "Talent", "TalentTab", "CharBaseInfo"));
        for (String table : List.of("forever_diff", "forever_diff_coverage")) {
            try (PreparedStatement p = connection.prepareStatement("DELETE FROM " + table + " WHERE era_snapshot_id=? AND forever_snapshot_id=?")) {
                p.setString(1, eraSnapshot); p.setString(2, foreverSnapshot); p.executeUpdate();
            }
        }
        for (String kind : kinds) {
            Manifest e = era.get(kind), f = forever.get(kind);
            String state = "comparable", detail = "Native column sets and ordered keys match";
            if (e == null || f == null) {
                state = "missing_table"; detail = e == null && f == null ? "Table absent from both snapshots" : e == null ? "Table absent from Era snapshot" : "Table absent from Forever snapshot";
            } else if (!cells(connection, e.keys).equals(cells(connection, f.keys))) {
                state = "key_mismatch"; detail = "Ordered key columns differ";
            }
            Map<String, String> comparable = new TreeMap<>();
            if (e != null && f != null && !state.equals("key_mismatch")) {
                List<String> ec = cells(connection, e.columns), fc = cells(connection, f.columns);
                List<String> aliases = new ArrayList<>();
                for (String column : fc) {
                    if (ec.contains(column)) comparable.put(column, column);
                    else if (kind.equals("SkillLineAbility") && column.matches("RaceMasks_[01]")) {
                        String old = column.replace("RaceMasks_", "RaceMask_");
                        if (ec.contains(old)) { comparable.put(column, old); aliases.add(old + "=>" + column); }
                    }
                }
                Set<String> excludedEra = new TreeSet<>(ec), excludedForever = new TreeSet<>(fc);
                excludedEra.removeAll(comparable.values()); excludedForever.removeAll(comparable.keySet());
                if (!excludedEra.isEmpty() || !excludedForever.isEmpty() || !aliases.isEmpty()) {
                    state = "partial_schema";
                    detail = "Excluded Era columns: " + excludedEra + "; excluded Forever columns: " + excludedForever + "; aliases: " + aliases;
                }
            }
            try (PreparedStatement p = connection.prepareStatement("INSERT INTO forever_diff_coverage VALUES(?,?,?,?,?,?,?)")) {
                p.setString(1, kind); p.setString(2, eraSnapshot); p.setString(3, foreverSnapshot);
                p.setString(4, state); p.setString(5, detail);
                p.setObject(6, e == null ? null : e.rows); p.setObject(7, f == null ? null : f.rows); p.executeUpdate();
            }
            if (!state.equals("comparable") && !state.equals("partial_schema")) continue;
            compareRows(connection, kind, eraSnapshot, foreverSnapshot, eraBuild, foreverBuild, comparable);
        }
    }

    /** The primary-key join streams one pair at a time; JSON is parsed once per row. */
    private static void compareRows(Connection c, String kind, String eraSnapshot, String foreverSnapshot,
                                    String eraBuild, String foreverBuild, Map<String, String> comparable) throws SQLException {
        try (PreparedStatement rows = c.prepareStatement("""
                SELECT f.row_id,f.payload,e.row_id,e.payload
                FROM client_table_row f LEFT JOIN client_table_row e
                ON e.snapshot_id=? AND e.table_name=f.table_name AND e.row_id=f.row_id
                WHERE f.snapshot_id=? AND f.table_name=?
                """);
             PreparedStatement insert = c.prepareStatement("""
                INSERT INTO forever_diff(kind,id,status,fields,era_snapshot_id,forever_snapshot_id,era_build,forever_build)
                VALUES(?,?,?,?,?,?,?,?)
                """)) {
            rows.setString(1, eraSnapshot); rows.setString(2, foreverSnapshot); rows.setString(3, kind);
            int pending = 0;
            try (ResultSet r = rows.executeQuery()) {
                while (r.next()) {
                    boolean isNew = r.getString(3) == null;
                    Map<?, ?> foreverCells = payload(r.getString(2));
                    Map<?, ?> eraCells = isNew ? Map.of() : payload(r.getString(4));
                    List<String> changed = new ArrayList<>();
                    for (Map.Entry<String, String> field : comparable.entrySet()) {
                        if (isNew || !Objects.equals(foreverCells.get(field.getKey()), eraCells.get(field.getValue())))
                            changed.add(field.getKey());
                    }
                    if (!isNew && changed.isEmpty()) continue;
                    StringJoiner fields = new StringJoiner(",", "[", "]");
                    changed.forEach(field -> fields.add(quote(field)));
                    insert.setString(1, kind); insert.setString(2, r.getString(1));
                    insert.setString(3, isNew ? "new" : "changed"); insert.setString(4, fields.toString());
                    insert.setString(5, eraSnapshot); insert.setString(6, foreverSnapshot);
                    insert.setString(7, eraBuild); insert.setString(8, foreverBuild);
                    insert.addBatch();
                    if (++pending == 256) { insert.executeBatch(); insert.clearBatch(); pending = 0; }
                }
            }
            if (pending > 0) insert.executeBatch();
        }
    }

    private static Map<?, ?> payload(String json) {
        Object value = MiniJson.parse(json);
        if (!(value instanceof Map<?, ?> cells)) throw new IllegalArgumentException("Native row payload must be an object");
        for (Map.Entry<?, ?> cell : cells.entrySet()) {
            if (!(cell.getKey() instanceof String) || !(cell.getValue() instanceof String))
                throw new IllegalArgumentException("Native row cells must retain their original strings");
        }
        return cells;
    }

    private static String quote(String text) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 32) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }

    private static String validate(Connection c, String id, String product, String prefix) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT product,build FROM client_table_dataset WHERE snapshot_id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next() || !product.equals(r.getString(1)) || r.getString(2) == null || !r.getString(2).startsWith(prefix))
                    throw new IllegalArgumentException("Expected " + product + " build " + prefix + "* snapshot: " + id);
                return r.getString(2);
            }
        }
    }
    private record Manifest(String keys, String columns, long rows) {}
    private static Map<String, Manifest> manifests(Connection c, String id) throws SQLException {
        Map<String, Manifest> result = new TreeMap<>();
        try (PreparedStatement p = c.prepareStatement("SELECT table_name,key_columns,column_names,row_count FROM client_table_manifest WHERE snapshot_id=?")) {
            p.setString(1, id);
            try (ResultSet r = p.executeQuery()) { while (r.next()) result.put(r.getString(1), new Manifest(r.getString(2), r.getString(3), r.getLong(4))); }
        }
        return result;
    }
    private static List<String> cells(Connection c, String json) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT value FROM json_each(?) ORDER BY key")) {
            p.setString(1, json);
            try (ResultSet r = p.executeQuery()) { while (r.next()) result.add(r.getString(1)); }
        }
        return result;
    }
}
