package dev.wowforever.lua;

import java.util.ArrayList;
import java.util.List;

public final class ProblemCollector {
  private final List<Problem> problems = new ArrayList<>();

  public void add(String file, int line, String message) {
    problems.add(new Problem(file, line, message));
  }

  public List<Problem> problems() {
    return List.copyOf(problems);
  }

  public int size() {
    return problems.size();
  }
}
