package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.Main;
import dev.wowforever.app.db.FixtureDb;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FixtureDb.class)
class MainSelfTestTest {

  @FunctionalInterface
  interface Body {
    void run() throws Exception;
  }

  @Test
  void selfTestPrintsCounts() {
    String out = captureStdout(() -> Main.selfTest(FixtureDb.path()));
    assertThat(out).contains("items=").contains("instances=3");
  }

  @Test
  void selfTestPrintsOneLineWithBuildIdAndReturnsZero() {
    AtomicInteger code = new AtomicInteger(-1);
    String out = captureStdout(() -> code.set(Main.selfTest(FixtureDb.path())));
    assertThat(code).hasValue(0);
    assertThat(out.strip()).matches("items=[1-9]\\d* npcs=[1-9]\\d* instances=3 build_id=\\S+")
        .endsWith("build_id=" + FixtureDb.buildId());
  }

  private static String captureStdout(Body body) {
    PrintStream original = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    try {
      body.run();
    } catch (Exception e) {
      throw new AssertionError(e);
    } finally {
      System.setOut(original);
    }
    return buf.toString(StandardCharsets.UTF_8);
  }
}
