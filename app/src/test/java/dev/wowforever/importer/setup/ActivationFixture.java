package dev.wowforever.importer.setup;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Reuses the importer's authored synthetic core projection, never a real-data readiness claim. */
public final class ActivationFixture {
  public record Built(SetupPlan plan, LibraryBuildJob.Candidate candidate) {}
  public static Built create(Path directory) throws Exception {
    Files.createDirectories(directory);
    var plan=LibraryJobFixtures.plan();
    var job=plan.prepareJob(directory.resolve("staging"),java.util.Optional.empty(),"fixture",java.util.List.of());
    String ingestion=LibraryJobFixtures.captureSynthetic(plan,job,LibraryJobFixtures.inputs(directory));
    Path database=job.paths().archiveDatabase();
    var validation=new LibraryCandidateValidator().validate(database,plan,ingestion,()->false);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(database)));
    return new Built(plan,new LibraryBuildJob.Candidate(database,database,directory.resolve("synthetic.log"),hash,validation));
  }
}
