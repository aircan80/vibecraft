package dev.wowforever.app.db;

import static org.assertj.core.api.Assertions.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NpcTurntablesTest {
  @Test void onlyTheVerifiedNpcAndExactForeverBuildAreSupported(){
    assertThat(NpcTurntables.load(247003,NpcTurntables.BUILD)).isEmpty();
    assertThat(NpcTurntables.load(2671,"1.15.9.70003")).isEmpty();
    assertThat(NpcTurntables.load(2671,"1.60.1.70451")).isEmpty();
  }
  @Test void everyBundledViewPassesPinnedManifestAndImageHashes(){
    var pack=NpcTurntables.load(2671,NpcTurntables.BUILD).orElseThrow();
    assertThat(pack.frames()).hasSize(12).allSatisfy(image->{assertThat(image.getWidth()).isEqualTo(768);assertThat(image.getHeight()).isEqualTo(768);});
    assertThat(pack.evidence()).contains("84bea4233331e390fda8724c6f91ffa5","126062","7937","30-degree","native bind pose");
    assertThat(pack.frame(-1)).isSameAs(pack.frames().get(11));
    assertThat(pack.frame(12)).isSameAs(pack.frames().getFirst());
    assertThat(NpcTurntables.load(2671,NpcTurntables.BUILD).orElseThrow()).isSameAs(pack);
  }
  @Test void decodingAndEvenCacheReadsAreRejectedOnTheEdt()throws Exception {
    SwingUtilities.invokeAndWait(()->assertThatThrownBy(()->NpcTurntables.load(2671,NpcTurntables.BUILD)).isInstanceOf(IllegalStateException.class));
  }
}
