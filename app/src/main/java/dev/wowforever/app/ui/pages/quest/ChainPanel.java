package dev.wowforever.app.ui.pages.quest;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestChain.Step;
import dev.wowforever.app.model.QuestRelationEvidence;
import dev.wowforever.app.ui.widgets.WrappingText;
import dev.wowforever.app.ui.widgets.SourceInfoButton;
import dev.wowforever.app.ui.widgets.SourceEvidencePanel;
import java.net.URI;
import java.util.function.Consumer;
import dev.wowforever.app.nav.Navigator;
import dev.wowforever.app.nav.ViewTarget;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.HierarchyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Interactive chain tree. Expansion only rebuilds its view model, without reading the database. */
public final class ChainPanel extends JPanel {
  private final QuestChain chain;
  private final Navigator nav;
  private final Consumer<URI> sourceOpener;
  private final Set<String> expandedFans = new HashSet<>();
  private final JPanel body = new JPanel();
  private final JButton heading = new JButton();
  private boolean expandEarlier;
  private boolean expandLater;
  private boolean compact;
  private boolean collapsed;
  private List<ChainRows.Row> rows = List.of();

  public ChainPanel(QuestChain chain, Navigator nav) {
    this(chain, nav, ChainPanel::browse);
  }

  public ChainPanel(QuestChain chain, Navigator nav, Consumer<URI> sourceOpener) {
    super(new BorderLayout());
    this.chain = chain;
    this.nav = nav;
    this.sourceOpener = sourceOpener;
    setOpaque(false);
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    heading.setHorizontalAlignment(JButton.LEADING);
    heading.addActionListener(e -> {
      collapsed = !collapsed;
      updateVisibility();
    });
    add(heading, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    if (chain.attUnsupportedConditions() > 0) {
      var warning = new WrappingText("Some AllTheThings conditions were not imported.", "AllTheThings import coverage");
      warning.setToolTipText("Dataset-wide coverage note; this does not establish whether this quest has additional requirements.");
      add(warning, BorderLayout.SOUTH);
    }
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
        updateCompact();
      }
    });
    rebuild();
  }

  /** The displayed model, including expansion controls, independent of compact section visibility. */
  public List<ChainRows.Row> rows() { return rows; }
  public JButton heading() { return heading; }
  public boolean isCollapsed() { return collapsed; }

  public void previous() { chain.previous().ifPresent(this::open); }
  public void next() { chain.next().ifPresent(this::open); }

  private void open(Step step) {
    if (step.name() != null) {
      nav.open(new ViewTarget.Entity(step.ref()));
    }
  }

  private void updateCompact() {
    boolean now = CompactColumns.within(this);
    if (now != compact) {
      compact = now;
      collapsed = now;
      updateVisibility();
    }
  }

  private void updateVisibility() {
    heading.setText("Quest chain (" + chain.steps().size() + ")");
    heading.getAccessibleContext().setAccessibleName(heading.getText() + (collapsed ? ", collapsed" : ", expanded"));
    heading.setVisible(compact && (!chain.standalone() || chain.attUnsupportedConditions() > 0) && !chain.isEmpty());
    body.setVisible(!collapsed);
    setVisible(!chain.isEmpty() && !(compact && chain.standalone() && chain.attUnsupportedConditions() == 0));
    revalidate();
    repaint();
  }

  @Override
  public void doLayout() {
    updateCompact();
    super.doLayout();
  }

  private void rebuild() {
    rows = ChainRows.of(chain, expandedFans, expandEarlier, expandLater);
    body.removeAll();
    for (ChainRows.Row row : rows) {
      JPanel line = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (row.kind() == ChainRows.Kind.CURRENT) {
            g.setColor(Theme.current().accentGold());
            g.fillRect(0, 0, UIScale.scale(3), getHeight());
          }
        }
      };
      line.setOpaque(false);
      line.setBorder(BorderFactory.createEmptyBorder(UIScale.scale(3), UIScale.scale(8 + 16 * row.indent()),
          UIScale.scale(3), 0));
      line.setAlignmentX(Component.LEFT_ALIGNMENT);
      boolean action = row.kind() == ChainRows.Kind.FAN || row.kind() == ChainRows.Kind.SHOW_EARLIER
          || row.kind() == ChainRows.Kind.SHOW_LATER
          || row.kind() == ChainRows.Kind.STEP && row.step().name() != null;
      if (action) {
        JButton link = new JButton(row.text());
        link.setHorizontalAlignment(JButton.LEADING);
        link.setBorderPainted(false);
        link.setContentAreaFilled(false);
        link.setBorder(BorderFactory.createEmptyBorder());
        link.addActionListener(e -> {
          switch (row.kind()) {
            case FAN -> expandedFans.add(row.fanKey());
            case SHOW_EARLIER -> expandEarlier = true;
            case SHOW_LATER -> expandLater = true;
            case STEP -> open(row.step());
            default -> { }
          }
          if (row.kind() != ChainRows.Kind.STEP) {
            rebuild();
          }
        });
        line.add(link);
      } else {
        JLabel label = new JLabel(row.text()) {
          @Override
          public Color getForeground() {
            return row.kind() == ChainRows.Kind.CURRENT
                ? Theme.current().textPrimary() : Theme.current().textMuted();
          }
        };
        if (row.kind() == ChainRows.Kind.CURRENT) {
          label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        line.add(label);
      }
      if (row.step() != null && row.step().parentQuestId() != null) {
        var evidence = chain.evidenceFor(row.step());
        if (!evidence.isEmpty()) {
          String providers=evidence.stream().map(QuestRelationEvidence::sourceLabel).distinct().collect(java.util.stream.Collectors.joining(", "));
          boolean partial=evidence.stream().anyMatch(QuestRelationEvidence::partial);
          String qualification=partial?"Conditional requirements may be incomplete; not in-game verified":"Authored relationship; not in-game verified";
          var info=new SourceInfoButton("quest connection "+row.step().questId()+" / "+row.step().parentQuestId(),providers+" · "+qualification,()->relationshipDetails(evidence,sourceOpener));
          info.setName("quest-relationship-source-info");line.add(info,BorderLayout.EAST);
          var warning=new WrappingText(qualification,"Quest relationship qualification");warning.setFont(warning.getFont().deriveFont(18f));line.add(warning,BorderLayout.SOUTH);
        }
      }
      line.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
      body.add(line);
    }
    if (!chain.evidence().isEmpty()) {
      body.add(new WrappingText("Relationship assertions are authored source data; not in-game verified.",
          "Quest relationship provenance"));
    }
    updateVisibility();
  }

  static JPanel relationshipDetails(List<QuestRelationEvidence> evidence,Consumer<URI> sourceOpener){
    var content=new SourceEvidencePanel(List.of(),"Evidence applies only to the recorded quest relationships below. It does not establish every quest field or a complete set of access requirements.","");
    for(var source:List.copyOf(evidence)){
      var facts=new SourceEvidencePanel(List.of(
          new SourceEvidencePanel.Fact("Source",source.sourceLabel()),
          new SourceEvidencePanel.Fact("Applies to","Quest #"+source.questId()+" · "+source.kind()+" · quest #"+source.relatedId()),
          new SourceEvidencePanel.Fact("Separate build",null),
          new SourceEvidencePanel.Fact("Refreshed",null)),source.qualification(),
          "Snapshot: "+recorded(source.snapshotId())+"\nArtifact: "+recorded(source.artifactPath())+"\nSource URL: "+recorded(source.sourceUrl()));
      var open=new JButton("Open original source ↗");open.setFont(open.getFont().deriveFont(18f));open.setAlignmentX(Component.LEFT_ALIGNMENT);open.setEnabled(source.uri().isPresent());open.setToolTipText(source.uri().isPresent()?"Open the recorded source in your browser":"No valid HTTP(S) source URL was recorded.");
      open.getAccessibleContext().setAccessibleName("Open original "+source.sourceLabel()+" source for quest relationship "+source.questId()+" "+source.kind()+" "+source.relatedId());
      var failure=new WrappingText("","Source link status");failure.setFont(failure.getFont().deriveFont(18f));failure.setVisible(false);
      open.addActionListener(event->source.uri().ifPresent(uri->{try{sourceOpener.accept(uri);}catch(RuntimeException problem){failure.setText("The original source could not be opened.");failure.setVisible(true);content.revalidate();content.repaint();}}));
      facts.add(open);facts.add(failure);content.add(facts);content.add(javax.swing.Box.createVerticalStrut(14));
    }
    return content;
  }
  private static String recorded(String value){return value==null||value.isBlank()?"Not recorded":value;}

  private static void browse(URI uri) {
    if (!java.awt.Desktop.isDesktopSupported()
        || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
      throw new IllegalStateException("Browser opening is unavailable");
    }
    try { java.awt.Desktop.getDesktop().browse(uri); }
    catch (java.io.IOException e) { throw new IllegalStateException(e.getMessage(), e); }
  }
}
