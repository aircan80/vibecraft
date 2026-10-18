# Incomplete spell-description audit and bounded persistence proposal

Related: [#13](https://github.com/aircan80/vibecraft/issues/13), [#6](https://github.com/aircan80/vibecraft/issues/6), [#1](https://github.com/aircan80/vibecraft/issues/1). Audit only: no production formatter, schema, repository or UI changes were made by this investigation.

## Exact reproduced cohort

At **2026-10-18T04:36:49Z**, the audit read the failed job's retained Questie snapshot and exact Forever **1.60.1.70450 / enUS** CSV input. It reapplied the same Questie correction layers, selected the same client items, and called the real item-effect and set-bonus loaders with the real formatter. It reproduced **162 incomplete spells**, **5,429 total item effects** and **153 selected sets**, matching the original report.

| Affected scope | Count |
| --- | ---: |
| Distinct incomplete spell descriptions | 162 |
| Incomplete spells called for item effects | 159 |
| Additional set-bonus-only incomplete spells | 3 |
| Incomplete item-effect rows / distinct items | 166 / 166 |
| Incomplete item-effect rows eligible for an item tooltip | 149 |
| Incomplete set-bonus rows | 3 |
| Selected item cards receiving those set bonuses | 24 |
| Distinct affected items across effects and sets | 190 |
| Distinct affected items eligible for a rendered tooltip | 173 |
| Incomplete cohort spells with no selected item/set context | **0** |

These are not unused spells discovered by scanning the whole spell database. The formatter records only descriptions actually requested by selected item effects or set bonuses. The 17 non-tooltip-eligible effect rows belong to items with no matched ItemSparse card; they are still selected effect records, not globally unused spells.

The 166 incomplete effect rows comprise 152 Use, 7 Equip and 7 Chance-on-hit rows. None used an item-description template override. The three incomplete set bonuses are spell 27778 / set 514, 27780 / set 518, and 27867 / set 517, each rendered for eight selected set pieces.

Durable full cohort evidence is retained **outside Git** in the development review directory `spell-text-audit/cohort.json`: **133,489 bytes**, SHA-256 **`6113e16f232e7d5dab34b8c2f049730d973eaa1c5c4f28de113749f53ff4f547`**. It includes each spell ID, exact unresolved tokens, source/raw template, partially formatted text, affected item/effect/set contexts and tooltip eligibility. The archived input copy, Java audit source and formatter report are retained alongside it. No game-text corpus or generated database was added to source control.

## Token families

Counts are distinct affected spells per family; one spell may appear in several families.

| Family | Spells | Cohort finding / safe next step |
| --- | ---: | --- |
| `$qN` | 100 | 99 are Replace Supplies spells; the other is Resurrection. Do not alias to `$sN` or assume the same numeric meaning across effects. |
| `$ecN` | 18 | Enchantment/kit description context. Needs verified enchantment lookup/scaling semantics. |
| `$<id>u` | 12 | Cross-spell stacking references. Research exact-build aura-stack mapping before implementation. |
| `$ecim` | 11 | Enchantment minimum-item-level context. Do not substitute player level or item-card level. |
| `$proccooldown` | 6 | Includes all three affected set bonuses. Native aura options retain candidate recovery data, but tooltip variable semantics/units still need validation. |
| `$<id>qN` | 5 | Soulstone resurrection descriptions; preserve unknown mana values until verified. |
| `$aN` | 4 | Radius references remain unresolved despite the radius table being present; inspect missing effect/radius relationships, rather than inventing a radius. |
| `$r` | 3 | Radius/range-looking context, but exact mapping is unverified; SpellRange is not selected. |
| `$<id>r`, `$<id>sN`, `$sN` | 2 each | Cross-spell range/value or missing effect-slot references. Missing source references are distinct from unsupported syntax. |
| `$U`, `$i`, `$n`, `$tN`, `$u`, `$v`, `$z`, `${...}` | 1 each | Stacks/targets/charges/tick/context/expression cases. Hearthstone's home location is character-specific and cannot become a fixed library fact. |

The selected SpellAuraOptions CSV includes CumulativeAura, ProcCategoryRecovery, ProcCharges and ProcChance. SpellEffect includes chain/radius/trigger and scaling fields. Field presence alone does not verify a tooltip-variable mapping. The [WoWDBDefs schema](https://github.com/wowdev/WoWDBDefs/blob/master/definitions/SpellAuraOptions.dbd) is useful structural evidence, not proof that an unknown placeholder should be replaced with a guessed value. No numeric formatter fixes are proposed as verified by this audit.

## What currently happens

`SpellDescriptionFormatter` leaves unrecognized or unsupported references unchanged and returns `complete=false` with unresolved-token evidence. This preserves evidence and avoids inventing values. `ItemSpellEffect` and `ItemSetInfo.Bonus` retain the boolean in memory, but `SqliteWriter.writeTooltips` stores only text, losing that status. The current app model/repository has no durable per-line completeness field.

The existing `TooltipLinesPanel.withoutSpellTokens` already replaces remaining variables with **?**, and ItemCardPanel uses that renderer. Therefore this is not a claim that raw `$` tokens are currently shown as finished tooltip facts. The remaining product gap is an explicit explanation of incomplete values, durable evidence and honest legacy status, rather than another token-masking implementation.

`clientdata.unformatted` is an informational report counter, not an input to `Main.unsupportedCounters`. That method counts missingField and supported unresolved entity/UI-field counters. Archive projection additionally checks UI skips and the separate named-item coverage threshold. This job failed because **15,672 / 21,294 named items (73%)** was below **85%**, not because the 162 spell descriptions triggered a strict formatting gate. This audit does not waive either the reported text findings or the item-coverage finding.

## Proposed bounded implementation for #13

1. Preserve each formatter result's completeness, exact unresolved tokens and template origin through item effects, set bonuses and derived tooltip lines. Add compatibility constructors so existing callers remain valid. Do not infer completeness by checking whether a partially formatted string contains a dollar sign.
2. Add optional sidecar evidence tables keyed by existing item/effect ordinal, set/bonus key and item/tooltip ordinal. Store COMPLETE/INCOMPLETE, spell identity, attributed raw/partially formatted text, unresolved tokens, formatter version and recorded source/build evidence. Keep the normal tooltip text and its existing numeric fields unchanged.
3. Write/update/delete the sidecars in the same candidate transaction as their corresponding rows. Include hotfix/client/native completion writer paths; replacing a tooltip must not leave stale status evidence. Retain the sidecars through compact runtime export.
4. Let repositories detect the optional tables once per connection and join evidence without per-line SQL. Older read-only databases remain compatible: missing status means **UNKNOWN**, never presumed COMPLETE. Static non-spell lines are a separate not-applicable case.
5. Keep the current question-mark masking. Add a brief explanation for incomplete descriptions and an explicit disclosure of recorded text/source evidence. Old spell-text rows can be qualified once per card as formatting status unavailable, avoiding a badge on every static line. No source-comparison redesign, Main or POM changes are required.
6. Keep all report counts. Validate round trips for complete and incomplete references, missing effects, set replication, updates/deletion, rollback, compact export and legacy UNKNOWN fallback. UI checks must verify helpful wording, accessibility and preserved raw evidence without exposing tokens as final facts.

Expected file scope: importer `ItemSpellEffect`, `ItemSetInfo`, `TooltipLine`, their loaders, `TooltipBuilder`, schema/`SqliteWriter` and client/native update writers; optional compact-export allowlist if applicable; app `TooltipLine`, `ItemRepository`, `TooltipLinesPanel` and focused tests. A small shared text-evidence record/table helper may reduce duplication. `SourceEvidencePanel`, unrelated UI files, Main and POM remain outside this proposal.
