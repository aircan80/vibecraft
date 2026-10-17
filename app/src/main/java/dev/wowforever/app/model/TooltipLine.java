package dev.wowforever.app.model;

/**
 * One line of an item's tooltip as the importer stored it: {@code kind} (name, binding, stat, use, ...),
 * {@code text} (a tab separates left and right text) and {@code colour} ({@code #rrggbb}, or null for the default
 * text colour).
 */
public record TooltipLine(String kind, String text, String colour) {}
