package dev.wowforever.app.ui;

import dev.wowforever.app.model.EntityKind;
import dev.wowforever.app.model.EntityRef;
import dev.wowforever.app.nav.ViewTarget;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a user pastes into the search box: an in-game link ({@code |cffa335ee|Hitem:19019:…|h[Name]|h|r},
 * quest links too), an item or NPC string ({@code item:10329}, {@code npc:448}), a Wowhead URL
 * ({@code …/item=10329}, {@code npc=}, {@code quest=}, {@code zone=}, {@code object=}) or a bare number (an item
 * id) opens that page; a bracketed name ({@code [Scarlet Belt]}, or a link of another type) searches for the name.
 * Anything else is ordinary text.
 */
public final class PasteParser {
  /** An in-game hyperlink: {@code |H<type>:<id>…|h[<name>]|h}, colour codes around it optional. */
  private static final Pattern LINK = Pattern.compile(
      "(?:\\|c\\p{XDigit}{8})?\\|H(\\w+):(\\d+)[^|]*\\|h\\[([^\\]]*)]\\|h(?:\\|r)?");
  private static final Pattern ENTITY_STRING =
      Pattern.compile("(?i)(item|npc|quest|object|zone):(\\d+)(?::-?\\d*)*");
  private static final Pattern WOWHEAD = Pattern.compile(
      "(?i)(?:https?://)?(?:[\\w-]+\\.)*wowhead\\.com[/?](?:.*?[/?&])?(item|npc|quest|object|zone)=(\\d+)\\b.*");
  private static final Pattern BRACKETED = Pattern.compile("\\[([^\\[\\]]*)]");
  private static final Pattern NUMBER = Pattern.compile("#?(\\d{1,10})");

  private PasteParser() {}

  /** What pasting {@code text} should open, or empty when it is ordinary text (or null or blank). */
  public static Optional<ViewTarget> parse(String text) {
    if (text == null) {
      return Optional.empty();
    }
    String s = text.strip();
    Matcher m = LINK.matcher(s);
    if (m.matches()) {
      Optional<ViewTarget> entity = entity(m.group(1), m.group(2));
      return entity.isPresent() ? entity : search(m.group(3));
    }
    m = ENTITY_STRING.matcher(s);
    if (m.matches()) {
      return entity(m.group(1), m.group(2));
    }
    m = WOWHEAD.matcher(s);
    if (m.matches()) {
      return entity(m.group(1), m.group(2));
    }
    m = BRACKETED.matcher(s);
    if (m.matches()) {
      return search(m.group(1));
    }
    m = NUMBER.matcher(s);
    if (m.matches()) {
      return entity("item", m.group(1));
    }
    return Optional.empty();
  }

  /** The page of {@code type} (a link, string or URL type such as {@code item}) {@code id}; empty if unknown. */
  private static Optional<ViewTarget> entity(String type, String id) {
    EntityKind kind = switch (type.toLowerCase(Locale.ROOT)) {
      case "item" -> EntityKind.ITEM;
      case "npc" -> EntityKind.NPC;
      case "quest" -> EntityKind.QUEST;
      case "object" -> EntityKind.OBJECT;
      case "zone" -> EntityKind.ZONE;
      default -> null;
    };
    long value;
    try {
      value = Long.parseLong(id);
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
    return kind == null || value <= 0 ? Optional.empty()
        : Optional.of(new ViewTarget.Entity(new EntityRef(kind, value)));
  }

  private static Optional<ViewTarget> search(String name) {
    String s = name.strip();
    return s.isEmpty() ? Optional.empty() : Optional.of(new ViewTarget.Search(s, Optional.empty()));
  }
}
