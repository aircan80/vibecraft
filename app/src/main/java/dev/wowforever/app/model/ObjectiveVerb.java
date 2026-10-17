package dev.wowforever.app.model;

/** Action words for Questie's objective kinds, including text-only extra objectives. */
public enum ObjectiveVerb {
  KILL("Kill"), COLLECT("Collect"), USE("Use"), EXPLORE("Explore"), REPUTATION("Reputation"),
  CAST("Cast"), ESCORT("Escort");

  private final String word;

  ObjectiveVerb(String word) {
    this.word = word;
  }

  public String word() {
    return word;
  }

  public static ObjectiveVerb of(String kind, String text) {
    if (kind == null) {
      return EXPLORE;
    }
    return switch (kind) {
      case "creature" -> KILL;
      case "object" -> USE;
      case "item" -> COLLECT;
      case "reputation" -> REPUTATION;
      case "spell" -> CAST;
      case "explore" -> text != null && text.startsWith("Use ") ? USE
          : text != null && (text.startsWith("Escort ") || text.startsWith("Protect ")) ? ESCORT : EXPLORE;
      default -> EXPLORE;
    };
  }
}
