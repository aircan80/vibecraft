package dev.wowforever.app.ui.pages;

import dev.wowforever.app.model.ForeverContent.*;
import dev.wowforever.app.nav.*;
import dev.wowforever.app.ui.widgets.*;
import java.awt.FlowLayout;
import java.util.Comparator;
import javax.swing.*;

/** Classic and Forever instances together; discovery evidence is separately qualified. */
public final class DungeonLadderPage extends ForeverPageLayout {
  public DungeonLadderPage(Hub data,Navigator nav) {
    super("Dungeons & raids");
    text("Classic and Forever content, ordered by recorded minimum level. Opening dates appear only with a cited announcement.");
    data.instances().stream().sorted(Comparator.comparing(i->i.levelMin()==null?Integer.MAX_VALUE:i.levelMin())).forEach(i->{
      JPanel row=new JPanel();row.setLayout(new PageContent.TopStack());
      row.add(LinkButton.create(i.displayName(),i.ref(),nav));
      row.add(new WrappingText(i.contentType()+" · "+(i.tunedMinLevel()!=null?"Level "+i.tunedMinLevel()+"+ (client tuning)":i.levelMin()==null?"Level not yet discovered":"Level "+i.levelMin()+(i.levelMax()==null?"+":"–"+i.levelMax())),"Instance level band"));
      boolean isNew=data.context().changes().stream().anyMatch(c->"new".equals(c.status()) && "Map".equals(c.kind()) && ("client:"+c.id()).equals(i.key()));
      if(isNew)row.add(new Chip("New",Chip.Style.PLAIN));
      if("client:3065".equals(i.key()) || "client:2999".equals(i.key())) row.add(new WrappingText(
          ("client:3065".equals(i.key())?"Beta week 1 announcement: Level 13–18":"Beta week 1 announcement: Level 15–20")
          +" · Final launch band unannounced · Blizzard: https://worldofwarcraft.blizzard.com/en-us/news/24304160/","Announced beta level band"));
      addRow(row);
    });
    text("Discovery records — achievement names are Reported; they do not establish a complete boss roster.");
    for(Discovery d:data.discoveries()){
      addRow(LinkButton.create(d.name(),new ViewTarget.Forever("discovery",d.mapId()),nav));
      text(d.type()+" · "+d.opening()+(d.openingSource().isBlank()?"":" · "+d.openingSource()));
    }
  }
}
