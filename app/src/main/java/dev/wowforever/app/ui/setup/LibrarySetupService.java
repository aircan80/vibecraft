package dev.wowforever.app.ui.setup;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Blocking adapter contract. Production wiring is deliberately absent until the actual job is accepted. */
public interface LibrarySetupService {
  enum Mode { FIRST_RUN, MANAGEMENT }
  enum Phase {
    DOWNLOAD("Download selected sources"),PREPARE("Prepare library"),BUILD("Build catalogue"),
    CHECK("Check data"),TRIM("Trim download size"),OPEN("Open library");
    private final String label;Phase(String label){this.label=label;}public String label(){return label;}
  }
  /** Counts describe this phase only. -1 means not supplied; no overall percentage is inferred. */
  record Progress(Phase phase,String detail,long completed,long total,String unit) {
    public Progress {Objects.requireNonNull(phase);detail=Objects.requireNonNullElse(detail,"");unit=Objects.requireNonNullElse(unit,"");if(completed< -1||total< -1||total>=0&&completed>total)throw new IllegalArgumentException("Invalid phase counts");}
    public Progress(Phase phase,String detail){this(phase,detail,-1,-1,"");}
  }
  record Capabilities(Map<String,Set<String>> sourcesByGame) {
    public Capabilities {var copy=new HashMap<String,Set<String>>();sourcesByGame.forEach((game,sources)->copy.put(game,Set.copyOf(sources)));sourcesByGame=Map.copyOf(copy);}
    public List<String> blockers(LibrarySetupDraft draft){
      var result=new ArrayList<String>();if(draft.games().isEmpty())result.add("Choose at least one game version.");
      draft.games().forEach((game,profile)->{
        Set<String> supported=sourcesByGame.get(game);
        if(supported==null){result.add("The "+game+" import adapter is not available.");return;}
        if(profile.sources().isEmpty())result.add("Choose at least one source for "+game+".");
        for(String source:profile.sources())if(!supported.contains(source))result.add("The "+source+" source is not available for "+game+"; it will not be silently omitted.");
        if(profile.sources().contains("wago")&&!profile.sources().contains("native"))result.add("Wago exports require explicit native data selection.");
        if(profile.sources().contains("raidbots-hotfix")&&(!game.equals("forever")||!profile.sources().containsAll(Set.of("native","wago"))))result.add("Raidbots hotfix details require Forever, native data and explicit Wago exports.");
      });return List.copyOf(result);
    }
  }
  /** Adapter-owned immutable pinned plan/storage context. UI neither invents nor mutates it. */
  interface PreparedPlan {}
  record Review(LibrarySetupDraft draft,String summary,List<String> blockers,PreparedPlan plan) {
    public Review {Objects.requireNonNull(draft);summary=Objects.requireNonNullElse(summary,"");blockers=List.copyOf(blockers);if(blockers.isEmpty()&&plan==null)throw new IllegalArgumentException("A ready review needs a prepared plan");}
    public boolean ready(){return blockers.isEmpty()&&plan!=null;}
  }
  /** A validated, compact candidate. nextStartupRegistered is true only after durable registration succeeds. */
  record Result(Path database,String summary,boolean nextStartupRegistered) {
    public Result {Objects.requireNonNull(database);summary=Objects.requireNonNullElse(summary,"");}
  }
  record Control(BooleanSupplier cancellationRequested,Consumer<Progress> progress) {
    public Control {Objects.requireNonNull(cancellationRequested);Objects.requireNonNull(progress);}
    /** Check before/after each safe stage. Cancellation never promises rollback of a partial stage. */
    public void boundary(){if(cancellationRequested.getAsBoolean())throw new CancellationException("Stopped after the current step");}
  }
  /** A provider may supply a reviewed, user-facing failure message rather than exposing raw technical errors. */
  final class JobFailure extends Exception {
    public JobFailure(String message){super(Objects.requireNonNull(message));}
    public JobFailure(String message,Throwable cause){super(Objects.requireNonNull(message),cause);}
  }
  /** All three methods run on the coordinator's background worker, including dynamic capability inspection. */
  Capabilities capabilities() throws Exception;
  Review review(LibrarySetupDraft draft,Control control) throws Exception;
  /** Must preserve the current session. Management may register a validated path for the next startup only. */
  Result build(Review review,Mode mode,Control control) throws Exception;
}
