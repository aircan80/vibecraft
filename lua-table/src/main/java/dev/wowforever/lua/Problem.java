package dev.wowforever.lua;

/** Something the evaluator could not resolve. */
public record Problem(String file, int line, String message) {}
