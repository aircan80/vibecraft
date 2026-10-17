package dev.wowforever.app.ui;

import java.util.Locale;

/** Shared TomTom waypoint formatting for tables and maps. */
public final class WayCommand {
  private WayCommand() {}

  public static String of(String zoneName, double x, double y) {
    return "/way " + (zoneName == null || zoneName.isBlank() ? "" : zoneName + " ")
        + String.format(Locale.ROOT, "%.1f %.1f", x, y);
  }
}
