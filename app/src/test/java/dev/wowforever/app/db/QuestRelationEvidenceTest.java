package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.ui.pages.quest.ChainPanel;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestRelationEvidenceTest {
  @TempDir Path tmp;

  private static void table(SchemaDb db) throws Exception {
    db.sql("CREATE TABLE IF NOT EXISTS quest_relation_source (quest_id INTEGER, related_id INTEGER,kind TEXT,"
        + "source TEXT,snapshot_id TEXT,artifact_path TEXT,source_url TEXT,evidence_status TEXT,"
        + "PRIMARY KEY(quest_id,related_id,kind,source,snapshot_id,artifact_path))");
  }

  @Test void legacyDatabaseHasNoFabricatedRelationshipAttribution() throws Exception {
    try (var schema = SchemaDb.create(tmp)) {
      schema.quest(1, "First", 10).quest(2, "Second", 11).relation(2, 1, "pre_single");
      try (var db = schema.open()) {
        QuestChain chain = new QuestRepository(db).chain(2);
        assertThat(chain.evidence()).isEmpty();
        assertThat(chain.attUnsupportedConditions()).isZero();
        assertThat(chain.steps()).hasSize(2);
      }
    }
  }

  @Test void exactProjectedEdgesLoadOffEdtAndRemainUsableAfterDatabaseCloses() throws Exception {
    QuestChain chain;
    URI expected = URI.create("https://github.com/ATTWoWAddon/AllTheThings/blob/abc/quests.lua#L12");
    try (var schema = SchemaDb.create(tmp)) {
      schema.quest(79975, "Eagle's Fist", 32).quest(79976, "This Must Be The Place", 32)
          .relation(79976, 79975, "pre_single");
      table(schema);
      schema.sql("INSERT INTO quest_relation_source VALUES (?,?,?,?,?,?,?,?)", 79976, 79975, "pre_single",
          "allthethings-forever", "snapshot-abc", "quests.lua", expected.toString(), "authored");
      // Unprojected and unrelated evidence must not decorate a displayed connector.
      schema.sql("INSERT INTO quest_relation_source VALUES (?,?,?,?,?,?,?,?)", 79976, 79975, "pre_group",
          "unprojected", "snapshot-other", "other.lua", "https://example.com/other", "authored");
      schema.sql("INSERT INTO meta(key,value) VALUES ('att_unsupported_conditions','2')");
      try (var db = schema.open()) { chain = new QuestRepository(db).chain(79976); }
    }
    assertThat(chain.evidence()).singleElement().satisfies(e -> {
      assertThat(e.sourceLabel()).isEqualTo("AllTheThings Forever");
      assertThat(e.snapshotId()).isEqualTo("snapshot-abc");
      assertThat(e.artifactPath()).isEqualTo("quests.lua");
    });
    assertThat(chain.attUnsupportedConditions()).isEqualTo(2);
    List<URI> opened = new ArrayList<>();
    SwingUtilities.invokeAndWait(() -> {
      ChainPanel panel = new ChainPanel(chain, target -> {}, opened::add);
      assertThat(opened).isEmpty();
      var info = descendants(panel).stream()
          .filter(dev.wowforever.app.ui.widgets.SourceInfoButton.class::isInstance)
          .map(dev.wowforever.app.ui.widgets.SourceInfoButton.class::cast).findFirst().orElseThrow();
      assertThat(info.getToolTipText()).contains("AllTheThings Forever", "not in-game verified");
      var details = info.detailsContent();
      JButton source = descendants(details).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
          .filter(b -> b.getText().equals("Open original source ↗")).findFirst().orElseThrow();
      assertThat(descendants(details)).anySatisfy(component -> {
        if (!(component instanceof javax.swing.JTextArea text)) throw new AssertionError();
        assertThat(text.getText()).contains("snapshot-abc", "quests.lua", expected.toString());
      });
      assertThat(descendants(panel)).anySatisfy(component -> {
        if (!(component instanceof javax.swing.JTextArea text)) throw new AssertionError();
        assertThat(text.getText()).isEqualTo("Some AllTheThings conditions were not imported.");
      });
      source.doClick();
      assertThat(opened).containsExactly(expected);
    });
  }

  @Test void reverseTraversalRetainsNativeRelationshipAttribution() throws Exception {
    try (var schema = SchemaDb.create(tmp)) {
      schema.quest(1, "First", 10).quest(2, "Second", 11).relation(2, 1, "pre_single");
      table(schema);
      schema.sql("INSERT INTO quest_relation_source VALUES (2,1,'pre_single','allthethings-forever','s','q.lua',"
          + "'https://example.com/q','authored')");
      try (var db = schema.open()) {
        var chain = new QuestRepository(db).chain(1);
        var after = chain.steps().stream().filter(s -> s.questId() == 2).findFirst().orElseThrow();
        assertThat(after.connector()).isEqualTo(QuestChain.Connector.UNLOCKS);
        assertThat(chain.evidenceFor(after)).hasSize(1);
        assertThat(chain.evidence().getFirst().questId()).isEqualTo(2);
      }
    }
  }

  private static List<Component> descendants(Container parent) {
    List<Component> out = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      out.add(child);
      if (child instanceof Container container) out.addAll(descendants(container));
    }
    return out;
  }
}
