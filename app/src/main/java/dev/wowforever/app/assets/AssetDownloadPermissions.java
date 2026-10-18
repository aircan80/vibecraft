package dev.wowforever.app.assets;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Active-catalog permission snapshot. Startup begins cache-only; database metadata never grants consent. */
public final class AssetDownloadPermissions {
  public enum Mode { CACHE_ONLY, MANAGED, LEGACY }
  public record Consent(Mode mode,boolean allowBlizzardCdn,boolean allowWagoExport,List<Path> readOnlyGameRoots) {
    public Consent {
      Objects.requireNonNull(mode);Objects.requireNonNull(readOnlyGameRoots);
      readOnlyGameRoots=readOnlyGameRoots.stream().map(path->path.toAbsolutePath().normalize()).distinct().toList();
      if(readOnlyGameRoots.size()>16 || readOnlyGameRoots.stream().anyMatch(path->path.toString().length()>1024))
        throw new IllegalArgumentException("Asset permission game-root list exceeds its bound");
      if(mode==Mode.CACHE_ONLY && (allowBlizzardCdn || allowWagoExport))
        throw new IllegalArgumentException("Cache-only policy cannot grant network access");
      if(mode==Mode.LEGACY && (!allowBlizzardCdn || !allowWagoExport || !readOnlyGameRoots.isEmpty()))
        throw new IllegalArgumentException("Legacy policy preserves the existing provider behavior");
    }
  }
  public static final Consent CACHE_ONLY=new Consent(Mode.CACHE_ONLY,false,false,List.of());
  public static final Consent LEGACY=new Consent(Mode.LEGACY,true,true,List.of());
  private record Active(Optional<Path> database,Consent consent) {}
  private static final AtomicReference<Active> ACTIVE=new AtomicReference<>(new Active(Optional.empty(),CACHE_ONLY));
  private AssetDownloadPermissions() {}

  public static Consent current() {return ACTIVE.get().consent();}
  /** Memory-only: setup management can capture the open database guard without SQL or hashing on the EDT. */
  public static Optional<Path> activeDatabase() {return ACTIVE.get().database();}
  public static void resetForCatalogResolution() {ACTIVE.set(new Active(Optional.empty(),CACHE_ONLY));}
  public static void configureManaged(Path database,Consent consent) {
    Objects.requireNonNull(consent);
    if(consent.mode()==Mode.LEGACY)throw new IllegalArgumentException("Managed selections require explicit permissions or cache-only policy");
    ACTIVE.set(new Active(Optional.of(database.toAbsolutePath().normalize()),consent));
  }
  /** Used only after opening an explicit/saved existing database or embedded legacy catalog. */
  public static void configureLegacy(Path database) {
    ACTIVE.set(new Active(Optional.of(database.toAbsolutePath().normalize()),LEGACY));
  }

  /** Returns the canonical app-owned cache destination, refusing game roots and aliases before any write. */
  public static Path checkCacheWrite(Path appCacheRoot,Path destination) throws IOException {
    Path owned=canonicalProspective(appCacheRoot),actual=canonicalProspective(destination);
    if(owned.getParent()==null || !actual.startsWith(owned))throw new IOException("Asset write is outside application cache storage");
    for(Path game:current().readOnlyGameRoots()) {
      Path protectedRoot=canonicalProspective(game);
      if(owned.startsWith(protectedRoot) || actual.startsWith(protectedRoot))
        throw new IOException("WoW folders are read-only; asset cache must be outside them");
    }
    for(Path parent=actual;parent!=null;parent=parent.getParent())
      if(Files.isRegularFile(parent.resolve(".build.info")))throw new IOException("Asset cache cannot be inside a WoW installation");
    return actual;
  }

  /** Resolves existing ancestors without creating directories; used off the EDT when capturing consent. */
  public static Path canonicalProspective(Path path) throws IOException {
    Path absolute=path.toAbsolutePath().normalize(),existing=absolute;
    while(existing!=null && !Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
    if(existing==null)throw new IOException("Asset path has no existing ancestor");
    return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
  }
}
