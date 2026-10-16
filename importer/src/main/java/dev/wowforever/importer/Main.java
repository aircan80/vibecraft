package dev.wowforever.importer;

import dev.wowforever.importer.report.ImportReport;
import java.io.PrintStream;

/**
 * Command-line entry point. Exit codes: 0 success, 1 bad arguments or a failed import, 2 when
 * {@code --fail-on-unsupported} is given and a skip lost data the UI shows.
 */
public final class Main {
  /**
   * When this system property is {@code true} (as in the {@code import-data} Maven profile, which runs
   * inside the Maven JVM) a non-zero exit code is thrown as an exception instead of exiting the JVM.
   */
  static final String NO_EXIT_PROPERTY = "wowforever.importer.noExit";

  private Main() {}

  public static void main(String[] args) {
    int code = run(args, System.out, System.err);
    if (Boolean.getBoolean(NO_EXIT_PROPERTY)) {
      if (code != 0) {
        throw new IllegalStateException("import failed with exit code " + code);
      }
      return;
    }
    System.exit(code);
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    ImportOptions options;
    try {
      options = ImportOptions.parse(args);
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      return 1;
    }
    try {
      ImportReport report = ImportPipeline.run(options, out);
      long uiSkips = report.skipsOnUiFields();
      int code = exitCode(options.failOnUnsupported(), uiSkips);
      if (code != 0) {
        err.println(uiSkips + " skip(s) lost data shown in the UI; failing because of --fail-on-unsupported");
      }
      return code;
    } catch (Exception e) {
      e.printStackTrace(err);
      return 1;
    }
  }

  static int exitCode(boolean failOnUnsupported, long skipsOnUiFields) {
    return failOnUnsupported && skipsOnUiFields > 0 ? 2 : 0;
  }
}
