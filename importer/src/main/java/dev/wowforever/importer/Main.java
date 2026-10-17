package dev.wowforever.importer;

import dev.wowforever.importer.clientdata.ClientDataStage;
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
 * shows (see {@link #unsupportedCounters}), or when client data was imported but fewer than {@code --coverage-min}
 * percent (default 85) of the named items matched an ItemSparse row ({@link ClientDataStage#coverageTooLow}).
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
    if (args.length > 0 && args[0].equals("ingest-collector")) {
      if (args.length != 5 || !args[1].equals("--database") || !args[3].equals("--collector-file")) {
        err.println("Usage: ingest-collector --database <file.sqlite> --collector-file <SavedVariables.lua>"); return 1;
      }
      try {
        String id = CollectorArchiveWorkflow.ingest(java.nio.file.Path.of(args[2]),java.nio.file.Path.of(args[4]));
        out.println("Captured archive-only collector ingestion: " + id);
        out.println("No application facts projected; real-observation acceptance remains pending.");
        return 0;
      } catch (Exception failure) { err.println(failure.getMessage()); return 1; }
    }
    if (args.length > 0 && args[0].equals("sources")) {
      if (args.length != 3 || !args[1].equals("--database")) {
        err.println("Usage: sources --database <file.sqlite>"); return 1;
      }
      try (var c = archiveConnection(args[2])) {
        for (var snapshot : dev.wowforever.importer.archive.SourceArchive.snapshots(c))
          out.println(snapshot.id()+"\t"+snapshot.metadata().source()+"\t"+snapshot.metadata().version()+"\t"+snapshot.capturedAt());
        return 0;
      } catch (Exception e) { e.printStackTrace(err); return 1; }
    }
    if (args.length > 0 && args[0].equals("diff")) {
      if (args.length != 7 || !args[1].equals("--database") || !args[3].equals("--before") || !args[5].equals("--after")) {
        err.println("Usage: diff --database <file.sqlite> --before <snapshot-id> --after <snapshot-id>"); return 1;
      }
      try (var c = archiveConnection(args[2])) {
        var before=dev.wowforever.importer.archive.SourceArchive.snapshot(c,args[4]);
        var after=dev.wowforever.importer.archive.SourceArchive.snapshot(c,args[6]);
        if (!before.metadata().source().equals(after.metadata().source()))
          throw new IllegalArgumentException("Compare snapshots of the same source");
        for (var change : dev.wowforever.importer.archive.SourceArchive.diff(c,args[4],args[6]))
          out.println("file\t"+change.kind()+"\t"+change.path());
        for (var change : dev.wowforever.importer.archive.records.SourceRecords.diff(c,args[4],args[6]))
          out.println("record\t"+change.kind()+"\t"+change.path()+"\t"+change.key());
        for (var change : dev.wowforever.importer.archive.records.SourceRecords.fieldDiff(c,args[4],args[6]))
          out.println("field\t"+change.kind()+"\t"+change.path()+"\t"+change.key()+"\t"+change.fieldPath()+"\t"
              +java.util.Objects.toString(change.beforePayload(),"<absent>")+"\t"+java.util.Objects.toString(change.afterPayload(),"<absent>"));
        return 0;
      } catch (Exception e) { e.printStackTrace(err); return 1; }
    }
    if (args.length > 0 && args[0].equals("project")) {
      if (args.length != 5 || !args[1].equals("--database") || !args[3].equals("--ingestion")) {
        err.println("Usage: project --database <file.sqlite> --ingestion <id>"); return 1;
      }
      try { return ArchiveWorkflow.project(java.nio.file.Path.of(args[2]), args[4], out, err); }
      catch (Exception e) { e.printStackTrace(err); return 1; }
    }
    boolean captureAtt = args.length > 0 && args[0].equals("ingest-att");
    boolean captureOnly = args.length > 0 && args[0].equals("ingest");
    if (captureOnly || captureAtt) args = Arrays.copyOfRange(args, 1, args.length);
    ImportOptions options;
    try {
      options = ImportOptions.parse(args);
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      return 1;
    }
    try {
      String ingestion = captureAtt ? LegacyAttWorkflow.ingest(options) : ArchiveWorkflow.ingest(options);
      out.println("Captured ingestion: " + ingestion);
      if (captureOnly || captureAtt) return 0;
      int result = ArchiveWorkflow.project(options.out(), ingestion, out, err);
      if (result == 0 && !options.buildIdOut().toAbsolutePath().equals(ImportOptions.defaultBuildIdOut(options.out()))) {
        try {
          java.nio.file.Files.createDirectories(options.buildIdOut().toAbsolutePath().getParent());
          java.nio.file.Files.copy(ImportOptions.defaultBuildIdOut(options.out()), options.buildIdOut(),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException failure) {
          err.println("Database published successfully; could not export custom build ID: " + failure.getMessage());
        }
      }
      return result;
    } catch (Exception e) {
      e.printStackTrace(err);
      return 1;
    }
  }

  private static java.sql.Connection archiveConnection(String file) throws java.sql.SQLException {
    var config = new org.sqlite.SQLiteConfig();
    config.setReadOnly(true);
    return java.sql.DriverManager.getConnection("jdbc:sqlite:"+java.nio.file.Path.of(file).toAbsolutePath(), config.toProperties());
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
