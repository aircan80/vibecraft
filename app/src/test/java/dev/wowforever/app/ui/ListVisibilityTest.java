package dev.wowforever.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListVisibilityTest {

  @Test
  void previewNeverCollapses() {
    var list = new ListVisibility(false);
    list.resultsShown();
    list.previewed();
    assertThat(list.visible()).isTrue();
  }

  @Test
  void openingAResultCollapses() {
    var list = new ListVisibility(false);
    list.resultsShown();
    list.resultOpened(1280);
    assertThat(list.visible()).isFalse();
    assertThat(list.pinned()).isFalse();
  }

  @Test
  void ctrlBKeepsTheListOpen() {
    var list = new ListVisibility(false);
    assertThat(list.visible()).as("unpinned: hidden until a search").isFalse();
    list.toggle();
    assertThat(list.visible()).isTrue();
    assertThat(list.pinned()).isTrue();
    list.resultOpened(1280);
    assertThat(list.visible()).as("pinned").isTrue();
    list.toggle();
    assertThat(list.visible()).isFalse();
    assertThat(list.pinned()).isFalse();
  }

  @Test
  void wideWindowsKeepTheList() {
    var list = new ListVisibility(false);
    list.resultsShown();
    list.resultOpened(ListVisibility.WIDE_PX);
    assertThat(list.visible()).isTrue();
    list.resultOpened(ListVisibility.WIDE_PX - 1);
    assertThat(list.visible()).isFalse();
    assertThat(ListVisibility.WIDE_PX).isEqualTo(1600);
  }

  @Test
  void searchingShowsTheList() {
    var list = new ListVisibility(false);
    list.resultsShown();
    list.resultOpened(1280);
    assertThat(list.visible()).isFalse();
    list.resultsShown();
    assertThat(list.visible()).isTrue();
    assertThat(list.pinned()).as("showing results is not a pin").isFalse();
  }

  @Test
  void aSavedPinStartsShownAndStaysPinned() {
    var list = new ListVisibility(true);
    assertThat(list.visible()).isTrue();
    assertThat(list.pinned()).isTrue();
    list.resultOpened(800);
    assertThat(list.visible()).isTrue();
  }

  @Test
  void ctrlBOnAListASearchShowedHidesIt() {
    var list = new ListVisibility(false);
    list.resultsShown();
    list.toggle();
    assertThat(list.visible()).isFalse();
    assertThat(list.pinned()).isFalse();
  }
}
