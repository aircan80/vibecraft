package dev.wowforever.app.ui.widgets;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.Icon;

/**
 * The rail's client art: the six Blizzard UI textures the database embeds ({@code ui_asset}, phase 3A), decoded once
 * per start. {@code plates} holds each target's {@value #ART} px image for its navy plate on the {@link Rail}
 * ({@link #plateArt}); {@code small} the image its {@value #SMALL} px icon is drawn from on section headings and list
 * pages: the {@code @24} variant of Interface/Icons art (already cropped), the native 16 px glyph of GossipFrame art. A
 * target missing from a map (no texture, or one that does not decode) shows its SVG icon instead.
 *
 * <p>{@link #load} reads the database, so it runs on a worker; the app then {@linkplain #install installs} the result
 * on the EDT, and until then {@link #shared} is {@link #none}. Client art is used for these icons only.
 */
public record RailIcons(Map<RailTarget, BufferedImage> plates, Map<RailTarget, BufferedImage> small) {
  /** The size of the plate art, in logical pixels. */
  public static final int ART = 24;
  /** The size of the icons on section headings and list pages, in logical pixels. */
  public static final int SMALL = 16;
  /** The part of an Interface/Icons texture kept: its centre, without the baked-in bevel at the edges. */
  static final double CROP = 0.86;
  /** How much a GossipFrame glyph is enlarged for the plate (plan open question 4: 16 px to 24 px). */
  static final double GOSSIP_SCALE = 1.5;

  private static final RailIcons NONE = new RailIcons(Map.of(), Map.of());
  private static volatile RailIcons shared = NONE;

  public RailIcons {
    plates = Map.copyOf(plates);
    small = Map.copyOf(small);
  }

  /** No client art: every target shows its SVG icon. */
  public static RailIcons none() {
    return NONE;
  }

  /**
   * The art of every target read through {@code uiAsset} (the PNG of a {@code ui_asset} name, empty when there is
   * none; on the calling thread, so not the EDT). A missing or undecodable texture leaves its target to the SVG.
   */
  public static RailIcons load(Function<String, Optional<byte[]>> uiAsset) {
    Objects.requireNonNull(uiAsset, "uiAsset");
    Map<RailTarget, BufferedImage> plates = new EnumMap<>(RailTarget.class);
    Map<RailTarget, BufferedImage> small = new EnumMap<>(RailTarget.class);
    for (RailTarget t : RailTarget.values()) {
      Optional<BufferedImage> art = uiAsset.apply(t.assetName()).flatMap(Images::decode);
      art.ifPresent(a -> plates.put(t, plateArt(a, t.gossip())));
      Optional<BufferedImage> icon = t.gossip() ? art
          : uiAsset.apply(t.assetName() + "@24").flatMap(Images::decode);
      icon.or(() -> Optional.ofNullable(plates.get(t))).ifPresent(i -> small.put(t, i));
    }
    return new RailIcons(plates, small);
  }

  /**
   * {@code src} fitted to the {@value #ART} px plate art. Interface/Icons art ({@code gossip} false) is cropped to
   * its central 86 % ({@link #CROP}: a centred square of 86 % of its shorter side), then scaled to {@value #ART} px,
   * bicubic. A
   * GossipFrame glyph is scaled by {@value #GOSSIP_SCALE}, bicubic (less if that would overflow), and centred on a
   * transparent {@value #ART} px canvas: the native 16 px glyph fills it.
   */
  public static BufferedImage plateArt(BufferedImage src, boolean gossip) {
    Objects.requireNonNull(src, "src");
    if (gossip) {
      double factor = Math.min(GOSSIP_SCALE, (double) ART / Math.max(src.getWidth(), src.getHeight()));
      int w = Math.max(1, (int) Math.round(src.getWidth() * factor));
      int h = Math.max(1, (int) Math.round(src.getHeight() * factor));
      BufferedImage out = new BufferedImage(ART, ART, BufferedImage.TYPE_INT_ARGB);
      draw(out, src, (ART - w) / 2, (ART - h) / 2, w, h);
      return out;
    }
    int side = Math.min(src.getWidth(), src.getHeight());
    int crop = Math.max(1, (int) Math.round(side * CROP));
    BufferedImage centre = new BufferedImage(crop, crop, BufferedImage.TYPE_INT_ARGB);
    // a copy, not a subimage: the bicubic scale must not reach into the cut-off edge
    draw(centre, src, -(src.getWidth() - crop) / 2, -(src.getHeight() - crop) / 2, src.getWidth(),
        src.getHeight());
    return IconCache.scale(centre, ART);
  }

  /** The art of {@code target} for its rail plate, {@value #ART} px; empty without client art. */
  public Optional<Icon> plate(RailTarget target) {
    return Optional.ofNullable(plates.get(target)).map(image -> new ArtIcon(image, ART));
  }

  /** The icon of {@code target} at {@code px} logical pixels, without a plate: the client art, else the SVG. */
  public Icon small(RailTarget target, int px) {
    BufferedImage image = small.get(target);
    return image != null ? new ArtIcon(image, px) : Icons.icon(target.iconName(), px);
  }

  /** The icons the app's rail and pages use; {@link #none} until the app installs its own. */
  public static RailIcons shared() {
    return shared;
  }

  /** Makes {@code icons} the {@linkplain #shared shared} ones; returns those it replaces. */
  public static RailIcons install(RailIcons icons) {
    RailIcons before = shared;
    shared = Objects.requireNonNull(icons, "icons");
    return before;
  }

  private static void draw(BufferedImage into, BufferedImage image, int x, int y, int w, int h) {
    Graphics2D g = into.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(image, x, y, w, h, null);
    } finally {
      g.dispose();
    }
  }

  /**
   * {@code image} drawn {@code size} logical pixels square (scaled with the UI, bicubic). Two icons of the same image
   * and size are equal.
   */
  private record ArtIcon(BufferedImage image, int size) implements Icon {
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(image, x, y, getIconWidth(), getIconHeight(), null);
      } finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return UIScale.scale(size);
    }

    @Override
    public int getIconHeight() {
      return UIScale.scale(size);
    }
  }
}
