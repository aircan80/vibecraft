package dev.wowforever.app.ui.journal;

import dev.wowforever.app.db.NativeRecipeGuideRepository;
import dev.wowforever.app.db.NativeRecipeGuideRepository.Recipe;
import dev.wowforever.app.db.NativeRecipeGuideRepository.Snapshot;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.ui.Async;
import dev.wowforever.app.ui.StatusSink;
import dev.wowforever.app.ui.pages.PageState;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.function.LongFunction;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.swing.*;

/** Explicitly selected, lazy native recipe section. Queries never run during construction/rendering. */
final class JournalRecipeGuide extends JPanel implements PageState {
  private final Supplier<Snapshot> load;
  private final Executor worker;
  private final Navigator nav;
  private final StatusSink sink;
  private final LongFunction<dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot> teachingLoad;
  private final JPanel content=new JPanel(new BorderLayout());
  private final JComboBox<Recipe> variants=new JComboBox<>();
  private final JPanel choices=new JPanel(new BorderLayout(0,6));
  private volatile boolean active;
  private volatile long generation;
  private boolean loading;
  private boolean updating;
  private Snapshot snapshot;
  private JScrollPane recipeScroll;
  private String restoredVariant;
  private Integer restoredScroll;
  private JournalTeachingItems teaching;
  private String teachingVariant;
  private final Map<String,Object> teachingStates=new LinkedHashMap<>();
  private record State(String variant,int scroll,Map<String,Object> teaching) {
    private State { teaching=Map.copyOf(teaching); }
  }

  JournalRecipeGuide(Supplier<Snapshot> load,Executor worker,Navigator nav,StatusSink sink) {
    this(load,worker,nav,sink,null);
  }
  JournalRecipeGuide(Supplier<Snapshot> load,Executor worker,Navigator nav,StatusSink sink,
      LongFunction<dev.wowforever.app.db.NativeTeachingItemRepository.Snapshot> teachingLoad) {
    super(new BorderLayout(0,8));this.load=load;this.worker=worker;this.nav=nav;this.sink=sink;this.teachingLoad=teachingLoad;
    setBorder(BorderFactory.createEmptyBorder(12,12,0,12));choices.setOpaque(false);
    choices.add(new JLabel("Recipe variant"),BorderLayout.NORTH);choices.add(variants);choices.setVisible(false);add(choices,BorderLayout.NORTH);add(content);
    variants.getAccessibleContext().setAccessibleName("Native recipe variant");
    variants.setRenderer(new DefaultListCellRenderer(){
      @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
        return super.getListCellRendererComponent(list,value instanceof Recipe recipe?recipe.name()+" · "+recipe.variant():value,index,selected,focus);
      }
    });
    variants.addActionListener(event->{if(snapshot!=null&&!updating)showRecipe();});
    message("Choose Crafting guide to load exact Forever create-item records. Missing records do not mean this item cannot be crafted.");
  }

  /** The tab owner selects/deactivates this section, including Back/Forward restoration. */
  void setActive(boolean value) {
    if(active==value)return;active=value;
    if(!value){generation++;loading=false;if(teaching!=null)teaching.deactivate();return;}
    if(teaching!=null)teaching.resumeIfRequested();
    if(snapshot==null&&!loading)refresh();
  }
  @Override public void addNotify(){super.addNotify();SwingUtilities.invokeLater(()->{if(isShowing())setActive(true);});}
  @Override public void removeNotify(){setActive(false);super.removeNotify();}

  private void refresh() {
    if(!active)return;long request=++generation;loading=true;message("Loading exact Forever recipe records…");
    Async.runIf(worker,()->active&&generation==request,load,result->{
      loading=false;snapshot=result;updating=true;
      variants.setModel(new DefaultComboBoxModel<>(result.recipes().toArray(Recipe[]::new)));
      choices.setVisible(result.recipes().size()>1);
      if(restoredVariant!=null)for(Recipe recipe:result.recipes())if(recipe.variant().equals(restoredVariant)){variants.setSelectedItem(recipe);break;}
      updating=false;
      showRecipe();
    },failure->{
      loading=false;message("Crafting records could not be loaded. This is a query failure, not evidence that the item cannot be crafted.");
      JButton retry=new JButton("Retry crafting guide");retry.addActionListener(event->refresh());content.add(retry,BorderLayout.SOUTH);content.revalidate();sink.fail(failure);
    });
  }
  private void showRecipe() {
    if(snapshot==null)return;
    Recipe selected=(Recipe)variants.getSelectedItem();
    Snapshot displayed=selected==null?snapshot:new Snapshot(snapshot.snapshotId(),snapshot.build(),List.of(selected));
    rememberTeaching();if(teaching!=null)teaching.deactivate();teaching=null;teachingVariant=null;
    if(selected!=null&&teachingLoad!=null){
      teachingVariant=selected.variant();teaching=new JournalTeachingItems(()->teachingLoad.apply(selected.spellId()),worker,nav,sink,snapshot.snapshotId(),selected.spellId());
      teaching.restoreState(teachingStates.get(teachingVariant));
    }
    var page=new RecipeFieldGuidePanel(displayed,nav,teaching);content.removeAll();content.add(page);recipeScroll=null;
    for(Component child:page.getComponents())if(child instanceof JScrollPane pane){recipeScroll=pane;break;}
    content.revalidate();content.repaint();
    if(restoredScroll!=null&&recipeScroll!=null){int position=restoredScroll;restoredScroll=null;JScrollPane target=recipeScroll;SwingUtilities.invokeLater(()->{if(recipeScroll==target)target.getVerticalScrollBar().setValue(position);});}
  }
  private void message(String value){content.removeAll();content.add(JournalSurface.copy(value),BorderLayout.NORTH);content.revalidate();content.repaint();}
  private void rememberTeaching(){if(teaching!=null&&teachingVariant!=null)teachingStates.put(teachingVariant,teaching.saveState());}
  @Override public Object saveState(){rememberTeaching();Recipe selected=(Recipe)variants.getSelectedItem();return new State(selected==null?restoredVariant:selected.variant(),recipeScroll==null?(restoredScroll==null?0:restoredScroll):recipeScroll.getVerticalScrollBar().getValue(),teachingStates);}
  @Override public void restoreState(Object value){
    if(!(value instanceof State state))return;if(teaching!=null)teaching.deactivate();teaching=null;teachingVariant=null;teachingStates.clear();teachingStates.putAll(state.teaching());restoredVariant=state.variant();restoredScroll=state.scroll();
    if(snapshot!=null){updating=true;for(Recipe recipe:snapshot.recipes())if(recipe.variant().equals(restoredVariant)){variants.setSelectedItem(recipe);break;}updating=false;showRecipe();}
  }
}
