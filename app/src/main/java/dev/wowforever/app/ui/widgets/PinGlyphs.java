package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import dev.wowforever.app.ui.widgets.MapView.PinKind;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

/**
 * The Questie-style glyphs of quest giver pins: a bold yellow {@code "!"} for a giver that starts a quest, a grey
 * {@code "?"} for one that only ends quests, in the page's bold font at {@value #SIZE} px ({@value #EMPHASISED_SIZE}
 * px for a quest page's own givers), scaled with the UI. Each has a 1 px dark outline and a 1 px light halo beyond it,
 * so it reads on the parchment-coloured maps in both themes.
 */
final class PinGlyphs {
  static final Color START = new Color(0xFFD100);
  static final Color END = new Color(0xB8B8B8);
  static final Color OUTLINE = new Color(0x0C1424);
  static final Color HALO = new Color(0xF4EBD0);
  /** The glyph size at 100 %, in logical px. */
  static final float SIZE = 14f;
  /** The glyph size of an emphasised pin at 100 %. */
  static final float EMPHASISED_SIZE = 20f;
  /** How far a glyph reaches from its centre, outline and halo included, in logical px. */
  static final int RADIUS = 8;
  static final int EMPHASISED_RADIUS = 11;

  private PinGlyphs() {}

  /** The glyph font: {@code base} in bold at {@link #SIZE} (or {@link #EMPHASISED_SIZE}) scaled px. */
  static Font font(Font base, boolean emphasised) {
    return base.deriveFont(Font.BOLD, UIScale.scale(emphasised ? EMPHASISED_SIZE : SIZE));
  }

  /** {@code "!"} for {@link PinKind#QUEST_START}, {@code "?"} for {@link PinKind#QUEST_END}. */
  static String glyph(PinKind kind) {
    return kind == PinKind.QUEST_START ? "!" : "?";
  }

  static Color fill(PinKind kind) {
    return kind == PinKind.QUEST_START ? START : END;
  }

  static void paintDisc(Graphics2D g, Font font, PinKind kind, double x, double y) {
    double r = UIScale.scale(9f);
    Shape disc = new java.awt.geom.Ellipse2D.Double(x-r, y-r, 2*r, 2*r);
    paintShape(g, disc, START, true);
    GlyphVector gv = font.deriveFont(font.getSize2D() * 0.8f).createGlyphVector(g.getFontRenderContext(), glyph(kind));
    Rectangle2D bounds = gv.getVisualBounds();
    g.setColor(OUTLINE);
    g.fill(gv.getOutline((float)(x-bounds.getCenterX()), (float)(y-bounds.getCenterY())));
  }

  static void paintObjective(Graphics2D g, Font font, PinKind kind, int number, double x, double y, double r) {
    Shape shape;
    if (kind == PinKind.COLLECT) {
      java.awt.geom.Path2D diamond = new java.awt.geom.Path2D.Double();
      diamond.moveTo(x, y-r); diamond.lineTo(x+r, y); diamond.lineTo(x, y+r);
      diamond.lineTo(x-r, y); diamond.closePath(); shape = diamond;
    } else if (kind == PinKind.INTERACT) {
      shape = new java.awt.geom.RoundRectangle2D.Double(x-r, y-r, 2*r, 2*r, r/2, r/2);
    } else {
      shape = new java.awt.geom.Ellipse2D.Double(x-r, y-r, 2*r, 2*r);
    }
    paintShape(g, shape, MapView.colour(kind), kind != PinKind.EXPLORE);
    if (kind == PinKind.KILL) {
      g.setFont(font); g.setColor(OUTLINE);
      String text = Integer.toString(number);
      java.awt.FontMetrics fm = g.getFontMetrics();
      g.drawString(text, (float)(x-fm.stringWidth(text)/2.0), (float)(y+(fm.getAscent()-fm.getDescent())/2.0));
    }
  }

  private static void paintShape(Graphics2D g, Shape shape, Color fill, boolean solid) {
    Stroke before = g.getStroke();
    g.setColor(HALO); g.setStroke(new BasicStroke(UIScale.scale(5f))); g.draw(shape);
    g.setColor(OUTLINE); g.setStroke(new BasicStroke(UIScale.scale(3f))); g.draw(shape);
    g.setColor(fill);
    if (solid) g.fill(shape);
    else { g.setStroke(new BasicStroke(UIScale.scale(1f))); g.draw(shape); }
    g.setStroke(before);
  }

  /** Paints the glyph of {@code kind} in {@code font}, its visual bounds centred on {@code x}, {@code y}. */
  static void paint(Graphics2D g2, Font font, PinKind kind, double x, double y) {
    GlyphVector gv = font.createGlyphVector(g2.getFontRenderContext(), glyph(kind));
    Rectangle2D bounds = gv.getVisualBounds();
    Shape shape = gv.getOutline((float) (x - bounds.getCenterX()), (float) (y - bounds.getCenterY()));
    Stroke before = g2.getStroke();
    // strokes are centred on the outline: the halo reaches 2 px out, the outline over it 1 px, the fill covers the rest
    g2.setStroke(new BasicStroke(UIScale.scale(4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(HALO);
    g2.draw(shape);
    g2.setStroke(new BasicStroke(UIScale.scale(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(OUTLINE);
    g2.draw(shape);
    g2.setColor(fill(kind));
    g2.fill(shape);
    g2.setStroke(before);
  }
}
