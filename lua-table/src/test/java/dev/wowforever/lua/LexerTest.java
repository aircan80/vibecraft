package dev.wowforever.lua;

import static dev.wowforever.lua.TokenType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

  private static List<Token> tok(String s) {
    return new Lexer(s, "t.lua").tokenize();
  }

  private static Token eof(int line) {
    return new Token(EOF, "", line);
  }

  @Test
  void decodesEscapes() {
    assertThat(tok("'Recruit\\'s Shirt'")).containsExactly(new Token(STRING, "Recruit's Shirt", 1), eof(1));
    assertThat(tok("\"a\\\"b\\\\c\\n\\65\"").get(0).text()).isEqualTo("a\"b\\c\nA");
  }

  @Test
  void longStrings() {
    assertThat(tok("[[return {\n[1]='x'}]]").get(0)).isEqualTo(new Token(STRING, "return {\n[1]='x'}", 1));
    assertThat(tok("[==[a]]b]==]").get(0).text()).isEqualTo("a]]b");
  }

  @Test
  void skipsComments() {
    assertThat(tok("--[[ block\n]] 1 -- line\n2")).extracting(Token::type).containsExactly(NUMBER, NUMBER, EOF);
    assertThat(tok("--[[ block\n]] 1 -- line\n2").get(1).line()).isEqualTo(3);
  }

  @Test
  void numbersAndOperators() {
    assertThat(tok("-1.5 3 == ~= .. ...")).extracting(Token::type)
        .containsExactly(MINUS, NUMBER, NUMBER, EQ, NE, CONCAT, ELLIPSIS, EOF);
  }

  @Test
  void keywordsAndNames() {
    assertThat(tok("local nil_x = nil")).extracting(Token::type).containsExactly(LOCAL, NAME, ASSIGN, NIL, EOF);
  }

  @Test
  void unterminatedStringFailsWithLine() {
    assertThatThrownBy(() -> tok("x = 'abc")).isInstanceOf(LuaSyntaxException.class).hasMessageContaining("line 1");
  }

  @Test
  void numbersKeepSourceText() {
    assertThat(tok("56.07 8589934770 0x1F 1e5")).extracting(Token::text)
        .containsExactly("56.07", "8589934770", "0x1F", "1e5", "");
  }

  @Test
  void commentsWithUrlsAndApostrophes() {
    assertThat(tok("-- see http://x.y/z it's\nreturn 1")).extracting(Token::type).containsExactly(RETURN, NUMBER, EOF);
  }

  @Test
  void topLevelVarargAndLengthAndComparisons() {
    assertThat(tok("local addonname = ... #t <= >= < > ~= a..b")).extracting(Token::type)
        .containsExactly(LOCAL, NAME, ASSIGN, ELLIPSIS, HASH, NAME, LE, GE, LT, GT, NE, NAME, CONCAT, NAME, EOF);
  }

  @Test
  void dotAndNumberDisambiguation() {
    assertThat(tok("a.b .5 1..2")).extracting(Token::type)
        .containsExactly(NAME, DOT, NAME, NUMBER, NUMBER, CONCAT, NUMBER, EOF);
  }

  @Test
  void bracketIsNotLongStringUnlessLevelMatches() {
    assertThat(tok("t[1] t[=x]")).extracting(Token::type)
        .containsExactly(NAME, LBRACKET, NUMBER, RBRACKET, NAME, LBRACKET, ASSIGN, NAME, RBRACKET, EOF);
  }

  @Test
  void lineCountsAcrossLongString() {
    List<Token> t = tok("[[a\nb\nc]] x");
    assertThat(t.get(0).line()).isEqualTo(1);
    assertThat(t.get(1).line()).isEqualTo(3);
  }

  @Test
  void longStringDropsFirstNewline() {
    assertThat(tok("[[\nabc]]").get(0).text()).isEqualTo("abc");
  }

  @Test
  void doubleQuotedWithApostrophe() {
    assertThat(tok("\"Captain Sanders' Hidden Treasure\"").get(0).text())
        .isEqualTo("Captain Sanders' Hidden Treasure");
  }

  @Test
  void unknownEscapeKeepsChar() {
    assertThat(tok("'\\q'").get(0).text()).isEqualTo("q");
  }

  @Test
  void unterminatedLongStringFails() {
    assertThatThrownBy(() -> tok("\n[[abc")).isInstanceOf(LuaSyntaxException.class).hasMessageContaining("line 2");
  }

  @Test
  void hugeLongStringIsFast() {
    StringBuilder sb = new StringBuilder("[[return {\n");
    for (int i = 0; i < 20_000; i++) {
      sb.append("[").append(i).append("]={\"name\",").append(i).append("},\n");
    }
    sb.append("}]]");
    long start = System.nanoTime();
    List<Token> t = tok(sb.toString());
    assertThat(t).hasSize(2);
    assertThat(t.get(0).text()).startsWith("return {").hasSizeGreaterThan(400_000);
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
  }
}
