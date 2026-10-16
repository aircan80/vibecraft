package dev.wowforever.app.model;

/** A zone's fully revealed map: its UiMap id, the image size in pixels and the JPEG bytes (not copied). */
public record ZoneMap(long uiMapId, int width, int height, byte[] jpeg) {}
