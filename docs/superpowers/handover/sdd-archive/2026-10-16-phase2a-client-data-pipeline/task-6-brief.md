### Task 6: Spell description formatter, item effects, sets and tooltip lines

**Files:**
- Create: `clientdata/SpellDescriptionFormatter.java`, `SpellTables.java` (Spell, SpellName, SpellEffect, SpellMisc, SpellDuration loaders), `ItemEffectLoader.java`, `ItemSetLoader.java`, `TooltipBuilder.java`, `TooltipLine.java` (`record TooltipLine(int ordinal, String kind, String text, String colour)`), `BindingNames.java`, `InventoryTypeNames.java`, `ClassRaceNames.java` (ChrClasses/ChrRaces CSVs)
- Modify: writer for `item_effect`, `item_set`, `item_set_item`, `item_set_bonus`, `item_tooltip_line`
- Test: `SpellDescriptionFormatterTest` (table of 30 real descriptions from the fixture spells with expected output; `unknownPlaceholderKeepsRawText`), `ItemEffectLoaderTest`, `ItemSetLoaderTest`, `TooltipBuilderTest` (order and colours), extend `KnownItemsTest` to all kinds.

**Interfaces:**
- `SpellDescriptionFormatter(SpellTables).format(long spellId, String template) -> Formatted(String text, boolean complete)`: supports `$sN`, `$oN` (over-time = base × ticks), `$d`/`$D` (duration from SpellMisc→SpellDuration, "10 sec"/"2 min"), `$aN` (radius), `$xN` (chain targets), `$tN` (period), `$/N;sN`, `${expr}` with `+ - * /` and parentheses, `$lsingular:plural;`, `$<id>sN`, `$g…:…;` (first form), colour codes `|cffRRGGBB … |r` stripped, `\n` kept; absolute values for negative base points when the template reads "by $s1" (rule: `$sN` prints `abs(base)` unless preceded by a sign in the template). Unknown `$…` → left as-is, `complete=false`.
- `TooltipBuilder.build(ClientItem, stats, damage, armor, effects, set, names) -> List<TooltipLine>` in spec order; colours: name = quality colour; use/equip/proc `#1eff00`; flavour `#ffd100`; item level/sell price muted `#9d9d9d`; everything else `#ffffff`. Kinds exactly as spec §4.3.
- Writer: `item_effect(trigger 0 Use/1 Equip/2 Chance on hit, spell_id, text)`, sets, `item_tooltip_line`; counters `clientdata.unformatted` (with spell ids in a note), `clientdata.tooltip.lines`.

- [ ] Steps 1–5 as before; commit `feat(importer): spell description formatting, item effects, sets and tooltip lines`

---

