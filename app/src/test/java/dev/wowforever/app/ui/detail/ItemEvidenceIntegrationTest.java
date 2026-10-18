package dev.wowforever.app.ui.detail;

import static org.assertj.core.api.Assertions.*;
import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.ui.widgets.*;
import java.nio.file.*;
import java.awt.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemEvidenceIntegrationTest {
  @TempDir Path temp;
  @Test void itemEvidenceAndKnowledgeExpandAfterWorkerDatabaseCloses()throws Exception {
    Path file=temp.resolve("item.sqlite");Files.copy(FixtureDb.path(),file);
    Repositories repos;DetailPanels.LoadedItem loaded;
    try(var db=Database.open(file)) {
      repos=Repositories.of(db);
      loaded=(DetailPanels.LoadedItem)DetailPanels.load(new EntityRef(EntityKind.ITEM,2874),repos).orElseThrow();
      assertThat(loaded.sourceDifferences()).isNotEmpty();assertThat(loaded.knowledge()).isNotEmpty();
    }
    SwingUtilities.invokeAndWait(()-> {
      JComponent page=TestPages.render(loaded,repos,ignored->{});
      var panel=find(page,SourceDifferencesPanel.class);assertThat(panel).isNotNull();
      var source=find(panel,SourceInfoButton.class);assertThat(source).isNotNull();
      assertThat(source.detailsContent().getComponentCount()).isPositive();
      assertThat(find(page,FieldKnowledgePanel.class)).isNotNull();
    });
  }
  @Test void onlyExplicitHiddenKnowledgeShowsTheLockLine()throws Exception {
    var detail=FixtureDb.repositories().items().detail(1176).orElseThrow();
    SwingUtilities.invokeAndWait(()-> {
      for(var state:new FieldKnowledgeView.State[]{FieldKnowledgeView.State.HIDDEN_BY_BLIZZARD,FieldKnowledgeView.State.KNOWN}) {
        var knowledge=java.util.List.of(new FieldKnowledgeView("stats",state,null,"forever","70450","ItemSparse.csv"));
        var page=new dev.wowforever.app.ui.pages.ItemPage(detail,null,java.util.Map.of(),java.util.Set.of(),SourceFilter.ALL,
            ignored->{},QuestInvolvement.fromRelated(detail.relatedQuests()),ignored->{},ignored->{},java.util.List.of(),knowledge,null,ForeverContent.Context.empty());
        assertThat(page.header().note()).isEqualTo(state==FieldKnowledgeView.State.HIDDEN_BY_BLIZZARD
            ?dev.wowforever.app.ui.pages.ItemPage.STATS_HIDDEN:"Stats recorded; tooltip text unavailable");
      }
    });
  }
  @Test void itemNewChangedContextUsesOnlyExactTypedIdAndPinnedBuild()throws Exception {
    Path file=temp.resolve("diff.sqlite");Files.copy(FixtureDb.path(),file);
    try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()) {
      s.execute("CREATE TABLE forever_diff(kind TEXT,id TEXT,status TEXT,fields TEXT,forever_build TEXT,era_build TEXT)");
      s.execute("INSERT OR REPLACE INTO meta VALUES('client_build','1.60.1.70450')");
      s.execute("INSERT INTO forever_diff VALUES('Item','2874','new','id','1.60.1.70450','1.15.9.70003'),('ItemSparse','2874','changed','name','1.60.1.70450','1.15.9.70003'),('Map','2874','new','id','1.60.1.70450','1.15.9.70003'),('Item','2875','new','id','1.60.1.70450','1.15.9.70003'),('Item','2874','new','id','other-build','era')");
    }
    try(var db=Database.open(file)) {
      var context=new ItemRepository(db).foreverContext(2874);
      assertThat(context.changes()).hasSize(2).extracting(ForeverContent.Change::kind).containsExactly("Item","ItemSparse");
      assertThat(context.foreverBuild()).isEqualTo("1.60.1.70450");assertThat(context.eraBuild()).isEqualTo("1.15.9.70003");
    }
  }
  private static <T> T find(Container root,Class<T> type) {
    if(type.isInstance(root))return type.cast(root);
    for(Component child:root.getComponents())if(child instanceof Container container) {
      T found=find(container,type);if(found!=null)return found;
    }
    return null;
  }
}
