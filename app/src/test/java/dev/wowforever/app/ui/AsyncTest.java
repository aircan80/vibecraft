package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AsyncTest {

  @Test
  void synchronousExecutorDeliversInline() {
    List<String> got = new ArrayList<>();
    Async.run(Runnable::run, () -> "done", got::add, e -> got.add("error"));
    assertThat(got).containsExactly("done");
  }

  @Test
  void failuresGoToOnError() {
    AtomicReference<Exception> err = new AtomicReference<>();
    Async.<String>run(Runnable::run, () -> {
      throw new IllegalStateException("boom");
    }, v -> {}, err::set);
    assertThat(err.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
  }

  @Test
  void backgroundWorkIsDeliveredOnTheEdt() throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      CountDownLatch done = new CountDownLatch(1);
      AtomicBoolean workOnEdt = new AtomicBoolean(true);
      AtomicBoolean deliveredOnEdt = new AtomicBoolean(false);
      Async.run(pool, () -> {
        workOnEdt.set(SwingUtilities.isEventDispatchThread());
        return 1;
      }, v -> {
        deliveredOnEdt.set(SwingUtilities.isEventDispatchThread());
        done.countDown();
      }, e -> done.countDown());
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(workOnEdt).isFalse();
      assertThat(deliveredOnEdt).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void swingWorkerFlavourDeliversOnTheEdt() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicBoolean deliveredOnEdt = new AtomicBoolean(false);
    Async.run(() -> 1, v -> {
      deliveredOnEdt.set(SwingUtilities.isEventDispatchThread());
      done.countDown();
    }, e -> done.countDown());
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(deliveredOnEdt).isTrue();
  }
}
