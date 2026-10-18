package dev.wowforever.app.ui.journal;

import dev.wowforever.app.ui.theme.Theme;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.ColorUIResource;

/** Journal-only explorer palette and readable control scale. Original startup never applies it. */
public final class JournalAppearance {
  private JournalAppearance() {}
  private static Color color(int value) { return new Color(value); }
  public static Theme palette(Theme previous) {
    boolean dark=previous.dark();
    Color base=color(dark?0x111719:0xf2eee5),surface=color(dark?0x192124:0xfaf7ef);
    Color raised=color(dark?0x202a2d:0xe8e4d9),line=color(dark?0x354043:0xc8c6ba);
    Color text=color(dark?0xe8e8de:0x263235),muted=color(dark?0xa6b4b6:0x566766);
    Color gold=color(dark?0xdab776:0x826025),accent=color(dark?0xa8d7cc:0x236c60);
    Color selected=color(dark?0x263d38:0xdce8de);
    return new Theme(dark,base,surface,raised,text,muted,gold,gold,accent,
        surface,line,previous.warn(),previous.plate(),line,
        previous.barStart(),previous.barEnd(),raised,selected);
  }
  public static void apply() {
    Theme theme=palette(Theme.current());Theme.setCurrent(theme);
    Font source=UIManager.getFont("Label.font");
    FontUIResource body=new FontUIResource(source==null?new Font(Font.SANS_SERIF,Font.PLAIN,14):source.deriveFont(14f));
    UIManager.getLookAndFeelDefaults().put("defaultFont",body);
    for(String kind:new String[]{"Label","Button","ToggleButton","CheckBox","RadioButton","ComboBox","TextField","TextArea","TextPane","Table","List","Tree","TabbedPane","Menu","MenuItem","CheckBoxMenuItem","PopupMenu"})UIManager.getLookAndFeelDefaults().put(kind+".font",body);
    for(String kind:new String[]{"Panel","Viewport","ScrollPane"})UIManager.getLookAndFeelDefaults().put(kind+".background",new ColorUIResource(theme.bgBase()));
    for(String kind:new String[]{"Label","Button","ToggleButton","CheckBox","RadioButton","ComboBox","TextField","TextArea","TextPane","Table","List","Tree","TabbedPane","MenuItem"})UIManager.getLookAndFeelDefaults().put(kind+".foreground",new ColorUIResource(theme.textPrimary()));
    for(String kind:new String[]{"Button","ToggleButton","ComboBox","List","Tree","Table"})UIManager.getLookAndFeelDefaults().put(kind+".background",new ColorUIResource(theme.bgSurface()));
    for(String kind:new String[]{"TextField","TextArea","TextPane"})UIManager.getLookAndFeelDefaults().put(kind+".background",new ColorUIResource(theme.bgBase()));
    for(String kind:new String[]{"List","Tree","Table"}) {
      UIManager.getLookAndFeelDefaults().put(kind+".selectionBackground",new ColorUIResource(theme.selection()));
      UIManager.getLookAndFeelDefaults().put(kind+".selectionForeground",new ColorUIResource(theme.textPrimary()));
    }
    UIManager.getLookAndFeelDefaults().put("Table.alternateRowColor",new ColorUIResource(theme.altRow()));
    UIManager.getLookAndFeelDefaults().put("Component.borderColor",new ColorUIResource(theme.hairline()));
    UIManager.getLookAndFeelDefaults().put("Component.focusColor",new ColorUIResource(theme.link()));
    UIManager.getLookAndFeelDefaults().put("Component.arc",8);
    UIManager.getLookAndFeelDefaults().put("Button.arc",8);
    UIManager.getLookAndFeelDefaults().put("Button.margin",new Insets(6,11,6,11));
  }
}
