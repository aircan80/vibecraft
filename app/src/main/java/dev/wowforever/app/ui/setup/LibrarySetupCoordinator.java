package dev.wowforever.app.ui.setup;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** One bounded background job. Closing/invalidation prevents all stale callbacks and automatic activation. */
public final class LibrarySetupCoordinator implements AutoCloseable {
  public enum State { IDLE,REVIEWING,REVIEWED,BLOCKED,BUILDING,CANCELLING,CANCELLED,FAILED,READY,CLOSED }
  public record Snapshot(State state,LibrarySetupDraft draft,LibrarySetupService.Review review,
      LibrarySetupService.Progress progress,LibrarySetupService.Result result,String message) {}
  private final LibrarySetupService service;
  private final LibrarySetupService.Mode mode;
  private final Consumer<Snapshot> listener;
  private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var thread=new Thread(r,"vibecraft-library-setup");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
  private AtomicBoolean cancellation=new AtomicBoolean();
  private long generation;
  private Snapshot snapshot=new Snapshot(State.IDLE,null,null,null,null,"");
  public LibrarySetupCoordinator(LibrarySetupService service,LibrarySetupService.Mode mode,Consumer<Snapshot> listener){this.service=Objects.requireNonNull(service);this.mode=Objects.requireNonNull(mode);this.listener=Objects.requireNonNull(listener);}
  public Snapshot snapshot(){edt();return snapshot;}
  public void review(LibrarySetupDraft draft){
    edt();if(snapshot.state()==State.CLOSED)return;if(busyBuild())throw new IllegalStateException("A library build is already running");
    invalidate();long token=generation;AtomicBoolean stop=cancellation;
    set(new Snapshot(State.REVIEWING,draft,null,null,null,"Checking selected sources and requirements…"));
    submit(token,()->{
      var control=control(token,stop);control.boundary();var blockers=service.capabilities().blockers(draft);control.boundary();
      var reviewed=blockers.isEmpty()?service.review(draft,control):new LibrarySetupService.Review(draft,"Selected sources need attention.",blockers,null);
      if(!reviewed.draft().equals(draft))throw new IllegalStateException("The reviewed source choices do not match this draft");
      control.boundary();finish(token,stop,()->set(new Snapshot(reviewed.ready()?State.REVIEWED:State.BLOCKED,draft,reviewed,null,null,reviewed.summary())));
    });
  }
  public void build(LibrarySetupDraft draft){
    edt();if(snapshot.state()!=State.REVIEWED||snapshot.review()==null||!snapshot.review().ready()||!draft.equals(snapshot.draft()))throw new IllegalStateException("Check the current choices before building");
    var review=snapshot.review();long token=++generation;AtomicBoolean stop=cancellation=new AtomicBoolean();
    set(new Snapshot(State.BUILDING,draft,review,null,null,"Starting the selected library job…"));
    submit(token,()->{var control=control(token,stop);control.boundary();var result=service.build(review,mode,control);if(!result.nextStartupRegistered())control.boundary();finish(token,stop,result.nextStartupRegistered(),()->set(new Snapshot(State.READY,draft,review,snapshot.progress(),result,mode==LibrarySetupService.Mode.MANAGEMENT?(result.nextStartupRegistered()?"Ready for next startup":"Library built; next-startup registration is not confirmed."):"Your library is ready.")));});
  }
  public void cancel(){edt();if(snapshot.state()!=State.REVIEWING&&!busyBuild())return;cancellation.set(true);set(new Snapshot(State.CANCELLING,snapshot.draft(),snapshot.review(),snapshot.progress(),null,"Stopping after the current step. The current step will finish safely; your working library has not changed."));}
  public void invalidate(){
    edt();if(snapshot.state()==State.CLOSED)return;cancellation.set(true);generation++;executor.getQueue().clear();cancellation=new AtomicBoolean();set(new Snapshot(State.IDLE,null,null,null,null,""));
  }
  private boolean busyBuild(){return snapshot.state()==State.BUILDING||snapshot.state()==State.CANCELLING;}
  private LibrarySetupService.Control control(long token,AtomicBoolean stop){return new LibrarySetupService.Control(stop::get,progress->publish(token,()->{if(snapshot.state()==State.REVIEWING||snapshot.state()==State.BUILDING||snapshot.state()==State.CANCELLING)set(new Snapshot(snapshot.state(),snapshot.draft(),snapshot.review(),progress,null,snapshot.message()));}));}
  @FunctionalInterface private interface Job {void run()throws Exception;}
  private void submit(long token,Job job){try{executor.execute(()->{try{job.run();}catch(CancellationException stopped){publish(token,()->set(new Snapshot(State.CANCELLED,snapshot.draft(),snapshot.review(),snapshot.progress(),null,"Stopped safely. Verified downloads may be reused; the catalogue is rebuilt and checked again, not resumed from a partial projection.")));}catch(Exception failure){publish(token,()->set(new Snapshot(State.FAILED,snapshot.draft(),snapshot.review(),snapshot.progress(),null,failure instanceof LibrarySetupService.JobFailure?failure.getMessage():"The library could not finish. Your working library has not changed. Review the selected sources and retry.")));}});}catch(RejectedExecutionException busy){set(new Snapshot(State.FAILED,snapshot.draft(),null,null,null,"A previous check is still finishing. Please retry."));}}
  private void finish(long token,AtomicBoolean stop,Runnable action){finish(token,stop,false,action);}
  /** Confirmed atomic publication wins a late stop request; closing still suppresses callbacks. */
  private void finish(long token,AtomicBoolean stop,boolean registered,Runnable action){publish(token,()->{if(stop.get()&&!registered)set(new Snapshot(State.CANCELLED,snapshot.draft(),snapshot.review(),snapshot.progress(),null,"Stopped safely after the current step. The catalogue is rebuilt and checked again on retry."));else action.run();});}
  private void publish(long token,Runnable action){SwingUtilities.invokeLater(()->{if(snapshot.state()!=State.CLOSED&&generation==token)action.run();});}
  private void set(Snapshot value){snapshot=value;listener.accept(value);}
  @Override public void close(){edt();cancellation.set(true);generation++;executor.getQueue().clear();executor.shutdown();snapshot=new Snapshot(State.CLOSED,null,null,null,null,"");}
  private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Setup coordinator control must run on the EDT");}
}
