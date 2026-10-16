package dev.wowforever.importer;

import dev.wowforever.importer.entity.EntityKind;
import dev.wowforever.importer.report.ImportReport;
import dev.wowforever.importer.report.UiFields;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Command-line entry point. Exit codes: 0 success, 1 bad arguments or a failed import, 2 when
 * {@code --fail-on-unsupported} is given and a skip, a missing field or an unresolved value lost data the UI
 * shows (see {@link #unsupportedCounters}).
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
      long unsupported = unsupportedCounters(report);
      int code = exitCode(options.failOnUnsupported(), uiSkips + unsupported);
      if (code != 0) {
        err.println(uiSkips + " skip(s) and " + unsupported + " missingField/unresolved counter(s) lost data"
            + " shown in the UI; failing because of --fail-on-unsupported");
      }
      return code;
    } catch (Exception e) {
      e.printStackTrace(err);
      return 1;
    }
  }

  /**
   * The counters {@code --fail-on-unsupported} also fails on: every {@code missingField.<kind>.<field>} (an
   * upstream rename the writer read as nil) and every {@code unresolved.<kind>.<field>} whose field the UI
   * shows ({@link UiFields#isUiField}). Counters for kinds that are not entity kinds are ignored.
   */
  static long unsupportedCounters(ImportReport report) {
    long total = 0;
    for (Map.Entry<String, Long> e : report.counters().entrySet()) {
      String[] parts = e.getKey().split("\\.", 3);
      if (e.getValue() == 0 || parts.length != 3) {
        continue;
      }
      if (parts[0].equals("missingField")) {
        total += e.getValue();
      } else if (parts[0].equals("unresolved")) {
        Optional<EntityKind> kind = Arrays.stream(EntityKind.values())
            .filter(k -> k.name().equalsIgnoreCase(parts[1])).findFirst();
        if (kind.isPresent() && UiFields.isUiField(kind.get(), parts[2])) {
          total += e.getValue();
        }
      }
    }
    return total;
  }

  static int exitCode(boolean failOnUnsupported, long skipsOnUiFields) {
    return failOnUnsupported && skipsOnUiFields > 0 ? 2 : 0;
  }
}
