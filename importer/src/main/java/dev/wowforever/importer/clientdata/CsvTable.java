package dev.wowforever.importer.clientdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A client table exported as CSV (one DB2 table per file, header row first).
 *
 * <p>The file is parsed by an RFC 4180 state machine reading a {@link BufferedReader} in chunks, so no
 * full-file string is built: quoted fields may contain commas, doubled quotes and line breaks (CRLF or LF,
 * kept verbatim inside quotes); records end in CRLF, LF or a lone CR; a UTF-8 byte order mark is skipped;
 * blank lines are skipped. Every record must have exactly as many fields as the header, and a character
 * after a closing quote other than a separator is an error: a shifted column would silently corrupt every
 * later field, so the parser fails with {@code file:line} instead. Rows are kept in memory as string
 * arrays (short repeated values such as {@code "0"} are shared).
 */
public final class CsvTable {
  private static final int SHARE_MAX_LENGTH = 8;

  private final String source;
  private final List<String> columns;
  private final Map<String, Integer> index;
  private final List<Row> rows;

  private CsvTable(String source, List<String> columns, Map<String, Integer> index, List<Row> rows) {
    this.source = source;
    this.columns = columns;
    this.index = index;
    this.rows = rows;
  }

  /**
   * Reads a UTF-8 CSV file.
   *
   * @throws UncheckedIOException if the file cannot be read (the message names it)
   * @throws IllegalStateException if the content is not well-formed CSV ({@code file:line: problem})
   */
  public static CsvTable read(Path csv) {
    String name = csv.getFileName() != null ? csv.getFileName().toString() : csv.toString();
    try (BufferedReader in = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
      return read(name, in);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + csv + ": " + e.getMessage(), e);
    }
  }

  /** Reads CSV from {@code in}; {@code source} names it in error messages. The reader is not closed. */
  public static CsvTable read(String source, Reader in) throws IOException {
    Parser parser = new Parser(source, in);
    String[] header = parser.next();
    if (header == null) {
      throw new IllegalStateException(source + ": empty file, no header row");
    }
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < header.length; i++) {
      if (index.putIfAbsent(header[i], i) != null) {
        throw new IllegalStateException(source + ":" + parser.recordLine + ": duplicate column " + header[i]);
      }
    }
    CsvTable table = new CsvTable(source, List.of(header), Map.copyOf(index), new ArrayList<>());
    String[] fields;
    while ((fields = parser.next()) != null) {
      if (fields.length != header.length) {
        throw new IllegalStateException(source + ":" + parser.recordLine + ": expected " + header.length
            + " fields, got " + fields.length);
      }
      table.rows.add(table.new Row(fields, parser.recordLine));
    }
    return table;
  }

  /** The file name this table was read from. */
  public String source() {
    return source;
  }

  public List<String> columns() {
    return columns;
  }

  public boolean hasColumn(String name) {
    return index.containsKey(name);
  }

  /** @throws IllegalArgumentException naming the file and the column when there is no such column */
  public int column(String name) {
    Integer i = index.get(name);
    if (i == null) {
      throw new IllegalArgumentException(source + " has no column " + name + " (columns: " + columns + ")");
    }
    return i;
  }

  public Iterable<Row> rows() {
    return Collections.unmodifiableList(rows);
  }

  public int size() {
    return rows.size();
  }

  /**
   * Rows by the integer value of {@code idColumn}, in file order. Rows whose cell is empty are left out.
   *
   * @throws IllegalStateException when two rows share a value (use {@link #groupByLong} for those columns)
   */
  public Map<Long, Row> indexByLong(String idColumn) {
    int c = column(idColumn);
    Map<Long, Row> out = new LinkedHashMap<>();
    for (Row r : rows) {
      OptionalLong key = r.getLong(c);
      if (key.isPresent() && out.putIfAbsent(key.getAsLong(), r) != null) {
        throw new IllegalStateException(source + ":" + r.line() + ": duplicate " + idColumn + " " + key.getAsLong());
      }
    }
    return out;
  }

  /** Rows grouped by the integer value of {@code col}, groups and rows in file order; empty cells left out. */
  public Map<Long, List<Row>> groupByLong(String col) {
    int c = column(col);
    Map<Long, List<Row>> out = new LinkedHashMap<>();
    for (Row r : rows) {
      OptionalLong key = r.getLong(c);
      if (key.isPresent()) {
        out.computeIfAbsent(key.getAsLong(), k -> new ArrayList<>()).add(r);
      }
    }
    return out;
  }

  /** One record. Cells are never null; an empty cell is {@code ""}. */
  public final class Row {
    private final String[] cells;
    private final int line;

    private Row(String[] cells, int line) {
      this.cells = cells;
      this.line = line;
    }

    /** The 1-based line on which this record starts. */
    public int line() {
      return line;
    }

    /** The table this record belongs to. */
    public CsvTable table() {
      return CsvTable.this;
    }

    public String get(int column) {
      return cells[column];
    }

    /** @throws IllegalArgumentException naming the file and the column when there is no such column */
    public String get(String col) {
      return cells[column(col)];
    }

    /** Empty when the cell is empty; {@link NumberFormatException} naming file, line and column otherwise. */
    public OptionalInt getInt(String col) {
      return getInt(column(col));
    }

    public OptionalInt getInt(int column) {
      String v = cells[column];
      if (v.isEmpty()) {
        return OptionalInt.empty();
      }
      try {
        return OptionalInt.of(Integer.parseInt(v));
      } catch (NumberFormatException e) {
        throw badNumber(column, v);
      }
    }

    public OptionalLong getLong(String col) {
      return getLong(column(col));
    }

    public OptionalLong getLong(int column) {
      String v = cells[column];
      if (v.isEmpty()) {
        return OptionalLong.empty();
      }
      try {
        return OptionalLong.of(Long.parseLong(v));
      } catch (NumberFormatException e) {
        throw badNumber(column, v);
      }
    }

    public OptionalDouble getDouble(String col) {
      return getDouble(column(col));
    }

    public OptionalDouble getDouble(int column) {
      String v = cells[column];
      if (v.isEmpty()) {
        return OptionalDouble.empty();
      }
      try {
        return OptionalDouble.of(Double.parseDouble(v));
      } catch (NumberFormatException e) {
        throw badNumber(column, v);
      }
    }

    public int intOr(String col, int ifEmpty) {
      return getInt(col).orElse(ifEmpty);
    }

    public long longOr(String col, long ifEmpty) {
      return getLong(col).orElse(ifEmpty);
    }

    public double doubleOr(String col, double ifEmpty) {
      return getDouble(col).orElse(ifEmpty);
    }

    private NumberFormatException badNumber(int column, String value) {
      return new NumberFormatException(source + ":" + line + ": column " + columns.get(column)
          + " is not a number: \"" + value + "\"");
    }

    @Override
    public String toString() {
      return source + ":" + line + " " + String.join(",", cells);
    }
  }

  /** RFC 4180 record reader over a character stream. */
  private static final class Parser {
    private enum State { FIELD_START, UNQUOTED, QUOTED, AFTER_QUOTE }

    private final String source;
    private final Reader in;
    private final char[] buf = new char[1 << 16];
    private final Map<String, String> shared = new HashMap<>();
    private final StringBuilder field = new StringBuilder();
    private int len;
    private int pos;
    private boolean bomChecked;
    private boolean skipLf;
    private boolean eof;
    private int line = 1;
    int recordLine;

    Parser(String source, Reader in) {
      this.source = source;
      this.in = in;
    }

    /** The next record's fields, or {@code null} at end of input. Blank lines are skipped. */
    String[] next() throws IOException {
      List<String> fields = new ArrayList<>();
      State state = State.FIELD_START;
      field.setLength(0);
      recordLine = line;
      while (true) {
        int c = read();
        if (c < 0) {
          if (state == State.QUOTED) {
            throw new IllegalStateException(source + ":" + recordLine + ": unterminated quoted field");
          }
          if (fields.isEmpty() && state == State.FIELD_START) {
            return null;
          }
          fields.add(take());
          return fields.toArray(String[]::new);
        }
        char ch = (char) c;
        if (skipLf) {
          skipLf = false;
          if (ch == '\n' && state != State.QUOTED) {
            continue;
          }
        }
        switch (state) {
          case FIELD_START, UNQUOTED -> {
            if (ch == ',') {
              fields.add(take());
              state = State.FIELD_START;
            } else if (ch == '\n' || ch == '\r') {
              newline(ch);
              if (fields.isEmpty() && state == State.FIELD_START) {
                recordLine = line; // blank line
                continue;
              }
              fields.add(take());
              return fields.toArray(String[]::new);
            } else if (ch == '"' && state == State.FIELD_START) {
              state = State.QUOTED;
            } else {
              field.append(ch);
              state = State.UNQUOTED;
            }
          }
          case QUOTED -> {
            if (ch == '"') {
              state = State.AFTER_QUOTE;
            } else {
              if (ch == '\n' || (ch == '\r' && peekNot('\n'))) {
                line++;
              }
              field.append(ch);
            }
          }
          case AFTER_QUOTE -> {
            if (ch == '"') {
              field.append('"');
              state = State.QUOTED;
            } else if (ch == ',') {
              fields.add(take());
              state = State.FIELD_START;
            } else if (ch == '\n' || ch == '\r') {
              newline(ch);
              fields.add(take());
              return fields.toArray(String[]::new);
            } else {
              throw new IllegalStateException(source + ":" + line + ": unexpected character '" + ch
                  + "' after closing quote");
            }
          }
          default -> throw new AssertionError(state);
        }
      }
    }

    private void newline(char ch) {
      line++;
      skipLf = ch == '\r';
    }

    /** True when the next character is not {@code c} (used to count a CRLF inside quotes once). */
    private boolean peekNot(char c) throws IOException {
      if (pos >= len && !fill()) {
        return true;
      }
      return buf[pos] != c;
    }

    private String take() {
      String s = field.toString();
      field.setLength(0);
      if (s.length() <= SHARE_MAX_LENGTH) {
        String prior = shared.putIfAbsent(s, s);
        return prior != null ? prior : s;
      }
      return s;
    }

    private int read() throws IOException {
      if (pos >= len && !fill()) {
        return -1;
      }
      return buf[pos++];
    }

    private boolean fill() throws IOException {
      if (eof) {
        return false;
      }
      int n;
      do {
        n = in.read(buf, 0, buf.length);
      } while (n == 0);
      if (n < 0) {
        eof = true;
        return false;
      }
      len = n;
      pos = 0;
      if (!bomChecked) {
        bomChecked = true;
        if (buf[0] == '﻿') {
          pos = 1;
          return pos < len || fill();
        }
      }
      return true;
    }
  }
}
