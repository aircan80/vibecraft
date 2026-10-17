package dev.wowforever.app.ui;

/** Plain text put into the HTML of tooltips and labels. */
public final class HtmlText {
  private HtmlText() {}

  /** {@code s} with {@code &}, {@code <} and {@code >} escaped, so it shows as written inside HTML. */
  public static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
