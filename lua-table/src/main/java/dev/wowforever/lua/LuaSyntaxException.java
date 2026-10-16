package dev.wowforever.lua;

public class LuaSyntaxException extends RuntimeException {

  public LuaSyntaxException(String fileName, int line, String message) {
    super(fileName + ": line " + line + ": " + message);
  }
}
