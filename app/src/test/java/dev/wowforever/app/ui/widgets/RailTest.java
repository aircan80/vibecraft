package dev.wowforever.app.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.Test;

class RailTest {

  @Test
  void sixButtonsWithTooltipsAndIcons() {
    Rail rail = new Rail();
    assertThat(rail.getOrientation()).isEqualTo(SwingConstants.VERTICAL);
    assertThat(rail.getComponentCount()).isEqualTo(6);
    assertThat(Arrays.stream(RailTarget.values()).map(t -> rail.button(t).getToolTipText()))
        .containsExactly("Home", "Items", "NPCs", "Quests", "Zones", "Dungeons & Raids");
    for (RailTarget t : RailTarget.values()) {
      JToggleButton b = rail.button(t);
      assertThat(rail.getComponentIndex(b)).isEqualTo(t.ordinal());
      assertThat(b.getAccessibleContext().getAccessibleName()).isEqualTo(t.label());
      assertThat(b.getIcon()).isInstanceOfSatisfying(FlatSVGIcon.class, i -> {
        assertThat(i.hasFound()).as(t.iconName()).isTrue();
        assertThat(i.getImage()).isNotNull();
      });
    }
  }

  @Test
  void clickingAButtonFiresItsTargetAndSelectsIt() {
    Rail rail = new Rail();
    List<RailTarget> fired = new ArrayList<>();
    rail.onSelect(fired::add);
    rail.button(RailTarget.ZONES).doClick(0);
    rail.button(RailTarget.ITEMS).doClick(0);
    assertThat(fired).containsExactly(RailTarget.ZONES, RailTarget.ITEMS);
    assertThat(rail.button(RailTarget.ITEMS).isSelected()).isTrue();
    assertThat(rail.button(RailTarget.ZONES).isSelected()).isFalse();
  }

  @Test
  void selectMarksWithoutFiring() {
    Rail rail = new Rail();
    List<RailTarget> fired = new ArrayList<>();
    rail.onSelect(fired::add);
    rail.select(RailTarget.QUESTS);
    assertThat(rail.button(RailTarget.QUESTS).isSelected()).isTrue();
    rail.select(null);
    assertThat(rail.button(RailTarget.QUESTS).isSelected()).isFalse();
    assertThat(fired).isEmpty();
  }

  @Test
  void railIs48LogicalPixelsWide() {
    assertThat(new Rail().getPreferredSize().width).isEqualTo(UIScale.scale(48));
  }

  @Test
  void everyIconRendersHeadless() {
    for (RailTarget t : RailTarget.values()) {
      FlatSVGIcon icon = Icons.icon(t.iconName(), 24);
      assertThat(icon.hasFound()).as(t.iconName()).isTrue();
      assertThat(icon.getIconWidth()).isEqualTo(UIScale.scale(24));
      assertThat(icon.getImage()).as(t.iconName()).isNotNull();
    }
  }

  @Test
  void unknownIconFails() {
    assertThatThrownBy(() -> Icons.icon("no-such-icon", 24)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-icon");
  }
}
