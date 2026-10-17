package dev.wowforever.importer.foundation;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ForeverDiffTest {
    private Connection database() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE client_table_dataset(snapshot_id TEXT PRIMARY KEY,product TEXT,build TEXT,locale TEXT)");
            s.execute("CREATE TABLE client_table_manifest(snapshot_id TEXT,table_name TEXT,key_columns TEXT,column_names TEXT,row_count INTEGER,source_path TEXT,sha256 TEXT,PRIMARY KEY(snapshot_id,table_name))");
            s.execute("CREATE TABLE client_table_row(snapshot_id TEXT,table_name TEXT,row_id TEXT,payload TEXT,source_path TEXT,source_line INTEGER,PRIMARY KEY(snapshot_id,table_name,row_id))");
            s.execute("INSERT INTO client_table_dataset VALUES('era','wow_classic_era','1.15.7','enUS'),('forever','wow_classic_beta','1.60.1','enUS'),('era2','wow_classic_era','1.15.8','enUS')");
        }
        return c;
    }
    private void manifest(Connection c, String snapshot, String kind, String keys, String columns) throws Exception {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO client_table_manifest VALUES(?,?,?,?,3,'native.csv','abc')")) {
            p.setString(1,snapshot); p.setString(2,kind); p.setString(3,keys); p.setString(4,columns); p.executeUpdate();
        }
    }
    private void row(Connection c, String snapshot, String kind, String id, String payload) throws Exception {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO client_table_row VALUES(?,?,?,?,'native.csv',2)")) {
            p.setString(1,snapshot); p.setString(2,kind); p.setString(3,id); p.setString(4,payload); p.executeUpdate();
        }
    }
    private String scalar(Connection c, String sql) throws Exception {
        try (Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { return r.next()?r.getString(1):null; }
    }
    @Test void comparesExactNativeCellsAcrossKindsAndPreservesSourceAndOtherPairs() throws Exception {
        try (Connection c=database()) {
            for (String snapshot : new String[]{"era","forever"}) {
                manifest(c,snapshot,"Item","[\"ID\"]",snapshot.equals("era")?"[\"ID\",\"Name\"]":"[\"Name\",\"ID\"]");
                manifest(c,snapshot,"Map","[\"ID\"]","[\"ID\",\"Name\"]");
                row(c,snapshot,"Item","1","{\"ID\":\"1\",\"Name\":\"same\"}");
                row(c,snapshot,"Item","2",snapshot.equals("era")?"{\"ID\":\"2\",\"Name\":\"\"}":"{\"Name\":\"0\",\"ID\":\"2\"}");
                row(c,snapshot,"Map","4",snapshot.equals("era")?"{\"ID\":\"4\",\"Name\":\"Old\"}":"{\"ID\":\"4\",\"Name\":\"New\"}");
            }
            row(c,"era","Item","99","{\"ID\":\"99\",\"Name\":\"removed\"}");
            row(c,"forever","Item","3","{\"ID\":\"3\",\"Name\":\"new\"}");
            String source=scalar(c,"SELECT group_concat(payload,'|') FROM client_table_row");
            c.setAutoCommit(false);
            ForeverDiff.project(c,"era","forever");
            assertThat(c.getAutoCommit()).isFalse();
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff")).isEqualTo("3");
            assertThat(scalar(c,"SELECT fields FROM forever_diff WHERE kind='Item' AND id='2'")).isEqualTo("[\"Name\"]");
            assertThat(scalar(c,"SELECT status FROM forever_diff WHERE id='3'")).isEqualTo("new");
            assertThat(scalar(c,"SELECT fields FROM forever_diff WHERE id='3'")).isEqualTo("[\"ID\",\"Name\"]");
            ForeverDiff.project(c,"era","forever");
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff")).isEqualTo("3");
            ForeverDiff.project(c,"era2","forever");
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff")).isEqualTo("3");
            assertThat(scalar(c,"SELECT group_concat(payload,'|') FROM client_table_row")).isEqualTo(source);
            assertThat(scalar(c,"SELECT era_build || '/' || forever_build FROM forever_diff LIMIT 1")).isEqualTo("1.15.7/1.60.1");
            c.rollback();
        }
    }
    @Test void reportsMissingSchemaAndOrderedKeyCoverageWithoutInventingNewRows() throws Exception {
        try (Connection c=database()) {
            manifest(c,"forever","Item","[\"ID\"]","[\"ID\",\"Name\"]");
            row(c,"forever","Item","1","{\"ID\":\"1\",\"Name\":\"new?\"}");
            manifest(c,"era","Map","[\"ID\"]","[\"ID\"]");
            manifest(c,"forever","Map","[\"ID\"]","[\"ID\",\"Name\"]");
            manifest(c,"era","Faction","[\"ID\",\"Name\"]","[\"ID\",\"Name\"]");
            manifest(c,"forever","Faction","[\"Name\",\"ID\"]","[\"ID\",\"Name\"]");
            ForeverDiff.project(c,"era","forever");
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff")).isEqualTo("0");
            assertThat(scalar(c,"SELECT state FROM forever_diff_coverage WHERE kind='Item'")).isEqualTo("missing_table");
            assertThat(scalar(c,"SELECT state FROM forever_diff_coverage WHERE kind='Map'")).isEqualTo("partial_schema");
            assertThat(scalar(c,"SELECT state FROM forever_diff_coverage WHERE kind='Faction'")).isEqualTo("key_mismatch");
            assertThat(scalar(c,"SELECT era_rows FROM forever_diff_coverage WHERE kind='Item'")).isNull();
        }
    }
    @Test void schemaEvolutionComparesSharedCellsAndExplicitRaceMaskAliases() throws Exception {
        try (Connection c=database()) {
            manifest(c,"era","SkillLineAbility","[\"ID\"]","[\"ID\",\"RaceMask_0\",\"RaceMask_1\",\"OldOnly\"]");
            manifest(c,"forever","SkillLineAbility","[\"ID\"]","[\"ID\",\"RaceMasks_0\",\"RaceMasks_1\",\"NewOnly\"]");
            row(c,"era","SkillLineAbility","1","{\"ID\":\"1\",\"RaceMask_0\":\"0\",\"RaceMask_1\":\"7\",\"OldOnly\":\"old\"}");
            row(c,"era","SkillLineAbility","2","{\"ID\":\"2\",\"RaceMask_0\":\"0\",\"RaceMask_1\":\"7\",\"OldOnly\":\"old\"}");
            row(c,"forever","SkillLineAbility","1","{\"ID\":\"1\",\"RaceMasks_0\":\"0\",\"RaceMasks_1\":\"7\",\"NewOnly\":\"new\"}");
            row(c,"forever","SkillLineAbility","2","{\"ID\":\"2\",\"RaceMasks_0\":\"0\",\"RaceMasks_1\":\"8\",\"NewOnly\":\"new\"}");
            row(c,"forever","SkillLineAbility","3","{\"ID\":\"3\",\"RaceMasks_0\":\"0\",\"RaceMasks_1\":\"7\",\"NewOnly\":\"new\"}");
            ForeverDiff.project(c,"era","forever");
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff")).isEqualTo("2");
            assertThat(scalar(c,"SELECT fields FROM forever_diff WHERE id='2'")).isEqualTo("[\"RaceMasks_1\"]");
            assertThat(scalar(c,"SELECT status FROM forever_diff WHERE id='3'")).isEqualTo("new");
            assertThat(scalar(c,"SELECT state FROM forever_diff_coverage WHERE kind='SkillLineAbility'")).isEqualTo("partial_schema");
            assertThat(scalar(c,"SELECT detail FROM forever_diff_coverage WHERE kind='SkillLineAbility'"))
                .contains("OldOnly", "NewOnly", "RaceMask_0=>RaceMasks_0");
        }
    }
    @Test void flushesMultipleBatchesWhileStreamingWideNativeRows() throws Exception {
        try (Connection c=database()) {
            StringBuilder columns = new StringBuilder("[\"ID\"");
            for (int j=0;j<300;j++) columns.append(",\"Field").append(j).append("\"");
            columns.append(']');
            for (String snapshot : new String[]{"era", "forever"})
                manifest(c, snapshot, "ItemSparse", "[\"ID\"]", columns.toString());
            for (int i=0;i<270;i++) {
                for (String snapshot : new String[]{"era", "forever"}) {
                    StringBuilder payload = new StringBuilder("{\"ID\":\"").append(i).append("\"");
                    for (int j=0;j<300;j++) payload.append(",\"Field").append(j).append("\":\"")
                        .append(j==299 && snapshot.equals("forever") ? "changed" : "same").append("\"");
                    row(c,snapshot,"ItemSparse",String.valueOf(i),payload.append('}').toString());
                }
            }
            ForeverDiff.project(c,"era","forever");
            assertThat(scalar(c,"SELECT count(*) FROM forever_diff WHERE kind='ItemSparse' AND status='changed' AND fields='[\"Field299\"]'"))
                .isEqualTo("270");
        }
    }
    @Test void rejectsUnidentifiedOrWrongProductAndSameSnapshot() throws Exception {
        try (Connection c=database()) {
            assertThatThrownBy(()->ForeverDiff.project(c,"era","era")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->ForeverDiff.project(c,"missing","forever")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->ForeverDiff.project(c,"forever","era")).isInstanceOf(IllegalArgumentException.class);
            c.createStatement().execute("UPDATE client_table_dataset SET build='1.14.0' WHERE snapshot_id='era'");
            assertThatThrownBy(()->ForeverDiff.project(c,"era","forever")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
