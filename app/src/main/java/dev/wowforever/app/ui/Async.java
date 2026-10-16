package dev.wowforever.app.ui;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Runs work off the event dispatch thread and hands the result (or failure) back on it. With an executor that
 * runs the task on the calling thread (tests pass {@code Runnable::run}) the result is delivered inline, so
 * callers can assert right after the call.
 */
public final class Async {
  /** Runs each task in a {@link SwingWorker} (Swing's shared background pool). */
  public static final Executor SWING_WORKER = task -> new SwingWorker<Void, Void>() {
    @Override
    protected Void doInBackground() {
      task.run();
      return null;
    }
  }.execute();

  private Async() {}

  /** Runs {@code work} in a {@link SwingWorker}; {@code onEdt} or {@code onError} then runs on the EDT. */
  public static <T> void run(Supplier<T> work, Consumer<T> onEdt, Consumer<Exception> onError) {
    run(SWING_WORKER, work, onEdt, onError);
  }

  /**
   * Runs {@code work} on {@code worker}. When the worker ran it on another thread, {@code onEdt} (or
   * {@code onError}, for anything {@code work} throws) runs later on the EDT; when it ran on the calling
   * thread, the callback runs inline before this method returns.
   */
  public static <T> void run(Executor worker, Supplier<T> work, Consumer<T> onEdt, Consumer<Exception> onError) {
    Thread caller = Thread.currentThread();
    worker.execute(() -> {
      Runnable delivery;
      try {
        T value = work.get();
        delivery = () -> onEdt.accept(value);
      } catch (Throwable t) { // an Error (say, a native library failing to load) must not vanish in the worker
        Exception e = t instanceof Exception ex ? ex : new ExecutionException(t.toString(), t);
        delivery = () -> onError.accept(e);
      }
      if (Thread.currentThread() == caller) {
        delivery.run();
      } else {
        SwingUtilities.invokeLater(delivery);
      }
    });
  }
}
