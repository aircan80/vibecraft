package dev.wowforever.app.ui;

/** Classic-compatible links copied as plain text for pasting into the game. */
public final class GameLinks {
  private GameLinks() {}

  public static String item(long id, String name) {
    return "|Hitem:" + id + ":0:0:0:0:0:0:0:60|h[" + name + "]|h";
  }

  public static String quest(long id, int level, String name) {
    return "|Hquest:" + id + ":" + level + "|h[" + name + "]|h";
  }
}
