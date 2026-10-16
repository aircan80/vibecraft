package dev.wowforever.lua;

/** A lexical token. STRING text is the decoded value; NUMBER text is the source text. */
public record Token(TokenType type, String text, int line) {}
