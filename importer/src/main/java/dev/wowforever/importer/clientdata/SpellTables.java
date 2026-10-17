package dev.wowforever.importer.clientdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * The client's spell tables, as far as spell descriptions need them: {@code Spell} (descriptions),
 * {@code SpellName}, {@code SpellEffect} (per spell and effect index), {@code SpellMisc} and {@code SpellDuration}
 * (a spell's duration), and the optional {@code SpellRadius} (radii) and {@code SpellAuraOptions} (proc chances):
 * fetched since the real data run, but read only when present, so {@code $aN} and {@code $h} stay unformatted
 * without them. Rows of {@code DifficultyID} 0 are preferred where a table has several per spell.
 *
 * <p>Every column read is checked up front; a missing table or column fails naming both.
 */
public final class SpellTables {
  static final List<String> SPELL_COLUMNS = List.of("ID", "Description_lang", "AuraDescription_lang");
  static final List<String> NAME_COLUMNS = List.of("ID", "Name_lang");
  static final List<String> EFFECT_COLUMNS = List.of("SpellID", "EffectIndex", "DifficultyID", "Effect", "EffectAura",
      "EffectBasePointsF", "Variance", "EffectAuraPeriod", "EffectChainTargets", "EffectPointsPerResource",
      "EffectRadiusIndex_0");
  static final List<String> MISC_COLUMNS = List.of("SpellID", "DifficultyID", "DurationIndex");
  static final List<String> DURATION_COLUMNS = List.of("ID", "Duration");
  static final List<String> RADIUS_COLUMNS = List.of("ID", "Radius");
  static final List<String> AURA_OPTIONS_COLUMNS = List.of("SpellID", "DifficultyID", "ProcChance");

  /**
   * One spell effect.
   *
   * @param index        {@code EffectIndex} (0-based; a description's {@code $s1} is index 0)
   * @param basePoints   {@code EffectBasePointsF}, the effect's value
   * @param variance     {@code Variance}: the value spans {@code basePoints * (1 -/+ variance / 2)}
   * @param auraPeriodMs {@code EffectAuraPeriod}, 0 for none
   * @param radiusIndex  {@code EffectRadiusIndex_0}, a {@code SpellRadius} id, 0 for none
   */
  public record Effect(int index, int effect, int aura, double basePoints, double variance, int auraPeriodMs,
      int chainTargets, double pointsPerResource, int radiusIndex) {}

  private record Text(String description, String aura) {}

  private final Map<Long, Text> texts;
  private final Map<Long, String> names;
  private final Map<Long, Map<Integer, Effect>> effects;
  private final Map<Long, Long> durationsMs;
  private final Map<Integer, Double> radii;
  private final Map<Long, Integer> procChances;

  private SpellTables(Map<Long, Text> texts, Map<Long, String> names, Map<Long, Map<Integer, Effect>> effects,
      Map<Long, Long> durationsMs, Map<Integer, Double> radii, Map<Long, Integer> procChances) {
    this.texts = texts;
    this.names = names;
    this.effects = effects;
    this.durationsMs = durationsMs;
    this.radii = radii;
    this.procChances = procChances;
  }

  /** @throws IllegalStateException when a required table is missing or lacks a column read here */
  public static SpellTables load(ClientDataSource src) {
    Map<Long, Text> texts = new HashMap<>();
    for (CsvTable.Row r : require(src.table("Spell"), "Spell", SPELL_COLUMNS).rows()) {
      r.getLong("ID").ifPresent(id -> texts.put(id, new Text(r.get("Description_lang"), r.get("AuraDescription_lang"))));
    }
    Map<Long, String> names = new HashMap<>();
    for (CsvTable.Row r : require(src.table("SpellName"), "SpellName", NAME_COLUMNS).rows()) {
      r.getLong("ID").ifPresent(id -> names.put(id, r.get("Name_lang")));
    }

    Map<Long, Map<Integer, Effect>> effects = new HashMap<>();
    Map<Long, Map<Integer, Integer>> effectDifficulty = new HashMap<>();
    for (CsvTable.Row r : require(src.table("SpellEffect"), "SpellEffect", EFFECT_COLUMNS).rows()) {
      OptionalLong spell = r.getLong("SpellID");
      if (spell.isEmpty()) {
        continue;
      }
      int index = r.intOr("EffectIndex", 0);
      int difficulty = r.intOr("DifficultyID", 0);
      Map<Integer, Integer> seen = effectDifficulty.computeIfAbsent(spell.getAsLong(), k -> new HashMap<>());
      Integer previous = seen.get(index);
      if (previous != null && (previous == 0 || difficulty != 0)) {
        continue; // keep difficulty 0, else the first row
      }
      seen.put(index, difficulty);
      effects.computeIfAbsent(spell.getAsLong(), k -> new HashMap<>()).put(index, new Effect(index,
          r.intOr("Effect", 0), r.intOr("EffectAura", 0), r.doubleOr("EffectBasePointsF", 0),
          r.doubleOr("Variance", 0), r.intOr("EffectAuraPeriod", 0), r.intOr("EffectChainTargets", 0),
          r.doubleOr("EffectPointsPerResource", 0), r.intOr("EffectRadiusIndex_0", 0)));
    }

    Map<Long, Long> durationByIndex = new HashMap<>();
    for (CsvTable.Row r : require(src.table("SpellDuration"), "SpellDuration", DURATION_COLUMNS).rows()) {
      r.getLong("ID").ifPresent(id -> durationByIndex.put(id, r.longOr("Duration", 0)));
    }
    Map<Long, Long> durationsMs = new HashMap<>();
    Map<Long, Integer> miscDifficulty = new HashMap<>();
    for (CsvTable.Row r : require(src.table("SpellMisc"), "SpellMisc", MISC_COLUMNS).rows()) {
      OptionalLong spell = r.getLong("SpellID");
      if (spell.isEmpty()) {
        continue;
      }
      int difficulty = r.intOr("DifficultyID", 0);
      Integer previous = miscDifficulty.get(spell.getAsLong());
      if (previous != null && (previous == 0 || difficulty != 0)) {
        continue;
      }
      miscDifficulty.put(spell.getAsLong(), difficulty);
      Long ms = durationByIndex.get(r.longOr("DurationIndex", 0));
      if (ms != null) {
        durationsMs.put(spell.getAsLong(), ms);
      } else {
        durationsMs.remove(spell.getAsLong());
      }
    }

    Map<Integer, Double> radii = new HashMap<>();
    Optional<CsvTable> radius = src.optionalTable("SpellRadius");
    if (radius.isPresent()) {
      for (CsvTable.Row r : require(radius.get(), "SpellRadius", RADIUS_COLUMNS).rows()) {
        r.getInt("ID").ifPresent(id -> radii.put(id, r.doubleOr("Radius", 0)));
      }
    }

    Map<Long, Integer> procChances = new HashMap<>();
    Map<Long, Integer> optionsDifficulty = new HashMap<>();
    Optional<CsvTable> auraOptions = src.optionalTable("SpellAuraOptions");
    if (auraOptions.isPresent()) {
      for (CsvTable.Row r : require(auraOptions.get(), "SpellAuraOptions", AURA_OPTIONS_COLUMNS).rows()) {
        OptionalLong spell = r.getLong("SpellID");
        if (spell.isEmpty()) {
          continue;
        }
        int difficulty = r.intOr("DifficultyID", 0);
        Integer previous = optionsDifficulty.get(spell.getAsLong());
        if (previous != null && (previous == 0 || difficulty != 0)) {
          continue;
        }
        optionsDifficulty.put(spell.getAsLong(), difficulty);
        procChances.put(spell.getAsLong(), r.intOr("ProcChance", 0));
      }
    }
    return new SpellTables(texts, names, effects, durationsMs, radii, procChances);
  }

  /** True when the spell has a {@code Spell} or a {@code SpellName} row. */
  public boolean contains(long spellId) {
    return texts.containsKey(spellId) || names.containsKey(spellId);
  }

  /** {@code Spell.Description_lang}, empty when the spell is unknown or has none. */
  public String description(long spellId) {
    Text t = texts.get(spellId);
    return t == null ? "" : t.description();
  }

  /** {@code Spell.AuraDescription_lang}, empty when the spell is unknown or has none. */
  public String auraDescription(long spellId) {
    Text t = texts.get(spellId);
    return t == null ? "" : t.aura();
  }

  public Optional<String> name(long spellId) {
    return Optional.ofNullable(names.get(spellId)).filter(n -> !n.isEmpty());
  }

  /** The effect at a 0-based index. */
  public Optional<Effect> effect(long spellId, int index) {
    Map<Integer, Effect> e = effects.get(spellId);
    return e == null ? Optional.empty() : Optional.ofNullable(e.get(index));
  }

  /** The spell's duration in ms ({@code -1} is "until cancelled"); empty without a {@code SpellMisc} duration. */
  public OptionalLong durationMs(long spellId) {
    Long ms = durationsMs.get(spellId);
    return ms == null ? OptionalLong.empty() : OptionalLong.of(ms);
  }

  /**
   * The spell's proc chance in percent ({@code SpellAuraOptions.ProcChance}, 1 to 100); empty without the table or
   * a row, and for 0 and the values above 100 (101 marks a proc that does not roll a percentage).
   */
  public OptionalInt procChance(long spellId) {
    Integer pct = procChances.get(spellId);
    return pct == null || pct < 1 || pct > 100 ? OptionalInt.empty() : OptionalInt.of(pct);
  }

  /** The effect's radius in yards; empty without {@code SpellRadius} or a radius index. */
  public OptionalDouble radius(long spellId, int index) {
    Optional<Effect> e = effect(spellId, index);
    if (e.isEmpty() || e.get().radiusIndex() == 0 || !radii.containsKey(e.get().radiusIndex())) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(radii.get(e.get().radiusIndex()));
  }

  private static CsvTable require(CsvTable t, String name, List<String> columns) {
    for (String col : columns) {
      if (!t.hasColumn(col)) {
        throw new IllegalStateException("client table " + name + " (" + t.source() + ") has no column " + col);
      }
    }
    return t;
  }
}
