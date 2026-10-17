package dev.wowforever.app.model;

/** A field's knowledge and its displayed value have separate meanings. */
public record FieldKnowledgeView(String field, State state, String value, String source, String version, String evidence) {
  public enum State {
    KNOWN("Known"), HIDDEN_BY_BLIZZARD("Hidden by Blizzard"), NOT_YET_DISCOVERED("Not yet discovered"),
    REPORTED("Reported"), UNANNOUNCED("Unannounced"), NONE("None");
    private final String label;
    State(String label){this.label=label;}
    public String label(){return label;}
  }
  public String label(){return switch(field){case "stats"->"Forever stats";case "classic_stats"->"Classic Era stats";case "name"->"Name";case "item_level"->"Item level";case "required_level"->"Required level";default->field.replace('_',' ');};}
  public String displayValue(){return value==null?state==State.NONE?"None":"Unknown":value;}
}
