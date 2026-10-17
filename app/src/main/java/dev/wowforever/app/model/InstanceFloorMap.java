package dev.wowforever.app.model;
import java.util.List;
/** Fully preloaded Atlas floor art and its independent, attributed legend. */
public record InstanceFloorMap(String floor, String label, byte[] jpeg, boolean forever, List<Legend> legend) {
  public InstanceFloorMap { jpeg=jpeg.clone(); legend=List.copyOf(legend); }
  @Override public byte[] jpeg() { return jpeg.clone(); }
  public record Legend(String number, String name, Long npcId) {}
}
