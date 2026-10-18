package dev.wowforever.app.ui;

/** Journal is the only launchable shell. The legacy marker remains for data/test compatibility. */
public enum UiExperience {
  /** Retired; never accepted by application startup or MainFrame. */
  @Deprecated
  CLASSIC,
  JOURNAL
}
