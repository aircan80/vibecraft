package dev.wowforever.app.ui.setup;

import java.nio.file.Path;
import java.util.*;

/** Unvalidated user choices, distinct from an executable pinned import plan. */
public record LibrarySetupDraft(String activeGame,Map<String,Profile> games) {
  public record Profile(Set<String> sources,Optional<Path> readOnlyGameFolder,boolean allowBlizzardCdn) {
    public Profile {sources=Set.copyOf(sources);readOnlyGameFolder=Objects.requireNonNull(readOnlyGameFolder);}
  }
  public LibrarySetupDraft {
    activeGame=Objects.requireNonNull(activeGame);games=Collections.unmodifiableMap(new LinkedHashMap<>(games));
    if(!games.isEmpty()&&!games.containsKey(activeGame))throw new IllegalArgumentException("The active game must be selected");
    games.forEach((id,profile)->{Objects.requireNonNull(id);Objects.requireNonNull(profile);});
  }
}
