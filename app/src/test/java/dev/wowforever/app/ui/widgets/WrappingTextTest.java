package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wowforever.app.ui.pages.InstancePage;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WrappingTextTest {
  @Test void encounterNotesMeasureNaturallyAfterNarrowLayoutAndRepeatedResizes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JPanel column = new JPanel();
      column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
      column.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
      WrappingText caption = new WrappingText(InstancePage.ENCOUNTER_CAPTION, "Order");
      WrappingText note = new WrappingText(InstancePage.BOSSES_NOTE, "Source");
      column.add(caption); column.add(note);
      column.setSize(250, 700);
      // Simulate Swing's initial narrow allocation; preferred-size measurement must repair the view only.
      note.setBounds(0, 0, 1, 1000);
      Rectangle before = note.getBounds();
      int narrow = note.getPreferredSize().height;
      assertThat(note.getBounds()).isEqualTo(before);
      assertThat(narrow).isBetween(30, 200);
      column.invalidate(); column.doLayout();
      assertThat(note.getHeight()).isEqualTo(narrow);
      assertThat(caption.getHeight()).isLessThan(60);
      column.setSize(800, 700);
      int wide = note.getPreferredSize().height;
      column.invalidate(); column.doLayout();
      assertThat(wide).isLessThan(narrow);
      for (int i = 0; i < 3; i++) {
        column.setSize(250, 700); column.invalidate(); column.doLayout();
        assertThat(note.getPreferredSize().height).isEqualTo(narrow);
        assertThat(note.getHeight()).isEqualTo(narrow);
        column.setSize(800, 700); column.invalidate(); column.doLayout();
        assertThat(note.getPreferredSize().height).isEqualTo(wide);
        assertThat(note.getHeight()).isEqualTo(wide);
      }
    });
  }

  @Test void coverageNoteIncludesTextAndParentInsetsWithoutChangingBounds() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JPanel top = new JPanel(new BorderLayout());
      top.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 20));
      WrappingText coverage = new WrappingText(InstancePage.COVERAGE_LINE.formatted(7, 0, "1.60.1.70450"), "Coverage");
      coverage.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
      top.add(coverage, BorderLayout.CENTER);
      top.setSize(300, 200);
      top.doLayout();
      Rectangle before = coverage.getBounds();
      int height = coverage.getPreferredSize().height;
      assertThat(height).isBetween(30, 120);
      assertThat(coverage.getBounds()).isEqualTo(before);
      assertThat(coverage.getMinimumSize().height).isEqualTo(height);
      assertThat(coverage.getMaximumSize().height).isEqualTo(height);
    });
  }
}
