package dev.wowforever.app.ui.journal;

import static org.junit.jupiter.api.Assertions.*;
import dev.wowforever.app.db.Database;
import dev.wowforever.app.nav.*;
import java.awt.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalLoreSearchPageTest {
  @TempDir Path directory;
  @Test void debouncesOffEdtAndOpensExactQuestThenRestoresQuery() throws Exception {
    Path file=directory.resolve("corpus.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){
      s.execute("CREATE TABLE meta(key TEXT,value TEXT)");s.execute("CREATE TABLE quest(id INTEGER,name TEXT,objectives_text TEXT)");
      s.execute("INSERT INTO quest VALUES(123,'Tower witness','Visit Dalaran')");
    }
    try(var db=Database.open(file)){
      ExecutorService worker=Executors.newSingleThreadExecutor();
      try {
        java.util.List<ViewTarget> opened=new ArrayList<>();JournalLoreSearchPage[] page=new JournalLoreSearchPage[1];
        SwingUtilities.invokeAndWait(()->{page[0]=new JournalLoreSearchPage(db,opened::add,task->worker.execute(()->{assertFalse(SwingUtilities.isEventDispatchThread());task.run();}));page[0].restoreState("Dalaran");});
        await(()->find(page[0],JButton.class).stream().anyMatch(b->b.getText().contains("Tower witness")));
        SwingUtilities.invokeAndWait(()->{find(page[0],JButton.class).stream().filter(b->b.getText().contains("Tower witness")).findFirst().orElseThrow().doClick();assertEquals("Dalaran",page[0].saveState());});
        assertEquals(new ViewTarget.Entity(new dev.wowforever.app.model.EntityRef(dev.wowforever.app.model.EntityKind.QUEST,123)),opened.getFirst());
        SwingUtilities.invokeAndWait(()->page[0].restoreState("no match"));
        await(()->find(page[0],JLabel.class).stream().anyMatch(l->l.getText().equals("No exact text mentions found.")));
        SwingUtilities.invokeAndWait(()->{page[0].restoreState("da");page[0].removeNotify();});
      } finally {worker.shutdownNow();}
    }
  }
  @Test void failedCorpusShowsRecoverableMessage() throws Exception {
    Path file=directory.resolve("missing.sqlite");
    try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){s.execute("CREATE TABLE meta(key TEXT,value TEXT)");}
    try(var db=Database.open(file)){
      ExecutorService worker=Executors.newSingleThreadExecutor();
      try {JournalLoreSearchPage[] page=new JournalLoreSearchPage[1];
        SwingUtilities.invokeAndWait(()->{page[0]=new JournalLoreSearchPage(db,t->{},worker);page[0].restoreState("Dalaran");});
        await(()->find(page[0],JLabel.class).stream().anyMatch(l->l.getText().equals("Quest text could not be searched. Try again.")));
        SwingUtilities.invokeAndWait(()->page[0].removeNotify());
      } finally {worker.shutdownNow();}
    }
  }
  private static void await(java.util.function.BooleanSupplier ready)throws Exception {
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean[] complete={false};
    while(System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->complete[0]=ready.getAsBoolean());if(complete[0])return;Thread.sleep(25);}fail("Timed out waiting for UI state");
  }
  private static <T extends Component> java.util.List<T> find(Container parent,Class<T> type){java.util.List<T> result=new ArrayList<>();for(Component c:parent.getComponents()){if(type.isInstance(c))result.add(type.cast(c));if(c instanceof Container child)result.addAll(find(child,type));}return result;}
}
