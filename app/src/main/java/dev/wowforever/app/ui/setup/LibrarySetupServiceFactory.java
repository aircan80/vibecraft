package dev.wowforever.app.ui.setup;

import dev.wowforever.app.assets.AssetDownloadPermissions;
import dev.wowforever.app.db.UserDataDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/** Pure construction for setup dialogs. The coordinator performs all capabilities, file and SQL work later. */
public final class LibrarySetupServiceFactory {
  private LibrarySetupServiceFactory() {}
  record Environment(Path storage,List<Path> protectedGameRoots,Optional<Path> activeDatabase) {
    Environment {Objects.requireNonNull(storage);protectedGameRoots=List.copyOf(protectedGameRoots);activeDatabase=Objects.requireNonNull(activeDatabase);}
  }
  static Environment snapshotEnvironment(){
    var assets=AssetDownloadPermissions.snapshot();
    return new Environment(UserDataDir.resolve(),assets.consent().readOnlyGameRoots(),assets.activeDatabase());
  }
  public static LibrarySetupService create(){
    return create(snapshotEnvironment(),context->new DefaultLibrarySetupService(context.storage(),context.protectedGameRoots(),context.activeDatabase()));
  }
  static LibrarySetupService create(Environment environment,Function<Environment,LibrarySetupService> builder){
    // Selected draft folders are added by SetupPlan when the reviewed job establishes its write boundary.
    return Objects.requireNonNull(builder.apply(Objects.requireNonNull(environment)));
  }
}
