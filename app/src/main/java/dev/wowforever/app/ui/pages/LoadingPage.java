package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.ui.detail.HeaderPanel;
import dev.wowforever.app.ui.widgets.RailTarget;
import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * What the detail area shows while a page loads for longer than {@link dev.wowforever.app.ui.AppController#
 * LOADING_DELAY_MS} ms: the page's header, which needs no data (the name the list entry or the search hit already
 * had), over a muted line saying what is loading ({@code "Loading drops…"}).
 */
public final class LoadingPage extends JPanel {
  private final HeaderPanel header;
  private final JLabel what;

  /** The loading page of {@code title}, reading {@code what} under the header. */
  public LoadingPage(String title, String what) {
    super(new BorderLayout());
    header = new HeaderPanel(Objects.requireNonNull(title, "title"), "");
    this.what = PageContent.mutedLabel(Objects.requireNonNull(what, "what"));
    this.what.setBorder(BorderFactory.createEmptyBorder(4, 12, 12, 12));
    this.what.getAccessibleContext().setAccessibleName(what);
    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    top.add(header, BorderLayout.NORTH);
    top.add(this.what, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
  }

  /** The loading page of an entity of {@code kind} called {@code title}: its rail plate and kind word, if any. */
  public static LoadingPage of(EntityKind kind, String title) {
    LoadingPage page = new LoadingPage(title, what(kind));
    RailTarget.of(kind).ifPresent(t -> page.header.setPlate(SourcesPage.plateIcon(t)));
    page.header.setKindWord(kind.label());
    return page;
  }

  /** What loads for a page of {@code kind}: {@code "Loading drops…"} for an NPC. */
  public static String what(EntityKind kind) {
    return switch (kind) {
      case ITEM -> "Loading sources…";
      case NPC -> "Loading drops…";
      case OBJECT -> "Loading contents…";
      case QUEST -> "Loading quest…";
      case ZONE -> "Loading zone…";
      case INSTANCE -> "Loading loot…";
      case BOSS -> "Loading…";
    };
  }

  /** The name in the header. */
  public String title() {
    return header.title();
  }

  /** The loading line: {@code "Loading drops…"}. */
  public String what() {
    return what.getText();
  }

  public HeaderPanel header() {
    return header;
  }
}
