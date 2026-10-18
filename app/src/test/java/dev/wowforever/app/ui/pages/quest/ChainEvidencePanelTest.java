package dev.wowforever.app.ui.pages.quest;

import static org.assertj.core.api.Assertions.assertThat;
import dev.wowforever.app.model.QuestChain;
import dev.wowforever.app.model.QuestRelationEvidence;
import dev.wowforever.app.ui.detail.CompactColumns;
import dev.wowforever.app.ui.widgets.SourceInfoButton;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChainEvidencePanelTest {
  @Test void invalidUrlCannotOpenAndCollapsedCompactKeepsCoverageWarningVisible() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var current = ChainRowsTest.CURRENT;
      var before = ChainRowsTest.step(9, "Before", QuestChain.Connector.REQUIRES_ONE, 10L, -1);
      var evidence = new QuestRelationEvidence(10, 9, "pre_single", "allthethings-forever", "s", "q.lua",
          "javascript:alert(1)", "authored");
      var chain = new QuestChain(List.of(before, current), 1, List.of(before, current), false, List.of(evidence), 1);
      var panel = new ChainPanel(chain, t -> {}, uri -> { throw new AssertionError("unexpected opening"); });
      var root = new JPanel(); root.putClientProperty(CompactColumns.KEY, true); root.add(panel);
      panel.doLayout();
      assertThat(panel.isCollapsed()).isTrue();
      var warning = children(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
          .filter(t -> t.getText().equals("Some AllTheThings conditions were not imported.")).findFirst().orElseThrow();
      assertThat(warning.isVisible()).isTrue();
      assertThat(warning.getParent()).isSameAs(panel);
      panel.heading().doClick();
      var info=children(panel).stream().filter(SourceInfoButton.class::isInstance).map(SourceInfoButton.class::cast).findFirst().orElseThrow();
      var details=info.detailsContent();
      JButton source = children(details).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
          .filter(b -> b.getText().equals("Open original source ↗")).findFirst().orElseThrow();
      assertThat(source.isEnabled()).isFalse();
      source.doClick();
    });
  }

  @Test void unsupportedConditionsDoNotTurnMissingRelationshipsIntoClaimedAbsence() {
    var chain = new QuestChain(List.of(ChainRowsTest.CURRENT), 0, List.of(ChainRowsTest.CURRENT), false, List.of(), 2);
    assertThat(ChainRows.of(chain, java.util.Set.of(), false, false).getFirst().text())
        .isEqualTo("No prerequisites or follow-ups recorded.");
  }

  @Test void partialQualificationStaysVisibleAndRetainedSourceUrlOpensOnlyFromDetails()throws Exception{
    SwingUtilities.invokeAndWait(()->{
      var current=ChainRowsTest.CURRENT;var before=ChainRowsTest.step(9,"Before",QuestChain.Connector.REQUIRES_ONE,10L,-1);
      var claim=new QuestRelationEvidence(10,9,"pre_single","allthethings-forever","snapshot-7","q.lua","https://example.org/quest/10","partial");
      var chain=new QuestChain(List.of(before,current),1,List.of(before,current),false,List.of(claim));
      var opened=new ArrayList<java.net.URI>();var panel=new ChainPanel(chain,target->{},opened::add);
      var visible=children(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText).collect(java.util.stream.Collectors.joining("\n"));
      assertThat(visible).contains("Conditional requirements may be incomplete","not in-game verified").doesNotContain("snapshot-7");
      var info=children(panel).stream().filter(SourceInfoButton.class::isInstance).map(SourceInfoButton.class::cast).findFirst().orElseThrow();
      var details=info.detailsContent();assertThat(opened).isEmpty();
      var source=children(details).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(button->button.getText().endsWith(" ↗")).findFirst().orElseThrow();source.doClick();
      assertThat(opened).containsExactly(java.net.URI.create("https://example.org/quest/10"));
      assertThat(children(details).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).map(JTextArea::getText)).anyMatch(text->text.contains("snapshot-7")&&text.contains("q.lua"));
    });
  }

  private static List<Component> children(Container parent) {
    List<Component> out = new ArrayList<>();
    for (Component child : parent.getComponents()) {
      out.add(child);
      if (child instanceof Container container) out.addAll(children(container));
    }
    return out;
  }
}
