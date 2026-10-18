package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.*;
import dev.wowforever.app.model.*;
import dev.wowforever.app.nav.*;
import java.awt.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

/** Debounced local text research; all SQL is dispatched to the worker supplied by the shell. */
public final class JournalLoreSearchPage extends JPanel implements dev.wowforever.app.ui.pages.PageState {
  private final JTextField query = new JTextField();
  private final JLabel status = new JLabel("Enter at least 3 characters to search.");
  private final JPanel results = JournalSurface.stack();
  private final AtomicLong generation = new AtomicLong();
  private final Timer debounce;
  public JournalLoreSearchPage(Database db, Navigator nav, Executor worker) {
    super(new BorderLayout(0,12));setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
    JPanel header=JournalSurface.stack();JournalSurface.add(header,JournalSurface.title("Azeroth through a word",32));
    JournalSurface.add(header,JournalSurface.copy("Find exact mentions in archived quest titles and objectives. This is a Questie reference corpus, not all Warcraft lore or a verified Forever client transcript. A matching name does not establish an entity's identity."));
    query.setName("lore-query");query.getAccessibleContext().setAccessibleName("Search quest text");
    JournalSurface.add(header,query);JournalSurface.add(header,status);add(header,BorderLayout.NORTH);
    JournalSurface.Body body=new JournalSurface.Body();body.add(results);add(JournalSurface.scroll(body),BorderLayout.CENTER);
    LoreSearchRepository repo=new LoreSearchRepository(db);
    debounce=new Timer(300,e->{
      long ticket=generation.get();String text=query.getText().strip();
      if(text.length()<3||text.length()>120){status.setText("Enter 3–120 characters to search.");return;}
      status.setText("Searching local quest text…");
      CompletableFuture.supplyAsync(()->repo.search(text,()->generation.get()==ticket),worker).whenComplete((data,error)->SwingUtilities.invokeLater(()->{
        if(generation.get()!=ticket)return;
        results.removeAll();
        if(error!=null){status.setText("Quest text could not be searched. Try again.");}
        else {
          status.setText(data.hits().isEmpty()?"No exact text mentions found.":data.hits().size()+" mentions"+(data.truncated()?" · Showing the first 100; refine your search.":""));
          JournalSurface.add(results,JournalSurface.copy(data.source()+" · Database build "+data.buildId()));
          for(var hit:data.hits()){
            JPanel card=JournalSurface.card();JButton open=new JButton(hit.title()+" · Quest #"+hit.questId());
            open.addActionListener(event->nav.open(new ViewTarget.Entity(new EntityRef(EntityKind.QUEST,hit.questId()))));
            JournalSurface.add(card,open);
            JTextArea excerpt=new JTextArea(hit.excerpt());excerpt.setLineWrap(true);excerpt.setWrapStyleWord(true);excerpt.setEditable(false);excerpt.setOpaque(false);
            try{excerpt.getHighlighter().addHighlight(hit.matchStart(),hit.matchEnd(),new DefaultHighlighter.DefaultHighlightPainter(new Color(215,179,82,110)));}catch(BadLocationException impossible){throw new IllegalStateException(impossible);}
            JournalSurface.add(card,excerpt);JournalSurface.add(results,card);
          }
        }
        results.revalidate();results.repaint();
      }));
    });debounce.setRepeats(false);
    query.getDocument().addDocumentListener(new DocumentListener(){
      private void changed(){generation.incrementAndGet();debounce.restart();results.removeAll();results.revalidate();results.repaint();}
      public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}
    });
  }
  public Object saveState(){return query.getText();}
  public void restoreState(Object state){if(state instanceof String text)query.setText(text);}
  @Override public void removeNotify(){generation.incrementAndGet();debounce.stop();super.removeNotify();}
}
